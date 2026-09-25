package io.asv.mtgocr.ocrreader

import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.MtgJsonCardNameResolver
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RulesTitleDictionaryDeviceTest {
    @Test fun replayWhiteCardTitlesAgainstFullLocalDictionary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = MtgJsonCardNameResolver(context, CardDatabase.get(context).cardDao(), OkHttpClient())
        val dir = File(context.noBackupFilesDir, "rules_replay/title_v9")
        val files = checkNotNull(dir.listFiles()).filter { it.extension == "json" }.sortedBy { it.name }
        assertEquals(30, files.size)
        val report = StringBuilder()
        var recovered = 0
        for (file in files) {
            val json = JSONObject(file.readText()); val array = json.getJSONArray("rawTitle")
            val raw = (0 until array.length()).map { array.getString(it) }
            val fast = resolver.resolveLocalOcrLines(raw)
            val start = android.os.SystemClock.elapsedRealtime()
            val full = if (fast.isEmpty()) resolver.resolveLocalTitleLines(raw) else fast
            val names = full.map { it.second.canonicalName }.distinct()
            if (fast.isEmpty() && full.isNotEmpty()) recovered++
            report.appendLine("${file.name}: old=${fast.map { it.second.canonicalName }.distinct()} new=$names ms=${android.os.SystemClock.elapsedRealtime()-start}")
            File(dir.parentFile, "title_v9_report.txt").writeText(report.toString())
            if (file.name.startsWith("1790257653400")) assertEquals(listOf("Farrelite Priest"), names)
            if (json.getJSONArray("matchedNames").length() > 0) assertEquals(fast.map { it.second.canonicalName }.distinct(), names)
        }
        assertTrue(recovered > 0)
        assertTrue(resolver.resolveLocalTitleLines(listOf("zxqvzxqvzxqv")).isEmpty())
    }
}
