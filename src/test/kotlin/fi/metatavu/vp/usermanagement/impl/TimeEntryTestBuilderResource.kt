package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.TimeEntriesApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.TimeEntry
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.Assert
import java.time.OffsetDateTime
import java.util.*

/**
 * Test builder resource for time entry api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class TimeEntryTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
) : ApiTestBuilderResource<TimeEntry, ApiClient>(testBuilder, apiClient) {

    private val timeEntryToEmployeeMap = mutableMapOf<UUID, UUID>()

    override fun clean(t: TimeEntry) {
        api.deleteEmployeeTimeEntry(
            timeEntryToEmployeeMap[t.id]!!,
            t.id!!
        )
    }

    override fun getApi(): TimeEntriesApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken
        return TimeEntriesApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Lists time entries
     *
     * @param employeeId employee id
     * @param start start
     * @param end end
     * @param first first
     * @param max max
     * @return list of time entries
     */
    fun listTimeEntries(
        employeeId: UUID,
        start: OffsetDateTime? = null,
        end: OffsetDateTime? = null,
        first: Int = 0,
        max: Int = 10
    ): Array<TimeEntry> {
        return api.listEmployeeTimeEntries(
            employeeId = employeeId,
            start = start?.toString(),
            end = end?.toString(),
            first = first,
            max = max
        )
    }

    /**
     * Creates time entry
     *
     * @param employeeId employee id
     * @param timeEntry time entry
     * @return created time entry
     */
    fun createTimeEntry(employeeId: UUID, timeEntry: TimeEntry): TimeEntry {
        val created = addClosable(api.createEmployeeTimeEntry(employeeId, timeEntry))
        timeEntryToEmployeeMap[created.id!!] = employeeId
        return created
    }

    /**
     * Finds time entry
     *
     * @param employeeId employee id
     * @param id time entry id
     * @return found time entry
     */
    fun findTimeEntry(employeeId: UUID, id: UUID): TimeEntry {
        return api.findEmployeeTimeEntry(employeeId, id)
    }

    /**
     * Updates time entry
     *
     * @param employeeId employee id
     * @param id time entry id
     * @param timeEntry time entry
     * @return updated time entry
     */
    fun updateTimeEntry(employeeId: UUID, id: UUID, timeEntry: TimeEntry): TimeEntry {
        return api.updateEmployeeTimeEntry(employeeId, id, timeEntry)
    }

    /**
     * Asserts time entry create fails
     *
     * @param employeeId employee id
     * @param timeEntry time entry
     * @param expectedStatus expected status
     */
    fun assertCreateFail(employeeId: UUID, timeEntry: TimeEntry, expectedStatus: Int) {
        try {
            api.createEmployeeTimeEntry(employeeId, timeEntry)
            Assert.fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Assert time entry update fails
     *
     * @param employeeId employee id
     * @param id time entry id
     * @param timeEntry time entry
     * @param expectedStatus expected status
     */
    fun assertUpdateFail(employeeId: UUID, id: UUID, timeEntry: TimeEntry, expectedStatus: Int) {
        try {
            api.updateEmployeeTimeEntry(employeeId, id, timeEntry)
            Assert.fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Deletes time entry
     *
     * @param employeeId employee id
     * @param id time entry id
     */
    fun deleteTimeEntry(employeeId: UUID, id: UUID) {
        api.deleteEmployeeTimeEntry(employeeId, id)
        removeCloseable {
            it is TimeEntry && it.id == id
        }
    }
}