package io.asv.mtgocr.ocrreader.domain.catalog

/** Inject the shared legacy search policy without coupling presentation to its Views adapter. */
fun interface SetSearchMatcher {
    fun matches(set: SetSummary, query: String): Boolean
}
