package fi.metatavu.vp.usermanagement.employees

import fi.metatavu.vp.api.model.Employee
import fi.metatavu.vp.api.model.EmployeeType
import fi.metatavu.vp.api.model.Office
import fi.metatavu.vp.api.model.SalaryGroup
import fi.metatavu.vp.api.spec.EmployeesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.timeentries.TimeEntryController
import fi.metatavu.vp.usermanagement.users.UserController
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.*
import kotlin.jvm.optionals.getOrNull

@RequestScoped
@Suppress("unused")
class EmployeeApiImpl: EmployeesApi, AbstractApi() {

    @Inject
    lateinit var usersController: UserController

    @Inject
    lateinit var employeeTranslator: EmployeeTranslator

    @Inject
    lateinit var timeEntryController: TimeEntryController

    @ConfigProperty(name = "env")
    lateinit var env: Optional<String>

    @RolesAllowed(MANAGER_ROLE)
    override fun listEmployees(
        search: String?,
        salaryGroup: SalaryGroup?,
        type: EmployeeType?,
        office: Office?,
        archived: Boolean?,
        first: Int,
        max: Int
    ): Uni<Response> = withCoroutineScope {
        val (employees, count) = usersController.listEmployees(search, salaryGroup, type, office, archived, first, max)
        val translatedEmployees = employees.mapNotNull { runCatching { employeeTranslator.translate(it) }.getOrNull() }
        createOk(translatedEmployees, count.toLong())
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun createEmployee(employee: Employee): Uni<Response> = withCoroutineScope {
        usersController.findEmployeeNumberDuplicate(employee.employeeNumber).let { if (it.isNotEmpty()) return@withCoroutineScope createBadRequest("Employee number already exists")}
        val created = usersController.createEmployee(employee) ?: return@withCoroutineScope createInternalServerError("Failed creating a user")
        createCreated(employeeTranslator.translate(created))
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun findEmployee(employeeId: UUID): Uni<Response> = withCoroutineScope {
        val employee = usersController.find(employeeId, EMPLOYEE_ROLE) ?: return@withCoroutineScope createNotFound("Employee not found")
        createOk(employeeTranslator.translate(employee))
    }

    @RolesAllowed(MANAGER_ROLE)
    override fun updateEmployee(employeeId: UUID, employee: Employee): Uni<Response> = withCoroutineScope {
        usersController.findEmployeeNumberDuplicate(employee.employeeNumber).let {
            if (it.isNotEmpty() && it.none { d -> d.id == employeeId.toString() }) return@withCoroutineScope createBadRequest("Employee number already exists")
        }
        val found = usersController.find(employeeId, EMPLOYEE_ROLE) ?: return@withCoroutineScope createNotFound("Employee not found")
        if (found.enabled == false && employee.archivedAt != null) return@withCoroutineScope createBadRequest("Cannot update archived employee")
        val updated = usersController.updateEmployee(found, employee) ?: return@withCoroutineScope createInternalServerError("Failed updating a user")
        createOk(employeeTranslator.translate(updated))
    }

    @RolesAllowed(MANAGER_ROLE)
    @WithTransaction
    override fun deleteEmployee(employeeId: UUID): Uni<Response> = withCoroutineScope {
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@withCoroutineScope createForbidden("Deleting employees is disabled")
        }

        timeEntryController.list(employeeId = employeeId, start = null, end = null).first.forEach {
            timeEntryController.delete(it)
        }

        usersController.deleteEmployee(employeeId)
        createNoContent()
    }

}