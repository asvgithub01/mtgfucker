package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import io.asv.mtgocr.ocrreader.data.ScryfallImageDataProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Loads the official Scryfall set glyph and keeps scrolling free of SVG/network work. */
object SetSymbolLoader {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = LruCache<String, Bitmap>(128)
    private val unavailable = HashSet<String>()
    private val loading = HashSet<String>()
    private val waitingTargets = HashMap<String, MutableList<WeakReference<ImageView>>>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun display(context: Context, setCode: String, target: ImageView) {
        val appContext = context.applicationContext
        val code = setCode.trim().lowercase(Locale.ROOT)
        if (code.isBlank()) {
            target.tag = null
            target.setImageDrawable(null)
            target.visibility = View.GONE
            return
        }
        val url = "https://svgs.scryfall.io/sets/$code.svg"
        target.tag = url
        target.setColorFilter(MagicPalette.secondaryColor(context))
        cache.get(code)?.let {
            target.setImageBitmap(it)
            target.visibility = View.VISIBLE
            return
        }
        target.setImageDrawable(null)
        target.visibility = View.GONE
        synchronized(unavailable) {
            if (code in unavailable) return
            waitingTargets.getOrPut(code) { mutableListOf() }.add(WeakReference(target))
            if (!loading.add(code)) return
        }
        executor.execute {
            val bitmap = runCatching {
                downloadAndRender(appContext, code, url, target.resources.displayMetrics.density)
            }
                .getOrNull()
            val targets = synchronized(unavailable) {
                loading -= code
                if (bitmap == null) unavailable += code else cache.put(code, bitmap)
                waitingTargets.remove(code).orEmpty()
            }
            main.post {
                targets.forEach { reference ->
                    val waitingTarget = reference.get() ?: return@forEach
                    if (waitingTarget.tag != url) return@forEach
                    if (bitmap == null) {
                        waitingTarget.visibility = View.GONE
                    } else {
                        waitingTarget.setImageBitmap(bitmap)
                        waitingTarget.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun downloadAndRender(context: Context, code: String, url: String, density: Float): Bitmap {
        val directory = File(context.filesDir, "set_symbols").apply { mkdirs() }
        val file = File(directory, "$code.svg")
        if (!file.isFile || file.length() == 0L) {
            val temporary = File(directory, "$code.svg.part")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
                .header("Accept", "image/svg+xml")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    temporary.outputStream().buffered().use(input::copyTo)
                } ?: error("SVG vacío")
            }
            check(temporary.renameTo(file) || runCatching {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }.isSuccess) { "No se pudo guardar el símbolo" }
        }
        val svg = file.inputStream().buffered().use(SVG::getFromInputStream)
        val size = (44 * density).toInt().coerceAtLeast(44)
        svg.documentWidth = size.toFloat()
        svg.documentHeight = size.toFloat()
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            svg.renderToCanvas(Canvas(bitmap))
        }
    }
}
