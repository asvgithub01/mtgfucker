package io.asv.mtgocr.ocrreader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Isolated database only; does not touch the user's catalog, preferences or inventory. */
class PriceHistoryDatabaseTest {
    private inline fun <T> CardDatabase.use(block: (CardDatabase) -> T): T = try { block(this) } finally { close() }
    @Test fun realLibraryTotalOpensHistoryWithoutRefreshingPrices() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        androidx.test.core.app.ActivityScenario.launch<OcrCaptureActivity>(android.content.Intent(context,OcrCaptureActivity::class.java)
            .putExtra(App.INTENT_PERSISTOR_MODE,"0")).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<android.view.View>(R.id.txtTotal).performClick())
            }
            Thread.sleep(1000)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val roots=android.view.inspector.WindowInspector.getGlobalWindowViews()
                val history=roots.mapNotNull { it.findViewById<android.widget.Button>(android.R.id.button3) }
                    .first { it.text.toString()==activity.getString(R.string.price_history_title) }
                assertTrue(history.performClick())
            }
            Thread.sleep(400)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val messages=android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .mapNotNull { it.findViewById<android.widget.TextView>(android.R.id.message)?.text?.toString() }
                assertTrue(messages.any { it.contains(activity.getString(R.string.price_history_group_note)) })
            }
        }
    }

    @Test fun selectedProviderNeverRelabelsLegacyAmount() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "price-test-${UUID.randomUUID()}-"
        val context = object : android.content.ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix+name,mode)
        }
        val card = io.asv.mtgocr.ocrreader.model.CardInfo("test","128 EUR","","","1").apply {
            printingUuid="price-test-uuid"; finish="nonfoil"; priceM="128"
        }
        try {
            PriceSourcePreferences.save(context,PriceSourcePreferences.available)
            assertNull(PriceCurrency.amountOrNull(context,card))
            SelectedPrices.publish(context,card.printingUuid,"nonfoil",CardPriceEntity(card.printingUuid,"normal",45.0,"EUR","cardmarket","2026-10-14",1))
            assertEquals(45.0,PriceCurrency.amountOrNull(context,card)!!,0.001)
            PriceSourcePreferences.save(context,PriceSourcePreferences.available.sortedBy { if (it.id=="scryfall") 0 else 1 })
            assertEquals("scryfall",PriceSourcePreferences.priorityIds(context).first())
            assertTrue(PriceSourcePreferences.priorityIds(context).contains("cardmarket"))
            assertNull(PriceCurrency.amountOrNull(context,card))
            SelectedPrices.publish(context,card.printingUuid,"nonfoil",CardPriceEntity(card.printingUuid,"normal",45.0,"EUR","cardmarket","2026-10-14",1))
            assertEquals(45.0,PriceCurrency.amountOrNull(context,card)!!,0.001)
            assertTrue(PriceSourcePresentation.format(context,card).toString().contains("MKM"))
            SelectedPrices.publish(context,card.printingUuid,"nonfoil",CardPriceEntity(card.printingUuid,"normal",39.0,"EUR","scryfall","2026-10-14",2))
            assertEquals(39.0,PriceCurrency.amountOrNull(context,card)!!,0.001)
        } finally {
            context.getSharedPreferences("price_source_preferences",0).edit().clear().commit()
            SelectedPrices.invalidate()
        }
    }

    @Test fun migrationSixToSevenPreservesExistingPricesAndPersistsHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "price-history-test-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        try {
            Room.databaseBuilder(context,CardDatabase::class.java,name).build().use { db ->
                db.cardDao().savePrices(listOf(CardPriceEntity("uuid","normal",128.0,"EUR","cardmarket","2026-09-27",1)))
            }
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.execSQL("DROP TABLE price_history")
                old.execSQL("DROP TABLE group_price_history")
                old.version = 6
            }
            Room.databaseBuilder(context,CardDatabase::class.java,name)
                .addMigrations(CardDatabase.MIGRATION_6_7).build().use { db ->
                    val dao = db.cardDao()
                    assertEquals(128.0,dao.pricesFor(listOf("uuid")).single().amount,0.0)
                    assertTrue(dao.priceHistory("uuid","nonfoil","cardmarket").isEmpty())
                    val old = PriceHistoryEntity("uuid","nonfoil","cardmarket","2026-09-27",128.0,"EUR",1)
                    val fresh = old.copy(priceDate="2026-10-13",amount=27.39,observedAt=2)
                    dao.savePriceHistory(listOf(old,fresh,old))
                    assertEquals(2,dao.priceHistory("uuid","nonfoil","cardmarket").size)
                    assertEquals(27.39,dao.priceHistory("uuid","nonfoil","cardmarket").first().amount,0.0)
                    assertTrue(dao.priceHistory("uuid","foil","cardmarket").isEmpty())
                    assertTrue(dao.priceHistory("uuid","nonfoil","scryfall").isEmpty())
                    val row=GroupPriceHistoryEntity("snapshot","library","group:test","cardmarket",1,"EUR",27.39,0,"stable")
                    dao.saveGroupPriceHistory(listOf(row))
                }
            Room.databaseBuilder(context,CardDatabase::class.java,name).build().use { db ->
                assertEquals(2,db.cardDao().priceHistory("uuid","nonfoil","cardmarket").size)
                assertEquals(27.39,db.cardDao().groupPriceHistory("library","group:test","cardmarket").single().total,0.0)
                assertTrue(db.cardDao().groupPriceHistory("other","group:test","cardmarket").isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
