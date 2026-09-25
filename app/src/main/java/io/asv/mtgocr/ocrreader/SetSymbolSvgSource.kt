package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.ScryfallImageDataProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A catalog code is not necessarily an SVG filename (PS11 -> psal, SLD -> star). */
internal object SetSymbolSvgSource {
    fun fetch(client: OkHttpClient, code: String): ByteArray {
        require(code.matches(Regex("[a-z0-9]+")))
        request(client, "https://svgs.scryfall.io/sets/$code.svg", allowNotFound = true)?.let { return it }
        val metadata = checkNotNull(request(client, "https://api.scryfall.com/sets/$code"))
        val url = JSONObject(String(metadata, Charsets.UTF_8)).getString("icon_svg_uri").toHttpUrl()
        check(url.scheme == "https" && url.host == "svgs.scryfall.io") { "Host SVG no permitido" }
        return checkNotNull(request(client, url.toString()))
    }

    private fun request(client: OkHttpClient, url: String, allowNotFound: Boolean = false): ByteArray? {
        val request = Request.Builder().url(url)
            .header("User-Agent", ScryfallImageDataProvider.USER_AGENT)
            .header("Accept", "application/json,image/svg+xml").build()
        return client.newCall(request).execute().use { response ->
            if (allowNotFound && response.code == 404) return@use null
            check(response.isSuccessful) { "HTTP ${response.code}" }
            checkNotNull(response.body?.bytes()?.takeIf { it.isNotEmpty() }) { "Respuesta vacía" }
        }
    }
}
