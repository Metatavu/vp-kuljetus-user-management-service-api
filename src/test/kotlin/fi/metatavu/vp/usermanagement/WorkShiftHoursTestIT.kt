package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.*
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
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
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        val workShift = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(WorkType.entries.size, workShiftHours.size, "Same amount of work shift hours as work types")

        workShiftHours.forEach {
            assertEquals(workShift.id, it.employeeWorkShiftId, "Work shift hours are associated with the correct work shift")
            assertNull(it.actualHours, "Actual hours are not set")
        }
    }

    /**
     * Tests:
     *  - Work Shift Hours listing
     */
    @Test
    fun testWorkShiftHoursListing() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        val employee1WorkShift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val employee1WorkShift2 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().plusDays(2).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val employee2Id = tb.manager.employees.createEmployee("02").id!!
        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2Id,
            workShift = EmployeeWorkShift(
                employeeId = employee2Id,
                date = LocalDate.now().plusDays(3).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        val employee1WorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(WorkType.entries.size * 2, employee1WorkShiftHours.size, "Correct amount of work shift hours for employee 1")
        assertEquals(
            WorkType.entries.size,
            employee1WorkShiftHours.filter { it.employeeWorkShiftId == employee1WorkShift1.id }.size,
            "Work shift hours for the first work shift of employee 1"
        )
        assertEquals(
            WorkType.entries.size,
            employee1WorkShiftHours.filter { it.employeeWorkShiftId == employee1WorkShift2.id }.size,
            "Work shift hours for the second work shift of employee 1"
        )

        val employee2WorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee2Id)
        assertEquals(WorkType.entries.size, employee2WorkShiftHours.size, "Correct amount of work shift hours for employee 2")

        val paidWorkFromEmployee2WorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(
            employeeId = employee2Id,
            workType = WorkType.PAID_WORK
        )
        assertEquals(
            1,
            paidWorkFromEmployee2WorkShiftHours.size,
            "Correct amount of work shift hours for employee 2 with work type PAID_WORK"
        )

        val paidWorkForBothEmployees = tb.manager.workShiftHours.listWorkShiftHours(workType = WorkType.PAID_WORK)
        assertEquals(
            3,
            paidWorkForBothEmployees.size,
            "Correct amount of work shift hours for work type PAID_WORK of both employees"
        )

        val workShiftHoursByDate = tb.manager.workShiftHours.listWorkShiftHours(
            employeeWorkShiftStartedAfter = OffsetDateTime.now().plusDays(2).toString(),
            employeeWorkShiftStartedBefore = OffsetDateTime.now().plusDays(3).toString()
        )
        assertEquals(
            WorkType.entries.size * 2,
            workShiftHoursByDate.size,
            "Correct amount of work shift hours for work shifts listed by start times"
        )
    }

    /**
     * Tests:
     *  - Work Shift Hours find by id
     */
    @Test
    fun teatWorkShiftHoursFind() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)[0]
        val foundHours = tb.manager.workShiftHours.findWorkShiftHours(workShiftHours.id!!)
        assertEquals(workShiftHours.employeeWorkShiftId, foundHours.employeeWorkShiftId, "Employee work shift ID matches")
        assertEquals(workShiftHours.calculatedHours, foundHours.calculatedHours, "Calculated hours match")
        assertEquals(workShiftHours.actualHours, foundHours.actualHours, "Actual hours match")
        assertNotNull(foundHours.id, "ID is set")
    }

    /**
     * Tests:
     *  1 Updating is impossible is work shift is approved
     *  2 employeeId, work ShiftId, work type cannot be updated
     *  3 actual hours are updated by the supervisor
     */
    @Test
    fun testWorkShiftHoursUpdate() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        val workShift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)[0]

        val updateData = workShiftHours.copy(actualHours = 5f)

        //update the hours
        val updated = tb.manager.workShiftHours.updateWorkShiftHours(
            id = updateData.id!!,
            workShiftHours = updateData
        )
        assertEquals(5f, updated.actualHours, "Actual hours are updated")

        // cannot update other fields
        tb.manager.workShiftHours.assertUpdateFail(
            expectedStatus = 400,
            id = workShiftHours.id!!,
            workEvent = workShiftHours.copy(workType = WorkType.PAID_WORK)
        )

        // approve shift
        tb.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1Id,
            id = workShift1.id!!,
            workShift = workShift1.copy(approved = true)
        )

        // updating is not allowed if work shift is approved
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
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = LocalDate.now().plusDays(1).toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)

        tb.manager.workShiftHours.deleteWorkShiftHours(workShiftHours[0].id!!)
        val workShiftHoursAfterDeletion = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(workShiftHours.size - 1, workShiftHoursAfterDeletion.size, "One work shift hours was deleted")
    }

    /**
     * This is not a completed test since the work shifts by default are expected to have the calculatedHours filled.
     * This tests verifies if it is possible to run the task to update those hours
     */
   @Test
    fun testOngoingWorkShiftHoursCalculation() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        val now = OffsetDateTime.now()

        tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = now.toLocalDate().toString(),
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        tb.manager.workEvents.createWorkEvent(employee1Id, now.minusHours(1).toString(), WorkEventType.BREWERY)
        tb.setCronKey().workShifts.recalculateWorkShiftHours(2)
    }

    /**
     * Tests:
     * - Work Shift Hours calculations during listing
     */
    @Test
    fun testWorkShiftHoursAllowanceListingCalculations() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        // starting at 16.30
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T16:30:00Z", WorkEventType.BREWERY)
        // 16.30 - 17.30, 1h PAID_WORK
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T17:30:00Z", WorkEventType.DRIVE)
        // 17.30 - 18.30, 1h PAID_WORK, 0.5h EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T18:30:00Z", WorkEventType.AVAILABILITY)
        // 18.30 - 19.30, 1h PAID_WORK, 1h EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T19:30:00Z", WorkEventType.BREAK)
        // 19.30 - 20.30, 1h BREAK, 0.5h PAID_WORK, 0.5h EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T20:30:00Z", WorkEventType.FROZEN)
        // 20.30 - 21.30, 1h PAID_WORK, 1h FROZEN_ALLOWANCE, 1h EVENING_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T21:30:00Z", WorkEventType.AVAILABILITY)
        // 21.30 - 12.30, 15h PAID_WORK, 0.5h EVENING_ALLOWANCE, 8h NIGHT_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-02T12:30:00Z", WorkEventType.SHIFT_END)

        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(WorkType.entries.size, workShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(19.5f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(3.5f, workShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
        assertEquals(7.5f, workShiftHours.find { it.workType == WorkType.NIGHT_ALLOWANCE }?.calculatedHours, "Amount of night allowance")
        assertEquals(1f, workShiftHours.find { it.workType == WorkType.BREAK }?.calculatedHours, "Amount of break")
        assertEquals(1f, workShiftHours.find { it.workType == WorkType.FROZEN_ALLOWANCE }?.calculatedHours, "Amount of frozen allowance")
        assertEquals(0f, workShiftHours.find { it.workType == WorkType.JOB_SPECIFIC_ALLOWANCE }?.calculatedHours, "Amount of job specific allowance")
    }

    /**
     * Tests Work Shift Hours calculations during holidays
     */
    @Test
    fun testWorkShiftHolidaysCalculations() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        tb.manager.holidays.create(
            holiday = Holiday(
                date = "2025-01-01",
                name = "New Year",
                compensationType = CompensationType.PUBLIC_HOLIDAY_ALLOWANCE
            )
        )

        // events during the new year, starting at 18.30
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-12-31T18:30:00Z", WorkEventType.BREWERY)
        // 18.30 - 04.30, 10h PAID_WORK, 4h EVENING_ALLOWANCE, 6.5h NIGHT_ALLOWANCE, 4.5h HOLIDAY_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-01T04:30:00Z", WorkEventType.DRIVE)
        // 04.30 - 18.30, 14h PAID_WORK, 0.5h EVENING_ALLOWANCE, 1.5h NIGHT_ALLOWANCE, 14h HOLIDAY_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-01T18:30:00Z", WorkEventType.SHIFT_END)

        val newYearWorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(WorkType.entries.size, newYearWorkShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(24f, newYearWorkShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(4f, newYearWorkShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
        assertEquals(8f, newYearWorkShiftHours.find { it.workType == WorkType.NIGHT_ALLOWANCE }?.calculatedHours, "Amount of night allowance")
        assertEquals(18.5f, newYearWorkShiftHours.find { it.workType == WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours, "Amount of holiday allowance")

        // Shift marked as day off work allowance
        val shiftDayOff = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = "2025-01-03",
                approved = false,
                dayOffWorkAllowance = true,
                costCentersFromEvents = arrayOf()
            )
        )
        // Starting at 8.00
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-03T08:00:00Z", WorkEventType.BREWERY)
        // 08.00 - 01.00, 17h PAID_WORK, 4h EVENING_ALLOWANCE, 3h NIGHT_ALLOWANCE, 16h HOLIDAY_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-04T01:00:00Z", WorkEventType.SHIFT_END)

        val dayOffWorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id, employeeWorkShiftId = shiftDayOff.id)
        assertEquals(WorkType.entries.size, dayOffWorkShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(17f, dayOffWorkShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(4f, dayOffWorkShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
        assertEquals(5f, dayOffWorkShiftHours.find { it.workType == WorkType.NIGHT_ALLOWANCE }?.calculatedHours, "Amount of night allowance")
        assertEquals(16f, dayOffWorkShiftHours.find { it.workType == WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours, "Amount of holiday allowance")

        // sunday work (2015-01-05), starting at 23.00
        val workEventSaturday = tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-04T23:00:00Z", WorkEventType.BREWERY)
        // 23.00 - 08.00, 9h PAID_WORK, 4h EVENING_ALLOWANCE, 3h NIGHT_ALLOWANCE, 16h HOLIDAY_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-05T08:00:00Z", WorkEventType.DRIVE)
        // 08.00 - 20.00, 17h PAID_WORK, 4h EVENING_ALLOWANCE, 3h NIGHT_ALLOWANCE, 16h HOLIDAY_ALLOWANCE
        tb.manager.workEvents.createWorkEvent(employee1Id, "2025-01-05T20:00:00Z", WorkEventType.SHIFT_END)

        val saturdayWorkShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id, employeeWorkShiftId = workEventSaturday.employeeWorkShiftId)
        assertEquals(WorkType.entries.size, saturdayWorkShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(21f, saturdayWorkShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(4f, saturdayWorkShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
        assertEquals(5f, saturdayWorkShiftHours.find { it.workType == WorkType.NIGHT_ALLOWANCE }?.calculatedHours, "Amount of night allowance")
        assertEquals(20f, saturdayWorkShiftHours.find { it.workType == WorkType.HOLIDAY_ALLOWANCE }?.calculatedHours, "Amount of holiday allowance")
    }

    /**
     * Tests Work Shift Hours updates when work events are created/updated/removed
     */
    @Test
    fun testWorkShiftHoursUpdates() = createTestBuilder().use { tb ->
        val employee1Id = tb.manager.employees.createEmployee("01").id!!

        // starting at 16.30
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T16:30:00Z", WorkEventType.BREWERY)
        // 16.30 - 17.30, 1h PAID_WORK
        val secondDriveEvent = tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T17:30:00Z", WorkEventType.DRIVE)
        // 17.30 - 18.30, 1h PAID_WORK, 0.5h EVENING_ALLOWANCE
        val thirdShiftEnd = tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-01T18:30:00Z", WorkEventType.SHIFT_END)

        var workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(WorkType.entries.size, workShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(2f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(2f, workShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")

        // move SHIFT_END event 0.5h earlier
        tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1Id,
            id = thirdShiftEnd.id!!,
            workEvent = thirdShiftEnd.copy(time = "2024-01-01T18:00:00Z")
        )

        workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(1.5f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(1.5f, workShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")

        // move SHIFT_END event 1h later
        tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1Id,
            id = thirdShiftEnd.id,
            workEvent = thirdShiftEnd.copy(time = "2024-01-01T19:00:00Z")
        )

        workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(2.5f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(2.5f, workShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")

        // change the type of the PAID_WORK event to BREAK
        // -> 1.5h of PAID_WORK should turn into 1.5h of BREAK and 0.5h of PAID_WORK
        tb.manager.workEvents.updateWorkEvent(
            employeeId = employee1Id,
            id = secondDriveEvent.id!!,
            workEvent = secondDriveEvent.copy(workEventType = WorkEventType.BREAK)
        )

        workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(1.5f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(1.5f, workShiftHours.find { it.workType == WorkType.BREAK }?.calculatedHours, "Amount of break")

        // delete the break event
        // -> Previous event should now be 2,5h long
        // -> 1.5h of PAID_WORK, 1h of EVENING_ALLOWANCE and 0h of BREAK
        tb.manager.workEvents.deleteWorkEvent(employee1Id, secondDriveEvent.id)

        workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        assertEquals(2.5f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(2.5f, workShiftHours.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
        assertEquals(0f, workShiftHours.find { it.workType == WorkType.BREAK }?.calculatedHours, "Amount of break")
    }

    /**
     * Tests that work events are correctly associated with work shifts of the correct day and their hours are calculated
     * for those fitting shifts
     */
    @Test
    fun testWorkShiftSelection(): Unit =  createTestBuilder().use { tb ->
        // Manually create 3 shifts
        val employee1Id = tb.manager.employees.createEmployee("01").id!!
        val shift1 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = "2024-01-01",
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val shift2 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = "2024-01-02",
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )
        val shift3 = tb.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1Id,
            workShift = EmployeeWorkShift(
                employeeId = employee1Id,
                date = "2024-01-03",
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        // Create work events for the shift on 2024-01-02
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-02T16:30:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-02T17:30:00Z", WorkEventType.SHIFT_END)

        // Hours are recorded for 2024-01-02 shift
        val workShiftHours = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id, employeeWorkShiftId = shift2.id)
        assertEquals(WorkType.entries.size, workShiftHours.size, "Correct amount of work shift hours created")
        assertEquals(1f, workShiftHours.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")

        // record to a day without shift
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-04T16:30:00Z", WorkEventType.BREWERY)
        tb.manager.workEvents.createWorkEvent(employee1Id, "2024-01-04T18:30:00Z", WorkEventType.SHIFT_END)

        // New shift is created for the day and hours are recoded there
        val workShiftHoursForDateWithoutShift = tb.manager.workShiftHours.listWorkShiftHours(employeeId = employee1Id)
        val workShiftHoursByWorkShifts = workShiftHoursForDateWithoutShift.groupBy { it.employeeWorkShiftId }
        assertEquals(4, workShiftHoursByWorkShifts.size, "Correct amount of work shifts found")
        val createdShift = workShiftHoursByWorkShifts.keys.find { !listOf(shift1.id, shift2.id, shift3.id).contains(it) }
        assertEquals(WorkType.entries.size, workShiftHoursByWorkShifts[createdShift]?.size, "Correct amount of work shift hours created")
        assertEquals(2f, workShiftHoursByWorkShifts[createdShift]?.find { it.workType == WorkType.PAID_WORK }?.calculatedHours, "Amount of paid work")
        assertEquals(2f, workShiftHoursByWorkShifts[createdShift]?.find { it.workType == WorkType.EVENING_ALLOWANCE }?.calculatedHours, "Amount of evening allowance")
    }
}