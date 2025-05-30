package fi.metatavu.vp.usermanagement.messaging

import fi.metatavu.vp.usermanagement.WithCoroutineScope
import fi.metatavu.vp.usermanagement.workevents.scheduled.WorkEventScheduledJobs
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.vertx.ConsumeEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class WorkEventDuplicateRemovalConsumer: WithCoroutineScope() {
    @Inject
    lateinit var workEventScheduledJobs: WorkEventScheduledJobs

    @ConsumeEvent("WORK_EVENT_DUPLICATE_REMOVAL")
    @WithTransaction
    fun removeDuplicateWorkEvents(event: WorkEventDuplicateRemovalEvent) = withCoroutineScope(600_000) {
        workEventScheduledJobs.removeDuplicateEvents()

        return@withCoroutineScope true
    }
}