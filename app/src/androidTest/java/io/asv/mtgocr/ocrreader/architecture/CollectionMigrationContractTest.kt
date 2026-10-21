package io.asv.mtgocr.ocrreader.architecture

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardDatabase
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Synthetic historical schemas only. No singleton, production database or user data. */
class CollectionMigrationContractTest {
    @Test fun everyHistoricalVersionPreservesOwnedPrintingAndPriceThroughVersionEight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val migrations = (1..7).map { from ->
            CardDatabase::class.java.getDeclaredField("MIGRATION_${from}_${from + 1}")
                .apply { isAccessible = true }.get(null) as Migration
        }
        for (version in 1..7) {
            val name = "compose-migration-${UUID.randomUUID()}.db"
            try {
                val helper = FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE card_printings (uuid TEXT NOT NULL PRIMARY KEY, normalizedName TEXT NOT NULL, name TEXT NOT NULL, setCode TEXT NOT NULL, setName TEXT NOT NULL, collectorNumber TEXT NOT NULL, releaseDate TEXT NOT NULL, rarity TEXT NOT NULL, scryfallId TEXT, finishes TEXT NOT NULL, typeLine TEXT NOT NULL, rulesText TEXT NOT NULL, imageUrl TEXT, updatedAt INTEGER NOT NULL)")
                                db.execSQL("CREATE TABLE card_prices (printingUuid TEXT NOT NULL, finish TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, provider TEXT NOT NULL, priceDate TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(printingUuid,finish))")
                                db.execSQL("CREATE TABLE card_set_sync (normalizedName TEXT NOT NULL, setCode TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(normalizedName,setCode))")
                                db.execSQL("CREATE TABLE owned_printings (collectionItemId TEXT NOT NULL PRIMARY KEY, cardName TEXT NOT NULL, printingUuid TEXT NOT NULL, finish TEXT NOT NULL, selectedAt INTEGER NOT NULL)")
                                db.execSQL("INSERT INTO card_printings VALUES ('fixture-printing','test','Test','SET','Set','1','2020-01-01','rare',NULL,'foil','type','rules',NULL,1)")
                                db.execSQL("INSERT INTO card_prices VALUES ('fixture-printing','foil',12.5,'EUR','cardmarket','2020-01-01',1)")
                                db.execSQL("INSERT INTO owned_printings VALUES ('fixture-row','Test','fixture-printing','foil',1)")
                                migrations.take(version - 1).forEach { it.migrate(db) }
                            }
                            override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = error("unexpected")
                        }).build())
                helper.writableDatabase
                helper.close()
                val db = Room.databaseBuilder(context, CardDatabase::class.java, name)
                    .addMigrations(*migrations.toTypedArray()).build()
                try {
                    assertEquals(8, db.openHelper.writableDatabase.version)
                    val owned = requireNotNull(db.cardDao().ownedPrinting("fixture-row"))
                    assertEquals("fixture-printing", owned.printingUuid)
                    assertEquals("foil", owned.finish)
                    assertEquals(12.5, db.cardDao().pricesFor(listOf("fixture-printing")).single().amount, 0.0)
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
    }
}
