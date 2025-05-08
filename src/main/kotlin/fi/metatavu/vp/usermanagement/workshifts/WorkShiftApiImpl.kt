package fi.metatavu.vp.usermanagement.workshifts

import fi.metatavu.vp.usermanagement.model.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.model.WorkShiftChangeReason
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.EmployeeWorkShiftsApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workshifthours.WorkShiftHoursController
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changes.WorkShiftChangeController
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.ChangeSetExistsWithOtherWorkShiftException
import fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets.WorkShiftChangeSetController
import fi.metatavu.vp.usermanagement.workshifts.scheduled.WorkShiftScheduledJobs
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.eventbus.EventBus
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.LocalDate
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

    @Inject
    lateinit var workShiftChangeSetController: WorkShiftChangeSetController

    @Inject
    lateinit var workShiftScheduledJobs: WorkShiftScheduledJobs

    @Inject
    lateinit var eventBus: EventBus

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @ConfigProperty(name = "vp.usermanagement.cron.apiKey")
    lateinit var cronKey: String

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var workShiftChangeController: WorkShiftChangeController

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE)
    override fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: OffsetDateTime?,
        startedBefore: OffsetDateTime?,
        dateAfter: LocalDate?,
        dateBefore: LocalDate?,
        first: Int,
        max: Int
    ): Uni<Response> = withCoroutineScope {
        employeeController.find(employeeId)
          ?: return@withCoroutineScope createNotFoundWithMessage(EMPLOYEE_ENTITY, employeeId)
        if (!isManager() && employeeId != loggedUserId ) {
            return@withCoroutineScope createForbidden("Employees can only list their own work shifts")
        }
        val (employeeWorkShifts, count) = workShiftController.listEmployeeWorkShifts(
            employeeId = employeeId,
            startedAfter = startedAfter,
            startedBefore = startedBefore,
            dateAfter = dateAfter,
            dateBefore = dateBefore,
            first = first,
            max = max
        )
        createOk(workShiftTranslator.translate(employeeWorkShifts), count)
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun createEmployeeWorkShift(employeeId: UUID, workShiftChangeSetId: UUID, employeeWorkShift: EmployeeWorkShift): Uni<Response> {
        return withCoroutineScope {
            if (employeeId != employeeWorkShift.employeeId) {
                return@withCoroutineScope createBadRequest("employeeId in path and body do not match")
            }

            if (loggedUserId == null) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val employee = employeeController.find(employeeId)
              ?: return@withCoroutineScope createNotFoundWithMessage(EMPLOYEE_ENTITY, employeeId)

            val existingChangeSet = workShiftChangeSetController.find(workShiftChangeSetId)

            if (existingChangeSet != null) {
                return@withCoroutineScope createBadRequest(CHANGESET_ID_RESERVED_BY_OTHER_WORKSHIFT)
            }

            val created = workShiftController.create(
                employeeId = UUID.fromString(employee.id),
                date = employeeWorkShift.date,
                absenceType = employeeWorkShift.absence,
                perDiemAllowanceType = employeeWorkShift.perDiemAllowance,
                startedAt = employeeWorkShift.startedAt,
                endedAt = employeeWorkShift.endedAt,
                dayOffWorkAllowance = employeeWorkShift.dayOffWorkAllowance,
                notes = employeeWorkShift.notes
            )

            val changeSet = workShiftChangeSetController.create(workShiftChangeSetId, created, loggedUserId!!)

            workShiftChangeController.create(
                reason = WorkShiftChangeReason.WORKSHIFT_CREATED.toString(),
                creatorId = loggedUserId!!,
                workShiftChangeSet = changeSet,
                workShift = created,
                workShiftHours = null,
                workEvent = null,
                oldValue = null,
                newValue = null
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

    @WithTransaction
    override fun endUnresolvedWorkshifts(): Uni<Response> = withCoroutineScope {
        if (requestCronKey != cronKey) {
            return@withCoroutineScope createUnauthorized(UNAUTHORIZED)
        }

        workShiftScheduledJobs.endUnresolvedWorkshifts()
        createOk()
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
        workShiftChangeSetId: UUID,
        employeeWorkShift: EmployeeWorkShift
    ): Uni<Response> = withCoroutineScope{
        val existingWorkShift = workShiftController.findEmployeeWorkShift(employeeId, workShiftId)
          ?: return@withCoroutineScope createNotFoundWithMessage(WORK_SHIFT, workShiftId)

        if (employeeId != existingWorkShift.employeeId) {
            return@withCoroutineScope createBadRequest("employeeId in path and body do not match")
        }

        if (loggedUserId == null) {
            return@withCoroutineScope createForbidden(FORBIDDEN)
        }

        val workShiftStaysApproved = existingWorkShift.approved && employeeWorkShift.approved
        val workShiftModified = existingWorkShift.absence != employeeWorkShift.absence ||
            existingWorkShift.perDiemAllowance != employeeWorkShift.perDiemAllowance ||
            existingWorkShift.dayOffWorkAllowance != employeeWorkShift.dayOffWorkAllowance ||
            existingWorkShift.notes != employeeWorkShift.notes

        if (workShiftStaysApproved && workShiftModified) {
            return@withCoroutineScope createBadRequest("Approved work shifts cannot be updated")
        }

        try {
            val changeSet = workShiftChangeSetController.createOrReturnExisting(workShiftChangeSetId, existingWorkShift, loggedUserId!!)
            workShiftChangeController.processWorkShiftChanges(
                oldWorkShift = existingWorkShift,
                newWorkShift = employeeWorkShift,
                changeSet = changeSet,
                creatorId = loggedUserId!!
            )
        } catch (exception: ChangeSetExistsWithOtherWorkShiftException) {
            return@withCoroutineScope createBadRequest(CHANGESET_ID_RESERVED_BY_OTHER_WORKSHIFT)
        }

        val updated = workShiftController.updateEmployeeWorkShift(
            existingWorkShift = existingWorkShift,
            updatedWorkShift = employeeWorkShift
        )

        createOk(workShiftTranslator.translate(updated))
    }

    /**
     * Calculates the work hours for {count} shifts which were ended but their hours not yet calculated
     */
    @WithSession
    override fun recalculateWorkHours(count: Int?): Uni<Response> = withCoroutineScope {
        if (requestCronKey != cronKey) {
            return@withCoroutineScope createUnauthorized(UNAUTHORIZED)
        }

        val lastRecord = count ?: 5
        val workShifts = workShiftController.listUnfinishedWorkShifts(0, lastRecord)
        workShifts.forEach {
            eventBus.send(WorkShiftHoursController.RECALCULATE_WORK_SHIFT_HOURS, it.id)
        }
        logger.info("Created events for recalculating work shift hours")
        createOk()
    }
}