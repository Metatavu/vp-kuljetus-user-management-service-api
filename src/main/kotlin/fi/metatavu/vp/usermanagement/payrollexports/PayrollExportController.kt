package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
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
    lateinit var workShiftHoursController: WorkShiftHoursController

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
     * Reference is also linked to work shifts that are contained in the payroll export.
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

        val workShiftsGroupedByDate = workShifts.sortedBy { it.date }.groupBy { it.date }
        val fileContent = workShiftsGroupedByDate.map {
            buildPayrollExportRowsForSingleDate(
                workShifts = it,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}"
            )
        }.joinToString(separator = "")

        producerTemplate.sendBodyAndHeader(
            "sftp://$ftpUserName@$ftpAddress?password=$ftpUserPassword",
            fileContent,
            Exchange.FILE_NAME,
            fileName
        )
    }

    /**
     * Salary type numbers used by Talenom
     */
    private object SalaryTypes {
        const val PAID_WORK = 11000
        const val EVENING_ALLOWANCE = 30000
        const val NIGHT_ALLOWANCE = 30010
        const val HOLIDAY_ALLOWANCE = 20121
        const val TRAINING = 0
        const val JOB_SPECIFIC_ALLOWANCE = 0
        const val FROZEN_ALLOWANCE = 0
        const val STANDBY = 0
        const val OFFICIAL_DUTIES = 0
    }

    /**
     * Builds rows for a single date
     *
     * @param workShifts
     * @param employeeNumber
     * @param employeeName
     */
    private suspend fun buildPayrollExportRowsForSingleDate(
        workShifts: Map.Entry<LocalDate, List<WorkShiftEntity>>,
        employeeNumber: String,
        employeeName: String
    ): String {
        val date = workShifts.key

        val paidWorkHours = extractNormalPaidWorkHours(workShifts.value)

        var rows = ""

        if (paidWorkHours > 0) {
            rows += buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.PAID_WORK,
                hoursWorked = paidWorkHours
            )
        }

        val eveningAllowanceHours = extractWorkTypeHours(
            workShifts = workShifts.value,
            workType = WorkType.EVENING_ALLOWANCE
        )

        if (eveningAllowanceHours > 0) {
            rows += buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.EVENING_ALLOWANCE,
                hoursWorked = eveningAllowanceHours
            )
        }

        val nightAllowanceHours = extractWorkTypeHours(
            workShifts = workShifts.value,
            workType = WorkType.NIGHT_ALLOWANCE
        )

        if (nightAllowanceHours > 0) {
            rows += buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.NIGHT_ALLOWANCE,
                hoursWorked = nightAllowanceHours
            )
        }

        val holidayAllowanceHours = extractWorkTypeHours(
            workShifts = workShifts.value,
            workType = WorkType.HOLIDAY_ALLOWANCE
        )

        if (holidayAllowanceHours > 0) {
            rows += buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.HOLIDAY_ALLOWANCE,
                hoursWorked = holidayAllowanceHours
            )
        }

        /**
         * TODO: Implement exporting for the remaining salary types
         */

        return rows
    }

    /**
     * Extracts the total sum of hours that will be paid under normal pay.
     * This includes normal paid work, sick leave and paid breaks.
     * Paid breaks are calculated by the rule specified by Talenom.
     *
     * @param workShifts
     */
    private suspend fun extractNormalPaidWorkHours(
        workShifts: List<WorkShiftEntity>
    ): Float {
        var totalHours = 0f

        workShifts.forEach {
            val activeWorkHours = extractWorkTypeHoursForSingleShift(
                workShift = it,
                workType = WorkType.PAID_WORK
            )

            totalHours += activeWorkHours

            totalHours += extractWorkTypeHoursForSingleShift(
                workShift = it,
                workType = WorkType.SICK_LEAVE
            )

            val breakHours = extractWorkTypeHoursForSingleShift(
                workShift = it,
                workType = WorkType.BREAK
            )

            val breakIsPaid = activeWorkHours + breakHours >= 8

            if (breakIsPaid) {
                totalHours += if (breakHours < 0.5f) {
                    breakHours
                } else {
                    0.5f
                }
            }
        }

        return totalHours
    }

    /**
     * Extracts a total sum of hours for a given work type.
     *
     * @param workShifts
     * @param workType
     */
    private suspend fun extractWorkTypeHours(
        workShifts: List<WorkShiftEntity>,
        workType: WorkType
    ): Float {
        var totalHours = 0f

        workShifts.forEach {
            totalHours += extractWorkTypeHoursForSingleShift(
                workShift = it,
                workType = workType
            )
        }

        return totalHours
    }

    /**
     * Extracts the total sum of hours for a given work type for a single shift.
     * Whenever there are manually entered hours (WorkShiftHoursEntity.actualHours), use those.
     * Otherwise, use automatically calculated hours.
     *
     * @param workShift
     * @param workType
     */
    private suspend fun extractWorkTypeHoursForSingleShift(
        workShift: WorkShiftEntity,
        workType: WorkType
    ): Float {
        val workShiftHours = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = workType
        ).first

        var totalHours = 0f
        workShiftHours.forEach { hours ->
            totalHours += hours.actualHours ?: hours.calculatedHours ?: 0f
        }

        return totalHours
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
        hoursWorked: Float
    ): String {
        val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString()
        val formattedWorkHours = "%.2f".format(hoursWorked)
        return "$formattedDate;$employeeNumber;$employeeName;$salaryTypeNumber;$formattedWorkHours;;;;;\n"
    }
}