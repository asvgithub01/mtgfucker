package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChroniclesSymbolPolicyTest {
    @Test fun whiteLegacySymbolAlsoConsidersChronicles() {
        assertEquals(
            setOf("LEG", "CHR"),
            ChroniclesSymbolPolicy.visualSetCodes(setOf("leg"), null, CardBorderColor.WHITE)
        )
    }

    @Test fun year1995WhiteCardConsidersChroniclesEvenWhenFooterSetWasMisread() {
        assertEquals(
            setOf("ICE", "CHR"),
            ChroniclesSymbolPolicy.visualSetCodes(setOf("ice"), 1995, CardBorderColor.WHITE)
        )
    }

    @Test fun yearAloneDoesNotLockAnOtherwiseUnknownWhiteCardToChronicles() {
        assertEquals(
            emptySet<String>(),
            ChroniclesSymbolPolicy.visualSetCodes(emptySet(), 1995, CardBorderColor.WHITE)
        )
    }

    @Test fun blackLegacyCardDoesNotBecomeChronicles() {
        assertFalse(ChroniclesSymbolPolicy.applies(setOf("LEG"), 1994, CardBorderColor.BLACK))
        assertEquals(
            setOf("LEG"),
            ChroniclesSymbolPolicy.visualSetCodes(setOf("LEG"), 1994, CardBorderColor.BLACK)
        )
    }

    @Test fun allChroniclesSourceSymbolsAreCovered() {
        assertTrue(listOf("ARN", "ATQ", "LEG", "DRK").all {
            ChroniclesSymbolPolicy.isRetainedSymbolSet(it)
        })
    }
}
