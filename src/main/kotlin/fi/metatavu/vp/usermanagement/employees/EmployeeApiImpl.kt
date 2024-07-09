package fi.metatavu.vp.usermanagement.employees

import fi.metatavu.vp.api.model.Employee
import fi.metatavu.vp.api.model.EmployeeType
import fi.metatavu.vp.api.model.Office
import fi.metatavu.vp.api.model.SalaryGroup
import fi.metatavu.vp.api.spec.EmployeesApi
import fi.metatavu.vp.usermanagement.rest.AbstractApi
import fi.metatavu.vp.usermanagement.users.UserController
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.*
import kotlin.jvm.optionals.getOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@RequestScoped
class EmployeeApiImpl: EmployeesApi, AbstractApi() {

    @Inject
    lateinit var usersController: UserController

    @Inject
    lateinit var employeeTranslator: EmployeeTranslator

    @Inject
    lateinit var vertx: Vertx

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
    ): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val (employees, count) = usersController.listEmployees(search, salaryGroup, type, office, archived, first, max)
        createOk(employees.map { employeeTranslator.translate(it) }, count.toLong())
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun createEmployee(employee: Employee): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        usersController.findEmployeeNumberDuplicate(employee.employeeNumber).let { if (it.isNotEmpty()) return@async createBadRequest("Employee number already exists")}
        val created = usersController.createEmployee(employee) ?: return@async createInternalServerError("Failed creating a user")
        createOk(employeeTranslator.translate(created))
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun findEmployee(employeeId: UUID): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        val employee = usersController.find(employeeId, EMPLOYEE_ROLE) ?: return@async createNotFound("Employee not found")
        createOk(employeeTranslator.translate(employee))
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun updateEmployee(employeeId: UUID, employee: Employee): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        usersController.findEmployeeNumberDuplicate(employee.employeeNumber).let {
            if (it.isNotEmpty() && it.find { d -> d.id == employeeId.toString() } == null) return@async createBadRequest(
                "Employee number already exists"
            )
        }
        val found = usersController.find(employeeId, EMPLOYEE_ROLE) ?: return@async createNotFound("Employee not found")
        if (found.enabled == false && employee.archivedAt != null) return@async createBadRequest("Cannot update archived employee")
        val updated = usersController.updateEmployee(found, employee) ?: return@async createInternalServerError("Failed updating a user")
        createOk(employeeTranslator.translate(updated))
    }.asUni()

    @RolesAllowed(MANAGER_ROLE)
    override fun deleteEmployee(employeeId: UUID): Uni<Response> = CoroutineScope(vertx.dispatcher()).async {
        if (env.isEmpty || env.getOrNull() != "TEST") {
            return@async createForbidden("Deleting employees is disabled")
        }
        usersController.deleteEmployee(employeeId)
        createNoContent()
    }.asUni()
}