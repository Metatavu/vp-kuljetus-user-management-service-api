package fi.metatavu.vp.usermanagement.workshifthours

import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
import fi.metatavu.vp.usermanagement.model.WorkShiftHours
import fi.metatavu.vp.usermanagement.model.WorkType
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.WorkShiftHoursApi
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changes.WorkShiftChangeController
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.ChangeSetExistsWithOtherWorkShiftException
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetController
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
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var workShiftHoursTranslator: WorkShiftHoursTranslator

    @Inject
    lateinit var workShiftChangeSetController: WorkShiftChangeSetController

    @Inject
    lateinit var workShiftChangeController: WorkShiftChangeController

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun listWorkShiftHours(
        employeeId: UUID?,
        employeeWorkShiftId: UUID?,
        workType: WorkType?,
        employeeWorkShiftStartedAfter: OffsetDateTime?,
        employeeWorkShiftStartedBefore: OffsetDateTime?
    ): Uni<Response> = withCoroutineScope {
        if (employeeWorkShiftId != null && (employeeWorkShiftStartedBefore != null || employeeWorkShiftStartedAfter != null)) {
            return@withCoroutineScope createBadRequest("employeeWorkShiftStartedBefore and employeeWorkShiftStartedAfter cannot be used with employeeWorkShiftId")
        }

        val workShiftFilter = if (employeeWorkShiftId != null) {
            workShiftController.findEmployeeWorkShift(employeeId, employeeWorkShiftId)
              ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT, employeeWorkShiftId)
        } else null

        val (workShiftHours, count) = workShiftHoursController.listWorkShiftHours(employeeId, workShiftFilter, workType, employeeWorkShiftStartedAfter, employeeWorkShiftStartedBefore)

        createOk(workShiftHoursTranslator.translate(workShiftHours), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteWorkShiftHours(workShiftHoursId: UUID): Uni<Response> = withCoroutineScope{
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@withCoroutineScope createForbidden("Deleting work shift hours is disabled")
        }
        val workShiftHours = workShiftHoursController.findWorkShiftHours(workShiftHoursId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT_HOURS, workShiftHoursId)
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
    override fun updateWorkShiftHours(workShiftHoursId: UUID, workShiftChangeSetId: UUID, workShiftHours: WorkShiftHours): Uni<Response> = withCoroutineScope {
        val existingWorkShiftHours = workShiftHoursController.findWorkShiftHours(workShiftHoursId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT_HOURS, workShiftHoursId)

        if (workShiftHours.employeeWorkShiftId != existingWorkShiftHours.workShift.id
            || workShiftHours.workType != existingWorkShiftHours.workType
            || workShiftHours.employeeId != existingWorkShiftHours.workShift.employeeId
        ) {
            return@withCoroutineScope createBadRequest("employeeId, employeeWorkShiftId, and workType cannot be updated")
        }

        if (existingWorkShiftHours.workShift.approved) {
            return@withCoroutineScope createBadRequest("Work shift hours cannot be updated if the related work shift is approved")
        }

        try {
            val changeSet = workShiftChangeSetController.createOrReturnExisting(workShiftChangeSetId, existingWorkShiftHours.workShift, loggedUserId!!)
            workShiftChangeController.create(
                reason = WorkShiftChangeReason.WORKSHIFTHOURS_UPDATED_ACTUALHOURS.toString(),
                creatorId = loggedUserId!!,
                workShift = existingWorkShiftHours.workShift,
                workShiftHours = existingWorkShiftHours,
                workEvent = null,
                oldValue = existingWorkShiftHours.actualHours?.toString(),
                newValue = workShiftHours.actualHours?.toString(),
                workShiftChangeSet = changeSet
            )
        } catch (exception: ChangeSetExistsWithOtherWorkShiftException) {
            return@withCoroutineScope createBadRequest(CHANGESET_ID_RESERVED_BY_OTHER_WORKSHIFT)
        }

        val updatedWorkShiftHours = workShiftHoursController.updateWorkShiftHours(existingWorkShiftHours, workShiftHours.actualHours)

        createOk(workShiftHoursTranslator.translate(updatedWorkShiftHours))
    }
}