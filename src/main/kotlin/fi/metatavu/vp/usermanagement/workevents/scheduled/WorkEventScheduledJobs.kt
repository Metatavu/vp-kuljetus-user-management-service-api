package fi.metatavu.vp.usermanagement.workevents.scheduled

import fi.metatavu.vp.usermanagement.workevents.WorkEventController
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
     * @param workShift
     */
    private suspend fun removeDuplicatesFromWorkShift(workShift: WorkShiftEntity) {
        val events = workEventController.list(employeeWorkShift = workShift).first.reversed()
        var currentIndex = 0
        var removedDuplicates = 0

        while (currentIndex < (events.size - 1)) {
            val currentEvent = events[currentIndex]

            var noMoreDuplicates = false
            var duplicatesIndex = 1
            while (!noMoreDuplicates && (currentIndex + duplicatesIndex) < events.size) {
                val nextEventToCheck = events[currentIndex + duplicatesIndex]

                if (nextEventToCheck.workEventType != currentEvent.workEventType) {
                    noMoreDuplicates = true
                } else {
                    workEventController.delete(nextEventToCheck)
                    removedDuplicates++
                    duplicatesIndex++
                }

                if (removedDuplicates >= 20) {
                    break
                }
            }

            if (removedDuplicates >= 20) {
                break
            }

            currentIndex+=duplicatesIndex
        }

        logger.info("Removed $removedDuplicates duplicate events from work shift ${workShift.id}.")

        if (removedDuplicates == 0) {
            workShiftRepository.markShiftAsCheckedForDuplicates(workShiftEntity = workShift)
        }
    }
}