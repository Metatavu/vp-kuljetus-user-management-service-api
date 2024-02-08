package fi.metatavu.vp.usermanagement.keycloak

import fi.metatavu.keycloak.adminclient.apis.RoleContainerApi
import fi.metatavu.keycloak.adminclient.apis.UserApi
import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.*

/**
 * Controller for accessing Keycloak as admin user
 */
@ApplicationScoped
class KeycloakAdminClient : KeycloakClient() {
    override var clientType = KeycloakClientType.ADMIN

    @ConfigProperty(name = "vp.keycloak.admin.secret")
    lateinit var keycloakAdminClientSecret: String

    @ConfigProperty(name = "vp.keycloak.admin.client")
    lateinit var keycloakAdminClientId: String

    @ConfigProperty(name = "vp.keycloak.admin.password")
    lateinit var keycloakAdminPassword: String

    @ConfigProperty(name = "vp.keycloak.admin.user")
    lateinit var keycloakAdminUser: String

    @Inject
    lateinit var vertxCore: io.vertx.core.Vertx

    /**
     * Lists users of a role
     *
     * @param role role name
     * @param first first result
     * @param max max results
     * @return users and total count
     */
    suspend fun listUsersOfRole(
        role: String,
        first: Int?,
        max: Int?
    ): Pair<Array<UserRepresentation>, Int> {
        val users = getRoleContainerApi().realmRolesRoleNameUsersGet(
            realm = getRealm(),
            roleName = role,
            first = first ?: 0,
            max = max ?: 100
        )
        // Keycloak REST api does not seem to provide total count in the response headers for this endpoint => new request has to be made to get the total count
        val count = getRoleContainerApi().realmRolesRoleNameUsersGet(
            realm = getRealm(),
            roleName = role
        ).size
        return users to count
    }

    /**
     * Finds a user by id
     *
     * @param userId user id
     * @return found user or null if not found
     */
    suspend fun findUserById(userId: UUID): UserRepresentation? {
        return getUserApi().realmUsersIdGet(realm = getRealm(), id = userId.toString())
    }

    /**
     * Requests a new access token
     *
     * @return new access token
     */
    override fun requestNewToken(): Uni<KeycloakAccessToken> {
        return sendTokenRequest(
            keycloakAdminClientId,
            keycloakAdminClientSecret,
            keycloakAdminUser,
            keycloakAdminPassword
        )
    }

    /**
     * Gets user api with valid access token
     *
     * @return Api with valid access token
     */
    suspend fun getUserApi(): UserApi {
        val baseUrl = getBaseUrl()
        return UserApi(
            basePath = "${baseUrl}/admin/realms",
            accessToken = getAccessToken(),
            vertx = vertxCore
        )
    }

    suspend fun getRoleContainerApi(): RoleContainerApi {
        val baseUrl = getBaseUrl()

        return RoleContainerApi(
            basePath = "${baseUrl}/admin/realms",
            accessToken = getAccessToken(),
            vertx = vertxCore
        )
    }

    /**
     * Gets base url
     *
     * @return base url
     */
    private fun getBaseUrl(): String {
        return keycloakUrl.substringBefore("/realms")
    }

    /**
     * Gets realm name
     *
     * @return realm name
     */
    private fun getRealm(): String {
        return keycloakUrl.substringAfterLast("realms/").substringBefore("/")
    }

}