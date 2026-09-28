package io.asv.mtgocr.ocrreader

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class OcrReleaseNamesDeviceTest {
    @Test fun resolvesNewCommanderNameOfflineWithOldAliasesPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CardDatabase::class.java).build()
        try {
            val dao = db.cardDao()
            val old = "Omnath, Locus of the Roil"
            dao.saveCardNameAlias(CardNameAliasEntity(MtgJsonParsers.normalizeSearchName(old), old, old, "English", 1L))
            val offline = OkHttpClient.Builder().addInterceptor { error("Network forbidden for local OCR") }.build()
            val resolver = MtgJsonCardNameResolver(context, dao, offline)
            val expected = "Omnath, Locus of the Void"
            assertEquals(expected, resolver.resolveLocalOcrCandidates(listOf(expected))?.canonicalName)
            assertEquals(expected, resolver.resolveLocalOcrCandidates(listOf("Omnath, Locus of the V0id"))?.canonicalName)
            assertTrue(resolver.suggestions("Omnath", 20).any { it.canonicalName == expected })
            assertEquals(old, resolver.cached(old)?.canonicalName)
        } finally { db.close() }
    }
}
