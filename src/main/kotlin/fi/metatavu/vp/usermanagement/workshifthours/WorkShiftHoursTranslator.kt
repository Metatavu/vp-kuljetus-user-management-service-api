package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkShiftHours
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for WorkShiftHours
 */
@ApplicationScoped
class WorkShiftHoursTranslator: AbstractTranslator<WorkShiftHoursEntity, WorkShiftHours>() {

    override suspend fun translate(entity: WorkShiftHoursEntity): WorkShiftHours {
        return WorkShiftHours(
            id = entity.id,
            employeeId = entity.workShift.employeeId,
            actualHours = entity.actualHours,
            employeeWorkShiftId = entity.workShift.id,
            calculatedHours = entity.calculatedHours,
            workType = entity.workType
        )
    }
}