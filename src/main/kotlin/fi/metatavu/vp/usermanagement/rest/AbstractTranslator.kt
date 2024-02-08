package fi.metatavu.vp.usermanagement.rest

/**
 * Abstract translator class
 *
 * @author Jari Nykänen
 */
abstract class AbstractTranslator<E, R> {

    abstract suspend fun translate(entity: E): R

    /**
     * Translates list of entities
     *
     * @param entities list of entities to translate
     * @return List of translated entities
     */
    open suspend fun translate(entities: List<E>): List<R> {
        return entities.mapNotNull { entity -> translate(entity) }
    }

}