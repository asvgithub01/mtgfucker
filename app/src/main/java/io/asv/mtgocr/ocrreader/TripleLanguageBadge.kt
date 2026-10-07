package io.asv.mtgocr.ocrreader

/** No default English flag: this is observed physical language, not reference-image language. */
object TripleLanguageBadge {
    fun label(code:String?):String = code?.trim()?.takeIf { it.isNotBlank() && !it.equals("und",true) }
        ?.let { CardLanguageFlag.label(it) } ?: "?"
}
