package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.api.model.WorkEvent
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for WorkEvent
 */
@ApplicationScoped
class WorkEventTranslator : AbstractTranslator<WorkEventEntity, WorkEvent>() {
    override suspend fun translate(entity: WorkEventEntity): WorkEvent =
        WorkEvent(
            id = entity.id,
            employeeId = entity.employeeId,
            startTime = entity.startTime,
            workEventType = entity.workEventType
        )
}