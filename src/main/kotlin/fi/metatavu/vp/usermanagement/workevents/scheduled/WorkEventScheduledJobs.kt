package fi.metatavu.vp.usermanagement.workevents.scheduled

import fi.metatavu.vp.usermanagement.workevents.WorkEventController
import fi.metatavu.vp.usermanagement.workevents.WorkEventRepository
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
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
    lateinit var workEventRepository: WorkEventRepository

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

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

        val notCheckedWorkShifts = workShiftRepository.countWorkShiftsWithPossibleDuplicateEvents(endedBefore = endedBefore)

        if (notCheckedWorkShifts > 0L) {
            logger.info("Amount of shifts that have not been marked as checked for duplicates: $notCheckedWorkShifts")

            removeDuplicatesFromWorkShift(
                workShift = workShiftRepository.listWorkShiftsWithPossibleDuplicateEvents(
                    endedBefore = endedBefore,
                    maxResults = 1
                ).first()
            )
        } else {
            logger.info("No work shifts found that would need duplicate event removal.")
        }
    }

    /**
     * Removes all duplicate events from a work shift via a native MySQL delete that leaves the first event in each
     * consecutive run of identical types and marks the shift as checked afterwards.
     *
     * @param workShift work shift from which to remove duplicate events
     */
    private suspend fun removeDuplicatesFromWorkShift(workShift: WorkShiftEntity) {
        val removedCount = workEventRepository.deleteConsecutiveDuplicateEvents(workShift.id)

        val shiftToMark = if (removedCount > 0) {
            val updatedShift = workEventController.recalculateWorkShiftTimes(workShift = workShift)
            workShiftHoursController.recalculateWorkShiftHours(workShift = updatedShift)
            logger.info("Removed $removedCount duplicate events from work shift ${workShift.id}.")
            updatedShift
        } else {
            logger.info("No duplicate events found for work shift ${workShift.id}. Marked it as checked.")
            workShift
        }

        workShiftRepository.markShiftAsCheckedForDuplicates(workShiftEntity = shiftToMark)
    }
}
