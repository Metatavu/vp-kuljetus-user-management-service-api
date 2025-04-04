package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.WorkShiftHoursApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.WorkShiftHours
import fi.metatavu.vp.test.client.models.WorkType
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.util.*

/**
 * Test builder resource for work shift hours api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class WorkShiftHoursTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<WorkShiftHours, ApiClient>(testBuilder, apiClient) {

    override fun clean(t: WorkShiftHours) {
        try {
            deleteWorkShiftHours(t.id!!)
        } catch (_: ClientException) {

        }
    }

    override fun getApi(): WorkShiftHoursApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return WorkShiftHoursApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists work shift hours
     *
     * @param employeeId employee id
     * @param employeeWorkShiftId employee work shift id
     * @param workType work type
     * @param employeeWorkShiftStartedAfter employee work shift started after
     * @param employeeWorkShiftStartedBefore employee work shift started before
     * @return list of work shift hours
     */
    fun listWorkShiftHours(
        employeeId: UUID? = null,
        employeeWorkShiftId: UUID? = null,
        workType: WorkType? = null,
        employeeWorkShiftStartedAfter: String? = null,
        employeeWorkShiftStartedBefore: String? = null
    ): Array<WorkShiftHours> {
        val all = api.listWorkShiftHours(
            employeeId = employeeId,
            employeeWorkShiftId = employeeWorkShiftId,
            workType = workType,
            employeeWorkShiftStartedBefore = employeeWorkShiftStartedBefore,
            employeeWorkShiftStartedAfter = employeeWorkShiftStartedAfter
        )

        all.forEach {
            addClosable(it)
        }

        return all
    }

    /**
     * Finds work shift hours
     */
    fun findWorkShiftHours(id: UUID): WorkShiftHours {
        return api.findWorkShiftHours(id)
    }

    /**
     * Updates work shift hours
     *
     * @param id work shift hours id
     * @param workEvent work event
     * @return updated work shift hours
     */
    fun updateWorkShiftHours(id: UUID, workShiftHours: WorkShiftHours): WorkShiftHours {
        return updateWorkShiftHours(id, UUID.randomUUID(), workShiftHours)
    }

    /**
     * Updates work shift hours
     *
     * @param id work shift hours id
     * @param workShiftChangeSetId work shift change set id
     * @param workEvent work event
     * @return updated work shift hours
     */
    fun updateWorkShiftHours(id: UUID, workShiftChangeSetId: UUID, workShiftHours: WorkShiftHours): WorkShiftHours {
        return api.updateWorkShiftHours(id, workShiftChangeSetId, workShiftHours)
    }


    /**
     * Deletes work shift hours
     *
     * @param id work shift hours id
     */
    fun deleteWorkShiftHours(id: UUID) {
        api.deleteWorkShiftHours(id)
    }

    /**
     * Asserts that update fails with expected status
     *
     * @param id work shift hours id
     * @param workEvent work event
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(id: UUID, workEvent: WorkShiftHours, expectedStatus: Int) {
        assertUpdateFail(id, UUID.randomUUID(), workEvent, expectedStatus)
    }

    /**
     * Asserts that update fails with expected status
     *
     * @param id work shift hours id
     * @param changeSetId change set id
     * @param workEvent work event
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(id: UUID, changeSetId: UUID, workEvent: WorkShiftHours, expectedStatus: Int) {
        try {
            api.updateWorkShiftHours(id, changeSetId, workEvent)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

}