package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.usermanagement.worktypes.WorkTypeEntity
import io.quarkus.panache.common.Parameters
import io.smallrye.mutiny.coroutines.awaitSuspending
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
     * Lists time entries by work type
     *
     * @param workType work type
     * @return count of time entries
     */
    suspend fun listByWorkType(workType: WorkTypeEntity): Long {
        return timeEntryRepository.find("workType = :workType", Parameters.with("workType", workType)).count().awaitSuspending()
    }

    /**
     * Creates a new time entry
     *
     * @param employee employee
     * @param workType work type
     * @param timeEntry time entry
     * @return created time entry
     */
    suspend fun create(employee: UserRepresentation, workType: WorkTypeEntity, timeEntry: TimeEntry): TimeEntryEntity {
        return timeEntryRepository.create(
            id = UUID.randomUUID(),
            employeeId = UUID.fromString(employee.id),
            startTime = timeEntry.startTime,
            workType = workType,
            endTime = timeEntry.endTime
        )
    }

    /**
     * Validates time entry
     *
     * @param employee employee
     * @param timeEntry time entry
     * @return error message if validation fails
     */
    suspend fun validateIncompleteEntries(employee: UserRepresentation, timeEntry: TimeEntry): String? {
        val existingEntry = timeEntryRepository.findIncomplete(UUID.fromString(employee.id))
        if (existingEntry != null) {
            return("User has an incomplete time entry")
        }

        return null
    }

    /**
     * Validates time entry update
     *
     * @param employee employee
     * @param timeEntry time entry
     * @return error message if validation fails
     */
    suspend fun validateOverlappingEntries(employee: UserRepresentation, timeEntry: TimeEntry): String? {
        val overlappingEntry = timeEntryRepository.findOverlapping(UUID.fromString(employee.id), timeEntry.startTime, timeEntry.endTime)
        if (overlappingEntry != null && overlappingEntry.id != timeEntry.id) {
            return("Time entry overlaps with another time entry")
        }
        return null
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
     * @param newWorkType new work type
     * @param timeEntry time entry
     * @return updated time entry
     */
    suspend fun update(foundTimeEntry: TimeEntryEntity, newWorkType: WorkTypeEntity, timeEntry: TimeEntry): TimeEntryEntity {
        foundTimeEntry.workType = newWorkType
        foundTimeEntry.startTime = timeEntry.startTime
        foundTimeEntry.endTime = timeEntry.endTime
        return timeEntryRepository.persistSuspending(foundTimeEntry)
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