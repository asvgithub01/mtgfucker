package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class TripleLanguageBadgeTest {
    @Test fun unknownDoesNotInventEnglish() {
        for(code in listOf(null,""," ","und"," UND ")) assertEquals("?",TripleLanguageBadge.label(code))
    }
    @Test fun confirmedUsesSharedFlagAndNormalizedCode() {
        for(code in listOf("en","es","pt","ja","zht"))
            assertEquals(CardLanguageFlag.label(code),TripleLanguageBadge.label(" ${code.uppercase()} "))
    }
}
