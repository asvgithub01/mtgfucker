package io.asv.mtgocr.ocrreader

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardRepository
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only host checks: never insert test cards into the user's library. */
class CorneliusHostDeviceTest {
    @Test fun hostStartsWithoutCreatingAGroupOrGuessingLanguage() {
        ActivityScenario.launch<CorneliusScanActivity>(android.content.Intent(InstrumentationRegistry.getInstrumentation().targetContext, CorneliusScanActivity::class.java).putExtra("edscan_autostart", false)).use { scenario ->
            scenario.onActivity { activity ->
                val language = CorneliusScanActivity::class.java.getDeclaredField("language").apply { isAccessible = true }.get(activity) as android.widget.Spinner
                assertEquals(0, language.selectedItemPosition)
                val group = CorneliusScanActivity::class.java.getDeclaredField("group").apply { isAccessible = true }.get(activity)
                assertNull(group)
                activity.finish()
            }
        }
    }
    @Test fun safetyToggleSurvivesRecreationAndReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("cornelius_scanner", 0)
        val existed = prefs.contains("safety_filters")
        val original = prefs.getBoolean("safety_filters", true)
        try {
            prefs.edit().remove("safety_filters").commit()
            ActivityScenario.launch<CorneliusScanActivity>(android.content.Intent(InstrumentationRegistry.getInstrumentation().targetContext, CorneliusScanActivity::class.java).putExtra("edscan_autostart", false)).use { scenario ->
                scenario.onActivity { activity ->
                    val check = activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_safety_filters")
                    assertTrue(check.isChecked)
                    check.performClick()
                    assertFalse(check.isChecked)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse(activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_safety_filters").isChecked)
                }
            }
            ActivityScenario.launch<CorneliusScanActivity>(android.content.Intent(InstrumentationRegistry.getInstrumentation().targetContext, CorneliusScanActivity::class.java).putExtra("edscan_autostart", false)).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_safety_filters").isChecked)
                }
            }
        } finally {
            val edit = prefs.edit()
            if (existed) edit.putBoolean("safety_filters", original) else edit.remove("safety_filters")
            edit.commit()
        }
    }
    @Test fun thumbnailFlagPersistsAndPriceUsesExactFinish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("cornelius_scanner", 0)
        val existed = prefs.contains("show_thumbnail")
        val original = prefs.getBoolean("show_thumbnail", true)
        try {
            prefs.edit().putBoolean("show_thumbnail", true).commit()
            ActivityScenario.launch<CorneliusScanActivity>(android.content.Intent(InstrumentationRegistry.getInstrumentation().targetContext, CorneliusScanActivity::class.java).putExtra("edscan_autostart", false)).use { scenario ->
                scenario.onActivity { activity ->
                    val cls = io.asv.collectorvision.NativeCollectorVisionActivity::class.java
                    fun field(name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
                    val check = activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_show_thumbnail")
                    check.performClick()
                    assertFalse(field("showThumbnail").getBoolean(activity))
                    assertEquals(android.view.View.GONE, (field("frameView").get(activity) as android.view.View).visibility)
                    field("priceCardId").set(activity, "test")
                    field("recognizedCurrency").set(activity, "eur")
                    field("recognizedFinish").set(activity, "foil")
                    @Suppress("UNCHECKED_CAST")
                    val cache = field("priceCache").get(activity) as MutableMap<String, org.json.JSONObject>
                    cache["test"] = org.json.JSONObject("{\"eur\":\"1.23\",\"eur_foil\":\"9.87\"}")
                    val render = cls.getDeclaredMethod("renderRecognizedPrice").apply { isAccessible = true }
                    render.invoke(activity)
                    val price = activity.window.decorView.findViewWithTag<android.widget.TextView>("cornelius_price")
                    assertTrue(price.text.toString().replace(',', '.').contains("9.87"))
                    field("recognizedFinish").set(activity, "etched")
                    render.invoke(activity)
                    assertFalse(price.text.toString().contains("9.87"))
                    assertFalse(price.text.toString().contains("1.23"))
                    assertEquals(android.view.View.VISIBLE, price.visibility)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse(activity.window.decorView.findViewWithTag<android.widget.CheckBox>("cornelius_show_thumbnail").isChecked)
                }
            }
        } finally {
            val edit = prefs.edit()
            if (existed) edit.putBoolean("show_thumbnail", original) else edit.remove("show_thumbnail")
            edit.commit()
        }
    }
    @Test fun visualUuidResolvesExactLocalPrintingWithoutInsertingCopies() {
        val latch = CountDownLatch(1)
        var result: List<io.asv.mtgocr.ocrreader.data.CardEditionOption> = emptyList()
        var failure: Throwable? = null
        CardRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
            .loadCollectorVisionPrinting("3f42c4d7-b555-449c-a539-119c1ae62232") { options, error ->
                result = options; failure = error; latch.countDown()
            }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        assertNull(failure)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.cardName == "Titanic Bulvox" && it.setCode.equals("scg", true) && it.collectorNumber == "129" })
        assertEquals(1, result.map { it.printingUuid }.distinct().size)
    }
}
