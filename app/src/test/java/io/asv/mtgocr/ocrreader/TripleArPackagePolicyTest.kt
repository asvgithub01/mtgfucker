package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleArPackagePolicyTest {
    private val labels=listOf("Desperate Futurescribe · FRA #129","Sol Ring · FRC #21","Extra · SPG #1")
    @Test fun compiledPackageCanCoverTwoSelectedSets() {
        assertTrue(TripleArPackagePolicy.covers(setOf("FRA","FRC"),labels))
    }
    @Test fun partialCoverageMustNotDisableFallbackForMissingSet() {
        assertFalse(TripleArPackagePolicy.covers(setOf("FRA","XLN"),labels))
    }
    @Test fun emptyScopeMustNotSelectArbitraryPackage() {
        assertFalse(TripleArPackagePolicy.covers(emptySet(),labels))
    }
    @Test fun malformedLabelsDoNotPretendToCoverRequestedSet() {
        assertFalse(TripleArPackagePolicy.covers(setOf("FRA"),listOf("FRA")))
    }
    @Test fun budgetKeepsFastPathAndGivesCameraHeadroomUnderLoad() {
        assertEquals(150L,TripleScanPolicy.frameRestMs(250))
        assertEquals(650L,TripleScanPolicy.frameRestMs(1400))
        assertEquals(650L,TripleScanPolicy.frameRestMs(10000))
    }
    @Test fun budgetDoesNotInvalidateConsecutiveNormalFrames() {
        val confirmations=TripleVisualConfirmation()
        confirmations.observe("card",.75f,.4f,0)
        assertTrue(confirmations.observe("card",.75f,.4f,1799+TripleScanPolicy.frameRestMs(1799)).reliable)
    }
}
