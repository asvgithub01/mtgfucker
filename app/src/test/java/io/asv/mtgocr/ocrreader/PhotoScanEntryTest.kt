package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoScanEntryTest {
    @Test
    fun totalUsesDetectedQuantityForEveryCard() {
        val entry = PhotoScanEntry(
            imagePath = "photo.jpg",
            cards = mutableListOf(
                card("Birds of Paradise", 2, 10.0),
                card("Lightning Bolt", 3, 1.5),
                card("Price missing", 1, null)
            )
        )

        assertEquals(24.5, entry.totalPrice(), 0.0001)
    }

    private fun card(name: String, quantity: Int, price: Double?) = PhotoScanCard(
        cardName = name,
        displayName = name,
        detectedQuantity = quantity,
        printingUuid = name,
        setCode = "TST",
        setName = "Test",
        collectorNumber = "1",
        finish = "nonfoil",
        imageUrl = "",
        typeLine = "",
        rulesText = "",
        price = price,
        currency = "EUR"
    )
}
