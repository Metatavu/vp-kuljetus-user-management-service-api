package fi.metatavu.vp.usermanagement.workshifts.scheduled

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventRepository
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
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

    suspend fun stopWorkShifts() {
        val breakEvent = workEventRepository.findLatestShiftEndingBreakEvent()

        if (breakEvent != null) {
            workEventController.changeToWorkShiftEnd(breakEvent)
            return
        }

        val event = workEventRepository.findLatestShiftEndingEvent() ?: return

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
    }
}