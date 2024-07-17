package fi.metatavu.vp.usermanagement.rest

import io.quarkus.security.identity.SecurityIdentity
import io.smallrye.mutiny.Uni
import io.vertx.core.Vertx
import jakarta.inject.Inject
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.*
import io.smallrye.mutiny.coroutines.asUni
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * Abstract base class for all API services
 *
 * @author Jari Nykänen
 */
abstract class AbstractApi {

    @Inject
    lateinit var vertx: Vertx

    @Context
    lateinit var headers: HttpHeaders

    @ConfigProperty(name = "vp.vehiclemanagement.telematics.apiKey")
    lateinit var apiKey: String

    @Context
    lateinit var securityContext: SecurityContext

    @Inject
    lateinit var identity: SecurityIdentity

    @Inject
    private lateinit var jsonWebToken: JsonWebToken

    /**
     * Checks if user is manager
     *
     * @return true if manager
     */
    protected fun isManager(): Boolean {
        return identity.hasRole(MANAGER_ROLE)
    }

    /**
     * Returns request api key
     *
     * @return request api key
     */
    protected val requestApiKey: String?
        get() {
            return headers.getHeaderString("X-API-Key")
        }

    /**
     * Returns logged user id
     *
     * @return logged user id
     */
    protected val loggedUserId: UUID?
        get() {
            if (jsonWebToken.subject != null) {
                return UUID.fromString(jsonWebToken.subject)
            }

            return null
        }

    /**
     * Checks if user has realm role
     *
     * @param realmRoles realm roles
     * @return response
     */
    protected fun hasRealmRole(vararg realmRoles: String): Boolean {
        if (jsonWebToken.subject == null) return false

        return realmRoles.any { securityContext.isUserInRole(it) }
    }

    /**
     * Constructs ok response
     *
     * @param entity payload
     * @param count total count
     * @return response
     */
    protected fun createOk(entity: Any?, count: Long): Response {
        return Response
            .status(Response.Status.OK)
            .header("X-Total-Count", count.toString())
            .header("Access-Control-Expose-Headers", "X-Total-Count")
            .entity(entity)
            .build()
    }

    /**
     * Constructs ok response
     *
     * @param entity payload
     * @return response
     */
    protected fun createOk(entity: Any?): Response {
        return Response
            .status(Response.Status.OK)
            .entity(entity)
            .build()
    }

    /**
     * Constructs created response
     *
     * @param entity payload
     * @return response
     */
    protected fun createCreated(entity: Any?): Response {
        return Response
            .status(Response.Status.CREATED)
            .entity(entity)
            .build()
    }

    /**
     * Constructs ok response
     *
     * @return response
     */
    protected fun createOk(): Response {
        return Response
            .status(Response.Status.OK)
            .build()
    }

    /**
     * Constructs no content response
     *
     * @param entity payload
     * @return response
     */
    protected fun createAccepted(entity: Any?): Response {
        return Response
            .status(Response.Status.ACCEPTED)
            .entity(entity)
            .build()
    }

    /**
     * Constructs no content response
     *
     * @return response
     */
    protected fun createNoContent(): Response {
        return Response
            .status(Response.Status.NO_CONTENT)
            .build()
    }

    /**
     * Constructs bad request response
     *
     * @param message message
     * @return response
     */
    protected fun createBadRequest(message: String): Response {
        return createError(Response.Status.BAD_REQUEST, message)
    }

    /**
     * Constructs not found response
     *
     * @param message message
     * @return response
     */
    protected fun createNotFound(message: String): Response {
        return createError(Response.Status.NOT_FOUND, message)
    }

    /**
     * Constructs not found response
     *
     * @return response
     */
    protected fun createNotFound(): Response {
        return Response
            .status(Response.Status.NOT_FOUND)
            .build()
    }
    /**
     * Constructs not found response
     *
     * @param message message
     * @return response
     */
    protected fun createConflict(message: String): Response {
        return createError(Response.Status.CONFLICT, message)
    }

    /**
     * Constructs not implemented response
     *
     * @param message message
     * @return response
     */
    protected fun createNotImplemented(message: String): Response {
        return createError(Response.Status.NOT_IMPLEMENTED, message)
    }

    /**
     * Constructs internal server error response
     *
     * @param message message
     * @return response
     */
    protected fun createInternalServerError(message: String): Response {
        return createError(Response.Status.INTERNAL_SERVER_ERROR, message)
    }

    /**
     * Constructs forbidden response
     *
     * @param message message
     * @return response
     */
    protected fun createForbidden(message: String): Response {
        return createError(Response.Status.FORBIDDEN, message)
    }

    /**
     * Constructs unauthorized response
     *
     * @param message message
     * @return response
     */
    protected fun createUnauthorized(message: String): Response {
        return createError(Response.Status.UNAUTHORIZED, message)
    }

    /**
     * Constructs an error response
     *
     * @param status status code
     * @param message message
     *
     * @return error response
     */
    private fun createError(status: Response.Status, message: String): Response {
        val entity = fi.metatavu.vp.api.model.Error(
            message = message,
            status = status.statusCode
        )

        return Response
            .status(status)
            .entity(entity)
            .build()
    }

    fun createNotFoundMessage(entity: String, id: UUID): String {
        return "$entity with id $id not found"
    }

    /**
     * Executes a block with coroutine scope
     *
     * @param requestTimeOut request timeout in milliseconds. Default is 10000
     * @param block block to execute
     * @return Uni
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> withCoroutineScope(requestTimeOut: Long = 10000L, block: suspend () -> T): Uni<T> {
        val context = Vertx.currentContext()
        val dispatcher = VertxCoroutineDispatcher(context)

        return CoroutineScope(context = dispatcher)
            .async {
                withTimeout(requestTimeOut) {
                    block()
                }
            }
            .asUni()
    }

    /**
     * Custom vertx coroutine dispatcher that keeps the context stable during the execution
     */
    private class VertxCoroutineDispatcher(private val vertxContext: io.vertx.core.Context): CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            vertxContext.runOnContext {
                block.run()
            }
        }
    }

    companion object {
        const val NOT_FOUND_MESSAGE = "Not found"
        const val UNAUTHORIZED = "Unauthorized"
        const val FORBIDDEN = "Forbidden"
        const val MISSING_REQUEST_BODY = "Missing request body"
        const val INVALID_REQUEST_BODY = "Invalid request body"
        const val INVALID_API_KEY = "Invalid API key"
        const val DRIVER_ENTITY = "Driver"
        const val EMPLOYEE_ENTITY = "Employee"
        const val WORK_TYPE = "Work type"
        const val TIME_ENTRY = "Time entry"

        const val DRIVER_ROLE = "driver"
        const val EMPLOYEE_ROLE = "employee"
        const val MANAGER_ROLE = "manager"
    }

}
