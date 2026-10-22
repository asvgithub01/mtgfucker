package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FraSupplementTest {
    private val assets=File("src/main/assets/edscan-fra")
    @Test fun packagedSupplementMatchesPinnedHashesAndDimensions() {
        for(asset in CameraVisionAssets.fraAssets) assertTrue(asset.fileName,CameraVisionAssets.valid(File(assets,asset.fileName),asset))
        val ids=Regex("[0-9a-f]{8}-[0-9a-f-]{27}").findAll(File(assets,CameraVisionAssets.FRA_IDS).readText()).map { it.value }.toList()
        assertEquals(CameraVisionAssets.FRA_ROWS,ids.size)
        assertEquals(ids.size,ids.toSet().size)
        val bytes=File(assets,CameraVisionAssets.FRA_EMBEDDINGS).readBytes()
        assertEquals(ids.size*128*2,bytes.size)
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(ids.size) {
            var norm=0f
            repeat(128) { val v=CameraVisionMath.half(buffer.short.toInt() and 65535); assertTrue(v.isFinite()); norm+=v*v }
            assertEquals(1f,norm,.005f)
        }
    }
    @Test fun collectorSupplementIsNormalizedAndIncludesCommanderAndSpecialGuests() {
        val ids=Regex("[0-9a-f]{8}-[0-9a-f-]{27}").findAll(File(assets,CameraVisionAssets.COLLECTOR_IDS).readText()).map { it.value }.toList()
        assertEquals(113, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        val buffer=ByteBuffer.wrap(File(assets,CameraVisionAssets.COLLECTOR_EMBEDDINGS).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        repeat(ids.size) {
            var norm=0f
            repeat(128) { val v=CameraVisionMath.half(buffer.short.toInt() and 65535); assertTrue(v.isFinite()); norm+=v*v }
            assertEquals(1f,norm,.005f)
        }
        val provenance=File(assets,"collector-20260928-provenance.json").readText()
        assertEquals(103, Regex("\"set\": \"frc\"").findAll(provenance).count())
        assertEquals(10, Regex("\"set\": \"spg\"").findAll(provenance).count())
    }
    @Test fun damagedSupplementFailsValidation() {
        val file=File.createTempFile("fra-corrupt",".bin")
        try { file.writeBytes(byteArrayOf(1,2,3)); assertFalse(CameraVisionAssets.valid(file,CameraVisionAssets.fraAssets.first())) }
        finally { file.delete() }
    }
}
