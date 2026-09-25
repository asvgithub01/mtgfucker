package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class CollectorVisionWebPolicyTest {
    @Test fun officialDemoAndOriginAllowed() {
        assertTrue(CollectorVisionWebPolicy.trustedPage(CollectorVisionWebPolicy.DEMO_URL))
        assertTrue(CollectorVisionWebPolicy.trustedOrigin("https://hanclinto.github.io:443"))
        assertTrue(CollectorVisionWebPolicy.canRequestVideo("https://hanclinto.github.io", CollectorVisionWebPolicy.DEMO_URL,
            arrayOf(CollectorVisionWebPolicy.VIDEO_CAPTURE)))
    }
    @Test fun untrustedSchemesHostsPortsAndCredentialsDenied() {
        listOf("http://hanclinto.github.io", "https://hanclinto.github.io.evil.test", "https://evil.test", "file:///test",
            "javascript:alert(1)", "https://user@hanclinto.github.io", "https://hanclinto.github.io:8443", "not a URI").forEach {
            assertFalse(it, CollectorVisionWebPolicy.trustedOrigin(it))
        }
    }
    @Test fun navigationConfinedToDemoDirectory() {
        listOf("https://hanclinto.github.io/", "https://hanclinto.github.io/CollectorVision-evil/",
            "https://hanclinto.github.io/CollectorVision/../other/", "https://hanclinto.github.io/CollectorVision/%2e%2e/other/").forEach {
            assertFalse(it, CollectorVisionWebPolicy.trustedPage(it))
        }
    }
    @Test fun microphoneAndOtherPermissionRequestsDenied() {
        listOf(emptyArray(), arrayOf("android.webkit.resource.AUDIO_CAPTURE"), arrayOf("unknown")).forEach {
            assertFalse(CollectorVisionWebPolicy.canRequestVideo("https://hanclinto.github.io", CollectorVisionWebPolicy.DEMO_URL, it))
        }
        assertFalse(CollectorVisionWebPolicy.canRequestVideo("https://evil.test", CollectorVisionWebPolicy.DEMO_URL,
            arrayOf(CollectorVisionWebPolicy.VIDEO_CAPTURE)))
    }
}
