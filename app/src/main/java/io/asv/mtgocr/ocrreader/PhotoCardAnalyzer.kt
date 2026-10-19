package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import io.asv.collectorvision.EdScanInferencePriority
import io.asv.collectorvision.NativeCollectorVisionEngine
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** Bounded still-photo analysis; never writes collection data or disposable EdScan evidence. */
object PhotoCardAnalyzer {
    private val worker = Executors.newSingleThreadExecutor()
    private data class Detection(val id: String, val score: Float, val bounds: PhotoCardBounds)

    fun recognize(context: Context, imagePath: String, callback: (List<PhotoScanCard>, Throwable?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val result = runCatching {
                val bitmap = decode(imagePath)
                val detections = mutableListOf<Detection>()
                try {
                    runBlocking { EdScanInferencePriority.awaitIdle() }
                    NativeCollectorVisionEngine.prepareForCamera(app).use { engine ->
                        for (tile in PhotoDetectionGeometry.tiles()) {
                            runBlocking { EdScanInferencePriority.awaitIdle() }
                            val x = (tile.left * bitmap.width).toInt()
                            val y = (tile.top * bitmap.height).toInt()
                            val w = ((tile.right * bitmap.width).toInt() - x).coerceAtLeast(1)
                            val h = ((tile.bottom * bitmap.height).toInt() - y).coerceAtLeast(1)
                            val crop = Bitmap.createBitmap(bitmap, x, y, w, h)
                            val scan = try { engine.scan(crop) } finally { if (crop !== bitmap) crop.recycle() }
                            val hits = scan.hits.distinctBy { it.cardId }
                            val best = hits.firstOrNull() ?: continue
                            if (!scan.present || !PhotoDetectionGeometry.confident(best.score, hits.getOrNull(1)?.score)) continue
                            val corners = scan.corners
                            if (corners.size != 4 || corners.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) continue
                            val bounds = PhotoCardBounds(
                                (x + corners.minOf { it.x } * w) / bitmap.width,
                                (y + corners.minOf { it.y } * h) / bitmap.height,
                                (x + corners.maxOf { it.x } * w) / bitmap.width,
                                (y + corners.maxOf { it.y } * h) / bitmap.height)
                            detections += Detection(best.cardId, best.score, bounds)
                        }
                    }
                } finally { bitmap.recycle() }
                val distinct = mutableListOf<Detection>()
                detections.sortedByDescending { it.score }.forEach { detection ->
                    if (distinct.none { it.bounds.overlaps(detection.bounds) }) distinct += detection
                }
                val repository = CardRepository.get(app)
                if (distinct.isNotEmpty()) {
                    val pricesReady = CountDownLatch(1)
                    repository.preparePriceIndex { pricesReady.countDown() }
                    pricesReady.await(90, TimeUnit.SECONDS)
                }
                val optionsById = mutableMapOf<String, CardEditionOption?>()
                distinct.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).mapNotNull { detection ->
                    val option = optionsById.getOrPut(detection.id) {
                        val loaded = CountDownLatch(1)
                        var found = emptyList<CardEditionOption>()
                        repository.loadCollectorVisionPrinting(detection.id) { options, error ->
                            if (error == null) found = options
                            loaded.countDown()
                        }
                        // A photo cannot determine finish: never silently pick foil for a higher price.
                        if (loaded.await(90, TimeUnit.SECONDS)) found.firstOrNull { it.finish == "nonfoil" || it.finish == "normal" } else null
                    } ?: return@mapNotNull null
                    PhotoScanCard(cardName = option.cardName, displayName = option.displayName,
                        detectedQuantity = 1, printingUuid = option.printingUuid, setCode = option.setCode,
                        setName = option.setName, collectorNumber = option.collectorNumber, finish = option.finish,
                        imageUrl = option.imageUrl.orEmpty(), typeLine = option.typeLine, rulesText = option.rulesText,
                        price = option.price, currency = option.currency ?: "EUR", mcmId = option.mcmId,
                        mcmMetaId = option.mcmMetaId, mcmSetId = option.mcmSetId, mcmSetIdExtras = option.mcmSetIdExtras,
                        mcmSetName = option.mcmSetName, photoBounds = detection.bounds)
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result.getOrDefault(emptyList()), result.exceptionOrNull()) }
        }
    }

    internal fun decode(path: String): Bitmap {
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, info)
        require(info.outWidth > 0 && info.outHeight > 0)
        var sample = 1
        while (maxOf(info.outWidth, info.outHeight) / sample > 2400) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        }))
        val exif = ExifInterface(path)
        val matrix = Matrix()
        if (exif.isFlipped) matrix.postScale(-1f, 1f)
        matrix.postRotate(exif.rotationDegrees.toFloat())
        if (matrix.isIdentity) return decoded
        return try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
        finally { decoded.recycle() }
    }
}
