package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.TestProfileSchedulerIgnoreWorkShiftStarts
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

/**
 * Tests the scheduled work shift stopper with a drive event
 */
@QuarkusTest
@TestProfile(TestProfileSchedulerIgnoreWorkShiftStarts::class)
class WorkShiftStopperDriveEventTestIT: AbstractFunctionalTest()  {
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
                workEventType = WorkEventType.SHIFT_START,
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
        Thread.sleep(5000)
        assertNull(it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first().endedAt)

        it.manager.workEvents.updateWorkEvent(
            employeeId = employee.id,
            id = event.id!!,
            workEvent = event.copy(time = now.minusHours(5).toString())
        )
        Thread.sleep(5000)
        val shifts = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id)
        assertEquals(1, shifts.size)
        assertNotNull(shifts.first().endedAt)
        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(3, events.size, "Shift should have 3 events")
        assertEquals(events[0].workEventType, WorkEventType.SHIFT_START, "First event should be SHIFT_START")
        assertEquals(events[1].workEventType, WorkEventType.DRIVE, "Second event should be DRIVE")
        assertEquals(events[2].workEventType, WorkEventType.SHIFT_END, "Third and final event should be SHIFT_END")
    }

}