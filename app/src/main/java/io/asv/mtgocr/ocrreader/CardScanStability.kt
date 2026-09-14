package io.asv.mtgocr.ocrreader

import java.util.Locale

/**
 * Debounces noisy camera OCR and separates an accidental repeated frame from a deliberate scan of
 * another physical copy. A different localized identity is always accepted immediately.
 */
class CardScanStability(
    private val requiredHits: Int = 2,
    private val maximumGapMs: Long = 1_800L,
    private val repeatCooldownMs: Long = 2_500L
) {
    enum class Decision {
        WAITING,
        ACCEPT,
        CONFIRM_REPEAT
    }

    private var pendingKey = ""
    private var pendingHits = 0
    private var lastHitAt = 0L
    private var acceptedKey = ""
    private var acceptedAt = 0L

    fun observe(
        canonicalName: String,
        displayName: String,
        language: String,
        nowMs: Long
    ): Decision {
        val key = recognitionKey(canonicalName, displayName, language)
        if (key.isEmpty()) return Decision.WAITING
        if (key == acceptedKey) {
            resetPending()
            if (nowMs - acceptedAt < repeatCooldownMs) return Decision.WAITING
            // Start another quiet period immediately so one camera frame can produce only one
            // confirmation request, even if the UI needs a moment to display it.
            acceptedAt = nowMs
            return Decision.CONFIRM_REPEAT
        }
        if (acceptedKey.isNotEmpty() && key != acceptedKey) acceptedKey = ""

        if (key != pendingKey || nowMs - lastHitAt > maximumGapMs) {
            pendingKey = key
            pendingHits = 1
        } else {
            pendingHits++
        }
        lastHitAt = nowMs
        if (pendingHits < requiredHits) return Decision.WAITING

        acceptedKey = key
        acceptedAt = nowMs
        pendingKey = ""
        pendingHits = 0
        return Decision.ACCEPT
    }

    fun resetPending() {
        pendingKey = ""
        pendingHits = 0
        lastHitAt = 0L
    }

    fun allowRepeat() {
        acceptedKey = ""
        acceptedAt = 0L
        resetPending()
    }

    /** Restarts the quiet period after the user rejects or dismisses a repeated-card prompt. */
    fun postponeRepeat(nowMs: Long) {
        if (acceptedKey.isNotEmpty()) acceptedAt = nowMs
        resetPending()
    }

    private fun recognitionKey(
        canonicalName: String,
        displayName: String,
        language: String
    ): String {
        val canonical = canonicalName.trim().lowercase(Locale.ROOT)
        val visible = displayName.trim().ifEmpty { canonicalName.trim() }.lowercase(Locale.ROOT)
        val localizedLanguage = language.trim().lowercase(Locale.ROOT)
        if (canonical.isEmpty() && visible.isEmpty()) return ""
        // Language and visible/localized name deliberately participate in identity. Thus English
        // "Fog" and Spanish "Niebla" are two unambiguous physical scans, despite sharing the same
        // canonical catalog name.
        return "$canonical\u0001$localizedLanguage\u0001$visible"
    }
}
