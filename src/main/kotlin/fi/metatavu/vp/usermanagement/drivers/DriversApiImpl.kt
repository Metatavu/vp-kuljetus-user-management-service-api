package fi.metatavu.vp.usermanagement.drivers

import fi.metatavu.vp.api.spec.DriversApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
@RequestScoped
class DriversApiImpl: DriversApi, AbstractApi() {

    @Inject
    lateinit var driverController: DriverController

    @Inject
    lateinit var driverTranslator: DriverTranslator

    @Inject
    lateinit var vertx: Vertx

    @RolesAllowed(DRIVER_ROLE, MANAGER_ROLE)
    override fun findDriver(driverId: UUID): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val driver = driverController.findDriver(driverId) ?: return@async createNotFound(createNotFoundMessage(DRIVER, driverId))
        createOk(driverTranslator.translate(driver))
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun listDrivers(first: Int?, max: Int?): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val ( drivers, count ) = driverController.listDrivers(first, max)
        createOk(drivers.map { driverTranslator.translate(it) }, count.toLong())
    }.asUni()

}