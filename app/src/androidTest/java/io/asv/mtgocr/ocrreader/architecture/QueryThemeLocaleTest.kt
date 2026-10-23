package io.asv.mtgocr.ocrreader.architecture

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.MagicPalette
import io.asv.mtgocr.ocrreader.R
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.domain.photos.*
import io.asv.mtgocr.ocrreader.feature.photos.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale
import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

/** Real resource themes, isolated locale/night configuration; never writes appearance preferences. */
@RunWith(Parameterized::class)
class QueryThemeLocaleTest(private val language: String, private val night: Boolean) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-night={1}")
        fun variants() = listOf("es", "en", "de", "fr", "it", "pt").flatMap { language ->
            listOf(false, true).map { arrayOf<Any>(language, it) }
        }
    }

    @Test fun localizedPhotoQueryAndPaletteContrast() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val localized = base.createConfigurationContext(config)
        val styles = listOf(R.style.Theme_Mtg_Green, R.style.Theme_Mtg_Red,
            R.style.Theme_Mtg_Blue, R.style.Theme_Mtg_Black,
            R.style.Theme_Mtg_White, R.style.Theme_Mtg_Metal)
        val palettes = styles.map { ContextThemeWrapper(localized, it) }
        val ratios = mutableListOf<String>()
        compose.setContent {
            // All six production palettes, including the historically pale White/Metal.
            palettes.forEachIndexed { index, context ->
                MtgTheme(MagicPalette.primaryColor(context), MagicPalette.secondaryColor(context),
                    MagicPalette.backgroundColor(context)) {
                    val c = MaterialTheme.colorScheme
                    listOf("body" to (c.onSurface to c.surface),
                        "action" to (c.primary to c.surface),
                        "filled" to (c.onPrimary to c.primary),
                        "secondaryBody" to (c.onSurfaceVariant to c.surface)).forEach { (role, pair) ->
                        val ratio = ColorUtils.calculateContrast(pair.first.toArgb(), pair.second.toArgb())
                        assertTrue("$language night=$night palette=$index $role=$ratio", ratio >= 4.5)
                        ratios.add("$index,$role,$ratio")
                    }
                }
            }
            val context = palettes[if (night) 4 else 5]
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MtgTheme(MagicPalette.primaryColor(context), MagicPalette.secondaryColor(context),
                    MagicPalette.backgroundColor(context)) {
                    Surface(Modifier.width(320.dp).height(480.dp).testTag("locale-fixture")) {
                        PhotoScreen(PhotoUiState(false, PhotoSnapshot(
                            PhotoSection(listOf(PhotoRecord("same", "Fixture", null, "Details"))),
                            PhotoSection(listOf(PhotoRecord("same", "Imported fixture", null, "Global details"))))),
                            {}, {}) { Text("Image fixture") }
                    }
                }
            }
        }
        val library = localized.getString(PhotosR.string.photo_preview_library)
        val global = localized.getString(PhotosR.string.photo_preview_global)
        compose.onNodeWithText(library).performScrollTo().assertIsSelected()
        compose.onNodeWithText(global).performScrollTo().performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Imported fixture").assertIsDisplayed()
        val bitmap = compose.onNodeWithTag("locale-fixture").captureToImage().asAndroidBitmap()
        File(base.cacheDir, "query-$language-$night.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        File(base.cacheDir, "contrast-$language-$night.csv").writeText(ratios.distinct().joinToString("\n"))
        compose.onNodeWithText("Imported fixture").performClick()
        compose.onNodeWithText("Global details").assertIsDisplayed()
        compose.onNodeWithText(localized.getString(android.R.string.ok)).performClick()
        compose.onNodeWithText("Global details").assertDoesNotExist()
    }
}
