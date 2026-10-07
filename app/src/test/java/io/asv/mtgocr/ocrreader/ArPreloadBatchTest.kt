package io.asv.mtgocr.ocrreader
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test

class ArPreloadBatchTest {
    private fun card(id: String,group: String,time: Long)=CardInfo("card","","","image","").apply {
        printingUuid=id; addGroup(group); addedAt=time
    }
    @Test fun latestUsesScanGroupNotUnrelatedRecentCard() {
        val cards=listOf(card("a","Cornelius 27",20),card("b","Cornelius 28",30),card("c","Manual",100))
        assertEquals("Cornelius 28",ArPreloadBatch.latestGroup(cards))
    }
    @Test fun deduplicatesExactPrintingAndRejectsMissingImages() {
        val a=card("a","Cornelius 28",10)
        val bad=card("b","Cornelius 28",20).apply { imgPath="" }
        assertEquals(listOf(a),ArPreloadBatch.select(listOf(a,card("a","Cornelius 28",15),bad),"Cornelius 28"))
    }
    @Test fun cacheKeyIndependentOfOrderAndPriceButNotImage() {
        val a=ArPreloadCard("a","url","label","1")
        val b=ArPreloadCard("b","url2","label","2")
        assertEquals(ArPreloadBatch.cacheIdentity(listOf(a,b)),ArPreloadBatch.cacheIdentity(listOf(b,a.copy(priceText="3"))))
        assertNotEquals(ArPreloadBatch.cacheIdentity(listOf(a)),ArPreloadBatch.cacheIdentity(listOf(a.copy(image="new"))))
    }
    @Test fun setCollapsesSamePrintedHalvesButKeepsVariants() {
        val a=io.asv.mtgocr.ocrreader.data.SetCardOption("a","card","FRA","Reality Fracture","3","nonfoil","image","","",null,null)
        assertEquals(listOf(a,a.copy(printingUuid="c",collectorNumber="303")),ArPreloadBatch.selectSet(listOf(
            a.copy(printingUuid="b"),a.copy(printingUuid="c",collectorNumber="303"),a)))
    }
    @Test fun setKeepsDistinctFaceImages() {
        val a=io.asv.mtgocr.ocrreader.data.SetCardOption("a","card","FRA","Reality Fracture","3","nonfoil","front","","",null,null)
        assertEquals(2,ArPreloadBatch.selectSet(listOf(a,a.copy(printingUuid="b",imageUrl="back"))).size)
    }
}
