package fi.metatavu.vp.usermanagement.assertions

import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime

/**
 * Custom assertions for testing
 */
class Assertions {
    companion object {
        /**
         * Asserts that two OffsetDateTime objects are equal per second
         *
         * @param expected expected OffsetDateTime
         * @param actual actual OffsetDateTime
         */
        fun assertOffsetDateTimeEquals(expected: OffsetDateTime, actual: OffsetDateTime) {
            assertEquals(expected.toEpochSecond(), actual.toEpochSecond())
        }

        /**
         * Asserts that two OffsetDateTime objects are equal per second
         *
         * @param expected expected OffsetDateTime
         * @param actual actual OffsetDateTime
         */
        fun assertOffsetDateTimeEquals(expected: String, actual: String?) {
            assertOffsetDateTimeEquals(OffsetDateTime.parse(expected), OffsetDateTime.parse(actual))
        }
    }
}