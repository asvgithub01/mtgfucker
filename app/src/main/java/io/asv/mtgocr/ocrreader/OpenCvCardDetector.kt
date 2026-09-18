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
import kotlin.math.atan2
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
class OpenCvCardDetector(
    private val guideAssisted: Boolean = false
) {
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
        val rgb = Mat()
        val hsv = Mat()
        val saturation = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val grayResult = detectGray(gray)
            if (grayResult != null && grayResult.confidence >= STRONG_DETECTION_CONFIDENCE) {
                grayResult
            } else {
                // The final JPEG contains colour information that the live Y plane lacks.
                // Saturation often separates a card from a mat with similar luminance.
                Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
                Core.extractChannel(hsv, saturation, 1)
                listOfNotNull(grayResult, detectGray(saturation)).maxByOrNull { it.confidence }
            }
        } finally {
            rgba.release()
            gray.release()
            rgb.release()
            hsv.release()
            saturation.release()
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
        val dogSmall = Mat()
        val dogLarge = Mat()
        val dog = Mat()
        val dogEdges = Mat()
        val adaptive = Mat()
        val adaptiveEdges = Mat()
        val kernel3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val kernel5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.GaussianBlur(working, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.createCLAHE(2.2, Size(8.0, 8.0)).apply(blurred, enhanced)

            val mean = Core.mean(blurred).`val`[0]
            val low = (mean * .42).coerceIn(22.0, 68.0)
            val high = (mean * 1.08).coerceIn(62.0, 155.0)
            Imgproc.Canny(enhanced, canny, low, high)
            Imgproc.morphologyEx(canny, canny, Imgproc.MORPH_CLOSE, kernel3)

            // Difference of Gaussians suppresses both fine card detail and broad shadows,
            // leaving the physical boundary at the scale we care about.
            Imgproc.GaussianBlur(working, dogSmall, Size(3.0, 3.0), 0.0)
            Imgproc.GaussianBlur(working, dogLarge, Size(21.0, 21.0), 0.0)
            Core.absdiff(dogSmall, dogLarge, dog)
            Imgproc.Canny(dog, dogEdges, 10.0, 30.0)
            Imgproc.morphologyEx(dogEdges, dogEdges, Imgproc.MORPH_CLOSE, kernel5)

            Imgproc.adaptiveThreshold(
                enhanced,
                adaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                51,
                5.0
            )
            Imgproc.morphologyEx(adaptive, adaptiveEdges, Imgproc.MORPH_GRADIENT, kernel3)
            Imgproc.morphologyEx(adaptiveEdges, adaptiveEdges, Imgproc.MORPH_CLOSE, kernel3)

            val edgeMaps = listOf(canny, dogEdges, adaptiveEdges).map(::edgeEvidence)
            val contourBest = edgeMaps.asSequence()
                .mapNotNull { bestCandidate(it, working.cols(), working.rows()) }
                .maxByOrNull(Candidate::score)
            val guidedBest = if (guideAssisted) guideCandidate(enhanced, edgeMaps) else null
            val best = listOfNotNull(contourBest, guidedBest).maxByOrNull(Candidate::score)
                ?: return null
            val refined = if (guideAssisted) {
                refineCandidate(enhanced, best, edgeMaps) ?: best
            } else best
            OpenCvDetectedQuad(
                normalizedCorners = refined.corners.map {
                    PointF(
                        (it.x / working.cols()).toFloat().coerceIn(0f, 1f),
                        (it.y / working.rows()).toFloat().coerceIn(0f, 1f)
                    )
                }.toTypedArray(),
                confidence = refined.score,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        } finally {
            working.release()
            blurred.release()
            enhanced.release()
            canny.release()
            dogSmall.release()
            dogLarge.release()
            dog.release()
            dogEdges.release()
            adaptive.release()
            adaptiveEdges.release()
            kernel3.release()
            kernel5.release()
        }
    }

    private fun bestCandidate(binary: EdgeEvidence, width: Int, height: Int): Candidate? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val contourInput = binary.mat.clone()
        return try {
            Imgproc.findContours(
                contourInput,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            val frameArea = width.toDouble() * height
            contours.asSequence()
                .filter { Imgproc.contourArea(it) / frameArea in MIN_AREA_FRACTION..MAX_AREA_FRACTION }
                .mapNotNull { contour -> candidate(contour, binary, width, height, frameArea) }
                .maxByOrNull(Candidate::score)
        } finally {
            contourInput.release()
            hierarchy.release()
            contours.forEach(MatOfPoint::release)
        }
    }

    /**
     * Completes broken contours by looking for four long, coherent edges around the on-screen
     * guide. In particular, the top and bottom of old dark cards often disappear from Canny when
     * the playmat has a similar tone; sampling the complete line is much more stable there.
     */
    private fun guideCandidate(gray: Mat, edgeMaps: List<EdgeEvidence>): Candidate? {
        val width = gray.cols()
        val height = gray.rows()
        if (width < 80 || height < 120) return null
        val pixels = ByteArray(width * height)
        gray.get(0, 0, pixels)

        var cardHeight = height * .72
        var cardWidth = cardHeight * CARD_ASPECT
        if (cardWidth > width * .90) {
            cardWidth = width * .90
            cardHeight = cardWidth / CARD_ASPECT
        }
        val leftExpected = (width - cardWidth) / 2.0
        val rightExpected = (width + cardWidth) / 2.0
        val topExpected = (height - cardHeight) / 2.0
        val bottomExpected = (height + cardHeight) / 2.0
        val centerX = width / 2.0
        val centerY = height / 2.0
        val offset = max(2, min(width, height) / 260)

        val left = searchGuideLine(
            pixels, width, height, leftExpected, centerY, topExpected, bottomExpected,
            max(10, (cardWidth * GUIDE_VERTICAL_SEARCH_FRACTION).toInt()), offset, vertical = true
        )
        val right = searchGuideLine(
            pixels, width, height, rightExpected, centerY, topExpected, bottomExpected,
            max(10, (cardWidth * GUIDE_VERTICAL_SEARCH_FRACTION).toInt()), offset, vertical = true
        )
        val top = searchGuideLine(
            pixels, width, height, topExpected, centerX, leftExpected, rightExpected,
            max(12, (cardHeight * GUIDE_HORIZONTAL_SEARCH_FRACTION).toInt()), offset, vertical = false
        )
        val bottom = searchGuideLine(
            pixels, width, height, bottomExpected, centerX, leftExpected, rightExpected,
            max(12, (cardHeight * GUIDE_HORIZONTAL_SEARCH_FRACTION).toInt()), offset, vertical = false
        )
        val edgeConfidence = (left.confidence + right.confidence) * .20 +
            (top.confidence + bottom.confidence) * .30
        if (edgeConfidence < GUIDE_MIN_CONFIDENCE ||
            minOf(left.confidence, right.confidence) < GUIDE_MIN_VERTICAL_CONFIDENCE ||
            minOf(top.confidence, bottom.confidence) < GUIDE_MIN_HORIZONTAL_CONFIDENCE
        ) return null

        val corners = arrayOf(
            guideIntersection(left, top) ?: return null,
            guideIntersection(right, top) ?: return null,
            guideIntersection(right, bottom) ?: return null,
            guideIntersection(left, bottom) ?: return null
        )
        if (corners.any { it.x !in 0.0..width.toDouble() || it.y !in 0.0..height.toDouble() }) {
            return null
        }
        val geometry = score(corners, width, height, width.toDouble() * height) ?: return null
        val lineCandidate = Candidate(
            corners,
            (geometry.score * .32 + edgeConfidence * .68).coerceIn(0.0, 1.0)
        )
        return withEdgeEvidence(lineCandidate, edgeMaps)
    }

    private fun searchGuideLine(
        pixels: ByteArray,
        width: Int,
        height: Int,
        expectedPosition: Double,
        axisCenter: Double,
        rangeStart: Double,
        rangeEnd: Double,
        radius: Int,
        offset: Int,
        vertical: Boolean
    ): GuideLine {
        var best = GuideLine(expectedPosition, 0.0, axisCenter, 0.0, vertical)
        val positionStep = max(1, (if (vertical) width else height) / 420)
        var position = (expectedPosition - radius).toInt()
        val last = (expectedPosition + radius).toInt()
        while (position <= last) {
            var slope = -GUIDE_MAX_SLOPE
            while (slope <= GUIDE_MAX_SLOPE + .001) {
                val strength = guideLineStrength(
                    pixels, width, height, position.toDouble(), slope, axisCenter,
                    rangeStart, rangeEnd, offset, vertical
                )
                val proximity = 1.0 - GUIDE_POSITION_PENALTY *
                    abs(position - expectedPosition) / radius.coerceAtLeast(1)
                val confidence = (strength * proximity / GUIDE_EDGE_NORMALIZER)
                    .coerceIn(0.0, 1.0)
                if (confidence > best.confidence) {
                    best = GuideLine(position.toDouble(), slope, axisCenter, confidence, vertical)
                }
                slope += GUIDE_SLOPE_STEP
            }
            position += positionStep
        }
        return best
    }

    private fun guideLineStrength(
        pixels: ByteArray,
        width: Int,
        height: Int,
        position: Double,
        slope: Double,
        axisCenter: Double,
        rangeStart: Double,
        rangeEnd: Double,
        offset: Int,
        vertical: Boolean
    ): Double {
        val inset = (rangeEnd - rangeStart) * .06
        val start = (rangeStart + inset).toInt()
        val end = (rangeEnd - inset).toInt()
        val step = max(2, (end - start) / GUIDE_LINE_SAMPLES)
        var total = 0.0
        var count = 0
        var axis = start
        while (axis <= end) {
            val perpendicular = (position + slope * (axis - axisCenter)).toInt()
            var strongest = 0
            for (shift in -1..1) {
                val firstX = if (vertical) perpendicular - offset + shift else axis
                val firstY = if (vertical) axis else perpendicular - offset + shift
                val secondX = if (vertical) perpendicular + offset + shift else axis
                val secondY = if (vertical) axis else perpendicular + offset + shift
                if (firstX in 0 until width && secondX in 0 until width &&
                    firstY in 0 until height && secondY in 0 until height
                ) {
                    val first = pixels[firstY * width + firstX].toInt() and 0xff
                    val second = pixels[secondY * width + secondX].toInt() and 0xff
                    strongest = max(strongest, abs(first - second))
                }
            }
            total += strongest
            count++
            axis += step
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun guideIntersection(vertical: GuideLine, horizontal: GuideLine): Point? {
        val verticalIntercept = vertical.position - vertical.slope * vertical.axisCenter
        val horizontalIntercept = horizontal.position - horizontal.slope * horizontal.axisCenter
        val denominator = 1.0 - vertical.slope * horizontal.slope
        if (abs(denominator) < .04) return null
        val x = (vertical.slope * horizontalIntercept + verticalIntercept) / denominator
        return Point(x, horizontal.slope * x + horizontalIntercept)
    }

    /** Refines every coarse side independently against the local grayscale gradient. */
    private fun refineCandidate(
        gray: Mat,
        candidate: Candidate,
        edgeMaps: List<EdgeEvidence>
    ): Candidate? {
        val width = gray.cols()
        val height = gray.rows()
        val gradientX = Mat()
        val gradientY = Mat()
        val magnitude = Mat()
        return try {
            Imgproc.Sobel(gray, gradientX, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(gray, gradientY, CvType.CV_32F, 0, 1, 3)
            Core.magnitude(gradientX, gradientY, magnitude)
            val values = FloatArray(width * height)
            magnitude.get(0, 0, values)
            val sides = candidate.corners.indices.map { index ->
                refineSide(
                    candidate.corners[index],
                    candidate.corners[(index + 1) % candidate.corners.size],
                    values,
                    width,
                    height
                ) ?: return null
            }
            val corners = arrayOf(
                lineIntersection(sides[3], sides[0]) ?: return null,
                lineIntersection(sides[0], sides[1]) ?: return null,
                lineIntersection(sides[1], sides[2]) ?: return null,
                lineIntersection(sides[2], sides[3]) ?: return null
            )
            val diagonal = hypot(width.toDouble(), height.toDouble()).coerceAtLeast(1.0)
            val maximumShift = corners.indices.maxOf { index ->
                distance(corners[index], candidate.corners[index]) / diagonal
            }
            if (maximumShift > MAX_REFINEMENT_SHIFT ||
                corners.any { it.x !in 0.0..width.toDouble() || it.y !in 0.0..height.toDouble() }
            ) return null
            val geometry = score(corners, width, height, width.toDouble() * height) ?: return null
            withEdgeEvidence(
                Candidate(corners, max(candidate.score, geometry.score)),
                edgeMaps
            )
        } finally {
            gradientX.release()
            gradientY.release()
            magnitude.release()
        }
    }

    private fun refineSide(
        start: Point,
        end: Point,
        magnitude: FloatArray,
        width: Int,
        height: Int
    ): FittedLine? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        if (length < 20.0) return null
        val normalX = -dy / length
        val normalY = dx / length
        val radius = max(4, (min(width, height) * REFINEMENT_RADIUS_FRACTION).toInt())
        val points = ArrayList<EdgePoint>(REFINEMENT_SAMPLES)
        for (sample in 0 until REFINEMENT_SAMPLES) {
            val amount = REFINEMENT_START +
                (REFINEMENT_END - REFINEMENT_START) * sample / (REFINEMENT_SAMPLES - 1.0)
            val baseX = start.x + dx * amount
            val baseY = start.y + dy * amount
            var bestScore = Double.NEGATIVE_INFINITY
            var bestMagnitude = 0f
            var bestX = baseX
            var bestY = baseY
            for (offset in -radius..radius) {
                val x = (baseX + normalX * offset).toInt()
                val y = (baseY + normalY * offset).toInt()
                if (x !in 1 until width - 1 || y !in 1 until height - 1) continue
                val strength = magnitude[y * width + x]
                val score = strength.toDouble() -
                    abs(offset) * REFINEMENT_DISTANCE_PENALTY
                if (score > bestScore) {
                    bestScore = score
                    bestMagnitude = strength
                    bestX = x.toDouble()
                    bestY = y.toDouble()
                }
            }
            if (bestMagnitude >= REFINEMENT_MIN_GRADIENT) {
                points += EdgePoint(bestX, bestY, bestMagnitude.toDouble())
            }
        }
        if (points.size < REFINEMENT_MIN_POINTS) return null
        val first = fitWeightedLine(points, horizontal = abs(dx) >= abs(dy)) ?: return null
        val filtered = points.filter { first.distance(it.x, it.y) <= REFINEMENT_MAX_RESIDUAL }
        if (filtered.size < REFINEMENT_MIN_POINTS) return first
        return fitWeightedLine(filtered, horizontal = abs(dx) >= abs(dy)) ?: first
    }

    private fun fitWeightedLine(points: List<EdgePoint>, horizontal: Boolean): FittedLine? {
        var sumWeight = 0.0
        var sumIndependent = 0.0
        var sumDependent = 0.0
        var sumIndependentSquared = 0.0
        var sumProduct = 0.0
        for (point in points) {
            val weight = point.weight.coerceIn(1.0, REFINEMENT_MAX_WEIGHT)
            val independent = if (horizontal) point.x else point.y
            val dependent = if (horizontal) point.y else point.x
            sumWeight += weight
            sumIndependent += weight * independent
            sumDependent += weight * dependent
            sumIndependentSquared += weight * independent * independent
            sumProduct += weight * independent * dependent
        }
        val denominator = sumWeight * sumIndependentSquared - sumIndependent * sumIndependent
        if (abs(denominator) < .0001) return null
        val slope = (sumWeight * sumProduct - sumIndependent * sumDependent) / denominator
        val intercept = (sumDependent - slope * sumIndependent) / sumWeight
        return if (horizontal) {
            FittedLine(slope, -1.0, intercept).normalized()
        } else {
            FittedLine(1.0, -slope, -intercept).normalized()
        }
    }

    private fun lineIntersection(first: FittedLine, second: FittedLine): Point? {
        val determinant = first.a * second.b - second.a * first.b
        if (abs(determinant) < .0001) return null
        return Point(
            (first.b * second.c - second.b * first.c) / determinant,
            (first.c * second.a - second.c * first.a) / determinant
        )
    }

    private fun candidate(
        contour: MatOfPoint,
        edgeMap: EdgeEvidence,
        width: Int,
        height: Int,
        frameArea: Double
    ): Candidate? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        return try {
            val perimeter = Imgproc.arcLength(curve, true)
            val points = APPROXIMATION_RATIOS.asSequence().mapNotNull { ratio ->
                Imgproc.approxPolyDP(curve, approximation, perimeter * ratio, true)
                if (approximation.total() != 4L) return@mapNotNull null
                val polygon = MatOfPoint(*approximation.toArray())
                val convex = Imgproc.isContourConvex(polygon)
                polygon.release()
                if (convex) approximation.toArray() else null
            }.firstOrNull() ?: return null
            val corners = order(points)
            val geometry = score(corners, width, height, frameArea) ?: return null
            withEdgeEvidence(geometry, listOf(edgeMap))
        } finally {
            curve.release()
            approximation.release()
        }
    }

    /** A geometrically plausible card is accepted only when all four sides have evidence. */
    private fun withEdgeEvidence(candidate: Candidate, edgeMaps: List<EdgeEvidence>): Candidate? {
        if (edgeMaps.isEmpty()) return null
        val supports = DoubleArray(4)
        edgeMaps.forEach { edgeMap ->
            val current = edgeSupports(edgeMap, candidate.corners)
            current.indices.forEach { index ->
                supports[index] = max(supports[index], current[index])
            }
        }
        val average = supports.average()
        val weakest = supports.minOrNull() ?: 0.0
        if (average < MIN_AVERAGE_EDGE_SUPPORT || weakest < MIN_SIDE_EDGE_SUPPORT ||
            supports[0] < MIN_HORIZONTAL_EDGE_SUPPORT ||
            supports[2] < MIN_HORIZONTAL_EDGE_SUPPORT
        ) return null
        return Candidate(
            candidate.corners,
            (candidate.score * .48 + average * .37 + weakest * .15).coerceIn(0.0, 1.0)
        )
    }

    private fun edgeSupports(edgeMap: EdgeEvidence, corners: Array<Point>): DoubleArray {
        val width = edgeMap.width
        val height = edgeMap.height
        val radius = max(3, (min(width, height) * EDGE_SEARCH_RADIUS_FRACTION).toInt())
        return DoubleArray(4) { side ->
            val start = corners[side]
            val end = corners[(side + 1) % 4]
            var supported = 0
            var total = 0
            for (sample in 0..EDGE_SUPPORT_SAMPLES) {
                val amount = sample / EDGE_SUPPORT_SAMPLES.toDouble()
                val centerX = (start.x + (end.x - start.x) * amount).toInt()
                val centerY = (start.y + (end.y - start.y) * amount).toInt()
                if (centerX !in 0 until width || centerY !in 0 until height) continue
                total++
                var found = false
                val minY = max(0, centerY - radius)
                val maxY = min(height - 1, centerY + radius)
                val minX = max(0, centerX - radius)
                val maxX = min(width - 1, centerX + radius)
                var y = minY
                while (y <= maxY && !found) {
                    var x = minX
                    while (x <= maxX) {
                        if ((edgeMap.pixels[y * width + x].toInt() and 0xff) > 0) {
                            found = true
                            break
                        }
                        x++
                    }
                    y++
                }
                if (found) supported++
            }
            if (total == 0) 0.0 else supported / total.toDouble()
        }
    }

    private fun edgeEvidence(mat: Mat): EdgeEvidence {
        val pixels = ByteArray(mat.rows() * mat.cols())
        mat.get(0, 0, pixels)
        return EdgeEvidence(mat, pixels, mat.cols(), mat.rows())
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
        val centerX = points.map(Point::x).average()
        val centerY = points.map(Point::y).average()
        val clockwise = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val topLeftIndex = clockwise.indices.minBy { clockwise[it].x + clockwise[it].y }
        return Array(4) { offset -> clockwise[(topLeftIndex + offset) % 4] }
    }

    private fun yPlane(image: ImageProxy): Mat {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val base = buffer.position()
        val bytes = ByteArray(width * height)
        if (plane.pixelStride == 1 && plane.rowStride == image.width &&
            crop.left == 0 && crop.top == 0 && width == image.width && height == image.height
        ) {
            buffer.get(bytes, 0, min(bytes.size, buffer.remaining()))
        } else if (plane.pixelStride == 1) {
            for (row in 0 until height) {
                val rowStart = base + (crop.top + row) * plane.rowStride + crop.left
                buffer.position(rowStart)
                buffer.get(bytes, row * width, width)
            }
        } else {
            for (row in 0 until height) {
                val rowStart = base + (crop.top + row) * plane.rowStride
                for (column in 0 until width) {
                    bytes[row * width + column] = buffer.get(
                        rowStart + (crop.left + column) * plane.pixelStride
                    )
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
    private data class EdgeEvidence(
        val mat: Mat,
        val pixels: ByteArray,
        val width: Int,
        val height: Int
    )
    private data class GuideLine(
        val position: Double,
        val slope: Double,
        val axisCenter: Double,
        val confidence: Double,
        val vertical: Boolean
    )
    private data class EdgePoint(val x: Double, val y: Double, val weight: Double)
    private data class FittedLine(val a: Double, val b: Double, val c: Double) {
        fun normalized(): FittedLine {
            val norm = hypot(a, b).coerceAtLeast(.0001)
            return FittedLine(a / norm, b / norm, c / norm)
        }

        fun distance(x: Double, y: Double): Double = abs(a * x + b * y + c)
    }

    private companion object {
        const val MAX_SIDE = 720.0
        const val MIN_AREA_FRACTION = .16
        const val MAX_AREA_FRACTION = .92
        const val CARD_ASPECT = 63.0 / 88.0
        const val GUIDE_MAX_SLOPE = .30
        const val GUIDE_SLOPE_STEP = .04
        const val GUIDE_POSITION_PENALTY = .62
        const val GUIDE_EDGE_NORMALIZER = 58.0
        const val GUIDE_LINE_SAMPLES = 52
        const val GUIDE_MIN_CONFIDENCE = .31
        const val GUIDE_MIN_VERTICAL_CONFIDENCE = .18
        const val GUIDE_MIN_HORIZONTAL_CONFIDENCE = .21
        const val GUIDE_VERTICAL_SEARCH_FRACTION = .14
        const val GUIDE_HORIZONTAL_SEARCH_FRACTION = .10
        const val MIN_AVERAGE_EDGE_SUPPORT = .42
        const val MIN_SIDE_EDGE_SUPPORT = .16
        const val MIN_HORIZONTAL_EDGE_SUPPORT = .23
        const val EDGE_SEARCH_RADIUS_FRACTION = .006
        const val EDGE_SUPPORT_SAMPLES = 36
        const val REFINEMENT_RADIUS_FRACTION = .018
        const val REFINEMENT_SAMPLES = 56
        const val REFINEMENT_START = .11
        const val REFINEMENT_END = .89
        const val REFINEMENT_DISTANCE_PENALTY = 2.4
        const val REFINEMENT_MIN_GRADIENT = 18f
        const val REFINEMENT_MIN_POINTS = 20
        const val REFINEMENT_MAX_RESIDUAL = 3.5
        const val REFINEMENT_MAX_WEIGHT = 180.0
        const val MAX_REFINEMENT_SHIFT = .035
        const val STRONG_DETECTION_CONFIDENCE = .82
        val APPROXIMATION_RATIOS = doubleArrayOf(.016, .022, .030, .040)
    }
}
