package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test
import io.asv.mtgocr.ocrreader.TripleLanguageCopyPolicy.Action.*

class TripleLanguageCopyPolicyTest {
    private fun TripleLanguageCopyPolicy.read(code:String?,at:Long,stored:String?="en",origin:String="rules",
        manual:Boolean=false,exact:Boolean=true,matched:Boolean=true,photo:Boolean=true,event:Long=56,now:Long=at)=
        observe(event,stored,origin,manual,code,exact,matched,photo,at,now)

    @Test fun willOWispConfirmedEnglishThenItalianCreatesCopyNotLanguageEdit() {
        val p=TripleLanguageCopyPolicy()
        assertEquals(KEEP,p.read("en",0))
        assertEquals(WAIT,p.read("it",3149))
        assertEquals(SPLIT,p.read("it",6304))
        assertEquals(KEEP,p.read("it",7000,stored="it",event=57))
    }
    @Test fun unknownLanguageCompletesSameRowInsteadOfCreatingAnother() {
        val p=TripleLanguageCopyPolicy()
        assertEquals(ASSIGN,p.read("pt",0,stored=null))
        assertEquals(KEEP,p.read("pt",700,stored="pt"))
        val rules=TripleLanguageCopyPolicy()
        assertEquals(WAIT,rules.read("ja",0,stored=null,exact=false))
        assertEquals(ASSIGN,rules.read("ja",700,stored=null,exact=false))
    }
    @Test fun manualEnglishAndPrintingInferenceConflictAreNotOverwrittenOrSplit() {
        for(p in listOf(TripleLanguageCopyPolicy(),TripleLanguageCopyPolicy())) {
            assertEquals(CONFLICT,p.read("pt",0,manual=true))
            assertEquals(CONFLICT,p.read("pt",900,origin="printing"))
        }
    }
    @Test fun oneNoisyReadRepeatedCallbackAndRulesOnDifferentCardCannotSplit() {
        val p=TripleLanguageCopyPolicy()
        assertEquals(WAIT,p.read("it",0))
        assertEquals(WAIT,p.read("it",0,exact=false))
        assertEquals(KEEP,p.read("it",700,matched=false))
        assertEquals(KEEP,p.read("en",900))
        assertEquals(WAIT,p.read("it",1700))
        assertEquals(WAIT,p.read("it",8000)) // history expired
        assertEquals(KEEP,p.read("it",9000,now=11501)) // stale
    }
    @Test fun noFreshPhotoMeansReviewNotReusingOldEnglishPhoto() {
        val p=TripleLanguageCopyPolicy()
        p.read("it",0)
        assertEquals(CONFLICT,p.read("it",700,photo=false))
    }
    @Test fun ruleChangesNeedThreeIndependentCapturesAndNoVotesLeakAcrossRows() {
        val p=TripleLanguageCopyPolicy()
        assertEquals(WAIT,p.read("pt",0,exact=false))
        assertEquals(WAIT,p.read("pt",700,exact=false))
        assertEquals(SPLIT,p.read("pt",1400,exact=false))
        assertEquals(WAIT,p.read("pt",2100,exact=false,event=57))
        assertEquals(KEEP,p.read(null,2800,event=57))
    }
    @Test fun languageBoundaryRejectsOldOcrAndDoesNotReaddSameIdentity() {
        val a=TripleRecognitionArbiter()
        a.observe("wisp","EdScan",true,0,0)
        a.separateLanguageCopy(5000,5100)
        assertEquals("stale",a.observe("wisp","OCR",false,4900,5200).reason)
        assertFalse(a.observe("wisp","EdScan",true,5500,5600).newEncounter)
    }
    @Test fun physicalAgreementUpgradesPrintingInferenceBeforeSubsequentLanguageCopy() {
        val p=TripleLanguageCopyPolicy()
        assertEquals(ASSIGN,p.read("en",0,origin="printing"))
        assertEquals(WAIT,p.read("it",1000,origin="title"))
        assertEquals(SPLIT,p.read("it",1700,origin="title"))
        val rules=TripleLanguageCopyPolicy()
        assertEquals(WAIT,rules.read("en",0,origin="printing",exact=false))
        assertEquals(ASSIGN,rules.read("en",700,origin="printing",exact=false))
    }
}
