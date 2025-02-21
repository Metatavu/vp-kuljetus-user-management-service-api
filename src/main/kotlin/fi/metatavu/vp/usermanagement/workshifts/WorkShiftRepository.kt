package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * Repository for employee work shifts
 */
@ApplicationScoped
class WorkShiftRepository: AbstractRepository<WorkShiftEntity, UUID>() {

    /**
     * Creates a new employee work shift
     *
     * @param id id
     * @param employeeId employee id
     * @param date date
     * @param approved approved
     * @param absence absence
     * @param perDiemAllowance per diem allowance
     * @param startedAt started at
     * @param endedAt ended at
     * @param dayOffWorkAllowance day off work allowance
     * @return created employee work shift
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        date: LocalDate,
        approved: Boolean,
        absence: AbsenceType?,
        perDiemAllowance: PerDiemAllowanceType?,
        startedAt: OffsetDateTime?,
        endedAt: OffsetDateTime?,
        dayOffWorkAllowance: Boolean? = null
    ): WorkShiftEntity {
        val employeeWorkShift = WorkShiftEntity()
        employeeWorkShift.id = id
        employeeWorkShift.employeeId = employeeId
        employeeWorkShift.date = date
        employeeWorkShift.approved = approved
        employeeWorkShift.absence = absence
        employeeWorkShift.perDiemAllowance = perDiemAllowance
        employeeWorkShift.startedAt = startedAt
        employeeWorkShift.endedAt = endedAt
        employeeWorkShift.dayOffWorkAllowance = dayOffWorkAllowance
        return persistSuspending(employeeWorkShift)
    }

    /**
     * Lists employee work shifts
     *
     * @param employeeId employee id
     * @param startedAfter started after
     * @param startedBefore started before
     * @param dateAfter date after filter
     * @param dateBefore date before filter
     * @param first first
     * @param max max
     * @return pair of list of employee work shifts and count
     */
    suspend fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        dateAfter: LocalDate?,
        dateBefore: LocalDate?,
        first: Int? = null,
        max: Int? = null
    ): Pair<List<WorkShiftEntity>, Long> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        queryBuilder.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        if (startedAfter != null) {
            queryBuilder.append(" AND startedAt >= :startedAfter")
            parameters.and("startedAfter", startedAfter)
        }

        if (startedBefore != null) {
            queryBuilder.append(" AND startedAt <= :startedBefore")
            parameters.and("startedBefore", startedBefore)
        }

        if (dateAfter != null) {
            queryBuilder.append(" AND date >= :dateAfter")
            parameters.and("dateAfter", dateAfter)
        }

        if (dateBefore != null) {
            queryBuilder.append(" AND date <= :dateBefore")
            parameters.and("dateBefore", dateBefore)
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.by("date", Sort.Direction.Descending).and("startedAt", Sort.Direction.Descending), parameters),
            firstIndex = first,
            maxResults = max
        )
    }

    /**
     * Get latest active shift
     */
    suspend fun getLatestActiveShift(): WorkShiftEntity? {
        return find("endedAt is NULL",  Sort.by("date", Sort.Direction.Descending).and("startedAt", Sort.Direction.Descending))
            .range<WorkShiftEntity>(0, 0).list<WorkShiftEntity>().awaitSuspending().firstOrNull()
    }

    /**
     * Lists all finished work shifts with missing calculated hours (excluding those hours that are not calculated)
     *
     * @param first first
     * @param last last
     * @return unfinished work shift list
     */
    suspend fun listsNotCalculatedWorkShifts(
        first: Int,
        last: Int
    ): List<WorkShiftEntity> {
        val query = """
        SELECT ws FROM WorkShiftEntity ws
        LEFT JOIN WorkShiftHoursEntity wh ON ws.id = wh.workShift.id
        WHERE wh.calculatedHours IS NULL AND wh.workType NOT IN ('OFFICIAL_DUTIES','SICK_LEAVE','TRAINING','UNPAID')
        """
        return find(query).range<WorkShiftEntity>(first, last).list<WorkShiftEntity>().awaitSuspending()
    }

    /**
     * Finds employee work shift by date and employee id
     *
     * @param employeeId employee id
     * @param workEventTime work event time
     * @return employee work shift or null if not found
     */
    suspend fun findSameDayWorkShift(employeeId: UUID?, workEventTime: OffsetDateTime): WorkShiftEntity? {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        queryBuilder.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        queryBuilder.append(" AND date = :date")
        parameters.and("date", workEventTime.toLocalDate())


        return find(queryBuilder.toString(), parameters).firstResult<WorkShiftEntity>().awaitSuspending()

    }

    /**
     * Finds latest employee work shift before work event time
     *
     * @param employeeId employee id
     * @param beforeWorkEventTime before work event time
     * @return employee work shift or null if not found
     */
    suspend fun findLatestEmployeeWorkShift(employeeId: UUID?, beforeWorkEventTime: OffsetDateTime): WorkShiftEntity? {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        queryBuilder.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        queryBuilder.append(" AND date < :date")
        parameters.and("date", beforeWorkEventTime.toLocalDate())

        return find(queryBuilder.toString(), Sort.descending("date"), parameters).firstResult<WorkShiftEntity>().awaitSuspending()

    }

    /**
     * Updates employee work shift
     *
     * @param workShiftEntity employee work shift entity
     * @return updated holiday
     */
    suspend fun update(
        workShiftEntity: WorkShiftEntity,
        absence: AbsenceType?,
        dayOffWorkAllowance: Boolean?,
        perDiemAllowance: PerDiemAllowanceType?,
        notes: String?,
        approved: Boolean
    ): WorkShiftEntity {
        workShiftEntity.absence = absence
        workShiftEntity.dayOffWorkAllowance = dayOffWorkAllowance
        workShiftEntity.perDiemAllowance = perDiemAllowance
        workShiftEntity.notes = notes
        workShiftEntity.approved = approved

        return persistSuspending(workShiftEntity)
    }

}