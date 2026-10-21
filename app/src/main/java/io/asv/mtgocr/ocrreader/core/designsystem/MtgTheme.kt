package io.asv.mtgocr.ocrreader.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MtgTheme(primary: Int, secondary: Int, background: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(primary), onPrimary = Color.White,
            secondary = Color(secondary), background = Color(background),
            surface = Color(background)
        ),
        content = content
    )
}
