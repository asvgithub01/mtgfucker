package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test

class ShoppingPhotosNavigationTest {
    @Test fun photosAreInsideShoppingAndBackReturnsThere() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val photos=PhotoScanStore.entries(context).map { it.toString() }
        ActivityScenario.launch<OcrCaptureActivity>(Intent(context,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scene ->
            Thread.sleep(800)
            scene.onActivity { activity ->
                val storage=activity.findViewById<View>(R.id.btnStorageSettings).parent as ArcaneGlassLayout
                val price=activity.findViewById<View>(R.id.priceSourcesRecycler).parent as ArcaneGlassLayout
                val settings=price.parent as android.view.ViewGroup
                assertSame(settings,storage.parent)
                assertEquals(settings.indexOfChild(price)+1,settings.indexOfChild(storage))
                val back=activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPhotosBack)
                val textColor=CollectionPopupChrome.color(activity,R.attr.appGlassTextColor)
                assertEquals(textColor,back.currentTextColor);assertEquals(textColor,back.iconTint!!.defaultColor)
                assertEquals(activity.getString(R.string.photo_header),back.text.toString())
                val nav=activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                assertEquals(5,nav.menu.size())
                nav.selectedItemId=R.id.nav_shopping
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.shoppingSection).visibility)
                activity.findViewById<View>(R.id.btnShoppingPhotos).performClick()
                assertEquals(R.id.nav_shopping,nav.selectedItemId)
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.photoControls).visibility)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.shoppingSection).visibility)
                activity.findViewById<View>(R.id.btnShoppingPhotos).performClick()
                activity.findViewById<View>(R.id.btnPhotosBack).performClick()
                assertEquals(View.GONE,activity.findViewById<View>(R.id.photoControls).visibility)
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.shoppingSection).visibility)
            }
        }
        assertEquals(photos,PhotoScanStore.entries(context).map { it.toString() })
    }
}
