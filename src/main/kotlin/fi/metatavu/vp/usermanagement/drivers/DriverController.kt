package fi.metatavu.vp.usermanagement.drivers

import fi.metatavu.keycloak.adminclient.models.UserRepresentation
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.keycloak.KeycloakAdminClient
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * Controller for driver management
 */
@ApplicationScoped
class DriverController {

    @Inject
    lateinit var keycloakAdminClient: KeycloakAdminClient

    /**
     * Finds a driver by id
     *
     * @param driverId driver id
     * @return found driver or null if not found
     */
    suspend fun findDriver(driverId: UUID): UserRepresentation? {
        val user = keycloakAdminClient.findUserById(driverId)
        if (user?.realmRoles?.contains(AbstractApi.DRIVER_ROLE) == false) {
            return null
        }

        return user
    }

    /**
     * Lists drivers
     *
     * @param driverCardId driver card id
     * @param archived archived status
     * @param first first result
     * @param max max results
     * @return drivers and total count
     */
    suspend fun listDrivers(driverCardId: String?, archived: Boolean?, first: Int?, max: Int?): Pair<List<UserRepresentation>, Int> {
        if (driverCardId != null) {
            // no need for paging when driver card id is filtered
            keycloakAdminClient.findUserByDriverId(driverCardId).let {
                val users = if (archived != null) {
                    it.filter { user -> user.enabled != archived }
                } else it.toList()
                return users to users.size
            }
        }

        val allRoleUsers = keycloakAdminClient.listUsersOfRole(
            role = AbstractApi.DRIVER_ROLE,
        ).toList()

        val pagedUsers = if (first != null || max != null) {
            val firstResult = first ?: 0
            val maxResults = max ?: 10
            val maxIndex = if (allRoleUsers.size < firstResult + maxResults) {
                allRoleUsers.size
            } else {
                maxResults + firstResult
            }
            if (maxIndex < firstResult) return emptyList<UserRepresentation>() to 0
            allRoleUsers.subList(firstResult, maxIndex)
        } else {
            allRoleUsers
        }

        return if (archived == true) {
            pagedUsers.filter { it.enabled == false } to allRoleUsers.filter { it.enabled == false }.size
        } else {
            pagedUsers.filter { it.enabled == true } to allRoleUsers.filter { it.enabled == true }.size
        }
    }

}