package fi.metatavu.vp.usermanagement.workshifts.changelogs.changes

import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeSet
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class WorkShiftChangeController {
    @Inject
    lateinit var workShiftChangeRepository: WorkShiftChangeRepository

    /**
     * Save work shift change to the database
     *
     * @param reason
     * @param creatorId
     * @param workShiftChangeSet
     * @param workShift
     * @param workShiftHours
     * @param workEvent
     * @param oldValue
     * @param newValue
     */
    suspend fun create(
        reason: String,
        creatorId: UUID,
        workShiftChangeSet: WorkShiftChangeSetEntity,
        workShift: WorkShiftEntity,
        workShiftHours: WorkShiftHoursEntity?,
        workEvent: WorkEventEntity?,
        oldValue: String?,
        newValue: String?
    ): WorkShiftChangeEntity {
        return workShiftChangeRepository.create(
            reason,
            creatorId,
            workShiftChangeSet,
            workShift,
            workShiftHours,
            workEvent,
            oldValue,
            newValue
        )
    }

    /**
     * List changes that belong to a change set
     * This will be used to build the change set REST entity
     *
     * @param workShiftChangeSet
     */
    suspend fun listByChangeSet(workShiftChangeSet: WorkShiftChangeSetEntity): List<WorkShiftChangeEntity> {
        return workShiftChangeRepository.listByChangeSet(workShiftChangeSet)
    }

    /**
     * Delete work shift change entity
     *
     * @param workShiftChangeEntity
     */
    suspend fun delete(workShiftChangeEntity: WorkShiftChangeEntity) {
        workShiftChangeRepository.deleteSuspending(workShiftChangeEntity)
    }

    /**
     * Create individual change entries for updated work shift fields
     *
     * @param oldWorkShift
     * @param newWorkShift
     * @param changeSet
     * @param creatorId
     */
    suspend fun processWorkShiftChanges(oldWorkShift: WorkShiftEntity, newWorkShift: EmployeeWorkShift, changeSet: WorkShiftChangeSetEntity, creatorId: UUID) {
        if (oldWorkShift.approved != newWorkShift.approved) {
            create(
                reason = WorkShiftChangeReason.WORKSHIFT_UPDATED_APPROVED.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldWorkShift,
                workShiftHours = null,
                workEvent = null,
                oldValue = oldWorkShift.approved.toString(),
                newValue = newWorkShift.approved.toString()
            )
        }

        if (oldWorkShift.dayOffWorkAllowance != newWorkShift.dayOffWorkAllowance) {
            create(
                reason = WorkShiftChangeReason.WORKSHIFT_UPDATED_DAYOFFWORKALLOWANCE.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldWorkShift,
                workShiftHours = null,
                workEvent = null,
                oldValue = oldWorkShift.dayOffWorkAllowance.toString(),
                newValue = newWorkShift.dayOffWorkAllowance.toString()
            )
        }

        if (oldWorkShift.absence.toString() != newWorkShift.absence.toString()) {
            create(
                reason = WorkShiftChangeReason.WORKSHIFT_UPDATED_ABSENCE.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldWorkShift,
                workShiftHours = null,
                workEvent = null,
                oldValue = oldWorkShift.absence.toString(),
                newValue = newWorkShift.absence.toString()
            )
        }

        if (oldWorkShift.perDiemAllowance.toString() != newWorkShift.perDiemAllowance.toString()) {
            create(
                reason = WorkShiftChangeReason.WORKSHIFT_UPDATED_PERDIEMALLOWANCE.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldWorkShift,
                workShiftHours = null,
                workEvent = null,
                oldValue = oldWorkShift.perDiemAllowance.toString(),
                newValue = newWorkShift.perDiemAllowance.toString()
            )
        }

        if (oldWorkShift.notes != newWorkShift.notes) {
            create(
                reason = WorkShiftChangeReason.WORKSHIFT_UPDATED_NOTES.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldWorkShift,
                workShiftHours = null,
                workEvent = null,
                oldValue = oldWorkShift.notes.toString(),
                newValue = newWorkShift.notes.toString()
            )
        }
    }

    /**
     * Create individual change entries for updated work event fields
     *
     * @param oldEvent
     * @param newEvent
     * @param changeSet
     * @param creatorId
     */
    suspend fun processWorkEventChanges(oldEvent: WorkEventEntity, newEvent: WorkEvent, changeSet: WorkShiftChangeSetEntity, creatorId: UUID) {
        if (oldEvent.costCenter != newEvent.costCenter) {
            create(
                reason = WorkShiftChangeReason.WORKEVENT_UPDATED_COSTCENTER.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldEvent.workShift,
                workShiftHours = null,
                workEvent = oldEvent,
                oldValue = oldEvent.costCenter,
                newValue = newEvent.costCenter
            )
        }

        if (oldEvent.workEventType.toString() != newEvent.workEventType.toString()) {
            create(
                reason = WorkShiftChangeReason.WORKEVENT_UPDATED_TYPE.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldEvent.workShift,
                workShiftHours = null,
                workEvent = oldEvent,
                oldValue = oldEvent.workEventType.toString(),
                newValue = newEvent.workEventType.toString()
            )
        }

        if (oldEvent.time.toString() != newEvent.time.toString()) {
            create(
                reason = WorkShiftChangeReason.WORKEVENT_UPDATED_TIMESTAMP.toString(),
                creatorId = creatorId,
                workShiftChangeSet = changeSet,
                workShift = oldEvent.workShift,
                workShiftHours = null,
                workEvent = oldEvent,
                oldValue = oldEvent.time.toString(),
                newValue = newEvent.time.toString()
            )
        }
    }
}