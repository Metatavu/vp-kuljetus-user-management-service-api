package fi.metatavu.vp.usermanagement.payrollexports

import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*

@ApplicationScoped
class PayrollExportRepository: AbstractRepository<PayrollExportEntity, UUID>() {

    /**
     * Save a payroll export to the database
     *
     * @param employeeId employee id
     * @param fileName file name
     * @param exportedAt exported at
     * @param creatorId creator id
     */
    suspend fun create(
        employeeId: UUID,
        fileName: String,
        exportedAt: OffsetDateTime,
        creatorId: UUID
    ): PayrollExportEntity {
        val payrollExport = PayrollExportEntity()
        payrollExport.id = UUID.randomUUID()
        payrollExport.employeeId = employeeId
        payrollExport.fileName = fileName
        payrollExport.creatorId = creatorId
        payrollExport.exportedAt = exportedAt
        return persistSuspending(payrollExport)
    }

    /**
     * Retrieves payroll exports from the database with the given filters
     *
     * @param employeeId
     * @param exportedAfter
     * @param exportedBefore
     * @param first
     * @param max
     */
    suspend fun list(
        employeeId: UUID?,
        exportedAfter: OffsetDateTime?,
        exportedBefore: OffsetDateTime?,
        first: Int?,
        max: Int?
    ): Pair<List<PayrollExportEntity>, Long> {
        val queryBuilder = StringBuilder()
        val parameters = Parameters()

        if (employeeId != null) {
            addCondition(queryBuilder,"employeeId = :employeeId")
            parameters.and("employeeId", employeeId)
        }

        if (exportedAfter != null) {
            addCondition(queryBuilder, "exportedAt > :exportedAfter")
            parameters.and("exportedAfter", exportedAfter)
        }

        if (exportedBefore != null) {
            addCondition(queryBuilder,"exportedAt < :exportedBefore")
            parameters.and("exportedBefore", exportedBefore)
        }

        return queryWithCount(
            query = find(queryBuilder.toString(), Sort.by("exportedAt", Sort.Direction.Descending), parameters),
            firstIndex = first,
            maxResults = max
        )
    }
}