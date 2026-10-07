package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.asv.collectorvision.EdScanPerf
import io.asv.collectorvision.NativeCollectorVisionEngine
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Triple recognition with per-encounter collection rows and immutable review photos. */
class TripleScanActivity:AppCompatActivity() {
    data class Target(val id:String,val printing:String,val name:String,val set:String,val label:String,val image:String,val finish:String,val source:String,val provisional:Boolean=false,val rarity:String="",val number:String=label.substringAfterLast(" #",""))
    private lateinit var preview:TripleArPreview
    private lateinit var status:TextView
    private lateinit var scopeStatus:TextView
    private lateinit var scopeSymbols:LinearLayout
    private lateinit var scopeLanguageButton:Button
    private var scopeSymbolsKey:String?=null
    private var languageScopeKey:String?=null
    private var fixedLanguage:String?=null
    @Volatile private var choosingLanguage=false
    private var languagePickerRevision=0
    private var languagePicker:androidx.appcompat.app.AlertDialog?=null
    private val setLanguagesLazy=lazy { TripleSetLanguageResolver(this) { trace.event(it) } }
    private val setLanguages by setLanguagesLazy
    private lateinit var result:TextView
    private lateinit var recent:LinearLayout
    private lateinit var input:MultiAutoCompleteTextView
    private lateinit var sets:ScannerSetInput
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private lateinit var lookup:TripleCardLookup
    private val sessionTargets=linkedMapOf<String,Target>()
    private lateinit var trace:TripleScanTrace
    private var lastVisualAudit=0L
    @Volatile private var sceneStartedAt=0L
    private var lastObservationLog=0L
    private var absentFrames=0
    @Volatile private var preferGlobalUntil=0L
    private var lastDisplayedId=""
    private var lastLookupFailureAt=0L
    private var engine:NativeCollectorVisionEngine?=null
    @Volatile private var visualFrames=0
    @Volatile private var ocrCompleted=0
    private val text=TripleSnapshotOcr()
    private val languages=CardTextLanguageDetector(useShortRules=false)
    private val languageConsensus=TripleLanguageConsensus()
    private val languageWorker=Executors.newSingleThreadExecutor()
    private val titleLanguageIndex by lazy { runCatching { TitleLanguageIndex.read(assets.open(TitleLanguageIndex.ASSET)) }.getOrNull() }
    private var languageConflict=false
    private lateinit var collectionSession:TripleCollectionSession
    private lateinit var cardControls:LinearLayout
    private var controlsKey=""
    private val pendingPhotos=mutableMapOf<String,ByteArray>()
    @Volatile private var photoCapturedFor=""
    private val addedCollectionIds=linkedSetOf<String>()
    private var printingLanguage:String?=null
    private var printingLanguageAttempt=""
    private val printingLanguagesLazy=lazy { TriplePrintingLanguageResolver(this) { trace.event(it) } }
    private val printingLanguages by printingLanguagesLazy
    private var absentSince=0L
    @Volatile private var resumed=false
    @Volatile private var closed=false
    @Volatile private var generation=0
    @Volatile private var references=emptyMap<String,Target>()
    @Volatile private var arEnabled=true
    @Volatile private var artEnabled=true
    @Volatile private var ocrEnabled=true
    private val encounters=TripleEncounterGate()
    private var currentEncounter=0L
    private var lastArSeen=0L
    private var runStartedAt=0L
    private var feedbackCount=0
    private var scanTone:android.media.ToneGenerator?=null
    @Volatile private var frameKey=""
    @Volatile private var lastFrameAt=0L
    @Volatile private var arReadyAt=0L
    private var lastTrackedAt=0L
    private var streakId=""
    private var readings=0
    @Volatile private var lastOcrKey=""
    @Volatile private var lastOcrAt=0L
    @Volatile private var candidate:Target?=null
    private var evidenceKey=""
    private var resolvingKey=""
    private var observedLanguage:String?=null
    private var observedName:String?=null
    private var allowedSets=emptySet<String>()
    private var prices=emptyMap<String,Pair<Double,String>>()
    @Volatile private var activePackage=false
    private data class GalleryEntry(var target:Target,val root:LinearLayout,val image:ImageView,val flag:TextView,val edition:TextView,val price:TextView,var language:String?=null,
        var manualLanguage:Boolean=false,var languageOrigin:String="",var manualEdition:Boolean=false,
        var editionOption:io.asv.mtgocr.ocrreader.data.CardEditionOption?=null,var editionRevision:Int=0,var resolvingEdition:Boolean=false,var scopeConflict:Boolean=false,
        var manualFinish:Boolean=false,var selectedFinish:String=target.finish,var count:Int=1,
        var photo:ByteArray?=null,val photoKey:String=java.util.UUID.randomUUID().toString(),
        var savedId:String?=null,var saveSignature:String="",var saveError:Boolean=false,
        var amount:Pair<Double,String>?=null,var priceRequested:String="")
    private val galleryEntries=linkedMapOf<String,GalleryEntry>()
    private val identifiedLanguages=mutableMapOf<String,String>()
    private val logHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeat=object:Runnable {
        override fun run() {
            if(!resumed || closed) return
            val power=getSystemService(POWER_SERVICE) as android.os.PowerManager
            val thermal=if(android.os.Build.VERSION.SDK_INT>=29) power.currentThermalStatus else -1
            val runtime=Runtime.getRuntime()
            trace.event("triple.health visualFrames=$visualFrames ocrCompleted=$ocrCompleted busy=${busy.get()} heapMb=${(runtime.totalMemory()-runtime.freeMemory())/1048576} powerSave=${power.isPowerSaveMode} thermal=$thermal")
            logHandler.postDelayed(this,5000)
        }
    }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(::preview.isInitialized) preview.retryStart() }
    private val picker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if(response.resultCode==RESULT_OK) response.data?.getStringExtra("package_id")?.let { input.setText(it);configureEdition() }
    }
    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state);EdScanPerf.enabled=BuildConfig.DEBUG
        trace=TripleScanTrace(applicationContext);collectionSession=TripleCollectionSession(applicationContext,trace::event);lookup=TripleCardLookup(applicationContext,trace::event);trace.event("triple.session v=6");runStartedAt=SystemClock.elapsedRealtime()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val toolbar=LinearLayout(this)
        val options=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(this).apply { addView(options);visibility=View.GONE }
        toolbar.addView(Button(this).apply { setText(R.string.triple_title);setOnClickListener { scroll.visibility=if(scroll.visibility==View.GONE) View.VISIBLE else View.GONE } },LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(Button(this).apply { setText(android.R.string.cancel);setOnClickListener { finish() } })
        root.addView(toolbar)
        val scopeRow=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4)) }
        scopeSymbols=LinearLayout(this).apply { tag="triple_scope_symbols" };scopeRow.addView(scopeSymbols)
        scopeStatus=TextView(this).apply { setPadding(dp(6),0,0,0);tag="triple_scope" }
        scopeRow.addView(scopeStatus,LinearLayout.LayoutParams(0,-2,1f));root.addView(scopeRow)
        val gallery=HorizontalScrollView(this)
        recent=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;tag="triple_recent" }
        gallery.addView(recent);root.addView(gallery,LinearLayout.LayoutParams(-1,dp(112)))
        status=scannerMessage("triple_status",R.string.triple_loading,R.string.triple_ready);root.addView(status)
        result=scannerMessage("triple_result",R.string.triple_waiting,R.string.triple_waiting);root.addView(result)
        preview=TripleArPreview(this,Intent(),{ resumed && !closed && !choosingLanguage && (artEnabled || ocrEnabled || galleryEntries[currentEncounter.toString()]?.let { it.count>0 && it.photo==null }==true) && !busy.get() && SystemClock.elapsedRealtime()-lastFrameAt>=TripleScanPolicy.FRAME_INTERVAL_MS },::frame,options)
        text.diagnostics=trace::event
        preview.diagnostics=trace::event
        preview.onRecognition={ printing ->
            if(arEnabled && resumed && !closed && !choosingLanguage) {
                if(printing!=null) {
                    lastArSeen=SystemClock.elapsedRealtime();encounters.present()
                    references[printing]?.let { target ->
                        if(candidate?.id!=target.id || (!artEnabled && !ocrEnabled)) {
                            frameKey=target.id;accept(target.copy(source="AR"))
                        }
                    }
                } else if(!artEnabled && !ocrEnabled) encounters.missing(SystemClock.elapsedRealtime())
            }
        }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        cardControls=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;setPadding(dp(6),dp(3),dp(6),dp(3))
            setBackgroundColor(0xE6202430.toInt());visibility=View.GONE;tag="triple_card_controls"
        }
        preview.addView(cardControls,FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(20) })
        preview.onCardPosition={ points ->
            if(points!=null && points.size==8 && cardControls.visibility==View.VISIBLE) {
                val x=(points.filterIndexed { i,_->i%2==0 }.average().toFloat()-cardControls.width/2f).coerceIn(0f,(preview.width-cardControls.width).coerceAtLeast(0).toFloat())
                val y=(points.filterIndexed { i,_->i%2==1 }.maxOrNull() ?: 0f).coerceIn(0f,(preview.height-cardControls.height).coerceAtLeast(0).toFloat())
                cardControls.translationX=x-cardControls.left;cardControls.translationY=y-cardControls.top
            } else { cardControls.translationX=0f;cardControls.translationY=0f }
        }
        val editionRow=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL;tag="triple_edition_options" }
        val editionField=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        editionRow.addView(editionField,LinearLayout.LayoutParams(0,-2,1f))
        input=MultiAutoCompleteTextView(this).apply { setHint(R.string.edition_search_hint);setSingleLine();setTypeface(typeface,Typeface.BOLD);tag="triple_set" };editionField.addView(input)
        scopeLanguageButton=Button(this).apply { tag="triple_scope_language";isAllCaps=false;setOnClickListener { showScopeLanguagePicker() } }
        editionRow.addView(scopeLanguageButton,LinearLayout.LayoutParams(0,-2,1f));options.addView(editionRow)
        sets=ScannerSetInput(this,input,"triple_scanner",Runnable { configureEdition() },Runnable { invalidateEvidence();if(languageScopeKey!=TripleLanguageScope.key(sets.codes())) { fixedLanguage=null;languagePickerRevision++;languagePicker?.dismiss();getSharedPreferences("triple_scanner",0).edit().remove("scope_language").apply() };updateScopeStatus();references=sessionTargets.toMap();activePackage=false;preview.setPreloadedBatch(null,emptyList());if(arEnabled) replaySession() })
        intent.getStringExtra("triple_set")?.let { input.setText(it) }
        options.addView(Button(this).apply { setText(R.string.ar_use_package);setOnClickListener { picker.launch(Intent(this@TripleScanActivity,ArDownloadsActivity::class.java).putExtra("choose",true)) } })
        val switches=LinearLayout(this)
        val prefs=getSharedPreferences("triple_scanner",0)
        fun method(key:String,label:Int,initial:(Boolean)->Unit,change:(Boolean)->Unit) {
            switches.addView(CheckBox(this).apply {
                tag="triple_method_$key";setText(label);isChecked=prefs.getBoolean(key,true);initial(isChecked)
                setOnCheckedChangeListener { _,enabled ->
                    initial(enabled);prefs.edit().putBoolean(key,enabled).apply();change(enabled)
                    trace.event("triple.methods ar=$arEnabled art=$artEnabled ocr=$ocrEnabled")
                }
            },LinearLayout.LayoutParams(0,-2,1f))
        }
        method("ar",R.string.triple_method_ar,{ arEnabled=it }) { preview.setRecognitionEnabled(it);configureEdition() }
        method("art",R.string.triple_method_art,{ artEnabled=it }) { invalidateEvidence();if(it) prepareArt() }
        method("ocr",R.string.triple_method_ocr,{ ocrEnabled=it }) { invalidateEvidence() }
        root.addView(switches,1)
        preview.setRecognitionEnabled(arEnabled)
        trace.event("triple.methods ar=$arEnabled art=$artEnabled ocr=$ocrEnabled")
        root.addView(scroll,LinearLayout.LayoutParams(-1,dp(240)));setContentView(root)
        if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(android.Manifest.permission.CAMERA)
        if(artEnabled) prepareArt()
        if(fixedLanguage==null) languageWorker.execute { titleLanguageIndex }
        val indexStatus=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;tag="triple_index_preparation";setPadding(dp(12),dp(8),dp(12),dp(8))
            setBackgroundColor(0x182196F3)
            addView(TextView(this@TripleScanActivity).apply { setText(R.string.scan_preparing_name_index);setTypeface(typeface,Typeface.BOLD) })
            addView(ProgressBar(this@TripleScanActivity,null,android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate=true;tag="triple_index_progress"
                indeterminateTintList=android.content.res.ColorStateList.valueOf(MagicPalette.secondaryColor(this@TripleScanActivity))
            },LinearLayout.LayoutParams(-1,dp(6)).apply { topMargin=dp(6) })
        }
        root.addView(indexStatus,2)
        val indexStart=System.nanoTime()
        CardRepository.get(this).prepareCardNamePredictor { ready ->
            trace.event("triple.nameIndex ready=$ready ms=${(System.nanoTime()-indexStart)/1000000}")
            if(!closed) indexStatus.visibility=View.GONE
        }
        configureEdition()
    }
    private fun prepareArt() { worker.execute {
        if(engine!=null || closed) return@execute
        try { engine=EdScanPerf.once("triple.engineInit") { NativeCollectorVisionEngine.prepare(applicationContext) {} };ui { status.setText(R.string.triple_ready) } }
        catch(error:Exception) { android.util.Log.w("EdScanPerf","Triple engine unavailable",error);ui { status.setText(R.string.triple_engine_error) } }
    } }
    /** Hide idle boilerplate, but keep loading, errors and actual recognition results visible. */
    private fun scannerMessage(tagName:String,initial:Int,idle:Int)=TextView(this).apply {
        tag=tagName;setPadding(12,4,12,4)
        doAfterTextChanged { visibility=if(it.isNullOrBlank() || it.toString()==getString(idle)) View.GONE else View.VISIBLE }
        setText(initial)
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun ui(action:()->Unit)=runOnUiThread { if(!closed && !isDestroyed) action() }
    private fun invalidateEvidence() { clearCurrentLanguage();languageConsensus.reset();rarityRequested.clear();generation++;candidate=null;frameKey="";lastOcrKey="";observedLanguage=null;observedName=null;preferGlobalUntil=0L;arReadyAt=0L;if(::result.isInitialized) result.setText(R.string.triple_waiting) }
    private fun updateScopeStatus() {
        val codes=sets.codes().sorted()
        val codeLabel=codes.joinToString(", ").ifBlank { getString(R.string.triple_all_editions) }
        val languageLabel=fixedLanguage?.let(TripleLanguageBadge::label) ?: getString(R.string.triple_all_languages)
        val summary=getString(R.string.triple_selected_scope,codeLabel)+" · "+languageLabel
        scopeStatus.text=android.text.SpannableString(summary).apply {
            if(codes.isNotEmpty()) {
                val start=summary.indexOf(codeLabel)
                if(start>=0) setSpan(android.text.style.StyleSpan(Typeface.BOLD),start,start+codeLabel.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        scopeLanguageButton.text=getString(R.string.review_language)+" · "+languageLabel+" ▾"
        val key=TripleLanguageScope.key(codes.toSet())
        if(scopeSymbolsKey!=key) {
            scopeSymbolsKey=key;scopeSymbols.removeAllViews()
            codes.take(3).forEach { code ->
                val icon=ImageView(this).apply { contentDescription=code;scaleType=ImageView.ScaleType.FIT_CENTER }
                scopeSymbols.addView(icon,LinearLayout.LayoutParams(dp(24),dp(24)))
                SetSymbolLoader.display(this,code,icon,android.R.drawable.ic_menu_gallery)
            }
        }
    }
    private fun syncLanguageScope() {
        val key=TripleLanguageScope.key(sets.codes())
        if(languageScopeKey!=key) {
            val first=languageScopeKey==null
            languageScopeKey=key;languagePickerRevision++;languagePicker?.dismiss()
            val prefs=getSharedPreferences("triple_scanner",0)
            fixedLanguage=if(first && prefs.getString("language_scope_sets",null)==key)
                prefs.getString("scope_language",null)?.takeIf { it in TripleLanguageScope.allCodes } else null
            if(!first) prefs.edit().remove("scope_language").putString("language_scope_sets",key).apply()
            if(!first && key.isNotEmpty()) scopeStatus.post { if(!closed && languageScopeKey==key) showScopeLanguagePicker() }
        }
        updateScopeStatus()
    }
    private fun setScopeLanguage(code:String?) {
        fixedLanguage=code
        getSharedPreferences("triple_scanner",0).edit().putString("scope_language",code).putString("language_scope_sets",languageScopeKey).apply()
        // New scope applies to future encounters, not already accepted physical copies.
        invalidateEvidence();updateScopeStatus()
        trace.event("triple.languageScope scope=$languageScopeKey language=${code ?: "all"} appliesTo=newEncounters")
    }
    private fun showScopeLanguagePicker() {
        val codes=sets.codes();val key=TripleLanguageScope.key(codes)
        if(key!=languageScopeKey) { configureEdition();return }
        val revision=++languagePickerRevision
        languagePicker?.dismiss()
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val loading=TextView(this).apply { setText(R.string.triple_languages_loading) };body.addView(loading)
        val dialog=androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.triple_choose_scope_language)
            .setView(body).setNegativeButton(android.R.string.cancel,null)
            .setNeutralButton(R.string.triple_all_languages) { _,_->setScopeLanguage(null) }.create()
        invalidateEvidence();choosingLanguage=true
        dialog.setOnDismissListener { if(languagePicker===dialog) choosingLanguage=false }
        languagePicker=dialog;dialog.show()
        setLanguages.load(codes) { available ->
            if(closed || revision!=languagePickerRevision || key!=TripleLanguageScope.key(sets.codes()) || !dialog.isShowing) return@load
            if(available==null) {
                loading.setText(R.string.triple_languages_failed)
                body.addView(Button(this).apply { setText(R.string.triple_retry_save);setOnClickListener { showScopeLanguagePicker() } })
            } else {
                body.removeAllViews()
                body.addView(TextView(this).apply { setText(R.string.triple_language_scope_help) })
                val list=ListView(this)
                val choices=listOf<String?>(null)+available
                list.choiceMode=ListView.CHOICE_MODE_SINGLE
                list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_single_choice,choices.map { it?.let(TripleLanguageBadge::label) ?: getString(R.string.triple_all_languages) })
                list.setItemChecked(choices.indexOf(fixedLanguage).coerceAtLeast(0),true)
                list.setOnItemClickListener { _,_,position,_->setScopeLanguage(choices[position]);dialog.dismiss() }
                body.addView(list,LinearLayout.LayoutParams(-1,dp(320)))
            }
        }
    }
    private fun configureEdition() {
        syncLanguageScope()
        invalidateEvidence();val token=generation;references=sessionTargets.toMap();activePackage=false
        preview.setPreloadedBatch(null,emptyList())
        val codes=sets.codes();allowedSets=io.asv.mtgocr.ocrreader.data.ScanSetLockPolicy.expand(codes)
        if(!arEnabled || codes.size!=1) { if(arEnabled) replaySession();status.setText(if(codes.size>1) R.string.ar_one_package else R.string.triple_ready);return }
        if(!PremiumAccess.isEnabled(this)) { status.setText(R.string.premium_required);return }
        val code=codes.single();status.setText(R.string.ar_loading_catalog)
        ArPackageStore.worker.execute {
            val installed=runCatching { ArPackageStore.installed(applicationContext,code) }.getOrNull()
            val rarities=runCatching {
                installed?.references.orEmpty().map { it.scryfallId }.distinct().chunked(400).flatMap {
                    io.asv.mtgocr.ocrreader.data.CardDatabase.get(applicationContext).cardDao().printingsByScryfallIds(it)
                }.associate { it.scryfallId to it.rarity }
            }.getOrDefault(emptyMap())
            ui {
                if(token!=generation) return@ui
                if(installed==null) { replaySession();status.setText(R.string.triple_missing_package);return@ui }
                references=installed.references.associate { ref -> ref.printing to Target(ref.scryfallId,ref.printing,ref.label.substringBefore(" · "),ref.label.substringAfterLast(" · ").substringBefore(" #"),ref.label,ref.image,ref.finish,"AR",rarity=ref.rarity.ifBlank { rarities[ref.scryfallId].orEmpty() }) }
                allowedSets=allowedSets+references.values.map { it.set }
                activePackage=true;arReadyAt=SystemClock.elapsedRealtime()
                // Recognition is available before local prices return. No catalog/network on this path.
                fun cards()=installed.references.mapNotNull { references[it.printing] }.map { t->ArPreloadCard(t.printing,t.image,t.label,price(t),t.rarity,t.set,t.number) }
                preview.setPreloadedBatch(installed.entry.name,cards(),arReadyAt,installed.database)
                references=sessionTargets+references;replaySession()
                val started=System.nanoTime()
                CardRepository.get(this).loadArPackagePrices(references.values.associate { it.id to it.finish }) { found ->
                    EdScanPerf.record("triple.localPrices",System.nanoTime()-started)
                    if(token==generation && !closed) { prices=prices+found.mapKeys { (id,_)->id+"|"+(references.values.firstOrNull { it.id==id }?.finish ?: "nonfoil") };preview.updatePrices(cards());refreshGallery();renderResult() }
                }
                status.setText(R.string.triple_ready)
            }
        }
    }
    private fun frame(image:android.media.Image,rotation:Int,release:()->Unit,tracked:String?,corners:FloatArray?,guide:FloatArray?):Boolean {
        if(!resumed || closed || !busy.compareAndSet(false,true)) return false
        lastFrameAt=SystemClock.elapsedRealtime();val token=generation;val queued=System.nanoTime()
        try { worker.execute {
            var bitmap:Bitmap?=null;var crop:Bitmap?=null;var handedOff=false
            try {
                if(closed || !resumed || token!=generation) return@execute
                EdScanPerf.record("triple.workerQueue",System.nanoTime()-queued)
                val frameNow=SystemClock.elapsedRealtime()
                val trackedTarget=if(!arEnabled || frameNow<preferGlobalUntil) null else tracked?.let { references[it] ?: candidate?.takeIf { c->c.printing==it } }
                val audit=artEnabled && trackedTarget!=null && TripleScanPolicy.auditDue(frameNow,lastVisualAudit)
                if(!audit && trackedTarget!=null && corners!=null && lastOcrKey==trackedTarget.id && SystemClock.elapsedRealtime()-lastOcrAt<TripleScanPolicy.OCR_INTERVAL_MS) {
                    frameKey=trackedTarget.id;lastTrackedAt=SystemClock.elapsedRealtime();EdScanPerf.record("triple.skipOcrCooldown",0);return@execute
                }
                bitmap=EdScanPerf.measure("triple.decode") {
                    val p=image.planes.map { ArYuvRgb.Plane(it.buffer,it.rowStride,it.pixelStride) }
                    Bitmap.createBitmap(ArYuvRgb.pixels(image.width,image.height,0,0,p[0],p[1],p[2]),image.width,image.height,Bitmap.Config.ARGB_8888)
                }
                release()
                if(ocrEnabled && guide!=null && trackedTarget==null && (!artEnabled || candidate==null || candidate?.provisional==true) && SystemClock.elapsedRealtime()-lastGuideAt>=1000 && !ocrScheduled.get()) {
                    val titleCrop=warp(requireNotNull(bitmap),guide,630,182)
                    val full=guideCardPhoto(requireNotNull(bitmap),guide)
                    lastGuideAt=SystemClock.elapsedRealtime()
                    runOnUiThread { guidedOcr(titleCrop,token,full) }
                }
                val now=SystemClock.elapsedRealtime()
                val target=trackedTarget
                if(!audit && target!=null && corners!=null) {
                    lastTrackedAt=now;frameKey=target.id
                    crop=warp(requireNotNull(bitmap),corners)
                    EdScanPerf.record("triple.arHit",0)
                    val captured=crop;crop=null;handedOff=true
                    captureReviewPhoto(captured,frameKey,token)
                    runOnUiThread { if(valid(token,target.id)) { accept(target);ocr(captured,target,token) } else { captured?.recycle();busy.set(false) } }
                    return@execute
                }
                if(!artEnabled) return@execute
                if(!audit && now>=preferGlobalUntil && !TripleScanPolicy.useVisual(now,arReadyAt,lastTrackedAt,false)) return@execute
                lastVisualAudit=now
                val raw=requireNotNull(bitmap)
                val upright=if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
                try {
                    val scan=engine?.let { EdScanPerf.measure("triple.edscan") { it.scan(upright) } } ?: return@execute
                    visualFrames++
                    trace.event("triple.methodSample method=art present=${scan.present} detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs} ar=$arEnabled ocr=$ocrEnabled")
                    if(!scan.present || scan.corners.size!=4) {
                        if(audit) return@execute
                        ui { if(token==generation && SystemClock.elapsedRealtime()-lastArSeen>1000) encounters.missing(SystemClock.elapsedRealtime()) }
                        absentFrames++;readings=0;if(absentSince==0L) absentSince=now
                        if(absentFrames>=2 && now-absentSince>=3000) { sceneStartedAt=0L;frameKey="";ui { if(token==generation) { if(candidate?.source!="OCR" || SystemClock.elapsedRealtime()-lastOcrNameAt>4000) { clearCurrentLanguage();languageConsensus.reset();candidate=null;result.setText(R.string.triple_waiting) } } } }
                        if(now-lastObservationLog>=1000) { lastObservationLog=now;trace.event("triple.observation present=false package=$activePackage detectMs=${scan.detectionMs}") }
                        return@execute
                    }
                    ui { if(token==generation) encounters.present() }
                    absentFrames=0;absentSince=0L;if(sceneStartedAt==0L) sceneStartedAt=now
                    val top=scan.hits.firstOrNull()
                    if(top?.cardId==streakId) readings++ else { streakId=top?.cardId.orEmpty();readings=1 }
                    val reliable=top!=null && TripleScanPolicy.visualReliable(top.score,scan.hits.getOrNull(1)?.score,readings)
                    val showCandidate=top!=null && TripleScanPolicy.visualCandidate(top.score,readings)
                    if(now-lastObservationLog>=750) {
                        lastObservationLog=now
                        trace.event("triple.observation present=true ageMs=${now-sceneStartedAt} top=${top?.cardId?.hashCode()} score=${top?.score} margin=${top?.score?.minus(scan.hits.getOrNull(1)?.score ?: Float.NaN)} readings=$readings preview=$showCandidate strict=$reliable package=$activePackage audit=$audit detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs}")
                    }
                    // A weak audit cannot displace a current AR match; a strong different card can.
                    if(audit && (!reliable || top?.cardId==target?.id)) return@execute
                    if(audit && reliable) { preferGlobalUntil=now+2000;trace.event("triple.globalOverridesTracked differentTarget=true") }
                    frameKey=if(showCandidate) top!!.cardId else "ocr"
                    crop=warp(upright,scan.corners.flatMap { listOf(it.x*upright.width,it.y*upright.height) }.toFloatArray())
                    val captured=crop;crop=null;handedOff=true
                    captureReviewPhoto(captured,frameKey,token)
                    runOnUiThread {
                        if(token!=generation || !resumed) { captured?.recycle();busy.set(false);return@runOnUiThread }
                        if(showCandidate) {
                            val id=top!!.cardId
                            if(candidate?.id==id) { accept(requireNotNull(candidate).copy(provisional=!reliable));ocr(captured,candidate,token) }
                            else {
                                result.setText(R.string.triple_resolving)
                                resolveVisual(id,token,!reliable)
                                // OCR and exact metadata resolve independently; raw reading is retained only for this identity.
                                ocr(captured,null,token,id)
                            }
                        } else ocr(captured,null,token)
                    }
                    return@execute
                } finally { if(upright!==raw) upright.recycle() }
            } catch(error:Exception) { android.util.Log.w("EdScanPerf","Triple frame failed",error) }
            finally { bitmap?.recycle();crop?.recycle();release();if(!handedOff) busy.set(false) }
        } } catch(_:java.util.concurrent.RejectedExecutionException) { busy.set(false);return false }
        return true
    }
    // Owned until the main-thread OCR callback completes; the AR render loop never waits on it.
    private val ocrScheduled=AtomicBoolean(false)
    private var nameLookupPending=false
    private var ocrSequence=0
    private fun valid(token:Int,key:String)=!closed && resumed && token==generation && key==frameKey
    private fun jpeg(bitmap:Bitmap):ByteArray=java.io.ByteArrayOutputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG,94,out));out.toByteArray()
    }
    private fun guideCardPhoto(bitmap:Bitmap,guide:FloatArray):ByteArray? {
        val matrix=Matrix()
        if(!matrix.setPolyToPoly(floatArrayOf(.03f,.025f,.97f,.025f,.97f,.22f,.03f,.22f),0,guide,0,4)) return null
        val points=floatArrayOf(0f,0f,1f,0f,1f,1f,0f,1f);matrix.mapPoints(points)
        val card=warp(bitmap,points) ?: return null
        return try { jpeg(card) } finally { card.recycle() }
    }
    private fun captureReviewPhoto(bitmap:Bitmap?,key:String,token:Int) {
        if(bitmap==null || key==photoCapturedFor) return
        val bytes=jpeg(bitmap);photoCapturedFor=key
        ui {
            if(token!=generation) return@ui
            val entry=galleryEntries[currentEncounter.toString()]?.takeIf { it.target.id==key && it.count>0 }
            if(entry!=null) { if(entry.photo==null) { entry.photo=bytes;saveEntry(entry) } }
            else { pendingPhotos.clear();pendingPhotos[key]=bytes }
        }
    }
    private fun warp(bitmap:Bitmap,points:FloatArray,width:Int=630,height:Int=880):Bitmap? {
        if(points.size!=8 || points.any { !it.isFinite() } || points.filterIndexed { i,_->i%2==0 }.any { it<0 || it>bitmap.width } || points.filterIndexed { i,_->i%2==1 }.any { it<0 || it>bitmap.height }) return null
        val matrix=Matrix();if(!matrix.setPolyToPoly(points,0,floatArrayOf(0f,0f,width.toFloat(),0f,width.toFloat(),height.toFloat(),0f,height.toFloat()),0,4)) return null
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(bitmap,matrix,Paint(Paint.FILTER_BITMAP_FLAG)) }
    }
    private fun resolveVisual(id:String,token:Int,provisional:Boolean) {
        val lookupKey="$token:$id";if(resolvingKey==lookupKey) return
        if(SystemClock.elapsedRealtime()-lastLookupFailureAt<1000) return
        val started=System.nanoTime();resolvingKey=lookupKey
        trace.event("triple.candidatePending target=${id.hashCode()} provisional=$provisional ageMs=${SystemClock.elapsedRealtime()-sceneStartedAt}")
        val queued=lookup.lookup(id) { option ->
            if(resolvingKey==lookupKey) resolvingKey=""
            if(!valid(token,id)) { trace.event("triple.lookupDiscard stale=true");return@lookup }
            if(option==null) { lastLookupFailureAt=SystemClock.elapsedRealtime();status.setText(R.string.triple_metadata_missing);trace.event("triple.lookupMissing elapsedMs=${(System.nanoTime()-started)/1000000}");return@lookup }
            option.price?.let { prices=prices+((id+"|"+option.finish) to it) }
            val target=Target(id,option.printing,option.name,option.set,"${option.name} · ${option.set} #${option.number}",option.image,option.finish,"EdScan",provisional,option.rarity)
            accept(target)
            registerSessionTarget(target)
        }
        if(!queued) { resolvingKey="";trace.event("triple.lookupDeferred busy=true") }
    }
    private fun replaySession() {
        sessionTargets.values.forEach { t -> preview.addFallbackReference(ArPreloadCard(t.printing,t.image,t.label,price(t),t.rarity,t.set,t.number)) }
    }
    private fun registerSessionTarget(target:Target) {
        sessionTargets[target.printing]=target
        references=references+(target.printing to target)
        if(arEnabled && target.printing.isNotBlank() && target.image.isNotBlank()) preview.addFallbackReference(ArPreloadCard(target.printing,target.image,target.label,price(target),target.rarity,target.set,target.number))
    }
    private var lastOcrCanonical=""
    private var lastOcrNameAt=0L
    private fun proposeOcrName(name:String) {
        lastOcrCanonical=name;lastOcrNameAt=SystemClock.elapsedRealtime()
        val known=candidate?.takeIf { TripleScanPolicy.agrees(it.name,name) }
            ?: galleryEntries[currentEncounter.toString()]?.target?.takeIf { TripleScanPolicy.agrees(it.name,name) }
        if(known!=null) accept(known) else {
            val identity="ocr-name:"+TripleReadingPolicy.identity(name)
            frameKey=identity;accept(Target(identity,"",name,"",name,"","nonfoil","OCR",true))
        }
        galleryEntries[currentEncounter.toString()]?.takeIf { it.target.printing.isBlank() }?.let { resolveOcrEntry(it,sets.codes()) }
    }
    private fun resolveOcrEntry(entry:GalleryEntry,locked:Set<String>) {
        if(entry.resolvingEdition || entry.manualEdition || entry.count<=0) return
        entry.resolvingEdition=true;entry.saveError=false
        val revision=++entry.editionRevision
        val started=System.nanoTime()
        fun current()=!closed && entry.count>0 && !entry.manualEdition && entry.editionRevision==revision && entry.target.printing.isBlank()
        fun resolve(scope:Set<String>,fallback:Boolean) {
            CardRepository.get(this).quickScanCard(entry.target.name,scope,entry.selectedFinish=="foil") { option,error ->
                if(!current()) { entry.resolvingEdition=false;return@quickScanCard }
                trace.event("triple.ocrQuick event=${entry.root.tag} ms=${(System.nanoTime()-started)/1000000} found=${option!=null} failed=${error!=null} globalFallback=$fallback scope=${scope.sorted().joinToString(",")}")
                if(option!=null) {
                    entry.resolvingEdition=false
                    applyEditionToEntry(entry,option,false,fallback)
                } else if(scope.isNotEmpty()) {
                    entry.scopeConflict=true;bindGallery(entry);resolve(emptySet(),true)
                } else {
                    entry.resolvingEdition=false;entry.saveError=true;entry.saveSignature=""
                    bindGallery(entry);renderControls()
                }
            }
        }
        resolve(locked,false)
    }
    private fun applyEditionToEntry(entry:GalleryEntry,option:io.asv.mtgocr.ocrreader.data.CardEditionOption,manual:Boolean,outsideScope:Boolean=false) {
        if(entry.count<=0) return
        val revision=++entry.editionRevision
        if(manual) entry.manualEdition=true // Lock before asynchronous lookup, not after it.
        val printedName=entry.target.label.substringBefore(" · ")
        languageWorker.execute {
            val id=runCatching { io.asv.mtgocr.ocrreader.data.CardDatabase.get(applicationContext).cardDao()
                .printingsByUuids(listOf(option.printingUuid)).firstOrNull()?.scryfallId }.getOrNull()
            ui {
                if(closed || entry.count<=0 || entry.editionRevision!=revision) return@ui
                val target=Target(id?.takeIf { it.isNotBlank() } ?: "local:${option.printingUuid}",option.printingUuid,option.cardName,
                    option.setCode,"$printedName · ${option.setCode} #${option.collectorNumber}",option.imageUrl.orEmpty(),option.finish,
                    entry.target.source,!manual,option.rarity,option.collectorNumber)
                updateEntryTarget(entry,target)
                entry.editionOption=option;entry.scopeConflict=outsideScope;entry.resolvingEdition=false
                if(manual || !entry.manualFinish) entry.selectedFinish=option.finish
                if(manual) entry.manualFinish=true
                if(entry.languageOrigin=="printing") { entry.language=null;entry.languageOrigin="" }
                if(entry.root.tag.toString()==currentEncounter.toString()) {
                    printingLanguage=null;printingLanguageAttempt="";languageConflict=false
                    candidate=target;frameKey=target.id;observedLanguage=entry.language;evidenceKey=target.id
                    preview.setImageLanguage(target.printing,entry.language ?: "und")
                    renderResult()
                }
                registerSessionTarget(target);bindGallery(entry);saveEntry(entry);renderControls()
                trace.event("triple.editionApplied event=${entry.root.tag} manual=$manual newCopy=false outsideScope=$outsideScope")
            }
        }
    }
    private fun applyTitleLanguage(name:String,lines:List<String>,token:Int,event:Long,alias:String?=null,hint:String?=null) {
        val entry=galleryEntries[event.toString()] ?: return
        if(fixedLanguage!=null || entry.manualLanguage) return
        languageWorker.execute {
            val raw=titleLanguageIndex?.lookup(lines,listOf(name)) ?: TitleLanguageIndex.Evidence()
            val localized=alias?.let { titleLanguageIndex?.lookup(listOf(it),listOf(name)) } ?: TitleLanguageIndex.Evidence()
            val code=TripleReadingPolicy.titleLanguage(raw,localized,hint)
            ui {
                if(closed || token!=generation || fixedLanguage!=null || entry.count<=0 || !TripleScanPolicy.agrees(entry.target.name,name) || entry.manualLanguage) return@ui
                trace.event("triple.titleLanguage event=$event raw=${raw.candidates.sorted().joinToString(",")} alias=${localized.candidates.sorted().joinToString(",")} selected=${code ?: "und"}")
                if(code!=null) {
                    entry.language=code;entry.languageOrigin="title"
                    if(event==currentEncounter) {
                        languageConflict=languageConflict || TriplePrintingLanguagePolicy.conflicts(printingLanguage,code)
                        languageConsensus.confirmExactTitle(event.toString(),code);observedLanguage=code;evidenceKey=entry.target.id
                        identifiedLanguages[entry.target.id]=code;preview.setImageLanguage(entry.target.printing,code)
                        renderResult()
                    }
                    bindGallery(entry);saveEntry(entry)
                }
            }
        }
    }
    private val rarityRequested=mutableSetOf<String>()
    private fun accept(incoming:Target) {
        if(choosingLanguage) return
        val previous=galleryEntries[currentEncounter.toString()]
        val event=encounters.observe(TripleReadingPolicy.identity(incoming.name))
        val sameEvent=event==currentEncounter
        val target=if(sameEvent && previous!=null && TripleReadingPolicy.keepCurrent(previous.manualEdition,
                previous.target.printing,previous.target.provisional,incoming.printing,incoming.provisional)) previous.target else incoming
        val changedPrinting=sameEvent && previous!=null && previous.target.printing!=target.printing
        if(changedPrinting) {
            printingLanguage=null;printingLanguageAttempt="";languageConflict=false
            if(previous!!.languageOrigin=="printing") { previous.language=null;previous.languageOrigin="" }
            previous.editionOption=null;previous.editionRevision++;previous.resolvingEdition=false
            trace.event("triple.printingUpdated event=$event from=${previous.target.id.hashCode()} to=${target.id.hashCode()} newCopy=false")
        }
        if(event!=currentEncounter) {
            printingLanguage=null;printingLanguageAttempt="";languageConflict=false;photoCapturedFor=""
            currentEncounter=event;playRecognizedFeedback();ocrSequence++;languageConsensus.reset();observedLanguage=null;evidenceKey=""
            identifiedLanguages.remove(target.id);preview.setImageLanguage(target.printing,"und")
            trace.event("triple.encounter event=$event source=${target.source} sinceRunMs=${SystemClock.elapsedRealtime()-runStartedAt} ar=$arEnabled art=$artEnabled ocr=$ocrEnabled")
        }
        if(target.source=="AR") sessionTargets[target.printing]=target
        if(target.rarity.isBlank() && target.source=="AR" && target.id !in rarityRequested) {
            val token=generation
            rarityRequested.add(target.id)
            if(!lookup.lookup(target.id) { data ->
                if(token==generation && !closed && !data?.rarity.isNullOrBlank()) {
                    val rarity=data!!.rarity
                    references=references.mapValues { (_,t)->if(t.id==target.id) t.copy(rarity=rarity) else t }
                    if(candidate?.id==target.id) candidate=candidate?.copy(rarity=rarity)
                    preview.updateRarity(target.printing,rarity)
                }
            }) rarityRequested.remove(target.id)
        }

        candidate=target
        observedLanguage=if(sameEvent) previous?.language else null
        evidenceKey=target.id
        if(observedLanguage==null) identifiedLanguages.remove(target.id) else identifiedLanguages[target.id]=observedLanguage!!
        preview.setImageLanguage(target.printing,observedLanguage ?: "und")
        renderResult()
        if(lastDisplayedId!=target.id) {
            lastDisplayedId=target.id
            trace.event("triple.candidateShown source=${target.source} provisional=${target.provisional} languageKnown=${observedLanguage!=null} ageMs=${if(sceneStartedAt==0L) 0 else SystemClock.elapsedRealtime()-sceneStartedAt}")
        }
        identifiedLanguages[target.id]?.let { preview.setImageLanguage(target.printing,it) }
        upsertGallery(target)
        galleryEntries[currentEncounter.toString()]?.let { entry ->
            if(entry.photo==null) entry.photo=pendingPhotos.remove(target.id)
            saveEntry(entry)
        }
        galleryEntries[currentEncounter.toString()]?.takeIf { it.manualLanguage }?.let {
            observedLanguage=it.language;evidenceKey=target.id
            if(it.language==null) identifiedLanguages.remove(target.id) else identifiedLanguages[target.id]=it.language!!
            preview.setImageLanguage(target.printing,it.language ?: "und")
        }
        renderControls()
        renderResult()
        inferPrintingLanguage(target)
    }
    private fun inferPrintingLanguage(target:Target) {
        if(galleryEntries[currentEncounter.toString()]?.manualLanguage==true) return
        if(!TriplePrintingLanguagePolicy.eligible(target.source,target.provisional,target.printing,target.set,target.number)) return
        val token=generation;val event=currentEncounter
        val attempt="$token:$event:${target.printing}"
        if(printingLanguageAttempt==attempt) return
        printingLanguageAttempt=attempt
        printingLanguages.resolve(target.set,target.number) { code ->
            val current=candidate
            if(code==null || galleryEntries[currentEncounter.toString()]?.manualLanguage==true || closed || !resumed || token!=generation || event!=currentEncounter || current?.printing!=target.printing || current.provisional) return@resolve
            printingLanguage=code
            // Keep physical OCR evidence when it arrived first; the edition is then questionable.
            languageConflict=languageConflict || TriplePrintingLanguagePolicy.conflicts(code,observedLanguage)
            if(!languageConflict && galleryEntries[event.toString()]?.languageOrigin !in setOf("title","rules")) {
                observedLanguage=code;evidenceKey=target.id
                rememberLanguage(current,code,"printing")
            }
            trace.event("triple.printingLanguageApplied event=$event language=$code conflict=$languageConflict")
            renderResult()
        }
    }
    private fun clearCurrentLanguage() {
        printingLanguage=null;printingLanguageAttempt="";pendingPhotos.clear()
        if(::cardControls.isInitialized) cardControls.visibility=View.GONE
        candidate?.let { current ->
            // Historical gallery keeps the last confirmed observation; a new encounter clears its row in accept().
            preview.setImageLanguage(current.printing,"und")
        }
        observedLanguage=null;evidenceKey="";languageConflict=false
    }
    private fun rememberLanguage(target:Target,code:String,origin:String="rules") {
        if(code.isBlank() || code=="und") return
        val entry=galleryEntries[currentEncounter.toString()]
        if(entry?.manualLanguage==true) return
        identifiedLanguages[target.id]=code
        if(target.printing.isNotBlank()) preview.setImageLanguage(target.printing,code)
        galleryEntries[currentEncounter.toString()]?.takeIf { it.target.id==target.id }?.let { it.language=code;it.languageOrigin=origin;bindGallery(it);saveEntry(it);renderControls() }
    }
    private fun upsertGallery(target:Target) {
        val entryKey=currentEncounter.toString()
        val previous=galleryEntries[entryKey]
        if(previous!=null) {
            updateEntryTarget(previous,target)
            if(previous.count==0) return
            bindGallery(previous);return
        }
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(4),dp(3),dp(4),dp(3));tag=entryKey
        }
        val picture=FrameLayout(this)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER }
        picture.addView(image,FrameLayout.LayoutParams(dp(52),dp(64),android.view.Gravity.CENTER))
        val flag=TextView(this).apply {
            textSize=11f;setTextColor(Color.WHITE);setBackgroundColor(0xCC151820.toInt());setPadding(dp(3),0,dp(3),0)
            tag="triple_gallery_language"
        }
        picture.addView(flag,FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP or android.view.Gravity.END))
        column.addView(picture,LinearLayout.LayoutParams(-1,dp(64)))
        fun line(tagName:String)=TextView(this).apply {
            textSize=12f;gravity=android.view.Gravity.CENTER;maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END;tag=tagName
            column.addView(this,LinearLayout.LayoutParams(-1,dp(18)))
        }
        val edition=line("triple_gallery_edition")
        val amount=line("triple_gallery_price").apply { setTypeface(typeface,Typeface.BOLD) }
        val entry=GalleryEntry(target,column,image,flag,edition,amount,
            language=fixedLanguage,manualLanguage=fixedLanguage!=null,languageOrigin=if(fixedLanguage!=null) "scope" else "")
        galleryEntries[entryKey]=entry
        if(target.image.isBlank()) image.setImageResource(android.R.drawable.ic_menu_gallery)
        else com.bumptech.glide.Glide.with(this).load(target.image).placeholder(CardLoadingDrawable()).into(image)
        column.setOnClickListener { showEntryMenu(entry,column,true) }
        recent.addView(column,0,LinearLayout.LayoutParams(dp(104),-1))
        if(recent.childCount>20) {
            val old=recent.getChildAt(20)
            galleryEntries.remove(old.tag as String)?.let { com.bumptech.glide.Glide.clear(it.image) }
            recent.removeViewAt(20)
        }
        bindGallery(entry)
    }
    private fun updateEntryTarget(entry:GalleryEntry,target:Target) {
        val changed=entry.target.image!=target.image
        if(entry.target.printing!=target.printing) {
            entry.amount=null;entry.priceRequested=""
            if(!entry.manualFinish) entry.selectedFinish=target.finish
        }
        entry.target=target
        if(changed) {
            com.bumptech.glide.Glide.clear(entry.image)
            if(target.image.isBlank()) entry.image.setImageResource(android.R.drawable.ic_menu_gallery)
            else com.bumptech.glide.Glide.with(this).load(target.image).placeholder(CardLoadingDrawable()).into(entry.image)
        }
    }
    private fun bindGallery(entry:GalleryEntry) {
        val t=entry.target
        entry.flag.text=TripleLanguageBadge.label(entry.language)
        entry.flag.contentDescription=getString(R.string.triple_language,entry.flag.text)
        entry.edition.text=(if(entry.saveError) "! " else if(entry.savedId==null) "… " else "✓ ")+"${entry.count}× · "+if(t.set.isBlank()) t.label else t.set.uppercase()+if(t.number.isNotBlank()) " #${t.number}" else ""
        entry.edition.contentDescription=t.label
        if(entry.scopeConflict) entry.edition.text="⚠ "+entry.edition.text
        entry.price.text=if(t.printing.isBlank()) getString(R.string.triple_edition_pending) else (entry.amount ?: prices[t.id+"|"+entry.selectedFinish])?.let { "%.2f %s".format(it.first,it.second) } ?: getString(R.string.triple_price_pending)
        entry.price.contentDescription=price(t)
        entry.root.alpha=if(entry.saveError) .55f else 1f
        entry.root.contentDescription=t.label+" · "+getString(if(entry.saveError) R.string.triple_save_failed else if(entry.savedId==null) R.string.triple_save_pending else R.string.triple_saved)
    }
    private fun saveEntry(entry:GalleryEntry) {
        val event=entry.root.tag.toString().toLongOrNull() ?: return
        val signature=listOf(entry.target.id,entry.target.provisional,entry.target.source,entry.language,
            entry.manualLanguage,entry.languageOrigin,entry.manualEdition,entry.selectedFinish,entry.count,entry.photo!=null).joinToString("|")
        if(entry.saveSignature==signature || (entry.count>0 && entry.target.printing.isBlank())) return
        entry.saveSignature=signature;entry.saveError=false
        val state=TripleCollectionSession.State(event,entry.target,entry.language,entry.selectedFinish,entry.count,entry.photo,entry.manualLanguage,entry.photoKey,entry.editionOption,entry.languageOrigin,entry.manualEdition)
        collectionSession.submit(state) { card,option,error ->
            if(closed || entry.saveSignature!=signature) return@submit
            entry.saveError=error!=null
            if(error!=null) {
                trace.event("triple.saveError event=$event type=${error.javaClass.simpleName}")
                Toast.makeText(this,R.string.triple_save_failed,Toast.LENGTH_LONG).show()
            }
            card?.let {
                entry.savedId=it.collectionItemId;addedCollectionIds.add(it.collectionItemId)
                setResult(RESULT_OK,Intent().putStringArrayListExtra(RapidEditionScanActivity.EXTRA_SESSION_CARD_IDS,ArrayList(addedCollectionIds)))
            }
            option?.let {
                val key=entry.target.id+"|"+entry.selectedFinish
                entry.amount=it.price?.let { amount -> amount to it.currency.orEmpty() } ?: entry.amount ?: prices[key]
                entry.amount?.let { amount->prices=prices+(key to amount) }
                if(entry.amount==null) requestMissingPrice(entry,it)
                preview.refreshSessionCard(ArPreloadCard(entry.target.printing,entry.target.image,entry.target.label,
                    price(entry.target.copy(finish=entry.selectedFinish)),entry.target.rarity,entry.target.set,entry.target.number))
            }
            bindGallery(entry);if(event==currentEncounter) { renderControls();renderResult() }
        }
    }
    private fun requestMissingPrice(entry:GalleryEntry,option:io.asv.mtgocr.ocrreader.data.CardEditionOption) {
        val key=option.printingUuid+"|"+entry.selectedFinish
        if(entry.priceRequested==key || entry.count<=0) return
        entry.priceRequested=key
        val id=entry.target.id;val printing=entry.target.printing;val finish=entry.selectedFinish
        val started=System.nanoTime()
        CardRepository.get(this).loadScannerPrice(option.printingUuid,finish) { priced,error ->
            trace.event("triple.priceFallback event=${entry.root.tag} uuid=${option.printingUuid} finish=$finish ms=${(System.nanoTime()-started)/1000000} found=${priced?.price!=null} failed=${error!=null}")
            if(closed || entry.count<=0 || entry.target.id!=id || entry.target.printing!=printing || entry.selectedFinish!=finish) return@loadScannerPrice
            if(priced?.price!=null) {
                entry.editionOption=priced
                entry.amount=priced.price to priced.currency.orEmpty()
                prices=prices+((id+"|"+finish) to requireNotNull(entry.amount))
                entry.saveSignature="";saveEntry(entry);bindGallery(entry)
                if(entry.root.tag.toString()==currentEncounter.toString()) renderResult()
            }
        }
    }
    private fun renderControls() {
        if(!::cardControls.isInitialized) return
        val entry=galleryEntries[currentEncounter.toString()]?.takeIf { it.count>0 && candidate?.id==it.target.id }
        cardControls.visibility=if(entry==null) View.GONE else View.VISIBLE
        if(entry==null) return
        val key="${currentEncounter}|${entry.language}|${entry.manualLanguage}|${entry.selectedFinish}|${entry.saveError}|${entry.savedId}|${entry.target.printing}|${entry.manualEdition}|${entry.scopeConflict}"
        if(controlsKey==key) return
        controlsKey=key;cardControls.removeAllViews()
        addEntryControls(entry,cardControls,false) {}
    }
    private fun showEntryMenu(entry:GalleryEntry,anchor:View,history:Boolean) {
        if(entry.count<=0) return
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(0xFF202430.toInt()) }
        val popup=PopupWindow(body,dp(300),-2,true).apply { elevation=dp(8).toFloat();setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true }
        body.addView(TextView(this).apply { text=entry.target.label;setTextColor(Color.WHITE) })
        addEntryControls(entry,body,history) { popup.dismiss() }
        popup.showAsDropDown(anchor)
    }
    private fun addEntryControls(entry:GalleryEntry,body:LinearLayout,history:Boolean,dismiss:()->Unit) {
        body.addView(Button(this).apply {
            text=if(entry.target.set.isBlank()) getString(R.string.triple_edition_pending) else entry.target.set.uppercase()+" #"+entry.target.number
            contentDescription=getString(R.string.editions);tag="triple_edit_edition"
            setOnClickListener {
                dismiss()
                EditionPicker.showGrid(this@TripleScanActivity,entry.target.name,entry.selectedFinish) { option ->
                    if(entry.count>0) applyEditionToEntry(entry,option,true)
                }
            }
        })
        if(history && entry.scopeConflict) body.addView(TextView(this).apply { setText(R.string.triple_outside_scope);setTextColor(Color.YELLOW) })
        body.addView(CheckBox(this).apply {
            setText(R.string.foil);setTextColor(Color.WHITE);buttonTintList=android.content.res.ColorStateList.valueOf(Color.CYAN);isChecked=entry.selectedFinish=="foil";tag="triple_edit_foil"
            setOnCheckedChangeListener { _,value ->
                entry.manualFinish=true;entry.selectedFinish=if(value) "foil" else "nonfoil";entry.amount=null
                preview.refreshSessionCard(ArPreloadCard(entry.target.printing,entry.target.image,entry.target.label,price(entry.target.copy(finish=entry.selectedFinish)),entry.target.rarity,entry.target.set,entry.target.number))
                saveEntry(entry);bindGallery(entry);dismiss();renderControls()
            }
        })
        if(entry.language.isNullOrBlank() || entry.manualLanguage || history) body.addView(Button(this).apply {
            text=getString(R.string.review_language)+" · "+TripleLanguageBadge.label(entry.language);tag="triple_edit_language"
            setOnClickListener {
                val codes=listOf("","es","pt","en","fr","de","it","ja","ko","ru","zhs","zht")
                androidx.appcompat.app.AlertDialog.Builder(this@TripleScanActivity).setTitle(R.string.review_choose_language)
                    .setItems(codes.map { if(it.isBlank()) getString(R.string.review_language_unknown) else TripleLanguageBadge.label(it) }.toTypedArray()) { _,index ->
                        entry.language=codes[index].ifBlank { null };entry.manualLanguage=true;entry.languageOrigin="manual"
                        if(entry.language==null) identifiedLanguages.remove(entry.target.id) else identifiedLanguages[entry.target.id]=entry.language!!
                        if(entry.root.tag.toString()==currentEncounter.toString()) {
                            observedLanguage=entry.language;languageConsensus.reset();languageConflict=false;evidenceKey=entry.target.id
                            preview.setImageLanguage(entry.target.printing,entry.language ?: "und")
                        }
                        saveEntry(entry);bindGallery(entry);dismiss();renderControls()
                    }.show()
            }
        })
        if(history) {
            val row=LinearLayout(this)
            fun button(label:String,action:()->Unit) { row.addView(Button(this).apply { text=label;setOnClickListener { action() } },LinearLayout.LayoutParams(0,-2,1f)) }
            fun remove() { androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.recent_delete_confirm)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_->
                    entry.count=0;saveEntry(entry);recent.removeView(entry.root);dismiss();renderControls()
                }.show() }
            button("−") { if(entry.count<=1) remove() else { entry.count--;saveEntry(entry);bindGallery(entry);dismiss() } }
            row.addView(TextView(this).apply { text=entry.count.toString();setTextColor(Color.WHITE) })
            button("+") { if(entry.count<99) { entry.count++;saveEntry(entry);bindGallery(entry);dismiss() } }
            button("🗑") { remove() };body.addView(row)
        }
        if(entry.saveError) body.addView(Button(this).apply { setText(R.string.triple_retry_save);setOnClickListener { entry.saveSignature="";if(entry.target.printing.isBlank()) resolveOcrEntry(entry,sets.codes()) else saveEntry(entry);dismiss() } })
    }
    private fun refreshGallery() { galleryEntries.values.forEach { bindGallery(it) } }
    private fun price(target:Target)=prices[target.id+"|"+target.finish]?.let { "%.2f %s · %s".format(it.first,it.second,target.finish) } ?: getString(R.string.triple_price_pending)
    private fun renderResult() {
        val candidateTarget=candidate ?: return
        val t=candidateTarget.copy(finish=galleryEntries[currentEncounter.toString()]?.selectedFinish ?: candidateTarget.finish)
        renderControls()
        val conflict=languageConflict || galleryEntries[currentEncounter.toString()]?.scopeConflict==true || (allowedSets.isNotEmpty() && t.set.uppercase() !in allowedSets) || (observedName!=null && !TripleScanPolicy.agrees(t.name,observedName!!))
        val pending=if(t.provisional) getString(R.string.triple_provisional)+"\n" else ""
        result.text=pending+"${t.source} · ${t.label}\n${price(t)}\n"+getString(if(conflict) R.string.triple_conflict else R.string.triple_language,observedLanguage ?: "?")
    }
    private fun ocr(bitmap:Bitmap?,target:Target?,token:Int,key:String=target?.id ?: "ocr") {
        val now=SystemClock.elapsedRealtime()
        val reviewPhoto=if(key=="ocr") pendingPhotos.remove(key) else pendingPhotos[key]
        if(!ocrEnabled || bitmap==null || !valid(token,key) || (key==lastOcrKey && now-lastOcrAt<TripleScanPolicy.OCR_INTERVAL_MS)) { bitmap?.recycle();busy.set(false);return }
        if(!ocrScheduled.compareAndSet(false,true)) { bitmap.recycle();busy.set(false);return }
        lastOcrKey=key;lastOcrAt=now;val started=System.nanoTime();val sequence=++ocrSequence
        // Identification by AR/ART is enough when language was explicitly set. Photo was captured earlier.
        val entry=galleryEntries[currentEncounter.toString()]
        if(target!=null && entry?.manualLanguage==true) {
            trace.event("triple.ocrSkipped event=$currentEncounter reason=fixedLanguage")
            bitmap.recycle();finishOcr();busy.set(false);return
        }
        val scopeLanguage=fixedLanguage
        text.process(InputImage.fromBitmap(bitmap,0),scopeLanguage).addOnSuccessListener { recognized ->
            bitmap.recycle();ocrCompleted++;EdScanPerf.record("triple.ocr",System.nanoTime()-started)
            if(!valid(token,key)) { finishOcr();return@addOnSuccessListener }
            val lines=recognized.textBlocks.flatMap { it.lines }
            val titles=lines.filter { (it.boundingBox?.centerY() ?: 9999)<170 }.map { it.text }
            val rules=lines.filter { (it.boundingBox?.centerY() ?: 0) in 510..800 }.joinToString(" ") { it.text }
            evidenceKey=key
            observedName=null
            val direct=target?.name?.takeIf { expected->titles.any { TripleScanPolicy.agrees(expected,it) } }
            if(direct!=null) observedName=direct
            else if(!nameLookupPending && titles.isNotEmpty()) {
                nameLookupPending=true;val titleStarted=System.nanoTime()
                CardRepository.get(this).matchLocalOcrText(titles) { name ->
                    nameLookupPending=false;EdScanPerf.record("triple.titleMatch",System.nanoTime()-titleStarted)
                    if(valid(token,key) && sequence==ocrSequence && SystemClock.elapsedRealtime()-now<2000) {
                        observedName=name?.canonicalName
                        name?.let { proposeOcrName(it.canonicalName)
                            applyTitleLanguage(it.canonicalName,titles,token,currentEncounter,it.displayName,it.language)
                            if(reviewPhoto!=null) galleryEntries[currentEncounter.toString()]?.takeIf { row -> TripleScanPolicy.agrees(row.target.name,it.canonicalName) && row.photo==null }?.let { row->row.photo=reviewPhoto;saveEntry(row) }
                        }
                        if(candidate?.id==key) renderResult()
                        else if(key=="ocr" && name!=null) result.text=getString(R.string.triple_ocr_only,name.displayName,observedLanguage ?: "?")
                    }
                }
            }
            if(scopeLanguage!=null) {
                trace.event("triple.languageSkipped reason=fixedScope language=$scopeLanguage")
                finishOcr();return@addOnSuccessListener
            }
            val langStart=System.nanoTime()
            val canonical=target?.name ?: candidate?.takeIf { it.id==key }?.name ?: observedName
            languages.detectText(rules,"und") { language ->
                // Exact localized-title lookup stays off the UI/camera worker and keeps ES/PT alias ambiguity.
                if(closed) { finishOcr();return@detectText }
                languageWorker.execute {
                    val title=canonical?.let { titleLanguageIndex?.lookup(titles,listOf(it)) } ?: TitleLanguageIndex.Evidence()
                    val decision=TripleLanguageFusion.decide(rules,
                        TripleScanPolicy.language(language.languageCode,language.confidence),title)
                    runOnUiThread {
                        EdScanPerf.record("triple.language",System.nanoTime()-langStart)
                        if(valid(token,key) && sequence==ocrSequence) {
                            val entry=galleryEntries[currentEncounter.toString()]
                            languageConflict=languageConflict || decision.conflict || TriplePrintingLanguagePolicy.conflicts(printingLanguage,decision.code)
                            if(entry?.manualLanguage!=true && entry?.languageOrigin!="title") {
                                val confirmed=if(decision.source=="exactTitle" && decision.code!=null) {
                                    languageConsensus.confirmExactTitle(currentEncounter.toString(),decision.code);decision.code
                                } else languageConsensus.observe(currentEncounter.toString(),decision.code)
                                if(confirmed!=null) {
                                    observedLanguage=confirmed
                                    candidate?.takeIf { it.id==key }?.let { rememberLanguage(it,confirmed,if(decision.source=="exactTitle") "title" else "rules") }
                                }
                            }
                            trace.event("triple.languageEvidence source=${decision.source} proposed=${decision.code ?: "und"} confirmed=${observedLanguage ?: "und"} conflict=${decision.conflict} titleCandidates=${title.candidates.sorted().joinToString(",")} confidence=${language.confidence} letters=${rules.count(Char::isLetter)} snapshot=true")
                            if(candidate?.id==key) renderResult()
                            else if(key=="ocr" && observedName!=null) result.text=getString(R.string.triple_ocr_only,observedName!!,observedLanguage ?: "?")
                            trace.event("triple.ocrComplete elapsedMs=${(System.nanoTime()-started)/1000000} languageConfirmed=${observedLanguage!=null} titleMatched=${observedName!=null}")
                        };finishOcr()
                    }
                }
            }
        }.addOnFailureListener { bitmap.recycle();android.util.Log.w("EdScanPerf","Triple OCR failed",it);finishOcr() }
        busy.set(false)
    }
    @Volatile private var lastGuideAt=0L
    private var guideLookupPending=false
    private var guideSequence=0
    @JvmOverloads private fun guidedOcr(bitmap:Bitmap?,token:Int,photo:ByteArray?=null) {
        if(bitmap==null) return
        if(!ocrEnabled || closed || !resumed || token!=generation || !ocrScheduled.compareAndSet(false,true)) { bitmap.recycle();return }
        val started=SystemClock.elapsedRealtime();val sequence=++guideSequence
        trace.event("triple.guideStart sequence=$sequence")
        fun valid()=!closed && resumed && ocrEnabled && token==generation && sequence==guideSequence && SystemClock.elapsedRealtime()-started<5000
        fun finish() { bitmap.recycle();finishOcr() }
        fun attempt(latinOnly:Boolean) {
            val task=if(latinOnly) text.processLatin(InputImage.fromBitmap(bitmap,0)) else text.process(InputImage.fromBitmap(bitmap,0),fixedLanguage)
            task.addOnSuccessListener { reading ->
                if(!valid()) { finish();return@addOnSuccessListener }
                val lines=reading.textBlocks.flatMap { it.lines }.map { it.text }.filter { it.length>=2 }
                trace.event("triple.guideOcr ms=${SystemClock.elapsedRealtime()-started} lines=${lines.size} latinOnly=$latinOnly")
                fun noMatch() {
                    if(latinOnly && valid() && fixedLanguage==null) attempt(false)
                    else { if(!artEnabled && SystemClock.elapsedRealtime()-lastArSeen>1000) encounters.missing(SystemClock.elapsedRealtime());lastOcrCanonical="";preview.showGuidedName(null);finish() }
                }
                if(lines.isEmpty()) { noMatch();return@addOnSuccessListener }
                guideLookupPending=true;val lookupStart=SystemClock.elapsedRealtime()
                CardRepository.get(this).matchLocalOcrText(lines) { name ->
                    guideLookupPending=false
                    trace.event("triple.guideLookup ms=${SystemClock.elapsedRealtime()-lookupStart} matched=${name!=null} valid=${valid()} latinOnly=$latinOnly")
                    if(!valid()) { finish();return@matchLocalOcrText }
                    if(name==null) noMatch() else {
                        preview.showGuidedName(name.displayName)
                        proposeOcrName(name.canonicalName)
                        if(photo!=null) galleryEntries[currentEncounter.toString()]?.takeIf { TripleScanPolicy.agrees(it.target.name,name.canonicalName) && it.photo==null }?.let { it.photo=photo;saveEntry(it) }
                        candidate?.takeIf { it.printing.isBlank() }?.let { current ->
                            val localized=current.copy(label=name.displayName)
                            candidate=localized;upsertGallery(localized);renderResult()
                        }
                        applyTitleLanguage(name.canonicalName,lines,token,currentEncounter,name.displayName,name.language)
                        trace.event("triple.ocrIdentityReady ms=${SystemClock.elapsedRealtime()-started} event=$currentEncounter latinOnly=$latinOnly")
                        finish()
                    }
                }
            }.addOnFailureListener {
                if(latinOnly && valid() && fixedLanguage==null) attempt(false)
                else { finish();trace.event("triple.guideOcr failed=${it.javaClass.simpleName}") }
            }
        }
        attempt(fixedLanguage==null || TripleLanguageScope.model(fixedLanguage)=="latin")
    }
    private fun playRecognizedFeedback() {
        feedbackCount++
        try {
            if(scanTone==null) scanTone=android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,90)
            val played=scanTone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,95)==true
            trace.event("triple.feedback event=$currentEncounter beep=$played")
        } catch(error:RuntimeException) { android.util.Log.w("EdScanPerf","Recognition beep unavailable",error) }
        recent.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
    private fun finishOcr() { ocrScheduled.set(false);if(closed) { text.close();languages.close() } }
    override fun onResume() { super.onResume();io.asv.collectorvision.EdScanInferencePriority.acquire(this);resumed=true;logHandler.removeCallbacks(heartbeat);logHandler.post(heartbeat); }
    override fun onPause() { io.asv.collectorvision.EdScanInferencePriority.release(this);resumed=false;logHandler.removeCallbacks(heartbeat);invalidateEvidence();super.onPause() }
    override fun onDestroy() {
        languagePickerRevision++;languagePicker?.dismiss()
        if(setLanguagesLazy.isInitialized()) setLanguages.close()
        if(printingLanguagesLazy.isInitialized()) printingLanguages.close()
        closed=true;generation++;scanTone?.release();scanTone=null;preview.close();lookup.close();trace.close()
        worker.execute { engine?.close();engine=null };worker.shutdown();languageWorker.shutdown()
        if(!ocrScheduled.get()) { text.close();languages.close() };super.onDestroy()
    }
}
