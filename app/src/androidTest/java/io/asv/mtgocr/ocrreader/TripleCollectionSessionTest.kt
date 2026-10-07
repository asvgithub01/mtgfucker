package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.*
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TripleCollectionSessionTest {
    @Test fun savesUnknownPhotoAndCopiesWithoutTouchingUserLibrary() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val real=instrumentation.targetContext
        // Recover exact fixture sandboxes left by an interrupted instrumentation process.
        real.cacheDir.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("triple-isolated-") }.forEach { dir ->
            check(dir.canonicalFile.parentFile==real.cacheDir.canonicalFile)
            val old=DataUtils.readSerializable<Biblio>(object:ContextWrapper(real) { override fun getFilesDir()=dir },LibraryCatalog.DEFAULT_FILE)
            old?.cards?.forEach { card -> CardDatabase.get(real).cardDao().deleteOwnedPrinting(card.collectionItemId) }
            dir.deleteRecursively()
        }
        val directory=File(real.cacheDir,"triple-isolated-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(real) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=directory
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("triple-isolated-test-"+name,mode)
        }
        // No cloud upload or actual-library selection: isolated preferences/files, real read-only catalog.
        val repository=CardRepository.get(real)
        val titleIndex=TitleLanguageIndex.read(real.assets.open(TitleLanguageIndex.ASSET))
        assertEquals(setOf("pt"),titleIndex.lookup(listOf("Madeixas da Medusa"),listOf("Coils of the Medusa")).candidates)
        val original=OcrCaptureActivity.mBiblio
        val userBefore=LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity }
        DataUtils.saveSerializable(context,Biblio(LibraryCatalog.DEFAULT_FILE,"Test"),LibraryCatalog.DEFAULT_FILE)
        val owned=mutableSetOf<String>()
        val meta=org.json.JSONObject(instrumentation.context.assets.open("triple-card.json").bufferedReader().use { it.readText() })
        val ref=meta.getJSONArray("references").getJSONObject(0)
        val target=TripleScanActivity.Target(ref.getString("scryfallId"),ref.getString("printing"),ref.getString("label").substringBefore(" · "),
            ref.getString("setCode"),ref.getString("label"),ref.getString("image"),ref.getString("finish"),"OCR",true)
        val session=TripleCollectionSession(context) {}
        var state=TripleCollectionSession.State(1,target,null,target.finish,1,null,false,java.util.UUID.randomUUID().toString())
        fun submit():CardInfo? {
            val latch=CountDownLatch(1);var error:Throwable?=null;var result:CardInfo?=null
            instrumentation.runOnMainSync { session.submit(state) { card,_,failure -> result=card;error=failure;card?.let { owned+=it.collectionItemId };latch.countDown() } }
            assertTrue("Save callback",latch.await(90,TimeUnit.SECONDS));if(error!=null) throw AssertionError(error)
            return result
        }
        try {
            val first=requireNotNull(submit());assertEquals("",first.languageCode);assertTrue(first.isProvisionalEdition)
            state=state.copy(photo=instrumentation.context.assets.open("triple-card.jpg").use { it.readBytes() },language="pt",manualLanguage=true)
            val edited=requireNotNull(submit());assertEquals(first.collectionItemId,edited.collectionItemId);assertEquals("pt",edited.languageCode)
            val review=ScanReviewStore.read(context,LibraryCatalog.DEFAULT_FILE)
            assertEquals(1,review.size);assertEquals("ocr",review.single().source);assertEquals(first.collectionItemId,review.single().card.collectionItemId)
            assertTrue(File(io.asv.collectorvision.EdScanJobs.directory(context),"${state.photoKey}.jpg").length()>0)
            state=state.copy(language=null,manualLanguage=true)
            assertEquals("",submit()!!.languageCode) // Explicit unknown must survive updates too.
            val reject=CountDownLatch(1);var rejected:Throwable?=null
            instrumentation.runOnMainSync { session.submit(state.copy(finish="invalid-finish")) { _,_,failure -> rejected=failure;reject.countDown() } }
            assertTrue(reject.await(15,TimeUnit.SECONDS));assertNotNull(rejected)
            assertEquals(target.finish,LegacyCollectionStore.cards(context).single().finish)
            state=state.copy(count=3);assertEquals(3,submit()!!.quantityCount)
            state=state.copy(event=2,photoKey=java.util.UUID.randomUUID().toString(),count=1,language=null,manualLanguage=false)
            val second=submit()!!;assertNotEquals(first.collectionItemId,second.collectionItemId);assertEquals("",second.languageCode)
            assertEquals(2,LegacyCollectionStore.cards(context).size)
            state=state.copy(count=0);assertNull(submit());assertEquals(1,LegacyCollectionStore.cards(context).size)
            assertEquals(3,LegacyCollectionStore.cards(context).single().quantityCount)
            assertEquals(userBefore,LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity })
            // Launch with all recognition disabled, then inject only the isolated writer before any fixture.
            val prefs=real.getSharedPreferences("triple_scanner",0)
            val oldScope=prefs.getString("locked_set",null)
            val oldLanguagePrefs=listOf("scope_language","language_scope_sets").associateWith { prefs.getString(it,null) }
            val old=listOf("ar","art","ocr").associateWith { if(prefs.contains(it)) prefs.getBoolean(it,true) else null }
            prefs.edit().remove("scope_language").remove("language_scope_sets").putBoolean("ar",false).putBoolean("art",false).putBoolean("ocr",false).commit()
            try {
                androidx.test.core.app.ActivityScenario.launch<TripleScanActivity>(android.content.Intent(real,TripleScanActivity::class.java).putExtra("triple_set","FRA")).use { scenario ->
                    scenario.onActivity { activity ->
                        TripleScanActivity::class.java.getDeclaredField("collectionSession").apply { isAccessible=true }.set(activity,session)
                        // Reuse the existing encounter, so this fixture updates only the sandbox row.
                        TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }.invoke(activity,target)
                        val root=activity.findViewById<android.view.View>(android.R.id.content)
                        val controls=root.findViewWithTag<android.widget.LinearLayout>("triple_card_controls")
                        assertEquals(android.view.View.VISIBLE,controls.visibility)
                        assertNotNull(controls.findViewWithTag<android.widget.CheckBox>("triple_edit_foil"))
                        assertNotNull(controls.findViewWithTag<android.widget.Button>("triple_edit_language"))
                        assertNotNull(controls.findViewWithTag<android.widget.Button>("triple_edit_edition"))
                        val accept=TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }
                        val eventField=TripleScanActivity::class.java.getDeclaredField("currentEncounter").apply { isAccessible=true }
                        val event=eventField.getLong(activity)
                        val candidateField=TripleScanActivity::class.java.getDeclaredField("candidate").apply { isAccessible=true }
                        // Old code created copies here, especially after a transient metadata lookup cleared candidate.
                        candidateField.set(activity,null)
                        accept.invoke(activity,target.copy(id="other-printing",printing="other-printing",source="EdScan",provisional=true))
                        accept.invoke(activity,target.copy(source="AR"))
                        assertEquals(event,eventField.getLong(activity))
                        assertEquals(1,root.findViewWithTag<android.widget.LinearLayout>("triple_recent").childCount)
                        val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                        val entry=entries[event.toString()]!!
                        val manual=entry.javaClass.getDeclaredField("manualEdition").apply { isAccessible=true };manual.setBoolean(entry,true)
                        accept.invoke(activity,target.copy(id="late-printing",printing="late-printing",provisional=false,source="EdScan"))
                        assertEquals(target.printing,(candidateField.get(activity) as TripleScanActivity.Target).printing)
                        manual.setBoolean(entry,false)
                        root.findViewWithTag<android.widget.LinearLayout>("triple_recent").getChildAt(0).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    val image=instrumentation.uiAutomation.takeScreenshot()
                    File(real.cacheDir,"triple-menu-proof.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
                    // Real PT alias + failed selected-set lookup -> global provisional printing on the SAME event.
                    var medusaEvent=0L
                    scenario.onActivity { activity ->
                        fun set(name:String,value:Any) { TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.set(activity,value) }
                        TripleScanActivity::class.java.getDeclaredMethod("proposeOcrName",String::class.java).apply { isAccessible=true }.invoke(activity,"Coils of the Medusa")
                        medusaEvent=TripleScanActivity::class.java.getDeclaredField("currentEncounter").apply { isAccessible=true }.getLong(activity)
                        val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                        val entry=entries[medusaEvent.toString()]!!
                        entry.javaClass.getDeclaredField("language").apply { isAccessible=true }.set(entry,"en")
                        entry.javaClass.getDeclaredField("languageOrigin").apply { isAccessible=true }.set(entry,"printing")
                        set("observedLanguage","en");set("printingLanguage","en")
                        val generation=TripleScanActivity::class.java.getDeclaredField("generation").apply { isAccessible=true }.getInt(activity)
                        TripleScanActivity::class.java.getDeclaredMethod("applyTitleLanguage",String::class.java,List::class.java,Int::class.javaPrimitiveType,
                            Long::class.javaPrimitiveType,String::class.java,String::class.java).apply { isAccessible=true }
                            .invoke(activity,"Coils of the Medusa",listOf("Madeixas da Medvsa"),generation,medusaEvent,"Madeixas da Medusa","pt")
                    }
                    var ready=false;val deadline=android.os.SystemClock.elapsedRealtime()+90000
                    while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                        scenario.onActivity { activity ->
                            val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                            val entry=entries[medusaEvent.toString()]!!
                            fun field(name:String)=entry.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(entry)
                            ready=field("language")=="pt" && field("savedId")!=null && (field("target") as TripleScanActivity.Target).printing.isNotBlank()
                            if(ready) {
                                assertEquals("title",field("languageOrigin"));assertEquals(true,field("scopeConflict"))
                                assertEquals(medusaEvent,TripleScanActivity::class.java.getDeclaredField("currentEncounter").apply { isAccessible=true }.getLong(activity))
                                assertEquals(2,activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.LinearLayout>("triple_recent").childCount)
                            }
                        }
                        if(!ready) Thread.sleep(100)
                    }
                    assertTrue("PT title + global fallback must be stored",ready)
                    assertEquals("pt",LegacyCollectionStore.cards(context).first { it.name.contains("Medusa",true) }.languageCode)
                    var chosenUuid=""
                    scenario.onActivity { activity ->
                        val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                        val entry=entries[medusaEvent.toString()]!!
                        val option=entry.javaClass.getDeclaredField("editionOption").apply { isAccessible=true }.get(entry) as CardEditionOption
                        chosenUuid=option.printingUuid
                        TripleScanActivity::class.java.getDeclaredMethod("applyEditionToEntry",entry.javaClass,CardEditionOption::class.java,
                            Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(activity,entry,option,true,false)
                        val current=entry.javaClass.getDeclaredField("target").apply { isAccessible=true }.get(entry) as TripleScanActivity.Target
                        TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }
                            .invoke(activity,current.copy(id="late-art",printing="late-art",source="EdScan",provisional=false))
                        assertEquals(chosenUuid,(entry.javaClass.getDeclaredField("target").apply { isAccessible=true }.get(entry) as TripleScanActivity.Target).printing)
                    }
                    var selected=false;val selectionDeadline=android.os.SystemClock.elapsedRealtime()+15000
                    while(!selected && android.os.SystemClock.elapsedRealtime()<selectionDeadline) {
                        selected=LegacyCollectionStore.cards(context).any { it.printingUuid==chosenUuid && !it.isProvisionalEdition && it.languageCode=="pt" }
                        if(!selected) Thread.sleep(100)
                    }
                    assertTrue("Manual edition must persist without losing PT or adding a copy",selected)
                    assertEquals(2,LegacyCollectionStore.cards(context).size)

                    // Fixed scope affects NEW encounters only, remains manual against late OCR,
                    // and survives printing updates without creating copies.
                    var scopeEvent=0L
                    scenario.onActivity { activity ->
                        fun method(name:String,vararg types:Class<*>)=TripleScanActivity::class.java.getDeclaredMethod(name,*types).apply { isAccessible=true }
                        fun field(name:String)=TripleScanActivity::class.java.getDeclaredField(name).apply { isAccessible=true }
                        method("setScopeLanguage",String::class.java).invoke(activity,"en")
                        method("accept",TripleScanActivity.Target::class.java).invoke(activity,target)
                        scopeEvent=field("currentEncounter").getLong(activity)
                        val entries=field("galleryEntries").get(activity) as Map<*,*>
                        val entry=entries[scopeEvent.toString()]!!
                        fun value(name:String)=entry.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(entry)
                        assertEquals("scope",value("languageOrigin"));assertEquals("en",value("language"));assertEquals(true,value("manualLanguage"))
                        assertEquals("en",field("observedLanguage").get(activity))
                        val oldEntry=entries[medusaEvent.toString()]!!
                        assertEquals("pt",oldEntry.javaClass.getDeclaredField("language").apply { isAccessible=true }.get(oldEntry))
                        val scope=activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.TextView>("triple_scope")
                        assertTrue(scope.text.toString(),scope.text.contains("EN"));assertTrue(scope.text.contains("FRA"))
                        method("applyTitleLanguage",String::class.java,List::class.java,Int::class.javaPrimitiveType!!,Long::class.javaPrimitiveType!!,String::class.java,String::class.java)
                            .invoke(activity,target.name,listOf("Portuguese"),field("generation").getInt(activity),scopeEvent,"Portuguese","pt")
                        assertEquals("en",value("language"))
                        // Seed only the selector catalog, no network dependency in this UI regression.
                        val resolver=method("getSetLanguages").invoke(activity)
                        resolver.javaClass.getDeclaredField("memory").apply { isAccessible=true }.set(resolver,mapOf("FRA" to listOf("English","French","German","Italian","Japanese","Spanish")))
                        assertFalse("Summary must not be interactive",scope.isClickable)
                        val styled=scope.text as android.text.Spanned
                        assertTrue(styled.getSpans(0,styled.length,android.text.style.StyleSpan::class.java).any { it.style==android.graphics.Typeface.BOLD })
                        val root=activity.findViewById<android.view.View>(android.R.id.content)
                        assertTrue(root.findViewWithTag<android.widget.ProgressBar>("triple_index_progress").isIndeterminate)
                        assertTrue(root.findViewWithTag<android.widget.LinearLayout>("triple_scope_symbols").childCount>0)
                        val languageButton=root.findViewWithTag<android.widget.Button>("triple_scope_language")
                        assertEquals("triple_edition_options",(languageButton.parent as android.view.View).tag)
                        languageButton.performClick()
                    }
                    var scopeSaved=false;val scopeDeadline=android.os.SystemClock.elapsedRealtime()+15000
                    while(!scopeSaved && android.os.SystemClock.elapsedRealtime()<scopeDeadline) {
                        scenario.onActivity { activity ->
                            val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                            val entry=entries[scopeEvent.toString()]!!
                            val id=entry.javaClass.getDeclaredField("savedId").apply { isAccessible=true }.get(entry) as String?
                            scopeSaved=id!=null && LegacyCollectionStore.cards(context).any { it.collectionItemId==id && it.languageCode=="en" }
                        }
                        if(!scopeSaved) Thread.sleep(100)
                    }
                    assertTrue("Fixed language must persist",scopeSaved)
                    instrumentation.waitForIdleSync()
                    val scopeImage=instrumentation.uiAutomation.takeScreenshot()
                    File(real.cacheDir,"triple-language-scope-proof.png").outputStream().use { scopeImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };scopeImage.recycle()
                    scenario.onActivity { activity ->
                        val dialog=TripleScanActivity::class.java.getDeclaredField("languagePicker").apply { isAccessible=true }.get(activity) as androidx.appcompat.app.AlertDialog
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertNull(TripleScanActivity::class.java.getDeclaredField("fixedLanguage").apply { isAccessible=true }.get(activity))
                        assertTrue(activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<android.widget.TextView>("triple_scope").text.contains(activity.getString(R.string.triple_all_languages)))
                        val accept=TripleScanActivity::class.java.getDeclaredMethod("accept",TripleScanActivity.Target::class.java).apply { isAccessible=true }
                        accept.invoke(activity,target) // Same physical card retains the already accepted scope choice.
                        val entries=TripleScanActivity::class.java.getDeclaredField("galleryEntries").apply { isAccessible=true }.get(activity) as Map<*,*>
                        val entry=entries[scopeEvent.toString()]!!
                        assertEquals("en",entry.javaClass.getDeclaredField("language").apply { isAccessible=true }.get(entry))
                        accept.invoke(activity,target.copy(id="pending-next",printing="",name="Next unknown",source="OCR",provisional=true))
                        val nextEvent=TripleScanActivity::class.java.getDeclaredField("currentEncounter").apply { isAccessible=true }.getLong(activity)
                        assertNull(entries[nextEvent.toString()]!!.let { it.javaClass.getDeclaredField("language").apply { isAccessible=true }.get(it) })
                    }

                    scenario.onActivity { activity ->
                        val root=activity.findViewById<android.view.View>(android.R.id.content)
                        val row=root.findViewWithTag<android.widget.LinearLayout>("triple_edition_options")
                        val options=row.parent as android.view.View
                        val scroll=options.parent as android.widget.ScrollView
                        scroll.visibility=android.view.View.VISIBLE
                        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
                        val status=root.findViewWithTag<android.widget.TextView>("triple_status")
                        status.setText(R.string.triple_ready);assertEquals(android.view.View.GONE,status.visibility)
                        val result=root.findViewWithTag<android.widget.TextView>("triple_result")
                        result.setText(R.string.triple_waiting);assertEquals(android.view.View.GONE,result.visibility)
                        status.setText(R.string.triple_engine_error);assertEquals(android.view.View.VISIBLE,status.visibility)
                        status.setText(R.string.triple_ready)
                    }
                    instrumentation.waitForIdleSync()
                    val optionsImage=instrumentation.uiAutomation.takeScreenshot()
                    File(real.cacheDir,"triple-options-ui-proof.png").outputStream().use { optionsImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };optionsImage.recycle()
                    // Drain all queued writes before restoring global collection state.
                    state=state.copy(event=2,count=0);submit()
                }
            } finally {
                assertEquals(userBefore,LegacyCollectionStore.cards(real).map { it.collectionItemId to it.quantity })
                val edit=prefs.edit();oldLanguagePrefs.forEach { (key,value)->if(value==null) edit.remove(key) else edit.putString(key,value) };if(oldScope==null) edit.remove("locked_set") else edit.putString("locked_set",oldScope);old.forEach { (key,value)->if(value==null) edit.remove(key) else edit.putBoolean(key,value) };edit.commit()
            }
        } finally {
            owned+=LegacyCollectionStore.cards(context).map { it.collectionItemId }
            owned.forEach { id -> val done=CountDownLatch(1);repository.clearSelectedEdition(id) { done.countDown() };assertTrue(done.await(10,TimeUnit.SECONDS)) }
            OcrCaptureActivity.mBiblio=original
            directory.deleteRecursively() // Exact test-created sandbox only.
        }
    }
}
