package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleSnapshotLanguageGateTest {
    @Test fun slowNameLookupDoesNotLosePortugueseRulesForAnAmbiguousTitle() {
        val result=mutableListOf<Triple<String,Long,String>>()
        val gate=TripleSnapshotLanguageGate<String> { name,event,code -> result+=Triple(name,event,code) }
        gate.language("pt")
        assertTrue(result.isEmpty())
        gate.identify("llanowar elves",56)
        assertEquals(listOf(Triple("llanowar elves",56L,"pt")),result)
        gate.identify("wrong next card",57);gate.language("en")
        assertEquals(1,result.size)
    }
    @Test fun nameFirstWorksButDifferentSnapshotsNeverCombineTheirEvidence() {
        val result=mutableListOf<String>()
        val a=TripleSnapshotLanguageGate<String> { _,_,code -> result+=code }
        val b=TripleSnapshotLanguageGate<String> { _,_,code -> result+=code }
        a.identify("wisp",56);b.language("it")
        assertTrue(result.isEmpty())
        a.language("en");assertEquals(listOf("en"),result)
    }
}
