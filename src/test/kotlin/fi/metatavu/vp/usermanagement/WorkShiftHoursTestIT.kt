package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Duration
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
        assertEquals(WorkType.entries.size, workShiftHours.size)

        workShiftHours.forEach {
            assertEquals(workShift.id, it.employeeWorkShiftId)
            assertNull(it.actualHours)
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
        assertEquals(WorkType.entries.size*2, workShiftHours.size)
        assertEquals(WorkType.entries.size, workShiftHours.filter { it.employeeWorkShiftId == workShift1.id }.size)
        assertEquals(WorkType.entries.size, workShiftHours.filter { it.employeeWorkShiftId == workShift2.id }.size)

        val workShiftHours2 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee2)
        assertEquals(WorkType.entries.size, workShiftHours2.size)

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
        assertEquals(WorkType.entries.size*2, byDate.size)
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
     * Tests that the hours of shifts that are not ended and are ongoing are calculated correctly (based on the current time)
     */
  /*  @Test
    fun testOngoingWorkShiftHoursCalculation() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val now = OffsetDateTime.now()

        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = now.toLocalDate().toString(),
                approved = false
            )
        )
        tb.manager.workEvents.createWorkEvent(employee1, now.minusHours(1).toString(), WorkEventType.BREWERY)

        Awaitility.await()
            .pollDelay(Duration.ofMinutes(1))
            .atMost(Duration.ofMinutes(3))
            .untilAsserted {
                val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
                assertEquals(WorkType.entries.size, workShiftHours.size)
                assertTrue(workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours!! > 1.0)
            }
    }*/

    /**
     * Tests:
     * - Work Shift Hours calculations during listing
     */
    @Test
    fun testWorkShiftHoursAllowanceListingCalculations() = createTestBuilder().use { tb ->
        // starting at 16 30
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val workEvent1 = tb.manager.workEvents.createWorkEvent(
            employeeId = employee1,
            workEvent = WorkEvent(
                employeeId = employee1,
                workEventType = fi.metatavu.vp.test.client.models.WorkEventType.BREWERY,
                time = "2024-01-01T16:30:00Z"
            )
        )

        //16 30 - 17 30, 1h PAID_WORK
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T17:30:00Z", WorkEventType.DRIVE)
        //17 30 - 18 30, 1h PAID_WORK 0.5H EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T18:30:00Z", WorkEventType.AVAILABILITY)
        //18 30 - 19 30, 1h PAID_WORK 1h EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T19:30:00Z", WorkEventType.BREAK)
        //19 30 - 20 30, 1h break
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T20:30:00Z", WorkEventType.FROZEN)
        // 20.30 - 21.30, 1h frozen allowance, 1h paid work, 1h night allowance
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T21:30:00Z", WorkEventType.AVAILABILITY)
        // 21.30 - 12.30, 15h paid work, 8.5 night allowance
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-02T12:30:00Z", WorkEventType.SHIFT_END)

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(WorkType.entries.size, workShiftHours.size)
        assertEquals(19f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(1.5f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.EVENING_ALLOWANCE }?.calculatedHours)
        assertEquals(9.5f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.NIGHT_ALLOWANCE }?.calculatedHours)
        assertEquals(1f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.BREAK }?.calculatedHours)
        assertEquals(1f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.FROZEN_ALLOWANCE }?.calculatedHours)
        assertEquals(0f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.JOB_SPECIFIC_ALLOWANCE }?.calculatedHours)
    }

    /**
     * Tests Work Shift Hours calculations during holidays
     */
    @Test
    fun testWorkShiftHolidaysCalculations() = createTestBuilder().use { tb ->
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        tb.manager.holidays.create(
            holiday = Holiday(
                date = "2025-01-01",
                name = "New Year",
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        // events during the new year
        tb.manager.workEvents.createWorkEvent(employee1, "2024-12-31T18:30:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1, "2025-01-01T04:30:00Z", WorkEventType.DRIVE)
        tb.manager.workEvents.createWorkEvent(employee1, "2025-01-01T18:30:00Z", WorkEventType.SHIFT_END)

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(WorkType.entries.size, workShiftHours.size)
        assertEquals(24f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(2f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.EVENING_ALLOWANCE }?.calculatedHours) // 2h
        assertEquals(10f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.NIGHT_ALLOWANCE }?.calculatedHours) //8.5
        assertEquals(18.5f, workShiftHours.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours) //18.5

        // Shift marked as day off work allowance
        val shiftDayOff = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = "2025-01-03",
                approved = false,
                dayOffWorkAllowance = true
            )
        )
        tb.manager.workEvents.createWorkEvent(employee1, "2025-01-03T08:00:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1, "2025-01-04T01:00:00Z", WorkEventType.SHIFT_END)

        val workShiftHours2 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1, employeeWorkShiftId = shiftDayOff.id)
        assertEquals(WorkType.entries.size, workShiftHours2.size)
        assertEquals(17f, workShiftHours2.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(2f, workShiftHours2.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.EVENING_ALLOWANCE }?.calculatedHours)
        assertEquals(5f, workShiftHours2.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.NIGHT_ALLOWANCE }?.calculatedHours)
        assertEquals(17f, workShiftHours2.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours)

        // sunday work (2015-01-05)
        val workEventSaturday = tb.manager.workEvents.createWorkEvent(employee1, "2025-01-04T23:00:00Z", WorkEventType.BREWERY)
        val workEventSunday = tb.manager.workEvents.createWorkEvent(employee1, "2025-01-05T08:00:00Z", WorkEventType.DRIVE)
        val brewerySunday = tb.manager.workEvents.createWorkEvent(employee1, "2025-01-05T20:00:00Z", WorkEventType.SHIFT_END)

        val workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1, employeeWorkShiftId = workEventSaturday.employeeWorkShiftId)
        assertEquals(WorkType.entries.size, workShiftHours3.size)
        assertEquals(21f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(2f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.EVENING_ALLOWANCE }?.calculatedHours)
        assertEquals(7f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.NIGHT_ALLOWANCE }?.calculatedHours)
        assertEquals(20f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours)
    }

    /**
     * Tests Work Shift Hours updates when work events are created/updated/removed
     */
    @Test
    fun testWorkShiftHoursUpdates() = createTestBuilder().use { tb ->
        // starting at 16 30
        val employee1 = tb.manager.employees.createEmployee("01").id!!

        val firstPaidEvent = tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T16:30:00Z", WorkEventType.BREWERY)
        //16 30 - 17 30, 1h PAID_WORK
        val secondDriveEvent = tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T17:30:00Z", WorkEventType.DRIVE)
        //17 30 - 18 30, 1h PAID_WORK 0.5H EVENING_ALLOWANCE
        val thirdShiftEnd = tb.manager.workEvents.createWorkEvent(employee1, "2024-01-01T18:30:00Z", WorkEventType.SHIFT_END)

        var workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(WorkType.entries.size, workShiftHours3.size)
        assertEquals(2f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)

        // move time of shift end half hr earlier
        var updated = tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1,
            id = thirdShiftEnd.id!!,
            workEvent = thirdShiftEnd.copy(
                time = "2024-01-01T18:00:00Z"
            )
        )
        workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(1.5f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)


        //move time of shift end half hr later
        updated = tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1,
            id = thirdShiftEnd.id!!,
            workEvent = thirdShiftEnd.copy(
                time = "2024-01-01T19:00:00Z"
            )
        )
        workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(2.5f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(0f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.BREAK }?.calculatedHours)

        // change the type of the paid work event to break, so 1.5 hr of paid work turn into break instead
        updated = tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1,
            id = secondDriveEvent.id!!,
            workEvent = secondDriveEvent.copy(
                workEventType = WorkEventType.BREAK
            )
        )
        workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(1f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(1.5f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.BREAK }?.calculatedHours)

        //delete the break event
        tb.manager.workEvents.deleteWorkEvent(employee1, secondDriveEvent.id)
        workShiftHours3 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        assertEquals(2.5f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.PAID_WORK }?.calculatedHours)
        assertEquals(0f, workShiftHours3.find { it.workType == fi.metatavu.vp.test.client.models.WorkType.BREAK }?.calculatedHours)
    }

    /**
     * Tests that work events are correctly associated with work shifts of the correct day and their hours are calculated
     * for those fitting shifts
     */
    @Test
    fun testWorkShiftSelection(): Unit =  createTestBuilder().use { tb ->
        // Manually create 3 shifts
        val employee1 = tb.manager.employees.createEmployee("01").id!!
        val shift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = "2024-01-01",
                approved = false
            )
        )
        val shift2 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = "2024-01-02",
                approved = false
            )
        )
        val shift3 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                employeeId = employee1,
                date = "2024-01-03",
                approved = false
            )
        )

        // Create work events for the shift on 01-02
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-02T16:30:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-02T17:30:00Z", WorkEventType.SHIFT_END)

        // Hours are recorded for 01-02 shift
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1, employeeWorkShiftId = shift2.id)
        assertEquals(WorkType.entries.size, workShiftHours.size)
        assertEquals(1f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours)

        // record to the day without shift
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-04T16:30:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1, "2024-01-04T17:30:00Z", WorkEventType.SHIFT_END)

        // New shift is created for the day and hours are recoded there
        val workShiftHours2 = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1)
        val byWorkShifts = workShiftHours2.groupBy { it.employeeWorkShiftId }
        assertEquals(4, byWorkShifts.size)
        val createdShift = byWorkShifts.keys.find { it != shift1.id && it != shift2.id && it != shift3.id }
        assertEquals(WorkType.entries.size, byWorkShifts[createdShift]?.size)
        assertEquals(1f, byWorkShifts[createdShift]?.find { it.workType == WorkType.PAID_WORK }?.calculatedHours)

    }
}