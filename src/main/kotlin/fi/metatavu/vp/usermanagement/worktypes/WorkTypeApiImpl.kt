package fi.metatavu.vp.usermanagement.worktypes

import fi.metatavu.vp.api.model.WorkType
import fi.metatavu.vp.api.model.WorkTypeCategory
import fi.metatavu.vp.api.spec.WorkTypesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.timeentries.TimeEntryController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

@RequestScoped
@WithSession
@Suppress("unused")
class WorkTypeApiImpl : WorkTypesApi, AbstractApi() {

    @Inject
    lateinit var workTypeController: WorkTypeController

    @Inject
    lateinit var workTypeTranslator: WorkTypeTranslator

    @Inject
    lateinit var timeEntryController: TimeEntryController

    @RolesAllowed(MANAGER_ROLE, EMPLOYEE_ROLE, DRIVER_ROLE)
    override fun listWorkTypes(category: WorkTypeCategory?): Uni<Response> = withCoroutineScope({
        val (workTypes, count) = workTypeController.list(category)
        createOk(workTypeTranslator.translate(workTypes), count)
    })

    @WithTransaction
    @RolesAllowed(MANAGER_ROLE)
    override fun createWorkType(workType: WorkType): Uni<Response> = withCoroutineScope({
        val duplicates = workTypeController.find(workType.name, workType.category)
        if (duplicates != null) {
            return@withCoroutineScope createConflict(
                "Work type with name ${workType.name} and category ${workType.category} already exists"
            )
        }
        val createdWorkType = workTypeController.create(workType = workType)
        createCreated(workTypeTranslator.translate(createdWorkType))
    })

    @RolesAllowed(MANAGER_ROLE)
    override fun findWorkType(workTypeId: UUID): Uni<Response> = withCoroutineScope({
        val found = workTypeController.find(workTypeId) ?: return@withCoroutineScope createNotFound(
            createNotFoundMessage(
                WORK_TYPE,
                workTypeId
            )
        )
        createOk(workTypeTranslator.translate(found))
    })

    @WithTransaction
    @RolesAllowed(MANAGER_ROLE)
    override fun deleteWorkType(workTypeId: UUID): Uni<Response> = withCoroutineScope({
        val found = workTypeController.find(workTypeId) ?: return@withCoroutineScope createNotFound(
            createNotFoundMessage(
                WORK_TYPE,
                workTypeId
            )
        )
        val timeEntries = timeEntryController.listByWorkType(workType = found)
        if (timeEntries > 0) {
            return@withCoroutineScope createConflict(
                "Work type with id $workTypeId is in use in time entries"
            )
        }
        workTypeController.delete(found)
        createNoContent()
    })

}