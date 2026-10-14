package io.asv.mtgocr.ocrreader
import android.content.Intent
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class DetailSheetRegressionTest {
 @Test fun searchUsesSharedArtworkBackground() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  ActivityScenario.launch<CardSearchActivity>(Intent(c,CardSearchActivity::class.java)).use { scene ->
   scene.onActivity { a -> assertTrue(a.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) is CardArtBackgroundView) }
  }
 }
 @Test fun missingSelectionOffersBothDestinations() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  ActivityScenario.launch<SetCollectionActivity>(Intent(c,SetCollectionActivity::class.java).putExtra(SetCollectionActivity.EXTRA_SET_CODE,"ZZZ").putExtra(SetCollectionActivity.EXTRA_SET_NAME,"Test")).use { scene ->
   scene.onActivity { a ->
    val adapter=SetCollectionActivity::class.java.getDeclaredField("adapter").apply { isAccessible=true }.get(a)
    val option=io.asv.mtgocr.ocrreader.data.SetCardOption("test-missing","Test","ZZZ","Test","1","nonfoil",null,"","",null,null)
    adapter.javaClass.getDeclaredMethod("submit",List::class.java,Map::class.java).apply { isAccessible=true }.invoke(adapter,listOf(option),emptyMap<String,io.asv.mtgocr.ocrreader.model.CardInfo>())
    val check=a.findViewById<android.widget.CheckBox>(R.id.checkSelectAllSetCards)
    assertEquals(a.getString(R.string.collection_select_missing),check.text)
    check.performClick()
    val add=a.findViewById<android.widget.Button>(R.id.btnAddSetCards);assertTrue(add.isEnabled);add.performClick()
   }
   Thread.sleep(300)
   scene.onActivity { a ->
    fun texts(v:View):List<String> = (if(v is android.widget.TextView)listOf(v.text.toString()) else emptyList())+(if(v is android.view.ViewGroup)(0 until v.childCount).flatMap { texts(v.getChildAt(it)) }else emptyList())
    val labels=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::texts)
    assertTrue(labels.contains(a.getString(R.string.collection_add_biblio)))
    assertTrue(labels.contains(a.getString(R.string.collection_add_buylist)))
   }
  }
 }
 @Test fun editionsRemainAboveHistoryAndWithinScreen() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  ActivityScenario.launch<Main2Activity>(Intent(c,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,"Lightning Bolt").putExtra("browseOnly",true)).use { scene ->
   Thread.sleep(3000)
   fun capture(name:String) { scene.onActivity { a ->
    val v=a.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File(a.cacheDir,name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
   } }
   capture("detail-collapsed.png")
   scene.onActivity { a -> BottomSheetBehavior.from(a.findViewById<View>(R.id.editionsBottomSheet)).state=BottomSheetBehavior.STATE_EXPANDED }
   Thread.sleep(1000);capture("detail-expanded.png")
   var originalHeight=0
   scene.onActivity { a ->
    val root=a.findViewById<View>(R.id.cardDetailRoot);originalHeight=root.height
    root.layoutParams=root.layoutParams.apply { height=originalHeight-300 }
   }
   Thread.sleep(400)
   scene.onActivity { a ->
    val sheet=a.findViewById<View>(R.id.editionsBottomSheet);val root=a.findViewById<View>(R.id.cardDetailRoot)
    assertEquals(root.height-(188*a.resources.displayMetrics.density).toInt(),sheet.height)
    root.layoutParams=root.layoutParams.apply { height=originalHeight }
   }
   Thread.sleep(400)

   scene.onActivity { a ->
    val sheet=a.findViewById<View>(R.id.editionsBottomSheet);val root=a.findViewById<View>(R.id.cardDetailRoot)
    assertTrue("sheet bottom=${sheet.bottom}, root=${root.height}",sheet.bottom<=root.height)
    assertTrue(sheet.height>root.height/2)
   }
  }
 }
}
