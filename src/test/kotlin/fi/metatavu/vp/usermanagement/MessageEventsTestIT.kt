package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.messaging.RoutingKey
import fi.metatavu.vp.messaging.client.MessagingClient
import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.test.client.models.WorkEventType
import fi.metatavu.vp.usermanagement.settings.RabbitMQTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

/**
 * Tests events that are arriving from messaging service
 */
@QuarkusTest
@TestProfile(RabbitMQTestProfile::class)
class MessageEventsTestIT: AbstractFunctionalTest() {

    /**
     * Tests that we're capable of handling lots of incoming messages simultaneously
     */
    @Test
    fun testSimultaneousMessages() = createTestBuilder().use { tb ->
        val drivers = tb.manager.drivers.listDrivers()
        val driverId = drivers[0].id!!
        (0 until 25).forEach { _ ->
            MessagingClient.publishMessage(createDriverWorkEvent(driverId, UUID.randomUUID(), WorkEventType.SHIFT_START), routingKey = RoutingKey.DRIVER_WORKING_STATE_CHANGE)
        }
        Awaitility.await().atMost(Duration.ofMinutes(5)).until {

            val workEvents = tb.manager.workEvents.listWorkEvents(employeeId = driverId, max = 200)
            workEvents.size == 25
        }

        // Manually add it to closable since the work events were created off-screen
        tb.manager.workEvents.listWorkEvents(employeeId = driverId, max = 1000).forEach {
            tb.manager.workEvents.addClosable(it)
        }
    }

    /**
     * Tests driver working state change global event
     */
    @Test
    fun testDriverWorkingStateChangeGlobalEvent() = createTestBuilder().use { tb ->
        val drivers = tb.manager.drivers.listDrivers()
        val driverId = drivers[0].id!!
        val startWorkEvent = createDriverWorkEvent(driverId, UUID.randomUUID(), WorkEventType.SHIFT_START)

        MessagingClient.publishMessage(startWorkEvent, routingKey = RoutingKey.DRIVER_WORKING_STATE_CHANGE)
        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            val workEvents = tb.manager.workEvents.listWorkEvents(driverId)
            workEvents.size == 1 && workEvents[0].truckId == startWorkEvent.truckId
        }
        // Check that truck id was added to work shift data
        var workShift = tb.manager.workShifts.listEmployeeWorkShifts(employeeId = driverId).first()
        assertEquals(1, workShift.truckIdsFromEvents!!.size)
        assertEquals(startWorkEvent.truckId, workShift.truckIdsFromEvents!![0])

        // Manually add it to closable since the work events were created off-screen
        tb.manager.workEvents.listWorkEvents(driverId).forEach {
            tb.manager.workEvents.addClosable(it)
        }

        val shiftStartedWorkEvent = tb.manager.workEvents.listWorkEvents(driverId)[0]
        assertEquals(driverId, shiftStartedWorkEvent.employeeId)
        assertNotNull(shiftStartedWorkEvent.workEventType)
        assertNotNull(shiftStartedWorkEvent.time)

        val endWorkDayEvent = createDriverWorkEvent(driverId, UUID.randomUUID(), WorkEventType.DRIVE)
        MessagingClient.publishMessage(endWorkDayEvent, routingKey = RoutingKey.DRIVER_WORKING_STATE_CHANGE)

        Awaitility.await().atMost(Duration.ofMinutes(2)).until {
            val workEvents = tb.manager.workEvents.listWorkEvents(driverId)
            workEvents.size == 2 && workEvents[0].truckId == endWorkDayEvent.truckId
        }

        // Manually add it to closable since the work events were created off-screen
        tb.manager.workEvents.listWorkEvents(driverId).forEach {
            tb.manager.workEvents.addClosable(it)
        }

        val driveWorkEvent = tb.manager.workEvents.listWorkEvents(driverId)[0]
        assertEquals(driverId, driveWorkEvent.employeeId)
        assertNotNull(driveWorkEvent.workEventType)
        assertNotNull(driveWorkEvent.time)

        workShift = tb.manager.workShifts.listEmployeeWorkShifts(employeeId = driverId).first()
        assertEquals(2, workShift.truckIdsFromEvents!!.size)
    }

    private fun createDriverWorkEvent(
        driverId: UUID,
        truckId: UUID,
        workEventType: WorkEventType
    ): DriverWorkEventGlobalEvent {
        return DriverWorkEventGlobalEvent(
            driverId = driverId,
            workEventType = fi.metatavu.vp.usermanagement.model.WorkEventType.valueOf(workEventType.name),
            time = OffsetDateTime.now(),
            truckId = truckId
        )
    }
}