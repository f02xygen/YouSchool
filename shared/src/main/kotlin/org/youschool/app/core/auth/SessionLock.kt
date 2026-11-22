package org.youschool.app.core.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Keep a school switch atomic with all requests and cache/queue writes. */
class SessionLock {
    private val mutex = Mutex()
    private class Owner(val lock: SessionLock) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Owner>
    }
    suspend fun <T> run(block: suspend () -> T): T {
        if (coroutineContext[Owner]?.lock === this) return block()
        return mutex.withLock { withContext(Owner(this)) { block() } }
    }
}
