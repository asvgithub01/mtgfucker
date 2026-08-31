package io.asv.mtgocr.ocrreader.data

/** In-memory fuzzy lookup for imperfect OCR from faded, white-framed and older cards. */
internal class OcrNameIndex(aliases: List<CardNameAliasEntity>) {
    private val byLength = aliases.groupBy { it.normalizedAlias.length }

    fun match(rawQueries: List<String>): CardNameAliasEntity? {
        var best: CardNameAliasEntity? = null
        var bestDistance = Int.MAX_VALUE
        var tiedCanonical = false
        val queries = rawQueries.asSequence()
            .map(MtgJsonParsers::normalizeSearchName)
            .filter { it.length >= 3 }
            .distinct()
            .take(10)
            .toList()

        for (query in queries) {
            val limit = allowedDistance(query.length)
            for (length in (query.length - limit).coerceAtLeast(1)..query.length + limit) {
                for (candidate in byLength[length].orEmpty()) {
                    val distance = boundedLevenshtein(query, candidate.normalizedAlias, limit)
                    if (distance > limit) continue
                    if (distance < bestDistance) {
                        best = candidate
                        bestDistance = distance
                        tiedCanonical = false
                    } else if (distance == bestDistance && best != null &&
                        !candidate.canonicalName.equals(best.canonicalName, ignoreCase = true)) {
                        tiedCanonical = true
                    }
                }
            }
            if (bestDistance == 0) break
        }
        return if (best == null || tiedCanonical) null else best
    }

    private fun allowedDistance(length: Int): Int = when {
        length >= 13 -> 3
        length >= 7 -> 2
        else -> 1
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
