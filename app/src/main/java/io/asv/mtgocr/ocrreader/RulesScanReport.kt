package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only raw variants enter this policy: no legacy uniqueness, default language or retained edition. */
internal object RulesScanReport {
    fun automatic(result: HashScanAnalysis.Result): RulesAutoAddPolicy.Decision = RulesAutoAddPolicy.evaluate(
        result.rows.map { row -> RulesAutoAddPolicy.Artwork(row.candidate.hit.illustrationId,
            row.candidate.hit.name, row.candidate.hit.phashDistance, row.candidate.hit.dhashDistance, row.variants) },
        result.names, decision(result), result.structuredPrinting, result.options.rulesScanner && result.options.rulesAutoAdd,
        result.options.rulesPreferredFinish, result.errors.isNotEmpty(), result.capturedJpeg != null, result.catalogSetTypes
    )

    fun decision(result: HashScanAnalysis.Result) = RulesScanPolicy.evaluate(RulesScanPolicy.Input(
        result.names, result.rows.map { RulesScanPolicy.Art(it.candidate.hit.name,
            it.candidate.hit.phashDistance, it.candidate.hit.dhashDistance) },
        result.rows.flatMap { it.variants }.map {
            RulesScanPolicy.Printing(it.printingUuid, it.cardName, it.set.code, it.collectorNumber)
        }, result.structuredPrinting, result.language?.languageCode.orEmpty(),
        result.language?.confidence ?: 0f, result.language?.recognizedText.orEmpty(), result.titleLanguage
    ))

    fun historicalComparison(result: HashScanAnalysis.Result) = HistoricalPrintingComparison.evaluate(
        result.rows.map { row -> RulesAutoAddPolicy.Artwork(row.candidate.hit.illustrationId,
            row.candidate.hit.name, row.candidate.hit.phashDistance, row.candidate.hit.dhashDistance, row.variants) },
        result.names, result.structuredPrinting, result.catalogSetTypes, result.artistReferences,
        decision(result).language, decision(result).languageState
    )

