package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.AbsenceType
import fi.metatavu.vp.usermanagement.model.PerDiemAllowanceType
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
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
     * @return created employee work shift
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        date: LocalDate,
        approved: Boolean,
        absence: AbsenceType?,
        perDiemAllowance: PerDiemAllowanceType?,
        startedAt: LocalDate?,
        endedAt: LocalDate?
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
        return persistSuspending(employeeWorkShift)
    }

    /**
     * Lists employee work shifts
     *
     * @param employeeId employee id
     * @param startedAfter started after
     * @param startedBefore started before
     * @param first first
     * @param max max
     * @return pair of list of employee work shifts and count
     */
    suspend fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        first: Int,
        max: Int
    ): Pair<List<WorkShiftEntity>, Long> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        queryBuilder.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        if (startedAfter != null) {
            queryBuilder.append(" AND startedAt >= :startedAfter")
            parameters.and("startedAfter", startedAfter.toLocalDate())
        }

        if (startedBefore != null) {
            queryBuilder.append(" AND startedAt <= :startedBefore")
            parameters.and("startedBefore", startedBefore.toLocalDate())
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.descending("date"), parameters),
            firstIndex = first,
            maxResults = max
        )
    }

}