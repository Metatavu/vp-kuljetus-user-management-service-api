package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.model.ClientAppStatus
import fi.metatavu.vp.usermanagement.persistence.AbstractRepository
import io.quarkus.panache.common.Parameters
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class ClientAppRepository: AbstractRepository<ClientAppEntity, UUID>() {

    /**
     * Lists client apps
     *
     * @param status filter by status
     * @param first first result
     * @param max max results
     * @return pair of client apps and count
     */
    suspend fun list(status: ClientAppStatus?, first: Int?, max: Int?): Pair<List<ClientAppEntity>, Long> {
        val stringBuilder = StringBuilder()
        val parameters = Parameters()
        if (status != null) {
            stringBuilder.append("status = :status")
            parameters.and("status", status)
        }
        return queryWithCount(find(stringBuilder.toString(), parameters), first, max)
    }

    /**
     * Creates a new client app
     *
     * @param id id
     * @param deviceId device i
     * @param name name
     * @param deviceOs device os
     * @param deviceOsVersion device os version
     * @param appVersion app version
     * @return created client app
     */
    suspend fun create(
        id: UUID,
        deviceId: String,
        name: String?,
        deviceOs: String?,
        deviceOsVersion: String?,
        appVersion: String?
    ): ClientAppEntity {
        val clientAppEntity = ClientAppEntity()
        clientAppEntity.id = id
        clientAppEntity.deviceId = deviceId
        clientAppEntity.status = ClientAppStatus.WAITING_FOR_APPROVAL
        clientAppEntity.name = name
        clientAppEntity.deviceOs = deviceOs
        clientAppEntity.deviceOsVersion = deviceOsVersion
        clientAppEntity.appVersion = appVersion

        return persistSuspending(clientAppEntity)
    }

    /**
     * Finds a client app by device id
     *
     * @param deviceId device id
     * @return found client app or null if not found
     */
    suspend fun findByDeviceId(deviceId: String): ClientAppEntity? {
        println("DEVICE ID IS: $deviceId")
        return find("deviceId = ?1", deviceId).firstResult<ClientAppEntity>().awaitSuspending()
//        return findSuspending("deviceId = ?1", deviceId)
    }

    /**
     * Updates a client app
     *
     * @param clientApp client app
     * @param name name
     * @param deviceOs device os
     * @param deviceOsVersion device os version
     * @param appVersion app version
     * @param status status
     * @param lastLoginAt last login at
     * @param userId user id
     * @return updated client app
     */
    suspend fun update(
        clientApp: ClientAppEntity,
        name: String?,
        deviceOs: String?,
        deviceOsVersion: String?,
        appVersion: String?,
        status: ClientAppStatus,
        lastLoginAt: OffsetDateTime?,
        userId: UUID?
    ): ClientAppEntity {
        clientApp.name = name
        clientApp.deviceOs = deviceOs
        clientApp.deviceOsVersion = deviceOsVersion
        clientApp.appVersion = appVersion
        clientApp.status = status
        clientApp.lastLoginAt = lastLoginAt
        clientApp.lastModifierId = userId

        return persistSuspending(clientApp)
    }
}