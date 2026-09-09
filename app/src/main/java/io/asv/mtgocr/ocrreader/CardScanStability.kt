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
    private var pendingKey = ""
    private var pendingHits = 0
    private var lastHitAt = 0L
    private var acceptedKey = ""

    fun observe(cardName: String, nowMs: Long): Boolean {
        val key = cardName.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return false
        if (key == acceptedKey) return false
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

    fun resetPending() {
        pendingKey = ""
        pendingHits = 0
        lastHitAt = 0L
    }

    fun allowRepeat() {
        acceptedKey = ""
        resetPending()
    }
}
