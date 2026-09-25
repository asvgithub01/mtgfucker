package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Read-only presentation of saved evidence (v1 and v2); never re-runs recognition. */
internal object ScanEvidenceFormatter {
    fun format(context: Context, raw: String): CharSequence {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return context.getString(R.string.scan_debug_evidence_invalid)
        val out = SpannableStringBuilder()
        fun section(title: Int, body: String) {
            if (body.isBlank()) return
            if (out.isNotEmpty()) out.append("\n\n")
            val start = out.length
            out.append(context.getString(title))
            out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.append("\n").append(body)
        }
        fun line(label: Int, value: String) = context.getString(label) + ": " + value
        fun text(obj: JSONObject, key: String) = obj.opt(key)?.takeUnless { it == JSONObject.NULL }
            ?.toString()?.takeIf(String::isNotBlank) ?: "—"
        fun decimal(obj: JSONObject, key: String): String = obj.optDouble(key).takeIf { it.isFinite() }
            ?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: "—"
        fun flag(obj: JSONObject, key: String): String = when {
            !obj.has(key) || obj.isNull(key) -> "—"
            obj.optBoolean(key) -> "✓"
            else -> "✗"
        }

        json.optJSONObject("selectedPrinting")?.let { selected ->
            section(R.string.scan_debug_evidence_selected, listOf(
                text(selected, "displayName").takeUnless { it == "—" } ?: text(selected, "name"),
                "${text(selected, "setName")} (${text(selected, "setCode")}) #${text(selected, "collectorNumber")}",
                line(R.string.scan_debug_evidence_finish, text(selected, "finish")),
                line(R.string.scan_debug_evidence_uuid, text(selected, "uuid")),
                line(R.string.scan_debug_evidence_date, date(json))
            ).joinToString("\n"))
        }
        json.optJSONObject("checks")?.let { checks ->
            section(R.string.scan_debug_evidence_checks,
                listOf(context.getString(R.string.scan_debug_evidence_ocr) to "ocr",
                    context.getString(R.string.scan_debug_evidence_symbol_v1) to "symbol",
                    context.getString(R.string.scan_debug_evidence_symbol_v2) to "symbolV2",
                    context.getString(R.string.scan_debug_evidence_border) to "border",
                    context.getString(R.string.scan_debug_evidence_language) to "language",
                    context.getString(R.string.scan_debug_evidence_auto_add) to "autoAdd")
                    .joinToString(" · ") { (label, key) -> "$label ${flag(checks, key)}" })
        }
        json.optJSONObject("ocr")?.let { ocr ->
            section(R.string.scan_debug_evidence_ocr, listOf(
                line(R.string.scan_debug_evidence_title, values(ocr.optJSONArray("rawTitle"))),
                line(R.string.scan_debug_evidence_names, values(ocr.optJSONArray("matchedNames"))),
                line(R.string.scan_debug_evidence_footer, text(ocr, "rawPrintingLine")),
                context.getString(R.string.scan_debug_evidence_printing,
                    text(ocr, "setCode"), text(ocr, "collectorNumber"), text(ocr, "printingYear")),
                line(R.string.scan_debug_evidence_codes, values(ocr.optJSONArray("setCandidates")))
            ).joinToString("\n"))
        }
        json.optJSONObject("rulesScanner")?.let { rules ->
            section(R.string.rules_scan_evidence, rules.toString(2))
        }
        val candidates = json.optJSONArray("candidates")
        section(R.string.scan_debug_evidence_art, objects(candidates).mapIndexed { i, candidate ->
            buildString {
                append("${i + 1}. ")
                append(context.getString(R.string.scan_debug_evidence_hash,
                    text(candidate, "name"), text(candidate, "phashDistance"), text(candidate, "dhashDistance")))
                append("\n")
                append(context.getString(R.string.scan_debug_evidence_variants,
                    text(candidate, "cachedVariants"), text(candidate, "compatibleVariants")))
                if (text(candidate, "resolvedPrintingUuid") != "—") {
                    append("\n")
                    append(context.getString(R.string.scan_debug_evidence_resolved,
                        text(candidate, "resolvedSetCode"), text(candidate, "resolvedCollectorNumber"),
                        text(candidate, "resolvedLanguage")))
                }
            }
        }.joinToString("\n\n"))
        json.optJSONObject("symbolV2")?.let { symbol ->
            section(R.string.scan_debug_evidence_symbol_v2, buildString {
                val attempt = json.optJSONObject("checks")?.optInt("symbolRetryAttempt") ?: 0
                if (attempt > 0) append(context.getString(R.string.scan_debug_symbol_retry_record, attempt)).append("\n")
                append(context.getString(R.string.scan_debug_hash_symbol_v2_result,
                    text(symbol, "selectedSet"), symbolReason(context, symbol.optString("reason")),
                    objects(symbol.optJSONArray("scores")).firstOrNull()?.let { decimal(it, "distance") } ?: "—",
                    symbol.optLong("elapsedMs")))
                append("\n").append(context.getString(R.string.scan_debug_evidence_thresholds,
                    decimal(symbol, "maxDistance"), decimal(symbol, "minMargin")))
                symbol.optJSONArray("comparedSets")?.let { sets ->
                    append("\n").append(context.getString(R.string.scan_debug_hash_symbol_v2_scope,
                        text(symbol, "cardName"), values(sets, ", ")))
                }
                symbol.optJSONArray("inputSize")?.takeIf { it.optInt(0) > 0 }?.let { size ->
                    append("\n").append(context.getString(R.string.scan_debug_symbol_resolution,
                        size.optInt(0), size.optInt(1), symbol.optInt("segmentationWidth")))
                }
                val noSymbol = symbol.optJSONArray("noSymbolSets")
                if (noSymbol != null && noSymbol.length() > 0) append("\n").append(
                    context.getString(R.string.scan_debug_symbol_without_print, values(noSymbol)))
                val missing = symbol.optJSONArray("missingSets")
                if (missing != null && missing.length() > 0) append("\n").append(
                    line(R.string.scan_debug_evidence_missing_sets, values(missing)))
                objects(symbol.optJSONArray("scores")).forEachIndexed { i, score ->
                    append("\n\n${i + 1}. ")
                    append(context.getString(R.string.scan_debug_evidence_symbol_score,
                        text(score, "set"), decimal(score, "distance"), text(score, "phashDistance"),
                        decimal(score, "silhouetteDistance")))
                    if (score.has("detailCorrelation") && !score.isNull("detailCorrelation"))
                        append("\n").append(context.getString(R.string.scan_debug_symbol_detail_contrast, decimal(score, "detailCorrelation")))
                    append("\n").append(line(R.string.scan_debug_evidence_crop, values(score.optJSONArray("cropXYWH"))))
                    append("\n").append(line(R.string.scan_debug_evidence_hashes,
                        "${text(score, "queryHash")} / ${text(score, "referenceHash")}"))
                }
            })
        }
        json.optJSONObject("symbol")?.let { symbol ->
            val distances = symbol.optJSONObject("distances")
            val scores = distances?.keys()?.asSequence()?.toList().orEmpty()
                .sortedBy { distances!!.optDouble(it, Double.MAX_VALUE) }
                .joinToString("\n") { "$it · ${decimal(distances!!, it)}" }
            if (scores.isNotBlank()) section(R.string.scan_debug_evidence_symbol_v1,
                line(R.string.scan_debug_evidence_confidence, flag(symbol, "reliable")) + "\n" + scores)
        }
        json.optJSONObject("border")?.let { border ->
            val stored = json.optJSONObject("panel")?.optString("border").orEmpty()
            if (text(border, "color") != "—") section(R.string.scan_debug_evidence_border,
                listOf(stored.ifBlank { text(border, "color") },
                    line(R.string.scan_debug_evidence_confidence, decimal(border, "confidence")),
                    line(R.string.scan_debug_evidence_sharpness, decimal(border, "sharpness")),
                    line(R.string.scan_debug_evidence_glare, decimal(border, "glareRatio")))
                    .joinToString("\n"))
        }
        json.optJSONObject("language")?.let { language ->
            val code = text(json, "effectiveLanguage")
            if (code != "—" || text(language, "recognizedText") != "—") section(R.string.scan_debug_evidence_language,
                listOf(code, line(R.string.scan_debug_evidence_confidence, decimal(language, "confidence")),
                    text(language, "recognizedText")).joinToString("\n"))
        }
        json.optJSONObject("timingsMs")?.let { timing ->
            section(R.string.scan_debug_evidence_timing, context.getString(R.string.scan_debug_evidence_times,
                text(timing, "hash"), text(timing, "ocr"), text(timing, "symbol"),
                text(timing, "border"), text(timing, "language"), text(timing, "total")))
        }
        val errors = json.optJSONArray("errors")
        if (errors != null && errors.length() > 0) section(R.string.scan_debug_evidence_errors, values(errors, "\n"))
        return out.takeIf { it.isNotEmpty() } ?: context.getString(R.string.scan_debug_evidence_invalid)
    }

