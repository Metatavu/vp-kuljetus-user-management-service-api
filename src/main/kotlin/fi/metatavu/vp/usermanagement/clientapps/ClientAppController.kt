package fi.metatavu.vp.usermanagement.clientapps

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

    suspend fun create(deviceId: String, name: String?, metadata: Map<String, String>?): ClientAppEntity {
        return clientAppRepository.create(
            id = UUID.randomUUID(),
            deviceId = deviceId,
            name = name,
            deviceOs = metadata?.get(DEVICE_OS_FIELD),
            deviceOsVersion = metadata?.get(DEVICE_OS_VERSION_FIELD),
            appVersion = metadata?.get(APP_VERSION_FIELD)
        )
    }

    suspend fun find(id: UUID): ClientAppEntity? {
        return clientAppRepository.findByIdSuspending(id)
    }

    suspend fun find(deviceId: String): ClientAppEntity? {
        return clientAppRepository.findByDeviceId(deviceId)
    }

    suspend fun delete(clientApp: ClientAppEntity) {
        clientAppRepository.delete(clientApp)
    }

    suspend fun update(
        clientApp: ClientAppEntity,
        name: String?,
        metadata: Map<String, String>?,
        status: ClientAppStatus,
        lastLoginAt: OffsetDateTime?,
        userId: UUID?
    ): ClientAppEntity {
        return clientAppRepository.update(
            clientApp = clientApp,
            name = name,
            deviceOs = metadata?.get(DEVICE_OS_FIELD),
            deviceOsVersion = metadata?.get(DEVICE_OS_VERSION_FIELD),
            appVersion = metadata?.get(APP_VERSION_FIELD),
            status = status,
            lastLoginAt = lastLoginAt,
            userId = userId
        )
    }

    companion object {
        const val DEVICE_OS_FIELD = "deviceOs"
        const val DEVICE_OS_VERSION_FIELD = "deviceOsVersion"
        const val APP_VERSION_FIELD = "appVersion"
    }
}