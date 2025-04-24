package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.model.SalaryGroup
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.payrollexports.utilities.PayrollExportCalculations
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
        val fileContent = workShiftsGroupedByDate.map {
            buildPayrollExportRowsForSingleDate(
                workShifts = it,
                employeeNumber = employeeNumber,
                employeeName = "${employee.firstName} ${employee.lastName}",
                isDriver = isDriver
            )
        }.joinToString(separator = "")

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
        const val HOLIDAY_ALLOWANCE = 20121
        const val WORKING_CONDITIONS_ALLOWANCE = 30300
        const val ADR_ALLOWANCE = 30058
        const val FROZEN_ALLOWANCE = 30059
        const val STANDBY = 11500
    }

    /**
     * Builds payroll export rows for a single date.
     *
     * @param workShifts
     * @param employeeNumber
     * @param employeeName
     * @param isDriver
     */
    private suspend fun buildPayrollExportRowsForSingleDate(
        workShifts: Map.Entry<LocalDate, List<WorkShiftEntity>>,
        employeeNumber: String,
        employeeName: String,
        isDriver: Boolean
    ): String {
        val paidWorkRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.PAID_WORK,
            salaryTypeNumber = SalaryTypes.PAID_WORK
        )

        val eveningAllowanceRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.EVENING_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.EVENING_ALLOWANCE
        )

        val nightAllowanceRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.NIGHT_ALLOWANCE,
            salaryTypeNumber =
                if (isDriver) SalaryTypes.DRIVER_NIGHT_ALLOWANCE else SalaryTypes.TERMINAL_NIGHT_ALLOWANCE
        )

        val jobSpecificAllowanceRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.JOB_SPECIFIC_ALLOWANCE,
            salaryTypeNumber =
                if (isDriver) SalaryTypes.ADR_ALLOWANCE else SalaryTypes.WORKING_CONDITIONS_ALLOWANCE
        )

        val frozenAllowanceRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.FROZEN_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.FROZEN_ALLOWANCE
        )

        val holidayAllowanceRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.HOLIDAY_ALLOWANCE,
            salaryTypeNumber = SalaryTypes.HOLIDAY_ALLOWANCE
        )

        val standbyRows = buildRows(
            date = workShifts.key,
            workShifts = workShifts.value,
            employeeNumber = employeeNumber,
            employeeName = employeeName,
            workType = WorkType.STANDBY,
            salaryTypeNumber = SalaryTypes.STANDBY
        )

        val rows = arrayOf(
            paidWorkRows,
            eveningAllowanceRows,
            nightAllowanceRows,
            frozenAllowanceRows,
            holidayAllowanceRows,
            standbyRows,
            jobSpecificAllowanceRows
        ).joinToString("")

        return rows
    }

    private suspend fun buildRows(
        date: LocalDate,
        workShifts: List<WorkShiftEntity>,
        employeeNumber: String,
        employeeName: String,
        workType: WorkType,
        salaryTypeNumber: Int
    ): String {
        val costCenterHours = mutableMapOf<String, Float>()
        workShifts.forEach {
            val calculatedHours = when(workType) {
                WorkType.PAID_WORK -> payrollExportCalculations.calculatePaidWorkForWorkShift(
                    workShift = it
                )

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

        return costCenterRows
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