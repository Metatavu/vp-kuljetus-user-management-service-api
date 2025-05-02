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

        val fileContent = buildRows(
            workShifts = workShifts,
            employee = employee
        )

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

    private suspend fun buildRows(
        workShifts: List<WorkShiftEntity>,
        employee: UserRepresentation
    ): String {
        var rows = ""

        val employeeNumber = employee.attributes!!["employeeNumber"]!!.first()
        val salaryGroup = SalaryGroup.valueOf(employee.attributes[SALARY_GROUP_ATTRIBUTE]!!.first())
        val isDriver = salaryGroup == SalaryGroup.DRIVER || salaryGroup == SalaryGroup.VPLOGISTICS
        val workShiftsGroupedByDate = workShifts.sortedBy { it.date }.groupBy { it.date }

        val vacationHours = salaryPeriodUtils.calculateTotalWorkHoursByAbsenceType(
            workShifts = workShifts,
            absenceType = AbsenceType.VACATION
        )
        val regularWorkingHours = employee.attributes[REGULAR_WORKING_HOURS_ATTRIBUTE]?.firstOrNull()?.toFloat()
        val employeeName = "${employee.firstName} ${employee.lastName}"

        var paidHoursForDriver = 0f

        val driverOverTimeFullLimit = regularWorkingHours?.plus(if (vacationHours > BigDecimal.valueOf(40)) 10 else 12)

        workShiftsGroupedByDate.entries.forEach {
            val date = it.key
            val workShiftsForDate = it.value

            val paidHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.PAID_WORK,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            val trainingHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.TRAINING,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            if (isDriver && regularWorkingHours != null && driverOverTimeFullLimit != null) {
                val regularPaidHours = mutableMapOf<String, Float>()
                val overTimeHalfHours = mutableMapOf<String, Float>()
                val overTimeFullHours = mutableMapOf<String, Float>()

                paidHours.entries.forEach { entry ->
                    val costCenter = entry.key
                    val hours = entry.value

                    if (paidHoursForDriver + hours < regularWorkingHours) {
                        regularPaidHours[costCenter] = (regularPaidHours[costCenter] ?: 0f) + hours
                    } else {
                        var regularHoursPart = regularWorkingHours - paidHoursForDriver
                        if (regularHoursPart < 0) {
                            regularHoursPart = 0f
                        }

                        regularPaidHours[costCenter] = (regularPaidHours[costCenter] ?: 0f) + regularHoursPart

                        val overTime = hours - regularHoursPart

                        if (paidHoursForDriver + regularHoursPart + overTime < driverOverTimeFullLimit) {
                            overTimeHalfHours[costCenter] = (overTimeHalfHours[costCenter] ?: 0f) + overTime
                        } else {
                            var overTimeHalfHoursPart = driverOverTimeFullLimit - (paidHoursForDriver + regularHoursPart)

                            if (overTimeHalfHoursPart < 0) {
                                overTimeHalfHoursPart = 0f
                            }

                            overTimeHalfHours[costCenter] = (overTimeHalfHours[costCenter] ?: 0f) + overTimeHalfHoursPart

                            val overTimeFull = overTime - overTimeHalfHoursPart
                            overTimeFullHours[costCenter] = (overTimeFullHours[costCenter] ?: 0f) + overTimeFull
                        }
                    }

                    paidHoursForDriver += hours
                }

                trainingHours.entries.forEach { trainingHour ->
                    regularPaidHours[trainingHour.key] = (regularPaidHours[trainingHour.key] ?: 0f) + trainingHour.value
                }

                rows += buildDailyRows(
                    hours = regularPaidHours,
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.PAID_WORK
                )

                rows += buildDailyRows(
                    hours = overTimeHalfHours,
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.OVER_TIME_HALF
                )

                rows += buildDailyRows(
                    hours = overTimeFullHours,
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.OVER_TIME_FULL
                )
            } else {
                trainingHours.entries.forEach { trainingHour ->
                    paidHours[trainingHour.key] = (paidHours[trainingHour.key] ?: 0f) + trainingHour.value
                }

                rows += buildDailyRows(
                    hours = paidHours,
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.PAID_WORK
                )
            }

            val eveningAllowanceHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.EVENING_ALLOWANCE,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = eveningAllowanceHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.EVENING_ALLOWANCE
            )

            val nightAllowanceHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.NIGHT_ALLOWANCE,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = nightAllowanceHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber =
                    if (isDriver) SalaryTypes.DRIVER_NIGHT_ALLOWANCE else SalaryTypes.TERMINAL_NIGHT_ALLOWANCE
            )

            val jobSpecificAllowanceHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.JOB_SPECIFIC_ALLOWANCE,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = jobSpecificAllowanceHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber =
                    if (isDriver) SalaryTypes.ADR_ALLOWANCE else SalaryTypes.WORKING_CONDITIONS_ALLOWANCE
            )

            val frozenAllowanceHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.FROZEN_ALLOWANCE,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = frozenAllowanceHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.FROZEN_ALLOWANCE
            )

            val holidayAllowanceHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.HOLIDAY_ALLOWANCE,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = holidayAllowanceHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.HOLIDAY_ALLOWANCE
            )

            val standbyHours = getWorkTypeHours(
                workShifts = workShiftsForDate,
                workType = WorkType.STANDBY,
                isDriver = isDriver,
                regularWorkingTime = regularWorkingHours,
                vacationHours = vacationHours.toFloat()
            )

            rows += buildDailyRows(
                hours = standbyHours,
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = SalaryTypes.STANDBY
            )

            val partialDailyAllowance = workShifts.filter { shift ->
                shift.perDiemAllowance == PerDiemAllowanceType.PARTIAL
            }.size

            if (partialDailyAllowance != 0) {
                rows += buildPayrollExportRow(
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.PARTIAL_DAILY_ALLOWANCE,
                    hoursWorked = partialDailyAllowance.toFloat(),
                    costCenter = ""
                )
            }

            val fullDailyAllowance = workShifts.filter { shift ->
                shift.perDiemAllowance == PerDiemAllowanceType.FULL
            }.size

            if (fullDailyAllowance != 0) {
                rows += buildPayrollExportRow(
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.FULL_DAILY_ALLOWANCE,
                    hoursWorked = fullDailyAllowance.toFloat(),
                    costCenter = ""
                )
            }

            val dayOffBonus = salaryPeriodUtils.calculateDayOffBonus(workShifts = workShifts)
            if (dayOffBonus.toFloat() != 0f) {
                rows += buildPayrollExportRow(
                    date = date,
                    employeeNumber = employeeNumber,
                    employeeName = employeeName,
                    salaryTypeNumber = SalaryTypes.DAY_OFF_BONUS,
                    hoursWorked = dayOffBonus.toFloat(),
                    costCenter = ""
                )
            }
        }

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

        if (fillingHours.toFloat() != 0f) {
            rows += buildPayrollExportRow(
                date = workShiftsGroupedByDate.entries.last().key,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}",
                salaryTypeNumber = SalaryTypes.FILLING_HOURS,
                hoursWorked = fillingHours.toFloat(),
                costCenter = ""
            )
        }

        return rows
    }

    private fun buildDailyRows(
        hours: Map<String, Float>,
        date: LocalDate,
        employeeNumber: String,
        employeeName: String,
        salaryTypeNumber: Int
    ): String {

        return hours.entries.joinToString("") {
            val costCenter = it.key
            val hoursWorked = it.value

            if (hoursWorked == 0f) {
                return@joinToString ""
            }

            buildPayrollExportRow(
                date = date,
                employeeNumber = employeeNumber,
                employeeName = employeeName,
                salaryTypeNumber = salaryTypeNumber,
                hoursWorked = hoursWorked,
                costCenter = costCenter
            )
        }
    }


    private suspend fun getWorkTypeHours(
        workShifts: List<WorkShiftEntity>,
        workType: WorkType,
        isDriver: Boolean,
        regularWorkingTime: Float?,
        vacationHours: Float
    ): MutableMap<String, Float> {
        val costCenterHours = mutableMapOf<String, Float>()

        workShifts.forEach {
            val calculatedHours = when(workType) {
                WorkType.PAID_WORK -> {
                    val result = payrollExportCalculations.calculatePaidWorkForWorkShift(
                        workShift = it,
                        isDriver = isDriver,
                        regularWorkingTime = regularWorkingTime,
                        vacationHours = vacationHours
                    )

                    result
                }

                WorkType.TRAINING -> {
                    val trainingMap = mutableMapOf<String, Float>()
                    val hours = workShiftHoursController.listWorkShiftHours(
                        workShiftFilter = it,
                        workType = WorkType.TRAINING
                    ).first.first().actualHours ?: 0f
                    trainingMap[it.defaultCostCenter ?: ""] = hours

                    trainingMap
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

        return costCenterHours
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