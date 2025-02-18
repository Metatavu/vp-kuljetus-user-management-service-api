package fi.metatavu.vp.usermanagement.workshifts.changelogs.changes

import fi.metatavu.vp.usermanagement.model.WorkShiftChangeSet
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class WorkShiftChangeController {
    @Inject
    lateinit var workShiftChangeRepository: WorkShiftChangeRepository

    /**
     * Save work shift change to the database
     *
     * @param reason
     * @param creatorId
     * @param workShiftChangeSet
     * @param workShift
     * @param workShiftHours
     * @param workEvent
     * @param oldValue
     * @param newValue
     */
    suspend fun create(
        reason: String,
        creatorId: UUID,
        workShiftChangeSet: WorkShiftChangeSetEntity,
        workShift: WorkShiftEntity,
        workShiftHours: WorkShiftHoursEntity?,
        workEvent: WorkEventEntity?,
        oldValue: String?,
        newValue: String?
    ): WorkShiftChangeEntity {
        return workShiftChangeRepository.create(
            reason,
            creatorId,
            workShiftChangeSet,
            workShift,
            workShiftHours,
            workEvent,
            oldValue,
            newValue
        )
    }

    /**
     * List changes that belong to a change set
     * This will be used to build the change set REST entity
     *
     * @param workShiftChangeSet
     */
    suspend fun listByChangeSet(workShiftChangeSet: WorkShiftChangeSetEntity): List<WorkShiftChangeEntity> {
        return workShiftChangeRepository.listByChangeSet(workShiftChangeSet)
    }

    /**
     * Delete work shift change entity
     *
     * @param workShiftChangeEntity
     */
    suspend fun delete(workShiftChangeEntity: WorkShiftChangeEntity) {
        workShiftChangeRepository.deleteSuspending(workShiftChangeEntity)
    }
}