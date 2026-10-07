package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class ScannerScopeTest {
    @Test fun pausesOnlyAfterThirtySeconds() {
        assertFalse(ScannerIdlePolicy.shouldPause(30_999, 1000))
        assertTrue(ScannerIdlePolicy.shouldPause(31_000, 1000))
        assertFalse(ScannerIdlePolicy.shouldPause(31_000, 30_000))
    }
    @Test fun continuousArDoesNotAutoPause() {
        assertFalse(ScannerIdlePolicy.shouldPause(120_000,0,enabled=false))
        assertTrue(ScannerIdlePolicy.shouldPause(120_000,0,enabled=true))
    }
    @Test fun scopeKeepsExactRowsAndFailsClosed() {
        val ids = listOf("a", "b", "c")
        assertNull(VisualSearchScope.rows(ids, null))
        assertArrayEquals(intArrayOf(0,2), VisualSearchScope.rows(ids,setOf("c","a")))
        assertArrayEquals(intArrayOf(), VisualSearchScope.rows(ids,emptySet()))
        assertArrayEquals(intArrayOf(), VisualSearchScope.rows(ids,setOf("missing")))
    }
}
