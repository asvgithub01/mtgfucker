package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class StartupPriceRefreshPolicyTest {
    @Test fun onlyAfterOneWeek() {
        val updated=1000L
        assertFalse(StartupPriceRefreshPolicy.shouldPrompt(updated,updated))
        assertFalse(StartupPriceRefreshPolicy.shouldPrompt(updated,updated+StartupPriceRefreshPolicy.WEEK_MILLIS))
        assertTrue(StartupPriceRefreshPolicy.shouldPrompt(updated,updated+StartupPriceRefreshPolicy.WEEK_MILLIS+1))
        assertTrue(StartupPriceRefreshPolicy.shouldPrompt(0,updated))
        assertFalse(StartupPriceRefreshPolicy.shouldPrompt(updated,updated-1))
    }
}
