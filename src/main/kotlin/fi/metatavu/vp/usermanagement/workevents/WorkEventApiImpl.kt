package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.WorkEventsApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftRepository
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.util.*

/**
 * Work Event API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class WorkEventApiImpl: WorkEventsApi, AbstractApi() {

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workEventTranslator: WorkEventTranslator

    @Inject
    lateinit var employeeWorkShiftController: WorkShiftController

    @Inject
    lateinit var workShiftRepository: WorkShiftRepository

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    @WithTransaction
    override fun createEmployeeWorkEvent(employeeId: UUID, workEvent: WorkEvent): Uni<Response> =
        withCoroutineScope {
            if (employeeId != workEvent.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }

            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val employee = userController.find(employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = EMPLOYEE_ENTITY,
                    id = employeeId
                )

            isWorkEventCreatable(workEvent)?.let {
                return@withCoroutineScope createBadRequest(it)
            }

            val created = workEventController.create(
                employee = employee,
                time = workEvent.time,
                workEventType = workEvent.workEventType,
                truckId = workEvent.truckId
            )


            createCreated(workEventTranslator.translate(created))
        }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun findEmployeeWorkEvent(employeeId: UUID, workEventId: UUID): Uni<Response> =
        withCoroutineScope {
            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val timeEntry = workEventController.find(workEventId, employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = TIME_ENTRY,
                    id = workEventId
                )

            createOk(workEventTranslator.translate(timeEntry))
        }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun listEmployeeWorkEvents(
        employeeId: UUID,
        employeeWorkShiftId: UUID?,
        after: OffsetDateTime?,
        before: OffsetDateTime?,
        first: Int,
        max: Int
    ): Uni<Response> = withCoroutineScope{
        if (!isManager() && loggedUserId != employeeId) {
            return@withCoroutineScope createForbidden(FORBIDDEN)
        }

        val workShiftFilter = employeeWorkShiftId?.let {
            employeeWorkShiftController.findEmployeeWorkShift(employeeId, it)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = WORK_SHIFT,
                    id = employeeWorkShiftId
                )
        }
        val (timeEntries, count) = workEventController.list(
            employeeWorkShift = workShiftFilter,
            employeeId = employeeId,
            after = after,
            before = before,
            max = max
        )
        createOk(workEventTranslator.translate(timeEntries), count)
    }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE)
    @WithTransaction
    override fun updateEmployeeWorkEvent(employeeId: UUID, workEventId: UUID, workShiftChangeSetId: UUID, workEvent: WorkEvent): Uni<Response> =
        withCoroutineScope {
            if (employeeId != workEvent.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }

            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            userController.find(employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = EMPLOYEE_ENTITY,
                    id = employeeId
                )

            val foundWorkEvent = workEventController.find(workEventId, employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = TIME_ENTRY,
                    id = workEventId
                )

            isWorkEventEditable(foundWorkEvent, workEvent)?.let {
                return@withCoroutineScope createBadRequest(it)
            }

            val updatedWorkEvent = workEventController.updateFromRest(foundWorkEvent, workEvent)
            createOk(workEventTranslator.translate(updatedWorkEvent))
        }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployeeWorkEvent(employeeId: UUID, workEventId: UUID): Uni<Response> =
        withCoroutineScope {
            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val foundTimeEntry =
                workEventController.find(workEventId, employeeId)
                    ?: return@withCoroutineScope createNotFoundWithMessage(
                        entity = TIME_ENTRY,
                        id = workEventId
                    )
            isWorkEventRemovable(foundTimeEntry)?.let {
                return@withCoroutineScope createBadRequest(it)
            }

            workEventController.delete(foundTimeEntry)
            createNoContent()
        }


    /**
     * Checks if work event is editable. Checks the condtions:
     * - Type of shift start/end cannot be changed
     * - Cannot have two start or end events in the same shift
     * - Events apart from shift start or end cannot be moved outside the shift
     * - Shift start cannot be later than the next event
     * - Shift end cannot be earlier than the previous event
     *
     * @param workEvent work event
     * @param newWorkEventData new work event data
     * @return null if work event is editable, error message otherwise
     */
    private suspend fun isWorkEventEditable(workEvent: WorkEventEntity, newWorkEventData: WorkEvent): String? {
        if (isStartOrEndEvent(workEvent.workEventType) && workEvent.workEventType != newWorkEventData.workEventType) {
            return "Type of shift start/end cannot be changed"
        }

        val otherShiftEvents = workEventController.list(employeeWorkShift = workEvent.workShift).first
            .filter { it.id != workEvent.id }
        val (shiftStart, shiftEnd) = workEventController.getShiftStartEnd(otherShiftEvents)

        if (workEventController.isDuplicateStartOrEndEvent(shiftStart, shiftEnd, newWorkEventData.workEventType)) {
            return "Cannot have two start or end events in the same shift"
        }

        if (!isStartOrEndEvent(workEvent.workEventType)
            && !workEventController.isWithinShiftBounds(shiftStart, shiftEnd, newWorkEventData.time)
        ) {
            return "Event cannot be moved outside the shift"
        }

        if (isStartOrEndEvent(newWorkEventData.workEventType)) {
            otherShiftEvents.forEach { otherEvent ->
                if (newWorkEventData.workEventType == WorkEventType.SHIFT_START && otherEvent.time < newWorkEventData.time) {
                    return "Shift start cannot be moved earlier than other events"
                } else if (newWorkEventData.workEventType == WorkEventType.SHIFT_END && otherEvent.time > newWorkEventData.time) {
                    return "Shift end cannot be moved later than other events"
                }
            }
        }

        if (shiftEnd != null && newWorkEventData.workEventType == WorkEventType.SHIFT_START
            && newWorkEventData.time.isAfter(shiftEnd)
        ) {
            return "Shift start event must be before shift end event"
        }
        if (shiftStart != null && newWorkEventData.workEventType == WorkEventType.SHIFT_END
            && newWorkEventData.time.isBefore(shiftStart)
        ) {
            return "Shift start event must be before shift end event"
        }

        return null
    }

    /**
     * Checks if work event is removable. Checks the conditions:
     * - Shift start or end events cannot be removed if there are other events in the same shift
     *
     * @param workEvent work event
     * @return null if work event is removable, error message otherwise
     */
    private suspend fun isWorkEventRemovable(workEvent: WorkEventEntity): String? {
        val otherEventsOfWorkShift = workEventController.list(employeeWorkShift = workEvent.workShift).first.filter {
            it.workEventType != WorkEventType.SHIFT_START && it.workEventType != WorkEventType.SHIFT_END
        }

        val isShiftStartOrEnd = isStartOrEndEvent(workEvent.workEventType)
        val hasOtherEvents = otherEventsOfWorkShift.isNotEmpty()

        if (isShiftStartOrEnd && hasOtherEvents) {
            return "Shift start or end events cannot be removed if there are other events in the same shift"
        }

        return null
    }

    /**
     * Checks if work event is creatable. Checks the conditions:
     * - Cannot have two start or end events in the same shift
     * - Not start or end events cannot be created outside the shift
     * - Shift start event must be before shift end event
     *
     * @param workEventData work event data
     * @return null if work event is creatable, error message otherwise
     */
    private suspend fun isWorkEventCreatable(workEventData: WorkEvent): String? {
        val latestWorkEvent =
            workEventController.list(employeeId = workEventData.employeeId, first = 0, max = 1).first.firstOrNull()
        val latestWorkShiftEntity = workShiftRepository.findLatestEmployeeWorkShift(workEventData.employeeId, workEventData.time)
        val oldShiftIsEnded = workEventController.oldShiftIsEnded(
            latestWorkEvent,
            workEventData.time,
            latestWorkShiftEntity
        )
        if (oldShiftIsEnded) {
            return null
        }

        // If the latest shift is empty then new event is recorded there
        val workShiftEvents = workEventController.list(employeeWorkShift = latestWorkEvent?.workShift).first
        if (latestWorkShiftEntity != null) {
            val latestWorkShiftEvents = workEventController.list(employeeWorkShift = latestWorkShiftEntity).first
            if (latestWorkShiftEvents.isEmpty()) {
                return null
            }
        }

        // and this is based on work events
        val shiftStart = workShiftEvents.find { it.workEventType == WorkEventType.SHIFT_START }?.time
        val shiftEnd = workShiftEvents.find { it.workEventType == WorkEventType.SHIFT_END }?.time

        if (shiftStart == null && shiftEnd == null) {
            return null
        }

        if (workEventController.isDuplicateStartOrEndEvent(shiftStart, shiftEnd, workEventData.workEventType)) {
            return "Cannot have two start or end events in the same shift"
        }

        if (!isStartOrEndEvent(workEventData.workEventType)
            && !workEventController.isWithinShiftBounds(shiftStart, shiftEnd, workEventData.time)
        ) {
            return "Event cannot be created outside the shift"
        }

        if (shiftEnd != null && workEventData.workEventType == WorkEventType.SHIFT_START
            && workEventData.time.isAfter(shiftEnd)
        ) {
            return "Shift start event must be before shift end event"
        }
        if (shiftStart != null && workEventData.workEventType == WorkEventType.SHIFT_END
            && workEventData.time.isBefore(shiftStart)
        ) {
            return "Shift start event must be before shift end event"
        }

        return null
    }

    /**
     * Checks if work event is start or end event
     *
     * @param workEventType work event type
     * @return true if work event is start or end event
     */
    private fun isStartOrEndEvent(workEventType: WorkEventType): Boolean {
        return workEventType == WorkEventType.SHIFT_START || workEventType == WorkEventType.SHIFT_END
    }
}