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
        val prefix="shopping-test-${UUID.randomUUID()}"
        val isolated=object:ContextWrapper(app) {
            override fun getFilesDir()=dir
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$prefix-$name",mode)
        }
        isolated.getSharedPreferences("premium_access",0).edit().putBoolean("premium_enabled",true).commit()
        CloudLibrarySync.setAutoSyncEnabled(isolated,false)
        BuylistStore.worker.submit {
            val entry=BuyEntry(uuid=UUID.randomUUID().toString(),name="Card",set="FRA",number="3",finish="nonfoil",image="",mcmId="123")
            val created=BuylistStore.create(isolated,"Test",listOf(entry))
            assertEquals(created,BuylistStore.read(isolated).single())
            val snapshot=BuySnapshot(1,BuyPolicy.composition(created.entries),listOf(BuyQuote(entry.id,45.0,"2026-10-14")))
            val updated=BuylistStore.update(isolated,created.id) { it.copy(history=listOf(snapshot)) }
            assertEquals(updated,BuylistStore.decode(BuylistStore.encode(updated)))
            val cloud=BuylistStore.cloudState(isolated)!!
            assertEquals(listOf(updated),BuylistStore.decodeAll(cloud))
            assertTrue(runCatching { BuylistStore.restoreCloud(isolated,cloud,"stale hash") }.isFailure)
            BuylistStore.restoreCloud(isolated,cloud,CloudSnapshotCodec.sha256(cloud))
            assertEquals(updated,BuylistStore.read(isolated).single())
            isolated.getSharedPreferences("premium_access",0).edit().putBoolean("premium_enabled",false).commit()
            assertTrue(runCatching { BuylistStore.create(isolated,"Blocked") }.isFailure)
            assertEquals(updated,BuylistStore.read(isolated).single())
            isolated.getSharedPreferences("premium_access",0).edit().putBoolean("premium_enabled",true).commit()
            val imported=BuylistStore.importList(isolated,updated);assertNotEquals(imported.id,updated.id);assertEquals(updated.history,imported.history)
            BuylistStore.remove(isolated,imported.id)
            val invalid=BuylistStore.encode(updated).put("version",2)
            assertTrue(runCatching { BuylistStore.decode(invalid) }.isFailure)
            BuylistStore.update(isolated,created.id) { it.copy(entries=emptyList()) }
            assertEquals(1,BuylistStore.read(isolated).single().history.size)
            BuylistStore.remove(isolated,created.id);assertTrue(BuylistStore.read(isolated).isEmpty())
        }.get()
    }
    @Test fun searchUsesFloatingGlassAndRightAlignedIcon() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<CardSearchActivity>(Intent(c,CardSearchActivity::class.java)).use { scenario ->
            scenario.onActivity { a ->
                val query=a.findViewById<View>(R.id.txtCardSearch)
                val search=a.findViewById<View>(R.id.btnCardSearch)
                assertNotNull(a.findViewById<View>(R.id.btnClosePopup))
                assertTrue(search.left>=query.right)
                val attrs=a.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
                assertTrue(attrs.getBoolean(0,false));attrs.recycle()
            }
        }
    }
    @Test fun premiumGateHidesShoppingActionsOnEmulator() {
        org.junit.Assume.assumeTrue(android.os.Build.FINGERPRINT.contains("generic"))
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=c.getSharedPreferences("premium_access",0)
        val previous=prefs.getBoolean("premium_enabled",false)
        try {
            prefs.edit().putBoolean("premium_enabled",false).commit()
            ActivityScenario.launch<OcrCaptureActivity>(Intent(c,OcrCaptureActivity::class.java).putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scenario ->
                scenario.onActivity { a ->
                    val nav=a.findViewById<ArcaneBottomNavigationView>(R.id.bottomNavigation)
                    nav.selectedItemId=R.id.nav_shopping
                    assertNull(a.findViewById<View>(R.id.btnShoppingCreate))
                    prefs.edit().putBoolean("premium_enabled",true).commit()
                    nav.selectedItemId=R.id.nav_catalog
                    nav.selectedItemId=R.id.nav_shopping
                    assertNotNull(a.findViewById<View>(R.id.btnShoppingCreate))
                }
            }
        } finally { prefs.edit().putBoolean("premium_enabled",previous).commit() }
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
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java).putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { s ->
            s.onActivity { a ->
                val nav=a.findViewById<ArcaneBottomNavigationView>(R.id.bottomNavigation)
                nav.selectedItemId=R.id.nav_shopping
                assertEquals(R.id.nav_shopping,nav.selectedItemId)
                assertEquals(View.VISIBLE,a.findViewById<View>(R.id.shoppingSection).visibility)
                assertEquals(View.GONE,a.findViewById<View>(R.id.fabCardSearch).visibility)
                nav.selectedItemId=R.id.nav_catalog
                assertEquals(View.GONE,a.findViewById<View>(R.id.shoppingSection).visibility)
            }
        }
    }
}
