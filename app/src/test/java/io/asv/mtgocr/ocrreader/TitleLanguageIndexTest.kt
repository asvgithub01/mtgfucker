package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class TitleLanguageIndexTest {
    private fun data(body: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(body.toByteArray()) }
    }.toByteArray()
    private val header = "TLV1\t${"a".repeat(64)}\n"
    private fun index() = TitleLanguageIndex.read(data(header +
        "elfos de llanowar\tLlanowar Elves\tElfos de Llanowar\tes\n" +
        "elfos de llanowar\tLlanowar Elves\tElfos de Llanowar\tpt\n" +
        "espiritu fluctuante\tFlickering Spirit\tEspíritu fluctuante\tes\n" +
        "espirito flutuante\tFlickering Spirit\tEspírito Flutuante\tpt\n").inputStream())
    // Sorted by normalized UTF-8, not localized collator.
    private fun sortedIndex(): TitleLanguageIndex {
        val rows = listOf("Elfos de Llanowar\tLlanowar Elves\tes", "Elfos de Llanowar\tLlanowar Elves\tpt",
            "Espírito Flutuante\tFlickering Spirit\tpt", "Espíritu fluctuante\tFlickering Spirit\tes")
            .map { val f = it.split('\t'); "${TitleLanguageIndex.normalize(f[0])}\t${f[1]}\t${f[0]}\t${f[2]}\n" }.sorted()
        return TitleLanguageIndex.read(data(header + rows.joinToString("")).inputStream())
    }
    @Test fun exactPortugueseIgnoresCaseAccentAndSpacing() {
        val evidence = sortedIndex().lookup(listOf(" ESPIRITO   Flutuante "), listOf("Flickering Spirit"))
        assertEquals(setOf("pt"), evidence.candidates)
        assertEquals("Espírito Flutuante", evidence.printedName("Flickering Spirit", "pt"))
    }
    @Test fun sharedTitleRetainsBothLanguages() {
        assertEquals(setOf("es", "pt"), sortedIndex().lookup(listOf("Elfos de Llanowar"), listOf("Llanowar Elves")).candidates)
    }
    @Test fun typoWrongIdentityAndMultipleIdentitiesDoNotSupplyLanguage() {
        assertTrue(sortedIndex().lookup(listOf("Espírito Flutuarnte"), listOf("Flickering Spirit")).matches.isEmpty())
        assertTrue(sortedIndex().lookup(listOf("Espírito Flutuante"), listOf("Other")).matches.isEmpty())
        assertTrue(sortedIndex().lookup(listOf("Espírito Flutuante"), listOf("Other", "Flickering Spirit")).matches.isEmpty())
    }
    @Test fun contradictoryExactTitlesRemainConflict() {
        assertTrue(sortedIndex().lookup(listOf("Espírito Flutuante", "Espíritu fluctuante"), listOf("Flickering Spirit")).conflict)
    }
    @Test fun absentTitleNeverDefaultsEnglish() {
        assertTrue(sortedIndex().lookup(emptyList(), listOf("Flickering Spirit")).languages.isEmpty())
    }
    @Test fun invalidHeaderColumnsEndingAndOrderRejected() {
        for (body in listOf("wrong\n", header + "key\tName\ten\n", header + "key\tName\tName\ten",
            header + "z\tN\tN\ten\na\tN\tN\ten\n")) {
            assertTrue(runCatching { TitleLanguageIndex.read(data(body).inputStream()) }.isFailure)
        }
        assertTrue(runCatching { index() }.isFailure)
    }
    @Test fun packagedDictionaryPreservesRealPortugueseAndSharedSpanishTitles() {
        val index = TitleLanguageIndex.read(java.io.File("src/main/assets/" + TitleLanguageIndex.ASSET).inputStream())
        assertEquals(setOf("pt"), index.lookup(listOf("Espirito Flutuante"), listOf("Flickering Spirit")).candidates)
        assertEquals(setOf("pt"), index.lookup(listOf("Bôifalo Titânico"), listOf("Titanic Bulvox")).candidates)
        assertTrue(index.lookup(listOf("Elfos de Llanowar"), listOf("Llanowar Elves")).candidates.containsAll(listOf("es", "pt")))
    }
}
