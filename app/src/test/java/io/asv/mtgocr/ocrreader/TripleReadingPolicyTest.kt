package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleReadingPolicyTest {
    @Test fun printingChangesDoNotCreatePhysicalCopies() {
        val gate=TripleEncounterGate()
        val first=gate.observe(TripleReadingPolicy.identity("Coils of the Medusa"))
        repeat(12) { assertEquals(first,gate.observe(TripleReadingPolicy.identity("Coils of the Medusa"))) }
        gate.missing(100);gate.missing(1101)
        assertEquals(first+1,gate.observe(TripleReadingPolicy.identity("Coils of the Medusa")))
        assertEquals(first+2,gate.observe(TripleReadingPolicy.identity("Cuombajj Witches")))
    }
    @Test fun manualAndReliableEditionsCannotBeReplacedByLateProposals() {
        assertTrue(TripleReadingPolicy.keepCurrent(true,"a",true,"b",false))
        assertTrue(TripleReadingPolicy.keepCurrent(false,"a",false,"b",true))
        assertTrue(TripleReadingPolicy.keepCurrent(false,"a",true,"b",true))
        assertFalse(TripleReadingPolicy.keepCurrent(false,"a",true,"b",false))
        assertTrue(TripleReadingPolicy.keepCurrent(false,"a",true,"",true))
    }
    private fun title(vararg codes:String)=TitleLanguageIndex.Evidence(codes.map { TitleLanguageIndex.Match("alias","Coils of the Medusa","alias",it) })
    @Test fun localizedMatchedAliasSuppliesPortugueseEvenWhenRawOcrHasTypos() {
        assertEquals("pt",TripleReadingPolicy.titleLanguage(TitleLanguageIndex.Evidence(),title("pt"),"pt"))
        assertEquals("pt",TripleReadingPolicy.titleLanguage(title("pt"),TitleLanguageIndex.Evidence(),null))
        assertNull(TripleReadingPolicy.titleLanguage(TitleLanguageIndex.Evidence(),title("es","pt"),"pt"))
        assertNull(TripleReadingPolicy.titleLanguage(title("es","pt"),title("pt"),"pt"))
        assertNull(TripleReadingPolicy.titleLanguage(TitleLanguageIndex.Evidence(),title("pt"),"en"))
    }
}
