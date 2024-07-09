package fi.metatavu.vp.usermanagement.drivers

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.api.model.Driver
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import fi.metatavu.vp.usermanagement.users.UserController.Companion.ARCHIVED_AT_ATTRIBUTE
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.*


/**
 * Translator for translating UserRepresentation to Driver
 */
@ApplicationScoped
class DriverTranslator: AbstractTranslator<UserRepresentation, Driver>() {
    override suspend fun translate(entity: UserRepresentation): Driver {
        return Driver(
            id = UUID.fromString(entity.id),
            displayName = entity.firstName + " " + entity.lastName,
            archivedAt = entity.attributes?.get(ARCHIVED_AT_ATTRIBUTE)?.get(0)?.let { OffsetDateTime.parse(it) }
        )
    }

}
