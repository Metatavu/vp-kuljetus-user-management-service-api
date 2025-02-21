package fi.metatavu.vp.usermanagement.workshifts.scheduled

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class WorkShiftScheduledJobs: WithCoroutineScope() {
    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workShiftController: WorkShiftController

    /**
     * End work shifts with rest events longer than 3 hours and any event longer than 5 hours
     */
    @Scheduled(every="\${vp.usermanagement.schedulers.workshiftstopper.interval}")
    fun stopWorkShifts() {
        withCoroutineScope {
            val shift = workShiftController.getLatestActiveWorkShift()
            val event = workEventController.list(employeeWorkShift = shift, max = 1).first.firstOrNull()
        }
    }
}