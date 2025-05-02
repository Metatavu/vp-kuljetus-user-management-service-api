package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.resources.S3FileDownload
import fi.metatavu.vp.usermanagement.resources.S3TestResource
import fi.metatavu.vp.usermanagement.resources.SftpServerTestResource
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@QuarkusTest
@QuarkusTestResource.List(
    QuarkusTestResource(SftpServerTestResource::class),
    QuarkusTestResource(S3TestResource::class)
)
@TestProfile(DefaultTestProfile::class)
class PayrollExportEndpointTestsIT: AbstractFunctionalTest() {

    @Test
    fun testCreatePayrollExport() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        val now = OffsetDateTime.now()
        val workShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.toString(),
                date = now.toLocalDate().toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.payrollExports.assertCreateFail(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id!!)
            ),
            expectedStatus = 400
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id,
            workShift = workShift.copy(
                approved = true
            )
        )

        it.manager.payrollExports.assertCreateFail(
            payrollExport = PayrollExport(
                employeeId = employee2.id!!,
                workShiftIds = arrayOf(workShift.id)
            ),
            expectedStatus = 400
        )

        val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.toString(),
                date = now.toLocalDate().toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        val exportId = it.manager.payrollExports.createPayrollExport(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id)
            )
        ).id!!

        val payrollExport = it.manager.payrollExports.findPayrollExport(exportId)

        assertEquals(employee.id, payrollExport.employeeId, "Employee ID should match the one entered")
        assertEquals(2, payrollExport.workShiftIds.size, "There should be two work shifts in the export")
        assertNotNull(payrollExport.exportedAt, "Exported at should not be null")
        assertNotNull(payrollExport.creatorId, "Creator ID should not be null")

        assertNotNull(payrollExport.workShiftIds.find { shiftId -> shiftId == workShift.id }, "Work shift ID ${workShift.id} should be in the export")
        assertNotNull(payrollExport.workShiftIds.find { shiftId -> shiftId == workShift2.id }, "Work shift ID ${workShift2.id} should be in the export")

        it.manager.payrollExports.assertCreateFail(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id)
            ),
            expectedStatus = 400
        )

        it.driver1.payrollExports.assertCreateFail(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id, workShift2.id)
            ),
            expectedStatus = 403
        )

        it.manager.payrollExports.assertCreateFail(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(UUID.randomUUID())
            ),
            expectedStatus = 400
        )

        it.manager.payrollExports.assertFindFail(
            id = UUID.randomUUID(),
            expectedStatus = 404
        )

        it.driver1.payrollExports.assertFindFail(
            id = exportId,
            expectedStatus = 403
        )
    }

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

        val date1 = OffsetDateTime.of(
            day1.year,
            day1.monthValue,
            day1.dayOfMonth,
            12,
            0,
            0,
            0,
            ZonedDateTime.now().offset
        )

        val date2 = OffsetDateTime.of(
            day2.year,
            day2.monthValue,
            day2.dayOfMonth,
            12,
            0,
            0,
            0,
            ZonedDateTime.now().offset
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
        val row6 = "$formattedDate1;1212;Test Employee;11000;2.00;;Default;;;"
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

        val ftpFileContent = File("src/test/resources/payrollexports/" + payrollExport.csvFileName!!).readText()

        assertEquals(
            expectedFileContent,
            ftpFileContent,
            "Payroll FTP export file content should match the expected content"
        )
    }

    @Test
    fun testListPayrollExports() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        val now = OffsetDateTime.now()
        val workShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.toString(),
                date = now.toLocalDate().toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true
            )
        )

        it.manager.payrollExports.createPayrollExport(
            payrollExport = PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift.id)
            )
        )

        val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2.id!!,
            workShift = EmployeeWorkShift(
                employeeId = employee2.id,
                startedAt = now.toString(),
                date = now.toLocalDate().toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee2.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        it.manager.payrollExports.createPayrollExport(
            payrollExport = PayrollExport(
                employeeId = employee2.id,
                workShiftIds = arrayOf(workShift2.id)
            )
        )

        assertEquals(2, it.manager.payrollExports.listPayrollExports().size, "There should be two payroll exports")
        assertEquals(1, it.manager.payrollExports.listPayrollExports(employeeId = employee.id).size, "There should be one payroll export for employee 1")
        assertEquals(1, it.manager.payrollExports.listPayrollExports(max = 1).size, "There should be one payroll export when max is 1")
        assertEquals(1, it.manager.payrollExports.listPayrollExports(first = 1).size, "There should be one payroll export when first is 1")
        it.driver1.payrollExports.assertListFail(expectedStatus = 403)
    }
}