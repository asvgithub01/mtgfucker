package io.asv.mtgocr.ocrreader
import org.junit.Assert.assertEquals
import org.junit.Test
class CardLanguageFlagTest {
    @Test fun portugueseShowsFlagAndCode() { assertEquals("🇵🇹 PT",CardLanguageFlag.label("pt")) }
    @Test fun normalizesCaseAndWhitespace() { assertEquals("🇪🇸 ES",CardLanguageFlag.label(" Es ")) }
    @Test fun unknownDoesNotInventNationalFlag() { assertEquals("🌐",CardLanguageFlag.flagFor("und")) }
    @Test fun distinguishesChineseVariants() {
        assertEquals("🇨🇳",CardLanguageFlag.flagFor("zhs"))
        assertEquals("🇹🇼",CardLanguageFlag.flagFor("zht"))
    }
}
