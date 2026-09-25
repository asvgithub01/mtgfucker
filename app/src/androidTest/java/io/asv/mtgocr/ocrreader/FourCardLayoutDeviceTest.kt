package io.asv.mtgocr.ocrreader

import androidx.test.core.app.ActivityScenario
import androidx.recyclerview.widget.RecyclerView
import org.junit.Test
import org.junit.Assert.*

class FourCardLayoutDeviceTest {
    @Test fun fourCellsFitViewportAndScrollHorizontally() {
        ActivityScenario.launch(io.asv.collectorvision.NativeCollectorVisionActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            scenario.onActivity { activity ->
                list = RecyclerView(android.view.ContextThemeWrapper(activity, R.style.Theme_Mtg))
                list.layoutManager = FourCardLayoutManager(activity)
                list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = 12
                    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) =
                        object : RecyclerView.ViewHolder(android.view.LayoutInflater.from(parent.context)
                            .inflate(if (type == 0) R.layout.card_grid_item else R.layout.set_card_grid_item, parent, false)) {}
                    override fun getItemViewType(position: Int) = position % 2
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                }
                activity.setContentView(list)
            }
            Thread.sleep(600)
            scenario.onActivity {
                val manager = list.layoutManager!!
                assertTrue(manager.canScrollHorizontally())
                assertFalse(manager.canScrollVertically())
                assertEquals(4, list.childCount)
                for (i in 0 until 4) {
                    val child = list.getChildAt(i)
                    assertEquals(list.width / 2, child.width)
                    assertEquals(list.height / 2, child.height)
                }
                list.scrollBy(list.width, 0)
                assertEquals(4, (manager as FourCardLayoutManager).findFirstVisibleItemPosition())
            }
        }
    }
}

