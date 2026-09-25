package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test

class RulesArtworkEvidenceTest {
    private fun art(p: Int = 4, d: Int = 4, id: String = "a", name: String = "Card") =
        RulesAutoAddPolicy.Artwork(id, name, p, d, emptyList())
    @Test fun strongArtworkDoesNotNeedAnEditionOrOcrToBeVisuallyStrong() {
        assertEquals("STRONG_ART", RulesArtworkEvidence.reason(listOf(art(), art(12,24,"b")), emptyList()))
    }
    @Test fun weakAndMarginAreDifferentReasons() {
        assertEquals("WEAK_ART", RulesArtworkEvidence.reason(listOf(art(14)), emptyList()))
        assertEquals("ART_MARGIN", RulesArtworkEvidence.reason(listOf(art(),art(6,8,"b")), emptyList()))
        assertEquals("ART_MARGIN", RulesArtworkEvidence.reason(listOf(art()), emptyList()))
    }
    @Test fun independentOcrConflictIsPreserved() {
        assertEquals("OCR_CONFLICT", RulesArtworkEvidence.reason(listOf(art(),art(12,24,"b")), listOf("Other")))
    }
    @Test fun duplicatesOfSameArtDoNotCreateCompetitors() {
        assertEquals("ART_MARGIN", RulesArtworkEvidence.reason(listOf(art(),art()), emptyList()))
        assertEquals("NO_ART", RulesArtworkEvidence.reason(emptyList(), emptyList()))
    }
}
