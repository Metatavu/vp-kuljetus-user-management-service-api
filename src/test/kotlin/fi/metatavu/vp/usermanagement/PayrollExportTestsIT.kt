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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@QuarkusTest
@QuarkusTestResource.List(
    QuarkusTestResource(SftpServerTestResource::class),
    QuarkusTestResource(S3TestResource::class)
)
@TestProfile(DefaultTestProfile::class)
class PayrollExportTestsIT: AbstractFunctionalTest() {

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
    fun testPayrollExportContent() = createTestBuilder().use { it ->
        val employee = it.manager.employees.createEmployee("1212")

        val todayAtMidday = OffsetDateTime.now().withHour(11).withMinute(0).withSecond(0)
        val workShift1 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = todayAtMidday.minusHours(5).toString(),
                date = todayAtMidday.toLocalDate().toString(),
                endedAt = todayAtMidday.toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = todayAtMidday.minusHours(5).toString(),
                workEventType = WorkEventType.DRIVE,
                workShift1.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = todayAtMidday.minusSeconds(1).toString(),
                workEventType = WorkEventType.SHIFT_END,
                workShift1.id
            )
        )

        /** val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = todayAtMidday.minusDays(1).minusHours(5).toString(),
                date = todayAtMidday.toLocalDate().minusDays(1).toString(),
                endedAt = todayAtMidday.minusDays(1).toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        val workShift3 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = todayAtMidday.minusHours(5).toString(),
                date = todayAtMidday.toLocalDate().toString(),
                endedAt = todayAtMidday.toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )*/

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift1.id!!,
            workShift = workShift1.copy(
                approved = true
            )
        )

        /*it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift2.id!!,
            workShift = workShift2.copy(
                approved = true
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift3.id!!,
            workShift = workShift3.copy(
                approved = true
            )
        )*/

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift1.id)
            )
        )

        val s3FileContent = S3FileDownload().downloadFile(ApiTestSettings.S3_FOLDER_PATH + payrollExport.csvFileName)

        val expectedFileContent = ""
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