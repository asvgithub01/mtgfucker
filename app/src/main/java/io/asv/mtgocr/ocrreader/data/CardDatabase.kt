package io.asv.mtgocr.ocrreader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "card_printings", primaryKeys = ["uuid"])
data class CardPrintingEntity(
    val uuid: String,
    val normalizedName: String,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val releaseDate: String,
    val rarity: String,
    val scryfallId: String?,
    val finishes: String,
    val typeLine: String,
    val rulesText: String,
    val imageUrl: String?,
    val updatedAt: Long
)

@Entity(tableName = "card_prices", primaryKeys = ["printingUuid", "finish"])
data class CardPriceEntity(
    val printingUuid: String,
    val finish: String,
    val amount: Double,
    val currency: String,
    val provider: String,
    val priceDate: String,
    val updatedAt: Long
)

@Entity(tableName = "card_set_sync", primaryKeys = ["normalizedName", "setCode"])
data class CardSetSyncEntity(
    val normalizedName: String,
    val setCode: String,
    val updatedAt: Long
)

@Entity(tableName = "owned_printings", primaryKeys = ["collectionItemId"])
data class OwnedPrintingEntity(
    val collectionItemId: String,
    val cardName: String,
    val printingUuid: String,
    val finish: String,
    val selectedAt: Long
)

@Entity(tableName = "card_name_aliases", primaryKeys = ["normalizedAlias"])
data class CardNameAliasEntity(
    val normalizedAlias: String,
    val canonicalName: String,
    val displayName: String,
    val language: String,
    val updatedAt: Long
)

@Entity(tableName = "magic_sets", primaryKeys = ["code"])
data class MagicSetEntity(
    val code: String,
    val name: String,
    val releaseDate: String,
    val type: String,
    val cardCount: Int,
    val updatedAt: Long
)

@Dao
interface CardDao {
    @Query("SELECT * FROM card_printings WHERE normalizedName = :name ORDER BY releaseDate DESC, setCode, collectorNumber")
    fun printingsByName(name: String): List<CardPrintingEntity>

    @Query("SELECT * FROM card_printings WHERE setCode = :setCode ORDER BY CAST(collectorNumber AS INTEGER), collectorNumber, name")
    fun printingsBySet(setCode: String): List<CardPrintingEntity>

    @Query("SELECT * FROM card_prices WHERE printingUuid IN (:uuids)")
    fun pricesFor(uuids: List<String>): List<CardPriceEntity>

    @Query("SELECT * FROM card_set_sync WHERE normalizedName = :name")
    fun syncedSets(name: String): List<CardSetSyncEntity>

    @Query("SELECT * FROM card_set_sync WHERE normalizedName = :name AND setCode = '*' LIMIT 1")
    fun cardDiscoverySync(name: String): CardSetSyncEntity?

    @Query("SELECT * FROM card_set_sync WHERE normalizedName = '*' AND setCode = :setCode LIMIT 1")
    fun fullSetSync(setCode: String): CardSetSyncEntity?

    @Query("SELECT * FROM owned_printings WHERE collectionItemId = :collectionItemId LIMIT 1")
    fun ownedPrinting(collectionItemId: String): OwnedPrintingEntity?

    @Query("SELECT * FROM owned_printings")
    fun ownedPrintings(): List<OwnedPrintingEntity>

    @Query("SELECT * FROM card_name_aliases WHERE normalizedAlias = :alias LIMIT 1")
    fun cardNameAlias(alias: String): CardNameAliasEntity?

    @Query("""SELECT * FROM card_name_aliases
        WHERE normalizedAlias >= :prefix AND normalizedAlias < :upperBound
        ORDER BY CASE WHEN normalizedAlias = :prefix THEN 0 ELSE 1 END,
                 LENGTH(normalizedAlias), normalizedAlias
        LIMIT :limit""")
    fun cardNameAliasesByPrefix(
        prefix: String,
        upperBound: String,
        limit: Int
    ): List<CardNameAliasEntity>

    @Query("""SELECT DISTINCT name FROM card_printings
        WHERE normalizedName >= :prefix AND normalizedName < :upperBound
        ORDER BY normalizedName
        LIMIT :limit""")
    fun printingNamesByPrefix(prefix: String, upperBound: String, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM card_name_aliases")
    fun cardNameAliasCount(): Int

    @Query("SELECT * FROM card_name_aliases")
    fun allCardNameAliases(): List<CardNameAliasEntity>

    @Query("SELECT * FROM magic_sets ORDER BY releaseDate, name")
    fun magicSets(): List<MagicSetEntity>

    @Query("SELECT * FROM card_set_sync WHERE normalizedName = '__catalog__' AND setCode = '*' LIMIT 1")
    fun magicSetCatalogSync(): CardSetSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePrintings(printings: List<CardPrintingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePrices(prices: List<CardPriceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSetSync(sync: CardSetSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveOwnedPrinting(owned: OwnedPrintingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCardNameAlias(alias: CardNameAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCardNameAliases(aliases: List<CardNameAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveMagicSets(sets: List<MagicSetEntity>)
}

@Database(
    entities = [
        CardPrintingEntity::class,
        CardPriceEntity::class,
        CardSetSyncEntity::class,
        OwnedPrintingEntity::class,
        CardNameAliasEntity::class,
        MagicSetEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile private var instance: CardDatabase? = null

        fun get(context: Context): CardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CardDatabase::class.java,
                "mtg_catalog.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `card_name_aliases` (
                        `normalizedAlias` TEXT NOT NULL,
                        `canonicalName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`normalizedAlias`)
                    )""".trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `magic_sets` (
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `releaseDate` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `cardCount` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`code`)
                    )""".trimIndent()
                )
            }
        }
    }
}
