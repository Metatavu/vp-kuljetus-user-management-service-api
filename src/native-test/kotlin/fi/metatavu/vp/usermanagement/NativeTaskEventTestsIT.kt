package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.usermanagement.settings.RabbitMQTestProfile
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.TestProfile

@QuarkusIntegrationTest
@TestProfile(RabbitMQTestProfile::class)
class NativeTaskEventTestsIT: TaskEventTestsIT()