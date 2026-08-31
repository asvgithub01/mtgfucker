package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFormatRulesTest {
    @Test
    fun constructedFormatsExposeMainAndSideboardLimits() {
        val modern = DeckFormatRules.byId("modern")
        val pauper = DeckFormatRules.byId("pauper")
        val legacy = DeckFormatRules.byId("legacy")

        listOf(modern, pauper, legacy).forEach {
            assertEquals(60, it.minimumMain)
            assertEquals(15, it.maximumSideboard)
            assertEquals(4, it.maximumCopies)
        }
    }

    @Test
    fun commanderIsExactHundredSingletonWithoutSideboard() {
        val commander = DeckFormatRules.byId("commander")

        assertEquals(100, commander.minimumMain)
        assertEquals(0, commander.maximumSideboard)
        assertEquals(1, commander.maximumCopies)
        assertTrue(commander.summary.contains("99 + comandante"))
    }
}
