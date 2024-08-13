package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.messaging.events.DriverWorkingStateChangeGlobalEvent
import fi.metatavu.vp.messaging.events.GlobalEvent
import fi.metatavu.vp.messaging.events.WorkingState
import fi.metatavu.vp.usermanagement.timeentries.TimeEntryController
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.worktypes.WorkTypeController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.jboss.logging.Logger
import java.time.OffsetDateTime

/**
 * Controller that listens to events sent by messaging service
 */
@ApplicationScoped
class MessagingController {

    @Inject
    lateinit var timeEntryController: TimeEntryController

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var workTypeController: WorkTypeController

    @Inject
    lateinit var vertx: Vertx

    @Inject
    lateinit var logger: Logger

    /**
     * Processes the DRIVER_WORKING_STATE_CHANGE event
     *
     * @param event event to process
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @ConsumeEvent("DRIVER_WORKING_STATE_CHANGE")
    @WithTransaction
    fun processWorkingStateChangeEvent(event: GlobalEvent): Uni<Void> {
        return CoroutineScope(vertx.dispatcher()).async {
            val parsed = event as DriverWorkingStateChangeGlobalEvent
            val foundDriver = userController.find(parsed.driverId)
            if (foundDriver == null) {
                logger.error("Driver with id ${parsed.driverId} not found")
                return@async
            }

            val workType = workTypeController.find(parsed.workTypeId)
            if (workType == null) {
                logger.error("WorkType with id ${parsed.workTypeId} not found")
                return@async
            }

            when (parsed.workingStateNew) {
                WorkingState.WORKING -> {
                    timeEntryController.create(
                        foundDriver, workType, TimeEntry(
                            employeeId = parsed.driverId,
                            workTypeId = workType.id,
                            startTime = parsed.time
                        )
                    )
                }

                WorkingState.NOT_WORKING -> {
                    val latest = timeEntryController.findIncompleteEntries(employee = foundDriver)
                    if (latest != null && latest.startTime < parsed.time) {
                        latest.endTime = OffsetDateTime.now()
                        timeEntryController.update(
                            latest,
                            latest.startTime,
                            parsed.time
                        )
                    } else {
                        logger.error("No incomplete valid time entries found for driver ${foundDriver.id}")
                    }
                }
            }

        }.asUni().replaceWithVoid()
    }
}