package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class CloudRestorePolicyTest {
    @Test fun emptySyncedLibraryMustNotResurrectDeletedCards() {
        assertFalse(CloudRestorePolicy.restoreEmpty(true, true, true))
    }
    @Test fun freshInstallCanRestoreAnEmptyPlaceholder() {
        assertTrue(CloudRestorePolicy.restoreEmpty(true, true, false))
    }
    @Test fun nonemptyLocalAndEmptyRemoteAreNotBootstrapCases() {
        assertFalse(CloudRestorePolicy.restoreEmpty(false, true, false))
        assertFalse(CloudRestorePolicy.restoreEmpty(true, false, false))
    }
}
