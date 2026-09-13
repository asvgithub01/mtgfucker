package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.asv.mtgocr.ocrreader.CardBorderColor
import io.asv.mtgocr.ocrreader.CardEditionVisualFingerprint
import io.asv.mtgocr.ocrreader.CardImageFingerprint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class CardIdentificationCandidate(
    val option: CardEditionOption,
    val distance: Double,
    val artworkDistance: Double = distance,
    val setSymbolDistance: Double = 1.0,
    val referenceBorder: CardBorderColor = CardBorderColor.UNKNOWN,
    val borderMatches: Boolean? = null
)

data class CardIdentificationResult(
    val candidates: List<CardIdentificationCandidate>,
    val confident: Boolean,
    val comparedImages: Int,
    val detectedBorder: CardBorderColor = CardBorderColor.UNKNOWN,
    val detectedBorderConfidence: Double = 0.0,
    val setSymbolCrop: Bitmap? = null
)

/**
 * Compares artwork, set-symbol region and border colour with the Scryfall images for an
 * OCR-resolved name. OCR narrows a 100k+ catalog first, so this slower visual pass only has to
 * distinguish the printings of one card.
 */
class CardArtworkIdentifier(
    context: Context,
    private val client: OkHttpClient
) {
    private val fingerprintDirectory = File(context.cacheDir, "card-edition-fingerprints").apply { mkdirs() }

    fun identify(
        jpeg: ByteArray,
        options: List<CardEditionOption>,
        lockedSetCodes: Set<String>,
        preferFoil: Boolean
    ): CardIdentificationResult {
        val cameraBitmap = decodeSampled(jpeg) ?: return CardIdentificationResult(emptyList(), false, 0)
        val cameraFingerprint = CardEditionVisualFingerprint.fromCamera(cameraBitmap)
        val setSymbolCrop = CardEditionVisualFingerprint.setSymbolCropFromCamera(cameraBitmap)
        cameraBitmap.recycle()
        val locked = ScanSetLockPolicy.expand(lockedSetCodes)
        val unique = options.asSequence()
            .filter { it.imageUrl?.isNotBlank() == true }
            .filter { locked.isEmpty() || it.setCode.uppercase(Locale.US) in locked }
            .groupBy { it.printingUuid }
            .values
            .mapNotNull { finishes ->
                ScanPrintingPolicy.preferred(finishes, preferFoil)
                    ?.takeIf { !preferFoil || it.isFoil }
            }
            .take(MAX_CANDIDATE_IMAGES)
            .toList()

        val matches = mutableListOf<CardIdentificationCandidate>()
        for (option in unique) {
            if (Thread.currentThread().isInterrupted) break
            val fingerprint = fingerprint(option.imageUrl!!) ?: continue
            val artworkDistance = CardImageFingerprint.normalizedDistance(
                cameraFingerprint.artworkHash, fingerprint.artworkHash)
            val symbolDistance = CardImageFingerprint.normalizedDistance(
                cameraFingerprint.setSymbolHash, fingerprint.setSymbolHash)
            val borderMatches = if (
                cameraFingerprint.borderColor == CardBorderColor.UNKNOWN ||
                fingerprint.borderColor == CardBorderColor.UNKNOWN
            ) null else cameraFingerprint.borderColor == fingerprint.borderColor
            matches += CardIdentificationCandidate(
                option,
                CardEditionVisualFingerprint.combinedDistance(
                    artworkDistance,
                    symbolDistance,
                    cameraFingerprint.borderColor,
                    fingerprint.borderColor,
                    cameraFingerprint.borderConfidence,
                    fingerprint.borderConfidence
                ),
                artworkDistance,
                symbolDistance,
                fingerprint.borderColor,
                borderMatches
            )
        }
        val ranked = matches.sortedBy { it.distance }
        val best = ranked.firstOrNull()
        val runnerUp = ranked.getOrNull(1)
        val confident = best != null && best.distance <= MAX_CONFIDENT_DISTANCE &&
            (runnerUp == null || runnerUp.distance - best.distance >= MIN_WINNING_MARGIN)
        return CardIdentificationResult(
            ranked.take(6),
            confident,
            matches.size,
            cameraFingerprint.borderColor,
            cameraFingerprint.borderConfidence,
            setSymbolCrop
        )
    }

    private fun fingerprint(url: String): CardEditionVisualFingerprint? {
        val cache = File(fingerprintDirectory, sha256(url) + ".txt")
        if (cache.isFile) {
            runCatching {
                return decodeFingerprint(cache.readText())
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes() ?: return null
        }
        val bitmap = decodeSampled(bytes) ?: return null
        val result = try {
            CardEditionVisualFingerprint.fromReference(bitmap)
        } finally {
            bitmap.recycle()
        }
        runCatching { cache.writeText(encodeFingerprint(result)) }
        return result
    }

    private fun encodeFingerprint(value: CardEditionVisualFingerprint): String = listOf(
        CACHE_VERSION,
        value.artworkHash.joinToString(","),
        value.setSymbolHash.joinToString(","),
        value.borderColor.name,
        value.borderConfidence.toString()
    ).joinToString("|")

    private fun decodeFingerprint(value: String): CardEditionVisualFingerprint {
        val fields = value.split('|')
        require(fields.size == 5 && fields[0] == CACHE_VERSION)
        return CardEditionVisualFingerprint(
            fields[1].split(',').map(String::toLong).toLongArray(),
            fields[2].split(',').map(String::toLong).toLongArray(),
            CardBorderColor.valueOf(fields[3]),
            fields[4].toDouble()
        )
    }

    private fun decodeSampled(bytes: ByteArray): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1_600 || bounds.outHeight / sample > 1_600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        })
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_CANDIDATE_IMAGES = 48
        private const val CACHE_VERSION = "v2"
        private const val MAX_CONFIDENT_DISTANCE = .40
        private const val MIN_WINNING_MARGIN = .025
    }
}
