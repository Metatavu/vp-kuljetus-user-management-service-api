package fi.metatavu.vp.usermanagement.settings

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
        config["vp.vehiclemanagement.telematics.apiKey"] = "test-api-key"

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
