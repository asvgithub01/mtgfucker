package io.asv.mtgocr.ocrreader

/** Suspected framing only, not proof of occlusion or missing card. Live rules scanner only. */
internal class RulesCaptureGate {
    private var consecutiveSkips = 0
    fun shouldRetry(quality: Map<String, Double>): Boolean {
        if (!suspicious(quality)) { consecutiveSkips = 0; return false }
        // Never trap an unusual valid layout forever. Third stable attempt reaches normal review.
        if (consecutiveSkips >= 2) return false
        consecutiveSkips++
        return true
    }
    companion object {
        fun suspicious(quality: Map<String, Double>): Boolean {
            val title = quality["titleLaplacianVariance"] ?: return false
            val footer = quality["footerLaplacianVariance"] ?: return false
            return title.isFinite() && footer.isFinite() && title in 0.0..<10.0 && footer > 100.0
        }
    }
}
