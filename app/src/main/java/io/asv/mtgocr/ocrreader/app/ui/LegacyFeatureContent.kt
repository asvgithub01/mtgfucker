package io.asv.mtgocr.ocrreader.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.asv.mtgocr.ocrreader.R
import io.asv.mtgocr.ocrreader.core.designsystem.CardArtwork
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.feature.carddetail.CollectionCardDetail
import io.asv.mtgocr.ocrreader.feature.launcher.LauncherBranding
import io.asv.mtgocr.ocrreader.feature.library.LibraryDestinationLabels
import io.asv.mtgocr.ocrreader.feature.catalog.R as CatalogR
import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR
import io.asv.mtgocr.ocrreader.feature.services.R as ServicesR

/** Composition adapters only: keep legacy resources/cache in app, never in a feature. */
@Composable
fun legacyLauncherBranding() = LauncherBranding(
    stringResource(R.string.app_display_name), stringResource(R.string.launch_tagline),
    stringResource(R.string.choose_library), stringResource(R.string.mode_biblio),
    FontFamily(Font(R.font.mtg_title))
)

@Composable
fun legacyLibraryDestinationLabels() = LibraryDestinationLabels(
    stringResource(CatalogR.string.catalog_title), stringResource(PhotosR.string.photo_preview_title),
    stringResource(ServicesR.string.services_title)
)

@Composable
fun LegacyLauncherArtwork(url: String?, modifier: Modifier) {
    CardArtwork(url, stringResource(R.string.launch_background), modifier)
}

@Composable
fun LegacyLibraryArtwork(url: String?, modifier: Modifier) {
    CardArtwork(url, null, modifier)
}

@Composable
fun LegacyCollectionCardDetail(card: CollectionRow, onDismiss: () -> Unit) {
    CollectionCardDetail(card, onDismiss, artwork = { url, name, modifier ->
        CardArtwork(url, name, modifier, fit = true)
    })
}
