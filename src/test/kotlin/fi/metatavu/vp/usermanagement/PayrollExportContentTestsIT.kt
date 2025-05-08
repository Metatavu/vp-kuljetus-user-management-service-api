package fi.metatavu.vp.usermanagement

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.resources.S3FileDownload
import fi.metatavu.vp.usermanagement.resources.S3TestResource
import fi.metatavu.vp.usermanagement.resources.SftpServerTestResource
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@QuarkusTest
@QuarkusTestResource.List(
    QuarkusTestResource(SftpServerTestResource::class),
    QuarkusTestResource(S3TestResource::class)
)
@TestProfile(DefaultTestProfile::class)
class PayrollExportContentTestsIT: AbstractFunctionalTest() {
    @Test
    fun testPayrollExportPaidWork() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        it.manager.employees.updateEmployee(
            employeeId = employee.id!!,
            employee = employee.copy(
                regularWorkingHours = 40f
            )
        )

        val day1 = getLastWorkDay(
            date = LocalDate.now()
        )

        val day2 = getLastWorkDay(
            date = day1
        )

        val finlandZone = ZoneId.of("Europe/Helsinki")
        val currentOffset = OffsetDateTime.now().atZoneSameInstant(finlandZone).offset

        val date1 = OffsetDateTime.of(
            day1.year,
            day1.monthValue,
            day1.dayOfMonth,
            12,
            0,
            0,
            0,
            currentOffset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            12,
            0,
            0,
            0,
            currentOffset
        )

        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(5).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event1.id!!,
            workEvent = event1.copy(
                costCenter = "Cost center 1"
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(3).toString(),
                workEventType = WorkEventType.OTHER_WORK
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event2.id!!,
            workEvent = event2.copy(
                costCenter = "Cost center 2"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift1 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        val workShiftHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift1.id!!,
            workType = WorkType.PAID_WORK
        ).first()

        it.manager.workShiftHours.updateWorkShiftHours(
            id = workShiftHours.id!!,
            workShiftHours = workShiftHours.copy(
                actualHours = 6f
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift1.id,
            workShift = workShift1.copy(
                approved = true
            )
        )


        val event3 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(5).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event3.id!!,
            workEvent = event3.copy(
                costCenter = "Cost center 1"
            )
        )

        val event4 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(3).toString(),
                workEventType = WorkEventType.OTHER_WORK
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event4.id!!,
            workEvent = event4.copy(
                costCenter = "Cost center 2"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift2 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        val event5 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.plusSeconds(1).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event5.id!!,
            workEvent = event5.copy(
                costCenter = "Cost center 1"
            )
        )

        val event6 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.plusHours(2).toString(),
                workEventType = WorkEventType.OTHER_WORK
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event6.id!!,
            workEvent = event6.copy(
                costCenter = "Cost center 2"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.plusHours(5).toString(),
                workEventType = WorkEventType.SHIFT_END,
            )
        )

