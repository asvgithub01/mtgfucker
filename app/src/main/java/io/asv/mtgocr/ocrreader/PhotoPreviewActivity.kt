package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.feature.photos.R as PhotosR

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.asv.mtgocr.ocrreader.core.designsystem.MtgTheme
import io.asv.mtgocr.ocrreader.data.legacy.LegacyPhotoReader
import io.asv.mtgocr.ocrreader.data.legacy.PhotoPathPolicy
import io.asv.mtgocr.ocrreader.feature.photos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Debug query-only entry: deliberately does not open legacy photo/review Activities. */
class PhotoPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(BiblioShellActivity.EXTRA_LIBRARY_ID).orEmpty()
        if (!BuildConfig.DEBUG || id.isBlank()) { finish(); return }
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PhotoViewModel(LegacyPhotoReader(applicationContext), id) as T
            }
        })[PhotoViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(model) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() }
            }
            MtgTheme(MagicPalette.primaryColor(this), MagicPalette.secondaryColor(this), MagicPalette.backgroundColor(this)) {
                Surface { PhotoScreen(state, model::refresh, { finish() }) { path -> key(path) { LocalPhoto(path) } } }
            }
        }
    }

    @Composable private fun LocalPhoto(path: String?) {
        // No image/network cache writes. Decode off main, sampled to a bounded preview.
        val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
            value = null
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val absolute = path ?: return@runCatching null
                    val relative = File(absolute).canonicalFile.relativeTo(filesDir.canonicalFile).path
                    val file = PhotoPathPolicy.resolve(filesDir, absolute, true)
                        ?: PhotoPathPolicy.resolve(filesDir, relative, false) ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            }
        }
        if (bitmap == null) Text(stringResource(PhotosR.string.photo_preview_unavailable))
        else Image(bitmap!!.asImageBitmap(), stringResource(PhotosR.string.photo_preview_title),
            Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Fit)
    }
}
