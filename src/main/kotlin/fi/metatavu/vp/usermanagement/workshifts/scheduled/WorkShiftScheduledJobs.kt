package fi.metatavu.vp.usermanagement.workshifts.scheduled

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.scheduler.Scheduled
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@ApplicationScoped
class WorkShiftScheduledJobs: WithCoroutineScope() {
    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftController: WorkShiftController

    /**
     * End work shifts with rest events longer than 3 hours and any event longer than 5 hours
     */
    @WithTransaction
    @Scheduled(every="\${vp.usermanagement.schedulers.workshiftstopper.interval}")
    fun stopWorkShifts(): Uni<Void>  = withCoroutineScope {
            val shift = workShiftController.getLatestActiveWorkShift()

            val event = workEventController.list(employeeWorkShift = shift, max = 1).first.firstOrNull() ?: return@withCoroutineScope
            if (event.workEventType == WorkEventType.BREAK && timeComparison(event.time, 3)) {
                workEventController.setWorkShiftEnd(event)
                return@withCoroutineScope
            }

            /*if (event.time.plusHours(5).isBefore(OffsetDateTime.now())) {
                workEventController.create(
                    employeeId = event.employeeId,
                    time = OffsetDateTime.now(),
                    workEventType = WorkEventType.SHIFT_END
                )
            }*/
        }.replaceWithVoid()

    private fun timeComparison(eventTime: OffsetDateTime, hours: Long): Boolean {
        val convertedEventTime = eventTime.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
        return convertedEventTime.plusHours(hours).isBefore(OffsetDateTime.now())
    }
}