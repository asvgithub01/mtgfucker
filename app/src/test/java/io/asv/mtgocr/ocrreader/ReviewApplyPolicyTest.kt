package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
class ReviewApplyPolicyTest {
    private fun entry(): ScanReviewStore.Entry {
        val card=CardInfo("Card","","","","2")
        card.printingUuid="old"; card.finish="foil"; card.languageCode="es"
        return ScanReviewStore.Entry("photo",card,"ocr","conflict",null,"candidate","New card",123)
    }
    @Test fun titleConflictCannotBeBulkAppliedAndProvisionalLanguageNeedsEdition() {
        val e=entry()
        assertFalse(ReviewApplyPolicy.applicable(e.copy(titleState="conflict")))
        e.card.setProvisionalEdition(true)
        assertFalse(ReviewApplyPolicy.applicable(e.copy(visual=false,language="pt")))
    }
    @Test fun bulkSkipsInconclusiveAndDeduplicatesIdenticalProposals() {
        val first=entry()
        val duplicate=first.copy(key="photo2")
        val pending=first.copy(key="pending",state="inconclusive")
        assertEquals(listOf(first),ReviewApplyPolicy.bulkChoices(listOf(first,duplicate,pending),emptyList()))
        assertTrue(ReviewApplyPolicy.bulkChoices(listOf(pending),emptyList()).isEmpty())
    }
    @Test fun bulkDoesNotGuessBetweenConflictsButKeepsManualChoice() {
        val first=entry(); val alternative=first.copy(key="other",candidateId="different")
        val entries=listOf(first,alternative)
        assertTrue(ReviewApplyPolicy.bulkChoices(entries,emptyList()).isEmpty())
        assertEquals(listOf(alternative),ReviewApplyPolicy.bulkChoices(entries,listOf(alternative)))
    }

    @Test fun onlyActionableChangesCanBeSelected() {
        val e=entry(); assertTrue(ReviewApplyPolicy.applicable(e))
        for(state in listOf("pending","inconclusive","consistent","reviewed")) assertFalse(ReviewApplyPolicy.applicable(e.copy(state=state)))
        assertFalse(ReviewApplyPolicy.applicable(e.copy(candidateId="")))
        assertTrue(ReviewApplyPolicy.applicable(e.copy(source="edscan",visual=false,language="pt")))
        assertFalse(ReviewApplyPolicy.applicable(e.copy(source="edscan",visual=false,language="es")))
    }
    @Test fun edscanVisualProposalDoesNotDependOnOriginalScanner() {
        val e=entry().copy(source="edscan",visual=true)
        assertTrue(ReviewApplyPolicy.applicable(e))
        assertFalse(ReviewApplyPolicy.applicable(e.copy(candidateId="")))
        assertFalse(ReviewApplyPolicy.sameProposal(e,e.copy(visual=false)))
    }
    @Test fun newAttemptInvalidatesOldSelection() {
        val old=entry(); val fresh=old.copy(key="photo.new-attempt",photoKey="photo")
        assertFalse(ReviewApplyPolicy.sameProposal(old,fresh))
    }
    @Test fun languageAndIdentityNeedExplicitChoiceForSameInventoryRow() {
        val visual=entry().copy(source="edscan",visual=true)
        val language=visual.copy(key="language",visual=false,language="pt")
        assertTrue(ReviewApplyPolicy.bulkChoices(listOf(visual,language),emptyList()).isEmpty())
        assertEquals(listOf(language),ReviewApplyPolicy.bulkChoices(listOf(visual,language),listOf(language)))
    }
    @Test fun staleEvidenceAndManualEditsInvalidateSelection() {
        val e=entry()
        assertTrue(ReviewApplyPolicy.sameProposal(e,e.copy()))
        assertFalse(ReviewApplyPolicy.sameProposal(e,e.copy(stamp=124)))
        assertFalse(ReviewApplyPolicy.sameProposal(e,e.copy(candidateId="other")))
        val changed=e.card.snapshotForPersistence(); changed.finish="nonfoil"
        assertFalse(ReviewApplyPolicy.sameProposal(e,e.copy(card=changed)))
        changed.finish="foil"; changed.languageCode="pt"
        assertFalse(ReviewApplyPolicy.sameProposal(e,e.copy(card=changed)))
    }
}
