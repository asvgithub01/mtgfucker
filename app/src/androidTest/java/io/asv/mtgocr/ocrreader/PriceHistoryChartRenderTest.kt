package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.PriceHistoryEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic chart rendering only. Never inserts sample prices into the catalog/history. */
class PriceHistoryChartRenderTest {
    @Test fun rendersDownUpAndSinglePointWithoutMixingProviders() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scenario ->
            scenario.onActivity { activity ->
                fun render(name:String, amounts:List<Double>, expectedArrow:String?) {
                    val view=PriceHistorySection(activity)
                    val rows=amounts.mapIndexed { i, value -> PriceHistoryEntity("synthetic","nonfoil","cardmarket",
                        "2026-10-${(i+1).toString().padStart(2,'0')}",value,"EUR",i.toLong()) }
                    view.render("synthetic","nonfoil",null,rows,listOf("cardmarket","scryfall"))
                    val width=(340*activity.resources.displayMetrics.density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    view.layout(0,0,width,view.measuredHeight)
                    fun labels(root:View):List<TextView> = (if(root is TextView)listOf(root) else emptyList())+
                        if(root is ViewGroup)(0 until root.childCount).flatMap { labels(root.getChildAt(it)) } else emptyList()
                    val text=labels(view)
                    if(expectedArrow!=null) {
                        val trend=text.single { it.text.contains(expectedArrow) }
                        assertTrue(trend.text.contains("%"))
                        assertEquals(if(expectedArrow=="↑") Color.rgb(98,220,146) else Color.rgb(255,119,129),trend.currentTextColor)
                    } else assertTrue(text.any { it.text==activity.getString(R.string.price_history_need_two) })
                    assertTrue(view.measuredHeight>0)
                    val bitmap=Bitmap.createBitmap(width,view.measuredHeight,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(activity.cacheDir,"price-chart-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
                render("down",listOf(32.0,31.0,28.0,26.0,25.0,24.5,22.0,21.0,21.2,20.5),"↓")
                render("up",listOf(20.0,22.0),"↑")
                render("one",listOf(20.0),null)
            }
        }
    }
}
