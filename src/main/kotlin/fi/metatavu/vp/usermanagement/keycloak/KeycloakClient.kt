package fi.metatavu.vp.usermanagement.keycloak

import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.core.MultiMap
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.OffsetDateTime
import java.util.*

/**
 * Abstract class for requesting Keycloak's tokens
 */
abstract class KeycloakClient {

    abstract var clientType: KeycloakClientType

   @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    lateinit var keycloakUrl: String

    @Inject
    lateinit var vertx: Vertx

    private val expireSlack = 60L

    private lateinit var accessTokensMap: EnumMap<KeycloakClientType, KeycloakAccessToken?>

    private lateinit var accessTokenExpiresMap: EnumMap<KeycloakClientType, OffsetDateTime?>

    /**
     * Post construct method for initializing accessTokens & accessTokenExpires EnumMaps
     */
    @PostConstruct
    fun init() {
        accessTokensMap = EnumMap<KeycloakClientType, KeycloakAccessToken?>(KeycloakClientType::class.java)
        accessTokenExpiresMap = EnumMap<KeycloakClientType, OffsetDateTime?>(KeycloakClientType::class.java)
    }

    /**
     * Resolves an access token from Keycloak for the given client, this is the proper way to access the token
     *
     * @return access token
     */
    suspend fun getAccessToken(): String? {
        val accessToken = accessTokensMap[clientType]
        val accessTokenExpires = accessTokenExpiresMap[clientType]

        val now = OffsetDateTime.now()
        val expires = accessTokenExpires?.minusSeconds(expireSlack)

        if (accessToken == null || expires == null || expires.isBefore(now)) {
            val res = this.requestNewToken().awaitSuspending()
            accessTokensMap[clientType] = res
            accessTokenExpiresMap[clientType] = OffsetDateTime.now().plusSeconds(res.expiresIn)
        }

        return accessTokensMap[clientType]?.accessToken
    }

    /**
     * Gets keycloak token
     *
     * @param clientId client id
     * @param clientSecret client secret
     * @param username username
     * @param password password
     * @return token future
     */
    fun sendTokenRequest(
        clientId: String,
        clientSecret: String,
        username: String,
        password: String
    ): Uni<KeycloakAccessToken> {
        val url = "${keycloakUrl}/protocol/openid-connect/token"
        val client = WebClient.create(vertx)
        val form = MultiMap.caseInsensitiveMultiMap()
        form.set("client_id", clientId)
        form.set("client_secret", clientSecret)
        form.set("grant_type", "password")
        form.set("username", username)
        form.set("password", password)
        return client
            .postAbs(url)
            .sendForm(form)
            .onItem().transform { it.bodyAsJson(KeycloakAccessToken::class.java) }
    }

    abstract fun requestNewToken(): Uni<KeycloakAccessToken>
}

enum class KeycloakClientType {
    ADMIN
}