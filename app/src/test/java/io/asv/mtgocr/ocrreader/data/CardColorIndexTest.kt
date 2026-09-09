package io.asv.mtgocr.ocrreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class CardColorIndexTest {
    @Test fun readsCompressedOfflineIndexAndOnlyRejectsKnownSingleColorMismatch() {
        val bytes = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).bufferedWriter().use { writer ->
                writer.appendLine("Exile\tW")
                writer.appendLine("Guile\tU")
                writer.appendLine("Supreme Verdict\tWU")
            }
        }.toByteArray()
        val index = CardColorIndex.read(ByteArrayInputStream(bytes))

        assertTrue(index.isCompatible("Exile", "W"))
        assertFalse(index.isCompatible("Guile", "W"))
        assertTrue(index.isCompatible("Supreme Verdict", "W"))
        assertTrue(index.isCompatible("Unknown Future Card", "W"))
        assertTrue(index.isCompatible("Guile", null))
    }
}
