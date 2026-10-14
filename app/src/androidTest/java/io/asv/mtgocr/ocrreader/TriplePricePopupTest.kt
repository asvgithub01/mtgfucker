package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic prices only in emulator; diagnostic mode never writes inventory. */
class TriplePricePopupTest {
    @Test fun coldGalleryLoadsPriceAndQuickEditsResetDismissal() {
        check(android.os.Build.MODEL.contains("sdk",true) || android.os.Build.FINGERPRINT.contains("generic"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("triple_scanner",0)
        val modes=listOf("ar","art","ocr").associateWith { prefs.getBoolean(it,true) }
        val uuid="popup-price-test"
        val dao=CardDatabase.get(context).cardDao()
        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
        fun popup(a:TripleScanActivity)=field("entryPopup").get(a) as PopupWindow?
        fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
        dao.savePrices(listOf(CardPriceEntity(uuid,"normal",45.0,"EUR","cardmarket","2026-10-14",1)))
        SelectedPrices.invalidate()
        prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).commit()
        try {
            ActivityScenario.launch<TripleScanActivity>(Intent(context,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)).use { scene ->
                scene.onActivity { a ->
                    val target=TripleScanActivity.Target("popup-test",uuid,"Test","FRA","Test #1","","nonfoil","AR")
                    TripleScanActivity::class.java.getDeclaredMethod("upsertGallery",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(a,target)
                }
                Thread.sleep(1200)
                scene.onActivity { a ->
                    val recent=field("recent").get(a) as LinearLayout
                    val row=recent.getChildAt(0)
                    val price=row.findViewWithTag<TextView>("triple_gallery_price").text.toString()
                    assertTrue(price,price.contains("45") && price.contains("MKM"))
                    row.performClick()
                    val p=requireNotNull(popup(a));assertTrue(p.isShowing)
                    views(p.contentView).filterIsInstance<Button>().first { it.text=="+" }.performClick()
                    assertTrue(p.isShowing)
                }
                Thread.sleep(650)
                scene.onActivity { a ->
                    val p=requireNotNull(popup(a));assertTrue(p.isShowing)
                    views(p.contentView).filterIsInstance<Button>().first { it.text=="−" }.performClick()
                    assertTrue(p.isShowing)
                }
                Thread.sleep(650)
                scene.onActivity { a ->
                    val p=requireNotNull(popup(a));assertTrue(p.isShowing)
                    views(p.contentView).filterIsInstance<CheckBox>().first { it.tag=="triple_edit_foil" }.performClick()
                    assertTrue(p.isShowing)
                }
                Thread.sleep(650)
                scene.onActivity { a -> assertTrue(requireNotNull(popup(a)).isShowing) }
                Thread.sleep(600)
                scene.onActivity { a -> assertNull(popup(a)) }
            }
        } finally {
            dao.deletePricesFor(listOf(uuid));SelectedPrices.invalidate()
            prefs.edit().also { e->modes.forEach { (k,v)->e.putBoolean(k,v) } }.commit()
        }
    }
}
