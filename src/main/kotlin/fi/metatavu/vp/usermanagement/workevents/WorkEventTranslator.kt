package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEvent
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
            time = entity.time,
            workEventType = entity.workEventType,
            employeeWorkShiftId = entity.workShift.id,
            truckId = entity.truckId,
            costCenter = entity.costCenter
        )
}