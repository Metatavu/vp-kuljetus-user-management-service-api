package fi.metatavu.vp.usermanagement.timeentries

import fi.metatavu.vp.api.model.TimeEntry
import fi.metatavu.vp.api.spec.TimeEntriesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.worktypes.WorkTypeController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.util.*

/**
 * Time entry API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class TimeEntryApiImpl : TimeEntriesApi, AbstractApi() {

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
    ): Uni<Response> = withCoroutineScope({
        val (timeEntries, count) = timeEntryController.list(employeeId, start, end, first, max)
        createOk(timeEntryTranslator.translate(timeEntries), count)
    })

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    @WithTransaction
    override fun createEmployeeTimeEntry(employeeId: UUID, timeEntry: TimeEntry): Uni<Response> =
        withCoroutineScope({
            if (employeeId != timeEntry.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }
            if (timeEntry.endTime != null && timeEntry.startTime >= timeEntry.endTime) {
                return@withCoroutineScope createBadRequest("End time must be after start time")
            }

            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val employee = userController.find(employeeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(
                    EMPLOYEE_ENTITY,
                    employeeId
                )
            )
            val workType = workTypeController.find(timeEntry.workTypeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(
                    WORK_TYPE,
                    timeEntry.workTypeId
                )
            )
            timeEntryController.findIncompleteEntries(employee, timeEntry)?.let { return@withCoroutineScope createBadRequest("User already has unfinished time entry") }
            timeEntryController.findOverlappingEntry(employee, timeEntry)?.let { return@withCoroutineScope createBadRequest("Time entry overlaps with another entry") }

            val created = timeEntryController.create(employee, workType, timeEntry)
            createCreated(timeEntryTranslator.translate(created))
        })

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun findEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID): Uni<Response> =
        withCoroutineScope({
            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val timeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(
                    TIME_ENTRY,
                    timeEntryId
                )
            )
            createOk(timeEntryTranslator.translate(timeEntry))
        })

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun updateEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID, timeEntry: TimeEntry): Uni<Response> =
        withCoroutineScope({
            if (employeeId != timeEntry.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }
            if (timeEntry.endTime != null && timeEntry.startTime >= timeEntry.endTime) {
                return@withCoroutineScope createBadRequest("End time must be after start time")
            }

            val employee = userController.find(employeeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(EMPLOYEE_ENTITY, employeeId)
            )

            val foundTimeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(TIME_ENTRY, timeEntryId)
            )

            val newWorkType = if (foundTimeEntry.workType.id == timeEntry.workTypeId) {
                foundTimeEntry.workType
            } else {
                workTypeController.find(timeEntry.workTypeId) ?: return@withCoroutineScope createNotFound(
                    createNotFoundMessage(WORK_TYPE, timeEntry.workTypeId)
                )
            }

            if (foundTimeEntry.endTime != null && timeEntry.endTime == null) {
                return@withCoroutineScope createBadRequest("End time cannot be set to null")
            }
            timeEntryController.findOverlappingEntry(employee, timeEntry)?.let {
                if (it.id != timeEntryId) {
                    return@withCoroutineScope createBadRequest("Time entry overlaps with another entry")
                }
            }

            val updatedTimeEntry = timeEntryController.update(foundTimeEntry, newWorkType, timeEntry)
            createOk(timeEntryTranslator.translate(updatedTimeEntry))
        })

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployeeTimeEntry(employeeId: UUID, timeEntryId: UUID): Uni<Response> =
        withCoroutineScope({
            val foundTimeEntry = timeEntryController.find(timeEntryId, employeeId) ?: return@withCoroutineScope createNotFound(
                createNotFoundMessage(
                    TIME_ENTRY,
                    timeEntryId
                )
            )
            timeEntryController.delete(foundTimeEntry)
            createNoContent()
        })
}