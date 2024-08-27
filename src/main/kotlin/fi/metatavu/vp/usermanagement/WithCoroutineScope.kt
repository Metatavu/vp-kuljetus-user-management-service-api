package fi.metatavu.vp.usermanagement

import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.asUni
import io.vertx.core.Vertx
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * Base class for classes that need to execute code with coroutine scope in correct vertx context
 */
open class WithCoroutineScope {

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
}