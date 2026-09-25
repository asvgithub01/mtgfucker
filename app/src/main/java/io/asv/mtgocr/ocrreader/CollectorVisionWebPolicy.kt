package io.asv.mtgocr.ocrreader

import java.net.URI

/** No native bridge: only the official demo may request the camera. */
internal object CollectorVisionWebPolicy {
    const val DEMO_URL = "https://hanclinto.github.io/CollectorVision/"
    const val VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
    fun canRequestVideo(origin: String, page: String, resources: Array<String>): Boolean =
        trustedOrigin(origin) && trustedPage(page) && resources.contains(VIDEO_CAPTURE)

    fun trustedOrigin(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "hanclinto.github.io" &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
    fun trustedPage(url: String): Boolean = trustedOrigin(url) && runCatching {
        URI(null, null, URI(url).path, null).normalize().path.startsWith("/CollectorVision/")
    }.getOrDefault(false)
}