    fun json(result: HashScanAnalysis.Result): JSONObject {
        val decision = decision(result)
        val automatic = automatic(result)
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("captureQuality", JSONObject(result.captureQuality))
            put("occlusionState", "UNASSESSED")
            put("policyVersion", "rules-boundaries-ab-18")
            put("scanId", result.scanId)
            put("rulesAttempt", result.options.rulesAttempt)
            put("source", "rules_scanner")
            put("boundaryMode", result.options.boundaryMode)
            put("boundaryQuad", result.options.boundaryQuad)
            put("branch", BuildConfig.GIT_BRANCH)
            put("capturedAt", System.currentTimeMillis())
            put("coverage", decision.coverage)
            put("artIndex", ArtHashIndex.ASSET)
            put("printingIndex", ArtPrintingIndex.ASSET)
            put("catalogRevision", "UNVERSIONED_ASSET")
            put("status", decision.status.name)
            put("canAutoAdd", automatic.canAutoAdd)
            put("autoAdd", JSONObject().put("enabled", result.options.rulesAutoAdd)
                .put("reason", automatic.reason).put("uuid", automatic.variant?.printingUuid ?: JSONObject.NULL)
                .put("preferredFinish", result.options.rulesPreferredFinish)
                .put("finishProvenance", "SCANNER_PREFERENCE")
                .put("identityProvenance", "ART_HASH_WITH_OCR_CONTRADICTION_CHECK"))
            put("rules", JSONArray(decision.ruleIds))
            put("identities", JSONArray(decision.identities))
            put("candidateUuids", JSONArray(decision.printingUuids))
            put("observedLanguage", decision.language)
            put("languageState", decision.languageState.name)
            put("titleLanguage", JSONObject().put("revision", result.titleLanguage.revision)
                .put("coverage", "ALLPRINTINGS_EXACT_TITLES_NOT_COMPLETE_LANGUAGE_PROOF")
                .put("candidates", JSONArray(result.titleLanguage.candidates.toList().sorted()))
                .put("conflict", result.titleLanguage.conflict)
                .put("matches", JSONArray().apply { result.titleLanguage.matches.forEach {
                    put(JSONObject().put("raw", it.raw).put("canonical", it.canonical)
                        .put("printed", it.printed).put("language", it.language))
                } }))
            put("finishState", ScanReadState.UNREADABLE.name)
            put("footerState", result.structuredPrinting.state.name)
            val comparison = historicalComparison(result)
            put("historicalComparison", JSONObject().put("state", comparison.state)
                .put("identity", comparison.identity ?: JSONObject.NULL).put("identitySource", comparison.identitySource)
                .put("coverage", "VISUAL_SHORTLIST_ONLY").put("canAutoAdd", false)
                .put("artistComparison", if (result.artistReferenceRevision.isBlank()) "REFERENCE_UNAVAILABLE" else "EXACT_PRINTING_AND_ARTWORK")
                .put("artistReferenceRevision", result.artistReferenceRevision)
                .put("artistReferenceAsset", PrintingArtistIndex.ASSET)
                .put("printedTotalComparison", "CATALOG_FIELD_UNAVAILABLE")
                .put("copyrightComparison", "RELEASE_YEAR_HINT_ONLY")
                .put("languageComparison", "INDEXED_PRINTING_POSITIVE_ONLY")
                .put("candidates", JSONArray().apply { comparison.candidates.forEach { candidate ->
                    put(JSONObject().put("uuid", candidate.uuid).put("set", candidate.set)
                        .put("number", candidate.number).put("releaseYear", candidate.releaseYear)
                        .put("numberMatch", candidate.numberMatch.name).put("releaseYearMatch", candidate.releaseYearMatch.name)
                        .put("retainedFooterPossible", candidate.retainedFooterPossible)
                        .put("artist", candidate.artist).put("artistMatch", candidate.artistMatch.name)
                        .put("indexedLanguages", JSONArray(candidate.languages)).put("languageMatch", candidate.languageMatch.name))
                } }))
            put("historicalFooter", JSONObject().apply {
                val historical = result.structuredPrinting.historical
                listOf("years" to historical.years, "artists" to historical.artists, "fractions" to historical.fractions,
                    "collectorNumbers" to historical.collectorNumbers, "printedTotals" to historical.printedTotals).forEach { (key, field) ->
                    put(key, JSONObject().put("state", field.state.name).put("observations", JSONArray().apply {
                        field.observations.forEach { observation ->
                            val line = observation.source
                            put(JSONObject().put("value", observation.value).put("pass", line.pass)
                                .put("originalValue", observation.originalValue ?: JSONObject.NULL)
                                .put("normalization", observation.normalization ?: JSONObject.NULL)
                                .put("raw", line.text).put("box", JSONArray(listOf(line.left, line.top, line.right, line.bottom))))
                        }
                    }))
                }
            })
            put("legacyFooterCandidates", JSONArray().apply {
                result.structuredPrinting.legacyCandidates.forEach { candidate ->
                    put(JSONObject().put("number", candidate.number).put("total", candidate.total).put("year", candidate.year))
                }
            })
            put("completeTuplePasses", JSONArray(result.structuredPrinting.completeTuplePasses.toList()))
            put("corroboratedTuple", result.structuredPrinting.corroboratedTuple)
            val candidateCodes = result.rows.flatMap { it.variants }.map { it.set.code.uppercase(java.util.Locale.ROOT) }.toSet()
            put("candidateSetTypes", JSONObject(result.catalogSetTypes.filterKeys { it in candidateCodes }))
            put("footerCandidates", JSONArray().also { array -> result.structuredPrinting.candidates.forEach {
                array.put(JSONObject().put("set", it.setCode).put("number", it.number ?: JSONObject.NULL).put("language", it.language))
            } })
            put("ocrLines", JSONArray().also { array -> result.structuredPrinting.lines.forEach {
                array.put(JSONObject().put("pass", it.pass).put("text", it.text)
                    .put("box", JSONArray(listOf(it.left, it.top, it.right, it.bottom)))
                    .put("elements", JSONArray().also { words -> it.elements.forEach { word ->
                        words.put(JSONObject().put("text", word.text)
                            .put("box", JSONArray(listOf(word.left, word.top, word.right, word.bottom))))
                    } }))
            } })
            put("artReadReason", RulesArtworkEvidence.reason(result.rows.map { row ->
                RulesAutoAddPolicy.Artwork(row.candidate.hit.illustrationId, row.candidate.hit.name,
                    row.candidate.hit.phashDistance, row.candidate.hit.dhashDistance, row.variants)
            }, result.names))
            put("ocrElapsedMs", result.ocrMs)
            put("titleReadState", when {
                result.names.isNotEmpty() -> "MATCHED"
                result.rawTitle.isNotEmpty() -> "TEXT_UNRESOLVED"
                else -> "NO_TEXT"
            })
            put("rawTitle", JSONArray(result.rawTitle))
            put("matchedNames", JSONArray(result.names))
            put("rulesText", result.language?.recognizedText.orEmpty())
            put("rulesLanguageCode", result.language?.languageCode.orEmpty())
            put("rulesLanguageConfidence", result.language?.confidence ?: 0f)
            put("border", result.border?.borderColor?.name.orEmpty())
            put("artCandidates", JSONArray().also { array -> result.rows.forEach { row ->
                array.put(JSONObject().put("name", row.candidate.hit.name)
                    .put("illustrationId", row.candidate.hit.illustrationId)
                    .put("phash", row.candidate.hit.phashDistance).put("dhash", row.candidate.hit.dhashDistance)
                    .put("variants", JSONArray(row.variants.map { it.printingUuid })))
            } })
            put("elapsedMs", result.elapsedMs)
            put("errors", JSONArray(result.errors))
        }
    }

    /** Preserve exact printing/finish, use observed localized title and available localized image. */
    fun localizedOption(result: HashScanAnalysis.Result, option: CardEditionOption, language: String): CardEditionOption {
        val indexed = result.rows.flatMap { it.variants }.filter {
            it.printingUuid == option.printingUuid && it.languageCode == language
        }.distinctBy { it.scryfallId to it.face }.singleOrNull()?.toEditionOption()
        val printed = result.titleLanguage.printedName(option.cardName, language)
        return option.copy(displayName = printed ?: indexed?.displayName ?: option.displayName,
            imageUrl = indexed?.imageUrl ?: option.imageUrl)
    }

    fun capture(result: HashScanAnalysis.Result, selected: CardEditionOption, language: String,
        automatic: Boolean = false): LegacyCollectionStore.ScanCaptureEvidence? {
        val jpeg = result.capturedJpeg ?: return null
        val metadata = JSONObject().put("schemaVersion", 2).put("source", "rules_scanner")
            .put("capturedAt", System.currentTimeMillis()).put("rulesScanner", json(result))
            .put("selectionProvenance", if (automatic) "AUTO_${RulesScanReport.automatic(result).reason}" else "USER_CONFIRMED")
            .put("finishProvenance", if (automatic) "SCANNER_PREFERENCE" else "USER_CONFIRMED")
            .put("effectiveLanguage", language)
            .put("selectedPrinting", JSONObject().put("uuid", selected.printingUuid)
                .put("name", selected.cardName).put("displayName", selected.displayName)
                .put("setCode", selected.setCode).put("setName", selected.setName)
                .put("collectorNumber", selected.collectorNumber).put("finish", selected.finish)
                .put("language", language).put("imageUrl", selected.imageUrl.orEmpty()))
        return LegacyCollectionStore.ScanCaptureEvidence(metadata.toString(), jpeg)
    }

    /** Called by HashScanAnalysis on its worker, never the camera/UI thread. Private, rolling, no upload. */
    fun save(context: Context, result: HashScanAnalysis.Result) {
        val parent = File(context.filesDir, "rules_scan").apply { check(isDirectory || mkdirs()) }
        val directory = File(parent, "${System.currentTimeMillis()}-${result.scanId}")
        check(directory.mkdir())
        try {
            result.options.boundaryOriginal?.let { File(directory, "original.jpg").writeBytes(it) }
            result.capturedJpeg?.let { File(directory, "card.jpg").writeBytes(it) }
            File(directory, "metadata.json").writeText(json(result).toString(2))
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
        parent.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }
            ?.drop(30)?.forEach { it.deleteRecursively() }
    }
}
