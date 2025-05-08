package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.usermanagement.resources.S3TestResource
import fi.metatavu.vp.usermanagement.resources.SftpServerTestResource
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.TestProfile

@QuarkusIntegrationTest
@TestProfile(DefaultTestProfile::class)
@QuarkusTestResource.List(
    QuarkusTestResource(S3TestResource::class),
    QuarkusTestResource(SftpServerTestResource::class)
)
class NativePayrollExportContentTestsIT: PayrollExportContentTestsIT()