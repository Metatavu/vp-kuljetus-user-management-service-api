package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

/**
 * Work Types API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class WorkEventTestIT : AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val employee2 = it.manager.employees.createEmployee("2")

        val now = OffsetDateTime.now()
        it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.MEAT_CELLAR,
                time = now.toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent =  WorkEvent(
                workEventType = WorkEventType.OTHER_WORK,
                time = now.plusDays(1).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.BREWERY,
                time = now.plusHours(3).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee2.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.BREWERY,
                time = now.toString(),
                employeeId = employee2.id,
                id = UUID.randomUUID()
            )
        )

        val employee1Records = it.manager.workEvents.listWorkEvents(employee1.id)
        assertEquals(4, employee1Records.size) // 3 events + shift start
        val employee2Records = it.manager.workEvents.listWorkEvents(employee2.id)
        assertEquals(2, employee2Records.size) // 1 event + shift start
        val afterFilter = it.manager.workEvents.listWorkEvents(employee1.id, after = now.plusHours(2))
        assertEquals(2, afterFilter.size)
        val beforeFilter = it.manager.workEvents.listWorkEvents(employee1.id, before = now.plusHours(1))
        assertEquals(2, beforeFilter.size)  // 1 event + shift start

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)
    }

    @Test
    fun testCreate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val now = OffsetDateTime.now()

        val data = WorkEvent(
            workEventType = WorkEventType.DRY,
            time = now.toString(),
            employeeId = employee1.id!!
        )
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent = data
        )

        assertNotNull(created.id)
        assertEquals(data.workEventType, created.workEventType)
        assertEquals(
            OffsetDateTime.parse(data.time).toEpochSecond(),
            OffsetDateTime.parse(created.time).toEpochSecond()
        )
        assertEquals(data.employeeId, created.employeeId)

        it.manager.workEvents.assertCreateFail(
            employeeId = employee1.id,
            workEvent = data.copy(employeeId = UUID.randomUUID()),
            expectedStatus = 400
        )

        //Check that shift start event was created automatically
        val allEvents = it.manager.workEvents.listWorkEvents(employee1.id)
        assertEquals(2, allEvents.size)
        val shiftStart = allEvents.find { e -> e.workEventType == WorkEventType.SHIFT_START }
        assertNotNull(shiftStart)

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)

        // No adding shift end before shift start
        it.manager.workEvents.assertCreateFail(
            employeeId = employee1.id,
            workEvent = created.copy(
                workEventType = WorkEventType.SHIFT_END,
                time = now.minusDays(10).toString()
            ),
            expectedStatus = 400
        )
        // No adding double shift start/end events
        it.manager.workEvents.assertCreateFail(
            employeeId = employee1.id,
            workEvent = created.copy(
                workEventType = WorkEventType.SHIFT_START,
                time = now.plusDays(10).toString()
            ),
            expectedStatus = 400
        )

        // no adding events outside of shift start/end
        it.manager.workEvents.assertCreateFail(
            employeeId = employee1.id,
            workEvent = created.copy(
                workEventType = WorkEventType.SHIFT_END,
                time = now.minusDays(10).toString()
            ),
            expectedStatus = 400
        )

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)
    }

    @Test
    fun testWorkEventListSecondarySorting() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")

        val now = OffsetDateTime.now()

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.LOGOUT,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVER_CARD_REMOVED,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVER_CARD_INSERTED,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.LOGIN,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                time = now.toString(),
                employeeId = employee.id,
                id = UUID.randomUUID()
            )
        )

        assertEquals(
            1,
            it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).size,
            "There should be only one work shift"
        )

        val workEvents = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        val eventTime = workEvents[1].time

        workEvents.forEach { event ->
            if (event.workEventType != WorkEventType.SHIFT_START) {
                assertEquals(
                    eventTime,
                    event.time,
                    "All events except SHIFT_START should have the same time"
                )
            }
        }

        assertEquals(
            WorkEventType.DRIVER_CARD_INSERTED,
            workEvents[0].workEventType,
            "First event should be DRIVER_CARD_INSERTED"
        )

        assertEquals(
            WorkEventType.LOGIN,
            workEvents[1].workEventType,
            "Second event should be LOGIN"
        )

        assertEquals(
            WorkEventType.DRIVE,
            workEvents[2].workEventType,
            "Third event should be DRIVE"
        )

        assertEquals(
            WorkEventType.LOGOUT,
            workEvents[3].workEventType,
            "Fourth event should be LOGOUT"
        )

        assertEquals(
            WorkEventType.DRIVER_CARD_REMOVED,
            workEvents[4].workEventType,
            "Fifth event should be DRIVER_CARD_REMOVED"
        )

        assertEquals(
            WorkEventType.SHIFT_END,
            workEvents[5].workEventType,
            "Sixth event should be SHIFT_END"
        )
    }

    @Test
    fun testFind() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                time = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val found = it.manager.workEvents.findWorkEvent(employee1.id, created.id!!)

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)

        assertEquals(created.id, found.id)
        assertEquals(created.workEventType, found.workEventType)
        assertEquals(
            OffsetDateTime.parse(created.time).toEpochSecond(),
            OffsetDateTime.parse(found.time).toEpochSecond()
        )
        assertEquals(created.employeeId, found.employeeId)
    }

    @Test
    fun testUpdate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.UNLOADING,
                time = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val updateData = created.copy(
            workEventType = WorkEventType.BREWERY,
            time = OffsetDateTime.now().toString(),
            costCenter = "centre"
        )
        val shiftStartEvent = it.manager.workEvents.listWorkEvents(employee1.id).find { e -> e.workEventType == WorkEventType.SHIFT_START }!!
        val updatedEvent = it.manager.workEvents.updateWorkEvent(employee1.id, created.id!!, updateData)

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)

        assertEquals(updateData.id, updatedEvent.id)
        assertEquals(updateData.costCenter, updatedEvent.costCenter)
        assertEquals(updateData.workEventType, updatedEvent.workEventType)
        assertEquals(
            OffsetDateTime.parse(updateData.time).toEpochSecond(),
            OffsetDateTime.parse(updatedEvent.time).toEpochSecond()
        )
        assertEquals(updateData.employeeId, updatedEvent.employeeId)

        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = created.id,
            workEvent = updateData.copy(employeeId = UUID.randomUUID()),
            expectedStatus = 400
        )

        // Cannot update shift start/end event types
        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = created.id,
            workEvent = updateData.copy(workEventType = WorkEventType.SHIFT_START),
            expectedStatus = 400
        )

        // cannot update event to be outside of shift start/end
        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = created.id,
            workEvent = created.copy(time = OffsetDateTime.now().minusDays(10).toString()),
            expectedStatus = 400
        )

        // cannot update event to have doulbe shift start/end events
        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = created.id,
            workEvent = created.copy(workEventType = WorkEventType.SHIFT_START),
            expectedStatus = 400
        )

        // cannot update shift end event to be before shift start
        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = updatedEvent.id!!,
            workEvent = updatedEvent.copy(time = OffsetDateTime.now().minusDays(10).toString()),
            expectedStatus = 400
        )

        // Cannot move shift start event to not be the first/last event in the list
        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = shiftStartEvent.id!!,
            workEvent = shiftStartEvent.copy(time = OffsetDateTime.now().toString()),
            expectedStatus = 400
        )

    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.VEGETABLE,
                time = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val all = it.manager.workEvents.listWorkEvents(employee1.id)
        assertEquals(2, all.size)

        // Cannot delete shift start/end events if there are other events present
        val shiftCreate = all.find { e -> e.workEventType == WorkEventType.SHIFT_START }!!
        it.manager.workEvents.assertDeleteFail(employee1.id, shiftCreate.id!!, 400)

        it.manager.workEvents.deleteWorkEvent(employee1.id, created.id!!)
        it.manager.workEvents.deleteWorkEvent(employee1.id, shiftCreate.id)
    }

    @Test
    fun testRemoveEventDuplicates() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")

        val date1 = OffsetDateTime.now().minusDays(7).withHour(18)

        val event1 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date1.minusHours(9).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date1.minusHours(8).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date1.minusHours(7).toString(),
                employeeId = employee.id
            )
        )

        val event2 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date1.minusHours(6).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date1.minusHours(5).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date1.minusHours(4).toString(),
                employeeId = employee.id
            )
        )
        val event3 = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date1.minusHours(3).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date1.minusHours(2).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date1.minusHours(1).toString(),
                employeeId = employee.id
            )
        )

        val endEvent = it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                time = date1.toString(),
                employeeId = employee.id
            )
        )

        assertEquals(11, it.manager.workEvents.listWorkEvents(employeeId = employee.id, max = 100).size, "There should be 11 events before the duplicate removal cron job has run")

        it.setCronKey().workEvents.removeEventDuplicates()

        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id).reversed()

        assertEquals(5, events.size, "There should be 5 events after the duplicate removal cron job has run")

        assertEquals(WorkEventType.SHIFT_START, events[0].workEventType, "First event should be SHIFT_START")
        assertEquals(event1.id, events[1].id, "Second event should be the first DRIVE event")
        assertEquals(event2.id, events[2].id, "Third event should be the first FROZEN event")
        assertEquals(event3.id, events[3].id, "Fourth event should be the first GREASE event")
        assertEquals(endEvent.id, events[4].id, "Fifth event should be the SHIFT_END event")

        it.setCronKey(UUID.randomUUID().toString()).workEvents.assertRemoveEventDuplicatesUnauthorized()
        it.manager.workEvents.assertRemoveEventDuplicatesUnauthorized()

        val date2 = OffsetDateTime.now().minusDays(1).withHour(18)

         it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date2.minusHours(9).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date2.minusHours(8).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.DRIVE,
                time = date2.minusHours(7).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date2.minusHours(6).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date2.minusHours(5).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.FROZEN,
                time = date2.minusHours(4).toString(),
                employeeId = employee.id
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date2.minusHours(3).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date2.minusHours(2).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.GREASE,
                time = date2.minusHours(1).toString(),
                employeeId = employee.id
            )
        )

        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                time = date2.toString(),
                employeeId = employee.id
            )
        )

        val newShift = it.manager.workShifts.listEmployeeWorkShifts(employeeId = employee.id).first()
        it.setCronKey().workEvents.removeEventDuplicates()
        assertEquals(11, it.manager.workEvents.listWorkEvents(employeeId = employee.id, employeeWorkShiftId = newShift.id, max = 100).size, "There should be 11 events in the new shift even after duplicate removal because grace period has not passed yet")
        assertEquals(16, it.manager.workEvents.listWorkEvents(employeeId = employee.id, max = 100).size, "There should be 16 events in total")
    }
}