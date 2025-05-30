package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.workshifts.scheduled.WorkShiftScheduledJobs
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class WorkShiftResolvingEventConsumer: WithCoroutineScope() {
    @Inject
    lateinit var workShiftScheduledJobs: WorkShiftScheduledJobs

    @ConsumeEvent("WORK_SHIFT_RESOLVING")
    @WithTransaction
    fun resolveWorkShifts(event: WorkShiftResolvingEvent) = withCoroutineScope(600_000) {


        workShiftScheduledJobs.endUnresolvedWorkshifts()

        return@withCoroutineScope true
    }
}