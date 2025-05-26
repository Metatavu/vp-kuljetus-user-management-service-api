package fi.metatavu.vp.usermanagement.workevents.scheduled

import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.OffsetDateTime

@ApplicationScoped
class WorkEventScheduledJobs {

    @Inject
    lateinit var workShiftRepository: WorkShiftRepository

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var logger: Logger

    @ConfigProperty(name = "vp.usermanagement.workevents.duplicateremoval.graceperiod.days")
    lateinit var gracePeriodDays: String

    /**
     * Removes duplicate events from work shifts that have ended after the defined grace period and that have not been yet checked.
     * This is used by a cron job that runs periodically to clean up duplicate events.
     */
    suspend fun removeDuplicateEvents() {
        val endedBefore = OffsetDateTime.now().minusDays(gracePeriodDays.toLong())

        val notCheckedWorkShifts = workShiftRepository.listWorkShiftsWithPossibleDuplicateEvents(endedBefore = endedBefore, maxResults = 100000)

        logger.info("Amount of shifts that have not been marked as checked for duplicates: ${notCheckedWorkShifts.size}")

        if (notCheckedWorkShifts.isEmpty()) {
            return
        }

        removeDuplicatesFromWorkShift(notCheckedWorkShifts.first())
    }

    /**
     * Removes all duplicate events from a work shift and marks the work shift as checked for duplicates.
     * This used by a cron job that runs periodically to clean up duplicate events.
     *
     * @param workShift work shift from which to remove duplicate events
     */
    private suspend fun removeDuplicatesFromWorkShift(workShift: WorkShiftEntity) {
        val eventsAscendingByTime = workEventController.list(employeeWorkShift = workShift).first.reversed()

        val eventsToDelete = mutableListOf<WorkEventEntity>()

        for ((index, event) in eventsAscendingByTime.withIndex()) {
            if (index == 0) continue // Skip the first event as it is the reference for duplicates

            if (event.workEventType == eventsAscendingByTime[index - 1].workEventType) {
                eventsToDelete.add(event)
            }
        }

        eventsToDelete.forEachIndexed { index, it ->
            workEventController.delete(foundWorkEvent = it, recalculate = index == eventsToDelete.lastIndex)
        }

        logger.info("Removed ${eventsToDelete.size} duplicate events from work shift ${workShift.id}.")

        workShiftRepository.markShiftAsCheckedForDuplicates(workShiftEntity = workShift)
    }
}