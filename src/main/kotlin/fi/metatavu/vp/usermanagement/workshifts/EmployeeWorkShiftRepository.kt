package fi.metatavu.vp.usermanagement.workshifts

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
class EmployeeWorkShiftRepository: AbstractRepository<EmployeeWorkShiftEntity, UUID>() {

    /**
     * Creates a new employee work shift
     *
     * @param id id
     * @param employeeId employee id
     * @param date date
     * @param approved approved
     * @return created employee work shift
     */
    suspend fun create(
        id: UUID,
        employeeId: UUID,
        date: LocalDate,
        approved: Boolean
    ): EmployeeWorkShiftEntity {
        val employeeWorkShift = EmployeeWorkShiftEntity()
        employeeWorkShift.id = id
        employeeWorkShift.employeeId = employeeId
        employeeWorkShift.date = date
        employeeWorkShift.approved = approved
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
    ): Pair<List<EmployeeWorkShiftEntity>, Long> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        queryBuilder.append("employeeId = :employeeId")
        parameters.and("employeeId", employeeId)

        if (startedAfter != null) {
            queryBuilder.append(" AND date >= :startedAfter")
            parameters.and("startedAfter", startedAfter)
        }

        if (startedBefore != null) {
            queryBuilder.append(" AND date <= :startedBefore")
            parameters.and("startedBefore", startedBefore)
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.descending("date"), parameters),
            firstIndex = first,
            maxResults = max
        )
    }

}