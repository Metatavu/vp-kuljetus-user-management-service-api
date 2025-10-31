package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.hibernate.reactive.panache.Panache
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

        if (employeeWorkShift != null) {
            addCondition(sb, "workShift = :workShift")
            parameters.and("workShift", employeeWorkShift)
        }

        if (employeeId != null) {
            addCondition(sb, "employeeId = :employeeId")
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
     * Deletes consecutive duplicate work events (same type as previous) for a work shift using a native SQL query.
     *  - Step 1: Identify consecutive duplicates using a CTE and LAG()
     *  - Step 2: Delete all related workshiftchange entries for those events
     *  - Step 3: Delete the duplicate events themselves
     *
     * @param workShiftId work shift id
     * @return number of deleted duplicate work events
     */
    suspend fun deleteConsecutiveDuplicateEvents(workShiftId: UUID): Int {
        // Step 1: Build a common subquery that detects duplicates within one shift
        val duplicateEventsCte = """
            WITH duplicate_events AS (
                SELECT id
                FROM (
                    SELECT
                        id,
                        workshift_id,
                        workeventtype,
                        LAG(workeventtype) OVER (
                            PARTITION BY workshift_id
                            ORDER BY time ASC, createdat ASC, id ASC
                        ) AS previous_type
                    FROM workevent
                    WHERE workshift_id = :workShiftId
                ) ordered_events
                WHERE ordered_events.previous_type = ordered_events.workeventtype
            )
        """.trimIndent()

        // Step 2: Delete all dependent workshiftchange entries linked to duplicate events
        val deleteChangesSql = """
            $duplicateEventsCte
            DELETE FROM workshiftchange
            WHERE workevent_id IN (SELECT id FROM duplicate_events)
        """.trimIndent()

        // Step 3: Delete the duplicate work events themselves
        val deleteDuplicateEventsSql = """
            $duplicateEventsCte
            DELETE we
            FROM workevent we
            INNER JOIN duplicate_events de ON de.id = we.id
        """.trimIndent()

        val session = Panache.getSession().awaitSuspending()

        // First delete all dependent change records to avoid FK constraint issues
        val deleteChangesQuery = session.createNativeQuery<Any>(deleteChangesSql)
        deleteChangesQuery.setParameter("workShiftId", workShiftId)
        deleteChangesQuery.executeUpdate().awaitSuspending()

        // Then delete the duplicate work events
        val deleteDuplicatesQuery = session.createNativeQuery<Any>(deleteDuplicateEventsSql)
        deleteDuplicatesQuery.setParameter("workShiftId", workShiftId)
        val deletedEvents = deleteDuplicatesQuery.executeUpdate().awaitSuspending()

        return deletedEvents
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
        addCondition(sb, "workShift.endedAt is NULL and time < :time")

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
