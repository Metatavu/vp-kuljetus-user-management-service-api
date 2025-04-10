package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.WorkShiftChangeSetsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.EmployeeWorkShift
import fi.metatavu.vp.test.client.models.WorkShiftChangeSet
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*

/**
 * Test builder resource for work shift change sets api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class WorkShiftChangeSetsTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
): ApiTestBuilderResource<WorkShiftChangeSet, ApiClient>(testBuilder, apiClient) {
    override fun clean(p0: WorkShiftChangeSet?) {
        TODO("Not yet implemented")
    }

    override fun getApi(): WorkShiftChangeSetsApi {

        ApiClient.accessToken = accessTokenProvider?.accessToken
        return WorkShiftChangeSetsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * List work shift change sets
     *
     * @param employeeId
     */
    fun list(employeeId: UUID): List<WorkShiftChangeSet> {
        return api.listWorkShiftChangeSets(employeeId).toList()
    }

    /**
     * Test that listing fails with the expected status
     *
     * @param employeeId
     * @param expectedStatus
     */
    fun assertListFail(employeeId: UUID, expectedStatus: Int) {
        try {
            api.listWorkShiftChangeSets(employeeId)
            Assert.fail(String.format("Expected list to fail with status %d", expectedStatus))
        } catch (exception: ClientException) {
            assertEquals(expectedStatus, exception.statusCode)
        }
    }
}