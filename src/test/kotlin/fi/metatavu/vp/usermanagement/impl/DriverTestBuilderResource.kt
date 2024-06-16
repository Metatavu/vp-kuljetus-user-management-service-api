package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.test.client.apis.DriversApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.Driver
import fi.metatavu.vp.usermanagement.TestBuilder
import org.junit.Assert
import java.util.UUID

/**
 * Test builder resource for drivers
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiKey api key
 * @param apiClient api client
 */
class DriverTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    private val apiKey: String?,
    apiClient: ApiClient
) : ApiTestBuilderResource<Driver, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: Driver) {
        // Does not need cleaning since API does not create any entities
    }

    override fun getApi(): DriversApi {
        if (apiKey != null) {
            ApiClient.apiKey["X-API-Key"] = apiKey
        }
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return DriversApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists drivers
     *
     * @param driverCardId driver card id
     * @param archived archived filter
     * @param first first result
     * @param max last result
     * @return drivers
     */
    fun listDrivers(driverCardId: String? = null, archived: Boolean? = null, first: Int? = null, max: Int? = null): Array<Driver> {
        return api.listDrivers(driverCardId, archived, first, max)
    }

    /**
     * Asserts that listing drivers fails with expected status
     *
     * @param expectedStatus expected status
     */
    fun assertListFail(expectedStatus: Int) {
        try {
            api.listDrivers()
            Assert.fail(String.format("Expected list to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Finds driver
     *
     * @param driverId driver id
     * @return driver
     */
    fun findDriver(driverId: UUID): Driver {
        return api.findDriver(driverId)
    }
}