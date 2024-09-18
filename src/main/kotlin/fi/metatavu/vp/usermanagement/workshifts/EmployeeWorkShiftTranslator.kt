package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for EmployeeWorkShift
 */
@ApplicationScoped
class EmployeeWorkShiftTranslator: AbstractTranslator<EmployeeWorkShiftEntity, EmployeeWorkShift>() {
    override suspend fun translate(entity: EmployeeWorkShiftEntity): EmployeeWorkShift {
        return EmployeeWorkShift(
            id = entity.id,
            employeeId = entity.employeeId,
            date = entity.date,
            approved = entity.approved
        )
    }

}