package io.asv.mtgocr.ocrreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import io.asv.mtgocr.ocrreader.app.di.LibraryShellDependencies
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellRoute
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellViewModel
import io.asv.mtgocr.ocrreader.legacybridge.LegacyShellNavigation
import io.asv.mtgocr.ocrreader.legacybridge.ShellLegacyDestination

/** Opt-in read-only preview. Registered only in the debug manifest; not the default Biblio. */
class BiblioShellActivity : ComponentActivity() {
    private var navigating = false
    private lateinit var viewModel: LibraryShellViewModel
    private val legacy = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        navigating = false
        if (::viewModel.isInitialized) viewModel.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        val libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()
        if (!BuildConfig.DEBUG || libraryId.isBlank()) { finish(); return }
        navigating = savedInstanceState?.getBoolean("legacyInFlight") ?: false
        viewModel = ViewModelProvider(this, LibraryShellDependencies(applicationContext, libraryId).factory)
            .get(LibraryShellViewModel::class.java)
        val libraryName = intent.getStringExtra(EXTRA_LIBRARY_NAME).orEmpty()
        setContent {
            MtgTheme(MagicPalette.primaryColor(this), MagicPalette.secondaryColor(this), MagicPalette.backgroundColor(this)) {
                androidx.compose.material3.Surface {
                    LibraryShellRoute(libraryName, viewModel,
                        onOpenPhotos = { startActivity(android.content.Intent(this, PhotoPreviewActivity::class.java)
                            .putExtra(EXTRA_LIBRARY_ID, libraryId)) },
                        onOpenCatalog = { startActivity(android.content.Intent(this, SetCatalogPreviewActivity::class.java)) },
                        onOpenLegacy = { openLegacy(libraryId, ShellLegacyDestination.LIBRARY) },
                        onOpenTriple = { openLegacy(libraryId, ShellLegacyDestination.TRIPLE) }) { finish() }
                }
            }
        }
    }

    private fun openLegacy(libraryId: String, destination: ShellLegacyDestination) {
        if (navigating || viewModel.state.value.loading || viewModel.state.value.failure != null) return
        val target = LegacyShellNavigation.intent(this, libraryId, destination)
        if (target == null) { viewModel.refresh(); return }
        navigating = true
        try { legacy.launch(target) }
        catch (_: android.content.ActivityNotFoundException) { navigating = false; viewModel.refresh() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("legacyInFlight", navigating)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "shellLibraryId"
        const val EXTRA_LIBRARY_NAME = "shellLibraryName"
    }
}
