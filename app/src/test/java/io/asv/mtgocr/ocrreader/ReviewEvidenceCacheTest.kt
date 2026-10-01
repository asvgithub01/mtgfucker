package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReviewEvidenceCacheTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun unchangedPendingOutcomeDoesNotRunAgain() {
        assertTrue(ReviewEvidenceCache.unchangedOutcome("skipped","weak",10,20,"same","same"))
    }
    @Test fun manualEditOrNewEvidenceMustBeReevaluated() {
        assertFalse(ReviewEvidenceCache.unchangedOutcome("skipped","weak",10,20,"old","new"))
        assertFalse(ReviewEvidenceCache.unchangedOutcome("skipped","weak",21,20,"same","same"))
    }
    @Test fun transientErrorsAreNotCachedAsUnresolvable() {
        assertFalse(ReviewEvidenceCache.unchangedOutcome("error","catalog",10,20,"same","same"))
        assertFalse(ReviewEvidenceCache.unchangedOutcome("skipped","catalog",10,20,"same","same"))
        assertFalse(ReviewEvidenceCache.unchangedOutcome("skipped","changed",10,20,"same","same"))
    }
    @Test fun identicalEvidenceHasStableFingerprint() {
        val photo=folder.newFile("photo.jpg").apply { writeText("capture") }
        val ocr=folder.newFile("ocr.json").apply { writeText("reading") }
        assertEquals(ReviewEvidenceCache.digest(listOf(photo,ocr)),ReviewEvidenceCache.digest(listOf(photo,ocr)))
    }
    @Test fun contentChangeInvalidatesEvenWithSameLengthAndTimestamp() {
        val photo=folder.newFile("photo.jpg").apply { writeText("before") }
        val stamp=photo.lastModified()
        val before=ReviewEvidenceCache.digest(listOf(photo))
        photo.writeText("after!"); photo.setLastModified(stamp)
        assertNotEquals(before,ReviewEvidenceCache.digest(listOf(photo)))
    }
    @Test fun differentOcrInvalidatesEvidence() {
        val photo=folder.newFile("photo.jpg").apply { writeText("capture") }
        val ocr=folder.newFile("ocr.json").apply { writeText("EN") }
        val before=ReviewEvidenceCache.digest(listOf(photo,ocr))
        ocr.writeText("ES")
        assertNotEquals(before,ReviewEvidenceCache.digest(listOf(photo,ocr)))
    }
    @Test fun freejamAmbiguousPrintingStillRequiresManualReview() {
        assertFalse(ReliableReviewPolicy.artReliable(.6327614188194275,.6327614188194275-.6256070137023926))
    }
}
