package io.asv.mtgocr.ocrreader.architecture

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.asv.mtgocr.ocrreader.R
import io.asv.mtgocr.ocrreader.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeatureCompositionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hostKeepsLegacyBrandingAndNullArtworkPlaceholdersWithoutNetwork() {
        compose.setContent {
            val branding = legacyLauncherBranding()
            SideEffect {
                assertEquals(compose.activity.getString(R.string.app_display_name), branding.appName)
                assertEquals(compose.activity.getString(R.string.mode_biblio), branding.openLibrary)
            }
            Column {
                LegacyLauncherArtwork(null, Modifier.size(64.dp))
                LegacyLibraryArtwork(null, Modifier.size(64.dp))
            }
        }
        compose.runOnIdle {
            fun images(view: View): List<ImageView> = when (view) {
                is ImageView -> listOf(view)
                is ViewGroup -> (0 until view.childCount).flatMap { images(view.getChildAt(it)) }
                else -> emptyList()
            }
            val images = images(compose.activity.window.decorView)
            assertEquals(2, images.size)
            assertEquals(compose.activity.getString(R.string.launch_background), images[0].contentDescription)
            assertNull(images[1].contentDescription)
            images.forEach {
                assertEquals(ImageView.ScaleType.CENTER_CROP, it.scaleType)
                assertNotNull(it.drawable)
            }
        }
    }
}
