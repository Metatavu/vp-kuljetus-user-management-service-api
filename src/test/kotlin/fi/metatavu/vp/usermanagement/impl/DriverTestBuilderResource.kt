package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.deliveryinfo.functional.settings.ApiTestSettings
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
 * @param apiClient api client
 */
class DriverTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<Driver, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: Driver) {
        // Does not need cleaning since API does not create any entities
    }

    override fun getApi(): DriversApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return DriversApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists drivers
     *
     * @param first first result
     * @param last last result
     * @return drivers
     */
    fun listDrivers(first: Int? = null, last: Int? = null): Array<Driver> {
        return api.listDrivers(first, last)
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