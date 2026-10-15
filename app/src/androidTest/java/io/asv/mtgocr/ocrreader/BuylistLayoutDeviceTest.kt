package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.widget.CheckBox
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Uses an existing list only. Selection is UI-only and never edits the list. */
class BuylistLayoutDeviceTest {
    @Test fun fullScreenHeaderAndSelectAllToggle() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(PremiumAccess.isEnabled(c))
        val list=BuylistStore.read(c).firstOrNull { it.entries.isNotEmpty() }
        org.junit.Assume.assumeNotNull(list)
        ActivityScenario.launch<BuylistActivity>(Intent(c,BuylistActivity::class.java).putExtra("listId",list!!.id)).use { scenario ->
            Thread.sleep(800)
            scenario.onActivity { a ->
                assertEquals(-1,a.window.attributes.width);assertEquals(-1,a.window.attributes.height)
                val attrs=a.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating));assertFalse(attrs.getBoolean(0,true));attrs.recycle()
                val total=a.findViewById<View>(R.id.buylistTotal)
                assertTrue(total.paddingLeft>0)
                val close=a.findViewById<View>(R.id.btnClosePopup)
                assertSame(total.parent,close.parent.parent)
                val select=a.findViewById<CheckBox>(R.id.buylistSelectAll)
                assertFalse(select.isChecked);select.performClick()
                assertTrue(a.findViewById<CheckBox>(R.id.buylistSelectAll).isChecked)
                assertTrue(a.findViewById<View>(R.id.buylistDeleteSelected).isEnabled)
                a.findViewById<CheckBox>(R.id.buylistSelectAll).performClick()
                assertFalse(a.findViewById<CheckBox>(R.id.buylistSelectAll).isChecked)
                assertFalse(a.findViewById<View>(R.id.buylistDeleteSelected).isEnabled)
            }
        }
        assertEquals(list,BuylistStore.read(c).first { it.id==list.id })
    }
}
