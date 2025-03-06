package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.messaging.client.MessagingClient
import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.test.client.models.AbsenceType
import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.PerDiemAllowanceType
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.assertions.Assertions
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.OffsetDateTime
import java.util.*

/**
 * Tests for WorkShifts
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkShiftsTestIT : AbstractFunctionalTest() {

    val now: OffsetDateTime = OffsetDateTime.now()

    /**
     * Tests:
     * - Work shift listing and filtering, sorting
     */
    @Test
    fun testWorkShiftListing() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1").id!!
        val employee2 = it.manager.employees.createEmployee("2").id!!

        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee1,
                approved = false,
                startedAt = now.toString(),
                costCentersFromEvents = arrayOf()
            )
        )
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(2).toLocalDate().toString(),
                employeeId = employee2,
                approved = false,
                startedAt = now.plusDays(2).toString(),
                costCentersFromEvents = arrayOf()
            )
        )
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(3).toLocalDate().toString(),
                employeeId = employee2,
                approved = false,
                startedAt = now.plusDays(3).toString(),
                costCentersFromEvents = arrayOf()
            )
        )

        // This shift is not included in the time filtering (allShifts3, allShifts4) because startedAt is not set
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(3).toLocalDate().toString(),
                employeeId = employee2,
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        val employee1Shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1)
        assertEquals(1, employee1Shifts.size)

        val employee2Shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee2)
        assertEquals(3, employee2Shifts.size)

        val allShifts3 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee2, startedAfter = now.plusDays(3).toString())
        assertEquals(1, allShifts3.size)

        val allShifts4 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee2, startedBefore = now.plusDays(2).toString())
        assertEquals(1, allShifts4.size)

        val byDate = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee2, dateAfter = now.plusDays(3).toLocalDate().toString())
        assertEquals(2, byDate.size)

        val byDate1 = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee2, dateBefore = now.plusDays(2).toLocalDate().toString())
        assertEquals(1, byDate1.size)

        // Employee doesn't have any shifts
        it.employee.workShifts.assertListCount(employeeId = EMPLOYEE_USER_ID, expectedCount = 0)

        // Create shift for the employee
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = EMPLOYEE_USER_ID,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = EMPLOYEE_USER_ID,
                approved = false,
                startedAt = now.toString(),
                costCentersFromEvents = arrayOf()
            )
        )
        // Employee has one shift
        it.employee.workShifts.assertListCount(employeeId = EMPLOYEE_USER_ID, expectedCount = 1)
    }

    @Test
    fun testWorkShiftCostCenters() = createTestBuilder().use { it ->
        val costCenter1 = UUID.randomUUID().toString()
        val costCenter2 = UUID.randomUUID().toString()
        val employee1 = it.manager.employees.createEmployee("1")
        val createdShift = it.manager.workShifts.createEmployeeWorkShift(employeeId = employee1.id!!, EmployeeWorkShift(
            date = now.toLocalDate().toString(),
            employeeId = employee1.id,
            approved = false,
            startedAt = now.toString(),
            endedAt = now.plusHours(25).toString(),
            costCentersFromEvents = arrayOf()
        )
        )
        val created1 = it.manager.workEvents.createWorkEvent(employee1.id, now.toString(), WorkEventType.SHIFT_START)
        val created2 = it.manager.workEvents.createWorkEvent(employee1.id, now.plusMinutes(2).toString(), WorkEventType.DRIVE)
        it.manager.workEvents.updateWorkEvent(employeeId = employee1.id, created1.id!!, created1.copy(costCenter = costCenter1))
        it.manager.workEvents.updateWorkEvent(employeeId = employee1.id, created2.id!!, created2.copy(costCenter = costCenter2))

        val workShift = it.manager.workShifts.findEmployeeWorkShift(employeeId = employee1.id, id = createdShift.id!!)
        assertEquals(2, workShift.costCentersFromEvents.size)
        assertNotNull(workShift.costCentersFromEvents.find { costCenter -> costCenter == costCenter1 })
        assertNotNull(workShift.costCentersFromEvents.find { costCenter -> costCenter == costCenter2 })
    }

    /**
     * tests:
     *  - work shift creation from the endpoint
     *  - work shift creation based on work events
     *  - work events additional creation based on shifts
     */
    @Test
    fun testWorkShiftCreate() = createTestBuilder().use { it ->
        val employee1 = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        // employee 1 events
        it.manager.workEvents.createWorkEvent(employee1.id!!, now.toString(), WorkEventType.MEAT_CELLAR) // first event triggers new shift
        val longBreakEvent = it.manager.workEvents.createWorkEvent(employee1.id, now.plusHours(1).toString(), WorkEventType.BREAK)    //Long break triggers new shift

        it.manager.workEvents.createWorkEvent(employee1.id,  now.plusHours(5).toString(), WorkEventType.BREWERY)
        it.manager.workEvents.createWorkEvent(employee1.id, now.plusHours(20).toString(), WorkEventType.SHIFT_END)
        it.manager.workEvents.createWorkEvent(employee1.id, now.plusHours(25).toString(), WorkEventType.OTHER_WORK) // ended shift triggers new shift
        val allWorkEvens = it.manager.workEvents.listWorkEvents(employeeId = employee1.id)
        assertEquals(8, allWorkEvens.size)
        val createdWorkShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1.id,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee1.id,
                approved = false,
                startedAt = now.toString(),
                endedAt = now.plusHours(25).toString(),
                costCentersFromEvents = arrayOf()
            )
        )

        it.manager.workShifts.findEmployeeWorkShift(employeeId = employee1.id, id = createdWorkShift.id!!)

        // employee 2 events
        it.manager.workEvents.createWorkEvent(employee2.id!!, now.toString(), WorkEventType.MEAT_CELLAR)
        it.manager.workEvents.createWorkEvent(employee2.id, now.plusHours(1).toString(), WorkEventType.BREAK)
        it.manager.workEvents.createWorkEvent(employee2.id, now.plusHours(3).toString(), WorkEventType.BREWERY)
        val longUnknownEvent = it.manager.workEvents.createWorkEvent(employee2.id, now.plusHours(5).toString(), WorkEventType.UNKNOWN)
        it.manager.workEvents.createWorkEvent(employee2.id, now.plusHours(9).toString(), WorkEventType.OTHER_WORK)   // long unknown triggers new shift
        val allWorkEvens2 = it.manager.workEvents.listWorkEvents(employeeId = employee2.id)
        assertEquals(7, allWorkEvens2.size)

        // Check that last events (long break and long unknown) triggered new shifts and changed their own types
        assertEquals(WorkEventType.SHIFT_END, it.manager.workEvents.findWorkEvent(employee1.id, longBreakEvent.id!!).workEventType)
        assertEquals(WorkEventType.SHIFT_END, it.manager.workEvents.findWorkEvent(employee2.id, longUnknownEvent.id!!).workEventType)

        // Check that work shift was created based on work events
        val employee1AllWorkShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )
        // The first 2 shifts for employee 1 were supposed to get endedAt assigned automatically on next shift creation
        employee1AllWorkShifts.sortBy { it.startedAt }
        assertNotNull(employee1AllWorkShifts[0].endedAt)
        assertNotNull(employee1AllWorkShifts[1].endedAt)
        assertEquals(4, employee1AllWorkShifts.size)

        val employee2AllWorkShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee2.id
        )
        assertEquals(2, employee2AllWorkShifts.size)
        // The first shift for employee 1 is supposed to have endedAt set automatically on next shift creation
        employee2AllWorkShifts.sortBy { it.startedAt }
        assertNotNull(employee2AllWorkShifts[0].endedAt)

        val workevents = it.manager.workEvents.listWorkEvents(
            employeeId = employee1.id
        )
        workevents.forEach { event ->
            assertNotNull(event.employeeWorkShiftId)
        }

        it.manager.workShifts.assertCreateFail(UUID.randomUUID(), EmployeeWorkShift(date = now.toLocalDate().toString(), employeeId = UUID.randomUUID(), approved = false, costCentersFromEvents = arrayOf()), 400)
    }

    /**
     * Tests:
     * - work shift creation based on work events in the past
     */
    @Test
    fun testCreateShiftsInPast() = createTestBuilder().use { tb ->
        val employeeId = tb.manager.employees.createEmployee("1").id!!
        val now = OffsetDateTime.now()

        val workEvent1 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(10).toString(), WorkEventType.SHIFT_START)
        val workEvent2 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(5).toString(), WorkEventType.SHIFT_END)

        val workShifts = tb.manager.workShifts.listEmployeeWorkShifts(employeeId = employeeId)
        assertEquals(1, workShifts.size)
        assertEquals(now.minusDays(10).toLocalDate().toString(), workShifts[0].date)
        Assertions.assertOffsetDateTimeEquals(workEvent1.time, workShifts[0].startedAt)
        Assertions.assertOffsetDateTimeEquals(workEvent2.time, workShifts[0].endedAt)

        val workEvent3 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(13).toString(), WorkEventType.SHIFT_START)
        val workEvent4 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(11).toString(), WorkEventType.SHIFT_END)

        val workShifts2 = tb.manager.workShifts.listEmployeeWorkShifts(employeeId = employeeId)
        assertEquals(2, workShifts2.size)
        val anotherShift = workShifts2.find {
            OffsetDateTime.parse(it.startedAt!!).toEpochSecond() == OffsetDateTime.parse(workEvent3.time).toEpochSecond()
        }!!
        assertNotNull(anotherShift)
        Assertions.assertOffsetDateTimeEquals(workEvent3.time, anotherShift.startedAt)
        Assertions.assertOffsetDateTimeEquals(workEvent4.time, anotherShift.endedAt)
        assertEquals(now.minusDays(13).toLocalDate().toString(), anotherShift.date)

    }

    /**
     * Tests:
     *  - work shift time, started, ended assignment based on work event time
     *  - work shift time assignment based on work event time update
     *  - work shift deletion based on all work events deletion
     */
    @Test
    fun testWorkShiftTimeAssignment() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")

        val shiftStart = it.manager.workEvents.createWorkEvent(employee1.id!!, now.toString(), WorkEventType.SHIFT_START)
        val meatCellar = it.manager.workEvents.createWorkEvent(employee1.id, now.plusDays(2).toString(), WorkEventType.MEAT_CELLAR)
        val shiftEnd = it.manager.workEvents.createWorkEvent(employee1.id,  now.plusDays(5).toString(), WorkEventType.SHIFT_END)

        var workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1.id)
        assertEquals(1, workShifts.size)
        assertEquals(getWorkEventDate(shiftStart.time), workShifts[0].date)
        /* Order of events in time : shift start, meat cellar, shift end */

        // move shift start time to earlier
        val updatedShiftStart = it.manager.workEvents.updateWorkEvent(
            employeeId = employee1.id,
            id = shiftStart.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_START,
                time = now.minusDays(1).toString(),
                employeeId = employee1.id
            )
        )
        workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1.id)
        assertEquals(getWorkEventDate(updatedShiftStart.time), workShifts[0].date)
        Assertions.assertOffsetDateTimeEquals(updatedShiftStart.time, workShifts[0].startedAt)

        // Update work shift end to later
        val updatedShiftEnd = it.manager.workEvents.updateWorkEvent(
            employeeId = employee1.id,
            id = shiftEnd.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                time = now.plusDays(7).toString(),
                employeeId = employee1.id
            )
        )
        workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1.id)
        assertEquals(getWorkEventDate(updatedShiftStart.time), workShifts[0].date)
        Assertions.assertOffsetDateTimeEquals(updatedShiftStart.time, workShifts[0].startedAt)
        Assertions.assertOffsetDateTimeEquals(updatedShiftEnd.time, workShifts[0].endedAt)


        // Delete meat cellar event
        it.manager.workEvents.deleteWorkEvent(employeeId = employee1.id, id = meatCellar.id!!)

        // Delete all work events and check that it deleted the work shift
        it.manager.workEvents.listWorkEvents(employeeId = employee1.id).forEach { event ->
            it.manager.workEvents.deleteWorkEvent(employeeId = employee1.id, id = event.id!!)
        }
        workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1.id)
        assertEquals(0, workShifts.size)
    }


    /**
     * Tests:
     *  - finding work shift by id
     *  - work shift initially is not approved
     */
    @Test
    fun testWorkShiftFind() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val dayAgo = OffsetDateTime.now().minusDays(1)
        it.manager.workEvents.createWorkEvent(employee1.id!!, dayAgo.toString(), WorkEventType.MEAT_CELLAR,)

        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )

        val foundWorkShift = it.manager.workShifts.findEmployeeWorkShift(employee1.id, workShifts[0].id!!)
        assertEquals(workShifts[0].id, foundWorkShift.id)
        assertEquals(workShifts[0].date, foundWorkShift.date)
        assertEquals(workShifts[0].employeeId, foundWorkShift.employeeId)
        assertEquals(false, foundWorkShift.approved)

    }

    /**
     * Tests:
     *  - approving work shift                                      +
     */
    @Test
    fun testWorkShiftUpdate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1").id!!
        val employee2 = it.manager.employees.createEmployee("2").id!!

        val workShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee1,
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        val updatedWorkShift = it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true,
                dayOffWorkAllowance = true,
                perDiemAllowance = PerDiemAllowanceType.FULL,
                absence = AbsenceType.COMPENSATORY_LEAVE,
                notes = "approved"
            )
        )
        assertEquals(true, updatedWorkShift.approved, "After update, work shift should be approved")
        assertEquals(true, updatedWorkShift.dayOffWorkAllowance, "After update, day off work allowance should be true")
        assertEquals(PerDiemAllowanceType.FULL, updatedWorkShift.perDiemAllowance, "After update, per diem allowance should be FULL")
        assertEquals(AbsenceType.COMPENSATORY_LEAVE, updatedWorkShift.absence, "After update, absence should be COMPENSATORY_LEAVE")
        assertEquals("approved", updatedWorkShift.notes, "After update, notes should be 'approved'")
        // Employee ID not found
        it.manager.workShifts.assertUpdateFail(
            employeeId = UUID.randomUUID(),
            id = workShift.id,
            workShift = workShift,
            expectedStatus = 404
        )

        // Employee IDs don't fit
        it.manager.workShifts.assertUpdateFail(
            employeeId = employee2,
            id = workShift.id,
            workShift = workShift,
            expectedStatus = 404
        )

        // Cannot update when already approved
        it.manager.workShifts.assertUpdateFail(
            employeeId = employee1,
            id = updatedWorkShift.id!!,
            workShift = updatedWorkShift.copy(notes = "Trying to update already approved"),
            expectedStatus = 400
        )
    }

    /**
     * Tests:
     * - work shift deletion
     */
    @Test
    fun testWorkShiftDelete() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1").id!!
        val workShift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee1,
                approved = false,
                costCentersFromEvents = arrayOf()
            )
        )

        it.manager.workShifts.deleteEmployeeWorkShift(employee1, workShift.id!!)

        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1)
        assertEquals(0, workShifts.size)
    }

    @Test
    fun testWorkShiftEndSchedulerBreakEvent() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()
        val shift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            EmployeeWorkShift(
                date = now.minusHours(4).toLocalDate().toString(),
                approved = true,
                employeeId = employee.id,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = now.minusHours(4).toString(),
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id!!
            )
        )

        val event = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.BREAK,
                time = now.minusHours(2).toString(),
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id
            )
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event.id!!,
            workEvent = event.copy(time = now.minusHours(3).toString())
        )

        assertNull(it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first().endedAt)

        it.setCronKey().workShifts.endWorkshifts()

        val shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id)
        assertEquals(1, shifts.size, "There should be one shift")
        assertNotNull(shifts.first().endedAt, "Shift should have ended")
        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(3, events.size, "Shift should have 3 events")
        assertEquals(events[2].workEventType, WorkEventType.SHIFT_START, "Last event should be shift start")
        assertEquals(events[1].workEventType, WorkEventType.DRIVE, "Second event should be drive")
        assertEquals(events[0].workEventType, WorkEventType.SHIFT_END, "First event should be shift end")
    }

    @Test
    fun testWorkShiftEndSchedulerStartEvent() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()
        val shift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                approved = true,
                employeeId = employee.id,
                costCentersFromEvents = emptyArray()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_START,
                time = now.minusHours(5).toString(),
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id
            )
        )

        it.setCronKey().workShifts.endWorkshifts()
        val shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id)
        assertEquals(1, shifts.size, "There should be 1 shift")
        assertNotNull(shifts.first().endedAt, "The shift should have ended")
        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(3, events.size, "The shift should have 3 events")
        assertNotNull(
            events.find { foundEvent -> foundEvent.workEventType == WorkEventType.SHIFT_START },
            "Events should contain SHIFT_START"
        )
        assertNotNull(
            events.find { foundEvent -> foundEvent.workEventType == WorkEventType.UNKNOWN },
            "Events should contain UNKNOWN"
        )
        assertNotNull(
            events.find { foundEvent -> foundEvent.workEventType == WorkEventType.SHIFT_END },
            "Events should contain SHIFT_END"
        )
    }

    @Test
    fun testWorkShiftEndSchedulerDriveEvent() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()
        val shift = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee.id!!,
            EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                approved = true,
                employeeId = employee.id,
                costCentersFromEvents = emptyArray()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.OTHER_WORK,
                time = now.minusHours(6).toString(),
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id!!
            )
        )

        val event = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = now.minusHours(2).toString(),
                employeeId = employee.id,
                id = UUID.randomUUID(),
                employeeWorkShiftId = shift.id
            )
        )

        assertNull(
            it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first().endedAt,
            "Work shift should not have ended yet"
        )

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event.id!!,
            workEvent = event.copy(time = now.minusHours(5).toString())
        )

        it.setCronKey().workShifts.endWorkshifts()

        val shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id)
        assertEquals(1, shifts.size, "There should be one work shift for the employee")
        assertNotNull(shifts.first().endedAt, "The work shift should be finished")
        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(4, events.size, "Shift should have 4 events")
        assertEquals(events[3].workEventType, WorkEventType.SHIFT_START, "Last and final event should be SHIFT_END")
        assertEquals(events[2].workEventType, WorkEventType.OTHER_WORK, "Third event should be OTHER_WORK")
        assertEquals(events[1].workEventType, WorkEventType.DRIVE, "Second event should be DRIVE")
        assertEquals(events[0].workEventType, WorkEventType.SHIFT_END, "First event should be SHIFT_START")
    }

    /**
     * Translates offset date time string of work event to string of local date
     *
     * @param time offset date time string
     * @return string of local date
     */
    private fun getWorkEventDate(time: String): String {
        return OffsetDateTime.parse(time).toLocalDate().toString()
    }
}