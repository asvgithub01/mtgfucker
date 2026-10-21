package io.asv.mtgocr.ocrreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import io.asv.mtgocr.ocrreader.app.di.LibraryShellDependencies
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellRoute
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellViewModel

/** Opt-in read-only preview. Registered only in the debug manifest; not the default Biblio. */
class BiblioShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()
        if (!BuildConfig.DEBUG || libraryId.isBlank()) { finish(); return }
        val viewModel = ViewModelProvider(this, LibraryShellDependencies(applicationContext, libraryId).factory)
            .get(LibraryShellViewModel::class.java)
        val libraryName = intent.getStringExtra(EXTRA_LIBRARY_NAME).orEmpty()
        setContent {
            MtgTheme(MagicPalette.primaryColor(this), MagicPalette.secondaryColor(this), MagicPalette.backgroundColor(this)) {
                androidx.compose.material3.Surface {
                    LibraryShellRoute(libraryName, viewModel) { finish() }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "shellLibraryId"
        const val EXTRA_LIBRARY_NAME = "shellLibraryName"
    }
}
