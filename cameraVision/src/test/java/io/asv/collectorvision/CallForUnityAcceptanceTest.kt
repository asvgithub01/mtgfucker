package io.asv.collectorvision
import org.junit.Assert.*
import org.junit.Test
/** Scores from Milo on Scryfall reference images, not physical accuracy claims. */
class CallForUnityAcceptanceTest {
    @Test fun sameCardAmbiguitySavesTopPrintingProvisionallyAndLocksTheFamily() {
        val gate=CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        val identities=mapOf("aer" to "call for unity", "promo" to "call for unity")
        val hits=listOf(Hit("aer",.90f),Hit("promo",.89f),Hit("other",.50f))
        assertNull(gate.observe(true,hits,identities))
        val reversed=listOf(Hit("promo",.91f),Hit("aer",.90f),Hit("other",.50f))
        val result=requireNotNull(gate.observe(true,reversed,identities))
        assertEquals("promo",result.cardId)
        assertEquals(listOf("promo","aer"),result.provisionalIds)
        assertTrue(gate.complete(result,true))
        repeat(4) { assertNull(gate.observe(true,hits,identities)) }
        gate.observe(false,emptyList());gate.observe(false,emptyList())
        assertNull(gate.observe(true,hits,identities))
        assertNotNull(gate.observe(true,hits,identities))
    }
    @Test fun unknownOrDifferentCardAndTruncatedAmbiguityNeverAutoSave() {
        val hits=listOf(Hit("aer",.90f),Hit("promo",.89f),Hit("other",.50f))
        val gate=CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        repeat(4) { assertNull(gate.observe(true,hits,mapOf("aer" to "one","promo" to "two"))) }
        repeat(4) { assertNull(gate.observe(true,hits,mapOf("aer" to "one"))) }
        val allClose=listOf(Hit("aer",.90f),Hit("promo",.89f),Hit("third",.88f))
        repeat(4) { assertNull(gate.observe(true,allClose,mapOf("aer" to "one","promo" to "one","third" to "one"))) }
    }

    @Test fun normalAndPrereleaseAreTooSimilarForDefaultPrintingMargin() {
        val gate=CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        val hits=listOf(Hit("aer-9",.9991435f),Hit("paer-9s",.9796714f))
        repeat(4) { assertNull(gate.observe(true,hits)) }
        assertNotNull(gate.unresolvedMargin(true,hits))
        assertNull(gate.unresolvedMargin(false,hits))
        gate.configureFast(.55f,.01f,2)
        assertNull(gate.observe(true,hits))
        val accepted=requireNotNull(gate.observe(true,hits))
        assertEquals("aer-9",accepted.cardId)
        assertNull(gate.unresolvedMargin(true,hits))
        gate.complete(accepted,true)
        gate.configureFast(.55f,.04f,2)
        assertNull(gate.unresolvedMargin(true,hits))
    }
    @Test fun distinctCommanderArtworkPassesDefaultMargin() {
        val gate=CameraVisionAcceptanceGate().apply { setFiltersEnabled(false) }
        assertNotNull(gate.observe(true,listOf(Hit("ltc-163",.9994866f),Hit("other",.5975418f))))
    }
}
