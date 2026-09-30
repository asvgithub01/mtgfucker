package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
class ReliableReviewHistoryTest {
    @Test fun countsKeepConfirmedModifiedMissingAndUnknownSeparate() {
        val items=listOf(
            ReliableReviewHistory.Item("1","a","applied","ok",true),
            ReliableReviewHistory.Item("2","b","applied","ok",false),
            ReliableReviewHistory.Item("3","c","applied","ok",null),
            ReliableReviewHistory.Item("4","d","skipped","weak",null),
            ReliableReviewHistory.Item("5","e","error","catalog",null))
        val batch=ReliableReviewHistory.Batch("batch",1,7,items)
        assertEquals(1,batch.modified); assertEquals(1,batch.confirmed)
        assertEquals(1,batch.unknown); assertEquals(1,batch.manual)
        assertEquals(1,batch.errors); assertEquals(2,batch.missing)
    }
    @Test fun metadataRefreshIsNotAnIdentityCorrection() {
        val before=CardInfo("Card","","","old-image","1")
        val after=before.snapshotForPersistence()
        assertFalse(ReliableReviewHistory.changed(before,after))
        after.languageCode="es"
        assertTrue(ReliableReviewHistory.changed(before,after))
    }
    @Test fun printingCorrectionIsReportedEvenWhenNameUnchanged() {
        val before=CardInfo("Card","","","","1")
        val after=before.snapshotForPersistence(); after.printingUuid="new-printing"
        assertTrue(ReliableReviewHistory.changed(before,after))
    }
}
