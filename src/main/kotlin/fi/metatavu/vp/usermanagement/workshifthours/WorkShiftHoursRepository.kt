package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.EmployeeWorkShiftEntity
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

/**
 * Repository for work shift hours
 */
@ApplicationScoped
class WorkShiftHoursRepository: AbstractRepository<WorkShiftHoursEntity, UUID>() {

    /**
     * Creates a new work shift hours
     *
     * @param id id
     * @param workShiftEntity work shift entity
     * @param workEventType work event type
     * @param actualHours actual hours
     * @return created work shift hours
     */
    suspend fun create(
        id: UUID,
        workShiftEntity: EmployeeWorkShiftEntity,
        workEventType: WorkEventType,
        actualHours: Float?
    ): WorkShiftHoursEntity {
        val workShiftHours = WorkShiftHoursEntity()
        workShiftHours.id = id
        workShiftHours.workShift = workShiftEntity
        workShiftHours.workEventType = workEventType
        workShiftHours.actualHours = actualHours
        return persistSuspending(workShiftHours)
    }

    /**
     * Lists work shift hours, orders by work shift date descending
     *
     * @param employeeId employee id
     * @param workShift work shift
     * @param workType work type
     * @param employeeWorkShiftStartedAfter employee work shift started after
     * @param employeeWorkShiftStartedBefore employee work shift started before
     * @return pair of list of work shift hours and count
     */
    suspend fun listWorkShiftHours(
        employeeId: UUID? = null,
        workShift: EmployeeWorkShiftEntity? = null,
        workType: WorkEventType? = null,
        employeeWorkShiftStartedAfter: OffsetDateTime? = null,
        employeeWorkShiftStartedBefore: OffsetDateTime? = null
    ): Pair<List<WorkShiftHoursEntity>, Long> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        if (employeeId != null) {
            addCondition(queryBuilder, "workShift.employeeId = :employeeId")
            parameters.and("employeeId", employeeId)
        }

        if (workShift != null) {
            addCondition(queryBuilder, "workShift = :workShift")
            parameters.and("workShift", workShift)
        }

        if (workType != null) {
            addCondition(queryBuilder, "workEventType = :workEventType")
            parameters.and("workEventType", workType)
        }

        if (employeeWorkShiftStartedAfter != null) {
            addCondition(queryBuilder, "employeeWorkShiftStartedAfter >= :employeeWorkShiftStartedAfter")
            parameters.and("employeeWorkShiftStartedAfter", employeeWorkShiftStartedAfter)
        }

        if (employeeWorkShiftStartedBefore != null) {
            addCondition(queryBuilder, "employeeWorkShiftStartedBefore <= :employeeWorkShiftStartedBefore")
            parameters.and("employeeWorkShiftStartedBefore", employeeWorkShiftStartedBefore)
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.descending("workShift.date"), parameters)
        )

    }

}