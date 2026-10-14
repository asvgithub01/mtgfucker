package io.asv.mtgocr.ocrreader

object StartupPriceRefreshPolicy {
    const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    @JvmStatic fun shouldPrompt(lastUpdate: Long, now: Long): Boolean =
        lastUpdate <= 0L || (now > lastUpdate && now - lastUpdate > WEEK_MILLIS)
}
