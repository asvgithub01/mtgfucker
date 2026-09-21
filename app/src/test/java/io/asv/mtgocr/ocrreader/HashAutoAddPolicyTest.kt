package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashAutoAddPolicyTest {
    @Test fun acceptsOneExactPrinting() {
        val target = HashAutoAddTarget("Fleshbag Marauder", "ori-098")
        assertEquals(target, HashAutoAddPolicy.uniqueTarget(listOf(target)))
    }

    @Test fun collapsesRepeatedEvidenceForTheSamePrinting() {
        val target = HashAutoAddTarget("Fleshbag Marauder", "ori-098")
        assertEquals(target, HashAutoAddPolicy.uniqueTarget(listOf(target, target)))
    }

    @Test fun rejectsAmbiguousPrintings() {
        assertNull(HashAutoAddPolicy.uniqueTarget(listOf(
            HashAutoAddTarget("Card", "first"),
            HashAutoAddTarget("Card", "second")
        )))
    }
}
