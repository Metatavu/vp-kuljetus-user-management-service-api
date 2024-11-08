package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.AbsenceType
import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.PerDiemAllowanceType
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
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
                startedAt = now.toLocalDate().toString()
            )
        )
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(2).toLocalDate().toString(),
                employeeId = employee2,
                approved = false,
                startedAt = now.plusDays(2).toLocalDate().toString(),
            )
        )
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(3).toLocalDate().toString(),
                employeeId = employee2,
                approved = false,
                startedAt = now.plusDays(3).toLocalDate().toString(),
            )
        )

        // This shift is not included in the time filtering (allShifts3, allShifts4) because startedAt is not set
        it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee2,
            workShift = EmployeeWorkShift(
                date = now.plusDays(3).toLocalDate().toString(),
                employeeId = employee2,
                approved = false
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
    }


    /**
     * tests:
     *  - work shift creation from the endpoint
     *  - work shift creation based on work events
     *  - work events additional creation based on shifts
     */
    @Test
    fun testWorkShiftCreate() = createTestBuilder().use {
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

        val created = it.manager.workShifts.createEmployeeWorkShift(
            employeeId = employee1.id,
            workShift = EmployeeWorkShift(
                date = now.toLocalDate().toString(),
                employeeId = employee1.id,
                approved = false,
                startedAt = now.toLocalDate().toString(),
                endedAt = now.plusHours(25).toLocalDate().toString()
            )
        )
        assertNotNull(created)

        // Check that work shift was created based on work events
        val employee1AllWorkShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee1.id
        )
        assertEquals(4, employee1AllWorkShifts.size)

        val employee2AllWorkShifts = it.manager.workShifts.listEmployeeWorkShifts(
            employeeId = employee2.id
        )
        assertEquals(2, employee2AllWorkShifts.size)

        val workevents = it.manager.workEvents.listWorkEvents(
            employeeId = employee1.id
        )
        workevents.forEach { event ->
            assertNotNull(event.employeeWorkShiftId)
        }

        it.manager.workShifts.assertCreateFail(UUID.randomUUID(), EmployeeWorkShift(date = now.toLocalDate().toString(), employeeId = UUID.randomUUID(), approved = false), 400)
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
        assertEquals(getWorkEventDate(workEvent1.time), workShifts[0].startedAt)
        assertEquals(getWorkEventDate(workEvent2.time), workShifts[0].endedAt)

        val workEvent3 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(13).toString(), WorkEventType.SHIFT_START)
        val workEvent4 = tb.manager.workEvents.createWorkEvent(employeeId, now.minusDays(11).toString(), WorkEventType.SHIFT_END)

        val workShifts2 = tb.manager.workShifts.listEmployeeWorkShifts(employeeId = employeeId)
        assertEquals(2, workShifts2.size)
        val anotherShift = workShifts2.find { it.startedAt == getWorkEventDate(workEvent3.time) }!!
        assertNotNull(anotherShift)
        assertEquals(getWorkEventDate(workEvent3.time), anotherShift.startedAt)
        assertEquals(getWorkEventDate(workEvent4.time), anotherShift.endedAt)
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
        assertEquals(getWorkEventDate(updatedShiftStart.time), workShifts[0].startedAt)

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
        assertEquals(getWorkEventDate(updatedShiftStart.time), workShifts[0].startedAt)
        assertEquals(getWorkEventDate(updatedShiftEnd.time), workShifts[0].endedAt)


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
            )
        )

        val updatedWorkShift = it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1,
            id = workShift.id!!,
            workShift = workShift.copy(
                approved = true,
                dayOffWorkAllowance = true,
                perDiemAllowance = PerDiemAllowanceType.FULL,
                absence = AbsenceType.OFFICIAL_DUTIES,
                notes = "approved"
            )
        )
        assertEquals(true, updatedWorkShift.approved, "After update, work shift should be approved")
        assertEquals(true, updatedWorkShift.dayOffWorkAllowance, "After update, day off work allowance should be true")
        assertEquals(PerDiemAllowanceType.FULL, updatedWorkShift.perDiemAllowance, "After update, per diem allowance should be FULL")
        assertEquals(AbsenceType.OFFICIAL_DUTIES, updatedWorkShift.absence, "After update, absence should be OFFICIAL_DUTIES")
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
                approved = false
            )
        )

        it.manager.workShifts.deleteEmployeeWorkShift(employee1, workShift.id!!)

        val workShifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee1)
        assertEquals(0, workShifts.size)
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