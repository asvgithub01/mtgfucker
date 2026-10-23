package io.asv.mtgocr.ocrreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.app.di.ServicesDependencies
import io.asv.mtgocr.ocrreader.feature.services.*

/** Query-only debug entry, no navigation to service Activities with startup side effects. */
class ServicesPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) { finish(); return }
        val model = ViewModelProvider(this, ServicesDependencies(this).factory)[ServicesViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(model) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() } }
            MtgTheme(MagicPalette.primaryColor(this), MagicPalette.secondaryColor(this), MagicPalette.backgroundColor(this)) {
                Surface { ServicesScreen(state, model::refresh, { finish() }) }
            }
        }
    }
}
