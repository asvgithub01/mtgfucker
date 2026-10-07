package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test

class TripleScanPolicyTest {
    @Test fun trackingSuppressesFallback() { assertFalse(TripleScanPolicy.useVisual(10000,0,0,true)) }
    @Test fun graceIsBoundedAndResetsAfterTracking() {
        assertFalse(TripleScanPolicy.useVisual(1100,1000,0,false))
        assertTrue(TripleScanPolicy.useVisual(1450,1000,0,false))
        assertFalse(TripleScanPolicy.useVisual(2100,1000,2000,false))
    }
    @Test fun noSetDoesNotNeedAnArMatchFirst() { assertTrue(TripleScanPolicy.useVisual(5000,0,0,false)) }
    @Test fun weakOrAmbiguousVisualEvidenceIsNotAccepted() {
        assertFalse(TripleScanPolicy.visualReliable(.9f,null,3))
        assertFalse(TripleScanPolicy.visualReliable(.9f,.89f,3))
        assertFalse(TripleScanPolicy.visualReliable(.6f,.3f,3))
        assertFalse(TripleScanPolicy.visualReliable(.9f,.3f,1))
        assertFalse(TripleScanPolicy.visualReliable(Float.NaN,.3f,3))
        assertTrue(TripleScanPolicy.visualReliable(.9f,.7f,2))
    }
    @Test fun uncertainLanguageIsNotInventedFromReference() {
        assertNull(TripleScanPolicy.language("en",0f))
        assertNull(TripleScanPolicy.language("und",1f))
        assertNull(TripleScanPolicy.language("it",.6f))
        assertEquals("it",TripleScanPolicy.language("it",.95f))
    }
    @Test fun canonicalTitleAgreementIgnoresAccentsNotDifferentCards() {
        assertTrue(TripleScanPolicy.agrees("Nicol Bolas, the Ravager","Nicol Bolas the Ravager"))
        assertFalse(TripleScanPolicy.agrees("Zombie Master","Zombie Lord"))
    }
    @org.junit.Test fun provisionalDoesNotRequireEditionMargin() {
        org.junit.Assert.assertTrue(TripleScanPolicy.visualCandidate(.9f,1))
        org.junit.Assert.assertTrue(TripleScanPolicy.visualCandidate(.72f,2))
        org.junit.Assert.assertFalse(TripleScanPolicy.visualReliable(.9f,.89f,2))
        org.junit.Assert.assertFalse(TripleScanPolicy.visualCandidate(.72f,1))
        org.junit.Assert.assertFalse(TripleScanPolicy.visualCandidate(Float.NaN,10))
        org.junit.Assert.assertFalse(TripleScanPolicy.visualCandidate(.69f,10))
    }
    @org.junit.Test fun auditIsBounded() {
        org.junit.Assert.assertFalse(TripleScanPolicy.auditDue(1499,0))
        org.junit.Assert.assertTrue(TripleScanPolicy.auditDue(1500,0))
    }
}
