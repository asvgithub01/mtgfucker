package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
class ReviewPresentationPolicyTest {
    @Test fun tabsSeparateWholePhotosAndFilterEachOrigin() {
        val pending=row("pending","conflict",true)
        val resolved=row("resolved","reviewed",false).copy(photoKey="done",source="ocr")
        val mixed=row("partial","reviewed",false).copy(photoKey=pending.photoKey)
        val all=listOf(pending,mixed,resolved)
        assertEquals(listOf(pending),ReviewPresentationPolicy.forTab(all,false,"all"))
        assertEquals(listOf(resolved),ReviewPresentationPolicy.forTab(all,true,"all"))
        assertEquals(listOf(pending),ReviewPresentationPolicy.forTab(all,false,"edscan"))
        assertTrue(ReviewPresentationPolicy.forTab(all,false,"ocr").isEmpty())
        assertEquals(listOf(resolved),ReviewPresentationPolicy.forTab(all,true,"ocr"))
        assertTrue(ReviewPresentationPolicy.forTab(all,true,"edscan").isEmpty())
    }
    @Test fun reopenedPhotoReturnsToPendingUntilSaved() {
        val entry=row("resolved","reviewed",false)
        assertEquals(listOf(entry),ReviewPresentationPolicy.forTab(listOf(entry),false,"edscan",setOf(entry.photoKey)))
        assertTrue(ReviewPresentationPolicy.forTab(listOf(entry),true,"all",setOf(entry.photoKey)).isEmpty())
    }
    @Test fun emptyTabsNeverInventRows() {
        for(resolved in listOf(false,true)) for(source in listOf("all","edscan","ocr"))
            assertTrue(ReviewPresentationPolicy.forTab(emptyList(),resolved,source).isEmpty())
    }
    private fun row(key:String,state:String,visual:Boolean)=ScanReviewStore.Entry(key,CardInfo("A","","","","1"),"edscan",state,null,"","",1,photoKey="photo",visual=visual)
    @Test fun closesOnlyWhenAllPhotoEvidenceIsReviewed() {
        assertFalse(ReviewPresentationPolicy.reviewed(emptyList()))
        assertFalse(ReviewPresentationPolicy.reviewed(listOf(row("art","conflict",true),row("language","reviewed",false))))
        assertTrue(ReviewPresentationPolicy.reviewed(listOf(row("art","reviewed",true),row("language","reviewed",false))))
    }
    @Test fun weakCandidateIsNotTreatedAsVerifiedOrApplicable() {
        for(same in listOf(true,false)) assertEquals("inconclusive",ReviewPresentationPolicy.candidateState(false,same))
        assertEquals("consistent",ReviewPresentationPolicy.candidateState(true,true))
        assertEquals("conflict",ReviewPresentationPolicy.candidateState(true,false))
        assertFalse(ReviewApplyPolicy.applicable(row("art","inconclusive",true).copy(candidateId="uuid",candidateLabel="Proposal")))
    }
    @Test fun onePhotoCombinesEvidenceWithoutInventingAnApplyKey() {
        val art=row("art","consistent",true)
        val language=row("language","conflict",false)
        assertEquals(listOf(language),ReviewPresentationPolicy.primary(listOf(art,language)))
    }
    @Test fun visualConflictIsReviewedBeforeLanguageAndDifferentPhotosRemainSeparate() {
        val art=row("art","conflict",true)
        val language=row("language","conflict",false)
        val other=language.copy(key="other",photoKey="photo2")
        assertEquals(listOf(art,other),ReviewPresentationPolicy.primary(listOf(language,art,other)))
    }
}
