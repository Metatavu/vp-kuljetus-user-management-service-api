package fi.metatavu.vp.usermanagement.auth

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenTestBuilderAuthentication
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.impl.DriverTestBuilderResource
import fi.metatavu.vp.usermanagement.impl.EmployeeTestBuilderResource
import fi.metatavu.vp.usermanagement.impl.WorkTypeTestBuilderResource

/**
 * Test builder authentication
 *
 * @author Jari Nykänen
 * @author Antti Leppä
 *
 * @param testBuilder test builder instance
 * @param accessTokenProvider access token provider
 * @param apiKey api key
 */
class TestBuilderAuthentication(
    private val testBuilder: TestBuilder,
    val accessTokenProvider: AccessTokenProvider,
    private val apiKey: String?
) : AccessTokenTestBuilderAuthentication<ApiClient>(testBuilder, accessTokenProvider) {

    val drivers = DriverTestBuilderResource(testBuilder, accessTokenProvider, this.apiKey, createClient(accessTokenProvider))
    val employees = EmployeeTestBuilderResource(testBuilder, accessTokenProvider, this.apiKey, createClient(accessTokenProvider))
    val workTypes = WorkTypeTestBuilderResource(testBuilder, accessTokenProvider, this.apiKey, createClient(accessTokenProvider))

    override fun createClient(authProvider: AccessTokenProvider): ApiClient {
        val result = ApiClient(ApiTestSettings.apiBasePath)
        ApiClient.accessToken = authProvider.accessToken
        return result
    }

}