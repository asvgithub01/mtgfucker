package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class FraPackagedIndexTest {
    @Test fun packagedHashAndPrintingIndicesIncludeRealityFracture() {
        val root=File("src/main/assets")
        val hash=ArtHashIndex.read(File(root,"art_hash_index.bin").inputStream())
        val matches=hash.nearest(java.lang.Long.parseUnsignedLong("249c1d975d2d2d23",16),java.lang.Long.parseUnsignedLong("8573707133313031",16))
        assertTrue(matches.any { it.illustrationId=="fb7ded10-a9bf-4600-9770-c942f67ae0fe" && it.phashDistance==0 && it.dhashDistance==0 })
        assertTrue(hash.size>51990)
        val prints=ArtPrintingIndex.read(File(root,"art_printing_index.bin").inputStream()).variants("fb7ded10-a9bf-4600-9770-c942f67ae0fe")
        assertTrue(prints.any { it.set.code=="FRA" && it.printingUuid=="98144bab-0002-58e6-a809-93c937e71d8f" })
    }
}
