package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashAutoAddPolicyTest {
    @Test fun onlyStrongUnverifiedHitsCanAutoAdd() {
        assertEquals(true, HashAutoAddPolicy.acceptsTopHit(10, false))
        assertEquals(false, HashAutoAddPolicy.acceptsTopHit(11, false))
        assertEquals(true, HashAutoAddPolicy.acceptsTopHit(20, true))
    }

    @Test fun exactResolutionUsesItsUuid() {
        val target = HashAutoAddTarget("Card", "exact", "SET", "12")
        val exact = option("exact", "OTHER", "99", "foil")
        val indexed = option("indexed", "SET", "12")

        assertEquals(exact, HashAutoAddPolicy.preferredOption(target, listOf(indexed, exact)))
    }

    @Test fun topHashHitUsesItsIndexedSetAndCollector() {
        val target = HashAutoAddTarget("Card", null, "ORI", "098")
        val other = option("other", "MMA", "098")
        val indexed = option("indexed", "ori", "98")

        assertEquals(indexed, HashAutoAddPolicy.preferredOption(target, listOf(other, indexed)))
    }

    @Test fun prefersNonfoilForTheIndexedPrinting() {
        val target = HashAutoAddTarget("Card", "same", "SET", "12")
        val foil = option("same", "SET", "12", "foil")
        val nonfoil = option("same", "SET", "12", "nonfoil")

        assertEquals(nonfoil, HashAutoAddPolicy.preferredOption(target, listOf(foil, nonfoil)))
    }

    @Test fun doesNotFallBackToAnUnrelatedEdition() {
        val target = HashAutoAddTarget("Card", null, "ORI", "098")
        assertNull(HashAutoAddPolicy.preferredOption(target, listOf(option("other", "MMA", "1"))))
    }

    private fun option(uuid: String, set: String, collector: String, finish: String = "nonfoil") =
        CardEditionOption(
            printingUuid = uuid,
            cardName = "Card",
            displayName = "Card",
            setCode = set,
            setName = set,
            collectorNumber = collector,
            releaseDate = "2020-01-01",
            rarity = "common",
            finish = finish,
            isFoil = CardFinish.isFoil(finish),
            imageUrl = null,
            typeLine = "",
            rulesText = "",
            price = null,
            currency = null,
            priceProvider = null,
            priceDate = null
        )
}
