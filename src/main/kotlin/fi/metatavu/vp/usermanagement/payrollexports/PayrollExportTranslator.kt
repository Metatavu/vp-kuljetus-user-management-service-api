package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.vp.usermanagement.model.PayrollExport
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class PayrollExportTranslator: AbstractTranslator<PayrollExportEntity, PayrollExport>() {

    @Inject
    lateinit var workShiftController: WorkShiftController

    /**
     * Translates a PayrollExport from a database entity to a REST entity
     */
    override suspend fun translate(entity: PayrollExportEntity): PayrollExport {
        val workShiftIds = workShiftController.listEmployeeWorkShifts(
            employeeId = entity.employeeId,
            payrollExport = entity,
            startedAfter = null,
            startedBefore = null,
            dateAfter = null,
            dateBefore = null,
            first = null,
            max = null
        ).first.map { it.id }

        return PayrollExport(
            id = entity.id,
            employeeId = entity.employeeId,
            csvFileName = entity.fileName,
            exportedAt = entity.exportedAt,
            creatorId = entity.creatorId,
            workShiftIds = workShiftIds
        )
    }
}