    fun date(json: JSONObject?): String = json?.optLong("capturedAt")?.takeIf { it > 0 }
        ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "—"

    private fun values(array: JSONArray?, separator: String = " / "): String =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.opt(it)?.takeUnless { it == JSONObject.NULL } }
            .joinToString(separator).ifBlank { "—" }

    private fun objects(array: JSONArray?): List<JSONObject> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }

    fun symbolReason(context: Context, value: String): String = when (value) {
        "simbolo_confirmado" -> R.string.scan_debug_evidence_confirmed
        "referencias_incompletas" -> R.string.scan_debug_evidence_missing_sets
        "sin_recorte_de_simbolo" -> R.string.scan_debug_evidence_no_crop
        "identidad_no_confirmada" -> R.string.scan_debug_evidence_no_identity
        "simbolo_reutilizado_chronicles" -> R.string.scan_debug_evidence_reused
        "simbolo_ambiguo" -> R.string.scan_debug_evidence_ambiguous
        "distancia_excesiva" -> R.string.scan_debug_symbol_distance_failed
        "margen_insuficiente" -> R.string.scan_debug_symbol_margin_failed
        "simbolo_no_aplicable" -> R.string.scan_debug_symbol_not_applicable
        "ausencia_posible_no_confirmada" -> R.string.scan_debug_symbol_absence_possible
        else -> null
    }?.let(context::getString) ?: value.ifBlank { "—" }
}
