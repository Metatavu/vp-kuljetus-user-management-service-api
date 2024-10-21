package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftEntity
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
     * Creates a new work shift hours with empty calculated hours value
     *
     * @param id id
     * @param workShiftEntity work shift entity
     * @param workType work type
     * @param actualHours actual hours
     * @return created work shift hours
     */
    suspend fun create(
        id: UUID,
        workShiftEntity: WorkShiftEntity,
        workType: WorkType,
    ): WorkShiftHoursEntity {
        val workShiftHours = WorkShiftHoursEntity()
        workShiftHours.id = id
        workShiftHours.workShift = workShiftEntity
        workShiftHours.workType = workType
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
        workShift: WorkShiftEntity? = null,
        workType: WorkType? = null,
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
            addCondition(queryBuilder, "workType = :workType")
            parameters.and("workType", workType)
        }

        if (employeeWorkShiftStartedAfter != null) {
            addCondition(queryBuilder, "workShift.date >= :employeeWorkShiftStartedAfter")
            parameters.and("employeeWorkShiftStartedAfter", employeeWorkShiftStartedAfter.toLocalDate())
        }

        if (employeeWorkShiftStartedBefore != null) {
            addCondition(queryBuilder, "workShift.date <= :employeeWorkShiftStartedBefore")
            parameters.and("employeeWorkShiftStartedBefore", employeeWorkShiftStartedBefore.toLocalDate())
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.descending("workShift.date"), parameters)
        )

    }
}