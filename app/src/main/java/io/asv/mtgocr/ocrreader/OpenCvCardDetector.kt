package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class OpenCvDetectedQuad(
    val normalizedCorners: Array<PointF>,
    val confidence: Double,
    val sourceWidth: Int,
    val sourceHeight: Int
) {
    fun cornersFor(width: Int, height: Int): Array<PointF> = normalizedCorners.map {
        PointF(it.x * width, it.y * height)
    }.toTypedArray()
}

/** OpenCV document-style detector operating directly on CameraX's Y plane. */
class OpenCvCardDetector {
    fun detect(image: ImageProxy): OpenCvDetectedQuad? {
        val source = yPlane(image)
        val upright = rotate(source, image.imageInfo.rotationDegrees)
        if (upright !== source) source.release()
        return try {
            detectGray(upright)
        } finally {
            upright.release()
        }
    }

    fun detect(bitmap: Bitmap): OpenCvDetectedQuad? {
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            detectGray(gray)
        } finally {
            rgba.release()
            gray.release()
        }
    }

    private fun detectGray(original: Mat): OpenCvDetectedQuad? {
        val sourceWidth = original.cols()
        val sourceHeight = original.rows()
        val scale = min(1.0, MAX_SIDE / max(sourceWidth, sourceHeight).toDouble())
        val working = Mat()
        if (scale < 1.0) {
            Imgproc.resize(original, working, Size(), scale, scale, Imgproc.INTER_AREA)
        } else {
            original.copyTo(working)
        }
        val blurred = Mat()
        val enhanced = Mat()
        val canny = Mat()
        val adaptive = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.GaussianBlur(working, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.createCLAHE(2.2, Size(8.0, 8.0)).apply(blurred, enhanced)
            Imgproc.Canny(enhanced, canny, 45.0, 135.0)
            Imgproc.morphologyEx(canny, canny, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.adaptiveThreshold(
                enhanced,
                adaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                7.0
            )
            Imgproc.morphologyEx(adaptive, adaptive, Imgproc.MORPH_CLOSE, kernel)
            val best = sequenceOf(canny, adaptive)
                .mapNotNull { bestCandidate(it, working.cols(), working.rows()) }
                .maxByOrNull(Candidate::score)
                ?: return null
            OpenCvDetectedQuad(
                normalizedCorners = best.corners.map {
                    PointF(
                        (it.x / working.cols()).toFloat().coerceIn(0f, 1f),
                        (it.y / working.rows()).toFloat().coerceIn(0f, 1f)
                    )
                }.toTypedArray(),
                confidence = best.score,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        } finally {
            working.release()
            blurred.release()
            enhanced.release()
            canny.release()
            adaptive.release()
            kernel.release()
        }
    }

    private fun bestCandidate(binary: Mat, width: Int, height: Int): Candidate? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val contourInput = binary.clone()
        return try {
            Imgproc.findContours(contourInput, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val frameArea = width.toDouble() * height
            contours.asSequence()
                .filter { Imgproc.contourArea(it) / frameArea in MIN_AREA_FRACTION..MAX_AREA_FRACTION }
                .mapNotNull { contour -> candidate(contour, width, height, frameArea) }
                .maxByOrNull(Candidate::score)
        } finally {
            contourInput.release()
            hierarchy.release()
            contours.forEach(MatOfPoint::release)
        }
    }

    private fun candidate(
        contour: MatOfPoint,
        width: Int,
        height: Int,
        frameArea: Double
    ): Candidate? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        return try {
            val perimeter = Imgproc.arcLength(curve, true)
            Imgproc.approxPolyDP(curve, approximation, perimeter * .022, true)
            val points = if (approximation.total() == 4L) {
                val polygon = MatOfPoint(*approximation.toArray())
                val convex = Imgproc.isContourConvex(polygon)
                polygon.release()
                if (convex) approximation.toArray() else null
            } else null
            val corners = order(points ?: run {
                val rotated = Imgproc.minAreaRect(curve)
                val fallback = Array(4) { Point() }
                rotated.points(fallback)
                fallback
            })
            score(corners, width, height, frameArea)
        } finally {
            curve.release()
            approximation.release()
        }
    }

    private fun score(corners: Array<Point>, width: Int, height: Int, frameArea: Double): Candidate? {
        val top = distance(corners[0], corners[1])
        val right = distance(corners[1], corners[2])
        val bottom = distance(corners[2], corners[3])
        val left = distance(corners[3], corners[0])
        val cardWidth = (top + bottom) / 2.0
        val cardHeight = (left + right) / 2.0
        if (cardHeight <= cardWidth || cardHeight <= 1.0) return null
        val ratio = cardWidth / cardHeight
        if (ratio !in .54..0.85) return null
        val area = polygonArea(corners)
        val areaFraction = area / frameArea
        if (areaFraction !in MIN_AREA_FRACTION..MAX_AREA_FRACTION) return null
        val centerX = corners.map { it.x }.average() / width
        val centerY = corners.map { it.y }.average() / height
        val centerDistance = hypot(centerX - .5, centerY - .5)
        if (centerDistance > .28) return null
        val rectangularity = 1.0 - corners.indices.maxOf { index ->
            val previous = corners[(index + 3) % 4]
            val current = corners[index]
            val next = corners[(index + 1) % 4]
            val firstX = previous.x - current.x
            val firstY = previous.y - current.y
            val secondX = next.x - current.x
            val secondY = next.y - current.y
            abs(firstX * secondX + firstY * secondY) /
                (hypot(firstX, firstY) * hypot(secondX, secondY)).coerceAtLeast(1.0)
        }
        if (rectangularity < .62) return null
        val aspectFit = (1.0 - abs(ratio - CARD_ASPECT) / .20).coerceIn(0.0, 1.0)
        val areaFit = (areaFraction / .42).coerceIn(0.0, 1.0)
        val centerFit = (1.0 - centerDistance / .28).coerceIn(0.0, 1.0)
        val score = aspectFit * .42 + rectangularity * .28 + areaFit * .20 + centerFit * .10
        return Candidate(corners, score.coerceIn(0.0, 1.0))
    }

    private fun order(points: Array<Point>): Array<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.maxBy { it.x - it.y }
        val bottomLeft = points.minBy { it.x - it.y }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun yPlane(image: ImageProxy): Mat {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val bytes = ByteArray(width * height)
        if (plane.pixelStride == 1 && plane.rowStride == width) {
            buffer.rewind()
            buffer.get(bytes, 0, min(bytes.size, buffer.remaining()))
        } else {
            for (row in 0 until height) {
                val rowStart = row * plane.rowStride
                for (column in 0 until width) {
                    bytes[row * width + column] = buffer.get(rowStart + column * plane.pixelStride)
                }
            }
        }
        return Mat(height, width, CvType.CV_8UC1).apply { put(0, 0, bytes) }
    }

    private fun rotate(source: Mat, degrees: Int): Mat {
        val rotation = when ((degrees % 360 + 360) % 360) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return source
        }
        return Mat().also { Core.rotate(source, it, rotation) }
    }

    private fun distance(first: Point, second: Point): Double =
        hypot(first.x - second.x, first.y - second.y)

    private fun polygonArea(points: Array<Point>): Double {
        var sum = 0.0
        points.indices.forEach { index ->
            val next = points[(index + 1) % points.size]
            sum += points[index].x * next.y - next.x * points[index].y
        }
        return abs(sum) / 2.0
    }

    private data class Candidate(val corners: Array<Point>, val score: Double)

    private companion object {
        const val MAX_SIDE = 720.0
        const val MIN_AREA_FRACTION = .16
        const val MAX_AREA_FRACTION = .92
        const val CARD_ASPECT = 63.0 / 88.0
    }
}
