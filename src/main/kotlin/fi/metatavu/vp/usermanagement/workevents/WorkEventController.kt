package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.api.model.WorkEvent
import fi.metatavu.vp.api.model.WorkEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for Work Events
 */
@ApplicationScoped
class WorkEventController {

    @Inject
    lateinit var workEventRepository: WorkEventRepository

    /**
     * Lists work events and sorts them by start time from new to old
     *
     * @param employeeId employee id
     * @param start start time
     * @param first first result
     * @param max max results
     * @return pair of work events and count
     */
    suspend fun list(
        employeeId: UUID,
        start: OffsetDateTime?,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<WorkEventEntity>, Long> {
        return workEventRepository.list(
            employeeId = employeeId,
            start = start,
            first = first,
            max = max
        )
    }

    /**
     * Creates a new work event
     *
     * @param employee employee
     * @param startTime start time
     * @param workEventType work event type
     * @return created work event
     */
    suspend fun create(
        employee: UserRepresentation,
        startTime: OffsetDateTime,
        workEventType: WorkEventType
    ): WorkEventEntity {
        return workEventRepository.create(
            id = UUID.randomUUID(),
            employeeId = UUID.fromString(employee.id),
            startTime = startTime,
            workEventType = workEventType
        )
    }

    /**
     * Finds work event
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
     * Updates work event
     *
     * @param foundWorkEvent found work event
     * @param workEvent work event
     * @return updated work event
     */
    suspend fun update(foundWorkEvent: WorkEventEntity, workEvent: WorkEvent): WorkEventEntity {
        foundWorkEvent.startTime = workEvent.startTime
        foundWorkEvent.workEventType = workEvent.workEventType

        return workEventRepository.persistSuspending(foundWorkEvent)
    }

    /**
     * Updates work event
     *
     * @param foundWorkEvent found work event
     * @param newStartTime new start time
     * @return updated time entry
     */
    suspend fun update(foundWorkEvent: WorkEventEntity, newStartTime: OffsetDateTime): WorkEventEntity {
        foundWorkEvent.startTime = newStartTime

        return workEventRepository.persistSuspending(foundWorkEvent)
    }

    /**
     * Deletes work event
     *
     * @param foundWorkEvent found work event
     */
    suspend fun delete(foundWorkEvent: WorkEventEntity) {
        workEventRepository.deleteSuspending(foundWorkEvent)
    }

}