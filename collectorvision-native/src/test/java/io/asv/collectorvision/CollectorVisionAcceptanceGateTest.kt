package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class CollectorVisionAcceptanceGateTest {
    private fun hits(id: String = "A", score: Float = .85f, runner: Float = .65f) = listOf(Hit(id,score),Hit("other",runner))
    private fun accept(gate: CollectorVisionAcceptanceGate, id: String="A"): CollectorVisionAcceptanceGate.Attempt {
        assertNull(gate.observe(true,hits(id)))
        assertNull(gate.observe(true,hits(id)))
        return requireNotNull(gate.observe(true,hits(id)))
    }
    @Test fun threeConsecutiveReadingsRequired() {
        val gate=CollectorVisionAcceptanceGate()
        val attempt=accept(gate)
        assertEquals("A",attempt.cardId)
        assertTrue(gate.isPending())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun unreliableScoresMarginsAndUnknownRunnerUpRejected() {
        val gate=CollectorVisionAcceptanceGate()
        listOf(hits(score=.69f),hits(runner=.80f),hits(score=Float.NaN),listOf(Hit("A",.99f)),emptyList()).forEach { candidate ->
            repeat(5) { assertNull(gate.observe(true,candidate)) }
        }
        accept(gate)
    }
    @Test fun heldCardNeverRepeatsAfterSuccessfulSave() {
        val gate=CollectorVisionAcceptanceGate();val attempt=accept(gate)
        assertTrue(gate.complete(attempt,true))
        repeat(10) { assertNull(gate.observe(true,hits())) }
        gate.resetStability()
        repeat(10) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.isSaved("A"))
    }
    @Test fun transientOtherCandidateDoesNotUnlockHeldCard() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        assertNull(gate.observe(true,hits("B")))
        assertNull(gate.observe(true,hits("B")))
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun oneAbsentOrUnreliablePresentFrameCannotRearm() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList())
        gate.observe(true,hits(score=.2f))
        gate.observe(false,emptyList())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun twoAbsentFramesPermitNextCopy() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        accept(gate)
    }
    @Test fun differentSuccessfullySavedCardPermitsReturningToPrevious() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.complete(accept(gate,"B"),true)
        assertEquals("A",accept(gate).cardId)
    }
    @Test fun failedDifferentSaveDoesNotUnlockPreviousCard() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.complete(accept(gate,"B"),false)
        repeat(5) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.isSaved("A"))
    }
    @Test fun failureRequiresRemovalBeforeRetryAndDuplicateCallbackIsIgnored() {
        val gate=CollectorVisionAcceptanceGate();val first=accept(gate)
        assertTrue(gate.complete(first,false))
        repeat(8) { assertNull(gate.observe(true,hits())) }
        gate.resetStability()
        repeat(3) { assertNull(gate.observe(true,hits())) }
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        val second=accept(gate)
        assertFalse(gate.complete(first,true))
        assertTrue(gate.isPending())
        assertTrue(gate.complete(second,true))
        assertFalse(gate.complete(second,true))
    }
    @Test fun failedIdsRemainBlockedUntilAnotherCardIsSuccessfullySaved() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),false)
        gate.complete(accept(gate,"B"),false)
        repeat(5) { assertNull(gate.observe(true,hits())) }
        repeat(5) { assertNull(gate.observe(true,hits("B"))) }
        gate.complete(accept(gate,"C"),true)
        accept(gate)
    }
    @Test fun lateFailureAfterRemovalDoesNotBlockNewPresentation() {
        val gate=CollectorVisionAcceptanceGate();val first=accept(gate)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        gate.complete(first,false)
        accept(gate)
    }
    @Test fun removalWhileSavePendingRearmsWithoutAllowingParallelSave() {
        val gate=CollectorVisionAcceptanceGate();val first=accept(gate)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        repeat(4) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.complete(first,true))
        accept(gate)
    }
    @Test fun pauseBreaksAbsenceSequenceButRetainsSavedLock() {
        val gate=CollectorVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList());gate.resetStability();gate.observe(false,emptyList())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun fastAcceptsFirstVisibleCandidateWithoutRunnerOrMargin() {
        val gate = CollectorVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        val first = requireNotNull(gate.observe(true, listOf(Hit("A", .50f))))
        gate.complete(first, true)
        assertNotNull(gate.observe(true, hits("B", .60f, .599f)))
    }
    @Test fun fastStillRejectsAbsentInvalidAndBelowThresholdCandidates() {
        val gate = CollectorVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        assertNull(gate.observe(false, hits()))
        listOf(emptyList(), listOf(Hit("", .9f)), hits(score = Float.NaN),
            hits(score = Float.POSITIVE_INFINITY), hits(score = .499f)).forEach {
            assertNull(gate.observe(true, it))
        }
    }
    @Test fun switchingPreservesPendingSavedAndFailedLocks() {
        val gate = CollectorVisionAcceptanceGate()
        val first = accept(gate)
        gate.setFiltersEnabled(false)
        assertNull(gate.observe(true, hits("B")))
        assertTrue(gate.complete(first, true))
        assertNull(gate.observe(true, hits()))
        val failed = requireNotNull(gate.observe(true, hits("B")))
        gate.complete(failed, false)
        gate.setFiltersEnabled(true); gate.setFiltersEnabled(false)
        assertNull(gate.observe(true, hits("B")))
        assertNull(gate.observe(true, hits()))
        gate.observe(false, emptyList()); gate.observe(false, emptyList())
        assertNotNull(gate.observe(true, hits()))
    }
    @Test fun switchingBackToFiltersRequiresFreshConsecutiveReadings() {
        val gate = CollectorVisionAcceptanceGate()
        gate.observe(true, hits()); gate.observe(true, hits())
        gate.setFiltersEnabled(false); gate.setFiltersEnabled(true)
        accept(gate)
    }
}
