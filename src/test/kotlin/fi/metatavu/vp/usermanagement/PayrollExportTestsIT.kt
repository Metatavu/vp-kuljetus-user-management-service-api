package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.PayrollExport
import fi.metatavu.vp.test.client.models.WorkType
import fi.metatavu.vp.usermanagement.resources.SftpServerTestResource
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
    QuarkusTestResource(SftpServerTestResource::class)
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

        val now = OffsetDateTime.now()
        val workShift1 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.minusHours(1).toString(),
                date = now.toLocalDate().toString(),
                endedAt = now.toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        val workShift2 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.minusDays(1).minusHours(1).toString(),
                date = now.toLocalDate().minusDays(1).toString(),
                endedAt = now.minusDays(1).toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        val workShift3 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.minusHours(1).toString(),
                date = now.toLocalDate().toString(),
                endedAt = now.toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        val workShift4 = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id,
            workShift = EmployeeWorkShift(
                employeeId = employee.id,
                startedAt = now.minusDays(1).minusHours(1).toString(),
                date = now.toLocalDate().minusDays(1).toString(),
                endedAt = now.minusDays(1).toString(),
                approved = false,
                costCentersFromEvents = emptyArray()
            )
        )

        val workShiftHours1 = it.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftId = workShift1.id
        )

        val workShiftHours2 = it.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftId = workShift2.id
        )

        val workShiftHours3 = it.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftId = workShift3.id
        )

        val workShiftHours4 = it.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftId = workShift4.id
        )

        val paidWorkHours1 = workShiftHours1.first { hours -> hours.workType == WorkType.PAID_WORK }
        val paidWorkHours2 = workShiftHours2.first { hours -> hours.workType == WorkType.PAID_WORK }
        val paidWorkHours3 = workShiftHours3.first { hours -> hours.workType == WorkType.PAID_WORK }
        val paidWorkHours4 = workShiftHours4.first { hours -> hours.workType == WorkType.PAID_WORK }

        val breakHours1 = workShiftHours1.first { hours -> hours.workType == WorkType.BREAK }
        val breakHours2 = workShiftHours2.first { hours -> hours.workType == WorkType.BREAK }
        val breakHours3 = workShiftHours3.first { hours -> hours.workType == WorkType.BREAK }
        val breakHours4 = workShiftHours4.first { hours -> hours.workType == WorkType.BREAK }

        val previousDayEveningAllowances1 = workShiftHours2.first { hours -> hours.workType == WorkType.EVENING_ALLOWANCE }
        val previousDayEveningAllowances2 = workShiftHours4.first { hours -> hours.workType == WorkType.EVENING_ALLOWANCE }


        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidWorkHours1.id!!,
            workShiftHours = paidWorkHours1.copy(
                actualHours = 8f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidWorkHours2.id!!,
            workShiftHours = paidWorkHours2.copy(
                actualHours = 12f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidWorkHours3.id!!,
            workShiftHours = paidWorkHours3.copy(
                actualHours = 2f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = paidWorkHours4.id!!,
            workShiftHours = paidWorkHours4.copy(
                actualHours = 8f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = breakHours1.id!!,
            workShiftHours = breakHours1.copy(
                actualHours = 0.75f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = breakHours2.id!!,
            workShiftHours = breakHours2.copy(
                actualHours = 0.15f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = breakHours3.id!!,
            workShiftHours = breakHours3.copy(
                actualHours = 0.15f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = breakHours4.id!!,
            workShiftHours = breakHours4.copy(
                actualHours = 0.02f
            )
        )

        it.manager.workShiftHours.updateWorkShiftHours(
            id = previousDayEveningAllowances1.id!!,
            workShiftHours = previousDayEveningAllowances1.copy(
                actualHours = 1f
            )
        )
        it.manager.workShiftHours.updateWorkShiftHours(
            id = previousDayEveningAllowances2.id!!,
            workShiftHours = previousDayEveningAllowances2.copy(
                actualHours = 2f
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift1.id!!,
            workShift = workShift1.copy(
                approved = true
            )
        )

        it.manager.workShifts.updateEmployeeWorkShift(
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
        )

        it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee.id,
            id = workShift4.id!!,
            workShift = workShift4.copy(
                approved = true
            )
        )

        val payrollExport = it.manager.payrollExports.createPayrollExport(
            PayrollExport(
                employeeId = employee.id,
                workShiftIds = arrayOf(workShift2.id, workShift1.id, workShift3.id, workShift4.id)
            )
        )

        val date1 = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val date2 = now.minusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val row1 = "$date2;1212;Test Employee;11000;20.17;;;;;\n"
        val row2 = "$date2;1212;Test Employee;30000;3.00;;;;;\n"
        val row3 = "$date1;1212;Test Employee;11000;10.50;;;;;\n"

        val fileContent = File("src/test/resources/payrollexports/" + payrollExport.csvFileName!!).readText()

        assertEquals(
            row1 + row2 + row3,
            fileContent
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