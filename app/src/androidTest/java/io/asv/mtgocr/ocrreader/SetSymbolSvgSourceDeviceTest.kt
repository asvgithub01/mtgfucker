package io.asv.mtgocr.ocrreader

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SetSymbolSvgSourceDeviceTest {
    @Test fun resolvesOfficialIconUrlWhenCodeIsNotFilename() {
        val calls = arrayListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            calls += url
            val (status, body) = when (url) {
                "https://svgs.scryfall.io/sets/ps11.svg" -> 404 to "missing"
                "https://api.scryfall.com/sets/ps11" -> 200 to """{"icon_svg_uri":"https://svgs.scryfall.io/sets/psal.svg?123"}"""
                "https://svgs.scryfall.io/sets/psal.svg?123" -> 200 to "<svg/>"
                else -> error("Unexpected request $url")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test").body(body.toResponseBody()).build()
        }.build()
        assertEquals("<svg/>", String(SetSymbolSvgSource.fetch(client, "ps11")))
        assertEquals(3, calls.size)
    }

    @Test fun directSvgDoesNotNeedApiAndTimeoutDoesNotMasqueradeAsAnAlias() {
        for (status in listOf(200, 503)) {
            var count = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                count++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test").body("<svg/>".toResponseBody()).build()
            }.build()
            assertEquals(status == 200, runCatching { SetSymbolSvgSource.fetch(client, "ori") }.isSuccess)
            assertEquals(1, count)
        }
    }
}
