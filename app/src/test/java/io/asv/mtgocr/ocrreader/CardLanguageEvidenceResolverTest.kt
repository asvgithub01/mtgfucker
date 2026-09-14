package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLanguageEvidenceResolverTest {
    @Test
    fun `localized title wins over a footer token found inside ordinary text`() {
        assertEquals(
            "pt",
            CardLanguageEvidenceResolver.resolve(
                footerLanguage = "de",
                detectedRulesLanguage = "pt",
                detectedRulesConfidence = .42f,
                matchedTitleLanguage = "pt"
            )
        )
    }

    @Test
    fun `reliable rules language wins over a conflicting footer token`() {
        assertEquals(
            "pt",
            CardLanguageEvidenceResolver.resolve(
                footerLanguage = "de",
                detectedRulesLanguage = "pt",
                detectedRulesConfidence = .78f
            )
        )
    }

    @Test
    fun `footer remains useful when rules detection is weak`() {
        assertEquals(
            "it",
            CardLanguageEvidenceResolver.resolve(
                footerLanguage = "it",
                detectedRulesLanguage = "pt",
                detectedRulesConfidence = .22f,
                matchedTitleLanguage = "en"
            )
        )
    }
}
