package io.asv.mtgocr.ocrreader

/** A suggestion for human review, never an automatic overwrite or script-to-language assumption. */
object LanguageReviewPolicy {
    data class Guess(val language: String, val confidence: Double, val textLength: Int)
    fun propose(guesses: List<Guess>): String? {
        val eligible = guesses.filter { it.textLength >= 20 && it.confidence.isFinite() && it.confidence in 0.0..1.0 }
            .filter { it.language in setOf("en","es","pt","fr","de","it","ja","ko","zh","zhs","zht") }
            .groupBy { if (it.language in setOf("zh","zhs","zht")) "zh" else it.language }
            .map { (language, values) -> language to values.maxOf { it.confidence } }.sortedByDescending { it.second }
        val best = eligible.firstOrNull() ?: return null
        if (best.second < .8 || (eligible.getOrNull(1)?.second ?: 0.0) > best.second-.15) return null
        // ML Kit language ID cannot establish simplified vs traditional printing.
        return best.first.takeUnless { it == "zh" }
    }
}
