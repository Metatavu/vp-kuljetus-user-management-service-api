package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.TestProfile

/**
 * Native tests for Holidays
 */
@QuarkusIntegrationTest
@TestProfile(DefaultTestProfile::class)
class NativeHolidayTestIT: HolidayTestIT() {
}