package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleLanguageScopeTest {
    @Test fun stableKeyDoesNotConfuseOrderingWithScopeChange() {
        assertEquals("FRA,XLN",TripleLanguageScope.key(setOf("xln"," FRA ","fra")))
    }
    @Test fun onlyCatalogLanguagesAreOfferedAndMultiSetUsesUnion() {
        val catalog=mapOf("FRA" to listOf("English","Japanese"),"XLN" to listOf("English","Portuguese (Brazil)"))
        assertEquals(listOf("en","ja"),TripleLanguageScope.available(setOf("FRA"),catalog))
        assertEquals(listOf("en","pt","ja"),TripleLanguageScope.available(setOf("FRA","XLN"),catalog))
        assertNull(TripleLanguageScope.available(setOf("FRA","UNKNOWN"),catalog))
        assertNull(TripleLanguageScope.available(setOf("EMPTY"),mapOf("EMPTY" to emptyList())))
    }
    @Test fun fixedScriptAvoidsUnrelatedOcrModels() {
        assertEquals("latin",TripleLanguageScope.model("pt"))
        assertEquals("japanese",TripleLanguageScope.model("ja"))
        assertEquals("korean",TripleLanguageScope.model("ko"))
        assertEquals("chinese",TripleLanguageScope.model("zht"))
        assertNull(TripleLanguageScope.model(null))
        assertNull(TripleLanguageScope.model("ru"))
    }
}
