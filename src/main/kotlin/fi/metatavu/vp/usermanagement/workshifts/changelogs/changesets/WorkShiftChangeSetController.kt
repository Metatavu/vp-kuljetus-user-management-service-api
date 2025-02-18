package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changes.WorkShiftChangeController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * Controller for work shift change sets
 */
@ApplicationScoped
class WorkShiftChangeSetController {
    @Inject
    lateinit var workShiftChangeSetRepository: WorkShiftChangeSetRepository

    @Inject
    lateinit var workShiftChangeController: WorkShiftChangeController

    /**
     * Save a new change set to the database
     *
     * @param id
     * @param workShift
     * @param creatorId
     */
    suspend fun create(id: UUID, workShift: WorkShiftEntity, creatorId: UUID): WorkShiftChangeSetEntity {
        return workShiftChangeSetRepository.create(id, workShift, creatorId)
    }

    /**
     * Find a change set by id
     *
     * @param id
     */
    suspend fun find(id: UUID): WorkShiftChangeSetEntity? {
        return workShiftChangeSetRepository.findByIdSuspending(id)
    }

    /**
     * Lists all change sets belonging to a work shift
     *
     * @param workShift
     */
    suspend fun listByWorkShift(workShift: WorkShiftEntity): List<WorkShiftChangeSetEntity> {
        return workShiftChangeSetRepository.listByWorkShift(workShift)
    }

    /**
     * Delete a change set entity
     *
     * @param workShiftChangeSetEntity change set entity
     */
    suspend fun delete(workShiftChangeSetEntity: WorkShiftChangeSetEntity) {
        workShiftChangeController.listByChangeSet(workShiftChangeSetEntity).forEach {
            workShiftChangeController.delete(it)
        }

        workShiftChangeSetRepository.deleteSuspending(workShiftChangeSetEntity)
    }
}