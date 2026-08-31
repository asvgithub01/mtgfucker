package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keeps every image that has been requested in app-private storage.
 *
 * Glide's own cache is still used while the permanent copy is downloaded, but files in this
 * directory are not subject to Glide's LRU eviction. They survive restarts and offline use until
 * the user clears the application's data or uninstalls it.
 */
object CardImageCache {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloads = Collections.synchronizedSet(mutableSetOf<String>())

    @JvmStatic
    fun display(context: Context, imageUrl: String?, target: ImageView) {
        displayInternal(context, imageUrl, target, keepCurrentDrawable = false)
    }

    /**
     * Replaces a fullscreen image without clearing the drawable that is already visible.
     *
     * Glide normally clears an ImageView as soon as a new request starts. That is desirable for
     * recycled list rows, but it creates a black frame while paging through fullscreen images.
     */
    @JvmStatic
    fun displayKeepingCurrent(context: Context, imageUrl: String?, target: ImageView) {
        displayInternal(context, imageUrl, target, keepCurrentDrawable = true)
    }

    private fun displayInternal(
        context: Context,
        imageUrl: String?,
        target: ImageView,
        keepCurrentDrawable: Boolean
    ) {
        val url = imageUrl?.trim().orEmpty()
        if (target.getTag(R.id.card_image_cache_url) == url && target.drawable != null) {
            // Rebinding a RecyclerView row or reselecting the current language must not restart
            // the same Glide request. Clearing and restoring that drawable was the gallery flash.
            return
        }
        // Glide 3 owns the unkeyed View.tag; use a keyed tag for recycled-view protection.
        target.setTag(R.id.card_image_cache_url, url)
        if (url.isBlank()) {
            Glide.clear(target)
            target.setImageDrawable(null)
            return
        }

        val cached = cachedFile(context, url)
        if (cached.isFile && cached.length() > 0L) {
            val request = Glide.with(context).load(cached)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .dontAnimate()
            if (keepCurrentDrawable) request.placeholder(target.drawable)
            request.into(target)
            return
        }

        // Show the network image as soon as possible while creating the durable local copy.
        val request = Glide.with(context).load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .dontAnimate()
        if (keepCurrentDrawable) request.placeholder(target.drawable)
        request.into(target)

        // The downloaded file is for subsequent/offline requests. Reloading the exact same bitmap
        // from that file as soon as it finishes used to clear and redraw the target a second time.
        download(context.applicationContext, url, null)
    }

    @JvmStatic
    fun prefetch(context: Context, imageUrl: String?) {
        val url = imageUrl?.trim().orEmpty()
        if (url.isNotBlank()) download(context.applicationContext, url, null)
    }

    private fun download(context: Context, url: String, onCached: ((File) -> Unit)?) {
        val destination = cachedFile(context, url)
        if (destination.isFile && destination.length() > 0L) {
            onCached?.let { callback -> mainHandler.post { callback(destination) } }
            return
        }
        if (!downloads.add(url)) return

        executor.execute {
            var temporary: File? = null
            try {
                destination.parentFile?.mkdirs()
                temporary = File(destination.parentFile, "${destination.name}.${Thread.currentThread().id}.tmp")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MTGOcrCollection/2.0 (Android)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CardImageCache", "HTTP ${response.code} al cachear $url")
                        return@use
                    }
                    val body = response.body ?: return@use
                    FileOutputStream(temporary).use { output -> body.byteStream().use { it.copyTo(output) } }
                    if (temporary.length() > 0L) {
                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(destination, overwrite = true)
                            temporary.delete()
                        }
                    }
                }
                if (destination.isFile && destination.length() > 0L && onCached != null) {
                    mainHandler.post { onCached(destination) }
                }
            } catch (error: Exception) {
                temporary?.delete()
                Log.w("CardImageCache", "No se pudo cachear la imagen $url", error)
                // Glide can still use its normal network/disk-cache path; retry on a later bind.
            } finally {
                downloads.remove(url)
            }
        }
    }

    private fun cachedFile(context: Context, url: String): File {
        val directory = File(context.filesDir, "card_images")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.image")
    }
}
