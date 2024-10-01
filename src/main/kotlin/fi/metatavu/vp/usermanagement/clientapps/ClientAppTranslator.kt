package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.clientapps.ClientAppController.Companion.APP_VERSION_FIELD
import fi.metatavu.vp.usermanagement.clientapps.ClientAppController.Companion.DEVICE_OS_FIELD
import fi.metatavu.vp.usermanagement.clientapps.ClientAppController.Companion.DEVICE_OS_VERSION_FIELD
import fi.metatavu.vp.usermanagement.model.ClientApp
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for Client Apps
 */
@ApplicationScoped
class ClientAppTranslator: AbstractTranslator<ClientAppEntity, ClientApp>() {
    override suspend fun translate(entity: ClientAppEntity): ClientApp {
        val metadata = mutableMapOf<String, String>()

        entity.deviceOs?.let { metadata[DEVICE_OS_FIELD] = it }
        entity.deviceOsVersion?.let { metadata[DEVICE_OS_VERSION_FIELD] = it }
        entity.appVersion?.let { metadata[APP_VERSION_FIELD] = it }
        return ClientApp(
            id = entity.id,
            deviceId = entity.deviceId,
            name = entity.name,
            status = entity.status,
            metadata = metadata,
            lastLoginAt = entity.lastLoginAt,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }
}