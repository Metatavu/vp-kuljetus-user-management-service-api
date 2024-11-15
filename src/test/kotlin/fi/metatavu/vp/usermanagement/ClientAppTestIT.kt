package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.ClientApp
import fi.metatavu.vp.test.client.models.ClientAppMetadata
import fi.metatavu.vp.test.client.models.ClientAppStatus
import fi.metatavu.vp.test.client.models.VerifyClientAppRequest
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Client Apps API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class ClientAppTestIT: AbstractFunctionalTest() {

    @Test
    fun testCreate() = createTestBuilder().use {
        val createdClientApp = it.setApiKey().clientApps.create(
            ClientApp(
                deviceId = "456DEF",
                status = ClientAppStatus.WAITING_FOR_APPROVAL,
                metadata = ClientAppMetadata(
                    deviceOS = ClientAppMetadata.DeviceOS.ANDROID,
                    deviceOSVersion = "0.1",
                    appVersion = "0.2"
                )
            )
        )
        val foundClientAppWithMetadata = it.manager.clientApps.find(createdClientApp.id!!)

        assertEquals(createdClientApp.deviceId, foundClientAppWithMetadata.deviceId)
        assertEquals(ClientAppStatus.WAITING_FOR_APPROVAL, foundClientAppWithMetadata.status)
        assertEquals(foundClientAppWithMetadata.metadata.deviceOSVersion, "0.1")
        assertEquals(foundClientAppWithMetadata.metadata.appVersion, "0.2")
        assertEquals(foundClientAppWithMetadata.metadata.deviceOS, ClientAppMetadata.DeviceOS.ANDROID)

        it.setApiKey("invalid-api-key").clientApps.assertCreateFail(createdClientApp, 403)

        // Assert that one cannot create a client app with the same device id if the status is not WAITING_FOR_APPROVAL
        it.manager.clientApps.update(createdClientApp.id, createdClientApp.copy(status = ClientAppStatus.APPROVED))
        it.setApiKey().clientApps.assertCreateFail(createdClientApp, 409)
    }

    @Test
    fun testList() = createTestBuilder().use {
        for (i in 0..10) {
            it.setApiKey().clientApps.create(
                ClientApp(
                    deviceId = "123ABC-$i",
                    status = ClientAppStatus.WAITING_FOR_APPROVAL,
                    metadata = ClientAppMetadata(
                        deviceOS = ClientAppMetadata.DeviceOS.ANDROID,
                        deviceOSVersion = "0.1",
                        appVersion = "0.2"
                    )
                )
            )
        }

        val clientApps = it.manager.clientApps.list()
        assertEquals(10, clientApps.size)

        val clientAppsPaginated1 = it.manager.clientApps.list(first = 0, max = 1)
        val clientAppsPaginated2 = it.manager.clientApps.list(first = 1, max = 1)
        assertEquals(1, clientAppsPaginated1.size)
        assertEquals(1, clientAppsPaginated2.size)
        assertNotEquals(clientAppsPaginated1.first().id, clientAppsPaginated2.first().id)

        val approvedClientApps = it.manager.clientApps.list(status = ClientAppStatus.APPROVED)
        assertEquals(0, approvedClientApps.size)

        val clientAppToUpdate = clientAppsPaginated1.first().copy(status = ClientAppStatus.APPROVED)
        it.manager.clientApps.update(clientAppToUpdate.id!!, clientAppToUpdate)
        val approvedClientApps1 = it.manager.clientApps.list(status = ClientAppStatus.APPROVED)
        assertEquals(1, approvedClientApps1.size)
    }

    @Test
    fun testUpdate() = createTestBuilder().use {
        val createdClientApp = it.setApiKey().clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL,
                metadata = ClientAppMetadata(
                    deviceOS = ClientAppMetadata.DeviceOS.ANDROID,
                    deviceOSVersion = "0.1",
                    appVersion = "0.2"
                )
            )
        )

        val updatedClientApp = createdClientApp.copy(
            deviceId = "456DEF",
            status = ClientAppStatus.APPROVED
        )
        it.manager.clientApps.update(updatedClientApp.id!!, updatedClientApp)

        val foundClientApp = it.manager.clientApps.find(updatedClientApp.id)
        // Assert that one cannot update the deviceId of the client app
        assertEquals(createdClientApp.deviceId, foundClientApp.deviceId)
        assertEquals(ClientAppStatus.APPROVED, foundClientApp.status)
        assertEquals(foundClientApp.metadata.deviceOSVersion, "0.1")
        assertEquals(foundClientApp.metadata.appVersion, "0.2")
        assertEquals(foundClientApp.metadata.deviceOS, ClientAppMetadata.DeviceOS.ANDROID)
    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val createdClientApp = it.setApiKey().clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL,
                metadata = ClientAppMetadata(
                    deviceOS = ClientAppMetadata.DeviceOS.ANDROID,
                    deviceOSVersion = "0.1",
                    appVersion = "0.2"
                )
            )
        )

        it.manager.clientApps.delete(createdClientApp.id!!)

        it.manager.clientApps.assertDeleteFail(UUID.randomUUID(), 404)
        it.manager.clientApps.assertDeleteFail(createdClientApp.id, 404)
    }

    @Test
    fun testVerifyClientApp() = createTestBuilder().use {
        val createdClientApp = it.setApiKey().clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL,
                metadata = ClientAppMetadata(
                    deviceOS = ClientAppMetadata.DeviceOS.ANDROID,
                    deviceOSVersion = "0.1",
                    appVersion = "0.2"
                )
            )
        )

        val verifiedResult = it.manager.clientApps.verifyClientApp(VerifyClientAppRequest(createdClientApp.deviceId))
        assertFalse(verifiedResult)

        it.manager.clientApps.update(createdClientApp.id!!, createdClientApp.copy(status = ClientAppStatus.APPROVED))
        val verifiedResult2 = it.manager.clientApps.verifyClientApp(VerifyClientAppRequest(createdClientApp.deviceId))
        assertTrue(verifiedResult2)

        val verifiedUnknownDevice = it.manager.clientApps.verifyClientApp(VerifyClientAppRequest(UUID.randomUUID().toString()))
        assertFalse(verifiedUnknownDevice)

        it.setApiKey("invalid-api-key").clientApps.assertVerifyClientAppFail(VerifyClientAppRequest(createdClientApp.deviceId), 403)
    }
}