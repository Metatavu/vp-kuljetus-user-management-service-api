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
class WorkShiftApiImpl: EmployeeWorkShiftsApi, AbstractApi() {

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var workShiftTranslator: WorkShiftTranslator

    @Inject
    lateinit var employeeController: UserController

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE)
    override fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        first: Int,
        max: Int
    ): Uni<Response> = withCoroutineScope {
        val employee = employeeController.find(employeeId)
          ?: return@withCoroutineScope createNotFoundWithMessage(EMPLOYEE_ENTITY, employeeId)
        if (!isManager() && employeeId != loggedUserId ) {
            return@withCoroutineScope createForbidden("Employees can only list their own work shifts")
        }
        val (employeeWorkShifts, count) = workShiftController.listEmployeeWorkShifts(employee, startedAfter, startedBefore, first, max)
        createOk(workShiftTranslator.translate(employeeWorkShifts), count)
    }

    override fun recalculateWorkHours(count: Int?): Uni<Response> = withCoroutineScope {
        createOk()
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun createEmployeeWorkShift(employeeId: UUID, employeeWorkShift: EmployeeWorkShift): Uni<Response> {
        return withCoroutineScope {
            if (employeeId != employeeWorkShift.employeeId) {
                return@withCoroutineScope createBadRequest("employeeId in path and body do not match")
            }

            val employee = employeeController.find(employeeId)
              ?: return@withCoroutineScope createNotFoundWithMessage(EMPLOYEE_ENTITY, employeeId)

            val created = workShiftController.create(
                employeeId = UUID.fromString(employee.id),
                date = employeeWorkShift.date,
                absenceType = employeeWorkShift.absence,
                perDiemAllowanceType = employeeWorkShift.perDiemAllowance,
                startedAt = employeeWorkShift.startedAt,
                endedAt = employeeWorkShift.endedAt,
                dayOffWorkAllowance = employeeWorkShift.dayOffWorkAllowance
            )
            createOk(workShiftTranslator.translate(created))
        }
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployeeWorkShift(employeeId: UUID, workShiftId: UUID): Uni<Response> = withCoroutineScope {
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@withCoroutineScope createForbidden("Deleting work shift hours is disabled")
        }
        val employeeWorkShift = workShiftController.findEmployeeWorkShift(employeeId, workShiftId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT, workShiftId)
        workShiftController.deleteEmployeeWorkShift(employeeWorkShift)
        createNoContent()
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun findEmployeeWorkShift(employeeId: UUID, workShiftId: UUID): Uni<Response> = withCoroutineScope{
        val employeeWorkShift = workShiftController.findEmployeeWorkShift(employeeId, workShiftId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT, workShiftId)
        createOk(workShiftTranslator.translate(employeeWorkShift))
    }


    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateEmployeeWorkShift(
        employeeId: UUID,
        workShiftId: UUID,
        employeeWorkShift: EmployeeWorkShift
    ): Uni<Response> = withCoroutineScope{
        val existingWorkShift = workShiftController.findEmployeeWorkShift(employeeId, workShiftId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT, workShiftId)

        if (employeeId != existingWorkShift.employeeId) {
            return@withCoroutineScope createBadRequest("employeeId in path and body do not match")
        }

        val workShiftStaysApproved = existingWorkShift.approved && employeeWorkShift.approved
        val workShiftModified = existingWorkShift.absence != employeeWorkShift.absence ||
            existingWorkShift.perDiemAllowance != employeeWorkShift.perDiemAllowance ||
            existingWorkShift.dayOffWorkAllowance != employeeWorkShift.dayOffWorkAllowance ||
            existingWorkShift.notes != employeeWorkShift.notes

        if (workShiftStaysApproved && workShiftModified) {
            return@withCoroutineScope createBadRequest("Approved work shifts cannot be updated")
        }

        val updated = workShiftController.updateEmployeeWorkShift(
            existingWorkShift = existingWorkShift,
            updatedWorkShift = employeeWorkShift
        )

        createOk(workShiftTranslator.translate(updated))
    }
}