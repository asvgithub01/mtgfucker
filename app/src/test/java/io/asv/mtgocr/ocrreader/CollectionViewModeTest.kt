package io.asv.mtgocr.ocrreader
import org.junit.Test
import org.junit.Assert.*
class CollectionViewModeTest {
    @Test fun oldListMigratesAndArtworkChoicesRemain() {
        assertEquals(CollectionViewMode.CARD_GRID, CollectionViewMode.sanitize(CollectionViewMode.LIST))
        assertEquals(CollectionViewMode.CARD_GRID, CollectionViewMode.sanitize(-1))
        assertEquals(CollectionViewMode.ARTWORK_WIDE, CollectionViewMode.sanitize(CollectionViewMode.ARTWORK_WIDE))
        assertEquals(CollectionViewMode.ARTWORK_GRID, CollectionViewMode.sanitize(CollectionViewMode.ARTWORK_GRID))
    }
}
