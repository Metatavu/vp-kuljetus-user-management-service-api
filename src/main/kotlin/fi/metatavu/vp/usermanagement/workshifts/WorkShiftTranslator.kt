package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Translator for EmployeeWorkShift
 */
@ApplicationScoped
class WorkShiftTranslator: AbstractTranslator<WorkShiftEntity, EmployeeWorkShift>() {

    @Inject
    lateinit var workEventController: WorkEventController

    override suspend fun translate(entity: WorkShiftEntity): EmployeeWorkShift {
        return EmployeeWorkShift(
            id = entity.id,
            employeeId = entity.employeeId,
            date = entity.date,
            approved = entity.approved,
            absence = entity.absence,
            perDiemAllowance = entity.perDiemAllowance,
            truckIds = workEventController.list(employeeWorkShift = entity).first.mapNotNull { it.truckId }.distinct()
        )
    }

}