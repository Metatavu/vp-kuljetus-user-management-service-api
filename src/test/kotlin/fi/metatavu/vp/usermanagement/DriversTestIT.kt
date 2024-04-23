package fi.metatavu.vp.usermanagement

import fi.metatavu.invalid.InvalidValueTestScenarioBuilder
import fi.metatavu.invalid.InvalidValueTestScenarioPath
import fi.metatavu.invalid.InvalidValues
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.http.Method
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Drivers API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class DriversTestIT : AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        val drivers = it.manager.drivers.listDrivers()
        assertEquals(2, drivers.size)

        val driversWithPagination1 = it.manager.drivers.listDrivers(archived = null, first = 0, max = 1)
        assertEquals(1, driversWithPagination1.size)

        val driversWithPagination2 = it.manager.drivers.listDrivers(archived = null, first = 0, max = 5)
        assertEquals(2, driversWithPagination2.size)

        val driversWithPagination3 = it.manager.drivers.listDrivers(archived = null, first = 1, max = 1)
        assertEquals(1, driversWithPagination3.size)

        val driversWithPagination4 = it.manager.drivers.listDrivers(archived = false, first = 1, max = 2)
        assertEquals(1, driversWithPagination4.size)

        val driversWithPagination5 = it.manager.drivers.listDrivers(archived = null, first = 1)
        assertEquals(1, driversWithPagination5.size)

        val driversWithPagination6 = it.manager.drivers.listDrivers(archived = null, max = 1)
        assertEquals(1, driversWithPagination6.size)

        val inactiveDrivers = it.manager.drivers.listDrivers(archived = true)
        assertEquals(1, inactiveDrivers.size)
        assertEquals(OffsetDateTime.parse("2024-02-20T09:32:46.063449777+02:00").toEpochSecond(),
            OffsetDateTime.parse(inactiveDrivers[0].archivedAt).toEpochSecond())

        val driverCardIdFilter = it.manager.drivers.listDrivers(driverCardId = "001")
        assertEquals(1, driverCardIdFilter.size)

        val driverCardIdFilter2 = it.manager.drivers.listDrivers(driverCardId = "003", archived = false)
        assertEquals(0, driverCardIdFilter2.size)

        val driverCardIdFilter3 = it.manager.drivers.listDrivers(driverCardId = "003", archived = true)
        assertEquals(1, driverCardIdFilter3.size)
    }

    @Test
    fun testListFail() = createTestBuilder().use {
        it.driver1.drivers.assertListFail(403)
        it.user.drivers.assertListFail(403)
    }

    @Test
    fun testFind() = createTestBuilder().use { tb ->
        val drivers = tb.manager.drivers.listDrivers()
        val driver1 = drivers.find { it.id.toString() == "95dd89a2-da9a-4ce4-979d-8897b7603b2e" }

        val driver = tb.manager.drivers.findDriver(driver1!!.id!!)
        assertEquals(driver1.id, driver.id)
        assertEquals("tommi tommi", driver.displayName)
    }

    @Test
    fun testFindFail() = createTestBuilder().use {
        InvalidValueTestScenarioBuilder(
            path = "v1/drivers/{driverId}",
            method = Method.GET,
            token = it.manager.accessTokenProvider.accessToken,
            basePath = ApiTestSettings.apiBasePath,
        )
            .path(
                InvalidValueTestScenarioPath(
                    name = "driverId",
                    values = InvalidValues.STRING_NOT_NULL,
                    expectedStatus = 404
                )
            )
            .build()
            .test()
    }

}