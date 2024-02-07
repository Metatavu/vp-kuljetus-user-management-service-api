package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.TestProfile

/**
 * Native tests for Drivers API
 */
@QuarkusIntegrationTest
@TestProfile(DefaultTestProfile::class)
class NativeDriversTestIT: DriversTestIT()
