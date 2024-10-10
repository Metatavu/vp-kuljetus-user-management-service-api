package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.model.ClientAppMetadata
import fi.metatavu.vp.usermanagement.model.ClientAppStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.OffsetDateTime
import java.util.*

/**
 * Controller for client apps
 */
@ApplicationScoped
class ClientAppController {

    @Inject
    lateinit var clientAppRepository: ClientAppRepository

    /**
     * Lists client apps
     *
     * @param status filter by status
     * @param first first result
     * @param max max results
     * @return pair of client apps and count
     */
    suspend fun list(status: ClientAppStatus?, first: Int?, max: Int?): Pair<List<ClientAppEntity>, Long> {
        return clientAppRepository.list(status = status, first = first, max = max)
    }

    /**
     * Creates a new client app
     *
     * @param deviceId device id
     * @param name name
     * @param metadata metadata
     * @return created client app
     */
    suspend fun create(deviceId: String, name: String?, metadata: ClientAppMetadata): ClientAppEntity {
        return clientAppRepository.create(
            id = UUID.randomUUID(),
            deviceId = deviceId,
            name = name,
            deviceOs = metadata.deviceOS,
            deviceOsVersion = metadata.deviceOSVersion,
            appVersion = metadata.appVersion
        )
    }

    /**
     * Finds a client app by id
     *
     * @param id id
     * @return found client app or null if not found
     */
    suspend fun find(id: UUID): ClientAppEntity? {
        return clientAppRepository.findByIdSuspending(id)
    }

    /**
     * Finds a client app by device id
     *
     * @param deviceId device id
     * @return found client app or null if not found
     */
    suspend fun find(deviceId: String): ClientAppEntity? {
        return clientAppRepository.findByDeviceId(deviceId)
    }

    /**
     * Deletes a client app
     *
     * @param clientApp client app
     */
    suspend fun delete(clientApp: ClientAppEntity) {
        clientAppRepository.deleteSuspending(clientApp)
    }

    /**
     * Updates a client app
     *
     * @param clientApp client app
     * @param name name
     * @param metadata metadata
     * @param status status
     * @param lastLoginAt last login at
     * @param userId user id
     * @return updated client app
     */
    suspend fun update(
        clientApp: ClientAppEntity,
        name: String?,
        metadata: ClientAppMetadata,
        status: ClientAppStatus,
        lastLoginAt: OffsetDateTime?,
        userId: UUID?
    ): ClientAppEntity {
        return clientAppRepository.update(
            clientApp = clientApp,
            name = name,
            deviceOs = metadata.deviceOS,
            deviceOsVersion = metadata.deviceOSVersion,
            appVersion = metadata.appVersion,
            status = status,
            lastLoginAt = lastLoginAt,
            userId = userId
        )
    }
}