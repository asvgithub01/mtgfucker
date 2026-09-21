package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

class ArtPrintingIndexTest {
    @Test fun readsOnlyTheRequestedIllustrationAndBuildsAnOption() {
        val illustration = UUID(1, 2)
        val other = UUID(3, 4)
        val source = ByteArrayOutputStream()
        DataOutputStream(source).use { output ->
            output.writeBytes("APV1")
            output.writeShort(1)
            output.writeInt(3)
            output.writeInt(2)
            output.writeInt(2)
            string8(output, "4ED")
            string16(output, "Fourth Edition")
            string8(output, "1995-04-01")
            string16(output, "Fourth Edition")
            output.writeInt(10)
            output.writeInt(-1)
            listOf("Alabaster Potion", "Poción de alabastro", "1").forEach { string16(output, it) }
            group(output, illustration, UUID(5, 6), UUID(7, 8), 0, 1, 2, 2)
            group(output, other, UUID(9, 10), UUID(11, 12), 0, 0, 2, 1)
        }

        val index = ArtPrintingIndex.read(ByteArrayInputStream(source.toByteArray()))
        val variants = index.variants(illustration.toString())

        assertEquals(2, index.illustrationCount)
        assertEquals(2, index.variantCount)
        assertEquals(1, variants.size)
        assertEquals("es", variants.single().languageCode)
        assertEquals(CardBorderColor.WHITE, variants.single().border)
        val option = variants.single().toEditionOption()
        assertEquals("00000000-0000-0005-0000-000000000006", option.printingUuid)
        assertEquals("Poción de alabastro", option.displayName)
        assertEquals("nonfoil", option.finish)
        assertEquals("10", option.mcmId)
        assertTrue(option.imageUrl!!.contains("/back/"))
        assertTrue(index.variants(UUID(20, 21).toString()).isEmpty())
    }

    private fun group(
        output: DataOutputStream,
        illustration: UUID,
        printing: UUID,
        scryfall: UUID,
        canonical: Int,
        display: Int,
        collector: Int,
        language: Int
    ) {
        output.writeLong(illustration.mostSignificantBits)
        output.writeLong(illustration.leastSignificantBits)
        output.writeInt(1)
        output.writeLong(printing.mostSignificantBits)
        output.writeLong(printing.leastSignificantBits)
        output.writeLong(scryfall.mostSignificantBits)
        output.writeLong(scryfall.leastSignificantBits)
        output.writeInt(canonical)
        output.writeInt(display)
        output.writeInt(collector)
        output.writeShort(0)
        output.writeByte(language)
        output.writeByte(2)
        output.writeByte(ArtPrintingIndex.FINISH_NONFOIL)
        output.writeByte(1)
        output.writeByte(1)
        output.writeByte(0)
        output.writeInt(10)
        output.writeInt(94)
    }

    private fun string8(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeByte(bytes.size)
        output.write(bytes)
    }

    private fun string16(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeShort(bytes.size)
        output.write(bytes)
    }
}
