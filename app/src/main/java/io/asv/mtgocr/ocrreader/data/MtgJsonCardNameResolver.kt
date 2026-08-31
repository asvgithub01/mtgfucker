package io.asv.mtgocr.ocrreader.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.File
import java.util.concurrent.TimeUnit

/** Resolves localized/OCR names to MTGJSON canonical English names. */
class MtgJsonCardNameResolver(
    context: Context,
    private val dao: CardDao,
    private val client: OkHttpClient
) {
    data class Resolution(
        val canonicalName: String,
        val displayName: String,
        val language: String
    )

    private val cacheDirectory = File(context.cacheDir, "mtgjson").apply { mkdirs() }
    private val atomicCardsFile = File(cacheDirectory, "AtomicCards.json.gz")
    private val predictionIndexMarker = File(cacheDirectory, "AtomicCards.names-indexed")
    @Volatile private var ocrNameIndex: OcrNameIndex? = null

    fun cached(cardName: String): Resolution? {
        return dao.cardNameAlias(MtgJsonParsers.normalizeSearchName(cardName))?.toResolution()
    }

    fun resolveLocalOcrCandidates(cardNames: List<String>): Resolution? {
        cardNames.asSequence().mapNotNull(::cached).firstOrNull()?.let { return it }
        ocrNameIndex?.match(cardNames)?.let { return it.toResolution() }
        if (!atomicCardsFile.exists()) return null
        val parsed = atomicCardsFile.source().buffer().use { compressed ->
            GzipSource(compressed).buffer().use { source ->
                MtgJsonParsers.readBestLocalOcrCardName(source, cardNames)
            }
        } ?: return null
        return Resolution(parsed.canonicalName, parsed.displayName, parsed.language).also {
            saveAliases(parsed.displayName, it)
        }
    }

    /** Builds the Room prefix index only from the already downloaded local JSON. */
    fun preparePredictionIndex(): Boolean {
        if (!atomicCardsFile.exists()) return dao.cardNameAliasCount() > 0
        val sourceVersion = atomicCardsFile.lastModified().toString()
        val indexCurrent = predictionIndexMarker.takeIf { it.isFile }?.readText() == sourceVersion &&
            dao.cardNameAliasCount() > 0
        if (indexCurrent) {
            prepareOcrNameIndex()
            return true
        }

        val indexedAt = System.currentTimeMillis()
        atomicCardsFile.source().buffer().use { compressed ->
            GzipSource(compressed).buffer().use { source ->
                MtgJsonParsers.streamCardNames(source) { names ->
                    dao.saveCardNameAliases(names.map { name ->
                        CardNameAliasEntity(
                            normalizedAlias = MtgJsonParsers.normalizeSearchName(name.displayName),
                            canonicalName = name.canonicalName,
                            displayName = name.displayName,
                            language = name.language,
                            updatedAt = indexedAt
                        )
                    })
                }
            }
        }
        predictionIndexMarker.writeText(sourceVersion)
        prepareOcrNameIndex()
        return true
    }

    private fun prepareOcrNameIndex() {
        if (ocrNameIndex == null) ocrNameIndex = OcrNameIndex(dao.allCardNameAliases())
    }

    fun suggestions(query: String, limit: Int): List<Resolution> {
        val normalized = MtgJsonParsers.normalizeSearchName(query)
        if (normalized.length < 2) return emptyList()
        val upperBound = normalized + '\uFFFF'
        val aliases = dao.cardNameAliasesByPrefix(normalized, upperBound, limit * 4)
            .map { it.toResolution() }
        val knownDisplays = aliases.mapTo(HashSet()) { it.displayName.lowercase(java.util.Locale.ROOT) }
        val printingSuggestions = if (aliases.size < limit) {
            dao.printingNamesByPrefix(normalized, upperBound, limit * 2)
                .filter { knownDisplays.add(it.lowercase(java.util.Locale.ROOT)) }
                .map { Resolution(it, it, "English") }
        } else {
            emptyList()
        }
        return (aliases + printingSuggestions)
            .distinctBy { it.displayName.lowercase(java.util.Locale.ROOT) }
            .take(limit)
    }

    fun remember(cardName: String, canonicalName: String) {
        saveAliases(
            originalQuery = cardName,
            resolution = Resolution(canonicalName, canonicalName, "English")
        )
    }

    fun resolve(cardName: String): Resolution? {
        cached(cardName)?.let { return it }
        ensureAtomicCardsFile()
        val parsed = atomicCardsFile.source().buffer().use { compressed ->
            GzipSource(compressed).buffer().use { source ->
                MtgJsonParsers.readBestCardName(source, cardName)
            }
        } ?: return null
        val resolution = Resolution(parsed.canonicalName, parsed.displayName, parsed.language)
        saveAliases(cardName, resolution)
        return resolution
    }

    private fun saveAliases(originalQuery: String, resolution: Resolution) {
        val now = System.currentTimeMillis()
        val aliases = listOf(
            Triple(originalQuery, resolution.displayName, resolution.language),
            Triple(resolution.displayName, resolution.displayName, resolution.language),
            Triple(resolution.canonicalName, resolution.canonicalName, "English")
        )
        aliases.forEach { (alias, displayName, language) ->
            val normalized = MtgJsonParsers.normalizeSearchName(alias)
            if (normalized.isNotBlank()) {
                dao.saveCardNameAlias(
                    CardNameAliasEntity(
                        normalized,
                        resolution.canonicalName,
                        displayName,
                        language,
                        now
                    )
                )
            }
        }
    }

    private fun ensureAtomicCardsFile() {
        val stale = !atomicCardsFile.exists() ||
            System.currentTimeMillis() - atomicCardsFile.lastModified() > TimeUnit.DAYS.toMillis(30)
        if (!stale) return
        try {
            downloadAtomicCards()
        } catch (error: Exception) {
            if (!atomicCardsFile.exists()) throw error
        }
    }

    private fun downloadAtomicCards() {
        val request = Request.Builder()
            .url("https://mtgjson.com/api/v5/AtomicCards.json.gz")
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        val temporary = File(cacheDirectory, "AtomicCards.json.gz.part")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MTGJSON nombres devolvió HTTP ${response.code}")
            val body = response.body ?: error("MTGJSON nombres devolvió una respuesta vacía")
            temporary.outputStream().buffered().use { output -> body.byteStream().copyTo(output) }
        }
        if (atomicCardsFile.exists()) atomicCardsFile.delete()
        check(temporary.renameTo(atomicCardsFile)) { "No se pudo guardar el catálogo multidioma de MTGJSON" }
    }

    private fun CardNameAliasEntity.toResolution() = Resolution(canonicalName, displayName, language)
}
