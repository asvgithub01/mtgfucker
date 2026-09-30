package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.*
import org.junit.Test
class ReviewProposalCycleTest {
    private fun option(uuid: String, finish: String="nonfoil")=CardEditionOption(uuid,"Name","Name","SET","Set","1","","rare",finish,finish=="foil",null,"","",null,null,null,null)
    @Test fun cyclesOriginalOcrArtOriginal() {
        val sources=listOf("OCR","arte")
        assertEquals("OCR",ReviewProposalCycle.next("",sources))
        assertEquals("arte",ReviewProposalCycle.next("OCR",sources))
        assertEquals("",ReviewProposalCycle.next("arte",sources))
    }
    @Test fun identicalPrintingAndFinishShareOneBadge() {
        val card=option("same")
        assertEquals(listOf("OCR/arte"),ReviewProposalCycle.proposals(card,card).map { it.first })
        assertEquals("",ReviewProposalCycle.next("OCR/arte",listOf("OCR/arte")))
    }
    @Test fun sameNameDoesNotMergeDifferentPrintingsOrFinishes() {
        assertEquals(2,ReviewProposalCycle.proposals(option("a"),option("b")).size)
        assertEquals(2,ReviewProposalCycle.proposals(option("a"),option("a","foil")).size)
    }
    @Test fun missingSourcesAreSkippedWithoutInventingProposal() {
        assertTrue(ReviewProposalCycle.proposals(null,null).isEmpty())
        assertEquals(listOf("arte"),ReviewProposalCycle.proposals(null,option("a")).map { it.first })
        assertEquals("",ReviewProposalCycle.next("",emptyList()))
    }
}
