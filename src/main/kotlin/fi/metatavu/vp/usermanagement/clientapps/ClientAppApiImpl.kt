package fi.metatavu.vp.usermanagement.clientapps

import fi.metatavu.vp.usermanagement.model.ClientApp
import fi.metatavu.vp.usermanagement.model.ClientAppStatus
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.ClientAppsApi
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

/**
 * Client App API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class ClientAppApiImpl: ClientAppsApi, AbstractApi() {

    @Inject
    lateinit var clientAppController: ClientAppController

    @Inject
    lateinit var clientAppTranslator: ClientAppTranslator

    @WithTransaction
    override fun createClientApp(clientApp: ClientApp): Uni<Response> = withCoroutineScope {
        if (requestApiKey != apiKey) return@withCoroutineScope createForbidden(INVALID_API_KEY)

        // Check if client app already exists with same device id
        clientAppController.find(clientApp.deviceId)?.let {
            // If client app already exists and it's status is other than WAITING_FOR_APPROVAL, return conflict
            if (it.status != ClientAppStatus.WAITING_FOR_APPROVAL) {
                return@withCoroutineScope createConflict("Client app already exists")
            }

            // Return the existing client app
            return@withCoroutineScope createOk(clientAppTranslator.translate(it))

        }

        val createdClientApp = clientAppController.create(
            deviceId = clientApp.deviceId,
            name = clientApp.name,
            metadata = clientApp.metadata
        )

        createCreated(clientAppTranslator.translate(createdClientApp))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteClientApp(clientAppId: UUID): Uni<Response> = withCoroutineScope {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val clientApp = clientAppController.find(clientAppId) ?: return@withCoroutineScope createNotFound(createNotFoundMessage(CLIENT_APP, clientAppId))

        clientAppController.delete(clientApp)

        createNoContent()
    }


    override fun findClientApp(clientAppId: UUID): Uni<Response> = withCoroutineScope {
        if (loggedUserId == null && requestApiKey == null) return@withCoroutineScope createUnauthorized(UNAUTHORIZED)
        if (requestApiKey != null && requestApiKey != apiKey) return@withCoroutineScope createForbidden(INVALID_API_KEY)
        if (loggedUserId != null && !hasRealmRole(MANAGER_ROLE)) return@withCoroutineScope createForbidden(FORBIDDEN)

        val foundClientApp = clientAppController.find(clientAppId) ?: return@withCoroutineScope createNotFound(createNotFoundMessage(CLIENT_APP, clientAppId))

        createOk(clientAppTranslator.translate(foundClientApp))

    }

    @RolesAllowed(MANAGER_ROLE)
    override fun listClientApps(status: ClientAppStatus?, first: Int, max: Int): Uni<Response> = withCoroutineScope {
        loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        val (clientApps, count) = clientAppController.list(
            status = status,
            first = first,
            max = max
        )

        createOk(clientAppTranslator.translate(clientApps), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateClientApp(clientAppId: UUID, clientApp: ClientApp): Uni<Response> = withCoroutineScope {
        val userId = loggedUserId ?: return@withCoroutineScope createUnauthorized(UNAUTHORIZED)

        if (clientAppId != clientApp.id) return@withCoroutineScope createBadRequest("Client App ID in path and body don't match")

        val foundClientApp = clientAppController.find(clientAppId) ?: return@withCoroutineScope createNotFound(createNotFoundMessage(CLIENT_APP, clientAppId))

        val updatedClientApp = clientAppController.update(
            clientApp = foundClientApp,
            name = clientApp.name,
            metadata = clientApp.metadata,
            status = clientApp.status,
            lastLoginAt = clientApp.lastLoginAt,
            userId = userId
        )

        createOk(clientAppTranslator.translate(updatedClientApp))
    }

}