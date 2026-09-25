package io.asv.mtgocr.ocrreader

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/** Offline synthetic image regression: no catalog, camera, collection writes or network. */
@RunWith(AndroidJUnit4::class)
class SetSymbolHashMatcherDeviceTest {
    @Test fun symbolsNearRightEdgeStayInsideSearchBandAtBothResolutions() {
        assertTrue(OpenCVLoader.initLocal())
        val refs = listOf("circle", "triangle", "cross").associateWith { glyph(it) }
        val matcher = SetSymbolHashMatcher { refs[it] }
        try {
            // Farther right than the supplied FUT Myr; do not widen the band or relax scores.
            for (left in listOf(685, 698)) for (scale in listOf(1, 2)) {
                val low = card(refs.getValue("cross"), 40, left)
                val photo = if (scale == 1) low else Bitmap.createScaledBitmap(low, 1488, 2080, false)
                try {
                    val result = matcher.match(photo, listOf("CIRCLE", "TRIANGLE", "CROSS"))
                    assertEquals("left=$left scale=$scale ${result.json()}", "CROSS", result.selectedSet)
                    assertTrue(result.scores.first().crop[0] > (photo.width * .9).toInt())
                } finally { if (photo !== low) photo.recycle(); low.recycle() }
            }
        } finally { matcher.close(); refs.values.forEach { it.recycle() } }
    }

    @Test fun highResolutionGlyphKeepsScaledGeometryAndReportsWorkingSize() {
        assertTrue(OpenCVLoader.initLocal())
        val refs = listOf("circle", "triangle", "cross").associateWith { glyph(it) }
        val matcher = SetSymbolHashMatcher { refs[it] }
        val low = card(refs.getValue("cross"), 40)
        val high = Bitmap.createScaledBitmap(low, 1488, 2080, false)
        try {
            val match = matcher.match(high, listOf("CIRCLE", "TRIANGLE", "CROSS"))
            android.util.Log.i("SymbolResolutionCost", "synthetic1488x2080=${match.elapsedMs}ms")
            assertEquals(match.json().toString(), "CROSS", match.selectedSet)
            assertEquals(1488, match.inputWidth)
            assertEquals(1488, match.json().getInt("segmentationWidth"))
            // The preview rectangle must still be in input pixels, not the old 744px coordinates.
            assertTrue(match.scores.first().crop[0] > 1200)
        } finally { matcher.close(); low.recycle(); high.recycle(); refs.values.forEach { it.recycle() } }
    }

    @Test fun tinyHolesDoNotDominateUniqueShapesButStillSeparateSimilarSilhouettes() {
        assertTrue(OpenCVLoader.initLocal())
        val disk = glyph("circle")
        val smallHole = disk.copy(Bitmap.Config.ARGB_8888, true).apply {
            Canvas(this).drawCircle(32f, 32f, 5f, Paint().apply { color = Color.BLACK })
        }
        val references = mapOf("hole" to smallHole, "disk" to disk)
        val matcher = SetSymbolHashMatcher { references[it] }
        val photo = card(smallHole, 48)
        try {
            val single = matcher.match(photo, listOf("HOLE"))
            assertEquals(single.json().toString(), "HOLE", single.selectedSet)
            assertNull(single.scores.first().detailCorrelation)
            val family = matcher.match(photo, listOf("HOLE", "DISK"))
            assertEquals(family.json().toString(), "HOLE", family.selectedSet)
            assertNotNull(family.scores.first().detailCorrelation)
        } finally { matcher.close(); photo.recycle(); disk.recycle(); smallHole.recycle() }
    }

