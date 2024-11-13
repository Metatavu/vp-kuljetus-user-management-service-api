package fi.metatavu.vp.usermanagement

import fi.metatavu.jaxrs.test.functional.builder.AbstractAccessTokenTestBuilder
import fi.metatavu.jaxrs.test.functional.builder.AbstractTestBuilder
import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.jaxrs.test.functional.builder.auth.AuthorizedTestBuilderAuthentication
import fi.metatavu.jaxrs.test.functional.builder.auth.KeycloakAccessTokenProvider
import fi.metatavu.jaxrs.test.functional.builder.auth.NullAccessTokenProvider
import fi.metatavu.vp.usermanagement.auth.TestBuilderAuthentication
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import org.eclipse.microprofile.config.ConfigProvider

/**
 * Abstract test builder class
 *
 * @author Jari Nykänen
 * @author Antti Leppä
 */
class TestBuilder(private val config: Map<String, String>): AbstractAccessTokenTestBuilder<ApiClient>() {

    val driver1 = createTestBuilderAuthentication(username = "driver1", password = "test")
    val driver2 = createTestBuilderAuthentication(username = "driver2", password = "test")
    val manager = createTestBuilderAuthentication(username = "manager", password = "test")
    val employee = createTestBuilderAuthentication(username = "employee", password = "test")

    override fun createTestBuilderAuthentication(
        abstractTestBuilder: AbstractTestBuilder<ApiClient, AccessTokenProvider>,
        authProvider: AccessTokenProvider
    ): AuthorizedTestBuilderAuthentication<ApiClient, AccessTokenProvider> {
        return TestBuilderAuthentication(this, authProvider, null)
    }

    /**
     * Returns authentication with api key
     *
     * @param apiKey device key
     * @return authorized client
     */
    fun setApiKey(apiKey: String? = null): TestBuilderAuthentication {
        val key = apiKey ?: "test-api-key"
        return TestBuilderAuthentication(this, NullAccessTokenProvider(), key)
    }

    /**
     * Creates test builder authenticatior for given user
     *
     * @param username username
     * @param password password
     * @return test builder authenticatior for given user
     */
    private fun createTestBuilderAuthentication(username: String, password: String): TestBuilderAuthentication {
        val serverUrl = ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String::class.java).substringBeforeLast("/").substringBeforeLast("/")
        val realm: String = ConfigProvider.getConfig().getValue("quarkus.keycloak.devservices.realm-name", String::class.java)
        val clientId = "test"
        return TestBuilderAuthentication(this, KeycloakAccessTokenProvider(serverUrl, realm, clientId, username, password, null), null)
    }

}