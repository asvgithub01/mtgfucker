package io.asv.collectorvision
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class EdScanInferencePriorityTest {
    @Test fun backgroundWaitsUntilAllLiveOwnersRelease() = runBlocking {
        val first=Any(); val second=Any()
        try {
            EdScanInferencePriority.acquire(first); EdScanInferencePriority.acquire(second)
            val waiting=async(start=CoroutineStart.UNDISPATCHED) { EdScanInferencePriority.awaitIdle() }
            assertFalse(waiting.isCompleted)
            EdScanInferencePriority.release(first); yield()
            assertFalse(waiting.isCompleted)
            EdScanInferencePriority.release(second)
            withTimeout(1000) { waiting.await() }
        } finally { EdScanInferencePriority.release(first); EdScanInferencePriority.release(second) }
    }
    @Test fun repeatedCameraStartDoesNotLeakOwnership() = runBlocking {
        val owner=Any()
        try {
            EdScanInferencePriority.acquire(owner); EdScanInferencePriority.acquire(owner)
            EdScanInferencePriority.release(owner)
            withTimeout(1000) { EdScanInferencePriority.awaitIdle() }
        } finally { EdScanInferencePriority.release(owner) }
    }
}
