package io.asv.mtgocr.ocrreader

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.model.Biblio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Artwork behind detail content, using the same active library and premium pin as the tabs. */
object CollectionScreenBackground {
    fun install(activity: AppCompatActivity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val content = root.getChildAt(0) ?: return
        content.setBackgroundColor(Color.TRANSPARENT)
        val background = CardArtBackgroundView(activity).apply {
            alpha = .78f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageResource(R.drawable.mtgback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(9f, 9f, Shader.TileMode.CLAMP))
            }
        }
        root.addView(background, 0, ViewGroup.LayoutParams(-1, -1))
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                Glide.clear(background)
                owner.lifecycle.removeObserver(this)
            }
        })
        activity.lifecycleScope.launch {
            val card = withContext(Dispatchers.IO) {
                val library = LibraryCatalog.active(activity)
                val collection: Biblio? = DataUtils.readSerializable(activity, library.fileName)
                LaunchBackgroundPolicy.choose(collection?.cards.orEmpty(),
                    LibraryCatalog.pinnedBackgroundId(activity, library.id), PremiumAccess.isEnabled(activity)) {
                    Random.nextInt(it)
                }
            }
            if (activity.isFinishing || activity.isDestroyed || card == null) return@launch
            background.setArtworkOnly(LaunchArtworkUrl.isAvailable(card.imgPath))
            CardImageCache.displayKeepingCurrent(activity, LaunchArtworkUrl.resolve(card.imgPath), background)
        }
    }
}
