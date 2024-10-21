package fi.metatavu.vp.usermanagement.settings

import io.quarkus.test.junit.QuarkusTestProfile

/**
 * Default test profile
 */
class DefaultTestProfile: QuarkusTestProfile {

    override fun getConfigOverrides(): Map<String, String> {
        val config: MutableMap<String, String> = HashMap()
        config["vp.keycloak.admin.secret"] = "gNyEuK2ybaj6yBf55wPfQaTmiuwlCyPf"
        config["vp.keycloak.admin.client"] = "api"
        config["vp.keycloak.admin.user"] = "admin"
        config["vp.keycloak.admin.password"] = "test"
        config["vp.vehiclemanagement.telematics.apiKey"] = "test-api-key"
        config["env"] = "TEST"
        config["fillWorkShiftHours.every.expr"] = "30s"
        config["fillWorkShiftHours.delay.expr"] = "1S"
        return config
    }
}