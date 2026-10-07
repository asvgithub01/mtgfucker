package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class ArReferencePolicyTest {
    @Test fun sameReferenceKeepsTrackingForPriceOrFinishUpdates() {
        assertTrue(ArReferencePolicy.canReuse("printing","image","printing","image",true))
    }
    @Test fun changedPrintingOrImageRequiresNewReference() {
        assertFalse(ArReferencePolicy.canReuse("old","image","new","image",true))
        assertFalse(ArReferencePolicy.canReuse("printing","old","printing","new",true))
    }
    @Test fun failedOrMissingReferenceCanRetry() {
        assertFalse(ArReferencePolicy.canReuse("printing","image","printing","image",false))
        assertFalse(ArReferencePolicy.canReuse(null,null,null,null,true))
        assertFalse(ArReferencePolicy.canReuse("","image","","image",true))
    }
}
