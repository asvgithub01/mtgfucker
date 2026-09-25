package io.asv.mtgocr.ocrreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local-only rolling evidence, including rejected attempts. Never uploads camera images. */
internal object SymbolScanDiagnostics {
    private const val MAX_ATTEMPTS = 30

    fun save(context: Context, result: HashScanAnalysis.Result) {
        val parent = File(context.filesDir, "symbol_scan_v2").apply { check(isDirectory || mkdirs()) }
        val directory = File(parent, "${System.currentTimeMillis()}-${UUID.randomUUID()}")
        check(directory.mkdir())
        try {
            result.capturedJpeg?.let { File(directory, "card.jpg").writeBytes(it) }
            result.symbolV2?.cropPng?.let { File(directory, "symbol.png").writeBytes(it) }
            val metadata = JSONObject().apply {
                put("schemaVersion", 2)
                put("branch", BuildConfig.GIT_BRANCH)
                put("capturedAt", System.currentTimeMillis())
                put("rawTitle", JSONArray(result.rawTitle))
                put("names", JSONArray(result.names))
                put("printingLine", result.printing?.rawText.orEmpty())
                put("printingEvidence", JSONObject().apply {
                    put("setCode", result.printing?.setCode)
                    put("collectorNumber", result.printing?.collectorNumber)
                    put("language", result.printing?.languageCode)
                })
                put("language", result.effectiveLanguage)
                put("border", result.border?.borderColor?.name.orEmpty())
                put("checks", JSONObject().apply {
                    put("ocr", result.options.ocr)
                    put("symbolV1", result.options.symbol)
                    put("symbolV2", result.options.symbolV2)
                    put("editionPicker", result.options.editionPicker)
                    put("probableEdition", result.options.probableEdition)
                    put("symbolRetryAttempt", result.options.symbolRetryAttempt)
                    put("border", result.options.border)
                    put("language", result.options.language)
                })
                put("symbolV2", result.symbolV2?.json())
                put("elapsedMs", result.elapsedMs)
                put("errors", JSONArray(result.errors))
                put("candidates", JSONArray().also { list -> result.rows.forEach { row ->
                    list.put(JSONObject().apply {
                        put("name", row.candidate.hit.name)
                        put("phashDistance", row.candidate.hit.phashDistance)
                        put("dhashDistance", row.candidate.hit.dhashDistance)
                        put("illustrationId", row.candidate.hit.illustrationId)
                        put("sets", JSONArray(row.editions.map { it.code }.distinct()))
                        put("compatibleVariants", row.compatibleVariants.size)
                        put("resolvedByUniqueArtwork", row.resolvedByUniqueArtwork)
                        put("probableUuid", row.probableVariant?.printingUuid)
                        put("resolvedUuid", row.resolvedVariant?.printingUuid)
                    })
                } })
            }
            File(directory, "metadata.json").writeText(metadata.toString(2))
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
        parent.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }
            ?.drop(MAX_ATTEMPTS)?.forEach { it.deleteRecursively() }
    }
}
