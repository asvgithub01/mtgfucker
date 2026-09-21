package io.asv.mtgocr.ocrreader

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.PriorityQueue
import java.util.UUID

/** Compact, offline index of unique Scryfall art crops; never contains reference JPEGs. */
internal class ArtHashIndex private constructor(
    private val phashes: LongArray,
    private val dhashes: LongArray,
    private val cardHigh: LongArray,
    private val cardLow: LongArray,
    private val faces: ByteArray,
    private val illustrationHigh: LongArray,
    private val illustrationLow: LongArray,
    private val names: Array<String>,
    private val sets: Array<String>,
    private val collectors: Array<String>
) {
    val size: Int get() = phashes.size

    data class Hit(
        val key: String,
        val illustrationId: String?,
        val name: String,
        val setCode: String,
        val collectorNumber: String,
        val phashDistance: Int,
        val dhashDistance: Int
    ) {
        /** Scryfall card id; face-specific index keys append `-0` or `-1`. */
        val scryfallId: String get() = key.take(UUID_TEXT_LENGTH)
    }

    private data class Score(val index: Int, val p: Int, val d: Int)

    fun nearest(phash: Long, dhash: Long, limit: Int = 5): List<Hit> {
        require(limit in 1..20)
        val worstFirst = compareByDescending<Score> { it.p }.thenByDescending { it.d }
            .thenByDescending { it.index }
        val best = PriorityQueue(worstFirst)
        for (index in phashes.indices) {
            val p = java.lang.Long.bitCount(phash xor phashes[index])
            val worst = best.peek()
            if (best.size == limit && worst != null && p > worst.p) continue
            val d = java.lang.Long.bitCount(dhash xor dhashes[index])
            if (best.size == limit && worst != null &&
                (p > worst.p || (p == worst.p && d >= worst.d))
            ) continue
            best += Score(index, p, d)
            if (best.size > limit) best.poll()
        }
        return best.sortedWith(compareBy<Score> { it.p }.thenBy { it.d }.thenBy { it.index })
            .map { scored ->
                val index = scored.index
                val cardId = UUID(cardHigh[index], cardLow[index]).toString()
                val face = faces[index].toInt()
                val illustrationId = if (
                    illustrationHigh[index] == 0L && illustrationLow[index] == 0L
                ) null else UUID(illustrationHigh[index], illustrationLow[index]).toString()
                Hit(
                    key = if (face == NO_FACE) cardId else "$cardId-$face",
                    illustrationId = illustrationId,
                    name = names[index],
                    setCode = sets[index],
                    collectorNumber = collectors[index],
                    phashDistance = scored.p,
                    dhashDistance = scored.d
                )
            }
    }

    companion object {
        const val ASSET = "art_hash_index.bin"
        private const val UUID_TEXT_LENGTH = 36
        private const val NO_FACE = -1 // 255 read as a signed Byte.
        private const val MAX_ROWS = 100_000

        fun read(source: InputStream): ArtHashIndex = DataInputStream(BufferedInputStream(source)).use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(byteArrayOf(65, 72, 73, 49))) {
                throw IOException("Cabecera del índice de arte no válida")
            }
            val count = input.readInt()
            if (count !in 1..MAX_ROWS) throw IOException("Número de artes no válido: $count")
            val phashes = LongArray(count)
            val dhashes = LongArray(count)
            val cardHigh = LongArray(count)
            val cardLow = LongArray(count)
            val faces = ByteArray(count)
            val illustrationHigh = LongArray(count)
            val illustrationLow = LongArray(count)
            val names = Array(count) { "" }
            val sets = Array(count) { "" }
            val collectors = Array(count) { "" }
            for (index in 0 until count) {
                phashes[index] = input.readLong()
                dhashes[index] = input.readLong()
                cardHigh[index] = input.readLong()
                cardLow[index] = input.readLong()
                faces[index] = input.readByte().also {
                    if (it.toInt() !in listOf(NO_FACE, 0, 1)) {
                        throw IOException("Cara de carta no válida en entrada $index")
                    }
                }
                illustrationHigh[index] = input.readLong()
                illustrationLow[index] = input.readLong()
                names[index] = input.readString(input.readUnsignedShort(), 512)
                sets[index] = input.readString(input.readUnsignedByte(), 32)
                collectors[index] = input.readString(input.readUnsignedByte(), 64)
            }
            if (input.read() != -1) throw IOException("Datos sobrantes en índice de arte")
            ArtHashIndex(
                phashes, dhashes, cardHigh, cardLow, faces, illustrationHigh,
                illustrationLow, names, sets, collectors
            )
        }

        private fun DataInputStream.readString(length: Int, maximum: Int): String {
            if (length > maximum) throw IOException("Texto demasiado largo en índice de arte: $length")
            val bytes = ByteArray(length)
            readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
