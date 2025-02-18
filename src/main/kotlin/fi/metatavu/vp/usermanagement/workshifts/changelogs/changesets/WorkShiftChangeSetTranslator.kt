package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.model.WorkShiftChangeSet
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class WorkShiftChangeSetTranslator: AbstractTranslator<WorkShiftChangeSetEntity, WorkShiftChangeSet>() {
    override suspend fun translate(entity: WorkShiftChangeSetEntity): WorkShiftChangeSet {
        return WorkShiftChangeSet(
            id = entity.id,
            createdAt = entity.createdAt,
            creatorId = entity.creatorId,
            propertyEntries = listOf()
        )
    }
}