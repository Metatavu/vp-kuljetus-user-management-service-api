package fi.metatavu.vp.usermanagement.auth

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenTestBuilderAuthentication
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.impl.*

/**
 * Test builder authentication
 *
 * @author Jari Nykänen
 * @author Antti Leppä
 *
 * @param testBuilder test builder instance
 * @param accessTokenProvider access token provider
 * @param driverAppApiKey driver app api key
 * @param cronKey cron key
 */
class TestBuilderAuthentication(
    private val testBuilder: TestBuilder,
    val accessTokenProvider: AccessTokenProvider,
    private val driverAppApiKey: String? = null,
    private val cronKey: String? = null,
    private val keycloakApiKey: String? = null
) : AccessTokenTestBuilderAuthentication<ApiClient>(testBuilder, accessTokenProvider) {

    val drivers = DriverTestBuilderResource(testBuilder, accessTokenProvider,createClient(accessTokenProvider))
    val employees = EmployeeTestBuilderResource(testBuilder, accessTokenProvider, createClient(accessTokenProvider))
    val workEvents = WorkEventTestBuilderResource(testBuilder, accessTokenProvider, cronKey = this.cronKey, createClient(accessTokenProvider))
    val holidays = HolidayTestBuilderResource(testBuilder, accessTokenProvider, createClient(accessTokenProvider))
    val workShifts = WorkShiftTestBuilderResource(testBuilder, accessTokenProvider, cronKey = this.cronKey, createClient(accessTokenProvider))
    val workShiftHours = WorkShiftHoursTestBuilderResource(testBuilder, accessTokenProvider, createClient(accessTokenProvider))
    val clientApps = ClientAppTestBuilderResource(testBuilder, accessTokenProvider, driverAppApiKey = this.driverAppApiKey, keycloakApiKey = this.keycloakApiKey, createClient(accessTokenProvider))
    val workShiftChangeSets = WorkShiftChangeSetsTestBuilderResource(testBuilder, accessTokenProvider, createClient(accessTokenProvider))
    val payrollExports = PayrollExportTestBuilderResource(testBuilder, accessTokenProvider, createClient(accessTokenProvider))

    override fun createClient(authProvider: AccessTokenProvider): ApiClient {
        val result = ApiClient(ApiTestSettings.apiBasePath)
        ApiClient.accessToken = authProvider.accessToken
        return result
    }

}