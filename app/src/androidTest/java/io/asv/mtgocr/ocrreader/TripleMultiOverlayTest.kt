package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TripleMultiOverlayTest {
    @Test fun twoImagesFollowIndependentQuadsAndMissingImageDoesNotReuseOldArt() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val overlay=TripleArOverlay(instrumentation.targetContext)
            overlay.layout(0,0,600,400)
            val red=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val blue=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val output=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888)
            overlay.cards=listOf(
                TripleArOverlay.Card("a",floatArrayOf(10f,10f,262f,10f,262f,362f,10f,362f),"",Color.GREEN),
                TripleArOverlay.Card("b",floatArrayOf(310f,10f,562f,10f,562f,362f,310f,362f),"",Color.YELLOW))
            overlay.images=mapOf("a" to TripleArOverlay.CardImage(red,"test"),"b" to TripleArOverlay.CardImage(blue,"test"))
            overlay.imageEnabled=true;overlay.draw(Canvas(output))
            assertEquals(Color.RED,output.getPixel(100,100));assertEquals(Color.BLUE,output.getPixel(400,100))
            output.eraseColor(Color.TRANSPARENT);overlay.images=mapOf("b" to TripleArOverlay.CardImage(blue,"test"));overlay.loading=setOf("a")
            overlay.draw(Canvas(output))
            assertEquals(Color.TRANSPARENT,output.getPixel(100,100));assertEquals(Color.BLUE,output.getPixel(400,100))
            output.eraseColor(Color.TRANSPARENT);overlay.cards=emptyList();overlay.points=null;overlay.draw(Canvas(output))
            assertEquals(Color.TRANSPARENT,output.getPixel(400,100))
            red.recycle();blue.recycle();output.recycle()
        }
    }
    @Test fun imageCacheLoadsOffThreadAndClearsMissingUrl() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        val file=java.io.File(c.cacheDir,"multi-image-fixture.jpg").apply { writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() }) }
        val loaded=CountDownLatch(1);val failed=CountDownLatch(1)
        lateinit var cache:TripleArImageCache
        var clearing=false
        var failureStarted=false
        instrumentation.runOnMainSync {
            cache=TripleArImageCache(c,{ images,loading ->
                if(!clearing && images.containsKey("a")) loaded.countDown()
                if(clearing && "a" in loading) failureStarted=true
                if(clearing && failureStarted && images.isEmpty() && loading.isEmpty()) failed.countDown()
            },{})
            cache.request(listOf(ArPreloadCard("a",file.toURI().toString(),"","")),emptyMap())
        }
        try {
            assertTrue(loaded.await(10,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                clearing=true
                cache.request(listOf(ArPreloadCard("a","file:///nonexistent-triple-image.jpg","","")),emptyMap())
            }
            assertTrue(failed.await(10,TimeUnit.SECONDS))
        } finally { instrumentation.runOnMainSync { cache.close() } }
    }
    @Test fun localizedImageUsesExactSetNumberAndFallsBackExplicitly() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        val file=java.io.File(c.cacheDir,"localized-ar-fixture.jpg").apply { writeBytes(instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() }) }
        val repo=io.asv.mtgocr.ocrreader.data.CardRepository.get(c)
        @Suppress("UNCHECKED_CAST")
        val variants=repo.javaClass.getDeclaredField("reviewImageLanguages").apply { isAccessible=true }.get(repo) as MutableMap<String,List<io.asv.mtgocr.ocrreader.data.CardImageVariant>>
        val key="zzz_triple_test:1";val previous=variants[key]
        variants[key]=listOf(io.asv.mtgocr.ocrreader.data.CardImageVariant("es","Test",file.toURI().toString()))
        val localized=CountDownLatch(1);val fallback=CountDownLatch(1)
        lateinit var cache:TripleArImageCache
        val card=ArPreloadCard("localization-test",file.toURI().toString(),"","","","ZZZ_TRIPLE_TEST","1")
        instrumentation.runOnMainSync {
            cache=TripleArImageCache(c,{ images,_ ->
                images[card.printing]?.let {
                    if(it.caption==c.getString(R.string.triple_ar_image_language,"ES")) localized.countDown()
                    if(it.caption==c.getString(R.string.triple_ar_image_reference)) fallback.countDown()
                }
            },{})
            cache.request(listOf(card),mapOf(card.printing to "es"))
        }
        try {
            assertTrue(localized.await(10,TimeUnit.SECONDS))
            instrumentation.runOnMainSync { cache.request(listOf(card),mapOf(card.printing to "fr")) }
            assertTrue(fallback.await(10,TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { cache.close() }
            if(previous==null) variants.remove(key) else variants[key]=previous
        }
    }

}
