package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated files; synthetic prices are NEVER inserted into inventory or the price database. */
class BuylistDeviceTest {
    @Test fun atomicStoreRoundtripAndImportValidation() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(app.cacheDir,"shopping-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated=object:ContextWrapper(app) { override fun getFilesDir()=dir }
        BuylistStore.worker.submit {
            val entry=BuyEntry(uuid=UUID.randomUUID().toString(),name="Card",set="FRA",number="3",finish="nonfoil",image="",mcmId="123")
            val created=BuylistStore.create(isolated,"Test",listOf(entry))
            assertEquals(created,BuylistStore.read(isolated).single())
            val snapshot=BuySnapshot(1,BuyPolicy.composition(created.entries),listOf(BuyQuote(entry.id,45.0,"2026-10-14")))
            val updated=BuylistStore.update(isolated,created.id) { it.copy(history=listOf(snapshot)) }
            assertEquals(updated,BuylistStore.decode(BuylistStore.encode(updated)))
            val imported=BuylistStore.importList(isolated,updated);assertNotEquals(imported.id,updated.id);assertEquals(updated.history,imported.history)
            BuylistStore.remove(isolated,imported.id)
            val invalid=BuylistStore.encode(updated).put("version",2)
            assertTrue(runCatching { BuylistStore.decode(invalid) }.isFailure)
            BuylistStore.update(isolated,created.id) { it.copy(entries=emptyList()) }
            assertEquals(1,BuylistStore.read(isolated).single().history.size)
            BuylistStore.remove(isolated,created.id);assertTrue(BuylistStore.read(isolated).isEmpty())
        }.get()
    }
    @Test fun shoppingCardsShowUpAndDownWithoutWritingSyntheticPrices() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val entries=listOf(BuyEntry(uuid=UUID.randomUUID().toString(),name="Synthetic decrease",set="FRA",number="1",finish="nonfoil",image="",mcmId=""),BuyEntry(uuid=UUID.randomUUID().toString(),name="Synthetic increase",set="FRA",number="2",finish="foil",image="",mcmId=""))
        val composition=BuyPolicy.composition(entries)
        val list=BuyList(name="QA · synthetic only",entries=entries,history=listOf(
            BuySnapshot(1,composition,listOf(BuyQuote(entries[0].id,50.0,"2026-10-13"),BuyQuote(entries[1].id,20.0,"2026-10-13"))),
            BuySnapshot(2,composition,listOf(BuyQuote(entries[0].id,45.0,"2026-10-14"),BuyQuote(entries[1].id,22.0,"2026-10-14")))))
        ActivityScenario.launch<BuylistActivity>(Intent(c,BuylistActivity::class.java)).use { scenario ->
            Thread.sleep(500)
            scenario.onActivity { a ->
                BuylistActivity::class.java.getDeclaredField("current").apply { isAccessible=true }.set(a,list)
                BuylistActivity::class.java.getDeclaredMethod("render",List::class.java).apply { isAccessible=true }.invoke(a,listOf(list))
            }
            Thread.sleep(300)
            scenario.onActivity { a ->
                val root=a.window.decorView
                fun labels(v:View):List<String> = (if(v is TextView)listOf(v.text.toString()) else emptyList())+if(v is ViewGroup)(0 until v.childCount).flatMap { labels(v.getChildAt(it)) } else emptyList()
                assertTrue(labels(root).any { it.contains("↓") });assertTrue(labels(root).any { it.contains("↑") })
                val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
                File(a.cacheDir,"shopping-cards.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
        }
    }
    @Test fun browsingDoesNotCreateOwnedPrinting() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val dao=io.asv.mtgocr.ocrreader.data.CardDatabase.get(c).cardDao();val before=dao.ownedPrintings()
        ActivityScenario.launch<Main2Activity>(Intent(c,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,"Lightning Bolt").putExtra("browseOnly",true)).use { s ->
            s.onActivity { a -> assertFalse(a.isFinishing);assertFalse(a.findViewById<View>(R.id.detailConditionSpinner).isEnabled) }
        }
        assertEquals(before,dao.ownedPrintings())
    }
    @Test fun sixNavigationItemsAndShoppingScreenRender() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java).putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { s ->
            s.onActivity { a -> val nav=a.findViewById<ArcaneBottomNavigationView>(R.id.bottomNavigation);assertEquals(6,nav.menu.size());assertNotNull(nav.menu.findItem(R.id.nav_shopping));nav.selectedItemId=R.id.nav_catalog
                val bitmap=Bitmap.createBitmap(a.window.decorView.width,a.window.decorView.height,Bitmap.Config.ARGB_8888);a.window.decorView.draw(Canvas(bitmap))
                File(a.cacheDir,"shopping-navigation.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                assertEquals(View.VISIBLE,a.findViewById<View>(R.id.fabCardSearch).visibility) }
        }
        ActivityScenario.launch<BuylistActivity>(Intent(context,BuylistActivity::class.java)).use { s ->
            Thread.sleep(800)
            s.onActivity { a ->
                val root=a.window.decorView
                assertTrue(root.width>0 && root.height>0)
                val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
                File(a.cacheDir,"shopping-screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                fun labels(v:View):List<String> = (if(v is TextView)listOf(v.text.toString()) else emptyList())+if(v is ViewGroup)(0 until v.childCount).flatMap { labels(v.getChildAt(it)) } else emptyList()
                assertTrue(labels(root).contains(a.getString(R.string.buy_title)))
            }
        }
    }
}
