package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReliableReviewPolicyTest {
    @Test fun oldPermissiveSkycatAcceptanceIsNeverAutoApplied() {
        assertFalse(ReliableReviewPolicy.artReliable(.5507,.0104))
        assertFalse(ReliableReviewPolicy.artReliable(.5872,.0500))
    }
    @Test fun requiresBothThresholdsAndFiniteMeasurements() {
        assertTrue(ReliableReviewPolicy.artReliable(.70,.08))
        assertFalse(ReliableReviewPolicy.artReliable(.69,.2))
        assertFalse(ReliableReviewPolicy.artReliable(.95,.079))
        assertFalse(ReliableReviewPolicy.artReliable(Double.NaN,.2))
        assertFalse(ReliableReviewPolicy.artReliable(.9,null))
    }
    @Test fun ocrAndArtMustAgreeEvenWhenBothNamesAreValid() {
        assertFalse(ReliableReviewPolicy.sameIdentity("Serra Sphinx","Petra Sphinx"))
        assertFalse(ReliableReviewPolicy.sameIdentity("","Petra Sphinx"))
        assertTrue(ReliableReviewPolicy.sameIdentity(" Petra Sphinx ","petra sphinx"))
    }
    @Test fun neverInventsOrFallsBackToEnglishWithAmbiguousLanguage() {
        assertNull(ReliableReviewPolicy.language(listOf("en","es"),null))
        assertNull(ReliableReviewPolicy.language(listOf("en","es"),"pt"))
        assertEquals("es",ReliableReviewPolicy.language(listOf("en","es"),"es"))
        assertEquals("ja",ReliableReviewPolicy.language(listOf("ja","ja"),null))
        assertNull(ReliableReviewPolicy.language(emptyList(),"en"))
    }
}
