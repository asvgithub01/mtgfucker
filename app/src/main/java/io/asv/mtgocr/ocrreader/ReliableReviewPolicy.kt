package io.asv.mtgocr.ocrreader

/** No score-only auto-acceptance: fresh OCR identity and exact art printing must agree. */
object ReliableReviewPolicy {
    fun artReliable(score: Double?, margin: Double?): Boolean =
        score!=null && margin!=null && score.isFinite() && margin.isFinite() && score>=.70 && margin>=.08

    fun sameIdentity(ocrName: String, artName: String): Boolean =
        ocrName.isNotBlank() && ocrName.trim().equals(artName.trim(),true)

    fun language(available: List<String>, strongOcr: String?): String? {
        val codes=available.filter { it.isNotBlank() }.distinct()
        return if(codes.size==1) codes.single() else strongOcr?.takeIf { it in codes }
    }
}
