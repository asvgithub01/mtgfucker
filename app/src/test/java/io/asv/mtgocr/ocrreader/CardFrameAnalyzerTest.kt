package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFrameAnalyzerTest {
    @Test fun classifiesIndividualBorderZones() {
        assertEquals(CardBorderColor.BLACK, CardFrameAnalyzer.classifyRgb(24, 23, 21))
        assertEquals(CardBorderColor.WHITE, CardFrameAnalyzer.classifyRgb(225, 219, 205))
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

    private fun zone(index: Int, color: CardBorderColor) = CardBorderZone(
        side = CardBorderSide.entries[index % CardBorderSide.entries.size],
        position = index / 16f,
        x = index,
        y = index,
        red = 0,
        green = 0,
        blue = 0,
        color = color
    )
}
