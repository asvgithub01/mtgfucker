package io.asv.mtgocr.ocrreader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Live exact-price regression: read-only inventory; only catalog/price caches may change. */
@RunWith(AndroidJUnit4::class)
class TriplePriceFallbackTest {
    @Test fun stingerquill434DoesNotBorrowThe150Price() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val before=LegacyCollectionStore.cards(context).map { Triple(it.collectionItemId,it.printingUuid,it.quantity) }
        val repository=CardRepository.get(context)
        var option:CardEditionOption?=null;var error:Throwable?=null
        var latch=CountDownLatch(1)
        repository.loadCollectorVisionPrinting("7c9013bc-bc85-49b2-ad9b-745b97d6775d") { choices,failure -> option=choices.firstOrNull { it.finish=="nonfoil" };error=failure;latch.countDown() }
        assertTrue(latch.await(45,TimeUnit.SECONDS));assertNull(error)
        val printing=requireNotNull(option).printingUuid
        repeat(2) {
            latch=CountDownLatch(1)
            repository.loadScannerPrice(printing,"nonfoil") { priced,failure -> option=priced;error=failure;latch.countDown() }
            assertTrue(latch.await(15,TimeUnit.SECONDS));assertNull(error)
            assertEquals(printing,option?.printingUuid);assertEquals("434",option?.collectorNumber)
            assertEquals("nonfoil",option?.finish);assertEquals("EUR",option?.currency)
            assertTrue(option.toString(),(option?.price ?: 0.0)>0)
        }
        assertEquals(before,LegacyCollectionStore.cards(context).map { Triple(it.collectionItemId,it.printingUuid,it.quantity) })
    }
}
