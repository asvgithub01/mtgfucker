/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

/** Visual-confidence policy, not proof of printing/language/finish. Thread safe for host callbacks. */
class CollectorVisionAcceptanceGate(
    private val requiredReadings: Int = 3,
    private val minScore: Float = .70f,
    private val minMargin: Float = .08f,
    private val absentFramesToRearm: Int = 2
) {
    data class Attempt internal constructor(val token: Long, val cardId: String, val score: Float, internal val epoch: Long)
    private var filtersEnabled = true

    /** Change confidence policy without unlocking saved, failed or in-flight cards. */
    @Synchronized fun setFiltersEnabled(enabled: Boolean) {
        if (filtersEnabled == enabled) return
        filtersEnabled = enabled
        resetStability()
    }

    private var sequence = 0L
    private var epoch = 0L
    private var absentFrames = 0
    private var streakId: String? = null
    private var streak = 0
    private var pending: Attempt? = null
    private var savedId: String? = null
    private val failedIds = mutableSetOf<String>()

    init { require(requiredReadings > 0 && absentFramesToRearm > 0 && minScore.isFinite() && minMargin.isFinite() && minMargin >= 0) }

    @Synchronized fun observe(present: Boolean, hits: List<Hit>): Attempt? {
        if (!present) {
            clearStreak()
            absentFrames++
            if (absentFrames == absentFramesToRearm) { epoch++; savedId = null; failedIds.clear() }
            return null
        }
        absentFrames = 0
        if (pending != null) { clearStreak(); return null }
        val best = hits.firstOrNull()
        // Missing runner-up is unknown separation, not an infinite margin.
        val runnerUp = hits.drop(1).firstOrNull { it.cardId != best?.cardId }
        val reliable = best != null && best.score.isFinite() && best.cardId.isNotBlank() &&
            if (filtersEnabled) {
                runnerUp != null && runnerUp.score.isFinite() &&
                    best.score >= minScore && best.score - runnerUp.score >= minMargin
            } else best.score >= .50f
        if (!reliable || best == null || best.cardId == savedId || best.cardId in failedIds) { clearStreak(); return null }
        if (best.cardId == streakId) streak++ else { streakId = best.cardId; streak = 1 }
        if (streak < if (filtersEnabled) requiredReadings else 1) return null
        val attempt = Attempt(++sequence, best.cardId, best.score, epoch)
        pending = attempt
        clearStreak()
        return attempt
    }

    /** Returns true only once for the matching in-flight save, never for a stale callback. */
    @Synchronized fun complete(attempt: Attempt, saved: Boolean): Boolean {
        if (pending?.token != attempt.token) return false
        pending = null
        if (attempt.epoch == epoch) {
            if (saved) {
                savedId = attempt.cardId
                failedIds.clear()
            } else {
                // Unknown/partial persistence failures must not trigger a request every three frames.
                failedIds.add(attempt.cardId)
            }
        }
        clearStreak()
        return true
    }

    /** Pausing, errors and inconclusive readings cannot pretend that the physical card left. */
    @Synchronized fun resetStability() { clearStreak(); absentFrames = 0 }
    private fun clearStreak() { streakId = null; streak = 0 }
    @Synchronized fun isSaved(cardId: String?) = cardId != null && savedId == cardId
    @Synchronized fun isPending() = pending != null
}
