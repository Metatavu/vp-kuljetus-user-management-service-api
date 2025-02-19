package fi.metatavu.vp.usermanagement.workshifts.changelogs.changes

import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workevents.WorkEventEntity
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursEntity
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetEntity
import io.quarkus.panache.common.Parameters
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Database operations for work shift changes
 */
@ApplicationScoped
class WorkShiftChangeRepository: AbstractRepository<WorkShiftChangeEntity, UUID>() {
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
        val workShiftChange = WorkShiftChangeEntity()
        workShiftChange.id = UUID.randomUUID()
        workShiftChange.reason = reason
        workShiftChange.creatorId = creatorId
        workShiftChange.workShiftChangeSet = workShiftChangeSet
        workShiftChange.workShift = workShift
        workShiftChange.workShiftHour = workShiftHours
        workShiftChange.workEvent = workEvent
        workShiftChange.oldValue = oldValue
        workShiftChange.newValue = newValue

        return persistSuspending(workShiftChange)
    }

    /**
     * List changes that belong to a change set
     * This will be used to build the change set REST entity
     *
     * @param workShiftChangeSetEntity
     */
    suspend fun listByChangeSet(workShiftChangeSetEntity: WorkShiftChangeSetEntity): List<WorkShiftChangeEntity> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        addCondition(queryBuilder, "workShiftChangeSet = :workShiftChangeSet")
        parameters.and("workShiftChangeSet", workShiftChangeSetEntity)

        return list(queryBuilder.toString(), parameters).awaitSuspending()
    }

    /**
     * List changes that belong to a work shift hours entity
     *
     * @param workShiftHoursEntity
     */
    suspend fun listByWorkshiftHour(workShiftHoursEntity: WorkShiftHoursEntity): List<WorkShiftChangeEntity> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        addCondition(queryBuilder, "workShiftHour = :workShiftHour")
        parameters.and("workShiftHour", workShiftHoursEntity)

        return list(queryBuilder.toString(), parameters).awaitSuspending()
    }

    /**
     * List changes that belong to a work event entity
     *
     * @param workEvent
     */
    suspend fun listByWorkEvent(workEvent: WorkEventEntity): List<WorkShiftChangeEntity> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        addCondition(queryBuilder, "workEvent = :workEvent")
        parameters.and("workEvent", workEvent)

        return list(queryBuilder.toString(), parameters).awaitSuspending()
    }
}