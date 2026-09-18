package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFrameAnalyzerTest {
    @Test fun classifiesIndividualBorderZones() {
        assertEquals(CardBorderColor.BLACK, CardFrameAnalyzer.classifyRgb(24, 23, 21))
        assertEquals(CardBorderColor.WHITE, CardFrameAnalyzer.classifyRgb(220, 220, 220))
        assertEquals(CardBorderColor.UNKNOWN, CardFrameAnalyzer.classifyRgb(220, 219, 220))
        assertEquals(CardBorderColor.GOLD, CardFrameAnalyzer.classifyRgb(198, 154, 66))
        assertEquals(CardBorderColor.SILVER, CardFrameAnalyzer.classifyRgb(142, 147, 151))
        assertEquals(CardBorderColor.UNKNOWN, CardFrameAnalyzer.classifyRgb(55, 125, 61))
    }

    @Test fun reportsVariableBorderWhenSeparatedZonesDisagree() {
        val zones = buildList {
            repeat(8) { add(zone(it, CardBorderColor.BLACK)) }
            repeat(8) { add(zone(it + 8, CardBorderColor.WHITE)) }
        }
        val result = CardFrameAnalyzer.classifyBorderZones(zones)
        assertEquals(CardBorderColor.MIXED, result.first)
        assertTrue(result.second >= .35)
    }

    @Test fun ignoresAFewUnknownOrGlareAffectedZones() {
        val zones = buildList {
            repeat(12) { add(zone(it, CardBorderColor.WHITE)) }
            repeat(4) { add(zone(it + 12, CardBorderColor.UNKNOWN)) }
        }
        val result = CardFrameAnalyzer.classifyBorderZones(zones)
        assertEquals(CardBorderColor.WHITE, result.first)
        assertTrue(result.second >= .70)
    }

    @Test fun requiresWhiteCirclesAcrossAtLeastThreeSides() {
        val zones = buildList {
            CardBorderSide.entries.forEach { side ->
                repeat(6) { index ->
                    add(zone(index, if (side == CardBorderSide.BOTTOM) CardBorderColor.UNKNOWN else CardBorderColor.WHITE, side))
                }
            }
        }

        val result = CardFrameAnalyzer.classifyBorderZones(zones)

        assertEquals(CardBorderColor.WHITE, result.first)
        assertTrue(result.second >= .70)
    }

    @Test fun oneBrightSideIsNotEnoughToCallTheBorderWhite() {
        val zones = buildList {
            CardBorderSide.entries.forEach { side ->
                repeat(6) { index ->
                    add(zone(index, if (side == CardBorderSide.TOP) CardBorderColor.WHITE else CardBorderColor.BLACK, side))
                }
            }
        }

        assertTrue(CardFrameAnalyzer.classifyBorderZones(zones).first != CardBorderColor.WHITE)
    }

    @Test fun reportsFullArtWhenTheOuterEdgeContainsUnrelatedArtworkColours() {
        val colors = listOf(
            0xFF183B55.toInt(), 0xFFB56A38.toInt(), 0xFF287A4B.toInt(), 0xFF72445D.toInt(),
            0xFFE0B76B.toInt(), 0xFF315D91.toInt(), 0xFF8C2F32.toInt(), 0xFF4D7D78.toInt(),
            0xFF251F48.toInt(), 0xFFC17E92.toInt(), 0xFF44722A.toInt(), 0xFF8B713C.toInt(),
            0xFF22465D.toInt(), 0xFFD05C38.toInt(), 0xFF566B2E.toInt(), 0xFF6F356D.toInt()
        )
        val zones = colors.mapIndexed { index, color ->
            CardBorderZone(
                CardBorderSide.entries[index % CardBorderSide.entries.size],
                index / colors.size.toFloat(),
                index,
                index,
                color shr 16 and 0xff,
                color shr 8 and 0xff,
                color and 0xff,
                CardFrameAnalyzer.classifyRgb(
                    color shr 16 and 0xff,
                    color shr 8 and 0xff,
                    color and 0xff
                )
            )
        }

        val result = CardFrameAnalyzer.classifyBorderZones(zones)

        assertEquals(CardBorderColor.FULL_ART, result.first)
        assertTrue(result.second >= .45)
    }

    private fun zone(
        index: Int,
        color: CardBorderColor,
        side: CardBorderSide = CardBorderSide.entries[index % CardBorderSide.entries.size]
    ) = CardBorderZone(
        side = side,
        position = index / 16f,
        x = index,
        y = index,
        red = 0,
        green = 0,
        blue = 0,
        color = color
    )
}
