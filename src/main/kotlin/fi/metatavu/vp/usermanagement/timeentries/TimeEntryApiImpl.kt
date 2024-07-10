package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.api.spec.TimeEntriesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.worktypes.WorkTypeController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.time.OffsetDateTime
import java.util.*

/**
 * Time entry API implementation
 */
@RequestScoped
@WithSession
@OptIn(ExperimentalCoroutinesApi::class)
class TimeEntryApiImpl : TimeEntriesApi, AbstractApi() {

    @Inject
    lateinit var vertx: io.vertx.core.Vertx

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var workTypeController: WorkTypeController

    @Inject
    lateinit var timeEntryController: TimeEntryController

    @Inject
    lateinit var timeEntryTranslator: TimeEntryTranslator

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun listEmployeeTimeEntries(
        employeeId: UUID,
        start: OffsetDateTime?,
        end: OffsetDateTime?,
        first: Int,
        max: Int
    ): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val (timeEntries, count) = timeEntryController.list(employeeId, start, end, first, max)
        createOk(timeEntryTranslator.translate(timeEntries), count)
    }.asUni()

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    @WithTransaction
    override fun createEmployeeTimeEntry(employeeId: UUID, timeEntry: TimeEntry): Uni<Response> =
        CoroutineScope(vertx.dispatcher()).async {
            if (employeeId != timeEntry.employeeId) {
                return@async createBadRequest("Employee id in path and in body do not match")
            }
            if (timeEntry.endTime != null && timeEntry.startTime >= timeEntry.endTime) {
                return@async createBadRequest("End time must be after start time")
            }

            //todo can not managers create records for others?
            if (!isManager() && loggedUserId != employeeId) {
                return@async createForbidden(FORBIDDEN)
            }

            val employee = userController.find(employeeId) ?: return@async createNotFound(
                createNotFoundMessage(
                    EMPLOYEE_ENTITY,
                    employeeId
                )
            )
            val workType = workTypeController.find(timeEntry.workTypeId) ?: return@async createNotFound(
                createNotFoundMessage(
                    WORK_TYPE,
                    timeEntry.workTypeId
                )
            )
            timeEntryController.validateIncompleteEntries(employee, timeEntry)?.let { return@async createBadRequest(it) }
            timeEntryController.validateOverlappingEntries(employee, timeEntry)?.let { return@async createBadRequest(it) }

            val created = timeEntryController.create(employee, workType, timeEntry)
            createCreated(timeEntryTranslator.translate(created))
        }.asUni()

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun findEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID): Uni<Response> =
        CoroutineScope(vertx.dispatcher()).async {
            //todo can not managers see records of others?
            val timeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@async createNotFound(
                createNotFoundMessage(
                    TIME_ENTRY,
                    timeEntryId
                )
            )
            createOk(timeEntryTranslator.translate(timeEntry))
        }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID, timeEntry: TimeEntry): Uni<Response> =
        CoroutineScope(vertx.dispatcher()).async {
            if (employeeId != timeEntry.employeeId) {
                return@async createBadRequest("Employee id in path and in body do not match")
            }
            if (timeEntry.endTime != null && timeEntry.startTime >= timeEntry.endTime) {
                return@async createBadRequest("End time must be after start time")
            }

            val employee = userController.find(employeeId) ?: return@async createNotFound(
                createNotFoundMessage(EMPLOYEE_ENTITY, employeeId)
            )

            val foundTimeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@async createNotFound(
                createNotFoundMessage(TIME_ENTRY, timeEntryId)
            )

            val newWorkType = if (foundTimeEntry.workType.id == timeEntry.workTypeId) {
                foundTimeEntry.workType
            } else {
                workTypeController.find(timeEntry.workTypeId) ?: return@async createNotFound(
                    createNotFoundMessage(WORK_TYPE, timeEntry.workTypeId)
                )
            }

            //todo do we want to set end time to null ?
            if (foundTimeEntry.endTime != null && timeEntry.endTime == null) {
                return@async createBadRequest("End time cannot be set to null")
            }

            timeEntryController.validateOverlappingEntries(employee, timeEntry)?.let { return@async createBadRequest(it) }
            val updatedTimeEntry = timeEntryController.update(foundTimeEntry, newWorkType, timeEntry)
            createOk(timeEntryTranslator.translate(updatedTimeEntry))
        }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID): Uni<Response> =
        CoroutineScope(vertx.dispatcher()).async {
            val foundTimeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@async createNotFound(
                createNotFoundMessage(
                    TIME_ENTRY,
                    timeEntryId
                )
            )
            timeEntryController.delete(foundTimeEntry)
            createNoContent()
        }.asUni()
}