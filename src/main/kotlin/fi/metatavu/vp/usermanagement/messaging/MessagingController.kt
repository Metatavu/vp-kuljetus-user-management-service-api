package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.users.UserController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.mutiny.Uni
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

/**
 * Controller that listens to events sent by messaging service
 */
@ApplicationScoped
@Suppress("unused")
class MessagingController: WithCoroutineScope() {

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var vertx: Vertx

    @Inject
    lateinit var logger: Logger

    /**
     * Processes the DRIVER_WORKING_STATE_CHANGE event
     *
     * @param event event to process
     */
    @ConsumeEvent("DRIVER_WORKING_STATE_CHANGE")
    @WithTransaction
    fun processWorkingStateChangeEvent(event: DriverWorkEventGlobalEvent): Uni<Void> = withCoroutineScope {
            logger.info("Processing event ${event.type}")
            val foundDriver = userController.find(event.driverId)

            if (foundDriver == null) {
                logger.error("Driver with id ${event.driverId} not found")
                return@withCoroutineScope
            }

            workEventController.create(
                employee = foundDriver,
                startTime = event.time,
                workEventType = event.workEventType
            )
            logger.debug("Event ${event.type} processed. Created new time entry (${event.workEventType}) for driver ${foundDriver.id}")

        }.replaceWithVoid()
}