package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.EmployeeWorkShiftsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.util.*

/**
 * Test builder resource for work shift api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class WorkShiftTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<EmployeeWorkShift, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: EmployeeWorkShift) {
        api.deleteWorkShift(t.employeeId, t.id!!)
    }

    override fun getApi(): EmployeeWorkShiftsApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return EmployeeWorkShiftsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists work shifts
     *
     * @param employeeId employee id
     * @param startedAfter started after
     * @param startedBefore started before
     * @param first first
     * @param max max
     * @return list of work shifts
     */
    fun listEmployeeWorkShifts(
        employeeId: UUID,
        startedAfter: String? = null,
        startedBefore: String? = null,
        first: Int? = 0,
        max: Int? = 10
    ): Array<EmployeeWorkShift> {
        return api.listEmployeeWorkShifts(
            employeeId = employeeId,
            startedBefore = startedBefore,
            startedAfter = startedAfter,
            first = first,
            max = max
        )
    }

    /**
     * Finds work shift
     *
     * @param employeeId employee id
     * @param id work shift id
     */
    fun findEmployeeWorkShift(employeeId: UUID, id: UUID): EmployeeWorkShift {
        return api.findEmployeeWorkShift(employeeId, id)
    }

    /**
     * Updates work shift
     *
     * @param employeeId employee id
     * @param id work shift id
     * @param workShift work shift
     * @return updated work shift
     */
    fun updateEmployeeWorkShift(employeeId: UUID, id: UUID, workShift: EmployeeWorkShift): EmployeeWorkShift {
        return api.updateEmployeeWorkShift(employeeId, id, workShift)
    }

    /**
     * Asserts work shift update fails
     *
     * @param employeeId employee id
     * @param id work shift id
     * @param workShift work shift
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(employeeId: UUID, id: UUID, workShift: EmployeeWorkShift, expectedStatus: Int) {
        try {
            api.updateEmployeeWorkShift(employeeId, id, workShift)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

}