package org.youschool.app.core.auth

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.youschool.app.domain.model.*

/** One server position per session: switching waits for every operation to finish cancelling. */
class PositionGate {
    private val mutex = Mutex()
    private val jobs = mutableSetOf<Job>()
    private val mutableState = MutableStateFlow(PositionState())
    val state = mutableState.asStateFlow()

    private class Lease(val context: SchoolContext, val revision: Long) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lease>
    }

    fun update(value: PositionState) { mutableState.value = value }

    fun invalidate(): PositionState {
        val previous = state.value
        update(previous.copy(active = null, busy = true, error = null, revision = previous.revision + 1))
        synchronized(jobs) { jobs.toList() }.forEach { it.cancel(CancellationException("Организация изменена")) }
        return previous
    }

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun <T> run(block: suspend (SchoolContext) -> T): T {
        currentCoroutineContext()[Lease]?.let { lease ->
            checkCurrent(lease)
            return block(lease.context)
        }
        val snapshot = state.value
        val context = snapshot.active ?: throw CancellationException("Сначала выберите организацию")
        return mutex.withLock {
            val lease = Lease(context, snapshot.revision)
            checkCurrent(lease)
            withContext(Dispatchers.IO + lease) {
                val job = currentCoroutineContext().job
                synchronized(jobs) { jobs.add(job) }
                try {
                    checkCurrent(lease)
                    block(context).also { currentCoroutineContext().ensureActive(); checkCurrent(lease) }
                } finally { synchronized(jobs) { jobs.remove(job) } }
            }
        }
    }

    private fun checkCurrent(lease: Lease) {
        val now = state.value
        if (now.active != lease.context || now.revision != lease.revision) {
            throw CancellationException("Организация изменена")
        }
    }
}
