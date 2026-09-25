package io.asv.mtgocr.ocrreader

import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Exact printed titles, including every language of shared aliases. Compact UTF-8 storage. */
internal class TitleLanguageIndex private constructor(
    private val bytes: ByteArray, private val offsets: IntArray, val revision: String
) {
    data class Match(val raw: String, val canonical: String, val printed: String, val language: String)
    data class Evidence(val matches: List<Match> = emptyList(), val revision: String = "") {
        val languages: Set<String> get() = matches.map { it.language }.toSet()
        // Alternative exact titles that disagree are not resolved by majority voting.
        val candidates: Set<String> get() = matches.groupBy { normalize(it.raw) }.values
            .map { group -> group.map { it.language }.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
        val conflict: Boolean get() = matches.isNotEmpty() && candidates.isEmpty()
        fun printedName(canonical: String, language: String): String? = matches.firstOrNull {
            normalize(it.canonical) == normalize(canonical) && it.language == language
        }?.printed
    }

    fun lookup(lines: List<String>, identities: List<String>): Evidence {
        val identity = identities.distinctBy(::normalize).singleOrNull() ?: return Evidence(revision = revision)
        val found = mutableListOf<Match>()
        lines.distinct().take(16).forEach { raw ->
            val key = normalize(raw)
            if (key.length < 4) return@forEach
            val encoded = key.toByteArray(Charsets.UTF_8)
            var low = 0; var high = offsets.size - 1
            while (low < high) {
                val mid = (low + high) ushr 1
                if (compareKey(mid, encoded) < 0) low = mid + 1 else high = mid
            }
            var row = low
            while (row < offsets.size - 1 && compareKey(row, encoded) == 0) {
                val fields = String(bytes, offsets[row], offsets[row + 1] - offsets[row] - 1, Charsets.UTF_8).split('\t')
                if (normalize(fields[1]) == normalize(identity)) found += Match(raw, fields[1], fields[2], fields[3])
                row++
            }
        }
        return Evidence(found.distinct(), revision)
    }

    private fun compareKey(row: Int, key: ByteArray): Int {
        var p = offsets[row]; var i = 0
        while (bytes[p] != 9.toByte() && i < key.size) {
            val delta = (bytes[p].toInt() and 255) - (key[i].toInt() and 255)
            if (delta != 0) return delta
            p++; i++
        }
        return if (bytes[p] == 9.toByte()) i - key.size else 1
    }

    companion object {
        const val ASSET = "title_language_index.bin"
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        fun read(input: InputStream): TitleLanguageIndex {
            val bytes = GZIPInputStream(input).use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer); if (n < 0) break
                    require(out.size() + n <= 64 * 1024 * 1024) { "Title index too large" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            val headerEnd = bytes.indexOf(10)
            require(headerEnd == 69 && bytes.lastOrNull() == 10.toByte()) { "Invalid TLV1 header/ending" }
            val header = String(bytes, 0, headerEnd, Charsets.UTF_8)
            require(header.matches(Regex("TLV1\t[0-9a-f]{64}")))
            val rows = ArrayList<Int>(); rows += headerEnd + 1
            var tabs = 0
            for (i in headerEnd + 1 until bytes.size) when (bytes[i]) {
                9.toByte() -> tabs++
                10.toByte() -> { require(tabs == 3); tabs = 0; rows += i + 1 }
            }
            require(rows.size in 2..1_000_001)
            // Validate ordering directly in UTF-8 without allocating 300k temporary strings.
            for (row in 0 until rows.size - 1) {
                var start = rows[row]
                for (i in start until rows[row + 1]) if (bytes[i] == 9.toByte() || bytes[i] == 10.toByte()) {
                    require(i > start) { "Empty title field" }; start = i + 1
                }
                if (row > 0) {
                    var a = rows[row - 1]; var b = rows[row]
                    while (bytes[a] != 9.toByte() && bytes[b] != 9.toByte() && bytes[a] == bytes[b]) { a++; b++ }
                    val order = when {
                        bytes[a] == 9.toByte() -> -1
                        bytes[b] == 9.toByte() -> 1
                        else -> (bytes[a].toInt() and 255) - (bytes[b].toInt() and 255)
                    }
                    require(order <= 0) { "Unsorted title index" }
                }
            }
            return TitleLanguageIndex(bytes, rows.toIntArray(), header.substring(5))
        }
    }
}
