package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.PayrollExport
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class PayrollExportTestsIT: AbstractFunctionalTest() {
    @Test
    fun testCreatePayrollExport() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
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

        assertNotNull(payrollExport.workShiftIds.find { shiftId -> shiftId == workShift.id }, "Work shift ID should be in the export")
        assertNotNull(payrollExport.workShiftIds.find { shiftId -> shiftId == workShift2.id }, "Work shift ID should be in the export")

    }
}