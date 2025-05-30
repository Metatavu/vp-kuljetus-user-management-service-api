package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.messaging.events.TaskGlobalEvent
import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

@ApplicationScoped
class TaskGlobalEventConsumer: WithCoroutineScope() {
    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var userController: UserController

    /**
     * Processes task events. When driver tells the mobile app that he is loading or unloading, Delivery Info API will send an event here.
     * This event processor will create new work events for loading and unloading.
     * When task is done, a new event will be created, that is either the type of the previous non-task event or OTHER_WORK if the previous event is SHIFT_START.
     */
    @ConsumeEvent("TASK")
    @Suppress("unused")
    @WithTransaction
    fun onTaskEvent(event: TaskGlobalEvent): Uni<Boolean> = withCoroutineScope(60_000) {
        logger.info("Task event: $event")

        val user = userController.find(id = event.userId)

        if (user != null) {
            val workShift = workShiftController.listEmployeeWorkShifts(
                employeeId = event.userId,
                null,
                null,
                null,
                null
            ).first.first()

            val events = workEventController.list(employeeWorkShift = workShift).first.filter {
                if (it.time.isAfter(event.eventTime)) {
                    workEventController.delete(it)
                    return@filter false
                }

                return@filter true
            }
            val previousEvent = events.first()
            val isPreviousEventTaskEvent = previousEvent.workEventType == WorkEventType.LOADING || previousEvent.workEventType == WorkEventType.UNLOADING

            if (event.taskStatus == TASK_STATUS.IN_PROGRESS) {
                if (workShift.endedAt == null && isPreviousEventTaskEvent) {
                    logger.error("Adding two task related work events in a row to a shift is not allowed. Ignoring the event.")
                    return@withCoroutineScope false
                }
                val workEventType = when (event.taskType) {
                    TASK_TYPE.LOAD -> WorkEventType.LOADING
                    TASK_TYPE.UNLOAD -> WorkEventType.UNLOADING
                    else -> null
                }

                if (workEventType != null) {
                    workEventController.create(
                        employee = user,
                        time = event.eventTime,
                        workEventType = workEventType,
                        truckId = event.truckId,
                    )

                    logger.info("Task in progress, created a work event of type $workEventType.")
                }
            } else if (event.taskStatus == TASK_STATUS.DONE) {
                if (workShift.endedAt == null && isPreviousEventTaskEvent) {
                    val previousNonTaskEvent = events.firstOrNull { it.workEventType != WorkEventType.LOADING && it.workEventType != WorkEventType.UNLOADING }
                    if (previousNonTaskEvent != null) {
                        val newEventType = if (previousNonTaskEvent.workEventType == WorkEventType.SHIFT_START) WorkEventType.OTHER_WORK else previousNonTaskEvent.workEventType
                        workEventController.create(
                            employee = user,
                            time = event.eventTime,
                            workEventType = newEventType,
                            truckId = event.truckId
                        )

                        logger.info("Task done, continuing the shift by adding a work event of type $newEventType.")
                    }
                }
            }
            return@withCoroutineScope true
        } else {
            logger.error("The event user ${event.userId} does not exist, thus event will be ignored.")
            return@withCoroutineScope false
        }

    }

    private object TASK_STATUS {
        val IN_PROGRESS = "IN_PROGRESS"
        val DONE = "DONE"
    }

    private object TASK_TYPE {
        val LOAD = "LOAD"
        val UNLOAD = "UNLOAD"
    }
}