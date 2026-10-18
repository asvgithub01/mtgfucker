package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TriplePriorityAndPhotoLanguageTest {
    @Test fun strongFirstVoteGetsSecondArtBeforeDueOcr() {
        val s=TripleFrameScheduler()
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(0,true,true,false))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(1000,true,true,false))
        s.visualResult("readings",2500)
        assertEquals(150L,TripleScanPolicy.frameRestMs(1500,s.hasFollowUp(2500)))
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(2650,true,true,false))
        s.visualResult("confirmed",4200)
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(4300,true,true,false))
    }
    @Test fun unstableIdentitiesCannotStarveIndependentOcr() {
        val s=TripleFrameScheduler()
        s.visualResult("readings",1000)
        assertEquals(TripleFrameScheduler.Lane.EDSCAN,s.next(1100,true,true,false))
        s.visualResult("readings",2200) // another identity also has only one vote
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(2300,true,true,false))
    }
    @Test fun weakMissingExpiredAndResetVotesNeverGetPriority() {
        for(reason in listOf("score","margin","no_detection","no_candidate","confirmed")) {
            val s=TripleFrameScheduler();s.visualResult("readings",100);s.visualResult(reason,200)
            assertFalse(s.hasFollowUp(300))
        }
        val s=TripleFrameScheduler();s.visualResult("readings",100)
        assertFalse(s.hasFollowUp(6100));s.visualResult("readings",7000);s.reset()
        assertFalse(s.hasFollowUp(7001))
    }
    @Test fun disabledArtNeverStealsOcrAndDefaultRestUnchanged() {
        val s=TripleFrameScheduler();s.visualResult("readings",1000)
        assertEquals(TripleFrameScheduler.Lane.OCR,s.next(1100,false,true,false))
        assertEquals(650L,TripleScanPolicy.frameRestMs(1500))
        assertEquals(.70f,TripleScanPolicy.VISUAL_MIN_SCORE)
        assertEquals(.08f,TripleScanPolicy.VISUAL_MIN_MARGIN)
        assertEquals(2,TripleScanPolicy.VISUAL_STRONG_READINGS)
    }
    private fun title(vararg codes:String)=TitleLanguageIndex.Evidence(codes.map {
        TitleLanguageIndex.Match("Elfos de Llanowar","Llanowar Elves","Elfos de Llanowar",it)
    })
    @Test fun savedPhotoCanDisambiguatePortugueseWithoutInventingChinese() {
        val t=title("es","pt")
        assertEquals("pt",TriplePhotoLanguagePolicy.choose(TripleLanguageFusion.decide("Adicione",null,t),t))
        assertNull(TriplePhotoLanguagePolicy.choose(TripleLanguageFusion.decide("",null,t),t))
        assertNull(TriplePhotoLanguagePolicy.choose(TripleLanguageFusion.decide("", "zhs", title()),title()))
    }
    @Test fun mismatchedTitleAndRulesCannotModifyLanguage() {
        val t=title("es")
        assertNull(TriplePhotoLanguagePolicy.choose(TripleLanguageFusion.decide("Adicione",null,t),t))
        assertEquals("pt",TriplePhotoLanguagePolicy.choose(TripleLanguageFusion.Decision("pt",false,"rules"),title()))
    }
    @Test fun lateLanguageOnlyUpdatesItsOwnedUneditedUnknownRow() {
        assertTrue(TriplePhotoLanguagePolicy.canApply(true,1,false,null,true))
        assertFalse(TriplePhotoLanguagePolicy.canApply(false,1,false,null,true))
        assertFalse(TriplePhotoLanguagePolicy.canApply(true,0,false,null,true))
        assertFalse(TriplePhotoLanguagePolicy.canApply(true,1,true,null,true))
        assertFalse(TriplePhotoLanguagePolicy.canApply(true,1,false,"it",true))
        assertFalse(TriplePhotoLanguagePolicy.canApply(true,1,false,null,false))
    }
}
