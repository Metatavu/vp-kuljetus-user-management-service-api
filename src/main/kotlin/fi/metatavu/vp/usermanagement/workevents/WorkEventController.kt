package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftRepository
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changes.WorkShiftChangeRepository
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
    lateinit var workShiftChangeRepository: WorkShiftChangeRepository

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
     * sets the work shift startedAt or endedAt,
     * creates new required work events (start and end)
     *
     * @param employeeId employee
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
    val employeeId = UUID.fromString(employee.id!!)
        val latestWorkEvent = workEventRepository.findLatestWorkEvent(employeeId = employeeId, time = time)
        val latestWorkShift = workShiftRepository.findLatestEmployeeWorkShift(
            employeeId = employeeId,
            beforeWorkEventTime = time
        )

        val workShift = getWorkEventShift(latestWorkShift, latestWorkEvent, workEventType, time, employeeId)
        val createdWorkEvent = workEventRepository.create(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            time = time,
            workEventType = workEventType,
            workShiftEntity = workShift,
            truckId = truckId,
            costCenter = null
        )

        val updatedShift = recalculateWorkShiftTimes(workShift = workShift)
        workShiftHoursController.recalculateWorkShiftHours(
            workShift = updatedShift,
        )

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
        foundWorkEvent.costCenter = workEvent.costCenter
        val updated = workEventRepository.persistSuspending(foundWorkEvent)

        val updatedShift = recalculateWorkShiftTimes(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedShift)
        return updated
    }

    /**
     * Ends a work shift with the given work event
     *
     * @param workEvent
     */
    suspend fun changeToWorkShiftEnd(workEvent: WorkEventEntity) {
        workEvent.workEventType = WorkEventType.SHIFT_END
        workEventRepository.persistSuspending(workEvent)
        val updatedShift = recalculateWorkShiftTimes(workShift = workEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedShift)
    }

    /**
     * Deletes work event, removes the work shift if it was the last event for it,
     * and recalculates the work shift date and work shift hours if needed.
     *
     * @param foundWorkEvent found work event
     * @param recalculate whether to recalculate the work shift date and hours
     */
    suspend fun delete(foundWorkEvent: WorkEventEntity, recalculate: Boolean = true) {
        workShiftChangeRepository.listByWorkEvent(foundWorkEvent).forEach {
            workShiftChangeRepository.deleteSuspending(it)
        }

        workEventRepository.deleteSuspending(foundWorkEvent)

        if (workEventRepository.list(employeeWorkShift = foundWorkEvent.workShift).first.isEmpty()) {
            val foundShift = workShiftRepository.findByIdSuspending(foundWorkEvent.workShift.id) ?: return
            workShiftController.deleteEmployeeWorkShift(foundShift)
            return
        }

        if (!recalculate) return

        val updatedWorkShift = recalculateWorkShiftTimes(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = updatedWorkShift)
    }

    /**
     * Deletes work event without any side effects
     *
     * @param foundWorkEvent found work event
     */
    suspend fun deleteWithNoSideEffects(foundWorkEvent: WorkEventEntity) {
        workEventRepository.deleteSuspending(foundWorkEvent)
    }

    /**
     * Returns the shift start and end events for the work event's work shift (excluding the work event itself)
     *
     * @param shiftEvents work events
     * @return pair of shift start and end events
     */
    suspend fun getShiftStartEnd(shiftEvents: List<WorkEventEntity>): Pair<OffsetDateTime?, OffsetDateTime?> {
        val shiftStart = shiftEvents.find { it.workEventType == WorkEventType.SHIFT_START }?.time
        val shiftEnd = shiftEvents.find { it.workEventType == WorkEventType.SHIFT_END }?.time
        return Pair(shiftStart, shiftEnd)
    }

    /**
     * Checks if the time is within the shift bounds
     *
     * @param shiftStart shift start event
     * @param shiftEnd shift end event
     * @param time time to be checked
     * @return true if the time is within the shift bounds, false otherwise
     */
    fun isWithinShiftBounds(shiftStart: OffsetDateTime?, shiftEnd: OffsetDateTime?, time: OffsetDateTime): Boolean {
        val isAfterShiftStart = shiftStart == null || time.isAfter(shiftStart)
        val isBeforeShiftEnd = shiftEnd == null || time.isBefore(shiftEnd)
        return isAfterShiftStart && isBeforeShiftEnd
    }

    /**
     * Checks if the work event is a duplicate start or end event
     *
     * @param shiftStart shift start event
     * @param shiftEnd shift end event
     * @param workEventType work event type to be compared
     * @return true if the work event is a duplicate start or end event, false otherwise
     */
    fun isDuplicateStartOrEndEvent(
        shiftStart: OffsetDateTime?,
        shiftEnd: OffsetDateTime?,
        workEventType: WorkEventType
    ): Boolean {
        return (shiftStart != null && workEventType == WorkEventType.SHIFT_START) ||
            (shiftEnd != null && workEventType == WorkEventType.SHIFT_END)
    }

    /**
     * Checks if a new shift is required based on the latest work event
     *
     * @param latestWorkEvent latest work event
     * @param currentWorkEventTime current work event time
     * @return true if a new shift is required, false otherwise
     */
    suspend fun oldShiftIsEnded(
        latestWorkEvent: WorkEventEntity?,
        currentWorkEventTime: OffsetDateTime,
        latestWorkShiftEntity: WorkShiftEntity?
    ): Boolean {
        if (latestWorkEvent == null || latestWorkEvent.workEventType == WorkEventType.SHIFT_END) {
            return true
        }

        if (latestWorkEvent.workEventType == WorkEventType.BREAK || latestWorkEvent.workEventType == WorkEventType.UNKNOWN) {
            if (Duration.between(latestWorkEvent.time, currentWorkEventTime).toHours().absoluteValue > 3) {
                return true
            }
        }

        return false
    }

    /**
     * Recalculates the work shift date/start/end times based on its work events.
     * Finds the first work event in the shift and updates the shift date if needed
     *
     * @param workShift work shift
     */
    suspend fun recalculateWorkShiftTimes(
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
                workShift.startedAt = workEvent.time
            } else if (workEvent.workEventType == WorkEventType.SHIFT_END) {
                workShift.endedAt = workEvent.time
            }
        }

        return workShiftRepository.persistSuspending(workShift)
    }

    /**
     * Returns the work shift for the work event.  Creates a new shift (if needed) or selects a suitable running shift
     *  and updates/creates other work events related to shift starting and ending.
     *
     * @param latestWorkEvent latest work event
     * @param workEventType work event type
     * @param workEventTime work event time
     * @param employeeId employee id
     * @return work shift
     */
    private suspend fun getWorkEventShift(
        latestWorkShiftEntity: WorkShiftEntity?,
        latestWorkEvent: WorkEventEntity?,
        workEventType: WorkEventType,
        workEventTime: OffsetDateTime,
        employeeId: UUID
    ): WorkShiftEntity {
        val oldShiftIsEnded = oldShiftIsEnded(
            latestWorkEvent = latestWorkEvent,
            currentWorkEventTime = workEventTime,
            latestWorkShiftEntity = latestWorkShiftEntity
        )

        return if (oldShiftIsEnded) {
            if (latestWorkEvent != null && latestWorkEvent.workEventType != WorkEventType.SHIFT_END) {
                //if not ended with end event then do it properly
                workEventRepository.updateEventType(latestWorkEvent, WorkEventType.SHIFT_END)
                recalculateWorkShiftTimes(workShift = latestWorkEvent.workShift)
            }

            val existingEmptyShift = workShiftRepository.findSameDayWorkShift(employeeId, workEventTime)
            val newShift: WorkShiftEntity =
                if (existingEmptyShift != null && workEventRepository.list(employeeWorkShift = existingEmptyShift).first.isEmpty()) {
                    existingEmptyShift
                } else {
                    workShiftController.create(
                        employeeId = employeeId,
                        date = workEventTime.toLocalDate()
                    )
                }

            if (workEventType != WorkEventType.SHIFT_START) {
                workEventRepository.create(
                    id = UUID.randomUUID(),
                    workShiftEntity = newShift,
                    employeeId = employeeId,
                    workEventType = WorkEventType.SHIFT_START,
                    time = workEventTime.minusSeconds(1),
                    costCenter = null
                )
            }
            newShift
        } else {
            latestWorkEvent!!.workShift
        }
    }
}