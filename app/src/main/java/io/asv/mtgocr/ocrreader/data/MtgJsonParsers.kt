package io.asv.mtgocr.ocrreader.data

import com.squareup.moshi.JsonReader
import okio.BufferedSource

internal data class ParsedMtgJsonCard(
    val uuid: String,
    val name: String,
    val faceName: String?,
    val number: String,
    val rarity: String,
    val scryfallId: String?,
    val finishes: List<String>,
    val availability: List<String>,
    val type: String,
    val text: String
)

internal data class ParsedMtgJsonSet(
    val code: String,
    val name: String,
    val releaseDate: String,
    val cards: List<ParsedMtgJsonCard>
)

internal object MtgJsonParsers {
    private val combiningMarks = "\\p{M}+".toRegex()
    private val nonNameCharacters = "[^\\p{L}\\p{N}]+".toRegex()
    private val repeatedWhitespace = "\\s+".toRegex()

    data class ResolvedCardName(
        val canonicalName: String,
        val displayName: String,
        val language: String,
        val distance: Int
    )

    data class IndexedCardName(
        val canonicalName: String,
        val displayName: String,
        val language: String
    )

    fun readSet(source: BufferedSource, wantedName: String? = null): ParsedMtgJsonSet {
        val reader = JsonReader.of(source)
        var code = ""
        var name = ""
        var releaseDate = ""
        val cards = mutableListOf<ParsedMtgJsonCard>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "code" -> code = reader.nextString()
                            "name" -> name = reader.nextString()
                            "releaseDate" -> releaseDate = reader.nextString()
                            "cards" -> {
                                reader.beginArray()
                                while (reader.hasNext()) readCard(reader)?.let {
                                    if (wantedName == null || it.name.equals(wantedName, true) || it.faceName.equals(wantedName, true)) cards += it
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ParsedMtgJsonSet(code, name, releaseDate, cards)
    }

    private fun readCard(reader: JsonReader): ParsedMtgJsonCard? {
        var uuid = ""
        var name = ""
        var faceName: String? = null
        var number = ""
        var rarity = ""
        var scryfallId: String? = null
        var type = ""
        var text = ""
        var finishes = emptyList<String>()
        var availability = emptyList<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "uuid" -> uuid = reader.nextString()
                "name" -> name = reader.nextString()
                "faceName" -> faceName = reader.nextString()
                "number" -> number = reader.nextString()
                "rarity" -> rarity = reader.nextString()
                "type" -> type = reader.nextString()
                "text" -> text = reader.nextString()
                "finishes" -> finishes = readStrings(reader)
                "availability" -> availability = readStrings(reader)
                "identifiers" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "scryfallId") scryfallId = reader.nextString() else reader.skipValue()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (uuid.isBlank() || name.isBlank()) null else ParsedMtgJsonCard(
            uuid, name, faceName, number, rarity, scryfallId, finishes, availability, type, text
        )
    }

    private fun readStrings(reader: JsonReader): List<String> = buildList {
        reader.beginArray()
        while (reader.hasNext()) add(reader.nextString())
        reader.endArray()
    }

    fun readBestCardName(source: BufferedSource, query: String): ResolvedCardName? {
        val normalizedQuery = normalizeSearchName(query)
        if (normalizedQuery.isBlank()) return null
        val maxDistance = maxOf(2, normalizedQuery.length / 5)
        var best: ResolvedCardName? = null
        val reader = JsonReader.of(source)
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "data") {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val canonicalKey = reader.nextName()
                reader.beginArray()
                while (reader.hasNext()) {
                    val candidates = readAtomicNames(reader, canonicalKey)
                    for ((candidateName, language) in candidates) {
                        val normalizedCandidate = normalizeSearchName(candidateName)
                        if (kotlin.math.abs(normalizedCandidate.length - normalizedQuery.length) > maxDistance) continue
                        val distance = boundedLevenshtein(normalizedQuery, normalizedCandidate, maxDistance)
                        if (distance <= maxDistance && (best == null || distance < best.distance)) {
                            best = ResolvedCardName(canonicalKey, candidateName, language, distance)
                            if (distance == 0) return best
                        }
                    }
                }
                reader.endArray()
            }
            reader.endObject()
        }
        reader.endObject()
        return best
    }

    /** Matches a small OCR candidate set in a single pass over the local AtomicCards JSON. */
    fun readBestLocalOcrCardName(source: BufferedSource, queries: List<String>): ResolvedCardName? {
        data class Query(val normalized: String, val maxDistance: Int)

        val normalizedQueries = queries.asSequence()
            .map(::normalizeSearchName)
            .filter { it.length >= 3 }
            .distinct()
            .take(8)
            .map { normalized ->
                // Automatic additions must be conservative: one OCR error for short names,
                // two only for names long enough to make accidental matches unlikely.
                Query(normalized, if (normalized.length >= 12) 2 else 1)
            }
            .toList()
        if (normalizedQueries.isEmpty()) return null

        var best: ResolvedCardName? = null
        val reader = JsonReader.of(source)
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "data") {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val canonicalKey = reader.nextName()
                reader.beginArray()
                while (reader.hasNext()) {
                    val candidates = readAtomicNames(reader, canonicalKey)
                    for ((candidateName, language) in candidates) {
                        val normalizedCandidate = normalizeSearchName(candidateName)
                        for (query in normalizedQueries) {
                            if (kotlin.math.abs(normalizedCandidate.length - query.normalized.length) > query.maxDistance) continue
                            val distance = boundedLevenshtein(query.normalized, normalizedCandidate, query.maxDistance)
                            if (distance <= query.maxDistance && (best == null || distance < best.distance)) {
                                best = ResolvedCardName(canonicalKey, candidateName, language, distance)
                                if (distance == 0) return best
                            }
                        }
                    }
                }
                reader.endArray()
            }
            reader.endObject()
        }
        reader.endObject()
        return best
    }

    /** Streams every English and translated name without retaining the full MTGJSON file in RAM. */
    fun streamCardNames(
        source: BufferedSource,
        batchSize: Int = 500,
        onBatch: (List<IndexedCardName>) -> Unit
    ) {
        val batch = ArrayList<IndexedCardName>(batchSize)
        val normalizedInBatch = HashSet<String>(batchSize)

        fun flush() {
            if (batch.isEmpty()) return
            onBatch(batch.toList())
            batch.clear()
            normalizedInBatch.clear()
        }

        val reader = JsonReader.of(source)
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "data") {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val canonicalKey = reader.nextName()
                reader.beginArray()
                while (reader.hasNext()) {
                    readAtomicNames(reader, canonicalKey).forEach { (displayName, language) ->
                        val normalized = normalizeSearchName(displayName)
                        if (normalized.isNotBlank() && normalizedInBatch.add(normalized)) {
                            batch += IndexedCardName(canonicalKey, displayName, language)
                            if (batch.size >= batchSize) flush()
                        }
                    }
                }
                reader.endArray()
            }
            reader.endObject()
        }
        reader.endObject()
        flush()
    }

    private fun readAtomicNames(reader: JsonReader, canonicalKey: String): List<Pair<String, String>> {
        var canonicalName = canonicalKey
        val result = mutableListOf<Pair<String, String>>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> canonicalName = reader.nextString()
                "foreignData" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var foreignName = ""
                        var language = ""
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> foreignName = reader.nextString()
                                "language" -> language = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (foreignName.isNotBlank()) result += foreignName to language
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        result.add(0, canonicalName to "English")
        return result
    }

    internal fun normalizeSearchName(value: String): String {
        val withoutAccents = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
        return withoutAccents.lowercase(java.util.Locale.ROOT)
            .replace(nonNameCharacters, " ")
            .trim()
            .replace(repeatedWhitespace, " ")
    }

    private fun boundedLevenshtein(left: String, right: String, limit: Int): Int {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            var rowMinimum = current[0]
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, substitution)
                rowMinimum = minOf(rowMinimum, current[j + 1])
            }
            if (rowMinimum > limit) return limit + 1
            previous = current
        }
        return previous[right.length]
    }

    data class ParsedPrice(
        val printingUuid: String,
        val finish: String,
        val amount: Double,
        val currency: String,
        val provider: String,
        val date: String
    )

    fun readPrices(source: BufferedSource, wantedUuids: Set<String>): List<ParsedPrice> {
        val reader = JsonReader.of(source)
        val result = mutableListOf<ParsedPrice>()
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "data") {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val uuid = reader.nextName()
                if (uuid in wantedUuids) result += readPriceEntry(reader, uuid) else reader.skipValue()
            }
            reader.endObject()
        }
        reader.endObject()
        return result
    }

    private fun readPriceEntry(reader: JsonReader, uuid: String): List<ParsedPrice> {
        val all = mutableListOf<ParsedPrice>()
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "paper") {
                reader.skipValue()
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val provider = reader.nextName()
                all += readProviderPrice(reader, uuid, provider)
            }
            reader.endObject()
        }
        reader.endObject()
        val preferred = all.filter { it.provider == "cardmarket" }
        return if (preferred.isNotEmpty()) preferred else all.groupBy { it.finish }.mapNotNull { it.value.firstOrNull() }
    }

    private fun readProviderPrice(reader: JsonReader, uuid: String, provider: String): List<ParsedPrice> {
        var currency = ""
        val values = mutableListOf<Triple<String, String, Double>>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "currency" -> currency = reader.nextString()
                "retail" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val finish = reader.nextName()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val date = reader.nextName()
                            val amount = reader.nextDouble()
                            values += Triple(finish, date, amount)
                        }
                        reader.endObject()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return values.groupBy { it.first }.mapNotNull { (_, dated) ->
            dated.maxByOrNull { it.second }?.let { ParsedPrice(uuid, it.first, it.third, currency, provider, it.second) }
        }
    }
}
