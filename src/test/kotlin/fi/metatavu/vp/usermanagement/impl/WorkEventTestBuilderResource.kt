package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.WorkEventsApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.WorkEvent
import fi.metatavu.vp.test.client.models.WorkEventType
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

    /**
     * Override for adding closable to remove existing closables with same type and same id
     */
    override fun addClosable(t: WorkEvent?): WorkEvent {
        removeCloseable {
            it is WorkEvent && it.id == t?.id
        }
        return super.addClosable(t)
    }

    override fun clean(t: WorkEvent) {
        try {
            api.deleteEmployeeWorkEvent(
                t.employeeId,
                t.id!!
            )
        } catch (_: ClientException) { }
    }

    override fun getApi(): WorkEventsApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return WorkEventsApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists work events
     *
     * @param employeeId employee id
     * @param after after
     * @param before before
     * @param first first
     * @param max max
     * @return list of work events
     */
    fun listWorkEvents(
        employeeId: UUID,
        after: OffsetDateTime? = null,
        before: OffsetDateTime? = null,
        first: Int = 0,
        max: Int = 10
    ): Array<WorkEvent> {
        return api.listEmployeeWorkEvents(
            employeeId = employeeId,
            after = after?.toString(),
            before = before?.toString(),
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
    fun createWorkEvent(employeeId: UUID, workEvent: WorkEvent, addClosable: Boolean = true): WorkEvent {
        val event = api.createEmployeeWorkEvent(employeeId, workEvent)
        if (addClosable) addClosable(event)
        return event
    }

    fun createWorkEvent(employeeId: UUID, time: String, type: WorkEventType): WorkEvent {
        return createWorkEvent(employeeId, WorkEvent(employeeId = employeeId, time = time, workEventType = type))
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
        return updateWorkEvent(employeeId, id, UUID.randomUUID(), workEvent)
    }

    /**
     * Updates work event
     *
     * @param employeeId employee id
     * @param id work event
     * @param changeSetId change set id
     * @param workEvent work event
     * @return updated work event
     */
    fun updateWorkEvent(employeeId: UUID, id: UUID, changeSetId: UUID, workEvent: WorkEvent): WorkEvent {
        return api.updateEmployeeWorkEvent(employeeId, id, changeSetId, workEvent)
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
            api.updateEmployeeWorkEvent(employeeId, id, UUID.randomUUID(), workEvent)
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

    /**
     * Asserts that event deletion fails with status
     *
     * @param employeeId employee id
     * @param id id
     * @param expectedStatus expected error code
     */
    fun assertDeleteFail(employeeId: UUID, id: UUID, expectedStatus: Int) {
        try {
            api.deleteEmployeeWorkEvent(employeeId, id)
            Assert.fail(String.format("Expected delete to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Adds employee shift start to closeables
     *
     * @param employeeId employee id
     */
    fun addEmployeeShiftStartToCloseables(employeeId: UUID) {
        api.listEmployeeWorkEvents(employeeId = employeeId).forEach { event ->
            if (event.workEventType == WorkEventType.SHIFT_START) {
                addClosable(event)
            }
        }
    }
}