package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.app.ui.*
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import io.asv.mtgocr.ocrreader.app.di.LauncherDependencies
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.feature.launcher.LauncherRoute
import io.asv.mtgocr.ocrreader.feature.launcher.LauncherViewModel
import io.asv.mtgocr.ocrreader.legacybridge.LegacyLibraryNavigation

/** Launcher only: the scanner host and all scanner sources remain unchanged. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this, LauncherDependencies(applicationContext).factory)
            .get(LauncherViewModel::class.java)
        setContent {
            MtgTheme(
                MagicPalette.primaryColor(this),
                MagicPalette.secondaryColor(this),
                MagicPalette.backgroundColor(this)
            ) {
                LauncherRoute(viewModel, legacyLauncherBranding(),
                    artwork = { url, modifier -> LegacyLauncherArtwork(url, modifier) }, onPreview = if (BuildConfig.DEBUG) { id, name ->
                    startActivity(Intent(this, BiblioShellActivity::class.java)
                        .putExtra(BiblioShellActivity.EXTRA_LIBRARY_ID, id)
                        .putExtra(BiblioShellActivity.EXTRA_LIBRARY_NAME, name))
                } else null) {
                    val ask = viewModel.prepareOpen()
                    if (ask != null) {
                        try {
                            startActivity(LegacyLibraryNavigation.intent(this, ask))
                            viewModel.navigationSucceeded()
                        } catch (_: android.content.ActivityNotFoundException) {
                            viewModel.navigationFailed()
                        }
                    }
                }
            }
        }
    }
}
