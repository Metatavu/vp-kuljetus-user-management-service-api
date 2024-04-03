package fi.metatavu.vp.usermanagement.keycloak

import fi.metatavu.keycloak.adminclient.apis.RoleContainerApi
import fi.metatavu.keycloak.adminclient.apis.UserApi
import fi.metatavu.keycloak.adminclient.apis.UsersApi
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
     * Lists users of a role.
     * All users are listed since keycloak does not provide total count in response headers
     * -> in order to get paging done it has to be done manually
     *
     * @param role role name
     * @param first first result
     * @param max max results
     * @return users and total users
     */
    suspend fun listUsersOfRole(
        role: String,
    ): Array<UserRepresentation> {
        return getRoleContainerApi().realmRolesRoleNameUsersGet(
            realm = getRealm(),
            roleName = role
        )
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
     * Lists users by driver card id
     *
     * @param driverCardId driver id
     * @return list of users
     */
    suspend fun findUserByDriverId(driverCardId: String): Array<UserRepresentation> {
        return getUsersApi().realmUsersGet(realm = getRealm(), q = "driverCardId:$driverCardId")
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

    /**
     * Gets users api with valid access token
     *
     * @return Api with valid access token
     */
    suspend fun getUsersApi(): UsersApi {
        val baseUrl = getBaseUrl()
        return UsersApi(
            basePath = "${baseUrl}/admin/realms",
            accessToken = getAccessToken(),
            vertx = vertxCore
        )
    }

    /**
     * Gets RoleContainerApi with valid access token
     *
     * @return Api with valid access token
     */
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