        val workShift3 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        val sickHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift3.id!!,
            workType = WorkType.SICK_LEAVE
        ).first()

        it.manager.workShiftHours.updateWorkShiftHours(
            id = sickHours.id!!,
            workShiftHours = sickHours.copy(
                actualHours = 1f
            )
        )

        val officialDutyHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift3.id,
            workType = WorkType.OFFICIAL_DUTIES
        ).first()

        val trainingHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift3.id,
            workType = WorkType.TRAINING
        ).first()

        it.manager.workShiftHours.updateWorkShiftHours(
            id = trainingHours.id!!,
            workShiftHours = trainingHours.copy(
                actualHours = 2f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = officialDutyHours.id!!,
            workShiftHours = officialDutyHours.copy(
                actualHours = 1f
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift3.id,
            workShift = workShift3.copy(
                approved = true,
                defaultCostCenter = "Default"
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift1.id, workShift2.id, workShift3.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val formattedDate1 = date1.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate2 = date2.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate2;1212;Test Employee;11000;2.00;;Cost center 1;;;"
        val row2 = "$formattedDate2;1212;Test Employee;11000;3.00;;Cost center 2;;;"
        val row3 = "$formattedDate2;1212;Test Employee;11000;1.00;;;;;"
        val row4 = "$formattedDate1;1212;Test Employee;11000;4.00;;Cost center 1;;;"
        val row5 = "$formattedDate1;1212;Test Employee;11000;6.00;;Cost center 2;;;"
        val row6 = "$formattedDate1;1212;Test Employee;11000;4.00;;Default;;;"
        val row7 = "$formattedDate1;1212;Test Employee;11010;22.00;;;;;"

        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n" +
                row5 + "\n" +
                row6 + "\n" +
                row7 + "\n"

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportManualSubtractions() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        it.manager.employees.updateEmployee(
            employeeId = employee.id!!,
            employee = employee.copy(
                regularWorkingHours = 40f
            )
        )

        val now = getLastWorkDay(
            date = LocalDate.now()
        )

        val currentOffset = OffsetDateTime.now().atZoneSameInstant(ZoneId.of("Europe/Helsinki")).offset

        val date = OffsetDateTime.of(
            now.year,
            now.monthValue,
            now.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event1.id!!,
            workEvent = event1.copy(
                costCenter = "Centre 1"
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(6).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event2.id!!,
            workEvent = event2.copy(
                costCenter = "Centre 2"
            )
        )

        val event3 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(3).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event3.id!!,
            workEvent = event3.copy(
                costCenter = "Centre 3"
            )
        )

        val event4 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(1).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event4.id!!,
            workEvent = event4.copy(
                costCenter = "Centre 4"
            )
        )

        val event5 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event5.id!!,
            workEvent = event5.copy(
                costCenter = "Centre 5"
            )
        )

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        val workShiftHoursPaid = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift.id!!,
            workType = WorkType.PAID_WORK
        ).first()

        it.manager.workShiftHours.updateWorkShiftHours(
            id = workShiftHoursPaid.id!!,
            workShiftHours = workShiftHoursPaid.copy(
                actualHours = 3.5f
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id,
            workShift = workShift.copy(
                approved = true
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val formattedDate = date.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate;1212;Test Employee;11000;2.00;;Centre 1;;;"
        val row2 = "$formattedDate;1212;Test Employee;11000;1.50;;Centre 2;;;"
        val row3 = "$formattedDate;1212;Test Employee;11010;36.50;;;;;"

        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n"

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportBreaks() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        it.manager.employees.updateEmployee(
            employeeId = employee.id!!,
            employee = employee.copy(
                regularWorkingHours = 40f
            )
        )

        val day1 = getLastWorkDay(
            date = LocalDate.now()
        )

        val day2 = getLastWorkDay(
            date = day1
        )

        val day3 = getLastWorkDay(
            date = day2
        )

        val finlandZone = ZoneId.of("Europe/Helsinki")
        val currentOffset = OffsetDateTime.now().atZoneSameInstant(finlandZone).offset

        val date1 = OffsetDateTime.of(
            day1.year,
            day1.monthValue,
            day1.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date3 = OffsetDateTime.of(
            day3.year,
            day3.monthValue,
            day3.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusMinutes(25).toString(),
                workEventType = WorkEventType.BREAK
            )
        )


        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END,
            )
        )


        val workShift3 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift3.id!!,
            workShift = workShift3.copy(
                approved = true
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(9).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusMinutes(15).toString(),
                workEventType = WorkEventType.BREAK
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift1 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift1.id!!,
            workShift = workShift1.copy(
                approved = true
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(9).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(1).toString(),
                workEventType = WorkEventType.BREAK
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift2 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift3.copy(
                approved = true
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift1.id, workShift2.id, workShift3.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val formattedDate1 = date1.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate2 = date2.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate3 = date3.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate3;1212;Test Employee;11000;3.58;;;;;"
        val row2 = "$formattedDate2;1212;Test Employee;11000;9.00;;;;;"
        val row3 = "$formattedDate1;1212;Test Employee;11000;8.50;;;;;"
        val row4 = "$formattedDate1;1212;Test Employee;11010;18.50;;;;;"

        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n"

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportDriverOverTime() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        val day1 = getLastWorkDay(
            date = LocalDate.now()
        )

        val day2 = getLastWorkDay(
            date = day1
        )

        val day3 = getLastWorkDay(
            date = day2
        )

        val finlandZone = ZoneId.of("Europe/Helsinki")
        val currentOffset = OffsetDateTime.now().atZoneSameInstant(finlandZone).offset

        val date1 = OffsetDateTime.of(
            day1.year,
            day1.monthValue,
            day1.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date3 = OffsetDateTime.of(
            day3.year,
            day3.monthValue,
            day3.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )


        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event1.id!!,
            workEvent = event1.copy(
                costCenter = "A"
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )


        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event2.id!!,
            workEvent = event2.copy(
                costCenter = "B"
            )
        )

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true
            )
        )

        val event3 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event3.id!!,
            workEvent = event3.copy(
                costCenter = "A"
            )
        )

        val event4 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event4.id!!,
            workEvent = event4.copy(
                costCenter = "B"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift2 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        val event5 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event5.id!!,
            workEvent = event5.copy(
                costCenter = "A"
            )
        )

        val event6 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event6.id!!,
            workEvent = event6.copy(
                costCenter = "B"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift3 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift3.id!!,
            workShift = workShift3.copy(
                approved = true
            )
        )

        it.manager.employees.updateEmployee(
            employeeId = employee.id,
            employee = employee.copy(
                regularWorkingHours = 8f
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id, workShift3.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val formattedDate1 = date1.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate2 = date2.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate3 = date3.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate3;1212;Test Employee;11000;4.00;;A;;;"
        val row2 = "$formattedDate3;1212;Test Employee;11000;4.00;;B;;;"
        val row3 = "$formattedDate2;1212;Test Employee;20050;4.00;;A;;;"
        val row4 = "$formattedDate2;1212;Test Employee;20050;4.00;;B;;;"
        val row5 = "$formattedDate1;1212;Test Employee;20050;4.00;;A;;;"
        val row6 = "$formattedDate1;1212;Test Employee;20060;4.00;;B;;;"


        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n" +
                row5 + "\n" +
                row6 + "\n"

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportDriverOverTimeWithMidEventSplits() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        val day1 = getLastWorkDay(
            date = LocalDate.now()
        )

        val day2 = getLastWorkDay(
            date = day1
        )

        val day3 = getLastWorkDay(
            date = day2
        )

        val finlandZone = ZoneId.of("Europe/Helsinki")
        val currentOffset = OffsetDateTime.now().atZoneSameInstant(finlandZone).offset

        val date1 = OffsetDateTime.of(
            day1.year,
            day1.monthValue,
            day1.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )

        val date3 = OffsetDateTime.of(
            day3.year,
            day3.monthValue,
            day3.dayOfMonth,
            16,
            0,
            0,
            0,
            currentOffset
        )


        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event1.id!!,
            workEvent = event1.copy(
                costCenter = "A"
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date3.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )


        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event2.id!!,
            workEvent = event2.copy(
                costCenter = "B"
            )
        )

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true
            )
        )

        val event3 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event3.id!!,
            workEvent = event3.copy(
                costCenter = "A"
            )
        )

        val event4 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event4.id!!,
            workEvent = event4.copy(
                costCenter = "B"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift2 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        val event5 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(8).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event5.id!!,
            workEvent = event5.copy(
                costCenter = "A"
            )
        )

        val event6 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.minusHours(4).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event6.id!!,
            workEvent = event6.copy(
                costCenter = "B"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date1.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift3 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift3.id!!,
            workShift = workShift3.copy(
                approved = true
            )
        )

        it.manager.employees.updateEmployee(
            employeeId = employee.id,
            employee = employee.copy(
                regularWorkingHours = 5f
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id, workShift3.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val formattedDate1 = date1.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate2 = date2.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate3 = date3.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate3;1212;Test Employee;11000;4.00;;A;;;"
        val row2 = "$formattedDate3;1212;Test Employee;11000;1.00;;B;;;"
        val row3 = "$formattedDate3;1212;Test Employee;20050;3.00;;B;;;"
        val row4 = "$formattedDate2;1212;Test Employee;20050;4.00;;A;;;"
        val row5 = "$formattedDate2;1212;Test Employee;20050;4.00;;B;;;"
        val row6 = "$formattedDate1;1212;Test Employee;20050;1.00;;A;;;"
        val row7 = "$formattedDate1;1212;Test Employee;20060;3.00;;A;;;"
        val row8 = "$formattedDate1;1212;Test Employee;20060;4.00;;B;;;"


        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n" +
                row5 + "\n" +
                row6 + "\n" +
                row7 + "\n" +
                row8 + "\n"

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportOfficeWorkerOverTime() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        val day = getLastWorkDay(
            date = LocalDate.now()
        )

        val day2 = getLastWorkDay(
            date = day
        )

        val finlandZone = ZoneId.of("Europe/Helsinki")
        val currentOffset = OffsetDateTime.now().atZoneSameInstant(finlandZone).offset

        val date = OffsetDateTime.of(
            day.year,
            day.monthValue,
            day.dayOfMonth,
            18,
            0,
            0,
            0,
            currentOffset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            18,
            0,
            0,
            0,
            currentOffset
        )

        it.manager.employees.updateEmployee(
            employeeId = employee.id!!,
            employee = employee.copy(
                salaryGroup = SalaryGroup.OFFICE,
                regularWorkingHours = null
            )
        )

        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(11).minusMinutes(30).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event1.id!!,
            workEvent = event1.copy(
                costCenter = "A"
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.minusHours(2).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event2.id!!,
            workEvent = event2.copy(
                costCenter = "B"
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date2.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(11).toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift2 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        val formattedDate1 = date.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val formattedDate2 = date2.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$formattedDate2;1212;Test Employee;11000;8.00;;A;;;"
        val row2 = "$formattedDate2;1212;Test Employee;20050;1.50;;A;;;"
        val row3 = "$formattedDate2;1212;Test Employee;20050;0.50;;B;;;"
        val row4 = "$formattedDate2;1212;Test Employee;20060;1.50;;B;;;"
        val row5 = "$formattedDate1;1212;Test Employee;11000;8.00;;;;;"
        val row6 = "$formattedDate1;1212;Test Employee;20050;2.00;;;;;"
        val row7 = "$formattedDate1;1212;Test Employee;20060;1.00;;;;;"

        val expectedFileContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n" +
                row5 + "\n" +
                row6 + "\n" +
                row7 + "\n"

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        assertEquals(
            expectedFileContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testPayrollExportAllowances() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1212")

        it.manager.employees.updateEmployee(
            employeeId = employee.id!!,
            employee = employee.copy(
                regularWorkingHours = 40f
            )
        )

        val now = getLastWorkDay(
            date = LocalDate.now()
        )

        val currentOffset = OffsetDateTime.now().atZoneSameInstant(ZoneId.of("Europe/Helsinki")).offset

        val date = OffsetDateTime.of(
            now.year,
            now.monthValue,
            now.dayOfMonth,
            23,
            0,
            0,
            0,
            currentOffset
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.minusHours(5).toString(),
                workEventType = WorkEventType.FROZEN
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = date.toString(),
                workEventType = WorkEventType.SHIFT_END
            )
        )

        val workShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()

        val standbyHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift.id!!,
            workType = WorkType.STANDBY
        )

        val trainingHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift.id,
            workType = WorkType.TRAINING
        )

        val jobSpecificAllowanceHours = it.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee.id,
            employeeWorkShiftId = workShift.id,
            workType = WorkType.JOB_SPECIFIC_ALLOWANCE
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = standbyHours.first().id!!,
            workShiftHours = standbyHours.first().copy(
                actualHours = 1.0f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = trainingHours.first().id!!,
            workShiftHours = trainingHours.first().copy(
                actualHours = 1.0f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = jobSpecificAllowanceHours.first().id!!,
            workShiftHours = jobSpecificAllowanceHours.first().copy(
                actualHours = 1.0f
            )
        )


        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id,
            workShift = workShift.copy(
                approved = true,
                dayOffWorkAllowance = true,
                perDiemAllowance = PerDiemAllowanceType.FULL
            )
        )

        it.manager.holidays.create(holiday = Holiday(
                date = date.toLocalDate().toString(),
                name = "Black Christmas",
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        val formattedDate = date.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val row1 = "$formattedDate;1212;Test Employee;11000;6.00;;;;;"
        val row2 = "$formattedDate;1212;Test Employee;30000;4.00;;;;;"
        val row3 = "$formattedDate;1212;Test Employee;30010;1.00;;;;;"
        val row4 = "$formattedDate;1212;Test Employee;30058;1.00;;;;;"
        val row5 = "$formattedDate;1212;Test Employee;30059;5.00;;;;;"
        val row6 = "$formattedDate;1212;Test Employee;60000;5.00;;;;;"
        val row7 = "$formattedDate;1212;Test Employee;11500;1.00;;;;;"
        val row8 = "$formattedDate;1212;Test Employee;80102;1.00;;;;;"
        val row9 = "$formattedDate;1212;Test Employee;20121;5.00;;;;;"
        val row10 = "$formattedDate;1212;Test Employee;11010;35.00;;;;;"

        val expectedContent = row1 + "\n" +
                row2 + "\n" +
                row3 + "\n" +
                row4 + "\n" +
                row5 + "\n" +
                row6 + "\n" +
                row7 + "\n" +
                row8 + "\n" +
                row9 + "\n" +
                row10 + "\n"

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        assertEquals(
            expectedContent,
            s3FileContent,
            "Payroll S3 export file content should match the expected content"
        )

        val ftpFileContent = downloadStringContentFromFtp(payrollExport.csvFileName!!)

        assertEquals(
            expectedContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    private fun downloadStringContentFromFtp(fileName: String): String {
        val ftpAddress = ConfigProvider.getConfig().getValue("vp.usermanagement.payrollexports.ftp.address", String::class.java)

        val jsch = JSch()
        val address = ftpAddress.removeSuffix("/${ApiTestSettings.FTP_FOLDER}")
        val session = jsch.getSession(ApiTestSettings.FTP_USER_NAME, address.split(":")[0], address.split(":")[1].toInt())
        session.setPassword(ApiTestSettings.FTP_USER_PASSWORD)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect()

        val channel = session.openChannel("sftp") as ChannelSftp

        channel.connect()

       return String(channel.get("/${ApiTestSettings.FTP_FOLDER}/${fileName}").readAllBytes())
    }

}