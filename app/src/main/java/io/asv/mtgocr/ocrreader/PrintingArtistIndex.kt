package io.asv.mtgocr.ocrreader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** PFR1: UUID printing + UUID illustration -> MTGJSON artist. Also supplies the global artist vocabulary. */
internal class PrintingArtistIndex private constructor(
    private val bytes: ByteArray, private val offset: Int, private val count: Int,
    private val artists: List<String>, val sourceSha256: String
) {
    val dictionary by lazy { ArtistNameDictionary(artists) }

    fun artist(printing: String, illustration: String): String? {
        val key = try {
            val p = UUID.fromString(printing); val a = UUID.fromString(illustration)
            ByteBuffer.allocate(32).putLong(p.mostSignificantBits).putLong(p.leastSignificantBits)
                .putLong(a.mostSignificantBits).putLong(a.leastSignificantBits).array()
        } catch (_: IllegalArgumentException) { return null }
        var low = 0; var high = count - 1
        while (low <= high) {
            val mid = (low + high).ushr(1); val at = offset + mid * ROW_BYTES
            val comparison = compare(bytes, at, key, 0)
            if (comparison < 0) low = mid + 1 else if (comparison > 0) high = mid - 1
            else return artists[ByteBuffer.wrap(bytes).getInt(at + 32)]
        }
        return null
    }
    companion object {
        const val ASSET = "printing_artist_index.bin"
        private const val ROW_BYTES = 36
        private const val MAX_BYTES = 16 * 1024 * 1024
        fun key(printing: String, illustration: String) = "$printing/$illustration"
        fun sha256(source: InputStream): ByteArray = source.use {
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
            while (true) { val n = it.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest()
        }
        private fun compare(a: ByteArray, ai: Int, b: ByteArray, bi: Int): Int {
            for (i in 0 until 32) {
                val difference = (a[ai + i].toInt() and 255) - (b[bi + i].toInt() and 255)
                if (difference != 0) return difference
            }
            return 0
        }
        fun read(source: InputStream, printingIndexSha256: ByteArray): PrintingArtistIndex = source.use {
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer); if (n < 0) break
                if (output.size() + n > MAX_BYTES) throw IOException("Artist index too large")
                output.write(buffer, 0, n)
            }
            val bytes = output.toByteArray()
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(4).also(input::readFully)
            if (!magic.contentEquals(byteArrayOf(80, 70, 82, 49))) throw IOException("Invalid artist schema")
            val boundHash = ByteArray(32).also(input::readFully)
            if (!boundHash.contentEquals(printingIndexSha256)) throw IOException("Artist index / printing index mismatch")
            val sourceHash = ByteArray(32).also(input::readFully).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
            val artistCount = input.readInt(); val count = input.readInt()
            if (artistCount !in 0..10000 || count !in 0..400000) throw IOException("Invalid artist counts")
            val artists = List(artistCount) {
                val length = input.readUnsignedShort()
                if (length !in 1..1024) throw IOException("Invalid artist name length")
                String(ByteArray(length).also(input::readFully), Charsets.UTF_8)
            }
            val offset = bytes.size - input.available()
            if (bytes.size.toLong() != offset.toLong() + count.toLong() * ROW_BYTES) throw IOException("Invalid artist rows")
            val data = ByteBuffer.wrap(bytes)
            for (i in 0 until count) {
                val at = offset + i * ROW_BYTES
                if (data.getInt(at + 32) !in artists.indices) throw IOException("Invalid artist reference")
                if (i > 0 && compare(bytes, at - ROW_BYTES, bytes, at) >= 0) throw IOException("Unsorted artist keys")
            }
            PrintingArtistIndex(bytes, offset, count, artists, sourceHash)
        }
    }
}
