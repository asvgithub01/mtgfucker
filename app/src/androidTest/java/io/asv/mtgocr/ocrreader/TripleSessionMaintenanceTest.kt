package io.asv.mtgocr.ocrreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardDatabase
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.security.MessageDigest

/** Explicit opt-in maintenance only. Never deletes anything in an ordinary test run. */
@RunWith(AndroidJUnit4::class)
class TripleSessionMaintenanceTest {
    @Test fun inspectOrRemoveExactSession() {
        val args=InstrumentationRegistry.getArguments()
        val mode=args.getString("tripleMaintenance")
        assumeTrue(mode in listOf("inspect","remove","verifyResume","resumeNext"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val library=LibraryCatalog.activeFile(context)
        val before=requireNotNull(DataUtils.readSerializable<Biblio>(context,library))
        val report=JSONObject().put("library",library).put("rows",before.cards.size)
        report.put("groups",JSONArray().apply {
            before.cards.flatMap { it.groups }.filter { it.startsWith("Triple ") }.distinct().sorted().forEach { group ->
                put(JSONObject().put("group",group).put("cards",JSONArray().apply {
                    before.cards.filter { group in it.groups }.forEach { card ->
                        put(JSONObject().put("id",card.collectionItemId).put("printing",card.printingUuid)
                            .put("quantity",card.quantityCount).put("addedAt",card.addedAt))
                    }
                }))
            }
        })
        if(mode=="verifyResume" || mode=="resumeNext") {
            check(args.getString("library")==library)
            val group=requireNotNull(args.getString("group"))
            val expected=requireNotNull(args.getString("ids")).split(',').toSet()
            val selected=before.cards.filter { group in it.groups }
            check(group.startsWith("Triple ") && selected.isNotEmpty() && selected.map { it.collectionItemId }.toSet()==expected)
            if(mode=="resumeNext") {
                check(context.getSharedPreferences("triple_resume_once",0).edit().putString("library",library).putString("group",group).commit())
            } else {
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                val files=LibraryCatalog.libraries(context).map { File(context.filesDir,it.fileName) }.filter { it.isFile }
                fun hashes()=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
                val originals=hashes()
                val prefs=context.getSharedPreferences("triple_scanner",0)
                val switches=listOf("ar","art","ocr").associateWith { prefs.getBoolean(it,true) }
                prefs.edit().putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).commit()
                try {
                    val intent=android.content.Intent(context,TripleScanActivity::class.java)
                        .putExtra("triple_resume_library",library).putExtra("triple_resume_group",group)
                    androidx.test.core.app.ActivityScenario.launch<TripleScanActivity>(intent).use { scenario ->
                        var restored=false
                        val until=android.os.SystemClock.elapsedRealtime()+60000
                        while(!restored && android.os.SystemClock.elapsedRealtime()<until) {
                            scenario.onActivity { activity ->
                                restored=!(TripleScanActivity::class.java.getDeclaredField("restoringSession").apply { isAccessible=true }.getBoolean(activity))
                            }
                            if(!restored) Thread.sleep(100)
                        }
                        check(restored)
                        scenario.onActivity { activity ->
                            val actual=TripleScanActivity::class.java.getDeclaredField("addedCollectionIds").apply { isAccessible=true }.get(activity) as Set<*>
                            check(actual==expected)
                            val totals=TripleScanActivity::class.java.getDeclaredField("totals").apply { isAccessible=true }.get(activity) as TripleSessionTotals
                            val value=totals.total { amount,currency -> io.asv.mtgocr.ocrreader.data.PriceCurrency.convert(context,amount,currency) }
                            val expectedTotal=selected.sumOf { io.asv.mtgocr.ocrreader.data.PriceCurrency.amount(context,it)*it.quantityCount }
                            check(kotlin.math.abs(value-expectedTotal)<.0001)
                            report.put("restoredRows",actual.size).put("restoredTotal",value)
                        }
                        instrumentation.waitForIdleSync();Thread.sleep(600)
                        val image=instrumentation.uiAutomation.takeScreenshot()
                        File(context.cacheDir,"triple-resume-total-proof.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
                    }
                } finally {
                    val editor=prefs.edit();switches.forEach { (key,value)->editor.putBoolean(key,value) };editor.commit()
                    check(hashes()==originals) { "Resume must not write any collection" }
                }
            }
        }
        if(mode=="remove") {
            check(args.getString("library")==library)
            val group=requireNotNull(args.getString("group"))
            check(group.startsWith("Triple "))
            val expected=requireNotNull(args.getString("ids")).split(',').toSet()
            val selected=before.cards.filter { group in it.groups }
            check(selected.isNotEmpty() && selected.map { it.collectionItemId }.toSet()==expected)
            check(selected.all { it.groups==listOf(group) })
            fun hash(card:CardInfo):String {
                val bytes=ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(card) } }.toByteArray()
                return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            val untouched=before.cards.filter { it.collectionItemId !in expected }.associate { it.collectionItemId to hash(it) }
            selected.forEach { card ->
                check(LegacyCollectionStore.editRecentQuantity(context,library,card.collectionItemId,0,true,card))
                CardDatabase.get(context).cardDao().deleteOwnedPrinting(card.collectionItemId)
            }
            val after=requireNotNull(DataUtils.readSerializable<Biblio>(context,library))
            check(after.cards.associate { it.collectionItemId to hash(it) }==untouched)
            // Archive only linked evidence of these exact rows; never match by name or date alone.
            val dir=io.asv.collectorvision.EdScanJobs.directory(context)
            val links=dir.listFiles().orEmpty().filter { it.name.endsWith(".link.json") }.filter {
                val link=ScanReviewStore.json(it)
                link?.optString("library")==library && link.optString("collectionItemId") in expected
            }
            val archive=File(context.filesDir,"triple-reset-recovery").apply { mkdirs() }
            links.forEach { link ->
                val prefix=link.name.removeSuffix(".link.json")+"."
                dir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith(prefix) }.forEach { file ->
                    check(file.renameTo(File(archive,file.name)))
                }
            }
            report.put("removed",JSONArray(expected.toList())).put("remainingRows",after.cards.size)
        }
        File(context.filesDir,"triple-maintenance-result.json").writeText(report.toString(2))
    }
}
