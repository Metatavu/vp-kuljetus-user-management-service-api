package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

/**
 * Controller that listens to events sent by messaging service
 */
@ApplicationScoped
@Suppress("unused")
class DriverGlobalEventConsumer: WithCoroutineScope() {

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var logger: Logger

    /**
     * Processes the DRIVER_WORKING_STATE_CHANGE event
     *
     * @param event event to process
     * @return A Uni that completes into true if event was processed successfully, false otherwise
     */
    @ConsumeEvent("DRIVER_WORKING_STATE_CHANGE")
    @WithTransaction
    fun processWorkingStateChangeEvent(event: DriverWorkEventGlobalEvent): Uni<Boolean> = withCoroutineScope(60_000) {
        logger.info("Processing event ${event.type}")
        val foundDriver = userController.find(event.driverId)

        if (foundDriver == null) {
            logger.error("Driver with id ${event.driverId} not found")
            return@withCoroutineScope false
        }

        val workShift = workShiftController.listEmployeeWorkShifts(
            employeeId = event.driverId,
            null,
            null,
            null,
            null
        ).first.first()

        val events = workEventController.list(employeeWorkShift = workShift).first
        val previousEvent = events.firstOrNull()
        if (previousEvent != null) {
            val isPreviousEventTaskEvent = previousEvent.workEventType == WorkEventType.LOADING || previousEvent.workEventType == WorkEventType.UNLOADING

            if (workShift.endedAt == null && isPreviousEventTaskEvent) {
                logger.error("Cannot add an event while there is an active loading or unloading task ongoing. Ignoring the event.")
                return@withCoroutineScope false
            }
        }


        workEventController.create(
            employee = foundDriver,
            time = event.time,
            workEventType = event.workEventType,
            truckId = event.truckId
        )
        logger.debug("Event ${event.type} processed. Created new time entry (${event.workEventType}) for driver ${foundDriver.id}")
        return@withCoroutineScope true
    }
}