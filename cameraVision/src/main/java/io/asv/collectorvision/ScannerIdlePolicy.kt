package io.asv.collectorvision

internal object ScannerIdlePolicy {
    fun shouldPause(now: Long, lastUse: Long, enabled: Boolean = true): Boolean = enabled && now - lastUse >= 30_000L
}

internal object VisualSearchScope {
    /** null means full catalog; empty means no candidates, never fallback to full catalog. */
    fun rows(ids: List<String>, allowed: Set<String>?): IntArray? =
        allowed?.let { ids.indices.filter { index -> ids[index] in it }.toIntArray() }
}
