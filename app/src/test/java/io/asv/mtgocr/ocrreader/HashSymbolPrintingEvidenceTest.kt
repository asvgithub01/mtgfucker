package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HashSymbolPrintingEvidenceTest {
    private val sets = setOf("M13", "ORI", "SPG", "STA", "TSP")
    private fun evidence(raw: String) = HashSymbolPrintingEvidence.trusted(
        PrintingMetadataParser.parse(raw, sets), sets)!!

    @Test fun spanishRulesAndPowerToughnessCannotVetoM13() {
        val value = evidence("Vigilancia. Esta criatura no se gira\nVengadora de Serra\n3/3\n© 2012 Wizards of the Coast 33/249")
        assertNull(value.setCode); assertNull(value.collectorNumber); assertNull(value.languageCode)
        assertEquals(2012, value.printingYear)
    }
    @Test fun exactModernFooterRetainsCodeNumberAndLanguage() {
        val value = evidence("Rules text\nR 0033\nM13 ES\nArtist name")
        assertEquals("M13", value.setCode); assertEquals("0033", value.collectorNumber)
        assertEquals("es", value.languageCode)
    }
    @Test fun unrelatedRulesAndConflictingFootersStayUntrusted() {
        assertNull(evidence("Esta criatura de ORI no se gira\n3/3").setCode)
        assertNull(evidence("033\nM13 ES\n033\nORI ES").setCode)
        assertNull(evidence("3/3\nM13 ES").collectorNumber)
    }
    @Test fun missingCollectorDoesNotReusePowerToughnessElsewhere() {
        val value = evidence("2/2\nother text\nSPG SP\nArtist")
        assertEquals("SPG", value.setCode); assertEquals("es", value.languageCode)
        assertNull(value.collectorNumber)
    }
}
