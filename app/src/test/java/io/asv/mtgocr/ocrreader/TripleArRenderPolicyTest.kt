package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleArRenderPolicyTest {
    @Test fun singleModePreservesPrimaryOnly() {
        assertEquals(listOf(7),TripleArRenderPolicy.visible(listOf(7,3,2),false))
        assertTrue(TripleArRenderPolicy.visible(emptyList<Int>(),true).isEmpty())
    }
    @Test fun multiModeIsBoundedAndKeepsPrimary() {
        val cards=(0..30).toList()
        assertEquals((0..19).toList(),TripleArRenderPolicy.visible(cards,true))
        assertEquals(listOf(2,4),TripleArRenderPolicy.visible(listOf(2,4),true))
    }
}
