package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.api.model.WorkEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for time entries
 */
@ApplicationScoped
class TimeEntryController {

    @Inject
    lateinit var timeEntryRepository: TimeEntryRepository

    /**
     * Lists time entries and sorts them by start time from new to old
     *
     * @param employeeId employee id
     * @param start start time
     * @param end end time
     * @param first first result
     * @param max max results
     * @return pair of time entries and count
     */
    suspend fun list(employeeId: UUID, start: OffsetDateTime?, end: OffsetDateTime?, first: Int? = null, max: Int? = null): Pair<List<TimeEntryEntity>, Long> {
        return timeEntryRepository.listEntries(employeeId, start, end, first, max)
    }

    /**
     * Creates a new time entry
     *
     * @param employee employee
     * @param startTime start time
     * @param workEventType work event type
     * @param endTime end time
     * @return created time entry
     */
    suspend fun create(
        employee: UserRepresentation,
        startTime: OffsetDateTime,
        workEventType: WorkEventType,
        endTime: OffsetDateTime? = null
    ): TimeEntryEntity {
        return timeEntryRepository.create(
            id = UUID.randomUUID(),
            employeeId = UUID.fromString(employee.id),
            startTime = startTime,
            workEventType = workEventType,
            endTime = endTime
        )
    }

    /**
     * Finds incomplete time entries
     *
     * @param employee employee
     * @return incomplete time entry or null if not found
     */
    suspend fun findIncompleteEntries(employee: UserRepresentation): TimeEntryEntity? {
        return timeEntryRepository.findIncomplete(UUID.fromString(employee.id))
    }

    /**
     * Finds overlapping time entry
     *
     * @param employee employee
     * @param timeEntry time entry
     * @return overlapping time entry or null if not found
     */
    suspend fun findOverlappingEntry(employee: UserRepresentation, timeEntry: TimeEntry): TimeEntryEntity? {
        val endTime = timeEntry.endTime ?: return null
        return timeEntryRepository.findOverlapping(UUID.fromString(employee.id), timeEntry.startTime, endTime)
    }

    /**
     * Finds time entry
     *
     * @param timeEntryId time entry id
     * @param employeeId employee id
     * @return time entry or null if not found
     */
    suspend fun find(timeEntryId: UUID, employeeId: UUID): TimeEntryEntity? {
        val found = timeEntryRepository.findByIdSuspending(timeEntryId)
        if (found?.employeeId == employeeId) {
            return found
        }
        return null
    }

    /**
     * Updates time entry
     *
     * @param foundTimeEntry found time entry
     * @param timeEntry time entry
     * @return updated time entry
     */
    suspend fun update(foundTimeEntry: TimeEntryEntity, timeEntry: TimeEntry): TimeEntryEntity {
        foundTimeEntry.startTime = timeEntry.startTime
        foundTimeEntry.endTime = timeEntry.endTime
        foundTimeEntry.workEventType = timeEntry.workEventType
        return timeEntryRepository.persistSuspending(foundTimeEntry)
    }

    /**
     * Updates time entry
     *
     * @param foundTimeEntry found time entry
     * @param newStartTime new start time
     * @param newEndTime new end time
     * @return updated time entry
     */
    suspend fun update(foundTimeEntry: TimeEntryEntity, newStartTime: OffsetDateTime, newEndTime: OffsetDateTime): TimeEntryEntity {
        foundTimeEntry.startTime = newStartTime
        foundTimeEntry.endTime = newEndTime
        return timeEntryRepository.persistSuspending(foundTimeEntry)
    }

    /**
     * Updates the end time of the time entry
     *
     * @param timeEntry time entry
     * @param newEndTime new end time
     * @return updated time entry
     */
    suspend fun updateEndTime(timeEntry: TimeEntryEntity, newEndTime: OffsetDateTime): TimeEntryEntity {
        timeEntry.endTime = newEndTime
        return timeEntryRepository.persistSuspending(timeEntry)
    }

    /**
     * Deletes time entry
     *
     * @param foundTimeEntry found time entry
     */
    suspend fun delete(foundTimeEntry: TimeEntryEntity) {
        timeEntryRepository.deleteSuspending(foundTimeEntry)
    }

}