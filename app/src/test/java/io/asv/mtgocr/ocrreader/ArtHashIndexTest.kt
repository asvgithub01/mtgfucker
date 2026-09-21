package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

class ArtHashIndexTest {
    @Test fun readsFaceKeysAndRanksByPHashThenDHash() {
        val source = ByteArrayOutputStream()
        DataOutputStream(source).use { output ->
            output.writeBytes("AHI1")
            output.writeInt(3)
            row(output, 1L, 0L, 255, "A", "abc", "1", UUID(1, 2))
            row(output, 0L, 3L, 0, "B", "def", "2", null)
            row(output, 0L, 1L, 1, "C", "ghi", "3", UUID(3, 4))
        }
        val index = ArtHashIndex.read(ByteArrayInputStream(source.toByteArray()))
        val hits = index.nearest(0L, 0L, 3)

        assertEquals(3, index.size)
        assertEquals(listOf("C", "B", "A"), hits.map { it.name })
        assertEquals("00000000-0000-0000-0000-000000000002-1", hits[0].key)
        assertEquals("00000000-0000-0000-0000-000000000002", hits[0].scryfallId)
        assertEquals(0, hits[0].phashDistance)
        assertEquals(1, hits[0].dhashDistance)
        assertNull(hits[1].illustrationId)
        assertEquals("00000000-0000-0000-0000-000000000002", hits[2].key)
    }

    @Test(expected = IOException::class)
    fun rejectsWrongVersion() {
        ArtHashIndex.read(ByteArrayInputStream(byteArrayOf(65, 72, 73, 50, 0, 0, 0, 1)))
    }

    private fun row(
        output: DataOutputStream, phash: Long, dhash: Long, face: Int,
        name: String, set: String, collector: String, illustration: UUID?
    ) {
        output.writeLong(phash)
        output.writeLong(dhash)
        output.writeLong(0L)
        output.writeLong(2L)
        output.writeByte(face)
        output.writeLong(illustration?.mostSignificantBits ?: 0L)
        output.writeLong(illustration?.leastSignificantBits ?: 0L)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        output.writeShort(nameBytes.size)
        output.write(nameBytes)
        for (value in listOf(set, collector)) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            output.writeByte(bytes.size)
            output.write(bytes)
        }
    }
}
