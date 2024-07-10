package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.EmployeesApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.infrastructure.ServerException
import fi.metatavu.vp.test.client.models.Employee
import fi.metatavu.vp.test.client.models.EmployeeType
import fi.metatavu.vp.test.client.models.Office
import fi.metatavu.vp.test.client.models.SalaryGroup
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.time.OffsetDateTime
import java.util.*

/**
 * Test builder resource for employees
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class EmployeeTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<Employee, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: Employee) {
        api.deleteEmployee(t.id!!)
    }

    override fun getApi(): EmployeesApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return EmployeesApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists employees
     *
     * @param search search
     * @param type type
     * @param office office
     * @param salaryGroup salary group
     * @param archived archived
     * @param first first
     * @param max max
     * @return list of employees
     */
    fun listEmployees(
        search: String? = null,
        type: EmployeeType? = null,
        office: Office? = null,
        salaryGroup: SalaryGroup? = null,
        archived: Boolean? = null,
        first: Int = 0,
        max: Int = 10
    ): Array<Employee> {
        return api.listEmployees(
            search = search,
            type = type,
            office = office,
            salaryGroup = salaryGroup,
            archived = archived,
            first = first,
            max = max
        )
    }

    /**
     * Creates new employee
     *
     * @param employee employee
     * @return created employee
     */
    fun createEmployee(employee: Employee): Employee {
        return addClosable(api.createEmployee(employee))
    }

    /**
     * Finds employee
     *
     * @param driverId driver id
     * @return found employee
     */
    fun findEmployee(driverId: UUID): Employee {
        return api.findEmployee(driverId)
    }

    /**
     * Updates employee
     *
     * @param employeeId employee id
     * @param employee employee
     * @return updated employee
     */
    fun updateEmployee(employeeId: UUID, employee: Employee): Employee {
        return api.updateEmployee(employeeId, employee)
    }

    /**
     * Creates new employee with random values
     *
     * @return created employee
     */
    fun createEmployee(numer: String): Employee {
        return createEmployee(
            Employee(
                firstName = "Test",
                lastName = "Employee",
                type = EmployeeType.AH,
                office = Office.KOTKA,
                salaryGroup = SalaryGroup.DRIVER,
                driverCardLastReadOut = OffsetDateTime.now().toString(),
                driverCardId = "001",
                regularWorkingHours = 8.0f,
                employeeNumber = numer
            )
        )
    }

    /**
     * Asserts find employee fails with expected status
     *
     * @param id id
     * @param expectedStatus expected status
     */
    fun assertFindServerFail(id: UUID, expectedStatus: Int) {
        try {
            api.findEmployee(id)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ServerException) {
            assertServerExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts create employee fails with expected status
     *
     * @param employee employee
     * @param expectedStatus expected status
     */
    fun assertCreateFail(employee: Employee, expectedStatus: Int) {
        try {
            api.createEmployee(employee)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts update employee fails with expected status
     *
     * @param employeeId employee id
     * @param employee employee
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(employeeId: UUID, employee: Employee, expectedStatus: Int) {
        try {
            api.updateEmployee(employeeId, employee)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }
}