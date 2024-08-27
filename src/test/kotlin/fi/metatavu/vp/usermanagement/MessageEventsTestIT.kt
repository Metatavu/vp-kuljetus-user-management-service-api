package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.messaging.client.MessagingClient
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.RabbitMQTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Tests events that are arriving from messaging service
 */
@QuarkusTest
@TestProfile(RabbitMQTestProfile::class)
class MessageEventsTestIT: AbstractFunctionalTest() {

    /**
     * Tests driver working state change global event
     */
    @Test
    fun testDriverWorkingStateChangeGlobalEvent() = createTestBuilder().use { tb ->
        val drivers = tb.manager.drivers.listDrivers()
        val driverId = drivers[0].id!!
        val startWorkEvent = createWorkingStateEvent(driverId, WorkEventType.SHIFT_START)

        MessagingClient.publishMessage(startWorkEvent)
        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            tb.manager.timeEntries.listTimeEntries(driverId).size == 1
        }

        // Manually add it to closable since the time entries were created off-screen
        tb.manager.timeEntries.listTimeEntries(driverId).forEach {
            tb.manager.timeEntries.addClosable(it)
        }

        val startedTimeEntry = tb.manager.timeEntries.listTimeEntries(driverId)[0]
        assertEquals(driverId, startedTimeEntry.employeeId)
        assertNotNull(startedTimeEntry.workEventType)
        assertNotNull(startedTimeEntry.startTime)
        assertNull(startedTimeEntry.endTime)

        val endWorkDayEvent = createWorkingStateEvent(driverId, WorkEventType.SHIFT_END)
        MessagingClient.publishMessage(endWorkDayEvent)

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            tb.manager.timeEntries.listTimeEntries(driverId).size == 2
        }
        val finishedTimeEntry = tb.manager.timeEntries.listTimeEntries(driverId)[0]
        assertEquals(startedTimeEntry.id, finishedTimeEntry.id)
        assertEquals(driverId, finishedTimeEntry.employeeId)
        assertNotNull(finishedTimeEntry.workEventType)
        assertNotNull(finishedTimeEntry.startTime)
        assertNotNull(finishedTimeEntry.endTime)
    }

    private fun createWorkingStateEvent(
        driverId: UUID,
        workEventType: WorkEventType
    ): DriverWorkEventGlobalEvent {
        return DriverWorkEventGlobalEvent(
            driverId = driverId,
            workEventType = fi.metatavu.vp.api.model.WorkEventType.valueOf(workEventType.name),
            time = OffsetDateTime.now())
    }
}