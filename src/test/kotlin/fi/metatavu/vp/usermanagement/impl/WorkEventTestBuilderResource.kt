package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.WorkEventsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.time.OffsetDateTime
import java.util.*

/**
 * Test builder resource for work event api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class WorkEventTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<WorkEvent, ApiClient>(testBuilder, apiClient) {


    override fun clean(t: WorkEvent) {
        api.deleteEmployeeWorkEvent(
            t.employeeId,
            t.id!!
        )
    }

    override fun getApi(): WorkEventsApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return WorkEventsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists work events
     *
     * @param employeeId employee id
     * @param start start
     * @param first first
     * @param max max
     * @return list of work events
     */
    fun listWorkEvents(
        employeeId: UUID,
        start: OffsetDateTime? = null,
        first: Int = 0,
        max: Int = 10
    ): Array<WorkEvent> {
        return api.listEmployeeWorkEvents(
            employeeId = employeeId,
            start = start?.toString(),
            first = first,
            max = max
        )
    }

    /**
     * Creates work event
     *
     * @param employeeId employee id
     * @param workEvent work event
     * @return created work event
     */
    fun createWorkEvent(employeeId: UUID, workEvent: WorkEvent): WorkEvent {
        return addClosable(api.createEmployeeWorkEvent(employeeId, workEvent))
    }

    /**
     * Finds work event
     *
     * @param employeeId employee id
     * @param id work event id
     * @return found work event
     */
    fun findWorkEvent(employeeId: UUID, id: UUID): WorkEvent {
        return api.findEmployeeWorkEvent(employeeId, id)
    }

    /**
     * Updates work event
     *
     * @param employeeId employee id
     * @param id work event
     * @param workEvent work event
     * @return updated work event
     */
    fun updateWorkEvent(employeeId: UUID, id: UUID, workEvent: WorkEvent): WorkEvent {
        return api.updateEmployeeWorkEvent(employeeId, id, workEvent)
    }

    /**
     * Asserts work event create fails
     *
     * @param employeeId employee id
     * @param workEvent work event
     * @param expectedStatus expected status
     */
    fun assertCreateFail(employeeId: UUID, workEvent: WorkEvent, expectedStatus: Int) {
        try {
            api.createEmployeeWorkEvent(employeeId, workEvent)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Assert work event update fails
     *
     * @param employeeId employee id
     * @param id time entry id
     * @param workEvent work event
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(employeeId: UUID, id: UUID, workEvent: WorkEvent, expectedStatus: Int) {
        try {
            api.updateEmployeeWorkEvent(employeeId, id, workEvent)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Deletes work event
     *
     * @param employeeId employee id
     * @param id work event id
     */
    fun deleteWorkEvent(employeeId: UUID, id: UUID) {
        api.deleteEmployeeWorkEvent(employeeId, id)
        removeCloseable {
            it is WorkEvent && it.id == id
        }
    }
}