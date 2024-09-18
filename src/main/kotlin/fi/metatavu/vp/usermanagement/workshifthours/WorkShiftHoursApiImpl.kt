package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkEventType
import fi.metatavu.vp.usermanagement.model.WorkShiftHours
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.WorkShiftHoursApi
import fi.metatavu.vp.usermanagement.workshifts.EmployeeWorkShiftController
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
 * Work shift hours API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class WorkShiftHoursApiImpl: WorkShiftHoursApi, AbstractApi() {

    @Inject
    lateinit var workShiftHoursController: WorkShiftHoursController

    @Inject
    lateinit var workShiftController: EmployeeWorkShiftController

    @Inject
    lateinit var workShiftHoursTranslator: WorkShiftHoursTranslator

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun listWorkShiftHours(
        employeeId: UUID?,
        employeeWorkShiftId: UUID?,
        workType: WorkEventType?,
        employeeWorkShiftStartedAfter: OffsetDateTime?,
        employeeWorkShiftStartedBefore: OffsetDateTime?
    ): Uni<Response> = withCoroutineScope {
        if (employeeWorkShiftId != null && (employeeWorkShiftStartedBefore != null || employeeWorkShiftStartedAfter != null)) {
            return@withCoroutineScope createBadRequest("employeeWorkShiftStartedBefore and employeeWorkShiftStartedAfter cannot be used with employeeWorkShiftId")
        }

        val workShiftFilter = if (employeeWorkShiftId != null) {
            workShiftController.findEmployeeWorkShift(employeeId, employeeWorkShiftId) ?: return@withCoroutineScope createNotFoundWithMessage(
                WORK_SHIFT, employeeWorkShiftId)
        } else null

        val (workShiftHours, count) = workShiftHoursController.listWorkShiftHours(employeeId, workShiftFilter, workType, employeeWorkShiftStartedAfter, employeeWorkShiftStartedBefore)

        createOk(workShiftHoursTranslator.translate(workShiftHours), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun createWorkShiftHours(workShiftHours: WorkShiftHours): Uni<Response> = withCoroutineScope {
        if (workShiftHours.actualHours == null) {
            return@withCoroutineScope createBadRequest("calculatedHours is required")
        }

        val workShift = workShiftController.findEmployeeWorkShift(shiftId = workShiftHours.employeeWorkShiftId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT, workShiftHours.employeeWorkShiftId
        )

        val (duplicates, _) = workShiftHoursController.listWorkShiftHours(
            workShiftFilter = workShift,
            workType = workShiftHours.workEventType,
        )

        if (duplicates.isNotEmpty()) {
            val updated = workShiftHoursController.updateWorkShiftHours(duplicates.first(), workShiftHours)
            createOk(workShiftHoursTranslator.translate(updated))
        }

        val created = workShiftHoursController.createWorkShiftHours(
            workShiftEntity = workShift,
            workEventType = workShiftHours.workEventType,
            actualHours = workShiftHours.actualHours
        )

        createOk(workShiftHoursTranslator.translate(created))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteWorkShiftHours(workShiftHoursId: UUID): Uni<Response> = withCoroutineScope{
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@withCoroutineScope createForbidden("Deleting work shift hours is disabled")
        }
        val workShiftHours = workShiftHoursController.findWorkShiftHours(workShiftHoursId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT_HOURS, workShiftHoursId)
        workShiftHoursController.deleteWorkShiftHours(workShiftHours)
        createNoContent()
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun findWorkShiftHours(workShiftHoursId: UUID): Uni<Response> = withCoroutineScope {
        val workShiftHours = workShiftHoursController.findWorkShiftHours(workShiftHoursId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT_HOURS, workShiftHoursId)
        createOk(workShiftHoursTranslator.translate(workShiftHours))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateWorkShiftHours(workShiftHoursId: UUID, workShiftHours: WorkShiftHours): Uni<Response> = withCoroutineScope {
        val existingWorkShiftHours = workShiftHoursController.findWorkShiftHours(workShiftHoursId) ?: return@withCoroutineScope createNotFoundWithMessage(
            WORK_SHIFT_HOURS, workShiftHoursId)

        if (workShiftHours.employeeWorkShiftId != existingWorkShiftHours.workShift.id ||
            workShiftHours.workEventType != existingWorkShiftHours.workEventType) {
            return@withCoroutineScope createBadRequest("employeeId, employeeWorkShiftId, and workType cannot be updated")
        }

        if (existingWorkShiftHours.workShift.approved) {
            return@withCoroutineScope createBadRequest("Work shift hours cannot be updated if the related work shift is approved")
        }

        val updatedWorkShiftHours = workShiftHoursController.updateWorkShiftHours(existingWorkShiftHours, workShiftHours)
        createOk(workShiftHoursTranslator.translate(updatedWorkShiftHours))
    }
}