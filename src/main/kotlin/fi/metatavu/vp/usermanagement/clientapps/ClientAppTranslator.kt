package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.model.ClientApp
import fi.metatavu.vp.usermanagement.model.ClientAppMetadata
import fi.metatavu.vp.usermanagement.rest.AbstractTranslator
import jakarta.enterprise.context.ApplicationScoped

/**
 * Translator for Client Apps
 */
@ApplicationScoped
class ClientAppTranslator: AbstractTranslator<ClientAppEntity, ClientApp>() {
    override suspend fun translate(entity: ClientAppEntity): ClientApp = ClientApp(
        id = entity.id,
        deviceId = entity.deviceId,
        name = entity.name,
        status = entity.status,
        metadata = ClientAppMetadata(
            deviceOS = entity.deviceOs,
            deviceOSVersion = entity.deviceOsVersion,
            appVersion = entity.appVersion
        ),
        lastLoginAt = entity.lastLoginAt,
        createdAt = entity.createdAt,
        modifiedAt = entity.modifiedAt,
    )
}