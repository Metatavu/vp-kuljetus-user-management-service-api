package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.messaging.RoutingKey
import fi.metatavu.vp.messaging.client.MessagingClient
import fi.metatavu.vp.messaging.events.TaskGlobalEvent
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.RabbitMQTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

@QuarkusTest
@TestProfile(RabbitMQTestProfile::class)
class TaskEventTestsIT: AbstractFunctionalTest() {
    @Test
    fun createTaskEvent() = createTestBuilder().use {
        val employee = it.manager.employees.createEmployee("1")
        it.manager.workEvents.createWorkEvent(
            employeeId = employee.id!!,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = OffsetDateTime.now().toString(),
                workEventType = WorkEventType.DRIVE
            )
        )

        val truckId = UUID.randomUUID()
        MessagingClient.publishMessage(
            TaskGlobalEvent(
                userId = employee.id,
                taskType = "LOAD",
                taskStatus = "IN_PROGRESS",
                truckId = truckId,
                eventTime = OffsetDateTime.now()
            ),
            routingKey = RoutingKey.TASK
        )

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            val workEvents = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
            workEvents.size >= 3
        }

        val events = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(3, events.size)

        val taskEvent = events.first()
        assertEquals(WorkEventType.LOADING, taskEvent.workEventType)
        assertEquals(truckId, taskEvent.truckId)
        assertEquals(employee.id, taskEvent.employeeId)

        it.manager.workEvents.assertCreateFail(
            employeeId = employee.id,
            workEvent = WorkEvent(
                employeeId = employee.id,
                time = OffsetDateTime.now().toString(),
                workEventType = WorkEventType.GREASE
            ),
            expectedStatus = 400
        )

        MessagingClient.publishMessage(
            TaskGlobalEvent(
                userId = employee.id,
                taskType = "UNLOAD",
                taskStatus = "IN_PROGRESS",
                truckId = truckId,
                eventTime = OffsetDateTime.now()
            ),
            routingKey = RoutingKey.TASK
        )

        MessagingClient.publishMessage(
            TaskGlobalEvent(
                userId = employee.id,
                taskType = "LOAD",
                taskStatus = "DONE",
                truckId = truckId,
                eventTime = OffsetDateTime.now()
            ),
            routingKey = RoutingKey.TASK
        )

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            val workEvents = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
            workEvents.size == 4
        }

        val events2 = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(4, events2.size)

        val newEvent = events2.first()
        assertEquals(WorkEventType.DRIVE, newEvent.workEventType)

        MessagingClient.publishMessage(
            TaskGlobalEvent(
                userId = employee.id,
                taskType = "UNLOAD",
                taskStatus = "IN_PROGRESS",
                truckId = truckId,
                eventTime = OffsetDateTime.now()
            ),
            routingKey = RoutingKey.TASK
        )

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            val workEvents = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
            workEvents.size == 5
        }

        val events3 = it.manager.workEvents.listWorkEvents(employeeId = employee.id)
        assertEquals(5, events3.size)

        val unloadEvent = events3.first()
        assertEquals(WorkEventType.UNLOADING, unloadEvent.workEventType)
    }
}