package io.asv.mtgocr.ocrreader

import java.util.Locale

/**
 * Debounces noisy camera OCR and prevents the same physical card from being added repeatedly.
 * A different recognized name arms the scanner again; [allowRepeat] is the explicit override.
 */
class CardScanStability(
    private val requiredHits: Int = 2,
    private val maximumGapMs: Long = 1_800L
) {
    private val requiredClearFrames = 2
    private var pendingKey = ""
    private var pendingHits = 0
    private var lastHitAt = 0L
    private var acceptedKey = ""
    private var clearFrames = 0

    fun observe(cardName: String, nowMs: Long): Boolean {
        val key = cardName.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return false
        clearFrames = 0
        if (key == acceptedKey) {
            resetPending()
            return false
        }
        if (acceptedKey.isNotEmpty() && key != acceptedKey) acceptedKey = ""

        if (key != pendingKey || nowMs - lastHitAt > maximumGapMs) {
            pendingKey = key
            pendingHits = 1
        } else {
            pendingHits++
        }
        lastHitAt = nowMs
        if (pendingHits < requiredHits) return false

        acceptedKey = key
        pendingKey = ""
        pendingHits = 0
        return true
    }

    /** Rearms an identical title only after the previous physical card visibly leaves the guide. */
    fun observeNoCandidate() {
        resetPending()
        if (acceptedKey.isEmpty()) {
            clearFrames = 0
            return
        }
        clearFrames++
        if (clearFrames >= requiredClearFrames) {
            acceptedKey = ""
            clearFrames = 0
        }
    }

    /** Keeps the clear-frame requirement consecutive even while a name lookup is busy. */
    fun observeCandidatePresent() {
        clearFrames = 0
    }

    fun resetPending() {
        pendingKey = ""
        pendingHits = 0
        lastHitAt = 0L
    }

    fun allowRepeat() {
        acceptedKey = ""
        clearFrames = 0
        resetPending()
    }
}
