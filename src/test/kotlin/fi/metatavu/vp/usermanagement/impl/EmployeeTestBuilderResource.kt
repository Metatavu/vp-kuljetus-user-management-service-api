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
 * Test builder resource for drivers
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiKey api key
 * @param apiClient api client
 */
class EmployeeTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    private val apiKey: String?,
    apiClient: ApiClient
) : ApiTestBuilderResource<Employee, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: Employee) {
        api.deleteEmployee(t.id!!)
    }

    override fun getApi(): EmployeesApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return EmployeesApi(ApiTestSettings.apiBasePath)
    }

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

    fun findEmployee(driverId: UUID): Employee {
        return api.findEmployee(driverId)
    }

    fun createEmployee(employee: Employee): Employee {
        return addClosable(api.createEmployee(employee))
    }

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

    fun assertFindServerFail(id: UUID, expectedStatus: Int) {
        try {
            api.findEmployee(id)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ServerException) {
            assertServerExceptionStatus(expectedStatus, ex)
        }
    }

    fun assertCreateFail(employee: Employee, expectedStatus: Int) {
        try {
            api.createEmployee(employee)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    fun updateEmployee(employeeId: UUID, employee: Employee): Employee {
        return api.updateEmployee(employeeId, employee)
    }

    fun assertUpdateFail(employeeId: UUID, employee: Employee, expectedStatus: Int) {
        try {
            api.updateEmployee(employeeId, employee)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }
}