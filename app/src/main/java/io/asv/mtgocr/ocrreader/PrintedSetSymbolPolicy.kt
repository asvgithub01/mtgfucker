package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Printed expansion symbols, not the retrospective logos shown by catalogues.
 * Core sets before Sixth have no symbol, except Simplified Chinese Fifth Edition.
 * https://magic.wizards.com/en/news/feature/fifth-edition-symbol-2002-12-12
 * Do not extend this to all old expansions, or infer absence from a failed matcher.
 */
internal object PrintedSetSymbolPolicy {
    private val WITHOUT_SYMBOL = setOf("LEA", "LEB", "2ED", "3ED", "4ED")
    private val CHINESE_SIMPLIFIED = setOf("zhs", "zh-hans", "zh-cn")

    fun mayHaveNoSymbol(code: String, language: String = ""): Boolean =
        code.uppercase(Locale.ROOT) in WITHOUT_SYMBOL ||
            (code.equals("5ED", true) && language.lowercase(Locale.ROOT) !in CHINESE_SIMPLIFIED)

    fun needsPrintedReference(code: String, language: String = ""): Boolean = when {
        code.uppercase(Locale.ROOT) in WITHOUT_SYMBOL -> false
        !code.equals("5ED", true) -> true
        // Unknown language retains BOTH possibilities, never silently discards Chinese Fifth.
        language.isBlank() || language.equals("zh", true) -> true
        else -> language.lowercase(Locale.ROOT) in CHINESE_SIMPLIFIED
    }
}
