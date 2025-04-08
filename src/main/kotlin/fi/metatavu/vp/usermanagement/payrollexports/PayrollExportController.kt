package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.apache.camel.Exchange
import java.time.OffsetDateTime
import java.util.*
import org.apache.camel.ProducerTemplate
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ApplicationScoped
class PayrollExportController {

    @Inject
    lateinit var payrollExportRepository: PayrollExportRepository

    @Inject
    lateinit var employeeWorkShiftController: WorkShiftController

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.address")
    lateinit var ftpAddress: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.username")
    lateinit var ftpUserName: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.password")
    lateinit var ftpUserPassword: String

    /**
     * Saves a reference about a payroll export to the database, so that exports can be listed from the UI.
     * Reference is also linked to work shifts that are contained in the payroll expor.
     * This is done after exporting.
     *
     * @param employeeId employee id
     * @param fileName file name
     * @param creatorId creator id
     */
    suspend fun save(
        employeeId: UUID,
        fileName: String,
        workShifts: List<WorkShiftEntity>,
        exportedAt: OffsetDateTime,
        creatorId: UUID,
    ): PayrollExportEntity {
        val export = payrollExportRepository.create(
            employeeId = employeeId,
            fileName = fileName,
            exportedAt = exportedAt,
            creatorId = creatorId
        )

        workShifts.forEach {
            employeeWorkShiftController.setPayrollExport(
                workShift = it,
                payrollExport= export
            )
        }

        return export
    }

    /**
     * Retrieves payroll exports from the database with the given filters.
     *
     * @param employeeId
     * @param exportedAfter
     * @param exportedBefore
     * @param first
     * @param max
     */
    suspend fun list(
        employeeId: UUID?,
        exportedAfter: OffsetDateTime?,
        exportedBefore: OffsetDateTime?,
        first: Int?,
        max: Int?,
    ): List<PayrollExportEntity> {
        return payrollExportRepository.list(
            employeeId = employeeId,
            exportedAfter = exportedAfter,
            exportedBefore = exportedBefore,
            first = first,
            max = max
        ).first
    }

    /**
     * Retrieves a payroll export by id if exists.
     *
     * @param id
     */
    suspend fun find(id: UUID): PayrollExportEntity? {
        return payrollExportRepository.findByIdSuspending(id)
    }

    /**
     * Deletes a payroll export
     *
     * @param payrollExport
     */
    suspend fun delete(payrollExport: PayrollExportEntity) {
        employeeWorkShiftController.listEmployeeWorkShifts(
            employeeId = payrollExport.employeeId,
            payrollExport = payrollExport,
            startedAfter = null,
            startedBefore = null,
            dateAfter = null,
            dateBefore = null,
            first = null,
            max = null
        ).first.forEach {
            employeeWorkShiftController.setPayrollExport(
                workShift = it,
                payrollExport = null
            )
        }
        payrollExportRepository.deleteSuspending(payrollExport)
    }

    /**
     * Builds a payroll file and sends it through FTP to a server defined by environment variables.
     *
     * @param workShifts
     * @param employee
     * @param fileName
     */
    suspend fun exportPayrollFile(
        workShifts: List<WorkShiftEntity>,
        employee: UserRepresentation,
        fileName: String
    ) {
        val employeeNumber = employee.attributes!!["employeeNumber"]!!.first()

        // TODO: Write actual export logic, next variable is temporary for testing
        val testContent = workShifts.joinToString(separator = "") { workShift ->
            buildPayrollExportRow(
                date = workShift.date,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}",
                salaryTypeNumber = 1,
                hoursWorked = 8
            )
        }

        producerTemplate.sendBodyAndHeader(
            "sftp://$ftpUserName@$ftpAddress?password=$ftpUserPassword",
            testContent,
            Exchange.FILE_NAME,
            fileName
        )
    }

    /**
     * Builds a single row for an exported payroll.
     * Work is split into salary types.
     * Each row represents work done for a specific salary type on a given day.
     *
     * @param date
     * @param employeeNumber
     * @param employeeName
     * @param salaryTypeNumber
     * @param hoursWorked
     */
    private fun buildPayrollExportRow(
        date: LocalDate,
        employeeNumber: String,
        employeeName: String,
        salaryTypeNumber: Int,
        hoursWorked: Int
    ): String {
        val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString()
        return "$formattedDate;$employeeNumber;$employeeName;$salaryTypeNumber;$hoursWorked;6;7;8;9;10\n"
    }
}