package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

/** Anonymized identities/timing from Sony 2026-10-14; no photos or inventory in the APK. */
class TripleSonySafetyRegressionTest {
    private fun TripleRecognitionArbiter.mixed(id:String,source:String,at:Long,strong:Boolean=source=="EdScan")=
        observe(id,source,strong,at,at,requireIndependentAr=true)

    @Test fun historicalWitchHunterAndBlacksmithCannotAutoAddOnAbuJafarOrPaladin() {
        for(wrong in listOf("witch hunter","repentant blacksmith")) {
            val a=TripleRecognitionArbiter()
            a.mixed("unyaro griffin","EdScan",0)
            assertFalse(a.mixed("abu jafar","EdScan",1500,false).accepted)
            assertEquals("ar_unverified",a.mixed(wrong,"AR",2000).reason)
            for(t in 2633L..15000L step 200) assertFalse(a.mixed(wrong,"AR",t).accepted)
            assertFalse(a.mixed("abu jafar","OCR",16000).accepted)
            a.mixed(wrong,"AR",16500)
            assertTrue(a.mixed("abu jafar","OCR",16700).newEncounter) // AR cannot veto real title
        }
    }

    @Test fun cuombajjCorrection1907msAfterFalseArCreatesOnlyTheCorrectRow() {
        val a=TripleRecognitionArbiter();val gate=TripleEncounterGate()
        a.mixed("northern paladin","EdScan",0);gate.observe("northern paladin")
        a.mixed("cloak of confusion","AR",1000)
        assertFalse(a.mixed("cloak of confusion","AR",1650).accepted)
        val correct=a.mixed("cuombajj witches","EdScan",3557)
        assertTrue(correct.newEncounter);gate.nextEncounter();gate.observe("cuombajj witches")
        assertEquals(2L,gate.event) // previous paladin + witches; no phantom cloak
    }

    @Test fun ornithopterCannotBeAddedAsPreviouslyLearnedJapaneseRoamingThrone() {
        val a=TripleRecognitionArbiter()
        a.mixed("roaming throne","OCR",0);a.mixed("roaming throne","OCR",700)
        a.mixed("faerie miscreant","EdScan",4000)
        a.mixed("roaming throne","AR",10000)
        assertFalse(a.mixed("roaming throne","AR",10680).accepted)
        assertTrue(a.mixed("ornithopter","EdScan",13000).newEncounter)
    }

    @Test fun oldIndependentEvidenceAndAbsenceCannotAuthorizeAnotherArCopy() {
        val a=TripleRecognitionArbiter()
        a.mixed("witch hunter","EdScan",0)
        a.missing(100);a.missing(800);a.missing(1600)
        a.mixed("witch hunter","AR",1700)
        assertFalse(a.mixed("witch hunter","AR",2400).accepted)
        assertTrue(a.mixed("witch hunter","EdScan",2500).newEncounter)
    }

    @Test fun allSevenModesStillProgressAndDoNotDuplicate500AlternatingCards() {
        for(mask in 1..7) {
            val a=TripleRecognitionArbiter();var rows=0
            fun read(name:String,source:String,t:Long) {
                if(a.observe(name,source,source=="EdScan",t,t,requireIndependentAr=mask and 6!=0).newEncounter) rows++
            }
            repeat(500) { i ->
                val name="card${i%2}";val t=i*8000L
                if(mask and 1!=0) { read(name,"AR",t);read(name,"AR",t+650) }
                if(mask and 2!=0) read(name,"EdScan",t+1000)
                if(mask and 4!=0) { read(name,"OCR",t+2000);read(name,"OCR",t+2700) }
            }
            assertEquals("mask=$mask",500,rows)
        }
    }
}
