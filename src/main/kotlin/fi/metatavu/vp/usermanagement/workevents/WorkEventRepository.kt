package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

@ApplicationScoped
class WorkEventRepository : AbstractRepository<WorkEventEntity, UUID>() {

    /**
     * Creates a new work event
     *
     * @param id id
     * @param employeeId employee id
     * @param startTime start time
     * @param workEventType work event type
     * @return created work event
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        startTime: OffsetDateTime,
        workEventType: WorkEventType
    ): WorkEventEntity {
        val workEventEntity = WorkEventEntity()
        workEventEntity.id = id
        workEventEntity.employeeId = employeeId
        workEventEntity.startTime = startTime
        workEventEntity.workEventType = workEventType

        return persistSuspending(workEventEntity)
    }

    /**
     * Lists work events
     *
     * @param employeeId employee id
     * @param start start time
     * @param first first
     * @param max max
     * @return pair of list of work events and count
     */
    suspend fun list(
        employeeId: UUID,
        start: OffsetDateTime?,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<WorkEventEntity>, Long> {
        val sb = StringBuilder()
        val parameters = Parameters()

        sb.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        if (start != null) {
            addCondition(sb, "startTime >= :start")
            parameters.and("start", start)
        }

        return queryWithCount(
            find(sb.toString(), Sort.descending("startTime"), parameters),
            first,
            max
        )
    }

}