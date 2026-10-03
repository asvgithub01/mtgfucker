package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ReliableReviewPolicyTest {
    @Test fun zombieMasterLebConfirmsOnlyAvailableEnglishWithCorroboratedIdentity() {
        assertEquals("en",ReliableReviewPolicy.edscanLanguage(true,setOf("en"),listOf("en"),"en"))
        // A unique catalog language does not need a second language classifier vote.
        assertEquals("en",ReliableReviewPolicy.edscanLanguage(true,emptySet(),listOf("en"),null))
    }
    @Test fun italianOcrConflictsWithEnglishOnlyEdscanPrinting() {
        assertNull(ReliableReviewPolicy.edscanLanguage(true,setOf("en"),listOf("en"),"it"))
        assertNull(ReliableReviewPolicy.edscanLanguage(true,emptySet(),listOf("en"),"it"))
        assertNull(ReliableReviewPolicy.edscanLanguage(false,emptySet(),listOf("en"),null))
    }
    @Test fun freejamEdscanUsesStoredEditionAndOcrEnglishWithoutNewArtGate() {
        assertFalse(ReliableReviewPolicy.requiresArt("edscan"))
        assertFalse(ReliableReviewPolicy.requiresArt("")) // Legacy EdScan metadata.
        assertTrue(ReliableReviewPolicy.requiresArt("ocr"))
        assertEquals("en",ReliableReviewPolicy.edscanLanguage(true,setOf("en"),listOf("en","es","fr"),"en"))
    }
    @Test fun falsePositiveRemainsManualEvenWithAvailableLanguage() {
        assertNull(ReliableReviewPolicy.edscanLanguage(false,emptySet(),listOf("en","es"),"es"))
    }
    @Test fun edscanLanguageNotInSelectedEditionRemainsConflict() {
        assertNull(ReliableReviewPolicy.edscanLanguage(true,setOf("es"),listOf("en"),"es"))
    }
    @Test fun localizedNamesDisambiguateLanguageWithoutInventingIt() {
        assertEquals("es",ReliableReviewPolicy.edscanLanguage(false,setOf("es"),listOf("en","es"),"es"))
        assertNull(ReliableReviewPolicy.edscanLanguage(true,setOf("es","pt"),listOf("es","pt"),null))
    }
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

    @Test fun localizedTitleDeterminesOnlyUnambiguousLanguage() {
        val titles=listOf(ReliableReviewPolicy.LocalTitle("en","Humble Defector"),ReliableReviewPolicy.LocalTitle("es","Desertor humilde"))
        val matched=ReliableReviewPolicy.titleLanguages(listOf("DESERTOR HUMILDE"),titles)
        assertEquals(setOf("es"),matched)
        assertEquals("es",ReliableReviewPolicy.resolveLanguage(listOf("en","es"),matched,null))
        assertNull(ReliableReviewPolicy.resolveLanguage(listOf("en","es"),matched,"en"))
    }
    @Test fun sharedLocalizedNameCannotDecideBetweenSpanishAndPortuguese() {
        val matched=ReliableReviewPolicy.titleLanguages(listOf("Alias"),listOf(
            ReliableReviewPolicy.LocalTitle("es","Alias"),ReliableReviewPolicy.LocalTitle("pt","Alias")))
        assertNull(ReliableReviewPolicy.resolveLanguage(listOf("es","pt"),matched,null))
        assertEquals("pt",ReliableReviewPolicy.resolveLanguage(listOf("es","pt"),matched,"pt"))
    }
    @Test fun unsupportedDetectedLanguageOverridesSingleAvailableLanguage() {
        assertNull(ReliableReviewPolicy.language(listOf("en"),"es"))
        assertNull(ReliableReviewPolicy.resolveLanguage(listOf("en"),setOf("en"),"es"))
    }
}
