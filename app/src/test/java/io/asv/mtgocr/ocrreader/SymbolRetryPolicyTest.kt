package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class SymbolRetryPolicyTest {
    @Test fun exactlyFiveFreshAttemptsThenStop() {
        val retry = SymbolRetryPolicy()
        for (i in 1..5) {
            assertEquals(i, retry.beginAttempt())
            assertEquals(i < 5, retry.shouldRetry("distancia_excesiva", false))
        }
        assertFalse(retry.shouldRetry("margen_insuficiente", false))
    }
    @Test fun successOrNonVisualFailureNeverLoops() {
        val retry = SymbolRetryPolicy(); retry.beginAttempt()
        assertFalse(retry.shouldRetry("simbolo_confirmado", true))
        for (reason in listOf("simbolo_no_aplicable", "simbolo_reutilizado_chronicles",
            "referencias_incompletas", "error_comparacion", null)) assertFalse(retry.shouldRetry(reason, false))
    }
    @Test fun resetStartsNewBurstAndDoesNotRetryBeforeAnAttempt() {
        val retry = SymbolRetryPolicy()
        retry.beginAttempt(); retry.beginAttempt(); retry.reset()
        assertEquals(0, retry.attempts)
        assertFalse(retry.shouldRetry("sin_recorte_de_simbolo", false))
        assertEquals(1, retry.beginAttempt())
    }
    @Test fun waitForFullStabilityAndCameraWarmupNotQuickHashTrigger() {
        assertFalse(SymbolRetryPolicy.frameReady(.34f, 7, 2000))
        assertFalse(SymbolRetryPolicy.frameReady(1f, 3, 2000))
        assertFalse(SymbolRetryPolicy.frameReady(1f, 7, 899))
        // Consensus ring has seven entries; progress=1 already requires eight stable detections.
        assertTrue(SymbolRetryPolicy.frameReady(1f, 7, 900))
    }
}
