package fi.metatavu.vp.usermanagement.event

import fi.metatavu.vp.messaging.events.TaskGlobalEvent
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger

@ApplicationScoped
class TaskGlobalEventConsumer {
    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var userController: UserController

    @ConsumeEvent("TASK")
    @Suppress("unused")
    @WithTransaction
    suspend fun onTaskEvent(event: TaskGlobalEvent) {
        logger.info("Task event: $event")

        val user = userController.find(id = event.userId)

        if (user != null) {
            if (event.taskStatus == TASK_STATUS.IN_PROGRESS) {

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
                } else {
                    logger.warn("The task type ${event.taskType} is not supported, thus event will be ignored.")
                }
            } else if (event.taskStatus == TASK_STATUS.DONE) {
               val workShift = workShiftController.listEmployeeWorkShifts(
                   employeeId = event.userId,
                   null,
                   null,
                   null,
                   null
               ).first.first()

                if (workShift.endedAt != null) {
                    val events = workEventController.list(employeeWorkShift = workShift).first
                    val previousEvent = events.first()
                    if (previousEvent.workEventType == WorkEventType.LOADING || previousEvent.workEventType == WorkEventType.UNLOADING) {
                        val previousNonTaskEvent = events.firstOrNull { it.workEventType != WorkEventType.LOADING && it.workEventType != WorkEventType.UNLOADING }
                        if (previousNonTaskEvent != null) {
                            if (previousNonTaskEvent.workEventType != WorkEventType.SHIFT_START) {
                                workEventController.create(
                                    employee = user,
                                    time = event.eventTime,
                                    workEventType = previousNonTaskEvent.workEventType,
                                    truckId = event.truckId
                                )
                            } else {
                                workEventController.create(
                                    employee = user,
                                    time = event.eventTime,
                                    workEventType = WorkEventType.OTHER_WORK,
                                    truckId = event.truckId
                                )
                            }

                        }
                    }
                }
            }
            logger.warn("The event user ${event.userId} does not exist, thus event will be ignored.")
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