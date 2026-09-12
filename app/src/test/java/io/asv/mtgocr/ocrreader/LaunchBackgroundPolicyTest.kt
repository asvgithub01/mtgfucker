package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchBackgroundPolicyTest {
    @Test
    fun premiumPinWinsOverRandomChoice() {
        val first = card("first", "https://img/first")
        val pinned = card("pinned", "https://img/pinned")

        val chosen = LaunchBackgroundPolicy.choose(
            listOf(first, pinned), pinned.collectionItemId, true
        ) { 0 }

        assertEquals("pinned", chosen?.name)
    }

    @Test
    fun pinIsIgnoredWithoutPremium() {
        val first = card("first", "https://img/first")
        val pinned = card("pinned", "https://img/pinned")

        val chosen = LaunchBackgroundPolicy.choose(
            listOf(first, pinned), pinned.collectionItemId, false
        ) { 0 }

        assertEquals("first", chosen?.name)
    }

    @Test
    fun cardsWithoutImagesAreNeverSelected() {
        assertNull(LaunchBackgroundPolicy.choose(listOf(card("empty", "")), "", true) { 0 })
    }

    @Test
    fun pinnedBackgroundCanBeReusedAcrossLibrarySections() {
        val pinned = card("pinned", "https://img/pinned")

        assertEquals(
            pinned,
            LaunchBackgroundPolicy.pinned(listOf(pinned), pinned.collectionItemId, true)
        )
        assertNull(LaunchBackgroundPolicy.pinned(listOf(pinned), pinned.collectionItemId, false))
    }

    @Test
    fun randomBackgroundPrefersCardsWithArtworkCrop() {
        val imported = card("imported", "https://example.com/card.jpg")
        val scryfall = card(
            "scryfall",
            "https://cards.scryfall.io/normal/front/a/b/example.jpg?123"
        )

        val chosen = LaunchBackgroundPolicy.choose(listOf(imported, scryfall), "", false) { 0 }

        assertEquals("scryfall", chosen?.name)
    }

    @Test
    fun scryfallCardImageIsConvertedToArtworkCrop() {
        assertEquals(
            "https://cards.scryfall.io/art_crop/front/a/b/example.jpg?123",
            LaunchArtworkUrl.resolve(
                "https://cards.scryfall.io/normal/front/a/b/example.jpg?123"
            )
        )
    }

    @Test
    fun artworkCropAndNonScryfallImagesRemainUsable() {
        assertEquals(
            "https://cards.scryfall.io/art_crop/back/a/b/example.jpg?123",
            LaunchArtworkUrl.resolve(
                "https://cards.scryfall.io/art_crop/back/a/b/example.jpg?123"
            )
        )
        assertEquals(
            "https://example.com/card.jpg",
            LaunchArtworkUrl.resolve("https://example.com/card.jpg")
        )
    }

    private fun card(name: String, image: String) = CardInfo(name, "", "", image, "1")
}
