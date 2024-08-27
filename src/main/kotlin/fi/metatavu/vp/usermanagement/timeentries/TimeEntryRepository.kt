package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.api.model.WorkEventType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

@ApplicationScoped
class TimeEntryRepository : AbstractRepository<TimeEntryEntity, UUID>() {

    /**
     * Creates a new time entry
     *
     * @param id id
     * @param employeeId employee id
     * @param startTime start time
     * @param workEventType work event type
     * @param endTime end time
     * @return created time entry
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        startTime: OffsetDateTime,
        workEventType: WorkEventType,
        endTime: OffsetDateTime?
    ): TimeEntryEntity {
        val timeEntryEntity = TimeEntryEntity()
        timeEntryEntity.id = id
        timeEntryEntity.employeeId = employeeId
        timeEntryEntity.startTime = startTime
        timeEntryEntity.workEventType = workEventType
        timeEntryEntity.endTime = endTime
        return persistSuspending(timeEntryEntity)
    }

    /**
     * Finds incomplete time entry
     *
     * @param employeeId employee id
     * @return incomplete time entry
     */
    suspend fun findIncomplete(employeeId: UUID?): TimeEntryEntity? {
        return find(
            "employeeId = :employeeId and endTime is NULL order by startTime desc limit 1",
            Parameters().and("employeeId", employeeId)
        ).firstResult<TimeEntryEntity>().awaitSuspending()
    }

    /**
     * Finds overlapping time entries
     *
     * @param employeeId employee id
     * @param startTime start time
     * @param endTime end time
     * @return overlapping time entry
     */
    suspend fun findOverlapping(
        employeeId: UUID?,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ): TimeEntryEntity? {
        return find("employeeId = :employeeId AND " +
                "endTime > :startTime AND " +
                "startTime < :endTime " +
                "order by startTime desc limit 1",
            Parameters()
                .and("employeeId", employeeId)
                .and("startTime", startTime)
                .and("endTime", endTime)
        ).firstResult<TimeEntryEntity>().awaitSuspending()
    }

    /**
     * Lists time entries
     *
     * @param employeeId employee id
     * @param start start time
     * @param end end time
     * @param first first
     * @param max max
     * @return pair of list of time entries and count
     */
    suspend fun listEntries(
        employeeId: UUID,
        start: OffsetDateTime?,
        end: OffsetDateTime?,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<TimeEntryEntity>, Long> {
        val sb = StringBuilder()
        val parameters = Parameters()

        sb.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        if (start != null) {
            addCondition(sb, "startTime >= :start")
            parameters.and("start", start)
        }

        if (end != null) {
            addCondition(sb, "endTime <= :end")
            parameters.and("end", end)
        }

        return queryWithCount(
            find(sb.toString(), Sort.descending("endTime"), parameters),
            first,
            max
        )
    }

}