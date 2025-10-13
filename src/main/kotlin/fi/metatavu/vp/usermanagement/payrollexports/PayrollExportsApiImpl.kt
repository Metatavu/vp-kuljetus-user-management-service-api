package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.vp.usermanagement.model.PayrollExport
import fi.metatavu.vp.usermanagement.model.SalaryGroup
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.PayrollExportsApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.users.UserController.Companion.SALARY_GROUP_ATTRIBUTE
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@RequestScoped
@WithSession
class PayrollExportsApiImpl: PayrollExportsApi, AbstractApi() {
    @Inject
    lateinit var payrollExportController: PayrollExportController

    @Inject
    lateinit var payrollExportTranslator: PayrollExportTranslator

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var userController: UserController

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun createPayrollExport(payrollExport: PayrollExport): Uni<Response> = withCoroutineScope(60000L) {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val employee = userController.find(
            id = payrollExport.employeeId
        ) ?: return@withCoroutineScope createBadRequest("Employee ${payrollExport.employeeId} does not exist")

        val workShifts = payrollExport.workShiftIds.map {
            workShiftController.findEmployeeWorkShift(employeeId = payrollExport.employeeId, shiftId = it)
                ?: return@withCoroutineScope createBadRequest("Work shift $it not found")
        }

        workShifts.forEach {
            if (it.payrollExport != null) {
                return@withCoroutineScope createBadRequest("Work shift ${it.id} already has a payroll export")
            }

            if (!it.approved) {
                return@withCoroutineScope createBadRequest("Work shift ${it.id} must be approved before including it in a payroll export")
            }

            if (it.employeeId != payrollExport.employeeId) {
                return@withCoroutineScope createBadRequest("Work shift ${it.id} does not belong to employee ${payrollExport.employeeId}")
            }
        }

        val exportTime = OffsetDateTime.now()
        val exportId = UUID.randomUUID()
        val salaryGroup = SalaryGroup.valueOf(employee.attributes!![SALARY_GROUP_ATTRIBUTE]!!.first())
        val fileTitlePrefix = when (salaryGroup) {
            SalaryGroup.TERMINAL -> "13165TERM"
            SalaryGroup.DRIVER -> "13165KULJ"
            SalaryGroup.VPLOGISTICS -> "13166TP"
            SalaryGroup.OFFICE -> "13165KK"
        }

        val formattedExportTime = exportTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")).toString()
        val fileName = "${fileTitlePrefix}_TAPA_$formattedExportTime.csv"

        try {
            payrollExportController.exportPayrollFile(
                workShifts = workShifts,
                employee = employee,
                fileName = fileName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withCoroutineScope createInternalServerError("Payroll export failed: ${e.message}")
        }

        createOk(payrollExportTranslator.translate(payrollExportController.save(
            exportId = exportId,
            employeeId = payrollExport.employeeId,
            fileName = fileName,
            creatorId = loggedUserId!!,
            exportedAt = exportTime,
            workShifts = workShifts
        )))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun findPayrollExport(payrollExportId: UUID): Uni<Response> = withCoroutineScope {
        val found = payrollExportController.find(payrollExportId)
            ?: return@withCoroutineScope createNotFound("Payroll export $payrollExportId not found")

        createOk(payrollExportTranslator.translate(found))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun listPayrollExports(
        employeeId: UUID?,
        exportedAfter: OffsetDateTime?,
        exportedBefore: OffsetDateTime?,
        first: Int,
        max: Int,
    ): Uni<Response> = withCoroutineScope {
        val payrollExports = payrollExportController.list(
            employeeId = employeeId,
            exportedAfter = exportedAfter,
            exportedBefore = exportedBefore,
            first = first,
            max = max
        )

        createOk(payrollExports.map {
            payrollExportTranslator.translate(it)
        })
    }
}