package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

/**
 * Repository for work events
 */
@ApplicationScoped
class WorkEventRepository : AbstractRepository<WorkEventEntity, UUID>() {

    /**
     * Creates a new work event
     *
     * @param id id
     * @param employeeId employee id
     * @param time time
     * @param workEventType work event type
     * @param workShiftEntity work shift entity
     * @param costCenter cost center
     * @param truckId truck id
     * @return created work event
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        time: OffsetDateTime,
        workEventType: WorkEventType,
        workShiftEntity: WorkShiftEntity,
        costCenter: String?,
        truckId: UUID? = null
    ): WorkEventEntity {
        val workEventEntity = WorkEventEntity()
        workEventEntity.id = id
        workEventEntity.employeeId = employeeId
        workEventEntity.time = time
        workEventEntity.workEventType = workEventType
        workEventEntity.workShift = workShiftEntity
        workEventEntity.costCenter = costCenter
        workEventEntity.truckId = truckId
        return persistSuspending(workEventEntity)
    }

    /**
     * Lists work events
     *
     * @param employeeId employee id
     * @param employeeWorkShift employee work shift
     * @param after after this time
     * @param before before this time
     * @param first first
     * @param max max
     * @return pair of list of work events and count
     */
    suspend fun list(
        employeeId: UUID? = null,
        employeeWorkShift: WorkShiftEntity? = null,
        after: OffsetDateTime? = null,
        before: OffsetDateTime? = null,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<WorkEventEntity>, Long> {
        val sb = StringBuilder()
        val parameters = Parameters()

        if (employeeId != null) {
            sb.append("employeeId = :employeeId")
            parameters.and("employeeId", employeeId)
        }

        if (after != null) {
            addCondition(sb, "time >= :after")
            parameters.and("after", after)
        }

        if (before != null) {
            addCondition(sb, "time <= :before")
            parameters.and("before", before)
        }

        if (employeeWorkShift != null) {
            addCondition(sb, "workShift = :workShift")
            parameters.and("workShift", employeeWorkShift)
        }

        sb.append(" ORDER by time DESC, CASE workEventType WHEN 'SHIFT_START' THEN 1 ")
        sb.append("WHEN 'DRIVER_CARD_INSERTED' THEN 2 ")
        sb.append("WHEN 'LOGIN' THEN 3 ")
        sb.append("WHEN 'LOGOUT' THEN 5 ")
        sb.append("WHEN 'DRIVER_CARD_REMOVED' THEN 6 ")
        sb.append("WHEN 'SHIFT_END' THEN 7 ")
        sb.append("ELSE 4 END")

        return queryWithCount(
            find(sb.toString(), parameters),
            first,
            max
        )
    }

    /**
     * Finds latest work event for the user
     *
     * @param employeeId employee id
     * @return latest work event
     */
    suspend fun findLatestWorkEvent(employeeId: UUID?, time: OffsetDateTime): WorkEventEntity? {
        return find(
            "employeeId = :employeeId and time < :time order by time desc limit 1",
            Parameters.with("employeeId", employeeId).and("time", time),
        ).firstResult<WorkEventEntity>().awaitSuspending()
    }

    /**
     * List shift ending events
     */
    suspend fun listShiftEndingEvents(): List<WorkEventEntity> {
        val sb = StringBuilder()
        val parameters = Parameters.with("time", OffsetDateTime.now().minusHours(5))
        addCondition(sb, "workShift.endedAt is NULL and time < :time");

        return find(sb.toString(), Sort.descending("time"), parameters).list<WorkEventEntity>().awaitSuspending()
    }

    /**
     * List shift ending break events
     */
    suspend fun listShiftEndingBreakEvents(): List<WorkEventEntity> {
        return find(
            "workShift.endedAt is NULL and workEventType = 'BREAK' and time < :time order by time desc limit 1",
            Parameters.with("time", OffsetDateTime.now().minusHours(3))
        ).list<WorkEventEntity>().awaitSuspending()
    }

    /**
     * Updates work event type
     *
     * @param latestWorkEvent latest work event
     * @param shiftEnd new work event type
     * @return updated work event
     */
    suspend fun updateEventType(latestWorkEvent: WorkEventEntity, shiftEnd: WorkEventType): WorkEventEntity {
        latestWorkEvent.workEventType = shiftEnd
        return persistSuspending(latestWorkEvent)
    }

}