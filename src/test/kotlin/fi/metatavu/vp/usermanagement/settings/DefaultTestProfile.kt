package fi.metatavu.vp.usermanagement.settings

import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.CRON_API_KEY
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.DRIVER_APP_API_KEY
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.KEYCLOAK_API_KEY
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
        config["vp.usermanagement.driverapp.apiKey"] = DRIVER_APP_API_KEY
        config["vp.usermanagement.keycloak.apiKey"] = KEYCLOAK_API_KEY
        config["vp.usermanagement.cron.apiKey"] = CRON_API_KEY
        config["vp.usermanagement.workevents.duplicateremoval.graceperiod.days"] = "3"
        config["env"] = "TEST"
        return config
    }
}