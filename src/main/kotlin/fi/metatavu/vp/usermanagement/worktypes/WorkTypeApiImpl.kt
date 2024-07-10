package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkType
import fi.metatavu.vp.api.model.WorkTypeCategory
import fi.metatavu.vp.api.spec.WorkTypesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.timeentries.TimeEntryController
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
import java.util.*

@RequestScoped
@WithSession
@OptIn(ExperimentalCoroutinesApi::class)
class WorkTypeApiImpl : WorkTypesApi, AbstractApi() {

    @Inject
    lateinit var vertx: io.vertx.core.Vertx

    @Inject
    lateinit var workTypeController: WorkTypeController

    @Inject
    lateinit var workTypeTranslator: WorkTypeTranslator

    @Inject
    lateinit var timeEntryController: TimeEntryController

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun listWorkTypes(category: WorkTypeCategory?): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val (workTypes, count) = workTypeController.list(category)
        createOk(workTypeTranslator.translate(workTypes), count)
    }.asUni()

    @WithTransaction
    @RolesAllowed(MANAGER_ROLE)
    override fun createWorkType(workType: WorkType): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val duplicates = workTypeController.find(workType.name, workType.category)
        if (duplicates != null) {
            return@async createConflict(
                "Work type with name ${workType.name} and category ${workType.category} already exists"
            )
        }
        val createdWorkType = workTypeController.create(workType = workType)
        createCreated(workTypeTranslator.translate(createdWorkType))
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun findWorkType(workTypeId: UUID): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val found = workTypeController.find(workTypeId) ?: return@async createNotFound(
            createNotFoundMessage(
                WORK_TYPE,
                workTypeId
            )
        )
        createOk(workTypeTranslator.translate(found))
    }.asUni()

    @WithTransaction
    @RolesAllowed(MANAGER_ROLE)
    override fun deleteWorkType(workTypeId: UUID): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val found = workTypeController.find(workTypeId) ?: return@async createNotFound(
            createNotFoundMessage(
                WORK_TYPE,
                workTypeId
            )
        )
        val timeEntries = timeEntryController.listByWorkType(workType = found)
        if (timeEntries > 0) {
            return@async createConflict(
                "Work type with id $workTypeId is in use in time entries"
            )
        }
        workTypeController.delete(found)
        createNoContent()
    }.asUni()
}