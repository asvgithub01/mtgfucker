package io.asv.mtgocr.ocrreader
import org.junit.Assert.assertEquals
import org.junit.Test
class ReviewImageStateTest {
    @Test fun inconclusiveIsFinishedNotLoading() {
        assertEquals(ReviewImageState.Missing.NO_IDENTIFICATION,ReviewImageState.missing(true,"inconclusive","","",false))
    }
    @Test fun acceptedCandidateWaitsForCatalogueNotRecognition() {
        assertEquals(ReviewImageState.Missing.CATALOG_PENDING,ReviewImageState.missing(true,"pending","uuid","",false))
    }
    @Test fun catalogueFailureOverridesPending() {
        assertEquals(ReviewImageState.Missing.CATALOG_ERROR,ReviewImageState.missing(true,"pending","uuid","",true))
    }
    @Test fun knownCardWithoutPictureIsNotPending() {
        assertEquals(ReviewImageState.Missing.NO_IMAGE,ReviewImageState.missing(true,"conflict","uuid","Card",false))
    }
    @Test fun queuedAnalysisRemainsPending() {
        assertEquals(ReviewImageState.Missing.ANALYSIS_PENDING,ReviewImageState.missing(true,"pending","","",false))
    }
}
