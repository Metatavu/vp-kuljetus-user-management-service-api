package fi.metatavu.vp.usermanagement.drivers

import fi.metatavu.vp.api.spec.DriversApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.users.UserController
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

@RequestScoped
@Suppress("unused")
class DriversApiImpl: DriversApi, AbstractApi() {

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var driverTranslator: DriverTranslator

    @RolesAllowed(DRIVER_ROLE, MANAGER_ROLE)
    override fun findDriver(driverId: UUID): Uni<Response> = withCoroutineScope({
        val driver = userController.find(driverId, DRIVER_ROLE) ?: return@withCoroutineScope createNotFound(createNotFoundMessage(DRIVER_ENTITY, driverId))
        createOk(driverTranslator.translate(driver))
    })

    override fun listDrivers(driverCardId: String?, archived: Boolean?, first: Int?, max: Int?): Uni<Response> = withCoroutineScope({
        if (loggedUserId == null && requestApiKey == null) return@withCoroutineScope createUnauthorized(UNAUTHORIZED)
        if (requestApiKey != null && requestApiKey != apiKey) return@withCoroutineScope createForbidden(INVALID_API_KEY)
        if (loggedUserId != null && !hasRealmRole(MANAGER_ROLE)) return@withCoroutineScope createForbidden(FORBIDDEN)

        val ( drivers, count ) = userController.listDrivers(driverCardId, archived, first, max)
        createOk(drivers.map { driverTranslator.translate(it) }, count.toLong())
    })

}