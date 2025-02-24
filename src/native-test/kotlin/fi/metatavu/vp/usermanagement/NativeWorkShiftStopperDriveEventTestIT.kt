package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.usermanagement.settings.TestProfileSchedulerIgnoreWorkShiftStarts
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.TestProfile

/**
 * Native test: Tests the scheduled work shift stopper with a drive event
 */
@QuarkusIntegrationTest
@TestProfile(TestProfileSchedulerIgnoreWorkShiftStarts::class)
class NativeWorkShiftStopperDriveEventTestIT: WorkShiftStopperDriveEventTestIT() {
}