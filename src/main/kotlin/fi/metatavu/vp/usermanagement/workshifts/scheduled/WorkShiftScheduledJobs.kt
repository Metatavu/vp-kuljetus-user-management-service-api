package fi.metatavu.vp.usermanagement.workshifts.scheduled

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventRepository
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.util.*

@ApplicationScoped
class WorkShiftScheduledJobs: WithCoroutineScope() {
    @Inject
    lateinit var workEventRepository: WorkEventRepository

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var logger: Logger

    /**
     * End unresolved workshifts
     */
    suspend fun endUnresolvedWorkshifts() {
        val shiftEndingBreakEvents = workEventRepository.listShiftEndingBreakEvents()
        logger.info("Found ${shiftEndingBreakEvents.size} shift ending break events")

        val breakEvent = shiftEndingBreakEvents.firstOrNull()

        if (breakEvent != null) {
            workEventController.changeToWorkShiftEnd(breakEvent)
            logger.info("Terminated workshift ${breakEvent.workShift.id}")
            return
        }

        val events = workEventRepository.listShiftEndingEvents()
        logger.info("Found ${events.size} shift ending events")
        val event = events.firstOrNull() ?: return
        if (event.workEventType == WorkEventType.SHIFT_START) {
            workEventRepository.create(
                id = UUID.randomUUID(),
                employeeId = event.employeeId,
                time = event.time,
                workEventType = WorkEventType.UNKNOWN,
                workShiftEntity = event.workShift,
                truckId = event.truckId,
                costCenter = event.costCenter
            )
        }

        workEventRepository.create(
            id = UUID.randomUUID(),
            employeeId = event.employeeId,
            time = OffsetDateTime.now(),
            workEventType = WorkEventType.SHIFT_END,
            workShiftEntity = event.workShift,
            truckId = event.truckId,
            costCenter = event.costCenter
        )

        val updatedShift = workEventController.recalculateWorkShiftTimes(workShift = event.workShift)
        workShiftHoursController.recalculateWorkShiftHours(
            workShift = updatedShift,
        )

        logger.info("Terminated workshift ${updatedShift.id}")
    }
}