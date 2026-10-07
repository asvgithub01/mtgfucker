package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleOcrPrintingPolicyTest {
    @Test fun incompleteCatalogCannotProveSingleSet() {
        assertFalse(TripleOcrPrintingPolicy.singleSet(listOf("ISD"),false))
        assertFalse(TripleOcrPrintingPolicy.singleSet(emptyList(),true))
        assertFalse(TripleOcrPrintingPolicy.singleSet(listOf(""),true))
    }
    @Test fun variantsAndMultipleSetsAreDifferent() {
        assertTrue(TripleOcrPrintingPolicy.singleSet(listOf("ISD","isd"),true))
        assertFalse(TripleOcrPrintingPolicy.singleSet(listOf("ISD","V17"),true))
    }
    @Test fun capacityIncludesPackageAndPendingReferences() {
        assertTrue(TripleOcrPrintingPolicy.hasCapacity(574,424,1))
        assertFalse(TripleOcrPrintingPolicy.hasCapacity(574,425,1))
        assertFalse(TripleOcrPrintingPolicy.hasCapacity(1000,0,0))
        assertFalse(TripleOcrPrintingPolicy.hasCapacity(0,999,1))
    }
}
