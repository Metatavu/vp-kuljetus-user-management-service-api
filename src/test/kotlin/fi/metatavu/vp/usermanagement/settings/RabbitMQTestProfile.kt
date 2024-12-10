package fi.metatavu.vp.usermanagement.settings

import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.CRON_API_KEY
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.DRIVER_APP_API_KEY
import fi.metatavu.vp.usermanagement.settings.ApiTestSettings.Companion.KEYCLOAK_API_KEY
import io.quarkus.test.junit.QuarkusTestProfile

/**
 * RabbitMQ test profile
 */
class RabbitMQTestProfile: QuarkusTestProfile {

    override fun getConfigOverrides(): Map<String, String> {
        val config: MutableMap<String, String> = HashMap()
        config["vp.keycloak.admin.secret"] = "gNyEuK2ybaj6yBf55wPfQaTmiuwlCyPf"
        config["vp.keycloak.admin.client"] = "api"
        config["vp.keycloak.admin.user"] = "admin"
        config["vp.keycloak.admin.password"] = "test"
        config["workShiftHours.recalculate.interval"] = "1s"
        config["vp.usermanagement.driverapp.apiKey"] = DRIVER_APP_API_KEY
        config["vp.usermanagement.keycloak.apiKey"] = KEYCLOAK_API_KEY
        config["vp.usermanagement.cron.apiKey"] = CRON_API_KEY

        config["mp.messaging.incoming.vp-in.connector"] = "smallrye-rabbitmq"
        config["mp.messaging.incoming.vp-in.queue.name"] = "incoming_queue"
        config["mp.messaging.incoming.vp-in.queue.x-queue-type"] = "quorum"
        config["mp.messaging.incoming.vp-in.exchange.name"] = EXCHANGE_NAME
        config["mp.messaging.incoming.vp-in.routing-keys"] = "DRIVER_WORKING_STATE_CHANGE"

        config["mp.messaging.outgoing.vp-out.connector"] = "smallrye-rabbitmq"
        config["mp.messaging.outgoing.vp-out.exchange.name"] = EXCHANGE_NAME

        config["env"] = "TEST"
        return config
    }

    companion object {
        const val EXCHANGE_NAME = "test-exchange"
    }
}
