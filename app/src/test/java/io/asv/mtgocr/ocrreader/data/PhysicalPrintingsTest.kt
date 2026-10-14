package io.asv.mtgocr.ocrreader.data

import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PhysicalPrintingsTest {
    private fun part(uuid: String, id: String? = "blossom", side: String? = null) = CardPrintingEntity(
        uuid, "blossom", "Blossom-Blessed Angel // Seed Suture", "FRA", "Reality Fracture",
        "3", "2026-10-02", "common", id, "909948", null, null, null, null,
        "nonfoil,foil", "Creature", "", "image", 0, side
    )

    private fun option(group: PhysicalPrintings.Group) = SetCardOption(
        group.primary.uuid, group.primary.name, "FRA", "Reality Fracture", "3", "nonfoil",
        "image", "Creature", "", null, null, printingUuids = group.uuids
    )

    @Test fun preparePartsBecomeOnePrintingPreferringFrontRegardlessOfInputOrder() {
        val front = part("z-front", side = "a")
        val back = part("a-back", side = "b")
        for (input in listOf(listOf(back, front), listOf(front, back))) {
            val group = PhysicalPrintings.groups(input).single()
            assertEquals(front, group.primary)
            assertEquals(setOf(front.uuid, back.uuid), group.uuids)
        }
    }

    @Test fun sameNameAndNumberButDifferentScryfallIdsRemainSeparate() {
        assertEquals(2, PhysicalPrintings.groups(listOf(part("a", "id1"), part("b", "id2"))).size)
    }

    @Test fun divinerNormalAndAlternatePrintingRemainSeparate() {
        val base = part("front-28", "diviner-28", "a").copy(collectorNumber = "28")
        val alt = base.copy(uuid = "front-425", collectorNumber = "425", scryfallId = "diviner-425")
        val groups = PhysicalPrintings.groups(listOf(base, base.copy(uuid = "back-28", side = "b"),
            alt, alt.copy(uuid = "back-425", side = "b")))
        assertEquals(listOf("28", "425"), groups.map { it.primary.collectorNumber })
    }

    @Test fun missingIdsNeverCollapseUnrelatedPrintings() {
        assertEquals(4, PhysicalPrintings.groups(listOf(part("a", null), part("b", null),
            part("c", ""), part("d", " "))).size)
    }

    @Test fun cachedRowsWithoutSideAlreadyCollapseDeterministicallyOffline() {
        val parts = listOf(part("z"), part("a"))
        assertEquals("a", PhysicalPrintings.groups(parts).single().primary.uuid)
        assertEquals(PhysicalPrintings.groups(parts), PhysicalPrintings.groups(parts.reversed()))
        assertTrue(PhysicalPrintings.needsFaceMetadata(parts))
        assertFalse(PhysicalPrintings.needsFaceMetadata(listOf(part("a"))))
        assertFalse(PhysicalPrintings.needsFaceMetadata(listOf(part("a", side = "a"), part("b", side = "b"))))
    }

    @Test fun ownedSecondaryUuidIsVisibleAndDoesNotMutateInventory() {
        val group = PhysicalPrintings.groups(listOf(part("front", side = "a"), part("back", side = "b"))).single()
        val owned = mapOf("back" to "real-collection-item", "other" to "unrelated")
        assertEquals(mapOf("front" to "real-collection-item"),
            PhysicalPrintings.ownedByVisiblePrinting(listOf(option(group)), owned))
        assertEquals(2, owned.size)
        assertEquals("real-collection-item", owned["back"])
    }

    @Test fun ownedBothPartsCountOnceWithoutDeletingEither() {
        val group = PhysicalPrintings.groups(listOf(part("front", side = "a"), part("back", side = "b"))).single()
        val owned = mapOf("front" to "item-1", "back" to "item-2")
        assertEquals(mapOf("front" to "item-1"), PhysicalPrintings.ownedByVisiblePrinting(listOf(option(group)), owned))
        assertEquals(2, owned.size)
        assertTrue(PhysicalPrintings.ownedByVisiblePrinting(listOf(option(group)), emptyMap<String, String>()).isEmpty())
    }

    @Test fun parserRetainsAllUuidsAndExplicitSidesIncludingNullAndMissing() {
        val json = """{"data":{"cards":[
            {"uuid":"a","name":"Angel // Spell","side":"a"},
            {"uuid":"b","name":"Angel // Spell","side":"b"},
            {"uuid":"c","name":"Other","side":null},
            {"uuid":"d","name":"Other"}
        ]}}"""
        val cards = MtgJsonParsers.readSet(Buffer().writeUtf8(json)).cards
        assertEquals(listOf("a", "b", "c", "d"), cards.map { it.uuid })
        assertEquals(listOf("a", "b", null, null), cards.map { it.side })
    }
}
