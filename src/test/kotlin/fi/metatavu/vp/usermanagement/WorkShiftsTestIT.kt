package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
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
        createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, now) // first event triggers new shift
        val longBreakEvent = createWorkEvent(it, employee1.id, WorkEventType.BREAK, now.plusHours(1))    //Long break triggers new shift
        createWorkEvent(it, employee1.id, WorkEventType.BREWERY, now.plusHours(5))
        createWorkEvent(it, employee1.id, WorkEventType.SHIFT_END, now.plusHours(20))
        createWorkEvent(it, employee1.id, WorkEventType.OTHER_WORK, now.plusHours(25)) // ended shift triggers new shift
        val allWorkEvens = it.manager.workEvents.listWorkEvents(employeeId = employee1.id)
        assertEquals(8, allWorkEvens.size)

        // employee 2 events
        createWorkEvent(it, employee2.id!!, WorkEventType.MEAT_CELLAR, now)
        createWorkEvent(it, employee2.id, WorkEventType.BREAK, now.plusHours(1))
        createWorkEvent(it, employee2.id, WorkEventType.BREWERY, now.plusHours(3))
        val longUnknownEvent = createWorkEvent(it, employee2.id, WorkEventType.UNKNOWN, now.plusHours(5))
        createWorkEvent(it, employee2.id, WorkEventType.OTHER_WORK, now.plusHours(9))   // long unknown triggers new shift
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
     *  - work shift time, started, ended assignment based on work event time
     *  - work shift time assignment based on work event time update
     *  - work shift deletion based on all work events deletion
     */
    @Test
    fun testWorkShiftTimeAssignment() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")

        val shiftStart = createWorkEvent(it, employee1.id!!, WorkEventType.SHIFT_START, now)
        val meatCellar = createWorkEvent(it, employee1.id, WorkEventType.MEAT_CELLAR, now.plusDays(2))
        val shiftEnd = createWorkEvent(it, employee1.id, WorkEventType.SHIFT_END, now.plusDays(5))

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
        createWorkEvent(it, employee1.id!!, WorkEventType.MEAT_CELLAR, dayAgo)

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
                approved = false
            )
        )

        val approved = it.manager.workShifts.updateEmployeeWorkShift(
            employeeId = employee1,
            id = workShift.id!!,
            workShift = workShift.copy(approved = true)
        )
        assertEquals(true, approved.approved)

        // Employee ID not found
        it.manager.workShifts.assertUpdateFail(employeeId = UUID.randomUUID(), id = workShift.id, workShift = workShift, expectedStatus = 404)
        // Employee IDs don't fit
        it.manager.workShifts.assertUpdateFail(employeeId = employee2, id = workShift.id, workShift = workShift, expectedStatus = 404)
        // Cannot update other fields
        it.manager.workShifts.assertUpdateFail(employeeId = employee1, id = workShift.id, workShift = workShift.copy(date = "12-02-2010", approved = false), expectedStatus = 400)
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

    /**
     * Creates work event
     *
     * @param testBuilder test builder
     * @param employeeId employee id
     * @param workEventType work event type
     * @param time time
     * @return created work event
     */
    private fun createWorkEvent(
        testBuilder: TestBuilder,
        employeeId: UUID,
        workEventType: WorkEventType,
        time: OffsetDateTime
    ): WorkEvent {
        return testBuilder.manager.workEvents.createWorkEvent(
            employeeId = employeeId,
            workEvent = WorkEvent(
                workEventType = workEventType,
                time = time.toString(),
                employeeId = employeeId,
                id = UUID.randomUUID()
            )
        )
    }
}