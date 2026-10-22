package io.asv.mtgocr.ocrreader.core.designsystem

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.CardImageCache
import io.asv.mtgocr.ocrreader.R

/** Narrow interoperability adapter to keep the existing image cache, not a legacy screen. */
@Composable
fun CardArtwork(url: String?, description: String?, modifier: Modifier = Modifier, fit: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        onReset = null,
        onRelease = { Glide.clear(it) },
        update = { view ->
            view.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            view.contentDescription = description
            CardImageCache.display(view.context, url, view)
            if (url.isNullOrBlank()) view.setImageResource(R.drawable.mtgback)
        }
    )
}
