package io.asv.mtgocr.ocrreader

import java.util.Locale

/**
 * Debounces noisy camera OCR and prevents the same physical card from being added repeatedly.
 * A card name must remain stable for a short time before it is accepted. After that, the same
 * name is armed again only when the title disappears for a sustained physical-card transition,
 * or when [allowRepeat] is explicitly requested.
 */
class CardScanStability(
    private val requiredHits: Int = 2,
    private val maximumGapMs: Long = 1_800L,
    private val minimumStableMs: Long = 300L,
    private val requiredClearHits: Int = 2,
    private val minimumClearMs: Long = 250L
) {
    private var pendingKey = ""
    private var pendingHits = 0
    private var pendingSinceAt = 0L
    private var lastHitAt = 0L
    private var acceptedKey = ""
    private var clearHits = 0
    private var clearSinceAt = 0L
    private var lastClearAt = 0L

    fun observe(cardName: String, nowMs: Long): Boolean {
        val key = cardName.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return false
        resetClearTransition()
        if (key == acceptedKey) {
            resetPendingCandidate()
            return false
        }

        if (key != pendingKey || nowMs - lastHitAt > maximumGapMs) {
            pendingKey = key
            pendingHits = 1
            pendingSinceAt = nowMs
        } else {
            pendingHits++
        }
        lastHitAt = nowMs
        if (pendingHits < requiredHits || nowMs - pendingSinceAt < minimumStableMs) return false

        acceptedKey = key
        resetPendingCandidate()
        return true
    }

    /** Records a frame where no plausible title is visible, used to detect that a card was moved. */
    fun observeNoCandidate(nowMs: Long) {
        resetPendingCandidate()
        if (acceptedKey.isEmpty()) {
            resetClearTransition()
            return
        }

        if (clearHits == 0 || nowMs - lastClearAt > maximumGapMs) {
            clearHits = 1
            clearSinceAt = nowMs
        } else {
            clearHits++
        }
        lastClearAt = nowMs
        if (clearHits >= requiredClearHits && nowMs - clearSinceAt >= minimumClearMs) {
            acceptedKey = ""
            resetClearTransition()
        }
    }

    fun resetPending() {
        resetPendingCandidate()
        resetClearTransition()
    }

    private fun resetPendingCandidate() {
        pendingKey = ""
        pendingHits = 0
        pendingSinceAt = 0L
        lastHitAt = 0L
    }

    private fun resetClearTransition() {
        clearHits = 0
        clearSinceAt = 0L
        lastClearAt = 0L
    }

    fun allowRepeat() {
        acceptedKey = ""
        resetPending()
    }
}
