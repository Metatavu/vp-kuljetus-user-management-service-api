package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.messaging.events.DriverWorkingStateChangeGlobalEvent
import fi.metatavu.vp.messaging.events.WorkingState
import fi.metatavu.vp.messaging.client.MessagingClient
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
        val workType = tb.manager.workTypes.createWorkType()
        val startWorkEvent = createWorkingStateEvent(driverId, workType.id!!, WorkingState.WORKING)

        MessagingClient.publishMessage(startWorkEvent)
        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            tb.manager.timeEntries.listTimeEntries(driverId).isNotEmpty()
        }

        // Manually add it to closable since the time entries were created off-screen
        tb.manager.timeEntries.listTimeEntries(driverId).forEach {
            tb.manager.timeEntries.addClosable(it)
        }

        val startedTimeEntry = tb.manager.timeEntries.listTimeEntries(driverId)[0]
        assertEquals(driverId, startedTimeEntry.employeeId)
        assertNotNull(startedTimeEntry.workTypeId)
        assertNotNull(startedTimeEntry.startTime)
        assertNull(startedTimeEntry.endTime)

        val endWorkDayEvent = createWorkingStateEvent(driverId, workType.id, WorkingState.NOT_WORKING)
        MessagingClient.publishMessage(endWorkDayEvent)

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            tb.manager.timeEntries.listTimeEntries(driverId)[0].endTime != null
        }
        val finishedTimeEntry = tb.manager.timeEntries.listTimeEntries(driverId)[0]
        assertEquals(startedTimeEntry.id, finishedTimeEntry.id)
        assertEquals(driverId, finishedTimeEntry.employeeId)
        assertNotNull(finishedTimeEntry.workTypeId)
        assertNotNull(finishedTimeEntry.startTime)
        assertNotNull(finishedTimeEntry.endTime)
    }

    private fun createWorkingStateEvent(
        driverId: UUID,
        workTypeId: UUID,
        workingState: WorkingState
    ): DriverWorkingStateChangeGlobalEvent {
        return DriverWorkingStateChangeGlobalEvent(driverId, workTypeId, workingState, OffsetDateTime.now())
    }
}