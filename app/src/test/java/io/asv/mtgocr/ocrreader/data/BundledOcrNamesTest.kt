package io.asv.mtgocr.ocrreader.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BundledOcrNamesTest {
    private fun names() = File("src/main/assets/" + BundledOcrNames.ASSET).reader().use {
        BundledOcrNames.read(it, 1L)
    }
    @Test fun commanderOmnathIsPresentAndNotCorrectedToOldOmnath() {
        val names = names() + CardNameAliasEntity("omnath locus of the roil",
            "Omnath, Locus of the Roil", "Omnath, Locus of the Roil", "English", 1L)
        assertTrue(names.any { it.canonicalName == "Omnath, Locus of the Void" })
        val index = OcrNameIndex(names)
        assertEquals("Omnath, Locus of the Void", index.match(listOf("Omnath, Locus of the Void"))?.canonicalName)
        assertEquals("Omnath, Locus of the Void", index.match(listOf("Omnath, Locus of the V0id"))?.canonicalName)
    }
    @Test fun includesMainSetAndOnlyVerifiedLanguages() {
        val names = names()
        assertTrue(names.any { it.canonicalName == "Academic Ascent" })
        assertTrue(names.all { it.language == "English" })
        assertTrue(names.all { it.normalizedAlias.isNotBlank() })
    }
}
