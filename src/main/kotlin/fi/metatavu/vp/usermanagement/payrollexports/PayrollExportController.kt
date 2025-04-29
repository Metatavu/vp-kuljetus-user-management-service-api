package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.employees.utilities.SalaryPeriodUtils
import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.model.SalaryGroup
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.payrollexports.utilities.PayrollExportCalculations
import fi.metatavu.vp.usermanagement.users.UserController.Companion.REGULAR_WORKING_HOURS_ATTRIBUTE
import fi.metatavu.vp.usermanagement.users.UserController.Companion.SALARY_GROUP_ATTRIBUTE
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
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.math.BigDecimal
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
    lateinit var payrollExportCalculations: PayrollExportCalculations

    @Inject
    lateinit var salaryPeriodUtils: SalaryPeriodUtils

    @Inject
    lateinit var producerTemplate: ProducerTemplate

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.address")
    lateinit var ftpAddress: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.username")
    lateinit var ftpUserName: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.ftp.password")
    lateinit var ftpUserPassword: String

    @ConfigProperty(name= "vp.usermanagement.payrollexports.ftp.enabled")
    lateinit var ftpEnabled: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.s3.bucket")
    lateinit var s3Bucket: String

    @ConfigProperty(name = "vp.usermanagement.payrollexports.s3.folderpath")
    lateinit var s3FolderPath: String

    @Inject
    lateinit var s3Client: S3Client

    /**
     * Saves a reference about a payroll export to the database, so that exports can be listed from the UI.
     * Reference is also linked to work shifts that are contained in the payroll export.
     * This is done after exporting.
     *
     * @param exportId export id
     * @param employeeId employee id
     * @param fileName file name
     * @param creatorId creator id
     */
    suspend fun save(
        exportId: UUID,
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
            creatorId = creatorId,
            exportId = exportId
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
     * Builds and exports a payroll file.
     * It is sent to:
     * - S3 bucket (always)
     * - FTP server (if FTP export is enabled through environment variables)
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
        val salaryGroup = SalaryGroup.valueOf(employee.attributes[SALARY_GROUP_ATTRIBUTE]!!.first())
        val isDriver = salaryGroup == SalaryGroup.DRIVER || salaryGroup == SalaryGroup.VPLOGISTICS
        val workShiftsGroupedByDate = workShifts.sortedBy { it.date }.groupBy { it.date }

        val vacationHours = salaryPeriodUtils.calculateTotalWorkHoursByAbsenceType(
            workShifts = workShifts,
            absenceType = AbsenceType.VACATION
        )
        val regularWorkingHours = employee.attributes[REGULAR_WORKING_HOURS_ATTRIBUTE]?.firstOrNull()?.toFloat()
        var driverRegularPaidHoursSum = 0f
        var driverOverTimeHalfSum = 0f
        val dailyRows = workShiftsGroupedByDate.map {
            val result = buildPayrollExportRowsForSingleDate(
                workShifts = it,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}",
                isDriver = isDriver,
                driverOverTimeHalfHoursSum = driverOverTimeHalfSum,
                driverRegularHoursSum = driverRegularPaidHoursSum,
                vacationHours = vacationHours.toFloat(),
                regularWorkingTime = regularWorkingHours
            )

            val (rows, hours) = result

            driverRegularPaidHoursSum = hours.first
            driverOverTimeHalfSum = hours.second

            return@map rows
        }.joinToString(separator = "")

        val fillingHours = if (regularWorkingHours != null) salaryPeriodUtils.calculateFillingHours(
            regularWorkingHours = regularWorkingHours.toBigDecimal(),
            workingHours = salaryPeriodUtils.calculateWorkingHoursByWorkType(
                workShifts = workShifts,
                workType = WorkType.PAID_WORK
            ),
            vacationHours = vacationHours,
            sickHours = salaryPeriodUtils.calculateWorkingHoursByWorkType(
                workShifts = workShifts,
                workType = WorkType.SICK_LEAVE
            ),
            officialDutyHours = salaryPeriodUtils.calculateWorkingHoursByWorkType(
                workShifts = workShifts,
                workType = WorkType.OFFICIAL_DUTIES
            ),
            compensatoryLeaveHours = salaryPeriodUtils.calculateTotalWorkHoursByAbsenceType(
                workShifts = workShifts,
                absenceType = AbsenceType.COMPENSATORY_LEAVE
            )
        ) else BigDecimal.valueOf(0)

        val fillingHoursRow = if (fillingHours.toFloat() != 0f) {
            buildPayrollExportRow(
                date = workShiftsGroupedByDate.entries.last().key,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}",
                salaryTypeNumber = SalaryTypes.FILLING_HOURS,
                hoursWorked = fillingHours.toFloat(),
                costCenter = ""
            )
        } else ""

        val fileContent = dailyRows + fillingHoursRow

        sendFileToS3(
            fileName = fileName,
            fileContent = fileContent
        )

        if (ftpEnabled == "TRUE") {
            producerTemplate.sendBodyAndHeader(
                "sftp://$ftpUserName@$ftpAddress?password=$ftpUserPassword",
                fileContent,
                Exchange.FILE_NAME,
                fileName
            )
        }
    }

    /**
     * Salary type numbers used by Talenom
     */
    private object SalaryTypes {
        const val PAID_WORK = 11000
        const val EVENING_ALLOWANCE = 30000
        const val DRIVER_NIGHT_ALLOWANCE = 30010
        const val TERMINAL_NIGHT_ALLOWANCE = 30011
        const val HOLIDAY_ALLOWANCE = 60000
        const val WORKING_CONDITIONS_ALLOWANCE = 30300
        const val ADR_ALLOWANCE = 30058
        const val FROZEN_ALLOWANCE = 30059
        const val STANDBY = 11500
        const val OVER_TIME_HALF = 20050
        const val OVER_TIME_FULL = 20060
        const val PARTIAL_DAILY_ALLOWANCE = 80112
        const val FULL_DAILY_ALLOWANCE = 80102
        const val FILLING_HOURS = 11010
        const val DAY_OFF_BONUS = 20121
    }

    /**
     * Builds payroll export rows for a single date.
     *
     * @param workShifts
     * @param employeeNumber
     * @param employeeName
     * @param isDriver
     * @param regularWorkingTime
     * @param vacationHours
     * @param driverRegularHoursSum
     * @param driverOverTimeHalfHoursSum
     */
    private suspend fun buildPayrollExportRowsForSingleDate(
        workShifts: Map.Entry<LocalDate, List<WorkShiftEntity>>,
        employeeNumber: String,
        employeeName: String,
        isDriver: Boolean,
        regularWorkingTime: Float?,
        vacationHours: Float,
        driverRegularHoursSum: Float,
        driverOverTimeHalfHoursSum: Float
    ): Pair<String, Pair<Float, Float>> {
        var driverRegularHoursSumCurrent = driverRegularHoursSum
        var driverOverTimeHalfSumCurrent = driverOverTimeHalfHoursSum

        val paidWorkRowsResult = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.PAID_WORK,
            salaryTypeNumber = SalaryTypes.PAID_WORK,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        )

        val paidWorkRows = paidWorkRowsResult.first
        driverRegularHoursSumCurrent = paidWorkRowsResult.second.first
        driverOverTimeHalfSumCurrent = paidWorkRowsResult.second.second

        val eveningAllowanceRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.EVENING_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.EVENING_ALLOWANCE,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first


        val nightAllowanceRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.NIGHT_ALLOWANCE,
            salaryTypeNumber =
                if (isDriver) SalaryTypes.DRIVER_NIGHT_ALLOWANCE else SalaryTypes.TERMINAL_NIGHT_ALLOWANCE,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first

        val jobSpecificAllowanceRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.JOB_SPECIFIC_ALLOWANCE,
            salaryTypeNumber =
                if (isDriver) SalaryTypes.ADR_ALLOWANCE else SalaryTypes.WORKING_CONDITIONS_ALLOWANCE,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first

        val frozenAllowanceRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.FROZEN_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.FROZEN_ALLOWANCE,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first

        val holidayAllowanceRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.HOLIDAY_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.HOLIDAY_ALLOWANCE,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first

        val standbyRows = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.STANDBY,
            salaryTypeNumber = SalaryTypes.STANDBY,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.REGULAR,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        ).first

        val overTimeHalfRowsResult = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.PAID_WORK,
            salaryTypeNumber = SalaryTypes.OVER_TIME_HALF,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.OVER_TIME_HALF,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        )

        val overTimeHalfRows = overTimeHalfRowsResult.first
        driverRegularHoursSumCurrent = overTimeHalfRowsResult.second.first
        driverOverTimeHalfSumCurrent = overTimeHalfRowsResult.second.second

        val overTimeFullRowsResult = buildWorkTypeRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.PAID_WORK,
            salaryTypeNumber = SalaryTypes.OVER_TIME_FULL,
            isDriver = isDriver,
            regularWorkingTime = regularWorkingTime,
            vacationHours = vacationHours,
            workTimeType = PayrollExportCalculations.WorkTimeType.OVER_TIME_FULL,
            driverRegularHoursSum = driverRegularHoursSumCurrent,
            driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent
        )

        val overTimeFullRows = overTimeFullRowsResult.first
        driverRegularHoursSumCurrent = overTimeFullRowsResult.second.first
        driverOverTimeHalfSumCurrent = overTimeFullRowsResult.second.second

        val partialDailyAllowance = workShifts.value.filter {
            it.perDiemAllowance == PerDiemAllowanceType.PARTIAL
        }.size

        val fullDailyAllowance = workShifts.value.filter {
            it.perDiemAllowance == PerDiemAllowanceType.FULL
        }.size

        var dailyAllowanceRows = ""
        if (partialDailyAllowance != 0) {
            dailyAllowanceRows += buildPayrollExportRow(
                date = workShifts.key,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.PARTIAL_DAILY_ALLOWANCE,
                hoursWorked = partialDailyAllowance.toFloat(),
                costCenter = ""
            )
        }

        if (fullDailyAllowance != 0) {
            dailyAllowanceRows += buildPayrollExportRow(
                date = workShifts.key,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.FULL_DAILY_ALLOWANCE,
                hoursWorked = fullDailyAllowance.toFloat(),
                costCenter = ""
            )
        }

        val dayOffBonus = salaryPeriodUtils.calculateDayOffBonus(workShifts = workShifts.value)
        val dayOffBonusRow = if (dayOffBonus.toFloat() != 0f) {
            buildPayrollExportRow(
                date = workShifts.key,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.DAY_OFF_BONUS,
                hoursWorked = dayOffBonus.toFloat(),
                costCenter = ""
            )
        } else ""

        val rows = arrayOf(
            paidWorkRows,
            eveningAllowanceRows,
            nightAllowanceRows,
            frozenAllowanceRows,
            holidayAllowanceRows,
            standbyRows,
            jobSpecificAllowanceRows,
            overTimeHalfRows,
            overTimeFullRows,
            dailyAllowanceRows,
            dayOffBonusRow
        ).joinToString("")

        return Pair(rows, Pair(driverRegularHoursSumCurrent, driverOverTimeHalfSumCurrent))
    }

    /**
     * Builds rows for a single work type.
     * The returned item is a pair of:
     * 1. String value which contains the rows.
     * 2. Pair of floats, which contains the sum of regular working hours and the sum of overtime hours that get the half overtime rate.
     *
     * These sums in the second item of the returned pair are only when the work type is PAID_WORK.
     * These values will be inputted to this same function in the next iteration of the loop which builds the rows.
     * This accumulation is needed to calculate the overtime hours.
     *
     * Also, the workTimeType is only relevant when the work type is PAID_WORK.
     * It is used to determine if the current iteration should return regular time or some type of overtime.
     *
     * @param date
     * @param workShifts
     * @param employeeNumber
     * @param employeeName
     * @param workType
     * @param salaryTypeNumber
     * @param isDriver
     * @param regularWorkingTime
     * @param vacationHours
     * @param workTimeType
     * @param driverRegularHoursSum
     * @param driverOverTimeHalfHoursSum
     *
     */
    private suspend fun buildWorkTypeRows(
        date: LocalDate,
        workShifts: List<WorkShiftEntity>,
        employeeNumber: String,
        employeeName: String,
        workType: WorkType,
        salaryTypeNumber: Int,
        isDriver: Boolean,
        regularWorkingTime: Float?,
        vacationHours: Float,
        workTimeType: PayrollExportCalculations.WorkTimeType,
        driverRegularHoursSum: Float,
        driverOverTimeHalfHoursSum: Float
    ): Pair<String, Pair<Float, Float>> {
        val costCenterHours = mutableMapOf<String, Float>()

        var driverRegularHoursSumCurrent = driverRegularHoursSum
        var driverOverTimeHalfSumCurrent = driverOverTimeHalfHoursSum

        workShifts.forEach {
            val calculatedHours = when(workType) {
                WorkType.PAID_WORK -> {
                    val result = payrollExportCalculations.calculatePaidWorkForWorkShift(
                        workShift = it,
                        workTimeType = workTimeType,
                        isDriver = isDriver,
                        driverRegularHoursSum = driverRegularHoursSumCurrent,
                        driverOverTimeHalfHoursSum = driverOverTimeHalfSumCurrent,
                        regularWorkingTime = regularWorkingTime,
                        vacationHours = vacationHours
                    )

                    if (workTimeType == PayrollExportCalculations.WorkTimeType.REGULAR) {
                        driverRegularHoursSumCurrent = result.second.first
                    } else if (workTimeType == PayrollExportCalculations.WorkTimeType.OVER_TIME_HALF) {
                        driverOverTimeHalfSumCurrent = result.second.first
                    }

                    result.first
                }

                WorkType.STANDBY -> {
                    val standbyMap = mutableMapOf<String, Float>()
                    val hours = workShiftHoursController.listWorkShiftHours(
                        workShiftFilter = it,
                        workType = WorkType.STANDBY
                    ).first.first().actualHours ?: 0f
                    standbyMap[it.defaultCostCenter ?: ""] = hours

                    standbyMap
                }

                WorkType.JOB_SPECIFIC_ALLOWANCE -> {
                    val jobSpecificMap = mutableMapOf<String, Float>()
                    val hours = workShiftHoursController.listWorkShiftHours(
                        workShiftFilter = it,
                        workType = WorkType.JOB_SPECIFIC_ALLOWANCE
                    ).first.first().actualHours ?: 0f
                    jobSpecificMap[it.defaultCostCenter ?: ""] = hours

                    jobSpecificMap
                }

                else -> payrollExportCalculations.calculateAllowanceForWorkShift(
                    workShift = it,
                    workType = workType
                )
            }

            calculatedHours.forEach { (costCenter, hours) ->
                costCenterHours[costCenter] = (costCenterHours[costCenter] ?: 0f) + hours
            }

        }

        val costCenterRows = costCenterHours.filterNot { it.value <= 0f }.map {
            buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = salaryTypeNumber,
                hoursWorked = it.value,
                costCenter = it.key
            )
        }.joinToString(separator = "")

        return Pair(costCenterRows, Pair(driverRegularHoursSumCurrent, driverOverTimeHalfSumCurrent))
    }


    /**
     * Sends a file to S3 bucket
     *
     * @param fileName
     * @param fileContent
     */
    private fun sendFileToS3(
        fileName: String,
        fileContent: String
    ) {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3Bucket)
            .key(s3FolderPath + fileName)
            .contentType("text/plain")
            .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromString(fileContent))
    }


    /**
     * Builds a single row for an exported payroll.
     * Work is split into salary types.
     * Each row represents work done for a specific salary type for a specific cost center on a given day.
     * For each day and salary type there can be work done for multiple cost centers.
     *
     * @param date
     * @param employeeNumber
     * @param employeeName
     * @param salaryTypeNumber
     * @param hoursWorked
     * @param costCenter
     */
    private fun buildPayrollExportRow(
        date: LocalDate,
        employeeNumber: String,
        employeeName: String,
        salaryTypeNumber: Int,
        hoursWorked: Float,
        costCenter: String
    ): String {
        val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString()
        val formattedWorkHours = "%.2f".format(hoursWorked)
        return "$formattedDate;$employeeNumber;$employeeName;$salaryTypeNumber;$formattedWorkHours;;$costCenter;;;\n"
    }
}