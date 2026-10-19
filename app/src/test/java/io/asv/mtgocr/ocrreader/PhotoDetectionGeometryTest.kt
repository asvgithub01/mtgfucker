package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PhotoDetectionGeometryTest {
    @Test fun confidenceRejectsAmbiguousAndInvalidScores() {
        assertTrue(PhotoDetectionGeometry.confident(.9f, .7f))
        assertFalse(PhotoDetectionGeometry.confident(.69f, .2f))
        assertFalse(PhotoDetectionGeometry.confident(.9f, .85f))
        assertFalse(PhotoDetectionGeometry.confident(Float.NaN, .1f))
        assertFalse(PhotoDetectionGeometry.confident(.9f, null))
    }
    @Test fun tilesAreBoundedAndIncludeOriginal() {
        val tiles = PhotoDetectionGeometry.tiles()
        assertEquals(36, tiles.size)
        assertEquals(PhotoCardBounds(0f,0f,1f,1f), tiles.first())
        assertTrue(tiles.all { it.left >= 0 && it.top >= 0 && it.right <= 1 && it.bottom <= 1 && it.area > 0 })
    }
    @Test fun duplicatePositionsMergeButSeparateCopiesDoNot() {
        val first = PhotoCardBounds(.1f,.1f,.3f,.7f)
        assertTrue(first.overlaps(PhotoCardBounds(.11f,.11f,.31f,.71f)))
        assertFalse(first.overlaps(PhotoCardBounds(.5f,.1f,.8f,.7f)))
        assertFalse(first.overlaps(PhotoCardBounds(.1f,.1f,.1f,.1f)))
    }
    @Test fun photoCardRoundTripPreservesOptionalGeometry() {
        val card = PhotoScanCard(cardName="Fixture",displayName="Fixture",detectedQuantity=1,
            printingUuid="uuid",setCode="set",setName="Set",collectorNumber="1",finish="nonfoil",
            imageUrl="",typeLine="",rulesText="",price=null,currency="EUR")
        fun copy():PhotoScanCard {
            val bytes=ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(card) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PhotoScanCard }
        }
        assertNull(copy().photoBounds)
        card.photoBounds=PhotoCardBounds(.1f,.2f,.3f,.4f)
        assertEquals(card.photoBounds,copy().photoBounds)
        assertEquals(1L,ObjectStreamClass.lookup(PhotoScanCard::class.java).serialVersionUID)
    }
}
