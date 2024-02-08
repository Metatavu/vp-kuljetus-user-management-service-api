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
     * @param first first result
     * @param max max results
     * @return drivers and total count
     */
    suspend fun listDrivers(first: Int?, max: Int?): Pair<Array<UserRepresentation>, Int> {
        return keycloakAdminClient.listUsersOfRole(
            role = AbstractApi.DRIVER_ROLE,
            first = first,
            max = max
        )
    }

}