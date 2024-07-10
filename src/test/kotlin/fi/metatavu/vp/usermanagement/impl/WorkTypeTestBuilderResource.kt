package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.WorkTypesApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.WorkType
import fi.metatavu.vp.test.client.models.WorkTypeCategory
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.util.*

/**
 * Test builder resource for work types api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class WorkTypeTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<WorkType, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: WorkType) {
        api.deleteWorkType(t.id!!)
    }

    override fun getApi(): WorkTypesApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return WorkTypesApi(ApiTestSettings.apiBasePath)
    }

    fun listWorkTypes(
        category: WorkTypeCategory? = null
    ): Array<WorkType> {
        return api.listWorkTypes(
            category = category
        )
    }

    fun findWorkType(id: UUID): WorkType {
        return api.findWorkType(id)
    }

    fun createWorkType(employee: WorkType): WorkType {
        return addClosable(api.createWorkType(employee))
    }

    fun assertCreateFail(employee: WorkType, expectedStatus: Int) {
        try {
            api.createWorkType(employee)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    fun deleteWorkType(id: UUID) {
        api.deleteWorkType(id)
        removeCloseable { it is WorkType && it.id == id }
    }

    fun assertFindFail(id: UUID, expectedStatus: Int) {
        try {
            api.findWorkType(id)
            Assert.fail(String.format("Expected find to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    fun assertDeleteFail(id: UUID, expectedStatus: Int) {
        try {
            api.deleteWorkType(id)
            Assert.fail(String.format("Expected delete to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }


}