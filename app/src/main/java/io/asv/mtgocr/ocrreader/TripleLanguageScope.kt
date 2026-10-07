package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardLanguage
import java.util.Locale

/** Explicit user scope, never a language inferred from a reference image. */
internal object TripleLanguageScope {
    val allCodes=listOf("en","es","pt","fr","de","it","ja","ko","ru","zhs","zht","he","la","grc","ar","sa","phyrexian")
    fun key(sets:Set<String>)=sets.map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(",")
    fun available(sets:Set<String>,catalog:Map<String,List<String>>):List<String>? {
        if(sets.isEmpty()) return allCodes
        val selected=key(sets).split(",").map { catalog[it]?.takeIf { langs->langs.isNotEmpty() } ?: return null }
        return selected.flatten().map(CardLanguage::toCode).filter { it in allCodes }.distinct().sortedBy { allCodes.indexOf(it) }.takeIf { it.isNotEmpty() }
    }
    fun model(code:String?)=when(code) {
        "ja"->"japanese"; "ko"->"korean"; "zhs","zht"->"chinese"
        "en","es","pt","fr","de","it","la"->"latin"
        else->null // Unsupported scripts keep the existing fallback; do not pretend Latin reads Russian.
    }
}
