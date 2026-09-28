package io.asv.collectorvision
import org.junit.Assert.assertEquals
import org.junit.Test
class VerificationPolicyTest {
    @Test fun matchingStrongEvidenceIsConsistent() {
        assertEquals("consistent", VerificationPolicy.status("abc", true, "ABC", .9f, .7f))
    }
    @Test fun differentStrongEvidenceRequiresReview() {
        assertEquals("conflict", VerificationPolicy.status("abc", true, "def", .9f, .7f))
    }
    @Test fun ambiguousEvidenceCannotDeclareConflict() {
        for (scores in listOf(.9f to .85f, .6f to .2f, Float.NaN to .1f, .9f to Float.NaN)) {
            assertEquals("inconclusive", VerificationPolicy.status("abc", true, "def", scores.first, scores.second))
        }
        assertEquals("inconclusive", VerificationPolicy.status("abc", false, "def", .9f, .1f))
        assertEquals("inconclusive", VerificationPolicy.status("abc", true, null, .9f, .1f))
    }
}
