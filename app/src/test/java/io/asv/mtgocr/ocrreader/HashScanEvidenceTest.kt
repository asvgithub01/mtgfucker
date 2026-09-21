package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HashScanEvidenceTest {
    @Test fun nameRequiresCanonicalAgreement() {
        assertTrue(HashScanEvidence.nameMatches("Giant Growth", listOf("giant growth")))
        assertTrue(HashScanEvidence.nameMatches("Fire // Ice", listOf("Fire")))
        assertFalse(HashScanEvidence.nameMatches("Giant Growth", listOf("Prey Upon")))
        assertFalse(HashScanEvidence.nameMatches("Giant Growth", emptyList()))
    }
    @Test fun ocrCanSelectALowerRankedHashCandidate() {
        assertEquals(
            1,
            HashScanEvidence.firstMatchingCandidateIndex(
                listOf("Prey Upon", "Giant Growth", "Titanic Growth"),
                listOf("Giant Growth")
            )
        )
        assertNull(HashScanEvidence.firstMatchingCandidateIndex(
            listOf("Prey Upon", "Titanic Growth"), listOf("Giant Growth")
        ))
    }
    @Test fun missingOrSingleSymbolIsNotReliable() {
        assertFalse(HashScanEvidence.reliableSymbol(emptyList()))
        assertFalse(HashScanEvidence.reliableSymbol(listOf(.1)))
        assertFalse(HashScanEvidence.reliableSymbol(listOf(.1, Double.NaN)))
    }
    @Test fun symbolRequiresAbsoluteScoreAndMargin() {
        assertFalse(HashScanEvidence.reliableSymbol(listOf(.2, .21)))
        assertFalse(HashScanEvidence.reliableSymbol(listOf(.6, .8)))
        assertTrue(HashScanEvidence.reliableSymbol(listOf(.4, .2)))
    }

    @Test fun exactEditionRequiresNameSetAndCollectorAgreement() {
        val m14 = edition("M14", "177", "m14-177")
        val promo = edition("FNM", "177", "fnm-177")
        val guess = PrintingMetadataGuess("M14 177", "177", "M14", null, 2013, listOf("M14"))

        assertEquals(
            m14,
            HashScanEvidence.resolveEdition(
                "Giant Growth",
                listOf("Giant Growth"),
                guess,
                listOf(m14, promo)
            )
        )
        assertNull(HashScanEvidence.resolveEdition("Giant Growth", listOf("Prey Upon"), guess, listOf(m14)))
        assertNull(HashScanEvidence.resolveEdition("Giant Growth", listOf("Giant Growth"), null, listOf(m14)))
        val lowerRankedOnly = guess.copy(setCode = "FNM", setCodeCandidates = listOf("FNM", "M14"))
        assertNull(HashScanEvidence.resolveEdition(
            "Giant Growth", listOf("Giant Growth"), lowerRankedOnly, listOf(m14)
        ))
    }

    @Test fun duplicateExactMetadataStaysAmbiguous() {
        val first = edition("ABC", "12", "first")
        val second = edition("ABC", "12", "second")
        val guess = PrintingMetadataGuess("ABC 12", "12", "ABC", null, 2020, listOf("ABC"))

        assertNull(HashScanEvidence.resolveEdition(
            "Card", listOf("Card"), guess, listOf(first, second)
        ))
    }

    private fun edition(code: String, collector: String, uuid: String) =
        HashScanAnalysis.Edition(code, code, 2020, collector, uuid)
}
