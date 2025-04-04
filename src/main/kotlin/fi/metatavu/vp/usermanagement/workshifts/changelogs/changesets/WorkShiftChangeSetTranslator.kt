package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.model.WorkShiftChange
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeSet
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changes.WorkShiftChangeController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class WorkShiftChangeSetTranslator: AbstractTranslator<WorkShiftChangeSetEntity, WorkShiftChangeSet>() {
    @Inject
    lateinit var workShiftChangeController: WorkShiftChangeController

    override suspend fun translate(entity: WorkShiftChangeSetEntity): WorkShiftChangeSet {
        return WorkShiftChangeSet(
            id = entity.id,
            createdAt = entity.createdAt,
            creatorId = entity.creatorId,
            propertyEntries = workShiftChangeController.listByChangeSet(entity).map {
                WorkShiftChange(
                    reason = WorkShiftChangeReason.valueOf(it.reason),
                    workShiftId = it.workShift.id,
                    workShiftHourId = it.workShiftHour?.id,
                    workEventId = it.workEvent?.id,
                    oldValue = it.oldValue,
                    newValue = it.newValue,
                    createdAt = it.createdAt
                )
            }
        )
    }
}