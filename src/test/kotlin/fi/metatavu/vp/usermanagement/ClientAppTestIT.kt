package fi.metatavu.vp.usermanagement

import fi.metatavu.vp.test.client.models.ClientApp
import fi.metatavu.vp.test.client.models.ClientAppStatus
import fi.metatavu.vp.usermanagement.clientapps.ClientAppController
import fi.metatavu.vp.usermanagement.settings.DefaultTestProfile
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Client Apps API tests
 */
@QuarkusTest
@TestProfile(DefaultTestProfile::class)
class ClientAppTestIT: AbstractFunctionalTest() {

    @Test
    fun testCreate() = createTestBuilder().use {
        val createdClientAppWithoutMetadata = it.setApiKey().clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL
            )
        )
        val foundClientAppWithoutMetadata = it.manager.clientApps.find(createdClientAppWithoutMetadata.id!!)

        assertEquals(createdClientAppWithoutMetadata.deviceId, foundClientAppWithoutMetadata.deviceId)
        assertEquals(ClientAppStatus.WAITING_FOR_APPROVAL, foundClientAppWithoutMetadata.status)

        val createdClientAppWithMetadata = it.manager.clientApps.create(
            ClientApp(
                deviceId = "456DEF",
                status = ClientAppStatus.WAITING_FOR_APPROVAL,
                metadata = mapOf(
                    ClientAppController.DEVICE_OS_VERSION_FIELD to "0.1",
                    ClientAppController.APP_VERSION_FIELD to "0.2",
                    ClientAppController.DEVICE_OS_FIELD to "iOS",
                    "unknown-key" to "unknown-value"
                )
            )
        )
        val foundClientAppWithMetadata = it.manager.clientApps.find(createdClientAppWithMetadata.id!!)

        assertEquals(createdClientAppWithMetadata.deviceId, foundClientAppWithMetadata.deviceId)
        assertEquals(ClientAppStatus.WAITING_FOR_APPROVAL, foundClientAppWithMetadata.status)
        assertEquals(foundClientAppWithMetadata.metadata?.get(ClientAppController.DEVICE_OS_VERSION_FIELD), "0.1")
        assertEquals(foundClientAppWithMetadata.metadata?.get(ClientAppController.APP_VERSION_FIELD), "0.2")
        assertEquals(foundClientAppWithMetadata.metadata?.get(ClientAppController.DEVICE_OS_FIELD), "iOS")
        assertFalse(foundClientAppWithMetadata.metadata?.containsKey("unknown-key") ?: true)
    }

    @Test
    fun testList() = createTestBuilder().use {
        for (i in 0..10) {
            it.setApiKey().clientApps.create(
                ClientApp(
                    deviceId = "123ABC-$i",
                    status = ClientAppStatus.WAITING_FOR_APPROVAL
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
        val createdClientApp = it.manager.clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL
            )
        )

        val updatedClientApp = createdClientApp.copy(
            deviceId = "456DEF",
            status = ClientAppStatus.APPROVED,
            metadata = mapOf(
                ClientAppController.DEVICE_OS_VERSION_FIELD to "0.1",
                ClientAppController.APP_VERSION_FIELD to "0.2",
                ClientAppController.DEVICE_OS_FIELD to "iOS",
                "unknown-key" to "unknown-value"
            )
        )
        it.manager.clientApps.update(updatedClientApp.id!!, updatedClientApp)

        val foundClientApp = it.manager.clientApps.find(updatedClientApp.id)
        // Assert that one cannot update the deviceId of the client app
        assertEquals(createdClientApp.deviceId, foundClientApp.deviceId)
        assertEquals(ClientAppStatus.APPROVED, foundClientApp.status)
        assertEquals(foundClientApp.metadata?.get(ClientAppController.DEVICE_OS_VERSION_FIELD), "0.1")
        assertEquals(foundClientApp.metadata?.get(ClientAppController.APP_VERSION_FIELD), "0.2")
        assertEquals(foundClientApp.metadata?.get(ClientAppController.DEVICE_OS_FIELD), "iOS")
        assertFalse(foundClientApp.metadata?.containsKey("unknown-key") ?: true)
    }

    @Test
    fun testDelete() = createTestBuilder().use {
        val createdClientApp = it.manager.clientApps.create(
            ClientApp(
                deviceId = "123ABC",
                status = ClientAppStatus.WAITING_FOR_APPROVAL
            )
        )

        it.manager.clientApps.delete(createdClientApp.id!!)

    }
}