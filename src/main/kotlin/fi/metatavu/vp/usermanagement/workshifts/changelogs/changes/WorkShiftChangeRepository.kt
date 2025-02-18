package fi.metatavu.vp.usermanagement.workshifts.changelogs.changes

import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetEntity
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Database operations for work shift changes
 */
@ApplicationScoped
class WorkShiftChangeRepository: AbstractRepository<WorkShiftChangeEntity, UUID>() {
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
        val workShiftChange = WorkShiftChangeEntity()
        workShiftChange.id = UUID.randomUUID()
        workShiftChange.reason = reason
        workShiftChange.creatorId = creatorId
        workShiftChange.workShiftChangeSet = workShiftChangeSet
        workShiftChange.workShift = workShift
        workShiftChange.workShiftHours = workShiftHours
        workShiftChange.workEvent = workEvent
        workShiftChange.oldValue = oldValue
        workShiftChange.newValue = newValue

        return persistSuspending(workShiftChange)
    }
}