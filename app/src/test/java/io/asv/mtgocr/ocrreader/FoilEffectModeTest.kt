package io.asv.mtgocr.ocrreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoilEffectModeTest {
    @Test
    fun `effects can be independently combined and removed`() {
        var mask = FoilEffectMode.DEFAULT
        assertTrue(FoilEffectMode.isEnabled(mask, FoilEffectMode.HOLOGRAPHIC))

        mask = FoilEffectMode.withMode(mask, FoilEffectMode.RAINBOW_ALT, true)
        assertTrue(FoilEffectMode.isEnabled(mask, FoilEffectMode.HOLOGRAPHIC))
        assertTrue(FoilEffectMode.isEnabled(mask, FoilEffectMode.RAINBOW_ALT))

        mask = FoilEffectMode.withMode(mask, FoilEffectMode.HOLOGRAPHIC, false)
        assertFalse(FoilEffectMode.isEnabled(mask, FoilEffectMode.HOLOGRAPHIC))
        assertTrue(FoilEffectMode.isEnabled(mask, FoilEffectMode.RAINBOW_ALT))
    }

    @Test
    fun `unknown bits never leak into the shader mask`() {
        assertEquals(
            0,
            FoilEffectMode.withMode(Int.MAX_VALUE, FoilEffectMode.RAINBOW, false) and
                FoilEffectMode.RAINBOW
        )
        assertEquals(
            FoilEffectMode.ALL,
            FoilEffectMode.withMode(Int.MAX_VALUE, FoilEffectMode.HOLOGRAPHIC, true)
        )
    }
}
