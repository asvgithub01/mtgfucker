package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HashScanEvidenceTest {
    @Test fun nameRequiresCanonicalAgreement() {
        assertTrue(HashScanEvidence.nameMatches("Giant Growth", listOf("giant growth")))
        assertFalse(HashScanEvidence.nameMatches("Giant Growth", listOf("Prey Upon")))
        assertFalse(HashScanEvidence.nameMatches("Giant Growth", emptyList()))
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
}
