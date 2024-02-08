package fi.metatavu.vp.usermanagement.keycloak

import com.fasterxml.jackson.annotation.JsonProperty
import io.quarkus.runtime.annotations.RegisterForReflection

/**
 * Data class for Keycloak access tokens
 *
 * @property accessToken access token field value
 * @property expiresIn expires in field value
 * @property refreshExpiresIn refresh expires in field value
 * @property refreshToken refresh token field value
 * @property tokenType token type field value
 * @property notBeforePolicy not before policy field value
 * @property sessionState session state field value
 * @property scope scope field value
 */
@RegisterForReflection
data class KeycloakAccessToken (

    @field:JsonProperty("access_token")
    val accessToken: String,

    @field:JsonProperty("expires_in")
    val expiresIn: Long,

    @field:JsonProperty("refresh_expires_in")
    val refreshExpiresIn: Long,

    @field:JsonProperty("refresh_token")
    val refreshToken: String,

    @field:JsonProperty("token_type")
    val tokenType: String,

    @field:JsonProperty("not-before-policy")
    val notBeforePolicy: Long,

    @field:JsonProperty("session_state")
    val sessionState: String,

    @field:JsonProperty("scope")
    val scope: String

)