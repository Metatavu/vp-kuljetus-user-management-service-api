package fi.metatavu.vp.usermanagement

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
                startTime = now.toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent =  WorkEvent(
                workEventType = WorkEventType.OTHER_WORK,
                startTime = now.plusDays(1).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent = WorkEvent(
                workEventType = WorkEventType.BREWERY,
                startTime = now.plusHours(3).toString(),
                employeeId = employee1.id,
                id = UUID.randomUUID()
            )
        )
        it.manager.workEvents.createWorkEvent(
            employeeId = employee2.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.BREWERY,
                startTime = now.toString(),
                employeeId = employee2.id,
                id = UUID.randomUUID()
            )
        )

        val employee1Records = it.manager.workEvents.listWorkEvents(employee1.id)
        assertEquals(3, employee1Records.size)
        val employee2Records = it.manager.workEvents.listWorkEvents(employee2.id)
        assertEquals(1, employee2Records.size)
        val afterFilter = it.manager.workEvents.listWorkEvents(employee1.id, after = now.plusHours(2))
        assertEquals(2, afterFilter.size)
        val beforeFilter = it.manager.workEvents.listWorkEvents(employee1.id, before = now.plusHours(1))
        assertEquals(1, beforeFilter.size)
    }

    @Test
    fun testCreate() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")

        val data = WorkEvent(
            workEventType = WorkEventType.DRY,
            startTime = OffsetDateTime.now().toString(),
            employeeId = employee1.id!!
        )
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id,
            workEvent = data
        )

        assertNotNull(created.id)
        assertEquals(data.workEventType, created.workEventType)
        assertEquals(
            OffsetDateTime.parse(data.startTime).toEpochSecond(),
            OffsetDateTime.parse(created.startTime).toEpochSecond()
        )
        assertEquals(data.employeeId, created.employeeId)

        it.manager.workEvents.assertCreateFail(
            employeeId = employee1.id,
            workEvent = data.copy(employeeId = UUID.randomUUID()),
            expectedStatus = 400
        )
    }

    @Test
    fun testFind() = createTestBuilder().use {
        val employee1 = it.manager.employees.createEmployee("1")
        val created = it.manager.workEvents.createWorkEvent(
            employeeId = employee1.id!!,
            workEvent = WorkEvent(
                workEventType = WorkEventType.SHIFT_END,
                startTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val found = it.manager.workEvents.findWorkEvent(employee1.id, created.id!!)

        assertEquals(created.id, found.id)
        assertEquals(created.workEventType, found.workEventType)
        assertEquals(
            OffsetDateTime.parse(created.startTime).toEpochSecond(),
            OffsetDateTime.parse(found.startTime).toEpochSecond()
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
                startTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        val updateData = created.copy(
            workEventType = WorkEventType.MEAT_CELLAR,
            startTime = OffsetDateTime.now().toString()
        )
        val updated = it.manager.workEvents.updateWorkEvent(employee1.id, created.id!!, updateData)

        assertEquals(updateData.id, updated.id)
        assertEquals(updateData.workEventType, updated.workEventType)
        assertEquals(
            OffsetDateTime.parse(updateData.startTime).toEpochSecond(),
            OffsetDateTime.parse(updated.startTime).toEpochSecond()
        )
        assertEquals(updateData.employeeId, updated.employeeId)

        it.manager.workEvents.assertUpdateFail(
            employeeId = employee1.id,
            id = created.id,
            workEvent = updateData.copy(employeeId = UUID.randomUUID()),
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
                startTime = OffsetDateTime.now().toString(),
                employeeId = employee1.id
            )
        )
        it.manager.workEvents.deleteWorkEvent(employee1.id, created.id!!)
        val all = it.manager.workEvents.listWorkEvents(employee1.id)
        assertEquals(0, all.size)
    }

}