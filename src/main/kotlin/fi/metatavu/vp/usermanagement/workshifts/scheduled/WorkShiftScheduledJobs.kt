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

    // Use only in testing
    @ConfigProperty(name = "vp.usermanagement.schedulers.workshiftstopper.ignore.starts", defaultValue = "false")
    lateinit var ignoreShiftStarts: String

    /**
     * End work shifts with rest events longer than 3 hours and any event longer than 5 hours
     */
    @WithTransaction
    @Scheduled(every="\${vp.usermanagement.schedulers.workshiftstopper.interval}")
    fun stopWorkShifts(): Uni<Void>  = withCoroutineScope {
            val breakEvent = workEventRepository.findLatestShiftEndingBreakEvent()

            if (breakEvent != null) {
                workEventController.setWorkShiftEnd(breakEvent)
                return@withCoroutineScope
            }

            val event = workEventRepository.findLatestShiftEndingEvent(ignoreShiftStarts == "true")

            if (event == null) return@withCoroutineScope
                if (event.workEventType == WorkEventType.SHIFT_START) {
                    workEventRepository.create(
                        id = UUID.randomUUID(),
                        employeeId = event.employeeId,
                        time = event.time.plusSeconds(1),
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
        }.replaceWithVoid()
}