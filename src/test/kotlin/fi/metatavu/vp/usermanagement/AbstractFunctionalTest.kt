package fi.metatavu.vp.usermanagement

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fi.metatavu.vp.test.client.models.Holiday
import io.quarkus.test.common.DevServicesContext
import org.eclipse.microprofile.config.ConfigProvider
import org.json.JSONException
import org.skyscreamer.jsonassert.JSONCompare
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.JSONCompareResult
import org.skyscreamer.jsonassert.comparator.CustomComparator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * Abstract base class for functional tests
 */
abstract class AbstractFunctionalTest {

    /**
     * Returns the latest day before the given date that is not on a weekend
     *
     * @param date date to subtract from
     */
    fun getLastWorkDay(
        date: LocalDate
    ): LocalDate {
        var dateToStartFrom = date

        dateToStartFrom = (
                when (dateToStartFrom.dayOfWeek.value) {
                    1 -> { dateToStartFrom.minusDays(3) }
                    7 -> { dateToStartFrom.minusDays(2) }
                    else -> { dateToStartFrom.minusDays(1) }
                })

        return dateToStartFrom
    }

    /**
     * Compares objects as serialized JSONs
     *
     * @param expected expected
     * @param actual actual
     * @return comparison result
     * @throws JSONException
     * @throws JsonProcessingException
     */
    @Throws(JSONException::class, JsonProcessingException::class)
    private fun jsonCompare(expected: Any?, actual: Any?): JSONCompareResult? {
        val customComparator = CustomComparator(JSONCompareMode.LENIENT)
        return JSONCompare.compareJSON(toJSONString(expected), toJSONString(actual), customComparator)
    }

    /**
     * Serializes an object into JSON
     *
     * @param object object
     * @return JSON string
     * @throws JsonProcessingException
     */
    @Throws(JsonProcessingException::class)
    private fun toJSONString(`object`: Any?): String? {
        return if (`object` == null) {
            null
        } else getObjectMapper().writeValueAsString(`object`)
    }

    /**
     * Returns object mapper with default modules and settings
     *
     * @return object mapper
     */
    private fun getObjectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(JavaTimeModule())
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        return objectMapper
    }

    private var devServicesContext: DevServicesContext? = null

    /**
     * Creates new test builder
     *
     * @return new test builder
     */
    protected fun createTestBuilder(): TestBuilder {
        return TestBuilder(getConfig())
    }

    /**
     * Returns config for tests.
     *
     * If tests are running in native mode, method returns config from devServicesContext and
     * when tests are runnig in JVM mode method returns config from the Quarkus config
     *
     * @return config for tests
     */
    fun getConfig(): Map<String, String> {
        return getDevServiceConfig() ?: getQuarkusConfig()
    }

    /**
     * Returns test config from dev services
     *
     * @return test config from dev services
     */
    private fun getDevServiceConfig(): Map<String, String>? {
        return devServicesContext?.devServicesProperties()
    }

    /**
     * Returns test config from Quarkus
     *
     * @return test config from Quarkus
     */
    private fun getQuarkusConfig(): Map<String, String> {
        val config = ConfigProvider.getConfig()
        return config.propertyNames.associateWith { config.getConfigValue(it).rawValue }
    }

    companion object {
        val EMPLOYEE_USER_ID = UUID.fromString("f1cc426d-8191-4046-8047-b6abe3ef4fde")
    }
}