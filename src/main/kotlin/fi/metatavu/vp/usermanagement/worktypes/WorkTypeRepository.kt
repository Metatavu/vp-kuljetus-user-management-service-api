package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkTypeCategory
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

/**
 * Repository for work types
 */
@ApplicationScoped
class WorkTypeRepository : AbstractRepository<WorkTypeEntity, UUID>() {

    /**
     * Creates a new work type
     *
     * @param id work type id
     * @param name work type name
     * @param category work type category
     * @return created work type
     */
    suspend fun create(id: UUID, name: String, category: WorkTypeCategory): WorkTypeEntity {
        val workTypeEntity = WorkTypeEntity()
        workTypeEntity.id = id
        workTypeEntity.name = name
        workTypeEntity.category = category
        return persistSuspending(workTypeEntity)
    }

    /**
     * Lists work types
     *
     * @param category work type category
     * @return list of work types
     */
    suspend fun list(category: WorkTypeCategory?): Pair<List<WorkTypeEntity>, Long> {
        val sort = Sort.descending("name")
        return when (category) {
            null -> queryWithCount(findAll(sort))
            else -> queryWithCount(find("category", sort, category))
        }
    }
}