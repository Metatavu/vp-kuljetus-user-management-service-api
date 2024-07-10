package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkType
import fi.metatavu.vp.api.model.WorkTypeCategory
import io.quarkus.panache.common.Parameters
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * Controller for work types
 */
@ApplicationScoped
class WorkTypeController {

    @Inject
    lateinit var workTypeRepository: WorkTypeRepository

    /**
     * Creates a new work type
     *
     * @param id work type id
     * @param name work type name
     * @param category work type category
     * @return created work type
     */
    suspend fun create(workType: WorkType): WorkTypeEntity {
        return workTypeRepository.create(
            id = UUID.randomUUID(),
            name = workType.name,
            category = workType.category
        )
    }

    /**
     * Lists work types
     *
     * @param category work type category
     * @return list of work types
     */
    suspend fun list(category: WorkTypeCategory?): Pair<List<WorkTypeEntity>, Long> {
        return workTypeRepository.list(category)
    }

    /**
     * Updates work type
     *
     * @param workType work type
     * @param name work type name
     * @param category work type category
     * @return updated work type
     */
    suspend fun find(workTypeId: String, category: WorkTypeCategory): WorkTypeEntity? {
        return workTypeRepository.find(
            "name = :name and category = :category",
            Parameters.with("name", workTypeId).and("category", category)
        ).firstResult<WorkTypeEntity>().awaitSuspending()
    }

    /**
     * Updates work type
     *
     * @param workType work type
     * @param name work type name
     * @param category work type category
     * @return updated work type
     */
    suspend fun find(workTypeId: UUID): WorkTypeEntity? {
        return workTypeRepository.findByIdSuspending(workTypeId)
    }

    /**
     * Updates work type
     *
     * @param workType work type
     * @param name work type name
     * @param category work type category
     * @return updated work type
     */
    suspend fun delete(found: WorkTypeEntity) {
        workTypeRepository.deleteSuspending(found)
    }


}