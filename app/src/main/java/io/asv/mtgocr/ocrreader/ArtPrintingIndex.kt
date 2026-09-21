package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Offline metadata for every paper printing that can share an indexed illustration.
 *
 * APV1 is big-endian: header/counts, variable set table, variable UTF-8 string table,
 * then sorted illustration groups. Each group starts with its 16-byte UUID and u32 count;
 * every variant is a fixed 60-byte row. Strings are decoded only for matching groups, so
 * the 39 MB asset remains a compact byte array instead of hundreds of thousands of objects.
 */
internal class ArtPrintingIndex private constructor(
    private val bytes: ByteArray,
    private val sets: Array<SetInfo>,
    private val stringOffsets: IntArray,
    private val stringLengths: IntArray,
    private val illustrationHigh: LongArray,
    private val illustrationLow: LongArray,
    private val groupOffsets: IntArray,
    private val groupSizes: IntArray,
    val variantCount: Int
) {
    data class SetInfo(
        val code: String,
        val name: String,
        val releaseDate: String,
        val mcmName: String,
        val mcmId: Int?,
        val mcmIdExtras: Int?
    )

    data class Variant(
        val printingUuid: String,
        val scryfallId: String,
        val cardName: String,
        val displayName: String,
        val collectorNumber: String,
        val set: SetInfo,
        val languageCode: String,
        val border: CardBorderColor,
        val finishes: Int,
        val rarity: String,
        val face: Int,
        val mcmId: String?,
        val mcmMetaId: String?
    ) {
        fun preferredFinish(): String = when {
            finishes and FINISH_NONFOIL != 0 -> "nonfoil"
            finishes and FINISH_FOIL != 0 -> "foil"
            finishes and FINISH_ETCHED != 0 -> "etched"
            else -> "nonfoil"
        }

        fun toEditionOption(): CardEditionOption {
            val finish = preferredFinish()
            val side = if (face == 1) "back" else "front"
            val image = "https://cards.scryfall.io/normal/$side/${scryfallId[0]}/${scryfallId[1]}/$scryfallId.jpg"
            return CardEditionOption(
                printingUuid = printingUuid,
                cardName = cardName,
                displayName = displayName,
                setCode = set.code,
                setName = set.name,
                collectorNumber = collectorNumber,
                releaseDate = set.releaseDate,
                rarity = rarity,
                finish = finish,
                isFoil = CardFinish.isFoil(finish),
                imageUrl = image,
                typeLine = "",
                rulesText = "",
                price = null,
                currency = null,
                priceProvider = null,
                priceDate = null,
                mcmId = mcmId,
                mcmMetaId = mcmMetaId,
                mcmSetId = set.mcmId,
                mcmSetIdExtras = set.mcmIdExtras,
                mcmSetName = set.mcmName
            )
        }
    }

    val illustrationCount: Int get() = illustrationHigh.size

    fun variants(illustrationId: String?): List<Variant> {
        val uuid = runCatching { UUID.fromString(illustrationId) }.getOrNull() ?: return emptyList()
        val group = find(uuid.mostSignificantBits, uuid.leastSignificantBits)
        if (group < 0) return emptyList()
        return List(groupSizes[group]) { index -> readVariant(groupOffsets[group] + index * ROW_BYTES) }
    }

    private fun find(high: Long, low: Long): Int {
        var left = 0
        var right = illustrationHigh.lastIndex
        while (left <= right) {
            val middle = (left + right).ushr(1)
            val comparison = compareUuid(illustrationHigh[middle], illustrationLow[middle], high, low)
            when {
                comparison < 0 -> left = middle + 1
                comparison > 0 -> right = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    private fun readVariant(offset: Int): Variant {
        val cursor = Cursor(bytes, offset)
        val printing = cursor.uuid()
        val scryfall = cursor.uuid()
        val cardName = string(cursor.int())
        val displayName = string(cursor.int())
        val collector = string(cursor.int())
        val set = sets.getOrElse(cursor.u16()) { throw IOException("Set fuera del índice de impresiones") }
        val language = LANGUAGES.getOrElse(cursor.u8()) { "" }
        val border = when (cursor.u8()) {
            1 -> CardBorderColor.BLACK
            2 -> CardBorderColor.WHITE
            else -> CardBorderColor.UNKNOWN
        }
        val finishes = cursor.u8()
        val rarity = RARITIES.getOrElse(cursor.u8()) { "" }
        val face = cursor.u8()
        cursor.u8() // padding
        val mcmId = cursor.int().takeIf { it >= 0 }?.toString()
        val mcmMetaId = cursor.int().takeIf { it >= 0 }?.toString()
        return Variant(
            printing.toString(), scryfall.toString(), cardName, displayName, collector,
            set, language, border, finishes, rarity, face, mcmId, mcmMetaId
        )
    }

    private fun string(index: Int): String {
        if (index !in stringOffsets.indices) throw IOException("Texto fuera del índice de impresiones")
        return String(bytes, stringOffsets[index], stringLengths[index], Charsets.UTF_8)
    }

    companion object {
        const val ASSET = "art_printing_index.bin"
        const val FINISH_NONFOIL = 1
        const val FINISH_FOIL = 2
        const val FINISH_ETCHED = 4
        private const val ROW_BYTES = 60
        private const val MAX_ASSET_BYTES = 64 * 1024 * 1024
        private const val MAX_SETS = 10_000
        private const val MAX_STRINGS = 1_000_000
        private const val MAX_GROUPS = 100_000
        private const val MAX_VARIANTS = 1_000_000
        private val LANGUAGES = arrayOf(
            "", "en", "es", "fr", "de", "it", "pt", "ja", "ko", "ru", "zhs", "zht",
            "he", "la", "grc", "ar", "sa", "phyrexian"
        )
        private val RARITIES = arrayOf("", "common", "uncommon", "rare", "mythic", "special", "bonus")

        fun read(source: InputStream): ArtPrintingIndex = source.use {
            val bytes = it.readBytes()
            if (bytes.size !in 18..MAX_ASSET_BYTES) throw IOException("Tamaño del índice de impresiones no válido")
            val cursor = Cursor(bytes)
            if (!cursor.bytes(4).contentEquals(byteArrayOf(65, 80, 86, 49))) {
                throw IOException("Cabecera del índice de impresiones no válida")
            }
            val setCount = cursor.u16().checked("sets", MAX_SETS)
            val stringCount = cursor.int().checked("textos", MAX_STRINGS)
            val groupCount = cursor.int().checked("ilustraciones", MAX_GROUPS)
            val variantCount = cursor.int().checked("variantes", MAX_VARIANTS)
            val sets = Array(setCount) {
                SetInfo(
                    cursor.string8(32), cursor.string16(512), cursor.string8(32), cursor.string16(512),
                    cursor.int().takeIf { value -> value >= 0 },
                    cursor.int().takeIf { value -> value >= 0 }
                )
            }
            val stringOffsets = IntArray(stringCount)
            val stringLengths = IntArray(stringCount)
            repeat(stringCount) { index ->
                val length = cursor.u16()
                stringOffsets[index] = cursor.position
                stringLengths[index] = length
                cursor.skip(length)
            }
            val high = LongArray(groupCount)
            val low = LongArray(groupCount)
            val offsets = IntArray(groupCount)
            val sizes = IntArray(groupCount)
            var counted = 0L
            repeat(groupCount) { index ->
                high[index] = cursor.long()
                low[index] = cursor.long()
                if (index > 0 && compareUuid(high[index - 1], low[index - 1], high[index], low[index]) >= 0) {
                    throw IOException("Ilustraciones desordenadas en el índice")
                }
                val size = cursor.int().checked("variantes por ilustración", MAX_VARIANTS)
                offsets[index] = cursor.position
                sizes[index] = size
                counted += size
                cursor.skip(Math.multiplyExact(size, ROW_BYTES))
            }
            if (counted != variantCount.toLong() || cursor.position != bytes.size) {
                throw IOException("Conteos inconsistentes en el índice de impresiones")
            }
            ArtPrintingIndex(bytes, sets, stringOffsets, stringLengths, high, low, offsets, sizes, variantCount)
        }

        private fun Int.checked(label: String, maximum: Int): Int {
            if (this !in 0..maximum) throw IOException("Número de $label no válido: $this")
            return this
        }

        private fun compareUuid(aHigh: Long, aLow: Long, bHigh: Long, bLow: Long): Int {
            val high = java.lang.Long.compareUnsigned(aHigh, bHigh)
            return if (high != 0) high else java.lang.Long.compareUnsigned(aLow, bLow)
        }
    }

    private class Cursor(private val source: ByteArray, var position: Int = 0) {
        fun skip(count: Int) {
            if (count < 0 || position > source.size - count) throw IOException("Índice de impresiones truncado")
            position += count
        }

        fun bytes(count: Int): ByteArray = source.copyOfRange(position, position + count).also { skip(count) }
        fun u8(): Int = byte().toInt() and 0xff
        fun u16(): Int = (u8() shl 8) or u8()
        fun int(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        fun long(): Long = (int().toLong() shl 32) or (int().toLong() and 0xffffffffL)
        fun uuid(): UUID = UUID(long(), long())
        fun string8(maximum: Int): String = string(u8(), maximum)
        fun string16(maximum: Int): String = string(u16(), maximum)

        private fun byte(): Byte {
            if (position >= source.size) throw IOException("Índice de impresiones truncado")
            return source[position++]
        }

        private fun string(length: Int, maximum: Int): String {
            if (length > maximum) throw IOException("Texto demasiado largo en índice de impresiones")
            val value = String(source, position, length, Charsets.UTF_8)
            skip(length)
            return value
        }
    }
}
