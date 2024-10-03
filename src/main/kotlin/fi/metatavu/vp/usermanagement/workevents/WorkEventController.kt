package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
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
     * recalculates the work shift hours
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
        val latestWorkEvent = workEventRepository.findLatestWorkEvent(employeeId = UUID.fromString(employee.id))

        val workShift = if (requiresNewShift(
            latestWorkEvent = latestWorkEvent,
            currentWorkEventTime = time
        )) {
            workShiftController.create(
                employeeId = UUID.fromString(employee.id),
                date = time.toLocalDate()
            )
        } else {
            latestWorkEvent!!.workShift     // latestWorkEvent is not null because of check in requiresNewShift
        }

        val created = workEventRepository.create(
            id = UUID.randomUUID(),
            employeeId = UUID.fromString(employee.id),
            time = time,
            workEventType = workEventType,
            workShiftEntity = workShift
        )

        recalculateWorkShiftDate(workShift = workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = workShift)
        return created
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
    suspend fun update(foundWorkEvent: WorkEventEntity, workEvent: WorkEvent): WorkEventEntity {
        foundWorkEvent.time = workEvent.time
        foundWorkEvent.workEventType = workEvent.workEventType
        val updated = workEventRepository.persistSuspending(foundWorkEvent)
        recalculateWorkShiftDate(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = foundWorkEvent.workShift)
        return updated
    }

    /**
     * Updates work event, recalculates work shift date if needed
     *
     * @param foundWorkEvent found work event
     * @param newTime new time
     * @return updated time entry
     */
    suspend fun update(foundWorkEvent: WorkEventEntity, newTime: OffsetDateTime): WorkEventEntity {
        foundWorkEvent.time = newTime

        val updated = workEventRepository.persistSuspending(foundWorkEvent)
        recalculateWorkShiftDate(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = foundWorkEvent.workShift)
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

        recalculateWorkShiftDate(workShift = foundWorkEvent.workShift)
        workShiftHoursController.recalculateWorkShiftHours(workShift = foundWorkEvent.workShift)
    }

    /**
     * Recalculates the work shift date based on the earliest work event.
     * Finds the first work event in the shift and updates the shift date if needed
     *
     * @param workShift work shift
     */
    private suspend fun recalculateWorkShiftDate(
        workShift: WorkShiftEntity,
    ) {
        workEventRepository.findEarliestWorkEvent(workShift)?.let {
            if (workShift.date != it.time.toLocalDate()) {
                workShiftController.updateEmployeeWorkShift(workShift, it.time.toLocalDate())
            }
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
        ) return true

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