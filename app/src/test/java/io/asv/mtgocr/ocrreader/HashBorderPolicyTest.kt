package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class HashBorderPolicyTest {
    @Test fun exposesOnlyWhiteAndBlackDuringTheDiagnosticPhase() {
        assertEquals(HashBorderVerdict.WHITE, HashBorderPolicy.verdict(CardBorderColor.WHITE))
        assertEquals(HashBorderVerdict.BLACK, HashBorderPolicy.verdict(CardBorderColor.BLACK))
        assertEquals(HashBorderVerdict.UNRESOLVED, HashBorderPolicy.verdict(CardBorderColor.GOLD))
        assertEquals(HashBorderVerdict.UNRESOLVED, HashBorderPolicy.verdict(CardBorderColor.SILVER))
        assertEquals(HashBorderVerdict.UNRESOLVED, HashBorderPolicy.verdict(CardBorderColor.MIXED))
        assertEquals(HashBorderVerdict.UNRESOLVED, HashBorderPolicy.verdict(CardBorderColor.FULL_ART))
        assertEquals(HashBorderVerdict.UNRESOLVED, HashBorderPolicy.verdict(null))
    }
}
