package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardRepository
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only collection diagnostic: resolves exact visual IDs, never accepts corrections. */
class ScanReviewCatalogDeviceTest {
    @Test fun reviewResolvesExistingSavedCandidates() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val library=LibraryCatalog.activeFile(context)
        val before=ScanReviewStore.read(context,library).filter { it.visual && it.candidateId.isNotBlank() && it.candidateLabel.isBlank() }
        android.util.Log.i("ReviewCatalogTest","Unresolved saved candidates: ${before.size}")
        androidx.test.core.app.ActivityScenario.launch(ScanReviewActivity::class.java).use {
            val deadline=System.currentTimeMillis()+30000
            var unresolved=before
            while(unresolved.isNotEmpty() && System.currentTimeMillis()<deadline) {
                Thread.sleep(500)
                unresolved=ScanReviewStore.read(context,library).filter { e -> before.any { it.key==e.key } && e.candidateLabel.isBlank() }
            }
            assertTrue("Saved candidates still unresolved: ${unresolved.map { it.candidateId }}",unresolved.isEmpty())
        }
    }
    @Test fun resolvesExactCandidateWithoutCollectionEdits() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val ids=listOf("fdfc2453-5a2b-4104-bf82-84f935de7b99","9371c4aa-424f-4b89-9f73-239a9e4f8a9d")
        for(id in ids) {
            val latch=CountDownLatch(1)
            var failure: Throwable?=null
            var count=0
            val start=System.currentTimeMillis()
            CardRepository.get(context).loadCollectorVisionPrinting(id) { options,error ->
                failure=error; count=options.size
                android.util.Log.i("ReviewCatalogTest","$id: ${options.firstOrNull()?.cardName} count=$count time=${System.currentTimeMillis()-start} error=$error")
                latch.countDown()
            }
            assertTrue("Catalogue lookup timed out: $id",latch.await(90,TimeUnit.SECONDS))
            assertNull("Exact lookup failed: $id",failure)
            assertTrue(count>0)
        }
    }
}
