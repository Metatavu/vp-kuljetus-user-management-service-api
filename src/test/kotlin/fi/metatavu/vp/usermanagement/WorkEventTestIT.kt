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
            workEventType = WorkEventType.SHIFT_END,
            time = OffsetDateTime.now().toString()
        )
        val updatedEndEvent = it.manager.workEvents.updateWorkEvent(employee1.id, created.id!!, updateData)

        it.manager.workEvents.addEmployeeShiftStartToCloseables(employeeId = employee1.id)

        assertEquals(updateData.id, updatedEndEvent.id)
        assertEquals(updateData.workEventType, updatedEndEvent.workEventType)
        assertEquals(
            OffsetDateTime.parse(updateData.time).toEpochSecond(),
            OffsetDateTime.parse(updatedEndEvent.time).toEpochSecond()
        )
        assertEquals(updateData.employeeId, updatedEndEvent.employeeId)

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
            workEvent = created.copy(time = OffsetDateTime.now().plusDays(10).toString()),
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
            id = updatedEndEvent.id!!,
            workEvent = updatedEndEvent.copy(time = OffsetDateTime.now().minusDays(10).toString()),
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

}