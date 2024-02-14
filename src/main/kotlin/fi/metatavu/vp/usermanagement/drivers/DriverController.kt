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
     * @param archived archived status
     * @param first first result
     * @param max max results
     * @return drivers and total count
     */
    suspend fun listDrivers(archived: Boolean?, first: Int?, max: Int?): Pair<List<UserRepresentation>, Int> {
        val allRoleUsers = keycloakAdminClient.listUsersOfRole(
            role = AbstractApi.DRIVER_ROLE,
        ).toList()

        val pagedUsers = if (first != null && max != null) {
            val maxIndex = if (allRoleUsers.size < first + max) {
                allRoleUsers.size
            } else {
                max
            }
            allRoleUsers.subList(first, maxIndex)
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