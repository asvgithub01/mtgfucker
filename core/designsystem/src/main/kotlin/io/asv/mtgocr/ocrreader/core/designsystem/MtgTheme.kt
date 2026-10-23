package io.asv.mtgocr.ocrreader.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun MtgTheme(primary: Int, secondary: Int, background: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = readablePrimary(Color(primary), Color(background)), onPrimary = Color.White,
            secondary = Color(secondary), background = Color(background),
            surface = Color(background)
        ),
        content = content
    )
}

/** Keep the palette hue, darkening only when needed for text actions and white button labels.
 * This theme is intentionally light, matching the existing Activity palettes.
 */
internal fun readablePrimary(primary: Color, background: Color): Color {
    fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
    fun readable(color: Color) =
        contrast(color, background) >= 4.6f && contrast(Color.White, color) >= 4.6f
    // Leave unsupported dark backgrounds unchanged; dark themes need their own full scheme.
    if (readable(primary) || !readable(Color.Black)) return primary
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2
        if (readable(lerp(primary, Color.Black, mid))) high = mid else low = mid
    }
    return lerp(primary, Color.Black, high)
}
