package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class CloudWebSnapshotCodecTest {
    @Test
    fun writesBrowserReadableCardmarketIdentifiers() {
        val collection = Biblio("main.Json", "Principal")
        collection.addCard(CardInfo("Jace Beleren", "2.50 EUR", "", "", "1").apply {
            printingUuid = "printing-1"
            mcmId = "17812"
            mcmMetaId = "9183"
            mcmSetId = 84
            mcmSetName = "Lorwyn"
            setCode = "LRW"
            collectorNumber = "71"
            languageCode = "es"
            condition = "near_mint"
            finish = "nonfoil"
        })

        val text = GZIPInputStream(ByteArrayInputStream(CloudWebSnapshotCodec.encode(collection)))
            .reader(Charsets.UTF_8).readText()
        assertTrue(text.contains("\"mcmId\":\"17812\""))
        assertTrue(text.contains("\"mcmMetaId\":\"9183\""))
        assertTrue(text.contains("\"mcmSetId\":84"))
        assertTrue(text.contains("\"setCode\":\"LRW\""))
    }
}
