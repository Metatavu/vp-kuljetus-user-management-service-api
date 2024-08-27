package fi.metatavu.vp.usermanagement.impl

import fi.metatavu.jaxrs.test.functional.builder.auth.AccessTokenProvider
import fi.metatavu.vp.test.client.apis.HolidaysApi
import fi.metatavu.vp.test.client.infrastructure.ApiClient
import fi.metatavu.vp.test.client.infrastructure.ClientException
import fi.metatavu.vp.test.client.models.Holiday
import fi.metatavu.vp.usermanagement.TestBuilder
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import org.junit.jupiter.api.fail
import java.util.*

/**
 * Test builder resource for holiday api
 *
 * @param testBuilder test builder
 * @param accessTokenProvider access token provider
 * @param apiClient api client
 */
class HolidayTestBuilderResource(
    testBuilder: TestBuilder,
    private val accessTokenProvider: AccessTokenProvider?,
    apiClient: ApiClient
): ApiTestBuilderResource<Holiday, ApiClient>(testBuilder, apiClient) {
    override fun clean(t: Holiday?) {
        api.deleteHoliday(t!!.id!!)
    }

    override fun getApi(): HolidaysApi {
        ApiClient.accessToken = accessTokenProvider?.accessToken

        return HolidaysApi(ApiTestSettings.apiBasePath)
    }

    /**
     * Creates a holiday
     *
     * @param holiday holiday
     * @return created holiday
     */
    fun create(holiday: Holiday): Holiday {
        return addClosable(api.createHoliday(holiday))
    }

    /**
     * Finds a holiday
     *
     * @param holidayId holiday id
     * @return found holiday
     */
    fun findHoliday(holidayId: UUID): Holiday {
        return api.findHoliday(holidayId)
    }

    /**
     * Lists holidays
     *
     * @param year year
     * @param first first
     * @param max max
     * @return list of holidays
     */
    fun listHolidays(year: Int? = null, first: Int = 0, max: Int = 10): Array<Holiday> {
        return api.listHolidays(year, first, max)
    }

    /**
     * Deletes a holiday
     *
     * @param holidayId holiday id
     */
    fun deleteHoliday(holidayId: UUID) {
        api.deleteHoliday(holidayId)
        removeCloseable {
            it is Holiday && it.id == holidayId
        }
    }

    /**
     * Updates a holiday
     *
     * @param holidayId holiday id
     * @param holiday holiday
     * @return updated holiday
     */
    fun updateHoliday(holidayId: UUID, holiday: Holiday): Holiday {
        return api.updateHoliday(holidayId, holiday)
    }

    /**
     * Asserts holiday create fails
     *
     * @param expectedStatus expected status
     * @param holiday holiday
     */
    fun assertCreateFail(expectedStatus: Int, holiday: Holiday) {
        try {
            api.createHoliday(holiday)
            fail(String.format("Expected create to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts holiday update fails
     *
     * @param expectedStatus expected status
     * @param holidayId holiday id
     * @param holiday holiday
     */
    fun assertUpdateFail(expectedStatus: Int, holidayId: UUID, holiday: Holiday) {
        try {
            api.updateHoliday(holidayId, holiday)
            fail(String.format("Expected update to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts holiday delete fails
     *
     * @param expectedStatus expected status
     * @param holidayId holiday id
     */
    fun assertDeleteFail(expectedStatus: Int, holidayId: UUID) {
        try {
            api.deleteHoliday(holidayId)
            fail(String.format("Expected delete to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }

    /**
     * Asserts holiday find fails
     *
     * @param expectedStatus expected status
     * @param holidayId holiday id
     */
    fun assertFindFail(expectedStatus: Int, holidayId: UUID) {
        try {
            api.findHoliday(holidayId)
            fail(String.format("Expected find to fail with status %d", expectedStatus))
        } catch (ex: ClientException) {
            assertClientExceptionStatus(expectedStatus, ex)
        }
    }
}