package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Test class for Work Shift Hours
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkShiftHoursTestIT : AbstractFunctionalTest() {

    /**
     * Tests:
     * - Work Shift Hours creation based on work shifts
     */
    @Test
    fun testWorkShiftHoursCreate() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val workShift = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().toString(),
                approved = false
            )
        )

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(7, workShiftHours.size)

        workShiftHours.forEach {
            assertEquals(workShift.id, it.employeeWorkShiftId)
            assertNull(it.actualHours)
            assertNull(it.calculatedHours)      //todo: replace with actual value later when calculations are implemented
        }

    }

    /**
     * Tests:
     *  - Work Shift Hours listing
     */
    @Test
    fun testWorkShiftHoursListing() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val workShift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false
            )
        )
        val workShift2 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().plusDays(2).toString(),
                approved = false
            )
        )
        val employee2 = tb.manager.employees.createEmployee("02").id!!
        val workShift3 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                employeeId = employee2,
                date = LocalDate.now().plusDays(3).toString(),
                approved = false
            )
        )

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(14, workShiftHours.size)
        assertEquals(7, workShiftHours.filter { it.employeeWorkShiftId == workShift1.id }.size)
        assertEquals(7, workShiftHours.filter { it.employeeWorkShiftId == workShift2.id }.size)

        val workShiftHours2 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee2)
        assertEquals(7, workShiftHours2.size)

        val workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee2,
            workType = fi.metatavu.vp.test.client.models.WorkType.PAID_WORK
        )
        assertEquals(1, workShiftHours3.size)

        val byWorkType =
            tb.manager.workShiftHours.listWorkShiftHours(workType = fi.metatavu.vp.test.client.models.WorkType.PAID_WORK)
        assertEquals(3, byWorkType.size)

        val byDate = tb.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftStartedAfter = OffsetDateTime.now().plusDays(2).toString(),
            employeeWorkShiftStartedBefore = OffsetDateTime.now().plusDays(3).toString()
        )
        assertEquals(14, byDate.size)
    }

    /**
     * Tests:
     *  - Work Shift Hours find by id
     */
    @Test
    fun teatWorkShiftHoursFind() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val workShift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)[0]
        val foundHours = tb.manager.workShiftHours.findWorkShiftHours(workShiftHours.id!!)
        assertEquals(workShiftHours.employeeWorkShiftId, foundHours.employeeWorkShiftId)
        assertEquals(workShiftHours.calculatedHours, foundHours.calculatedHours)
        assertEquals(workShiftHours.actualHours, foundHours.actualHours)
        Assertions.assertNotNull(foundHours.id)
    }

    /**
     * Tests:
     *  1 Updating is impossible is work shift is approved
     *  2 employeeId, work ShiftId, work type cannot be updated
     *  3 actual hours are updated by the supervisor
     */
    @Test
    fun testWorkShiftHoursUpdate() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val workShift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)[0]

        val updateData = workShiftHours.copy(actualHours = 5f)

        //update the hours
        val updated = tb.manager.workShiftHours.updateWorkShiftHours(
            id = updateData.id!!,
            workShiftHours = updateData
        )
        assertEquals(5f, updated.actualHours)

        // cannot update other fields
        tb.manager.workShiftHours.assertUpdateFail(
            expectedStatus = 400,
            id = workShiftHours.id!!,
            workEvent = workShiftHours.copy(workType = fi.metatavu.vp.test.client.models.WorkType.PAID_WORK)
        )

        // approve shift
        tb.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1,
            id = workShift1.id!!,
            workShift = workShift1.copy(approved = true)
        )

        // updating is impossible if work shift is apporved
        tb.manager.workShiftHours.assertUpdateFail(
            expectedStatus = 400,
            id = workShiftHours.id,
            workEvent = workShiftHours.copy(actualHours = 5f)
        )
    }

    /**
     * Tests:
     *  - Work Shift Hours deletion
     */
    @Test
    fun testWorkShiftHoursDelete() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)

        tb.manager.workShiftHours.deleteWorkShiftHours(workShiftHours[0].id!!)
        val workShiftHoursAfterDeletion = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(workShiftHours.size - 1, workShiftHoursAfterDeletion.size)
    }

    /**
     * Tests:
     *  1 - Background work shift hours calculation
     *  2 - Work Shift Hours calculation when requested
     *  3 - Work Shift Hours calculation when events are created/updated/removed
     */
    @Test
    fun testWorkShiftHoursCalculations() = createTestBuilder().use { tb ->

    }
}