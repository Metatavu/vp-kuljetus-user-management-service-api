package fi.metatavu.vp.usermanagement.workshifts.changelogs.changesets

import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.spec.WorkShiftChangeSetsApi
import fi.metatavu.vp.usermanagement.users.UserController
import fi.metatavu.vp.usermanagement.workshifts.WorkShiftController
import io.quarkus.hibernate.reactive.panache.common.WithSession
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.time.LocalDate
import java.util.*

@RequestScoped
@WithSession
@Suppress("unused")
class WorkShiftChangeSetsApiImpl: WorkShiftChangeSetsApi, AbstractApi() {
    @Inject
    lateinit var workShiftChangeSetController: WorkShiftChangeSetController

    @Inject
    lateinit var workShiftChangeSetTranslator: WorkShiftChangeSetTranslator

    @Inject
    lateinit var workShiftController: WorkShiftController

    @Inject
    lateinit var employeeController: UserController

    @RolesAllowed(MANAGER_ROLE)
    override fun listWorkShiftChangeSets(
        employeeId: UUID,
        workShiftDateAfter: LocalDate?,
        workShiftDateBefore: LocalDate?
    ): Uni<Response> = withCoroutineScope {
        employeeController.find(employeeId)
            ?: return@withCoroutineScope createNotFoundWithMessage(EMPLOYEE_ENTITY, employeeId)

        val workShifts = workShiftController.listEmployeeWorkShifts(employeeId = employeeId, dateAfter = workShiftDateAfter, dateBefore = workShiftDateBefore, startedBefore = null, startedAfter = null).first

        val changeSets = arrayListOf<WorkShiftChangeSetEntity>()

        workShifts.forEach {
            workShiftChangeSetController.listByWorkShift(it).forEach { changeSet ->
                changeSets.add(changeSet)
            }
        }

        createOk(changeSets.map { changeSet ->
            workShiftChangeSetTranslator.translate(changeSet)
        })
    }
}