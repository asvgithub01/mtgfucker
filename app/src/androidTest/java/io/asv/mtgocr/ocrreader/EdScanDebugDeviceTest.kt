package io.asv.mtgocr.ocrreader

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import io.asv.collectorvision.*
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

class EdScanDebugDeviceTest {
    @Test fun traceKeepsAcceptanceAndStoredIdentitySeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = JSONObject().put("mode", "fast").put("minimumScore", .5)
        val key = EdScanDebug.accepted(context, data)
        data.put("mode", "changed_after_record")
        EdScanDebug.record(context, key, "outcome", JSONObject().put("saved", false))
        val dir = File(context.filesDir, "edscan/debug")
        val accepted = File(dir, "$key.accepted.json")
        val outcome = File(dir, "$key.outcome.json")
        val until = android.os.SystemClock.elapsedRealtime()+5000
        while (!outcome.exists() && android.os.SystemClock.elapsedRealtime()<until) Thread.sleep(50)
        try {
            assertEquals("fast", JSONObject(accepted.readText()).getString("mode"))
            assertFalse(JSONObject(outcome.readText()).getBoolean("saved"))
        } finally { accepted.delete(); outcome.delete() }
    }
    @Test fun artworkOneColumnShowsTwoAndTwoColumnsShowsSixWithPrices() {
        for ((columns, rows) in listOf(1 to 2, 2 to 3)) {
            ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
                lateinit var list: RecyclerView
                scenario.onActivity { activity ->
                    val themed = android.view.ContextThemeWrapper(activity, R.style.Theme_Mtg)
                    list = RecyclerView(themed)
                    list.layoutManager = FourCardLayoutManager(themed, columns, rows)
                    list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 18
                        override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) =
                            object : RecyclerView.ViewHolder(android.view.LayoutInflater.from(parent.context).inflate(R.layout.card_art_item, parent, false)) {}
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            holder.itemView.findViewById<android.widget.TextView>(R.id.txtName).text = "Test card"
                            holder.itemView.findViewById<android.widget.TextView>(R.id.txtPrice).text = "14,50 €"
                        }
                    }
                    activity.setContentView(list)
                }
                Thread.sleep(500)
                scenario.onActivity {
                    assertEquals(columns*rows, list.childCount)
                    for (i in 0 until list.childCount) {
                        val cell = list.getChildAt(i)
                        assertEquals(list.width/columns, cell.width)
                        assertEquals(list.height/rows, cell.height)
                        val price = cell.findViewById<android.widget.TextView>(R.id.txtPrice)
                        val rect = android.graphics.Rect()
                        assertTrue(price.getGlobalVisibleRect(rect))
                        assertEquals(price.height, rect.height())
                    }
                    list.scrollBy(list.width, 0)
                    assertEquals(columns*rows, (list.layoutManager as FourCardLayoutManager).findFirstVisibleItemPosition())
                }
            }
        }
    }
    @Test fun inlineDeleteSurvivesSameCardRebindAndSitsAboveName() {
        ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
            lateinit var cell: android.view.View
            lateinit var button: android.widget.ImageButton
            var deleted = 0
            scenario.onActivity { activity ->
                val context = android.view.ContextThemeWrapper(activity, R.style.Theme_Mtg)
                cell = android.view.LayoutInflater.from(context).inflate(R.layout.card_grid_item, null)
                activity.setContentView(cell)
                HoldToDelete.bind(cell, "stable-id", Runnable { deleted++ })
                button = cell.findViewById(R.id.btnDeleteCard)
            }
            Thread.sleep(300)
            scenario.onActivity {
                val buttonPos = IntArray(2); val namePos = IntArray(2)
                button.getLocationOnScreen(buttonPos)
                cell.findViewById<android.view.View>(R.id.txtName).getLocationOnScreen(namePos)
                assertTrue(buttonPos[1]+button.height <= namePos[1])
                val now = android.os.SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,10f,10f,0)
                button.dispatchTouchEvent(down); down.recycle()
            }
            Thread.sleep(550)
            scenario.onActivity { HoldToDelete.bind(cell, "stable-id", Runnable { deleted++ }) }
            Thread.sleep(650)
            scenario.onActivity { assertEquals(1, deleted) }
        }
    }
    @Test fun celebrationOnlyOnceAndRemovedWhenActivityStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("valuable_scan_celebrations", 0)
        val old = prefs.getString("recent", null)
        try {
            ActivityScenario.launch(NativeCollectorVisionActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val key = "test:" + java.util.UUID.randomUUID()
                    assertFalse(PriceCelebration.show(activity, key, 10.0))
                    assertTrue(PriceCelebration.show(activity, key, 10.01))
                    assertFalse(PriceCelebration.show(activity, key, 10.01))
                    assertNotNull(activity.window.decorView.findViewWithTag<android.view.View>("price_confetti"))
                }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                scenario.onActivity { activity -> assertNull(activity.window.decorView.findViewWithTag<android.view.View>("price_confetti")) }
            }
        } finally { prefs.edit().putString("recent", old).commit() }
    }
}
