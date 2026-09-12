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

    private fun card(name: String, image: String) = CardInfo(name, "", "", image, "1")
}
