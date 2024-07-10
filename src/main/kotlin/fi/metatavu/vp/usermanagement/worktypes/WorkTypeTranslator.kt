package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkType
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for WorkType
 */
@ApplicationScoped
class WorkTypeTranslator : AbstractTranslator<WorkTypeEntity, WorkType>() {
    override suspend fun translate(entity: WorkTypeEntity): WorkType {
        return WorkType(
            id = entity.id,
            name = entity.name,
            category = entity.category
        )
    }
}