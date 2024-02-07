package fi.metatavu.vp.usermanagement

import fi.metatavu.invalid.InvalidValueTestScenarioBuilder
import fi.metatavu.invalid.InvalidValueTestScenarioPath
import fi.metatavu.invalid.InvalidValues
import fi.metatavu.vp.deliveryinfo.functional.settings.ApiTestSettings
import io.quarkus.test.junit.QuarkusTest
import io.restassured.http.Method
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Drivers API tests
 */
@QuarkusTest
class DriversTestIT : AbstractFunctionalTest() {

    @Test
    fun testList() = createTestBuilder().use {
        val drivers = it.manager.drivers.listDrivers()
        assertEquals(2, drivers.size)

        val driversWithPagination1 = it.manager.drivers.listDrivers(0, 1)
        assertEquals(1, driversWithPagination1.size)

        val driversWithPagination2 = it.manager.drivers.listDrivers(0, 3)
        assertEquals(2, driversWithPagination2.size)
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