/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

/** Visual-confidence policy, not proof of printing/language/finish. Thread safe for host callbacks. */
class CollectorVisionAcceptanceGate(
    private val requiredReadings: Int = 3,
    private val minScore: Float = .70f,
    private val minMargin: Float = .08f,
    private val absentFramesToRearm: Int = 2
) {
    data class Attempt internal constructor(val token: Long, val cardId: String, val score: Float, internal val epoch: Long, val filtered: Boolean, val readings: Int, val scoreThreshold: Float, val marginThreshold: Float, val provisionalIds: List<String> = emptyList(), val identity: String? = null)
    private var filtersEnabled = true
    private var fastScore = .55f
    private var fastMargin = .04f
    private var fastReadings = 2
    @Synchronized fun configureFast(score: Float, margin: Float, readings: Int) {
        require(score.isFinite() && score in 0f..1f && margin.isFinite() && margin in 0f..1f && readings in 1..3)
        fastScore = score; fastMargin = margin; fastReadings = readings
        resetStability()
    }

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
    private var savedIdentity: String? = null
    private val failedIds = mutableSetOf<String>()
    private val failedIdentities = mutableSetOf<String>()

    init { require(requiredReadings > 0 && absentFramesToRearm > 0 && minScore.isFinite() && minMargin.isFinite() && minMargin >= 0) }

    @Synchronized fun observe(present: Boolean, hits: List<Hit>, identities: Map<String,String> = emptyMap()): Attempt? {
        if (!present) {
            clearStreak()
            absentFrames++
            if (absentFrames == absentFramesToRearm) { epoch++; savedId = null; savedIdentity = null; failedIds.clear(); failedIdentities.clear() }
            return null
        }
        absentFrames = 0
        if (pending != null) { clearStreak(); return null }
        val best = hits.firstOrNull()
        // Missing runner-up is unknown separation, not an infinite margin.
        val runnerUp = hits.drop(1).firstOrNull { it.cardId != best?.cardId }
        val scoreThreshold = if (filtersEnabled) minScore else fastScore
        val marginThreshold = if (filtersEnabled) minMargin else fastMargin
        val identity = best?.let { identities[it.cardId] }?.takeIf { it.isNotBlank() }
        val near = if (best != null) hits.filter { best.score - it.score < marginThreshold } else emptyList()
        // The returned tail must be separated too; otherwise an unseen different card could tie.
        val sameCard = best != null && identity != null && near.size >= 2 &&
            hits.all { it.score.isFinite() } && best.score - hits.last().score >= marginThreshold &&
            near.all { identities[it.cardId] == identity }
        val reliable = best != null && best.score.isFinite() && best.cardId.isNotBlank() &&
            runnerUp != null && runnerUp.score.isFinite() &&
            best.score >= scoreThreshold && (best.score - runnerUp.score >= marginThreshold || sameCard)
        if (!reliable || best == null || best.cardId == savedId || (identity != null && (identity == savedIdentity || identity == savedId?.let { identities[it] } || identity in failedIdentities)) || best.cardId in failedIds) { clearStreak(); return null }
        val stabilityKey = if (sameCard) "identity:$identity" else best.cardId
        if (stabilityKey == streakId) streak++ else { streakId = stabilityKey; streak = 1 }
        // Fast remains immediate only for a strongly separated candidate; otherwise require stability.
        val strongFast = !sameCard && !filtersEnabled && best.score >= .80f && best.score - runnerUp!!.score >= .08f
        val readingsNeeded = if (filtersEnabled) requiredReadings else if (sameCard) maxOf(2,fastReadings) else if (strongFast) 1 else fastReadings
        if (streak < readingsNeeded) return null
        val attempt = Attempt(++sequence, best.cardId, best.score, epoch, filtersEnabled, streak,
            if (strongFast) maxOf(.80f, scoreThreshold) else scoreThreshold, if (strongFast) maxOf(.08f, marginThreshold) else marginThreshold,
            if (sameCard) near.map { it.cardId } else emptyList(), identity)
        pending = attempt
        clearStreak()
        return attempt
    }

    data class Ambiguity(val margin: Float, val minimum: Float)
    /** Explanation only; never relax acceptance or unlock a saved/in-flight printing. */
    @Synchronized fun unresolvedMargin(present: Boolean, hits: List<Hit>): Ambiguity? {
        if (!present || pending != null) return null
        val best = hits.firstOrNull() ?: return null
        if (best.cardId.isBlank() || best.cardId == savedId || best.cardId in failedIds) return null
        val runner = hits.drop(1).firstOrNull { it.cardId != best.cardId } ?: return null
        val threshold = if (filtersEnabled) minScore else fastScore
        val minimum = if (filtersEnabled) minMargin else fastMargin
        if (!best.score.isFinite() || !runner.score.isFinite() || best.score < threshold) return null
        val margin = best.score - runner.score
        return if (margin < minimum) Ambiguity(margin, minimum) else null
    }

    /** Returns true only once for the matching in-flight save, never for a stale callback. */
    @Synchronized fun complete(attempt: Attempt, saved: Boolean): Boolean {
        if (pending?.token != attempt.token) return false
        pending = null
        if (attempt.epoch == epoch) {
            if (saved) {
                savedId = attempt.cardId
                savedIdentity = attempt.identity
                failedIds.clear(); failedIdentities.clear()
            } else {
                // Unknown/partial persistence failures must not trigger a request every three frames.
                failedIds.add(attempt.cardId)
                attempt.identity?.let { failedIdentities.add(it) }
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
