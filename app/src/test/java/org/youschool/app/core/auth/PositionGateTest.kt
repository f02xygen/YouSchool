package org.youschool.app.core.auth

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.youschool.app.domain.model.*

class PositionGateTest {
    private val schoolA = SchoolContext("account-a", 11)
    private val schoolB = SchoolContext("account-a", 22)
    private fun ready(gate: PositionGate, context: SchoolContext) {
        gate.update(gate.state.value.copy(active = context, initialized = true, busy = false))
    }

    @Test fun nestedOperationsUseTheSameLease() = runBlocking {
        val gate = PositionGate()
        ready(gate, schoolA)
        withTimeout(2000) {
            gate.run { outer -> gate.run { inner -> assertEquals(outer, inner) } }
        }
    }

    @Test fun switchCancelsOldLoadAndWaitsForItsCleanup() = runBlocking {
        val gate = PositionGate()
        ready(gate, schoolA)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var applied = false
        val old = launch {
            gate.run {
                started.complete(Unit)
                // Simulates a response arriving after cancellation from a non-cooperative transport.
                withContext(NonCancellable) { release.await() }
                "old school"
            }
            applied = true
        }
        started.await()
        gate.invalidate()
        assertNull(gate.state.value.active)
        val switched = async { gate.exclusive { ready(gate, schoolB) } }
        yield()
        assertFalse(switched.isCompleted)
        release.complete(Unit)
        withTimeout(2000) { old.join(); switched.await() }
        assertFalse(applied)
        assertEquals(schoolB, gate.state.value.active)
    }

    @Test fun queuedOldOperationCannotRunInNewSchool() = runBlocking {
        val gate = PositionGate()
        ready(gate, schoolA)
        val started = CompletableDeferred<Unit>()
        val old = launch { gate.run { started.complete(Unit); awaitCancellation() } }
        started.await()
        var ran = false
        val queued = launch(start = CoroutineStart.UNDISPATCHED) { gate.run { ran = true } }
        gate.invalidate()
        gate.exclusive { ready(gate, schoolB) }
        withTimeout(2000) { old.join(); queued.join() }
        assertFalse(ran)
    }

    @Test fun noSchoolRequestBeforeSelection() = runBlocking {
        val gate = PositionGate()
        var ran = false
        try { gate.run { ran = true }; fail("Expected cancellation") } catch (_: CancellationException) {}
        assertFalse(ran)
    }

    @Test fun samePositionIdInAnotherAccountIsDifferentContext() {
        assertNotEquals(schoolA, SchoolContext("account-b", schoolA.positionId))
        assertNotEquals(schoolA.key, SchoolContext("account-b", schoolA.positionId).key)
    }
}
