package fi.metatavu.vp.usermanagement.workevents

import fi.metatavu.vp.usermanagement.model.WorkEvent
import fi.metatavu.vp.usermanagement.spec.WorkEventsApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.users.UserController
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
 * Work Event API implementation
 */
@RequestScoped
@WithSession
@Suppress("unused")
class WorkEventApiImpl : WorkEventsApi, AbstractApi() {

    @Inject
    lateinit var userController: UserController

    @Inject
    lateinit var workEventController: WorkEventController

    @Inject
    lateinit var workEventTranslator: WorkEventTranslator


    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    @WithTransaction
    override fun createEmployeeWorkEvent(employeeId: UUID, workEvent: WorkEvent): Uni<Response> =
        withCoroutineScope {
            if (employeeId != workEvent.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }

            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val employee = userController.find(employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = EMPLOYEE_ENTITY,
                    id = employeeId
                )

            val created = workEventController.create(
                employee = employee,
                startTime = workEvent.startTime,
                workEventType = workEvent.workEventType
            )

            createCreated(workEventTranslator.translate(created))
        }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployeeWorkEvent(employeeId: UUID, workEventId: UUID): Uni<Response> =
        withCoroutineScope {
            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val foundTimeEntry =
                workEventController.find(workEventId, employeeId)
                    ?: return@withCoroutineScope createNotFoundWithMessage(
                        entity = TIME_ENTRY,
                        id = workEventId
                    )
            workEventController.delete(foundTimeEntry)
            createNoContent()
        }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun findEmployeeWorkEvent(employeeId: UUID, workEventId: UUID): Uni<Response> =
        withCoroutineScope {
            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            val timeEntry = workEventController.find(workEventId, employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = TIME_ENTRY,
                    id = workEventId
                )

            createOk(workEventTranslator.translate(timeEntry))
        }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun listEmployeeWorkEvents(employeeId: UUID, start: OffsetDateTime?, first: Int, max: Int): Uni<Response> = withCoroutineScope {
        if (!isManager() && loggedUserId != employeeId) {
            return@withCoroutineScope createForbidden(FORBIDDEN)
        }

        val (timeEntries, count) = workEventController.list(
            employeeId = employeeId,
            start = start,
            first = first,
            max = max
        )
        createOk(workEventTranslator.translate(timeEntries), count)
    }

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE)
    @WithTransaction
    override fun updateEmployeeWorkEvent(employeeId: UUID, workEventId: UUID, workEvent: WorkEvent): Uni<Response> =
        withCoroutineScope {
            if (employeeId != workEvent.employeeId) {
                return@withCoroutineScope createBadRequest("Employee id in path and in body do not match")
            }

            if (!isManager() && loggedUserId != employeeId) {
                return@withCoroutineScope createForbidden(FORBIDDEN)
            }

            userController.find(employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = EMPLOYEE_ENTITY,
                    id = employeeId
                )

            val foundWorkEvent = workEventController.find(workEventId, employeeId)
                ?: return@withCoroutineScope createNotFoundWithMessage(
                    entity = TIME_ENTRY,
                    id = workEventId
                )

            val updatedWorkEvent = workEventController.update(foundWorkEvent, workEvent)
            createOk(workEventTranslator.translate(updatedWorkEvent))
        }
}