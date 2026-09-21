package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HashScanCompletionTest {
    @Test fun hashOnlyPublishesWithoutWaitingForDisabledOcr() {
        var calls = 0
        val barrier = HashScanCompletion(false) { calls++ }
        barrier.finish("title")
        assertEquals(0, calls)
        barrier.finish("visual")
        barrier.finish("visual")
        assertEquals(1, calls)
    }
    @Test fun waitsForAllEnabledStagesInAnyOrder() {
        for (order in listOf(listOf("visual", "title", "printing"), listOf("printing", "visual", "title"), listOf("title", "printing", "visual"))) {
            var calls = 0
            val barrier = HashScanCompletion(true) { calls++ }
            barrier.finish(order[0])
            barrier.finish(order[0])
            barrier.finish(order[1])
            assertEquals(0, calls)
            barrier.finish(order[2])
            assertEquals(1, calls)
        }
    }
    @Test fun languageCheckDelaysPublicationUntilItsCallback() {
        var calls = 0
        val barrier = HashScanCompletion(ocr = false, language = true) { calls++ }
        barrier.finish("visual")
        assertEquals(0, calls)
        barrier.finish("language")
        assertEquals(1, calls)
    }
    @Test fun concurrentCallbacksCompleteExactlyOnce() {
        val count = AtomicInteger()
        val barrier = HashScanCompletion(true) { count.incrementAndGet() }
        val executor = Executors.newFixedThreadPool(3)
        repeat(100) { for (stage in listOf("title", "printing", "visual")) executor.execute { barrier.finish(stage) } }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(1, count.get())
    }
}
