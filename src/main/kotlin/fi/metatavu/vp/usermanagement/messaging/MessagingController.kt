package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.messaging.events.DriverWorkEventGlobalEvent
import fi.metatavu.vp.messaging.events.abstracts.GlobalEvent
import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.timeentries.TimeEntryController
import fi.metatavu.vp.usermanagement.users.UserController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.mutiny.Uni
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.time.OffsetDateTime

/**
 * Controller that listens to events sent by messaging service
 */
@ApplicationScoped
@Suppress("unused")
class MessagingController: WithCoroutineScope() {

    @Inject
    lateinit var timeEntryController: TimeEntryController

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
    fun processWorkingStateChangeEvent(event: GlobalEvent): Uni<Void> = withCoroutineScope {
            logger.info("Processing event ${event.type}")
            val parsed = event as DriverWorkEventGlobalEvent
            val foundDriver = userController.find(parsed.driverId)
            if (foundDriver == null) {
                logger.error("Driver with id ${parsed.driverId} not found")
                return@withCoroutineScope
            }
            val latestTimeEntry = timeEntryController.findIncompleteEntries(employee = foundDriver)
            if (latestTimeEntry != null && latestTimeEntry.startTime < parsed.time) {
                logger.debug("Found incomplete time entry for driver ${foundDriver.id} with id ${latestTimeEntry.id}")
                latestTimeEntry.endTime = OffsetDateTime.now()
                timeEntryController.updateEndTime(
                    timeEntry = latestTimeEntry,
                    newEndTime = parsed.time
                )
            } else {
                logger.debug("No incomplete valid time entries found for driver ${foundDriver.id}")
            }

            timeEntryController.create(
                employee = foundDriver,
                startTime = parsed.time,
                workEventType = parsed.workEventType
            )
            logger.debug("Event ${event.type} processed. Created new time entry (${event.workEventType}) for driver ${foundDriver.id}")

        }.replaceWithVoid()
}