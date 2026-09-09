package io.asv.mtgocr.ocrreader.data

/** In-memory fuzzy lookup for imperfect OCR from faded, white-framed and older cards. */
internal class OcrNameIndex(aliases: List<CardNameAliasEntity>) {
    private val byLength = aliases.groupBy { it.normalizedAlias.length }

    fun match(rawQueries: List<String>): CardNameAliasEntity? {
        var best: CardNameAliasEntity? = null
        var bestScore = Int.MAX_VALUE
        var tiedCanonical = false
        val queries = OcrNameQueries.from(rawQueries)

        for (query in queries) {
            for (length in (query.normalized.length - query.maxDistance).coerceAtLeast(1)..
                query.normalized.length + query.maxDistance) {
                for (candidate in byLength[length].orEmpty()) {
                    val distance = boundedLevenshtein(
                        query.normalized,
                        candidate.normalizedAlias,
                        query.maxDistance
                    )
                    if (distance > query.maxDistance) continue
                    val score = query.score(distance)
                    if (score < bestScore) {
                        best = candidate
                        bestScore = score
                        tiedCanonical = false
                    } else if (score == bestScore && best != null &&
                        !candidate.canonicalName.equals(best.canonicalName, ignoreCase = true)) {
                        tiedCanonical = true
                    }
                }
            }
            if (bestScore == 0) break
        }
        return if (best == null || tiedCanonical) null else best
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
}

/**
 * Builds full title candidates from the pieces delivered by Mobile Vision.
 *
 * The same alias table powers autocomplete and OCR. Mobile Vision can nevertheless split a title
 * into fragments ("lend", "Tar--"). Comparing only each fragment can select a short card name or
 * miss the title entirely, so contiguous fragments are joined and ranked ahead of partial text.
 */
internal object OcrNameQueries {
    data class Query(
        val normalized: String,
        val maxDistance: Int,
        private val omittedCharacters: Int
    ) {
        fun score(distance: Int): Int = distance + omittedCharacters
    }

    fun from(rawQueries: List<String>): List<Query> {
        val fragments = rawQueries.asSequence()
            .map(MtgJsonParsers::normalizeSearchName)
            .filter { it.isNotBlank() }
            .fold(mutableListOf<String>()) { result, value ->
                // A detector occasionally reports the same line as both block and component.
                if (result.lastOrNull() != value) result += value
                result
            }
            .take(8)

        if (fragments.isEmpty()) return emptyList()

        val variants = LinkedHashSet<String>()
        if (fragments.size > 1) {
            // Prefer the complete observed title, then contiguous subphrases, then individual
            // detections. This also covers split words without inventing a remote lookup.
            variants += fragments.joinToString(" ")
            val largestWindow = minOf(4, fragments.size)
            for (windowSize in largestWindow downTo 2) {
                for (start in 0..fragments.size - windowSize) {
                    variants += fragments.subList(start, start + windowSize).joinToString(" ")
                }
            }
        }
        variants += fragments

        val usable = variants.filter { normalized ->
            normalized.length <= 80 && (
                normalized.length >= 3 || normalized.any { character ->
                    Character.isLetter(character) && character.code > 127
                }
            )
        }
        val completeLength = usable.maxOfOrNull(String::length) ?: return emptyList()
        return usable
            .map { normalized ->
                Query(
                    normalized = normalized,
                    maxDistance = allowedDistance(normalized.length),
                    omittedCharacters = (completeLength - normalized.length).coerceAtLeast(0)
                )
            }
            .sortedWith(compareBy<Query> { it.score(0) }.thenByDescending { it.normalized.length })
            .take(16)
    }

    private fun allowedDistance(length: Int): Int = when {
        length >= 13 -> 3
        length >= 7 -> 2
        // Very short CJK titles are valid card names, but accepting even one edit would make a
        // one/two-symbol OCR result dangerously broad. They are therefore exact-match only.
        length <= 2 -> 0
        else -> 1
    }
}
