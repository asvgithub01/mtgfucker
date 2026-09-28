package io.asv.collectorvision
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class EdScanInferencePriorityTest {
    @Test fun liveCameraDefersWorkAndReleaseResumesIt() = runBlocking {
        val owner=Any(); EdScanInferencePriority.acquire(owner)
        val waiting=async { EdScanInferencePriority.awaitIdle(); true }
        try { yield(); assertFalse(waiting.isCompleted) }
        finally { EdScanInferencePriority.release(owner) }
        assertTrue(withTimeout(1000) { waiting.await() })
    }
    @Test fun duplicateLifecycleAndMultipleOwnersAreSafe() = runBlocking {
        val a=Any(); val b=Any()
        EdScanInferencePriority.acquire(a); EdScanInferencePriority.acquire(a); EdScanInferencePriority.acquire(b)
        val waiting=async { EdScanInferencePriority.awaitIdle() }
        try {
            EdScanInferencePriority.release(a); EdScanInferencePriority.release(a); yield(); assertFalse(waiting.isCompleted)
        } finally { EdScanInferencePriority.release(a); EdScanInferencePriority.release(b) }
        withTimeout(1000) { waiting.await() }
    }
    @Test fun waitingWorkCanBeCancelledWithoutReleasingCameraOwner() = runBlocking {
        val owner = Any()
        EdScanInferencePriority.acquire(owner)
        try {
            val cancelled = launch { EdScanInferencePriority.awaitIdle(); error("Camera still active") }
            yield()
            cancelled.cancelAndJoin()
            val next = async { EdScanInferencePriority.awaitIdle() }
            yield()
            assertFalse(next.isCompleted)
            EdScanInferencePriority.release(owner)
            withTimeout(1000) { next.await() }
        } finally { EdScanInferencePriority.release(owner) }
    }
}
