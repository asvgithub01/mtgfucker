package io.asv.mtgocr.ocrreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.asv.mtgocr.ocrreader.app.di.SetCatalogViewModelFactory
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.feature.catalog.*

/** Debug opt-in; no replacement for existing collection/catalog entry points. */
class SetCatalogPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) { finish(); return }
        val viewModel = ViewModelProvider(this, SetCatalogViewModelFactory(applicationContext))
            .get(SetCatalogViewModel::class.java)
        setContent {
            MtgTheme(MagicPalette.primaryColor(this), MagicPalette.secondaryColor(this), MagicPalette.backgroundColor(this)) {
                androidx.compose.material3.Surface {
                    SetCatalogScreen(viewModel.state.collectAsStateWithLifecycle().value, viewModel::query,
                        viewModel::select, viewModel::dismiss, viewModel::reload) { finish() }
                }
            }
        }
    }
}
