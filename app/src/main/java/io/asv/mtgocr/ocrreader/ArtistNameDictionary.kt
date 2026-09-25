package io.asv.mtgocr.ocrreader

import java.text.Normalizer
import java.util.Locale

/** Global PFR1 vocabulary, never restricted by the proposed printing. */
internal class ArtistNameDictionary(names: List<String>) {
    private val entries = names.distinct().groupBy(::key)
    private fun exact(value: String) = entries[key(value)]?.singleOrNull()

    fun normalize(field: FooterField): FooterField {
        // Corroboration is from another pass of this capture, not another card or catalogue candidate.
        val exactReads = field.observations.mapNotNull { o -> exact(o.value)?.let { it to o.source.pass } }
        return field.copy(observations = field.observations.map { observation ->
            val input = key(observation.value)
            val direct = exact(observation.value)
            val candidates = if (direct != null) listOf(direct) else if (input.length in 8..60 && input.contains(' ')) {
                entries.filter { (name, values) -> values.size == 1 && (
                    oneEdit(input, name) ||
                        (input.length > 2 && input[0].isLetter() && input[1] == ' ' && input.substring(2) == name)
                    ) }.values.map { it.single() }
            } else emptyList()
            val candidate = candidates.singleOrNull()
            val accepted = candidate?.takeIf { direct != null || exactReads.any { (name, pass) ->
                name == candidate && pass != observation.source.pass
            } }
            if (accepted == null || accepted == observation.value) observation else observation.copy(
                value = accepted, originalValue = observation.originalValue ?: observation.value,
                normalization = if (direct != null) "ARTIST_DICTIONARY_EXACT" else "ARTIST_DICTIONARY_CORROBORATED"
            )
        })
    }

    private fun oneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return edits + (a.length - i) + (b.length - j) == 1
    }

    companion object {
        private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    }
}
