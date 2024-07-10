package fi.metatavu.vp.usermanagement.persistence

import io.quarkus.hibernate.reactive.panache.PanacheQuery
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase
import io.quarkus.panache.common.Sort
import io.smallrye.mutiny.coroutines.awaitSuspending

/**
 * Abstract base class for all Repository classes
 *
 * @author Jari Nykänen
 *
 * @param <T> entity type
 */
abstract class AbstractRepository<Entity, Id> : PanacheRepositoryBase<Entity, Id> {

    /**
     * Adds condition to the list of conditions. Note: parameters are not added here
     *
     * @param stringBuilder string builder
     * @param newCondition new condition
     */
    fun addCondition(stringBuilder: java.lang.StringBuilder, newCondition: String) {
        if (stringBuilder.toString().isNotBlank() || stringBuilder.toString().isNotEmpty()) {
            stringBuilder.append(" and $newCondition ")
        } else {
            stringBuilder.append(" $newCondition ")
        }
    }

    /**
     * Applies order parameter to query
     *
     * @param queryString original query string
     * @param asc if ordering is ascending (default is false)
     * @return formatter query string
     */
    fun applyOrder(queryString: String, asc: Boolean? = false): String {
        if (asc == true) {
            return "$queryString order by createdAt asc"
        }

        return "$queryString order by createdAt desc"
    }

    /**
     * Persists entity and waits for result
     *
     * @param entity new entity
     * @return saved entity
     */
    open suspend fun persistSuspending(entity: Entity): Entity {
        return persist(entity).awaitSuspending()
    }

    /**
     * Deletes entity and waits for result
     *
     * @param entity entity to delete
     * @return void
     */
    open suspend fun deleteSuspending(entity: Entity) {
        delete(entity).awaitSuspending()
    }

    /**
     * Lists all entities
     *
     * @param firstIndex first
     * @param maxResults max
     * @return entities
     */
    open suspend fun listAllSuspending(firstIndex: Int?, maxResults: Int?, sort: Sort?): Pair<List<Entity>, Long> {
        val count = findAll().count().awaitSuspending()
        return if (firstIndex != null && maxResults != null) {
            Pair(findAll(sort).range<Entity>(firstIndex,  maxResults + firstIndex - 1).list<Entity>().awaitSuspending(), count)
        } else
            Pair(listAll(sort).awaitSuspending(), count)
    }

    /**
     * Applies range to query and executes it
     *
     * @param query find query
     * @param firstIndex first index
     * @param maxResults max results
     * @return entities
     */
    open suspend fun queryWithCount(
        query: PanacheQuery<Entity>,
        firstIndex: Int? = null,
        maxResults: Int? = null
    ): Pair<List<Entity>, Long> {
        val count = query.count().awaitSuspending()
        return if (firstIndex != null || maxResults != null) {
            val first = firstIndex ?: 0
            val max = maxResults ?: 10
            Pair(query.range<Entity>(first,  max + first - 1).list<Entity>().awaitSuspending(), count)
        } else
            Pair(query.list<Entity>().awaitSuspending(), count)
    }

    /**
     * Finds entity by id
     *
     * @param id id
     * @return entity if found
     */
    open suspend fun findByIdSuspending(id: Id): Entity? {
        return findById(id).awaitSuspending()
    }
}