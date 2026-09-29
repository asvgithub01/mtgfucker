package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReviewTitlePolicyTest {
    @Test fun conflictingKnownTitleRequiresHumanReview() {
        assertEquals("conflict",ReviewTitlePolicy.compare("Call for Unity",listOf("Disenchant")))
    }
    @Test fun missingAmbiguousAndUnknownAreNotConflicts() {
        assertEquals("inconclusive",ReviewTitlePolicy.compare("",listOf("Disenchant")))
        assertEquals("inconclusive",ReviewTitlePolicy.compare("Disenchant",emptyList()))
        assertEquals("inconclusive",ReviewTitlePolicy.compare("Disenchant",listOf("Disenchant","Call for Unity")))
    }
    @Test fun canonicalNamesCanCorroborateButNotProvePrinting() {
        assertEquals("consistent",ReviewTitlePolicy.compare("Disenchant",listOf("disenchant")))
    }
}
