package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Database operations for work shift change sets
 */
@ApplicationScoped
class WorkShiftChangeSetRepository: AbstractRepository<WorkShiftChangeSetEntity, UUID>() {

    /**
     * Save a new change set to the database
     *
     * @param id
     * @param workShift
     * @param creatorId
     */
    suspend fun create(id: UUID, workShift: WorkShiftEntity, creatorId: UUID): WorkShiftChangeSetEntity {
        val workShiftChangeSetEntity = WorkShiftChangeSetEntity()
        workShiftChangeSetEntity.id = id
        workShiftChangeSetEntity.workShift = workShift
        workShiftChangeSetEntity.creatorId = creatorId
        return persistSuspending(workShiftChangeSetEntity)
    }

    /**
     * Lists all change sets belonging to a work shift
     *
     * @param workShift
     */
    suspend fun listByWorkShift(workShift: WorkShiftEntity): List<WorkShiftChangeSetEntity> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        addCondition(queryBuilder, "workShift = :workShift")
        parameters.and("workShift", workShift)

        return list(queryBuilder.toString(), parameters).awaitSuspending()
    }
}