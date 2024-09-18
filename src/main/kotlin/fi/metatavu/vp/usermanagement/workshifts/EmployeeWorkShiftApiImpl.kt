package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.EmployeeWorkShiftsApi
import fi.metatavu.vp.usermanagement.users.UserController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.OffsetDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Employee work shift API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class EmployeeWorkShiftApiImpl: EmployeeWorkShiftsApi, AbstractApi() {

    @Inject
    lateinit var employeeWorkShiftController: EmployeeWorkShiftController

    @Inject
    lateinit var employeeWorkShiftTranslator: EmployeeWorkShiftTranslator

    @Inject
    lateinit var employeeController: UserController

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @RolesAllowed(MANAGER_ROLE)
    override fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        first: Int,
        max: Int
    ): Uni<Response> = withCoroutineScope {
        val employee = employeeController.find(employeeId, EMPLOYEE_ROLE) ?: return@withCoroutineScope createNotFoundWithMessage(
            EMPLOYEE_ENTITY, employeeId)
        val (employeeWorkShifts, count) = employeeWorkShiftController.listEmployeeWorkShifts(employee, startedAfter, startedBefore, first, max)
        createOk(employeeWorkShiftTranslator.translate(employeeWorkShifts), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteWorkShift(employeeId: UUID, workShiftId: UUID): Uni<Response> = withCoroutineScope {
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@withCoroutineScope createForbidden("Deleting work shift hours is disabled")
        }
        val employeeWorkShift = employeeWorkShiftController.findEmployeeWorkShift(employeeId, workShiftId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT, workShiftId)
        employeeWorkShiftController.deleteEmployeeWorkShift(employeeWorkShift)
        createNoContent()
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun findEmployeeWorkShift(employeeId: UUID, workShiftId: UUID): Uni<Response> = withCoroutineScope{
        val employeeWorkShift = employeeWorkShiftController.findEmployeeWorkShift(employeeId, workShiftId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT, workShiftId)
        createOk(employeeWorkShiftTranslator.translate(employeeWorkShift))
    }


    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateEmployeeWorkShift(
        employeeId: UUID,
        workShiftId: UUID,
        employeeWorkShift: EmployeeWorkShift
    ): Uni<Response> = withCoroutineScope{
        val foundShift = employeeWorkShiftController.findEmployeeWorkShift(employeeId, workShiftId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT, workShiftId)

        val updated = employeeWorkShiftController.updateEmployeeWorkShift(foundShift, employeeWorkShift)
        createOk(employeeWorkShiftTranslator.translate(updated))
    }
}