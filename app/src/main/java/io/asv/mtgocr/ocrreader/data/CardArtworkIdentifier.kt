package io.asv.mtgocr.ocrreader.data

import android.content.Context
import android.graphics.BitmapFactory
import io.asv.mtgocr.ocrreader.CardImageFingerprint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class CardIdentificationCandidate(
    val option: CardEditionOption,
    val distance: Double
)

data class CardIdentificationResult(
    val candidates: List<CardIdentificationCandidate>,
    val confident: Boolean,
    val comparedImages: Int
)

/**
 * Compares the artwork in a camera photo with Scryfall printing images for an OCR-resolved name.
 * OCR narrows a 100k+ image catalog to a small candidate set; artwork then picks the printing.
 */
class CardArtworkIdentifier(
    context: Context,
    private val client: OkHttpClient
) {
    private val fingerprintDirectory = File(context.cacheDir, "card-art-fingerprints").apply { mkdirs() }

    fun identify(
        jpeg: ByteArray,
        options: List<CardEditionOption>,
        lockedSetCodes: Set<String>
    ): CardIdentificationResult {
        val cameraBitmap = decodeSampled(jpeg) ?: return CardIdentificationResult(emptyList(), false, 0)
        val cameraFingerprint = try {
            CardImageFingerprint.fromCamera(cameraBitmap)
        } finally {
            cameraBitmap.recycle()
        }
        val locked = lockedSetCodes.mapTo(HashSet()) { it.trim().uppercase(Locale.US) }
        val unique = options.asSequence()
            .filter { it.imageUrl?.isNotBlank() == true }
            .filter { locked.isEmpty() || it.setCode.uppercase(Locale.US) in locked }
            .distinctBy { it.printingUuid }
            .take(MAX_CANDIDATE_IMAGES)
            .toList()

        val matches = mutableListOf<CardIdentificationCandidate>()
        for (option in unique) {
            if (Thread.currentThread().isInterrupted) break
            val fingerprint = fingerprint(option.imageUrl!!) ?: continue
            matches += CardIdentificationCandidate(
                option,
                CardImageFingerprint.normalizedDistance(cameraFingerprint, fingerprint)
            )
        }
        val ranked = matches.sortedBy { it.distance }
        val best = ranked.firstOrNull()
        val runnerUp = ranked.getOrNull(1)
        val confident = best != null && best.distance <= MAX_CONFIDENT_DISTANCE &&
            (runnerUp == null || runnerUp.distance - best.distance >= MIN_WINNING_MARGIN)
        return CardIdentificationResult(ranked.take(6), confident, matches.size)
    }

    private fun fingerprint(url: String): LongArray? {
        val cache = File(fingerprintDirectory, sha256(url) + ".txt")
        if (cache.isFile) {
            runCatching {
                return cache.readText().split(',').map(String::toLong).toLongArray()
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
            CardImageFingerprint.fromReference(bitmap)
        } finally {
            bitmap.recycle()
        }
        runCatching { cache.writeText(result.joinToString(",")) }
        return result
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
        private const val MAX_CONFIDENT_DISTANCE = .36
        private const val MIN_WINNING_MARGIN = .035
    }
}