    @Test fun unprintedCoreSetsNeverLoadCatalogueLogosOrConfirmAbsence() {
        assertTrue(OpenCVLoader.initLocal())
        val loaded = java.util.Collections.synchronizedList(mutableListOf<String>())
        val ref = glyph("cross")
        val matcher = SetSymbolHashMatcher { code -> loaded += code; ref }
        val blank = Bitmap.createBitmap(744, 1040, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val printed = card(ref, 40)
        try {
            val ancient = matcher.match(blank, listOf("LEA", "LEB", "2ED", "3ED", "4ED"))
            assertEquals("simbolo_no_aplicable", ancient.reason)
            assertNull(ancient.selectedSet)
            assertTrue(ancient.scores.isEmpty())
            assertEquals(5, ancient.noSymbolSets.size)
            assertTrue(loaded.isEmpty())
            val mixedBlank = matcher.match(blank, listOf("4ED", "ORI"))
            assertEquals("ausencia_posible_no_confirmada", mixedBlank.reason)
            assertNull(mixedBlank.selectedSet)
            val mixedPrinted = matcher.match(printed, listOf("4ED", "ORI"))
            assertEquals("ORI", mixedPrinted.selectedSet)
            assertEquals(listOf("ORI"), mixedPrinted.comparedSets)
            assertEquals(listOf("4ED"), mixedPrinted.noSymbolSets)
            assertEquals(setOf("ori"), loaded.toSet())
        } finally { matcher.close(); ref.recycle(); blank.recycle(); printed.recycle() }
    }

    @Test fun listReprintsUseRetainedGlyphAndKeepOriginalSetAmbiguous() {
        assertTrue(OpenCVLoader.initLocal())
        val refs = mapOf("zen" to glyph("cross"), "mm2" to glyph("circle"))
        val matcher = SetSymbolHashMatcher { code -> checkNotNull(refs[code]) }
        try {
            for ((template, expected) in listOf("zen" to null, "mm2" to "MM2")) {
                val card = card(refs.getValue(template), 40)
                try {
                    val result = matcher.match(card, listOf("ZEN", "PLST", "MM2"), mapOf("PLST" to listOf("ZEN")))
                    assertEquals(result.scores.toString(), expected, result.selectedSet)
                    assertEquals("ZEN", result.scores.single { it.setCode == "PLST" }.referenceSetCode)
                    assertTrue(result.missingSets.isEmpty())
                } finally { card.recycle() }
            }
        } finally { matcher.close(); refs.values.forEach { it.recycle() } }
    }

    @Test fun normalizedGlyphSurvivesScaleAndPositionChanges() {
        assertTrue(OpenCVLoader.initLocal())
        val references = listOf("circle", "triangle", "cross").associateWith { glyph(it) }
        val matcher = SetSymbolHashMatcher { references[it] }
        try {
            for (size in listOf(26, 40, 52)) {
                val card = card(references.getValue("cross"), size)
                try {
                    val match = matcher.match(card, listOf("CIRCLE", "TRIANGLE", "CROSS"))
                    assertEquals("$size: ${match.scores}", "CROSS", match.selectedSet)
                    assertNotNull(match.cropPng)
                    assertFalse(card.isRecycled)
                } finally { card.recycle() }
            }
        } finally { matcher.close(); references.values.forEach { it.recycle() } }
    }

    @Test fun blankCardAndIdenticalReferencesNeverAuthorizeASet() {
        assertTrue(OpenCVLoader.initLocal())
        val reference = glyph("cross")
        val matcher = SetSymbolHashMatcher { reference }
        val blank = Bitmap.createBitmap(744, 1040, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val printed = card(reference, 40)
        try {
            assertNull(matcher.match(blank, listOf("A", "B")).selectedSet)
            assertEquals("sin_recorte_de_simbolo", matcher.match(blank, listOf("A", "B")).reason)
            assertNull(matcher.match(printed, listOf("A", "B")).selectedSet)
        } finally { matcher.close(); reference.recycle(); blank.recycle(); printed.recycle() }
    }

    @Test fun disconnectedPartsAreComparedAsOneGlyph() {
        assertTrue(OpenCVLoader.initLocal())
        val references = listOf("circle", "split", "cross").associateWith { glyph(it) }
        val matcher = SetSymbolHashMatcher { references[it] }
        val printed = card(references.getValue("split"), 48)
        try {
            val match = matcher.match(printed, listOf("CIRCLE", "SPLIT", "CROSS"))
            assertEquals(match.scores.toString(), "SPLIT", match.selectedSet)
        } finally { matcher.close(); references.values.forEach { it.recycle() }; printed.recycle() }
    }

    @Test fun internalHolesDistinguishReferencesWithIdenticalOuterContours() {
        assertTrue(OpenCVLoader.initLocal())
        val disk = glyph("circle")
        val ring = glyph("circle").apply {
            Canvas(this).drawCircle(32f, 32f, 17f, Paint().apply { color = Color.BLACK })
        }
        val matcher = SetSymbolHashMatcher { if (it == "ring") ring else disk }
        try {
            for ((reference, expected) in listOf(ring to "RING", disk to "DISK")) {
                val printed = card(reference, 48)
                try {
                    val result = matcher.match(printed, listOf("RING", "DISK"))
                    assertEquals(result.json().toString(), expected, result.selectedSet)
                } finally { printed.recycle() }
            }
        } finally { matcher.close(); disk.recycle(); ring.recycle() }
    }

    @Test fun completeMultipartGlyphNearRightEdgeIsNotCutOff() {
        assertTrue(OpenCVLoader.initLocal())
        val refs = listOf("split", "circle", "cross").associateWith { glyph(it) }
        val matcher = SetSymbolHashMatcher { refs[it] }
        // Printed rightmost pixel is x=733: beyond the old 98% search band.
        val printed = card(refs.getValue("split"), 48, 697)
        try {
            val result = matcher.match(printed, listOf("SPLIT", "CIRCLE", "CROSS"))
            assertEquals(result.json().toString(), "SPLIT", result.selectedSet)
        } finally { matcher.close(); refs.values.forEach { it.recycle() }; printed.recycle() }
    }

    @Test fun missingReferenceAndChroniclesRemainUnresolved() {
        assertTrue(OpenCVLoader.initLocal())
        val reference = glyph("cross")
        val matcher = SetSymbolHashMatcher { if (it == "missing") null else reference }
        val printed = card(reference, 40)
        try {
            val missing = matcher.match(printed, listOf("A", "MISSING"))
            assertNull(missing.selectedSet)
            assertEquals(listOf("MISSING"), missing.missingSets)
            assertEquals("simbolo_reutilizado_chronicles", matcher.match(printed, listOf("A", "CHR")).reason)
        } finally { matcher.close(); reference.recycle(); printed.recycle() }
    }

    @Test fun wideNumberedSymbolsKeepDetailsInBothInkPolaritiesAndOnlyRequestCandidateSets() {
        assertTrue(OpenCVLoader.initLocal())
        fun reference(number: String) = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            val canvas = Canvas(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(RectF(3f, 3f, 93f, 45f), 6f, 6f, paint)
            paint.color = Color.BLACK; paint.textSize = 32f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(number, 25f, 36f, paint)
        }
        val refs = mapOf("m14" to reference("14"), "m15" to reference("15"))
        val requested = java.util.Collections.synchronizedSet(HashSet<String>())
        val matcher = SetSymbolHashMatcher { code -> requested.add(code); refs[code] }
        try {
            for ((code, number) in listOf("M14" to "14", "M15" to "15")) for (metallic in listOf(false, true)) {
                val photo = Bitmap.createBitmap(744, 1040, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                    val canvas = Canvas(this)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (metallic) Color.LTGRAY else Color.BLACK }
                    canvas.drawRoundRect(RectF(618f, 583f, 708f, 625f), 6f, 6f, paint)
                    paint.color = if (metallic) Color.BLACK else Color.WHITE
                    paint.textSize = 32f; paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText(number, 640f, 616f, paint)
                    if (metallic) {
                        paint.color = Color.BLACK; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
                        canvas.drawRoundRect(RectF(618f, 583f, 708f, 625f), 6f, 6f, paint)
                    }
                }
                try {
                    val result = matcher.match(photo, listOf("M14", "M15"))
                    assertEquals("$code metallic=$metallic ${result.json()}", code, result.selectedSet)
                    assertEquals(listOf("M14", "M15"), result.comparedSets)
                } finally { photo.recycle() }
            }
            assertEquals(setOf("m14", "m15"), requested)
        } finally { matcher.close(); refs.values.forEach { it.recycle() } }
    }

    private fun glyph(kind: String): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
        val paint = Paint().apply { color = Color.WHITE }
        val canvas = Canvas(this)
        when (kind) {
            "split" -> {
                canvas.drawRect(10f, 8f, 24f, 56f, paint)
                canvas.drawRect(36f, 8f, 50f, 56f, paint)
            }
            "circle" -> canvas.drawCircle(32f, 32f, 28f, paint)
            "triangle" -> canvas.drawPath(Path().apply {
                moveTo(32f, 4f); lineTo(60f, 60f); lineTo(4f, 60f); close()
            }, paint)
            else -> {
                canvas.drawRect(24f, 4f, 40f, 60f, paint)
                canvas.drawRect(4f, 24f, 60f, 40f, paint)
            }
        }
    }

    private fun card(reference: Bitmap, size: Int, left: Int = 635): Bitmap =
        Bitmap.createBitmap(744, 1040, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 214, 198))
            val paint = Paint().apply {
                // Invert white-on-black SVG mask into a dark printed glyph on light paper.
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f,
                    0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)))
            }
            Canvas(this).drawBitmap(reference, null, Rect(left, 580, left + size, 580 + size), paint)
        }
}
