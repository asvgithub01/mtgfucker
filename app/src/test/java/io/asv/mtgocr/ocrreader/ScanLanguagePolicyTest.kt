package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanLanguagePolicyTest {
    @Test fun shortManaAbilitiesDisambiguateSharedElfTitles() {
        assertEquals("es", ScanLanguagePolicy.shortRulesLanguage("{T}: Agrega {G}."))
        assertEquals("es", ScanLanguagePolicy.shortRulesLanguage("Añade {G}."))
        assertEquals("pt", ScanLanguagePolicy.shortRulesLanguage("{T}: Adicione {G}."))
        assertEquals("it", ScanLanguagePolicy.shortRulesLanguage("{T}: Aggiungi {G}."))
        assertEquals("it", ScanLanguagePolicy.shortRulesLanguage("Pesca una carta. La creatura bersaglio."))
        assertEquals("en", ScanLanguagePolicy.shortRulesLanguage("{T}: Add {G}."))
        assertEquals(null, ScanLanguagePolicy.shortRulesLanguage("Criatura — Elfo"))
        assertEquals(null, ScanLanguagePolicy.shortRulesLanguage("Agrega Adicione"))
        assertEquals(null, ScanLanguagePolicy.shortRulesLanguage("Aggiungi Agrega"))
    }

    @Test fun clearSpanishRulesOverrideAmbiguousPortugueseTitle() {
        assertEquals("es", ScanLanguagePolicy.choose("pt", listOf("es" to .92f, "pt" to .06f)).first)
    }
    @Test fun clearPortugueseRulesRemainPortuguese() {
        assertEquals("pt", ScanLanguagePolicy.choose("es", listOf("pt" to .95f, "es" to .04f)).first)
    }
    @Test fun noisyRulesDoNotOverrideTitle() {
        assertEquals("es", ScanLanguagePolicy.choose("es", listOf("pt" to .38f, "es" to .35f)).first)
        assertEquals("es", ScanLanguagePolicy.choose("es", listOf("pt" to .70f, "es" to .60f)).first)
    }
    @Test fun unavailableLanguageDoesNotInventOne() {
        assertEquals("", ScanLanguagePolicy.choose("", listOf("und" to .99f)).first)
    }
    @Test fun enrichmentDoesNotReplaceLocalizedNameWithCanonicalEnglish() {
        assertEquals("Elfos de Llanowar", ScanIdentity.displayName("Elfos de Llanowar", "es", "Llanowar Elves"))
        assertEquals("Llanura", ScanIdentity.displayName("Llanura", "es", "Plains"))
        assertEquals("Plains", ScanIdentity.displayName("Plains", "en", "Plains"))
        assertEquals("Plains", ScanIdentity.displayName("", "es", "Plains"))
    }
}
