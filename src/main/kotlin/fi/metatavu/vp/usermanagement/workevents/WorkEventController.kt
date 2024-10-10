package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import kotlin.math.absoluteValue

/**
 * Controller for Work Events
 */
@ApplicationScoped
class WorkEventController {

    @Inject
    lateinit var workEventRepository: WorkEventRepository

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var workShiftRepository: WorkShiftRepository

    /**
     * Lists work events and sorts them by time from new to old
     *
     * @param employeeId employee id
     * @param employeeWorkShift employee work shift
     * @param after after this time
     * @param before before this time
     * @param first first result
     * @param max max results
     * @return pair of work events and count
     */
    suspend fun list(
        employeeId: UUID? = null,
        employeeWorkShift: WorkShiftEntity? = null,
        after: OffsetDateTime? = null,
        before: OffsetDateTime? = null,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<WorkEventEntity>, Long> {
        return workEventRepository.list(
            employeeId = employeeId,
            employeeWorkShift = employeeWorkShift,
            after = after,
            before = before,
            first = first,
            max = max
        )
    }

    /**
     * Creates a new work event and possibly creates a new shift for it,
     * recalculates the work shift date if needed,
     * recalculates the work shift hours,
     * sets the work shift startedAt or endedAt if needed
     *
     * @param employee employee
     * @param time time
     * @param workEventType work event type
     * @return created work event
     */
    suspend fun create(
        employee: UserRepresentation,
        time: OffsetDateTime,
        workEventType: WorkEventType,
        truckId: UUID? = null
    ): WorkEventEntity {
        val employeeId = UUID.fromString(employee.id)
        val latestWorkEvent = workEventRepository.findLatestWorkEvent(employeeId = employeeId)

        val workShift = getWorkEventShift(latestWorkEvent, time, employeeId)

        val createdWorkEvent = workEventRepository.create(
            id = UUID.randomUUID(),
            employeeId = UUID.fromString(employee.id),
            time = time,
            workEventType = workEventType,
            workShiftEntity = workShift,
            truckId = truckId
        )

        val updatedShift = recalculateWorkShiftTimes(workShift = workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedShift)
        return createdWorkEvent
    }

    /**
     * Finds work event for employee
     *
     * @param workEventId work event id
     * @param employeeId employee id
     * @return work event or null if not found
     */
    suspend fun find(workEventId: UUID, employeeId: UUID): WorkEventEntity? {
        val found = workEventRepository.findByIdSuspending(workEventId)
        if (found?.employeeId == employeeId) {
            return found
        }
        return null
    }

    /**
     * Updates work event,
     * recalculates work shift date if needed,
     * recalculates work shift hours
     *
     * @param foundWorkEvent found work event
     * @param workEvent work event
     * @return updated work event
     */
    suspend fun updateFromRest(foundWorkEvent: WorkEventEntity, workEvent: WorkEvent): WorkEventEntity {
        foundWorkEvent.time = workEvent.time
        foundWorkEvent.workEventType = workEvent.workEventType
        val updated = workEventRepository.persistSuspending(foundWorkEvent)

        val updatedShift = recalculateWorkShiftTimes(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedShift)
        return updated
    }

    /**
     * Deletes work event, removes the work shift if it was the last event for it,
     * recalculates the work shift date if needed,
     * recalculates the work shift hours
     *
     * @param foundWorkEvent found work event
     */
    suspend fun delete(foundWorkEvent: WorkEventEntity) {
        workEventRepository.deleteSuspending(foundWorkEvent)

        if (workEventRepository.list(employeeWorkShift = foundWorkEvent.workShift).first.isEmpty()) {
            workShiftController.deleteEmployeeWorkShift(foundWorkEvent.workShift)
            return
        }

        val updatedWorkShift = recalculateWorkShiftTimes(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedWorkShift)
    }

    /**
     * Recalculates the work shift date/start/end times based on its work events.
     * Finds the first work event in the shift and updates the shift date if needed
     *
     * @param workShift work shift
     */
    private suspend fun recalculateWorkShiftTimes(
        workShift: WorkShiftEntity,
    ): WorkShiftEntity {
        val workEventsForShift = workEventRepository.list(employeeWorkShift = workShift).first

        val first = workEventsForShift.minByOrNull { it.time }
        if (first != null && workShift.date != first.time.toLocalDate()) {
            workShift.date = first.time.toLocalDate()
        }

        workShift.startedAt = null
        workShift.endedAt = null
        workEventsForShift.forEach { workEvent ->
            if (workEvent.workEventType == WorkEventType.SHIFT_START) {
                workShift.startedAt = workEvent.time.toLocalDate()
            } else if (workEvent.workEventType == WorkEventType.SHIFT_END) {
                workShift.endedAt = workEvent.time.toLocalDate()
            }
        }

        return workShiftRepository.persistSuspending(workShift)
    }

    /**
     * Returns the work shift for the work event, creates a new shift if needed
     *
     * @param latestWorkEvent latest work event
     * @param workEventTime work event time
     * @param employeeId employee id
     * @return work shift
     */
    private suspend fun getWorkEventShift(
        latestWorkEvent: WorkEventEntity?,
        workEventTime: OffsetDateTime,
        employeeId: UUID
    ): WorkShiftEntity {
        return if (requiresNewShift(
                latestWorkEvent = latestWorkEvent,
                currentWorkEventTime = workEventTime
            )
        ) {
            if (latestWorkEvent?.workEventType == WorkEventType.UNKNOWN || latestWorkEvent?.workEventType == WorkEventType.BREAK) {
                latestWorkEvent.workEventType = WorkEventType.SHIFT_END
                workEventRepository.persistSuspending(latestWorkEvent)
            }

            workShiftController.create(
                employeeId = employeeId,
                date = workEventTime.toLocalDate()
            )
        } else {
            latestWorkEvent!!.workShift
        }

    }

    /**
     * Checks if a new shift is required based on the latest work event
     *
     * @param latestWorkEvent latest work event
     * @param currentWorkEventTime current work event time
     * @return true if a new shift is required, false otherwise
     */
    private fun requiresNewShift(
        latestWorkEvent: WorkEventEntity?,
        currentWorkEventTime: OffsetDateTime
    ): Boolean {
        if (latestWorkEvent == null ||
            latestWorkEvent.workEventType == WorkEventType.SHIFT_END
        ) {
            return true
        }

        if (latestWorkEvent.workEventType == WorkEventType.BREAK ||
            latestWorkEvent.workEventType == WorkEventType.UNKNOWN
        ) {
            if (Duration.between(latestWorkEvent.time, currentWorkEventTime).toHours().absoluteValue > 3) {
                return true
            }
        }

        return false
    }

}