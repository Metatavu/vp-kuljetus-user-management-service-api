package fi.metatavu.vp.usermanagement.workshifthours.workshifthourstasks

import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.panache.common.Parameters
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Repository for work shift tasks
 */
@ApplicationScoped
class WorkShiftTaskEntityRepository : AbstractRepository<WorkShiftTaskEntity, UUID>() {

    /**
     * Creates a new work shift task record
     *
     * @param id id
     * @param workShift work shift
     * @return created entity
     */
    suspend fun create(
        id: UUID,
        workShift: WorkShiftEntity
    ): WorkShiftTaskEntity {
        val new = WorkShiftTaskEntity()
        new.id = id
        new.workShiftId = workShift.id
        return persist(new).awaitSuspending()
    }

    /**
     * Lists work shift tasks
     *
     * @param start start
     * @param end end
     * @return work shift task list
     */
    suspend fun list(start: Int, end: Int): List<WorkShiftTaskEntity> {
        return findAll().range<WorkShiftTaskEntity>(start, end).list<WorkShiftTaskEntity>().awaitSuspending()
    }

    /**
     * Finds record for a work shift
     *
     * @param workShift work shift
     * @return work shift task
     */
    suspend fun findByWorkShift(workShift: WorkShiftEntity): WorkShiftTaskEntity? {
        return find(
            "workShiftId = :workShiftId",
            Parameters.with("workShiftId", workShift.id)
        ).firstResult<WorkShiftTaskEntity>().awaitSuspending()
    }
}