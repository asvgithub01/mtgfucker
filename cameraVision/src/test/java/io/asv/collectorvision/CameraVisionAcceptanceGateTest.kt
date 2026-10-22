package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class CameraVisionAcceptanceGateTest {
    @Test fun configurableFastCanUseOneReadingWithoutUnlockingSavedCard() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false); configureFast(.5f, 0f, 1) }
        val attempt = requireNotNull(gate.observe(true, hits("A", .51f, .50f)))
        assertTrue(gate.complete(attempt, true))
        gate.configureFast(.55f, .04f, 2)
        repeat(4) { assertNull(gate.observe(true, hits("A"))) }
    }
    @Test fun strongFastCannotBypassStricterSliders() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false); configureFast(.90f, .15f, 2) }
        repeat(3) { assertNull(gate.observe(true, hits("A", .85f, .65f))) }
        assertNull(gate.observe(true, hits("A", .95f, .85f)))
        val attempt = requireNotNull(gate.observe(true, hits("A", .95f, .70f)))
        assertEquals(.90f, attempt.scoreThreshold, .0001f)
        assertEquals(.15f, attempt.marginThreshold, .0001f)
    }

    private fun hits(id: String = "A", score: Float = .85f, runner: Float = .65f) = listOf(Hit(id,score),Hit("other",runner))
    private fun accept(gate: CameraVisionAcceptanceGate, id: String="A"): CameraVisionAcceptanceGate.Attempt {
        assertNull(gate.observe(true,hits(id)))
        assertNull(gate.observe(true,hits(id)))
        return requireNotNull(gate.observe(true,hits(id)))
    }
    @Test fun threeConsecutiveReadingsRequired() {
        val gate=CameraVisionAcceptanceGate()
        val attempt=accept(gate)
        assertEquals("A",attempt.cardId)
        assertTrue(gate.isPending())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun unreliableScoresMarginsAndUnknownRunnerUpRejected() {
        val gate=CameraVisionAcceptanceGate()
        listOf(hits(score=.69f),hits(runner=.80f),hits(score=Float.NaN),listOf(Hit("A",.99f)),emptyList()).forEach { candidate ->
            repeat(5) { assertNull(gate.observe(true,candidate)) }
        }
        accept(gate)
    }
    @Test fun heldCardNeverRepeatsAfterSuccessfulSave() {
        val gate=CameraVisionAcceptanceGate();val attempt=accept(gate)
        assertTrue(gate.complete(attempt,true))
        repeat(10) { assertNull(gate.observe(true,hits())) }
        gate.resetStability()
        repeat(10) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.isSaved("A"))
    }
    @Test fun transientOtherCandidateDoesNotUnlockHeldCard() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        assertNull(gate.observe(true,hits("B")))
        assertNull(gate.observe(true,hits("B")))
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun oneAbsentOrUnreliablePresentFrameCannotRearm() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList())
        gate.observe(true,hits(score=.2f))
        gate.observe(false,emptyList())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun twoAbsentFramesPermitNextCopy() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        accept(gate)
    }
    @Test fun differentSuccessfullySavedCardPermitsReturningToPrevious() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.complete(accept(gate,"B"),true)
        assertEquals("A",accept(gate).cardId)
    }
    @Test fun failedDifferentSaveDoesNotUnlockPreviousCard() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.complete(accept(gate,"B"),false)
        repeat(5) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.isSaved("A"))
    }
    @Test fun failureRequiresRemovalBeforeRetryAndDuplicateCallbackIsIgnored() {
        val gate=CameraVisionAcceptanceGate();val first=accept(gate)
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
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),false)
        gate.complete(accept(gate,"B"),false)
        repeat(5) { assertNull(gate.observe(true,hits())) }
        repeat(5) { assertNull(gate.observe(true,hits("B"))) }
        gate.complete(accept(gate,"C"),true)
        accept(gate)
    }
    @Test fun lateFailureAfterRemovalDoesNotBlockNewPresentation() {
        val gate=CameraVisionAcceptanceGate();val first=accept(gate)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        gate.complete(first,false)
        accept(gate)
    }
    @Test fun removalWhileSavePendingRearmsWithoutAllowingParallelSave() {
        val gate=CameraVisionAcceptanceGate();val first=accept(gate)
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        repeat(4) { assertNull(gate.observe(true,hits())) }
        assertTrue(gate.complete(first,true))
        accept(gate)
    }
    @Test fun pauseBreaksAbsenceSequenceButRetainsSavedLock() {
        val gate=CameraVisionAcceptanceGate();gate.complete(accept(gate),true)
        gate.observe(false,emptyList());gate.resetStability();gate.observe(false,emptyList())
        repeat(5) { assertNull(gate.observe(true,hits())) }
    }
    @Test fun fastRejectsUnknownRunnerAndNearTiesButAcceptsStrongImmediately() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        assertNull(gate.observe(true, listOf(Hit("A", .95f))))
        assertNull(gate.observe(true, hits("A", .80f, .79f)))
        assertNotNull(gate.observe(true, hits("A", .85f, .70f)))
    }
    @Test fun fastLowerThresholdStillRejectsWeakOrAmbiguousRepeatedCandidates() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        repeat(3) { assertNull(gate.observe(true, hits("A", .549f, .40f))) }
        repeat(3) { assertNull(gate.observe(true, hits("A", .55f, .53f))) }
        assertNull(gate.observe(true, hits("A", .55f, .50f)))
        assertNotNull(gate.observe(true, hits("A", .55f, .50f)))
    }
    @Test fun fastModerateCandidateRequiresTwoConsistentReadings() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        assertNull(gate.observe(true, hits("A", .55f, .50f)))
        assertNull(gate.observe(true, hits("B", .55f, .50f)))
        val attempt = requireNotNull(gate.observe(true, hits("B", .55f, .50f)))
        assertEquals(2, attempt.readings)
        assertEquals(.55f, attempt.scoreThreshold, .0001f)
        assertEquals(.04f, attempt.marginThreshold, .0001f)
    }
    @Test fun fastStillRejectsAbsentInvalidAndBelowThresholdCandidates() {
        val gate = CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        assertNull(gate.observe(false, hits()))
        listOf(emptyList(), listOf(Hit("", .9f)), hits(score = Float.NaN),
            hits(score = Float.POSITIVE_INFINITY), hits(score = .499f)).forEach {
            assertNull(gate.observe(true, it))
        }
    }
    @Test fun switchingPreservesPendingSavedAndFailedLocks() {
        val gate = CameraVisionAcceptanceGate()
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
        val gate = CameraVisionAcceptanceGate()
        gate.observe(true, hits()); gate.observe(true, hits())
        gate.setFiltersEnabled(false); gate.setFiltersEnabled(true)
        accept(gate)
    }
    @Test fun acceptanceExplainsFastAndFilteredDecision() {
        val gate = CameraVisionAcceptanceGate()
        val filtered = accept(gate)
        assertTrue(filtered.filtered)
        assertEquals(3, filtered.readings)
        assertEquals(.70f, filtered.scoreThreshold, .0001f)
        assertEquals(.08f, filtered.marginThreshold, .0001f)
        gate.complete(filtered, true)
        gate.setFiltersEnabled(false)
        val fast = requireNotNull(gate.observe(true, hits("B", .85f, .70f)))
        assertFalse(fast.filtered)
        assertEquals(1, fast.readings)
        assertEquals(.80f, fast.scoreThreshold, .0001f)
        assertEquals(.08f, fast.marginThreshold, .0001f)
    }
}
