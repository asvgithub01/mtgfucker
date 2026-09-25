package io.asv.mtgocr.ocrreader

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PrintingArtistIndexTest {
    private val hash = ByteArray(32) { 1 }
    private val printing = "00000000-0000-0000-0000-000000000001"
    private val illustration = "00000000-0000-0000-0000-000000000002"
    private fun bytes(): ByteArray = ByteArrayOutputStream().also { out -> DataOutputStream(out).use { d ->
        d.writeBytes("PFR1"); d.write(hash); d.write(ByteArray(32) { 2 }); d.writeInt(1); d.writeInt(1)
        val artist = "Wayne England".toByteArray(); d.writeShort(artist.size); d.write(artist)
        d.writeLong(0); d.writeLong(1); d.writeLong(0); d.writeLong(2); d.writeInt(0)
    } }.toByteArray()
    private fun read(value: ByteArray = bytes()) = PrintingArtistIndex.read(value.inputStream(), hash)
    @Test fun exactPairLookupAndRevision() {
        val index = read()
        assertEquals("Wayne England", index.artist(printing, illustration))
        assertNull(index.artist(illustration, printing))
        assertNull(index.artist(printing, "bad-uuid"))
        assertEquals("02".repeat(32), index.sourceSha256)
    }
    @Test(expected = IOException::class) fun mismatchedPrintingAssetRejected() {
        PrintingArtistIndex.read(bytes().inputStream(), ByteArray(32))
    }
    @Test(expected = IOException::class) fun badSchemaRejected() { read(bytes().also { it[0] = 0 }) }
    @Test(expected = IOException::class) fun truncationRejected() { read(bytes().dropLast(1).toByteArray()) }
    @Test(expected = IOException::class) fun trailingDataRejected() { read(bytes() + byteArrayOf(0)) }
    @Test(expected = IOException::class) fun invalidStringIndexRejected() { read(bytes().also { it[it.lastIndex] = 10 }) }
    @Test(expected = IOException::class) fun invalidCountsRejected() { read(bytes().also { it[68] = 127 }) }
}
