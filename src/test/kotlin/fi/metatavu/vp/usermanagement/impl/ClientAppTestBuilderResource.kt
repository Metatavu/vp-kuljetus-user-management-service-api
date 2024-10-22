package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.ClientAppsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.ClientApp
import fi.metatavu.vp.test.client.models.ClientAppStatus
import fi.metatavu.vp.test.client.models.VerifyClientAppRequest
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import java.util.*

/**
 * Test builder resource for client apps
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiKey api key
 * @param apiClient api client
 */
class ClientAppTestBuilderResource(
    private val testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    private val apiKey: String?,
    apiClient: ApiClient
): ApiTestBuilderResource<ClientApp, ApiClient>(testBuilder, apiClient) {
    override fun clean(t: ClientApp) {
        testBuilder.manager.clientApps.delete(t.id!!)
    }

    override fun getApi(): ClientAppsApi {
        if (apiKey != null) {
            ApiClient.apiKey["X-API-Key"] = apiKey
        }

        ApiClient.accessToken = accessTokenProvider?.accessToken
        return ClientAppsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Creates a new client app
     *
     * @param clientApp client app
     * @return created client app
     */
    fun create(clientApp: ClientApp): ClientApp {
        return addClosable(api.createClientApp(clientApp))
    }

    /**
     * Finds client app by id
     *
     * @param id client app id
     * @return found client app
     */
    fun find(id: UUID): ClientApp {
        return api.findClientApp(id)
    }

    /**
     * Lists client apps
     *
     * @param status status filter
     * @param first first result
     * @param max last result
     * @return client apps
     */
    fun list(status: ClientAppStatus? = null, first: Int? = null, max: Int? = null): Array<ClientApp> {
        return api.listClientApps(status, first, max)
    }

    /**
     * Updates client app
     *
     * @param clientAppId client app id
     * @param clientApp client app
     * @return updated client app
     */
    fun update(clientAppId: UUID, clientApp: ClientApp): ClientApp {
        return api.updateClientApp(clientAppId, clientApp)
    }

    /**
     * Deletes client app
     *
     * @param clientAppId client app id
     */
    fun delete(clientAppId: UUID) {
        api.deleteClientApp(clientAppId)
        removeCloseable {
            it is ClientApp && it.id == clientAppId
        }
    }

    /**
     * Verifies client app
     *
     * @param verifyClientAppRequest verify client app request
     * @return true if client app is verified, false otherwise
     */
    fun verifyClientApp(verifyClientAppRequest: VerifyClientAppRequest): Boolean {
        return api.verifyClientApp(verifyClientAppRequest)
    }

    /**
     * Asserts that verifying a client app fails with expected status
     *
     * @param verifyClientAppRequest verify client app request
     * @param expectedStatus expected status
     */
    fun assertVerifyClientAppFail(verifyClientAppRequest: VerifyClientAppRequest, expectedStatus: Int) {
        try {
            api.verifyClientApp(verifyClientAppRequest)
            throw AssertionError("Expected verify to fail with status $expectedStatus")
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts that creating a client app fails with expected status
     *
     * @param clientApp client app
     * @param expectedStatus expected status
     */
    fun assertCreateFail(clientApp: ClientApp, expectedStatus: Int) {
        try {
            api.createClientApp(clientApp)
            throw AssertionError("Expected create to fail with status $expectedStatus")
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts that updating a client app fails with expected status
     *
     * @param clientAppId client app id
     * @param clientApp client app
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(clientAppId: UUID, clientApp: ClientApp, expectedStatus: Int) {
        try {
            api.updateClientApp(clientAppId, clientApp)
            throw AssertionError("Expected update to fail with status $expectedStatus")
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts that finding a client app fails with expected status
     *
     * @param clientAppId client app id
     * @param expectedStatus expected status
     */
    fun assertFindFail(clientAppId: UUID, expectedStatus: Int) {
        try {
            api.findClientApp(clientAppId)
            throw AssertionError("Expected find to fail with status $expectedStatus")
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts that deleting a client app fails with expected status
     *
     * @param clientAppId client app id
     * @param expectedStatus expected status
     */
    fun assertDeleteFail(clientAppId: UUID, expectedStatus: Int) {
        try {
            api.deleteClientApp(clientAppId)
            throw AssertionError("Expected delete to fail with status $expectedStatus")
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }
}