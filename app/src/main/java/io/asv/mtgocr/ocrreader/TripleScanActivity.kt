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
    private lateinit var sessionTotal:TextView
    private val totals=TripleSessionTotals()
    private val totalPriceInputs=linkedMapOf<Long,PriceOriginPolicy.Input>()
    @Volatile private var restoringSession=false
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
    private var hybridIndex:io.asv.collectorvision.FastRegionIndex?=null
    private var hybridPolicy=TripleCropTrialPolicy() // worker only, never authorizes a save
    @Volatile private var singleArtReading=false
    @Volatile private var delayedArtCrop=false
    @Volatile private var uprightOnly=false
    private var hybridProfile=io.asv.collectorvision.FastRegion.ART
    @Volatile private var visualFrames=0
    @Volatile private var ocrCompleted=0
    @Volatile private var nameIndexReady=false
    private val text=TripleSnapshotOcr()
    private val languages=CardTextLanguageDetector(useShortRules=false)
    private val languageConsensus=TripleLanguageConsensus()
    private val languageCopies=TripleLanguageCopyPolicy()
    private val photoLanguageLazy=lazy { TriplePhotoLanguageReader({ titleLanguageIndex },trace::event) }
    private val photoLanguage by photoLanguageLazy
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
    private lateinit var edscanContour:EdScanContourView
    @Volatile private var contourEnabled=false
    @Volatile private var contourRevision=0
    private var repeatIdentity=""
    @Volatile private var repeatNeedsTap=false
    private lateinit var rescanButton:Button
    @Volatile private var resumed=false
    @Volatile private var closed=false
    @Volatile private var generation=0
    @Volatile private var references=emptyMap<String,Target>()
    @Volatile private var arEnabled=true
    @Volatile private var artEnabled=true
    @Volatile private var ocrEnabled=true
    private val encounters=TripleEncounterGate()
    private val arbiter=TripleRecognitionArbiter()
    private data class VisualProposal(val target:Target,val at:Long,val scene:Long)
    private val proposedVisualTargets=linkedMapOf<String,VisualProposal>()
    private var workerGeneration=-1
    private var visualConfirmation=TripleIdentityConfirmation() // worker only
    private val frameScheduler=TripleFrameScheduler() // worker only
    private val adaptiveShadow=TripleAdaptiveVisualPolicy() // never authorizes writes
    private data class SlowFrame(val scan:io.asv.collectorvision.ScanResult,val signature:FloatArray?,val started:Long,
        val scope:Set<String>?,val token:Int,val scene:Long)
    private var slowFrame:SlowFrame?=null // worker only, at most one; no retained bitmap/image
    @Volatile private var nextFrameAt=0L
    @Volatile private var liveArTarget:Target?=null
    private val arConsensus=TripleArConsensus()
    @Volatile private var lastAcceptedVisualId=""
    @Volatile private var confirmationPending=true
    @Volatile private var visualScope: Set<String>?=emptySet()
    @Volatile private var visualScopeError=false
    private var visualScopeRevision=0
    private var lastArbitrationLog=""
    private var lastAcceptedAt=0L
    private var currentEncounter=0L
    private var acceptedCaptureAt=0L
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
    private var packageVisualIds=emptySet<String>()
    private data class GalleryEntry(var target:Target,val root:LinearLayout,val image:ImageView,val flag:TextView,val edition:TextView,val price:TextView,var language:String?=null,
        var manualLanguage:Boolean=false,var languageOrigin:String="",var manualEdition:Boolean=false,
        var physicalLanguage:String?=null,var languageMismatch:Boolean=false,
        var editionOption:io.asv.mtgocr.ocrreader.data.CardEditionOption?=null,var editionRevision:Int=0,var resolvingEdition:Boolean=false,var scopeConflict:Boolean=false,
        var recognitionConflict:Boolean=false,var manualFinish:Boolean=false,var selectedFinish:String="nonfoil",var count:Int=1,
        var photo:ByteArray?=null,var photoKey:String=java.util.UUID.randomUUID().toString(),
        var savedId:String?=null,var saveSignature:String="",var saveError:Boolean=false,var finishUnavailable:Boolean=false,
        var amount:Pair<Double,String>?=null,var priceRequested:String="",var pricePrinting:String="",
        var languagePending:Boolean=false,var languageAttempted:Boolean=false,var languageSpinner:ProgressBar?=null,
        var foilBadge:ImageView?=null,var faceToggle:CardFaceToggle?=null)
    private val galleryEntries=linkedMapOf<String,GalleryEntry>()
    private val identifiedLanguages=mutableMapOf<String,String>()
    private val logHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeat=object:Runnable {
        override fun run() {
            if(!resumed || closed) return
            val power=getSystemService(POWER_SERVICE) as android.os.PowerManager
            val thermal=if(android.os.Build.VERSION.SDK_INT>=29) power.currentThermalStatus else -1
            val runtime=Runtime.getRuntime()
            trace.event("triple.health visualFrames=$visualFrames ocrCompleted=$ocrCompleted busy=${busy.get()} heapMb=${(runtime.totalMemory()-runtime.freeMemory())/1048576} powerSave=${power.isPowerSaveMode} thermal=$thermal ocrBusy=${ocrScheduled.get()} traceDropped=${trace.droppedEvents}")
            logHandler.postDelayed(this,5000)
        }
    }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(::preview.isInitialized) preview.retryStart() }
    private val picker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if(response.resultCode==RESULT_OK) response.data?.getStringExtra("package_id")?.let { input.setText(it);configureEdition() }
    }
    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state);EdScanPerf.enabled=BuildConfig.DEBUG
        val resumePrefs=getSharedPreferences("triple_resume_once",0)
        if(!intent.hasExtra("triple_resume_group") && resumePrefs.getString("library",null)==LibraryCatalog.activeFile(this)) {
            resumePrefs.getString("group",null)?.let { group ->
                intent.putExtra("triple_resume_group",group).putExtra("triple_resume_library",LibraryCatalog.activeFile(this))
            }
        }
        restoringSession=intent.hasExtra("triple_resume_group")
        trace=TripleScanTrace(applicationContext);collectionSession=TripleCollectionSession(applicationContext,trace::event);lookup=TripleCardLookup(applicationContext,trace::event);trace.event("triple.session v=21 priorityFollowUp=true soloArtRestMs=150 tapForConsecutiveRepeat=true photoLanguage=true identityConfirmation=true score=${TripleScanPolicy.VISUAL_MIN_SCORE} margin=${TripleScanPolicy.VISUAL_MIN_MARGIN} strongReadings=${TripleScanPolicy.VISUAL_STRONG_READINGS} copyIntervalMs=600 independentAr=true");runStartedAt=SystemClock.elapsedRealtime()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val toolbar=LinearLayout(this)
        val options=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(this).apply { addView(options);visibility=View.GONE }
        toolbar.addView(Button(this).apply { setText(R.string.triple_title);setOnClickListener { scroll.visibility=if(scroll.visibility==View.GONE) View.VISIBLE else View.GONE } },LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(Button(this).apply { setText(android.R.string.cancel);setOnClickListener { finish() } })
        root.addView(toolbar)
        root.addView(Button(this).apply {
            tag="triple_fast_mode";setText(R.string.edfast_open)
            setOnClickListener { startActivity(Intent(this@TripleScanActivity,EdScanFastActivity::class.java)) }
        })
        val scopeRow=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4)) }
        scopeSymbols=LinearLayout(this).apply { tag="triple_scope_symbols" };scopeRow.addView(scopeSymbols)
        scopeStatus=TextView(this).apply { setPadding(dp(6),0,0,0);tag="triple_scope" }
        scopeRow.addView(scopeStatus,LinearLayout.LayoutParams(0,-2,1f));root.addView(scopeRow)
        val summaryRow=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL }
        val languageShortcut=Button(this).apply {
            tag="triple_language_shortcut";isAllCaps=false
            setOnClickListener { showScopeLanguagePicker() }
        }
        summaryRow.addView(languageShortcut,LinearLayout.LayoutParams(0,-2,1f))
        sessionTotal=TextView(this).apply { tag="triple_session_total";gravity=android.view.Gravity.END;textSize=16f
            setTypeface(typeface,Typeface.BOLD);setPadding(dp(8),0,dp(12),0) }
        sessionTotal.setOnClickListener {
            PriceDetailsDialog.showInputs(this, totalPriceInputs.values.toList())
        }
        summaryRow.addView(sessionTotal,LinearLayout.LayoutParams(0,-2,1f));root.addView(summaryRow)
        renderSessionTotal()
        val gallery=HorizontalScrollView(this)
        recent=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;tag="triple_recent" }
        gallery.addView(recent);root.addView(gallery,LinearLayout.LayoutParams(-1,dp(112)))
        status=scannerMessage("triple_status",R.string.triple_loading,R.string.triple_ready);root.addView(status)
        result=scannerMessage("triple_result",R.string.triple_waiting,R.string.triple_waiting);root.addView(result)
        preview=TripleArPreview(this,Intent(),{ resumed && !closed && !choosingLanguage && !restoringSession && (arEnabled || artEnabled || ocrEnabled) && !busy.get() && !ocrScheduled.get() && SystemClock.elapsedRealtime()>=nextFrameAt && SystemClock.elapsedRealtime()-lastFrameAt>=TripleScanPolicy.FRAME_INTERVAL_MS },::frame,options)
        text.diagnostics=trace::event
        preview.diagnostics=trace::event
        preview.onRecognition={ printing ->
            if(arEnabled && resumed && !closed && !choosingLanguage) {
                val tracked=printing?.let { references[it] }
                if(tracked!=null && tracked.id!=liveArTarget?.id && tracked.id!=lastAcceptedVisualId) {
                    nextFrameAt=0;confirmationPending=true
                }
                liveArTarget=tracked
                arConsensus.observe(tracked?.id,SystemClock.elapsedRealtime())
                if(printing!=null) {
                    val now=SystemClock.elapsedRealtime();lastArSeen=now;arbiter.present()
                    if(now>=preferGlobalUntil) references[printing]?.let { target ->
                        offer(target.copy(source="AR"),now)
                    }
                } else if(!artEnabled && !ocrEnabled) arbiter.missing(SystemClock.elapsedRealtime())
            }
        }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        edscanContour=EdScanContourView(this).apply { tag="triple_edscan_contour" }
        preview.addView(edscanContour,FrameLayout.LayoutParams(-1,-1))
        rescanButton=Button(this).apply {
            tag="triple_rescan";setText(R.string.triple_tap_to_scan);visibility=View.GONE
            setOnClickListener { rearmScan() }
        }
        preview.addView(rescanButton,FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP))
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
        sets=ScannerSetInput(this,input,"triple_scanner",Runnable { configureEdition() },Runnable { visualScopeRevision++;visualScope=emptySet();invalidateEvidence();if(languageScopeKey!=TripleLanguageScope.key(sets.codes())) { fixedLanguage=null;languagePickerRevision++;languagePicker?.dismiss();getSharedPreferences("triple_scanner",0).edit().remove("scope_language").apply() };updateScopeStatus();references=sessionTargets.toMap();activePackage=false;packageVisualIds=emptySet();preview.setPreloadedBatch(null,emptyList());if(arEnabled) replaySession() })
        intent.getStringExtra("triple_set")?.let { input.setText(it) }
        options.addView(Button(this).apply { setText(R.string.ar_use_package);setOnClickListener { picker.launch(Intent(this@TripleScanActivity,ArDownloadsActivity::class.java).putExtra("choose",true)) } })
        val switches=LinearLayout(this)
        val prefs=getSharedPreferences("triple_scanner",0)
        contourEnabled=prefs.getBoolean("edscan_contour",false);preview.enableEdScanContour(contourEnabled)
        options.addView(CheckBox(this).apply {
            tag="triple_contour_flag";setText(R.string.edscan_contour_flag);isChecked=contourEnabled
            setOnCheckedChangeListener { _,value ->
                contourEnabled=value;contourRevision++;preview.enableEdScanContour(value);edscanContour.clearContour()
                prefs.edit().putBoolean("edscan_contour",value).apply();trace.event("triple.contour enabled=$value")
            }
        })
        trace.event("triple.contour enabled=$contourEnabled")
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
        fun trial(key:String,label:Int,update:(Boolean)->Unit) {
            options.addView(CheckBox(this).apply {
                tag="triple_trial_$key";setText(label);isChecked=prefs.getBoolean(key,false);update(isChecked)
                setOnCheckedChangeListener { _,value ->
                    update(value);prefs.edit().putBoolean(key,value).apply();invalidateEvidence()
                    trace.event("triple.trial singleReading=$singleArtReading delayedCrop=$delayedArtCrop cropAfterFailures=2 uprightOnly=$uprightOnly")
                }
            })
        }
        trial("single_art_reading",R.string.triple_trial_single_reading) { singleArtReading=it }
        options.addView(TextView(this).apply {
            tag="triple_fast_art_help";setText(R.string.triple_fast_art_help)
            setPadding(dp(8),0,dp(8),dp(8))
        })
        trial("upright_only",R.string.triple_trial_upright) { uprightOnly=it }
        trial("delayed_art_crop",R.string.triple_trial_delayed_crop) { delayedArtCrop=it }
        trace.event("triple.trial singleReading=$singleArtReading delayedCrop=$delayedArtCrop cropAfterFailures=2 uprightOnly=$uprightOnly")
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
            nameIndexReady=ready
            trace.event("triple.nameIndex ready=$ready ms=${(System.nanoTime()-indexStart)/1000000}")
            if(!closed) indexStatus.visibility=View.GONE
        }
        configureEdition()
        if(restoringSession) restoreSession()
    }
    private fun prepareArt() { worker.execute {
        if(engine!=null || closed) return@execute
        try {
            engine=EdScanPerf.once("triple.engineInit") {
                val prepared=NativeCollectorVisionEngine.prepareForCamera(applicationContext)
                try {
                    val index=io.asv.collectorvision.FastRegionIndex(applicationContext)
                    require(index.available) { "Hybrid visual index unavailable" }
                    prepared.configureFast(index,io.asv.collectorvision.FastRegion.UPPER)
                    prepared.configureFast(index,io.asv.collectorvision.FastRegion.ART)
                    hybridIndex=index
                    prepared
                } catch(error:Exception) { prepared.close();throw error }
            }
            trace.event("triple.hybrid ready=true fullAlways=true delayedCrop=$delayedArtCrop")
            ui { showScannerReady() }
        }
        catch(error:Exception) { android.util.Log.w("EdScanPerf","Triple engine unavailable",error);ui { status.setText(R.string.triple_engine_error) } }
    } }
    /** Explicit resume only. Rehydrates exact owned rows without adding copies or rewriting them. */
    private fun restoreSession() {
        status.setText(R.string.triple_restoring_session)
        worker.execute {
            val loaded=runCatching {
                val library=LibraryCatalog.activeFile(this)
                check(intent.getStringExtra("triple_resume_library")==library)
                val group=requireNotNull(intent.getStringExtra("triple_resume_group"))
                check(group.startsWith("Triple "))
                val cards=requireNotNull(DataUtils.readSerializable<io.asv.mtgocr.ocrreader.model.Biblio>(this,library))
                    .cards.filter { group in it.groups }.sortedBy { it.addedAt }
                check(cards.isNotEmpty())
                val ids=cards.map { it.collectionItemId }.toSet()
                val dir=io.asv.collectorvision.EdScanJobs.directory(this)
                val photos=dir.listFiles().orEmpty().filter { it.name.endsWith(".link.json") }.mapNotNull { file ->
                    val link=ScanReviewStore.json(file) ?: return@mapNotNull null
                    val id=link.optString("collectionItemId")
                    if(link.optString("library")!=library || id !in ids) return@mapNotNull null
                    id to file.name.removeSuffix(".link.json")
                }.toMap()
                Triple(group,cards,photos.mapValues { (_,key) -> key to ScanReviewStore.json(java.io.File(dir,"$key.json")) })
            }
            ui {
                val snapshot=loaded.getOrNull()
                if(snapshot==null) {
                    trace.event("triple.resume failed=true");Toast.makeText(this,R.string.triple_save_failed,Toast.LENGTH_LONG).show();finish();return@ui
                }
                collectionSession.restore(snapshot.first,snapshot.second) { error ->
                    if(closed) return@restore
                    if(error!=null) { Toast.makeText(this,R.string.triple_save_failed,Toast.LENGTH_LONG).show();finish();return@restore }
                    snapshot.second.forEachIndexed { index,card ->
                        val photo=snapshot.third[card.collectionItemId]
                        val metadata=photo?.second
                        val target=Target(metadata?.optString("id")?.takeIf { it.isNotBlank() } ?: card.printingUuid.orEmpty(),
                            card.printingUuid.orEmpty(),card.name.orEmpty(),card.setCode.orEmpty(),
                            "${card.name} · ${card.setCode} #${card.collectorNumber}",card.imgPath.orEmpty(),card.finish.orEmpty(),
                            if(metadata?.optString("source")=="ocr") "OCR" else "EdScan",card.isProvisionalEdition,number=card.collectorNumber.orEmpty())
                        val event=index+1L
                        currentEncounter=event;upsertGallery(target)
                        galleryEntries[event.toString()]?.let { entry ->
                            entry.savedId=card.collectionItemId;entry.count=card.quantityCount
                            entry.language=card.languageCode.takeIf { !it.isNullOrBlank() }
                            entry.manualLanguage=metadata?.optBoolean("manualLanguage") ?: true
                            entry.languageOrigin=metadata?.optString("languageOrigin").orEmpty()
                            entry.physicalLanguage=metadata?.optString("physicalLanguage")?.takeIf { it.isNotBlank() }
                            entry.languageMismatch=metadata?.optBoolean("languageConflict") ?: false
                            entry.manualEdition=metadata?.optBoolean("manualEdition") ?: false
                            entry.manualFinish=true;entry.selectedFinish=card.finish.orEmpty()
                            photo?.first?.let { entry.photoKey=it }
                            entry.amount=io.asv.mtgocr.ocrreader.data.PriceCurrency.amountOrNull(this,card)?.let {
                                it to io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this)
                            }
                            bindGallery(entry)
                        }
                        addedCollectionIds.add(card.collectionItemId)
                    }
                    currentEncounter=snapshot.second.size.toLong()
                    val last=requireNotNull(galleryEntries[currentEncounter.toString()]).target
                    val identity=TripleReadingPolicy.identity(last.name)
                    encounters.restoreLastEvent(currentEncounter,identity);arbiter.restoreCurrent(identity,last.source)
                    setResult(RESULT_OK,Intent().putStringArrayListExtra(RapidEditionScanActivity.EXTRA_SESSION_CARD_IDS,ArrayList(addedCollectionIds)))
                    restoringSession=false;status.text="";renderSessionTotal();lockConsecutiveRepeat()
                    getSharedPreferences("triple_resume_once",0).edit().clear().apply()
                    trace.event("triple.resume rows=${snapshot.second.size} group=${snapshot.first}")
                }
            }
        }
    }
    private fun renderSessionTotal(entry:GalleryEntry?=null) {
        entry?.let {
            val quote=io.asv.mtgocr.ocrreader.data.SelectedPrices.quote(this,it.target.printing,it.selectedFinish)
            val amount=quote?.let { q -> q.amount to q.currency }
            val card=io.asv.mtgocr.ocrreader.model.CardInfo("", "", "", "", "1").apply { printingUuid=it.target.printing;finish=it.selectedFinish }
            io.asv.mtgocr.ocrreader.data.SelectedPrices.ensure(this,listOf(card),Runnable {
                if (!isFinishing && !isDestroyed) { renderSessionTotal(it); bindGallery(it) }
            })
            totals.update(it.root.tag.toString().toLong(),it.count,amount?.first,amount?.second.orEmpty())
            val event = it.root.tag.toString().toLong()
            if (it.count <= 0) totalPriceInputs.remove(event)
            else totalPriceInputs[event] = PriceOriginPolicy.Input(it.target.printing, it.selectedFinish,
                amount?.first, amount?.second.orEmpty(), it.count, it.target.id)
        }
        val value=totals.total { amount,currency -> io.asv.mtgocr.ocrreader.data.PriceCurrency.convert(this,amount,currency) }
        val formatted=io.asv.mtgocr.ocrreader.data.PriceCurrency.format(this,value,io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this))
        val label=getString(R.string.triple_session_total,formatted)+if(totals.pending>0) "\n"+getString(R.string.triple_total_pending,totals.pending) else ""
        if(sessionTotal.text.toString()!=label) sessionTotal.text=label
    }
    /** Hide idle boilerplate, but keep loading, errors and actual recognition results visible. */
    private fun scannerMessage(tagName:String,initial:Int,idle:Int)=TextView(this).apply {
        tag=tagName;setPadding(12,4,12,4)
        doAfterTextChanged { visibility=if(it.isNullOrBlank() || it.toString()==getString(idle)) View.GONE else View.VISIBLE }
        setText(initial)
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun ui(action:()->Unit)=runOnUiThread { if(!closed && !isDestroyed) action() }
    private fun lockConsecutiveRepeat() {
        repeatIdentity=galleryEntries[currentEncounter.toString()]?.target?.name?.let(TripleReadingPolicy::identity).orEmpty()
        repeatNeedsTap=true
        if(::rescanButton.isInitialized) rescanButton.visibility=View.VISIBLE
        trace.event("triple.scanLatch state=repeat_locked event=$currentEncounter")
    }
    private fun rearmScan() {
        if(!repeatNeedsTap || closed || !resumed || restoringSession) return
        // No saved row is touched. Only future captures can create a new event/collectionItemId.
        invalidateEvidence()
        arbiter.beginManualScan(SystemClock.elapsedRealtime()+1)
        encounters.nextEncounter()
        resolvingKey="";lastLookupFailureAt=0;photoCapturedFor=""
        nextFrameAt=0;lastFrameAt=0
        rescanButton.visibility=View.GONE;repeatNeedsTap=false
        trace.event("triple.scanLatch state=armed afterEvent=$currentEncounter generation=$generation")
    }

    private fun invalidateEvidence() { if(::edscanContour.isInitialized) edscanContour.clearContour(); languageCopies.reset();arConsensus.reset();liveArTarget=null;lastAcceptedVisualId="";clearCurrentLanguage();languageConsensus.reset();rarityRequested.clear();generation++;proposedVisualTargets.clear();arbiter.resetPending();confirmationPending=true;candidate=null;frameKey="";lastOcrKey="";lastOcrCanonical="";observedLanguage=null;observedName=null;preferGlobalUntil=0L;arReadyAt=0L;if(::result.isInitialized) result.setText(R.string.triple_waiting) }
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
        findViewById<View>(android.R.id.content)?.findViewWithTag<Button>("triple_language_shortcut")?.text=scopeLanguageButton.text
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
        invalidateEvidence();val token=generation;references=sessionTargets.toMap();activePackage=false;packageVisualIds=emptySet()
        preview.setPreloadedBatch(null,emptyList())
        val codes=sets.codes();allowedSets=io.asv.mtgocr.ocrreader.data.ScanSetLockPolicy.expand(codes)
        updateVisualScope(codes)
        if(!arEnabled || codes.isEmpty()) { if(arEnabled) replaySession();status.setText(R.string.triple_ready);return }
        if(!PremiumAccess.isEnabled(this)) { status.setText(R.string.premium_required);return }
        status.setText(R.string.ar_loading_catalog)
        ArPackageStore.worker.execute {
            val installed=if(codes.size==1) runCatching { ArPackageStore.installed(applicationContext,codes.single()) }.getOrNull()
                else ArPackageStore.installedEntries(applicationContext).sortedBy { if(it.id in codes) 0 else 1 }
                    .firstNotNullOfOrNull { entry ->
                        runCatching { ArPackageStore.installed(applicationContext,entry.id) }.getOrNull()
                            ?.takeIf { TripleArPackagePolicy.covers(codes,it.references.map { ref -> ref.label }) }
                    }
            val rarities=runCatching {
                installed?.references.orEmpty().map { it.scryfallId }.distinct().chunked(400).flatMap {
                    io.asv.mtgocr.ocrreader.data.CardDatabase.get(applicationContext).cardDao().printingsByScryfallIds(it)
                }.associate { it.scryfallId to it.rarity }
            }.getOrDefault(emptyMap())
            ui {
                if(token!=generation) return@ui
                if(installed==null) { replaySession();status.setText(R.string.triple_missing_package);return@ui }
                val packageTargets=installed.references.associate { ref -> ref.printing to Target(ref.scryfallId,ref.printing,ref.label.substringBefore(" · "),ref.label.substringAfterLast(" · ").substringBefore(" #"),ref.label,ref.image,ref.finish,"AR",rarity=ref.rarity.ifBlank { rarities[ref.scryfallId].orEmpty() }) }
                // The compiled DB must keep all entries; only selected sets may emit recognition.
                references=packageTargets.filterValues { TripleScanScopePolicy.allows(allowedSets,it.set) }
                trace.event("triple.arPackage id=${installed.entry.id} requested=${codes.sorted().joinToString(",")} accepted=${references.size} total=${packageTargets.size}")
                packageVisualIds=packageTargets.values.map { it.id }.toSet()
                activePackage=true;arReadyAt=SystemClock.elapsedRealtime()
                // Recognition is available before local prices return. No catalog/network on this path.
                fun cards()=installed.references.mapNotNull { packageTargets[it.printing] }.map { t->ArPreloadCard(t.printing,t.image,t.label,price(t),t.rarity,t.set,t.number) }
                preview.setPreloadedBatch(installed.entry.name,cards(),arReadyAt,installed.database)
                references=sessionTargets+references;replaySession()
                val started=System.nanoTime()
                CardRepository.get(this).loadArPackagePrices(references.values.associate { it.id to it.finish }) { found ->
                    EdScanPerf.record("triple.localPrices",System.nanoTime()-started)
                    if(token==generation && !closed) { prices=prices+found.mapKeys { (id,_)->id+"|"+(references.values.firstOrNull { it.id==id }?.finish ?: "nonfoil") };preview.updatePrices(cards());refreshGallery();renderResult() }
                }
                showScannerReady()
            }
        }
    }
    private fun showScannerReady() {
        status.setText(if(visualScopeError) R.string.scan_card_not_found_in_selected_set
            else if(visualScope?.isEmpty()==true) R.string.triple_loading else R.string.triple_ready)
    }
    private fun updateVisualScope(codes: Set<String>) {
        val revision=++visualScopeRevision
        visualScopeError=false
        visualScope=if(codes.isEmpty()) null else emptySet()
        if(codes.isEmpty()) { trace.event("triple.visualScope ready=true mode=global");return }
        val started=SystemClock.elapsedRealtime()
        CardRepository.get(this).loadVisualSetIds(codes) { ids,error ->
            if(closed || revision!=visualScopeRevision) return@loadVisualSetIds
            visualScopeError=error!=null || ids.isEmpty()
            visualScope=if(error==null) ids.toSet() else emptySet()
            trace.event("triple.visualScope ready=${error==null && ids.isNotEmpty()} ids=${visualScope?.size} ms=${SystemClock.elapsedRealtime()-started} sets=${codes.sorted().joinToString(",")}")
            if(error!=null || ids.isEmpty()) status.setText(R.string.scan_card_not_found_in_selected_set)
        }
    }
    private fun observeAppearance(signature: FloatArray?, at: Long, token: Int) {
        if(token!=generation || !resumed) return
        val previous=arbiter.scene
        arbiter.frame(signature,at)
        if(previous!=arbiter.scene) {
            confirmationPending=true;proposedVisualTargets.clear()
            trace.event("triple.scene scene=${arbiter.scene} reason=appearance atMs=$at")
        }
    }
    private fun frame(image:android.media.Image,rotation:Int,release:()->Unit,tracked:String?,corners:FloatArray?,guide:FloatArray?):Boolean {
        if(!resumed || closed || !busy.compareAndSet(false,true)) return false
        lastFrameAt=SystemClock.elapsedRealtime();val arAtStart=liveArTarget?.id;val sampleAt=lastFrameAt;val token=generation;val queued=System.nanoTime()
        val frameUprightOnly=uprightOnly
        val contourToken=contourRevision
        val contourMap=if(contourEnabled) preview.edscanProjection(image.timestamp) else null
        fun publishContour(scan:io.asv.collectorvision.ScanResult,confirmed:Boolean) {
            val projection=contourMap ?: return
            if(!contourEnabled) return
            val points=if(scan.present && scan.corners.size==4) EdScanContourGeometry.project(
                scan.corners.flatMap { listOf(it.x,it.y) }.toFloatArray(),rotation,projection.quad) else null
            ui {
                if(token==generation && contourToken==contourRevision && contourEnabled && resumed && !closed &&
                    edscanContour.width==projection.width && edscanContour.height==projection.height) {
                    if(points==null) edscanContour.clearContour() else edscanContour.showContour(points,confirmed,sampleAt)
                    trace.event("triple.contour phase=${if(confirmed) "confirmed" else "detected"} ageMs=${SystemClock.elapsedRealtime()-sampleAt}")
                }
            }
        }
        try { worker.execute {
            var bitmap:Bitmap?=null;var crop:Bitmap?=null;var handedOff=false
            try {
                if(closed || !resumed || token!=generation) return@execute
                if(workerGeneration!=token) { workerGeneration=token;hybridPolicy=TripleCropTrialPolicy(delayedArtCrop);visualConfirmation=TripleIdentityConfirmation(singleArtReading);frameScheduler.reset();adaptiveShadow.reset();visualConfirmation.reset();slowFrame=null;streakId="";readings=0 }
                EdScanPerf.record("triple.workerQueue",System.nanoTime()-queued)
                val frameNow=SystemClock.elapsedRealtime()
                val trackedTarget=if(!arEnabled || frameNow<preferGlobalUntil) null else tracked?.let { references[it] ?: candidate?.takeIf { c->c.printing==it } }
                val audit=artEnabled && trackedTarget!=null &&
                    (confirmationPending || trackedTarget.id!=lastAcceptedVisualId || frameNow-lastVisualAudit>=5000L)
                bitmap=EdScanPerf.measure("triple.decode") {
                    val p=image.planes.map { ArYuvRgb.Plane(it.buffer,it.rowStride,it.pixelStride) }
                    Bitmap.createBitmap(ArYuvRgb.pixels(image.width,image.height,0,0,p[0],p[1],p[2]),image.width,image.height,Bitmap.Config.ARGB_8888)
                }
                release()
                val visualDue=artEnabled && (slowFrame!=null || audit || trackedTarget==null &&
                    TripleScanPolicy.useVisual(frameNow,arReadyAt,maxOf(lastTrackedAt,lastArSeen),false))
                val auditSame=trackedTarget!=null && trackedTarget.id==lastAcceptedVisualId && !confirmationPending &&
                    TripleScanPolicy.agrees(lastOcrCanonical,trackedTarget.name)
                val priorityFollowUp=frameScheduler.hasFollowUp(frameNow)
                val lane=frameScheduler.next(frameNow,visualDue,
                    ocrEnabled && nameIndexReady && (corners!=null && trackedTarget!=null || artEnabled && engine!=null || guide!=null),auditSame,revalidationDue=slowFrame!=null)
                trace.event("triple.lane lane=$lane priorityFollowUp=$priorityFollowUp captureAtMs=$sampleAt queueAndDecodeMs=${SystemClock.elapsedRealtime()-sampleAt}")
                if(lane==TripleFrameScheduler.Lane.OCR) {
                    // A NEW capture, never the bitmap that has already waited for Milo/search.
                    val target=trackedTarget
                    if(target!=null && corners!=null) {
                        crop=warp(requireNotNull(bitmap),corners)
                    } else if(engine!=null && artEnabled) {
                        val raw=requireNotNull(bitmap)
                        val upright=if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
                        try {
                            val detection=requireNotNull(engine).detect(upright)
                            trace.event("triple.ocrGeometry detectMs=${detection.detectionMs} present=${detection.present} inferMs=0")
                            if(!detection.present) {
                                ui { if(token==generation && SystemClock.elapsedRealtime()-lastArSeen>1000) arbiter.missing(sampleAt) }
                                return@execute
                            }
                            crop=warp(upright,TripleOcrGeometry.upright(detection.corners).flatMap { listOf(it.x*upright.width,it.y*upright.height) }.toFloatArray())
                        } finally { if(upright!==raw) upright.recycle() }
                    } else if(guide!=null) {
                        val titleCrop=warp(requireNotNull(bitmap),guide,630,182)
                        val full=guideCardPhoto(requireNotNull(bitmap),guide)
                        val signature=if(!artEnabled && full!=null) BitmapFactory.decodeByteArray(full,0,full.size)?.let { b -> try { TripleFrameSignature.read(b) } finally { b.recycle() } } else null
                        handedOff=true
                        runOnUiThread { try { observeAppearance(signature,sampleAt,token);guidedOcr(titleCrop,token,full,sampleAt) } finally { busy.set(false) } }
                        return@execute
                    } else return@execute
                    if(token!=generation || !resumed) return@execute
                    frameKey=target?.id ?: "ocr"
                    val captured=crop;crop=null;handedOff=true
                    captureReviewPhoto(captured,frameKey,token)
                    // A second language belongs to this capture, never to the previous immutable photo.
                    val languagePhoto=if(fixedLanguage==null) captured?.let { EdScanPerf.measure("triple.languagePhoto") { jpeg(it) } } else null
                    // Keep appearance from a single producer (EdScan when enabled).
                    val signature=if(!artEnabled) TripleFrameSignature.read(captured) else null
                    runOnUiThread {
                        if(token!=generation || !resumed) { captured?.recycle();busy.set(false);return@runOnUiThread }
                        observeAppearance(signature,sampleAt,token)
                        ocr(captured,target,token,sampleAt=sampleAt,languagePhoto=languagePhoto)
                    }
                    return@execute
                }
                if(lane!=TripleFrameScheduler.Lane.EDSCAN) {
                    // AR-only still needs an immutable review photo and physical presence.
                    if(trackedTarget!=null && corners!=null) {
                        lastTrackedAt=SystemClock.elapsedRealtime()
                        crop=warp(requireNotNull(bitmap),corners)
                        captureReviewPhoto(crop,trackedTarget.id,token)
                        val signature=if(!artEnabled) TripleFrameSignature.read(crop) else null
                        ui { observeAppearance(signature,sampleAt,token) }
                    }
                    return@execute
                }
                val now=SystemClock.elapsedRealtime()
                val target=trackedTarget
                if(!artEnabled) return@execute
                if(slowFrame==null && !audit && now>=preferGlobalUntil && !TripleScanPolicy.useVisual(now,arReadyAt,maxOf(lastTrackedAt,lastArSeen),false)) return@execute
                lastVisualAudit=now
                val raw=requireNotNull(bitmap)
                val upright=if(rotation==0) raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
                try {
                    val scope=visualScope
                    if(scope!=null && scope.isEmpty()) {
                        trace.event("triple.confirmation reason=scope_pending atMs=$sampleAt")
                        return@execute
                    }
                    val pending=slowFrame.also { slowFrame=null }
                    val validation=if(pending!=null && pending.token==token && pending.scope===scope && pending.scene==arbiter.scene) {
                        val freshCrop=warp(upright,pending.scan.corners.flatMap { listOf(it.x*upright.width,it.y*upright.height) }.toFloatArray())
                        try { TripleSlowFramePolicy.matches(pending.signature,TripleFrameSignature.read(freshCrop),pending.started,sampleAt) }
                        finally { freshCrop?.recycle() }
                    } else false
                    if(pending!=null) {
                        trace.event("triple.slowValidation accepted=$validation processingMs=${sampleAt-pending.started}")
                        if(!validation) visualConfirmation.reset()
                    }
                    val scan=if(validation) requireNotNull(pending).scan
                        else engine?.let {
                            val profile=hybridPolicy.region
                            if(profile!=hybridProfile) { visualConfirmation.reset();streakId="";readings=0 }
                            hybridProfile=profile
                            it.configureFast(requireNotNull(hybridIndex),profile)
                            EdScanPerf.measure("triple.edscan") {
                                it.scan(upright,scope,onDetected=if(contourMap!=null) ({ detection -> publishContour(detection,false) }) else null,assumeUpright=frameUprightOnly)
                            }
                        } ?: return@execute
                    if(token!=generation || scope!==visualScope) { visualConfirmation.reset();return@execute }
                    // AR has moved to another card while ONNX was running: never spend another
                    // OCR cycle (or renew old authority) on that obsolete capture.
                    if(trackedTarget!=null && liveArTarget?.id?.let { it!=trackedTarget.id }==true) {
                        trace.event("triple.supersededFrame ageMs=${SystemClock.elapsedRealtime()-sampleAt}")
                        slowFrame=null;return@execute
                    }
                    visualFrames++
                    if(!validation && scan.present && SystemClock.elapsedRealtime()-sampleAt>TripleSlowFramePolicy.REVALIDATE_AFTER_MS) {
                        val slowCrop=warp(upright,scan.corners.flatMap { listOf(it.x*upright.width,it.y*upright.height) }.toFloatArray())
                        val signature=try { TripleFrameSignature.read(slowCrop) } finally { slowCrop?.recycle() }
                        slowFrame=SlowFrame(scan,signature,sampleAt,scope,token,arbiter.scene)
                        trace.event("triple.slowPending frameMs=${SystemClock.elapsedRealtime()-sampleAt} detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs}")
                        return@execute
                    }
                    trace.event("triple.hybrid profile=${hybridProfile.key} conflict=${scan.regionConflict} lanes=${scan.regionDiagnostics}")
                    trace.event("triple.methodSample method=art present=${scan.present} detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs} ar=$arEnabled ocr=$ocrEnabled")
                    if(!scan.present || scan.corners.size!=4) {
                        if(contourEnabled) ui { if(token==generation && contourToken==contourRevision) edscanContour.clearContour() }
                        hybridPolicy.reset();visualConfirmation.reset();frameScheduler.visualResult("no_detection",0);confirmationPending=true
                        trace.event("triple.confirmation reason=no_detection atMs=$sampleAt detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs}")
                        ui { if(token==generation && SystemClock.elapsedRealtime()-lastArSeen>1000) arbiter.missing(sampleAt) }
                        absentFrames++;readings=0;if(absentSince==0L) absentSince=now
                        if(absentFrames>=2 && now-absentSince>=3000) { sceneStartedAt=0L;frameKey="";ui { if(token==generation) { if(candidate?.source!="OCR" || SystemClock.elapsedRealtime()-lastOcrNameAt>4000) { clearCurrentLanguage();languageConsensus.reset();candidate=null;result.setText(R.string.triple_waiting) } } } }
                        if(now-lastObservationLog>=1000) { lastObservationLog=now;trace.event("triple.observation present=false package=$activePackage detectMs=${scan.detectionMs}") }
                        return@execute
                    }
                    ui { if(token==generation) arbiter.present() }
                    absentFrames=0;absentSince=0L;if(sceneStartedAt==0L) sceneStartedAt=now
                    val index=requireNotNull(hybridIndex)
                    val visual=visualConfirmation.observe(scan.hits.map { h ->
                        val row=index.byId[h.cardId]
                        EdScanFastIdentity.Candidate(h.cardId,h.score,row?.identity,row?.set.orEmpty(),row?.released.orEmpty())
                    },scan.comparisonRegion=="full" && !scan.regionConflict,sampleAt,
                        if(validation) requireNotNull(pending).started else sampleAt)
                    val evidence=visual.evidence
                    val top=visual.printing?.let { id -> evidence.score?.let { io.asv.collectorvision.Hit(id,it) } }
                    if(evidence.identity==streakId) readings++ else { streakId=evidence.identity.orEmpty();readings=1 }
                    val confirmation=visual.confirmation
                    val reliable=confirmation.reliable
                    publishContour(scan,reliable)
                    frameScheduler.visualResult(confirmation.reason,SystemClock.elapsedRealtime())
                    val provisional=!visual.printingConfirmed
                    trace.event("triple.identity identity=${evidence.identity?.hashCode()} printing=${top?.cardId} score=${evidence.score} runner=${evidence.runner} margin=${evidence.score?.minus(evidence.runner ?: Float.NaN)} confirmed=$reliable printingConfirmed=${visual.printingConfirmed} region=${scan.comparisonRegion}")
                    // Keep this profile for the reserved second vote; never reset it between that pair.
                    val keepProfileForFollowUp=confirmation.reason=="readings" && !priorityFollowUp
                    if(!keepProfileForFollowUp && hybridPolicy.observe(scan.present,EdScanFastPolicy.Reading(reliable,confirmation.reason,confirmation.elapsedMs))) {
                        visualConfirmation.reset();frameScheduler.visualResult("profile_changed",0)
                        trace.event("triple.hybridSwitch from=${hybridProfile.key} to=${hybridPolicy.region.key} reason=${confirmation.reason}")
                    }
                    confirmationPending=!reliable && !(top?.cardId==lastAcceptedVisualId && target?.id==lastAcceptedVisualId)
                    trace.event("triple.confirmation target=${top?.cardId?.hashCode()} atMs=$sampleAt reason=${confirmation.reason} score=${top?.score} runner=${evidence.runner} margin=${evidence.score?.minus(evidence.runner ?: Float.NaN)} runnerKind=identity printingMargin=${scan.hits.firstOrNull()?.score?.minus(scan.hits.getOrNull(1)?.score ?: Float.NaN)} strongReadings=${confirmation.count} confirmMs=${confirmation.elapsedMs} frameMs=${SystemClock.elapsedRealtime()-sampleAt} revalidated=$validation pipelineMs=${SystemClock.elapsedRealtime()-(if(validation) requireNotNull(pending).started else sampleAt)} preprocessAndQueueMs=${((System.nanoTime()-queued)/1000000-(if(validation) 0 else scan.detectionMs+scan.recognitionMs)).coerceAtLeast(0)} detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs} searchMs=${scan.searchMs} featureMs=${scan.recognitionMs-scan.searchMs} searchedRows=${scan.searchedRows} scopeSize=${scope?.size ?: -1} audit=$audit")
                    val paired=visual.printingConfirmed && target!=null && top?.cardId==target.id &&
                        arConsensus.confirms(top.cardId,top.score,scan.hits.getOrNull(1)?.score,sampleAt,SystemClock.elapsedRealtime())
                    val showCandidate=top!=null && (reliable || TripleScanPolicy.visualCandidate(top.score,readings))
                    if(now-lastObservationLog>=750) {
                        lastObservationLog=now
                        trace.event("triple.observation present=true ageMs=${now-sceneStartedAt} top=${top?.cardId?.hashCode()} score=${top?.score} margin=${top?.score?.minus(scan.hits.getOrNull(1)?.score ?: Float.NaN)} readings=$readings preview=$showCandidate strict=$reliable package=$activePackage audit=$audit detectMs=${scan.detectionMs} inferMs=${scan.recognitionMs}")
                    }
                    // A weak audit cannot displace a current AR match; a strong different card can.
                    if(audit && reliable && top?.cardId!=target?.id) {
                        preferGlobalUntil=SystemClock.elapsedRealtime()+2000
                        trace.event("triple.edscanOverridesTracked differentTarget=true")
                    }
                    frameKey=if(showCandidate || paired) top!!.cardId else "ocr"
                    crop=warp(upright,scan.corners.flatMap { listOf(it.x*upright.width,it.y*upright.height) }.toFloatArray())
                    val captured=crop;crop=null;handedOff=true
                    val signature=TripleFrameSignature.read(captured)
                    captureReviewPhoto(captured,frameKey,token)
                    runOnUiThread {
                        if(token!=generation || !resumed) { captured?.recycle();busy.set(false);return@runOnUiThread }
                        // Physical motion is independent of recognition confidence. Filtering weak
                        // matches here traps the next card behind the previous EdScan authority.
                        // The fingerprint already rejects blank crops; the arbiter requires stability.
                        observeAppearance(signature,sampleAt,token)
                        if(paired && arConsensus.confirms(top?.cardId,top?.score,scan.hits.getOrNull(1)?.score,sampleAt,SystemClock.elapsedRealtime())) {
                            val accepted=offer(requireNotNull(target).copy(source="EdScan",provisional=false),sampleAt,pairedVisual=true)
                            trace.event("triple.pairedVisual accepted=$accepted target=${target.id.hashCode()} score=${top?.score} frameMs=${SystemClock.elapsedRealtime()-sampleAt}")
                            captured?.recycle();busy.set(false)
                            return@runOnUiThread
                        }
                        if(target!=null && target.id==lastAcceptedVisualId && top?.cardId==lastAcceptedVisualId &&
                            !confirmationPending && fixedLanguage!=null) {
                            captured?.recycle();busy.set(false);return@runOnUiThread
                        }
                        if(confirmation.reason=="readings") {
                            // Pending EdScan evidence does not suppress the independent fresh OCR lane.
                            captured?.recycle();busy.set(false);return@runOnUiThread
                        }
                        if(showCandidate) {
                            val id=top!!.cardId
                            if(candidate?.id==id) { offerVisual(requireNotNull(candidate).copy(source="EdScan",provisional=provisional),sampleAt,reliable) }
                            else {
                                result.setText(R.string.triple_resolving)
                                resolveVisual(id,token,provisional,sampleAt,reliable)
                                // OCR gets the next eligible fresh capture, not this old visual frame.
                            }
                        }
                        captured?.recycle();busy.set(false)
                    }
                    return@execute
                } finally { if(upright!==raw) upright.recycle() }
            } catch(error:Exception) { android.util.Log.w("EdScanPerf","Triple frame failed",error) }
            finally {
                bitmap?.recycle();crop?.recycle();release()
                val completed=SystemClock.elapsedRealtime()
                val soloArt=artEnabled && !arEnabled && !ocrEnabled
                val rest=TripleScanPolicy.frameRestMs(completed-sampleAt,frameScheduler.hasFollowUp(completed),soloArt,slowFrame!=null)
                val changedAr=liveArTarget?.id?.let { it!=arAtStart }==true
                nextFrameAt=if(changedAr) 0 else completed+rest
                trace.event("triple.framePacing uprightOnly=$frameUprightOnly captureAtMs=$sampleAt workMs=${completed-sampleAt} restMs=${if(changedAr) 0 else rest} soloArt=$soloArt revalidationPending=${slowFrame!=null} repeatLocked=$repeatNeedsTap")
                if(!handedOff) busy.set(false)
            }
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
    private fun resolveVisual(id:String,token:Int,provisional:Boolean,sampleAt:Long,identityConfirmed:Boolean) {
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
            if(offerVisual(target,sampleAt,identityConfirmed)) registerSessionTarget(target)
        }
        if(!queued) { resolvingKey="";trace.event("triple.lookupDeferred reason=busy_or_closed") }
    }
    private fun replaySession() {
        sessionTargets.values.filter { it.id !in packageVisualIds }.forEach { t -> preview.addFallbackReference(ArPreloadCard(t.printing,t.image,t.label,price(t),t.rarity,t.set,t.number)) }
    }
    private fun registerSessionTarget(target:Target) {
        sessionTargets[target.printing]=target
        references=references+(target.printing to target)
        if(arEnabled && target.id !in packageVisualIds && target.printing.isNotBlank() && target.image.isNotBlank()) preview.addFallbackReference(ArPreloadCard(target.printing,target.image,target.label,price(target),target.rarity,target.set,target.number))
    }
    @Volatile private var lastOcrCanonical=""
    private var lastOcrNameAt=0L
    @JvmOverloads private fun proposeOcrName(name:String,sampleAt:Long=SystemClock.elapsedRealtime()):Boolean {
        lastOcrCanonical=name;lastOcrNameAt=SystemClock.elapsedRealtime()
        val known=proposedVisualTargets[TripleReadingPolicy.identity(name)]?.takeIf {
            it.scene==arbiter.scene && SystemClock.elapsedRealtime()-it.at<=2500
        }?.target ?: candidate?.takeIf { arbiter.hasConfirmedScene && TripleScanPolicy.agrees(it.name,name) }
            ?: galleryEntries[currentEncounter.toString()]?.target?.takeIf { arbiter.hasConfirmedScene && TripleScanPolicy.agrees(it.name,name) }
        val proposed=known ?: Target("ocr-name:"+TripleReadingPolicy.identity(name),"",name,"",name,"","nonfoil","OCR",true)
        if(!offer(proposed,sampleAt,"OCR")) return false
        if(known==null) frameKey=proposed.id
        galleryEntries[currentEncounter.toString()]?.takeIf { it.target.printing.isBlank() }?.let { resolveOcrEntry(it,sets.codes()) }
        return true
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
                if(option!=null && TripleScanScopePolicy.allows(scope,option.setCode)) {
                    entry.resolvingEdition=false
                    applyEditionToEntry(entry,option,false,fallback)
                } else {
                    entry.scopeConflict=scope.isNotEmpty()
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
        if(manual) { entry.manualEdition=true;entry.recognitionConflict=false } // Lock before asynchronous lookup, not after it.
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
                // Edition recognition/selection never chooses the physical finish.
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
    @JvmOverloads private fun applyTitleLanguage(name:String,lines:List<String>,token:Int,event:Long,alias:String?=null,hint:String?=null,
                                   sampleAt:Long=SystemClock.elapsedRealtime(),photo:ByteArray?=null) {
        val entry=galleryEntries[event.toString()] ?: return
        if(fixedLanguage!=null || entry.manualLanguage) return
        languageWorker.execute {
            val raw=titleLanguageIndex?.lookup(lines,listOf(name)) ?: TitleLanguageIndex.Evidence()
            val localized=alias?.let { titleLanguageIndex?.lookup(listOf(it),listOf(name)) } ?: TitleLanguageIndex.Evidence()
            val code=TripleReadingPolicy.titleLanguage(raw,localized,hint)
            ui {
                if(closed || !resumed || token!=generation || event!=currentEncounter || sampleAt<arbiter.sceneAt ||
                    SystemClock.elapsedRealtime()-sampleAt>2500 || fixedLanguage!=null || entry.count<=0 ||
                    !TripleScanPolicy.agrees(entry.target.name,name) || entry.manualLanguage) return@ui
                trace.event("triple.titleLanguage event=$event raw=${raw.candidates.sorted().joinToString(",")} alias=${localized.candidates.sorted().joinToString(",")} selected=${code ?: "und"}")
                if(code!=null) {
                    applyPhysicalLanguage(name,code,"title",event,token,sampleAt,photo)
                }
            }
        }
    }
    /** Only current, same-snapshot title-bound evidence may complete or split a physical copy. */
    private fun applyPhysicalLanguage(name:String,code:String,origin:String,event:Long,token:Int,sampleAt:Long,photo:ByteArray?) {
        val entry=galleryEntries[event.toString()] ?: return
        val now=SystemClock.elapsedRealtime()
        if(closed || !resumed || token!=generation || event!=currentEncounter || entry.count<=0 || sampleAt<arbiter.sceneAt ||
            !TripleScanPolicy.agrees(entry.target.name,name)) return
        val action=languageCopies.observe(event,entry.language,entry.languageOrigin,entry.manualLanguage,code,
            origin=="title",true,photo!=null,sampleAt,now)
        trace.event("triple.languageCopy event=$event action=$action origin=$origin captureAgeMs=${now-sampleAt}")
        if(action==TripleLanguageCopyPolicy.Action.KEEP || action==TripleLanguageCopyPolicy.Action.WAIT) return
        if(action==TripleLanguageCopyPolicy.Action.CONFLICT || repeatNeedsTap && action==TripleLanguageCopyPolicy.Action.SPLIT) {
            entry.physicalLanguage=code;entry.languageMismatch=true;languageConflict=true
            bindGallery(entry);saveEntry(entry);renderResult();return
        }
        val destination=if(action==TripleLanguageCopyPolicy.Action.SPLIT) {
            // Preserve the old row/quantity/photo/finish. The printing for the NEW language
            // is a hypothesis until independently resolved; never inherit a manual edition.
            val next=entry.target.copy(source="OCR",provisional=true,finish=entry.selectedFinish)
            arbiter.separateLanguageCopy(sampleAt,now);encounters.nextEncounter();pendingPhotos.clear()
            acceptedCaptureAt=sampleAt;accept(next)
            requireNotNull(galleryEntries[currentEncounter.toString()]).also { it.photo=photo }
        } else entry
        destination.language=code;destination.languageOrigin=origin;destination.physicalLanguage=code
        observedLanguage=code;evidenceKey=destination.target.id
        identifiedLanguages[destination.target.id]=code;preview.setImageLanguage(destination.target.printing,code)
        languageConsensus.confirmExactTitle(currentEncounter.toString(),code)
        bindGallery(destination);saveEntry(destination);renderControls();renderResult()
    }
    private val rarityRequested=mutableSetOf<String>()
    private fun offer(incoming:Target,sampleAt:Long,source:String=incoming.source,pairedVisual:Boolean=false):Boolean =
        offerWithEvidence(incoming,sampleAt,source,pairedVisual,false)
    // The explicit per-capture proof is never stored on Target or inherited by a later weak reading.
    private fun offerVisual(incoming:Target,sampleAt:Long,identityConfirmed:Boolean):Boolean =
        offerWithEvidence(incoming,sampleAt,"EdScan",false,identityConfirmed)
    private fun offerWithEvidence(incoming:Target,sampleAt:Long,source:String,pairedVisual:Boolean,identityConfirmed:Boolean):Boolean {
        val strongVisual=source=="EdScan" && (identityConfirmed || !incoming.provisional)
        if(choosingLanguage || restoringSession || !resumed || closed) return false
        if(repeatNeedsTap && TripleReadingPolicy.identity(incoming.name)==repeatIdentity) return false
        if(!TripleScanScopePolicy.allows(allowedSets,incoming.set)) {
            trace.event("triple.scopeRejected source=$source set=${incoming.set}");return false
        }
        val now=SystemClock.elapsedRealtime()
        val previous=galleryEntries[currentEncounter.toString()]
        if(strongVisual) {
            proposedVisualTargets[TripleReadingPolicy.identity(incoming.name)]=VisualProposal(incoming,sampleAt,arbiter.scene)
            if(proposedVisualTargets.size>5) proposedVisualTargets.remove(proposedVisualTargets.keys.first())
        }
        val arbitrationStarted=System.nanoTime()
        val decision=arbiter.observe(TripleReadingPolicy.identity(incoming.name),source,
            strongVisual,sampleAt,now,pairedVisual,artEnabled || ocrEnabled)
        EdScanPerf.record("triple.arbitration",System.nanoTime()-arbitrationStarted)
        confirmationPending=TripleScanPolicy.confirmationNeeded(confirmationPending,arbiter.needsConfirmation,decision.reason)
        val logKey="${decision.reason}:$source:${incoming.id}:${arbiter.scene}"
        if(logKey!=lastArbitrationLog) {
            lastArbitrationLog=logKey
            trace.event("triple.arbitration event=$currentEncounter scene=${arbiter.scene} target=${incoming.id.hashCode()} source=$source accepted=${decision.accepted} newCopy=${decision.newEncounter} reason=${decision.reason} ageMs=${now-sampleAt}")
        }
        if(!decision.accepted) {
            if(decision.reason=="identity_conflict" && previous!=null && !previous.manualEdition) {
                previous.recognitionConflict=true;bindGallery(previous);saveEntry(previous)
            }
            if(decision.reason=="identity_conflict") result.setText(R.string.triple_recognition_conflict)
            else if(previous==null) result.setText(R.string.triple_resolving)
            return false
        }
        if(source=="AR" && !decision.newEncounter && previous!=null && (artEnabled || ocrEnabled) &&
            previous.target.printing!=incoming.printing) return false
        if(strongVisual || source=="AR" && !incoming.provisional) {
            // A valid AR acceptance is complete too: EdScan disagreement about a variant
            // must not keep full inference running continuously after the beep.
            lastAcceptedVisualId=incoming.id;confirmationPending=false
        }
        if(!decision.newEncounter && previous!=null) {
            if(source=="OCR") previous.recognitionConflict=false
            if(previous.manualEdition && !TripleScanPolicy.agrees(previous.target.name,incoming.name)) return false
            if(source=="AR" && ((artEnabled || ocrEnabled) || previous.target.source=="EdScan" && !previous.target.provisional))
                return previous.target.printing==incoming.printing // Confirm, but do not downgrade/rebind/save every AR callback.
            encounters.rebindIdentity(TripleReadingPolicy.identity(incoming.name))
        } else if(decision.newEncounter) encounters.nextEncounter()
        if(decision.newEncounter) {
            trace.event("triple.acceptInterval deltaMs=${if(lastAcceptedAt==0L) -1 else now-lastAcceptedAt} source=$source scene=${arbiter.scene}")
            lastAcceptedAt=now
        }
        acceptedCaptureAt=sampleAt
        val acceptStarted=System.nanoTime()
        accept(incoming)
        EdScanPerf.record("triple.acceptUi",System.nanoTime()-acceptStarted)
        return true
    }
    private fun accept(incoming:Target) {
        if(choosingLanguage) return
        val previous=galleryEntries[currentEncounter.toString()]
        val event=encounters.observe(TripleReadingPolicy.identity(incoming.name))
        val sameEvent=event==currentEncounter
        val target=if(sameEvent && previous!=null && TripleScanPolicy.agrees(previous.target.name,incoming.name) && TripleReadingPolicy.keepCurrent(previous.manualEdition,
                previous.target.printing,previous.target.provisional,incoming.printing,incoming.provisional)) previous.target else incoming
        if(sameEvent && previous!=null && !TripleScanPolicy.agrees(previous.target.name,target.name) && !previous.manualLanguage) {
            previous.language=null;previous.languageOrigin=""
        }
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
        if(!sameEvent) lockConsecutiveRepeat()
    }
    private fun inferPrintingLanguage(target:Target) {
        // Physical language comes from the saved photo, never the reference printing while OCR is enabled.
        if(ocrEnabled && target.source in setOf("EdScan","AR")) return
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
            if(!languageConflict && galleryEntries[event.toString()]?.languageOrigin !in setOf("title","rules","photo")) {
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
        if(!entry?.language.isNullOrBlank() && entry?.language!=code) return // physical changes go through the copy policy
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
        val languageSpinner=ProgressBar(this).apply {
            visibility=View.GONE;contentDescription=getString(R.string.triple_language_analyzing)
            tag="triple_language_loading"
        }
        picture.addView(languageSpinner,FrameLayout.LayoutParams(dp(18),dp(18),android.view.Gravity.TOP or android.view.Gravity.START))
        val foilBadge=ImageView(this).apply {
            tag="triple_gallery_foil";setImageResource(R.drawable.ic_foil_badge)
            contentDescription=getString(R.string.foil);visibility=View.GONE
        }
        picture.addView(foilBadge,FrameLayout.LayoutParams(dp(24),dp(24),android.view.Gravity.BOTTOM or android.view.Gravity.END))
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
        entry.languageSpinner=languageSpinner
        entry.foilBadge=foilBadge
        val flip=ImageButton(this).apply {
            tag="triple_gallery_flip";setBackgroundColor(0xCC202430.toInt());setPadding(dp(12),dp(12),dp(12),dp(12))
        }
        picture.addView(flip,FrameLayout.LayoutParams(dp(48),dp(48),android.view.Gravity.BOTTOM or android.view.Gravity.START))
        entry.faceToggle=CardFaceToggle(flip) { url ->
            CardImageCache.display(this,url,entry.image)
        }
        entry.faceToggle?.bind(target.image,target.id)
        galleryEntries[entryKey]=entry
        column.setOnClickListener { showEntryMenu(entry,column,true) }
        recent.addView(column,0,LinearLayout.LayoutParams(dp(104),-1))
        if(recent.childCount>20) {
            val old=recent.getChildAt(20)
            galleryEntries.remove(old.tag as String)?.let { it.faceToggle?.close();com.bumptech.glide.Glide.clear(it.image) }
            recent.removeViewAt(20)
        }
        bindGallery(entry)
    }
    private fun updateEntryTarget(entry:GalleryEntry,target:Target) {
        if(entry.target.printing!=target.printing) {
            entry.amount=null;entry.priceRequested="";entry.pricePrinting=""
        }
        entry.target=target
        entry.faceToggle?.bind(target.image,target.id)
    }
    private fun bindGallery(entry:GalleryEntry) {
        renderSessionTotal(entry)
        val t=entry.target
        entry.foilBadge?.visibility=if(CardFinish.isFoil(entry.selectedFinish)) View.VISIBLE else View.GONE
        entry.languageSpinner?.visibility=if(entry.languagePending) View.VISIBLE else View.GONE
        entry.flag.text=if(entry.languagePending) getString(R.string.triple_language_loading_icon) else TripleLanguageBadge.label(entry.language)
        entry.flag.contentDescription=if(entry.languagePending) getString(R.string.triple_language_analyzing) else getString(R.string.triple_language,entry.flag.text)
        entry.edition.text=(if(entry.saveError) "! " else if(entry.savedId==null) "… " else "✓ ")+"${entry.count}× · "+if(t.set.isBlank()) t.label else t.set.uppercase()+if(t.number.isNotBlank()) " #${t.number}" else ""
        entry.edition.contentDescription=t.label
        if(entry.scopeConflict || entry.recognitionConflict) entry.edition.text="⚠ "+entry.edition.text
        entry.price.text=if(entry.finishUnavailable) getString(R.string.triple_finish_review_short) else if(t.printing.isBlank()) getString(R.string.triple_edition_pending) else io.asv.mtgocr.ocrreader.data.SelectedPrices.quote(this,entry.pricePrinting.ifBlank { t.printing },entry.selectedFinish)?.let {
            PriceSourcePresentation.append(this,io.asv.mtgocr.ocrreader.data.PriceCurrency.format(this,it.amount,it.currency),it.provider)
        } ?: getString(R.string.triple_price_pending)
        val priceUuid=entry.pricePrinting.ifBlank { t.printing }
        val priceFinish=entry.selectedFinish
        io.asv.mtgocr.ocrreader.data.SelectedPrices.ensurePrintings(this,listOf(priceUuid to priceFinish),Runnable {
            if(!closed && entry.count>0 && entry.target==t && entry.selectedFinish==priceFinish) bindGallery(entry)
        })
        entry.price.contentDescription=entry.price.text
        entry.root.alpha=if(entry.saveError) .55f else 1f
        entry.root.contentDescription=t.label+" · "+getString(if(entry.saveError) R.string.triple_save_failed else if(entry.savedId==null) R.string.triple_save_pending else R.string.triple_saved)
    }
    private fun schedulePhotoLanguage(entry:GalleryEntry) {
        if(!ocrEnabled || closed || entry.languageAttempted || entry.manualLanguage ||
            !entry.language.isNullOrBlank() || entry.count<=0 || entry.target.source !in setOf("EdScan","AR")) return
        val photo=entry.photo ?: return
        val identity=entry.target.name
        val revision=entry.editionRevision
        val photoKey=entry.photoKey
        entry.languageAttempted=true;entry.languagePending=true
        bindGallery(entry)
        photoLanguage.submitReport(photo,identity) { report ->
            val code=report.decision.code
            entry.languagePending=false
            if(closed) return@submitReport
            val sameRow=galleryEntries[entry.root.tag.toString()]===entry
            if(TriplePhotoLanguagePolicy.canApply(sameRow,entry.count,entry.manualLanguage,entry.language,
                    entry.photoKey==photoKey && entry.editionRevision==revision && TripleScanPolicy.agrees(identity,entry.target.name))) {
                if(!(BuildConfig.DEBUG && intent.getBooleanExtra("triple_diagnostic_only",false))) {
                    val evidence=report.json()
                    languageWorker.execute {
                        runCatching { ScanReviewStore.write(java.io.File(filesDir,"edscan/$photoKey.language-resolution.json"),evidence) }
                            .onFailure { trace.event("triple.photoLanguageWrite failed=true") }
                    }
                }
                code?.let { entry.language=it;entry.physicalLanguage=it;entry.languageOrigin="photo";saveEntry(entry) }
                if(entry.root.tag.toString()==currentEncounter.toString()) {
                    observedLanguage=entry.language;evidenceKey=entry.target.id
                    renderResult()
                }
            }
            if(sameRow) bindGallery(entry)
        }
    }
    private fun saveEntry(entry:GalleryEntry) {
        schedulePhotoLanguage(entry)
        renderSessionTotal(entry)
        if(BuildConfig.DEBUG && intent.getBooleanExtra("triple_diagnostic_only",false)) return
        val event=entry.root.tag.toString().toLongOrNull() ?: return
        val reviewSource=TriplePersistencePolicy.reviewSource(entry.target.source,entry.target.provisional,entry.recognitionConflict)
        val signature=listOf(entry.target.id,entry.target.provisional,entry.recognitionConflict,reviewSource,entry.language,
            entry.manualLanguage,entry.languageOrigin,entry.manualEdition,entry.selectedFinish,entry.count,entry.photo!=null,entry.physicalLanguage,entry.languageMismatch).joinToString("|")
        if(entry.saveSignature==signature || (entry.count>0 && entry.target.printing.isBlank())) return
        entry.saveSignature=signature;entry.saveError=false;entry.finishUnavailable=false
        val state=TripleCollectionSession.State(event,entry.target.copy(provisional=entry.target.provisional || entry.recognitionConflict),entry.language,entry.selectedFinish,entry.count,entry.photo,entry.manualLanguage,entry.photoKey,entry.editionOption,entry.languageOrigin,entry.manualEdition,entry.physicalLanguage,entry.languageMismatch)
        collectionSession.submit(state) { card,option,error ->
            if(closed || entry.saveSignature!=signature) return@submit
            entry.saveError=error!=null
            entry.finishUnavailable=error is TripleCollectionSession.FinishUnavailable
            if(error!=null) {
                trace.event("triple.saveError event=$event type=${error.javaClass.simpleName}")
                Toast.makeText(this,if(entry.finishUnavailable) R.string.triple_finish_review else R.string.triple_save_failed,Toast.LENGTH_LONG).show()
            }
            card?.let {
                entry.savedId=it.collectionItemId;addedCollectionIds.add(it.collectionItemId)
                setResult(RESULT_OK,Intent().putStringArrayListExtra(RapidEditionScanActivity.EXTRA_SESSION_CARD_IDS,ArrayList(addedCollectionIds)))
            }
            option?.let {
                entry.pricePrinting=it.printingUuid
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
    private var entryPopup:PopupWindow?=null
    private fun showEntryMenu(entry:GalleryEntry,anchor:View,history:Boolean) {
        if(entry.count<=0) return
        entryPopup?.dismiss()
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(0xFF202430.toInt()) }
        val popup=PopupWindow(body,dp(300),-2,true).apply { elevation=dp(8).toFloat();setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true }
        body.addView(TextView(this).apply { text=entry.target.label;setTextColor(Color.WHITE) })
        entryPopup=popup
        val idleHandler=android.os.Handler(android.os.Looper.getMainLooper())
        val hide=Runnable { popup.dismiss() }
        var clearImage:()->Unit={}
        popup.setOnDismissListener { idleHandler.removeCallbacks(hide);clearImage();if(entryPopup===popup) entryPopup=null }
        addEntryControls(entry,body,history,quickEdit={
            idleHandler.removeCallbacks(hide);idleHandler.postDelayed(hide,1000L)
        }) { popup.dismiss() }
        if(history) {
            val bounds=android.graphics.Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
            val width=(bounds.width()-dp(16)).coerceAtLeast(dp(200))
            body.measure(View.MeasureSpec.makeMeasureSpec(dp(200),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val size=TripleGalleryPopupLayout.fit(width,(bounds.height()-dp(24)).coerceAtLeast(1),
                body.measuredHeight,dp(128),dp(8))
            val picture=ImageView(this).apply {
                scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription=entry.target.label
            }
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL;setBackgroundColor(0xFF202430.toInt())
                addView(FrameLayout(this@TripleScanActivity).apply {
                    addView(picture,FrameLayout.LayoutParams(-1,-1))
                },LinearLayout.LayoutParams(size.cardWidth,size.height))
                addView(android.widget.ScrollView(this@TripleScanActivity).apply { addView(body) },
                    LinearLayout.LayoutParams(size.menuWidth,size.height).apply { leftMargin=dp(8) })
            }
            popup.contentView=row;popup.width=width;popup.height=size.height
            val flip=ImageButton(this).apply {
                tag="triple_detail_flip";setBackgroundColor(0xCC202430.toInt());setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            (picture.parent as FrameLayout).addView(flip,FrameLayout.LayoutParams(dp(48),dp(48),android.view.Gravity.BOTTOM or android.view.Gravity.START))
            val toggle=CardFaceToggle(flip) { url -> CardImageCache.display(this,url,picture) }
            toggle.bind(entry.faceToggle?.displayedUrl ?: entry.target.image,entry.target.id)
            clearImage={ toggle.close();com.bumptech.glide.Glide.clear(picture) }
        }
        popup.showAsDropDown(anchor)
    }
    private fun addEntryControls(entry:GalleryEntry,body:LinearLayout,history:Boolean,quickEdit:()->Unit={},dismiss:()->Unit) {
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
        if(entry.finishUnavailable) body.addView(TextView(this).apply {
            setText(R.string.triple_finish_review);setTextColor(Color.YELLOW)
        })
        if(history && entry.scopeConflict) body.addView(TextView(this).apply { setText(R.string.triple_outside_scope);setTextColor(Color.YELLOW) })
        body.addView(CheckBox(this).apply {
            setText(R.string.foil);setTextColor(Color.WHITE);buttonTintList=android.content.res.ColorStateList.valueOf(Color.CYAN);isChecked=entry.selectedFinish=="foil";tag="triple_edit_foil"
            setOnCheckedChangeListener { _,value ->
                entry.manualFinish=true;entry.selectedFinish=if(value) "foil" else "nonfoil";entry.amount=null
                preview.refreshSessionCard(ArPreloadCard(entry.target.printing,entry.target.image,entry.target.label,price(entry.target.copy(finish=entry.selectedFinish)),entry.target.rarity,entry.target.set,entry.target.number))
                saveEntry(entry);bindGallery(entry);quickEdit();renderControls()
            }
        })
        if(entry.language.isNullOrBlank() || entry.manualLanguage || history) body.addView(Button(this).apply {
            text=getString(R.string.review_language)+" · "+TripleLanguageBadge.label(entry.language);tag="triple_edit_language"
            setOnClickListener {
                val codes=listOf("","es","pt","en","fr","de","it","ja","ko","ru","zhs","zht")
                androidx.appcompat.app.AlertDialog.Builder(this@TripleScanActivity).setTitle(R.string.review_choose_language)
                    .setItems(codes.map { if(it.isBlank()) getString(R.string.review_language_unknown) else TripleLanguageBadge.label(it) }.toTypedArray()) { _,index ->
                        entry.language=codes[index].ifBlank { null };entry.manualLanguage=true;entry.languageOrigin="manual";entry.languageMismatch=false
                        if(entry.language==null) identifiedLanguages.remove(entry.target.id) else identifiedLanguages[entry.target.id]=entry.language!!
                        if(entry.root.tag.toString()==currentEncounter.toString()) {
                            observedLanguage=entry.language;languageConsensus.reset();languageCopies.reset();languageConflict=false;evidenceKey=entry.target.id
                            preview.setImageLanguage(entry.target.printing,entry.language ?: "und")
                        }
                        saveEntry(entry);bindGallery(entry);dismiss();renderControls()
                    }.show()
            }
        })
        if(history) {
            val row=LinearLayout(this)
            fun button(label:String,action:()->Unit) { row.addView(Button(this).apply { text=label;setOnClickListener { action() } },LinearLayout.LayoutParams(0,-2,1f)) }
            fun remove() { dismiss();androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.recent_delete_confirm)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_->
                    entry.count=0;saveEntry(entry);recent.removeView(entry.root);dismiss();renderControls()
                }.show() }
            val quantity=TextView(this).apply { text=entry.count.toString();setTextColor(Color.WHITE) }
            button("−") { if(entry.count<=1) remove() else { entry.count--;quantity.text=entry.count.toString();saveEntry(entry);bindGallery(entry);quickEdit() } }
            row.addView(quantity)
            button("+") { if(entry.count<99) { entry.count++;quantity.text=entry.count.toString();saveEntry(entry);bindGallery(entry) };quickEdit() }
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
        val conflict=galleryEntries[currentEncounter.toString()]?.recognitionConflict==true || galleryEntries[currentEncounter.toString()]?.languageMismatch==true || languageConflict || galleryEntries[currentEncounter.toString()]?.scopeConflict==true || (allowedSets.isNotEmpty() && t.set.uppercase() !in allowedSets) || (observedName!=null && !TripleScanPolicy.agrees(t.name,observedName!!))
        val pending=if(t.provisional) getString(R.string.triple_provisional)+"\n" else ""
        result.text=pending+"${t.source} · ${t.label}\n${price(t)}\n"+getString(if(conflict) R.string.triple_conflict else R.string.triple_language,observedLanguage ?: "?")
    }
    @JvmOverloads private fun ocr(bitmap:Bitmap?,target:Target?,token:Int,key:String=target?.id ?: "ocr",sampleAt:Long=SystemClock.elapsedRealtime(),languagePhoto:ByteArray?=null) {
        val now=SystemClock.elapsedRealtime()
        val reviewPhoto=if(key=="ocr") pendingPhotos.remove(key) else pendingPhotos[key]
        // Fixed language + already cross-checked printing needs no recurring title OCR.
        if(fixedLanguage!=null && target?.id==lastAcceptedVisualId && !confirmationPending && arbiter.hasConfirmedScene) {
            bitmap?.recycle();busy.set(false);return
        }
        if(!ocrEnabled || bitmap==null || !valid(token,key)) { bitmap?.recycle();busy.set(false);return }
        if(!ocrScheduled.compareAndSet(false,true)) { bitmap.recycle();busy.set(false);return }
        lastOcrKey=key;lastOcrAt=now;val started=System.nanoTime();val sequence=++ocrSequence
        // Fixed language skips language classification, NOT the independent title check.
        val entry=galleryEntries[currentEncounter.toString()]
        val scopeLanguage=entry?.language?.takeIf { entry.manualLanguage } ?: fixedLanguage
        trace.event("triple.ocrStart captureAtMs=$sampleAt captureAgeMs=${now-sampleAt}")
        // Hold this lane through name resolution as well as language: otherwise a new frame
        // can change frameKey/sequence and silently invalidate a correct title callback.
        val laneStarted=SystemClock.elapsedRealtime();val laneEvent=currentEncounter
        var modelsMs=-1L;var namesMs=-1L;var languageMs=-1L;var expired=false
        val stages=TripleOcrStageGate {
            trace.event("triple.ocrLane captureAtMs=$sampleAt generation=$token event=$laneEvent totalMs=${SystemClock.elapsedRealtime()-laneStarted} captureAgeMs=${SystemClock.elapsedRealtime()-sampleAt} modelsMs=$modelsMs namesMs=$namesMs languageMs=$languageMs expired=$expired")
            finishOcr()
        }
        var matchedEvent:Long?=null
        val snapshotLanguage=TripleSnapshotLanguageGate<TripleLanguageFusion.Decision> { name,event,decision ->
            if(!closed && resumed && token==generation && event==currentEncounter && sampleAt>=arbiter.sceneAt &&
                SystemClock.elapsedRealtime()-sampleAt<=2500) {
                languageConflict=languageConflict || decision.conflict || TriplePrintingLanguagePolicy.conflicts(printingLanguage,decision.code)
                decision.code?.let { code -> applyPhysicalLanguage(name,code,
                    if(decision.source=="exactTitle") "title" else "rules",event,token,sampleAt,languagePhoto) }
            }
        }
        logHandler.postDelayed({ expired=true;stages.expire() },(sampleAt+2500-now).coerceAtLeast(0))
        text.process(InputImage.fromBitmap(bitmap,0),scopeLanguage).addOnSuccessListener { recognized ->
            modelsMs=SystemClock.elapsedRealtime()-laneStarted
            bitmap.recycle();ocrCompleted++;EdScanPerf.record("triple.ocr",System.nanoTime()-started)
            if(!valid(token,key) || sampleAt<arbiter.sceneAt) { stages.endModels();return@addOnSuccessListener }
            val lines=recognized.textBlocks.flatMap { it.lines }
            val titles=lines.filter { (it.boundingBox?.centerY() ?: 9999)<170 }.map { it.text }
            val rules=lines.filter { (it.boundingBox?.centerY() ?: 0) in 510..800 }.joinToString(" ") { it.text }
            evidenceKey=key
            observedName=null
            val direct=target?.name?.takeIf { expected->titles.any { TripleScanPolicy.agrees(expected,it) } }
            if(direct!=null) {
                observedName=direct
                if(proposeOcrName(direct,sampleAt)) {
                    matchedEvent=currentEncounter;snapshotLanguage.identify(direct,currentEncounter)
                }
            }
            else if(!nameLookupPending && titles.isNotEmpty()) {
                stages.beginName();nameLookupPending=true;val titleStarted=System.nanoTime()
                CardRepository.get(this).matchLocalOcrText(titles) { name ->
                    namesMs=(System.nanoTime()-titleStarted)/1000000
                    nameLookupPending=false;EdScanPerf.record("triple.titleMatch",System.nanoTime()-titleStarted)
                    trace.event("triple.titleResolved ms=${(System.nanoTime()-titleStarted)/1000000} captureAgeMs=${SystemClock.elapsedRealtime()-sampleAt} matched=${name!=null}")
                    if(valid(token,key) && sampleAt>=arbiter.sceneAt && sequence==ocrSequence && SystemClock.elapsedRealtime()-sampleAt<=2500) {
                        observedName=name?.canonicalName
                        name?.let {
                            if(!proposeOcrName(it.canonicalName,sampleAt)) return@let
                            matchedEvent=currentEncounter
                            snapshotLanguage.identify(it.canonicalName,currentEncounter)
                            applyTitleLanguage(it.canonicalName,titles,token,currentEncounter,it.displayName,it.language,sampleAt,languagePhoto)
                            if(reviewPhoto!=null) galleryEntries[currentEncounter.toString()]?.takeIf { row -> TripleScanPolicy.agrees(row.target.name,it.canonicalName) && row.photo==null }?.let { row->row.photo=reviewPhoto;saveEntry(row) }
                        }
                        if(candidate?.id==key) renderResult()
                        else if(key=="ocr" && name!=null) result.text=getString(R.string.triple_ocr_only,name.displayName,observedLanguage ?: "?")
                    }
                    stages.endName()
                }
            }
            if(scopeLanguage!=null || entry?.languageAttempted==true && target?.id==entry.target.id) {
                trace.event("triple.languageSkipped reason=fixedScope language=$scopeLanguage")
                stages.endModels();return@addOnSuccessListener
            }
            val langStart=System.nanoTime()
            val canonical=target?.name ?: candidate?.takeIf { it.id==key }?.name ?: observedName
            languages.detectText(rules,"und") { language ->
                // Exact localized-title lookup stays off the UI/camera worker and keeps ES/PT alias ambiguity.
                if(closed) { stages.endModels();return@detectText }
                languageWorker.execute {
                    val title=canonical?.let { titleLanguageIndex?.lookup(titles,listOf(it)) } ?: TitleLanguageIndex.Evidence()
                    val decision=TripleLanguageFusion.decide(rules,
                        TripleScanPolicy.language(language.languageCode,language.confidence),title)
                    runOnUiThread {
                        languageMs=(System.nanoTime()-langStart)/1000000
                        EdScanPerf.record("triple.language",System.nanoTime()-langStart)
                        // Keep the result if the slower name lookup has not returned yet.
                        // It may only be delivered with this capture's accepted identity.
                        snapshotLanguage.language(decision)
                        if((valid(token,key) && sequence==ocrSequence || matchedEvent==currentEncounter) && sampleAt>=arbiter.sceneAt) {
                            trace.event("triple.languageEvidence source=${decision.source} proposed=${decision.code ?: "und"} confirmed=${observedLanguage ?: "und"} conflict=${decision.conflict} titleCandidates=${title.candidates.sorted().joinToString(",")} confidence=${language.confidence} letters=${rules.count(Char::isLetter)} snapshot=true")
                            if(candidate?.id==key) renderResult()
                            else if(key=="ocr" && observedName!=null) result.text=getString(R.string.triple_ocr_only,observedName!!,observedLanguage ?: "?")
                            trace.event("triple.ocrComplete elapsedMs=${(System.nanoTime()-started)/1000000} languageConfirmed=${observedLanguage!=null} titleMatched=${observedName!=null}")
                        };stages.endModels()
                    }
                }
            }
        }.addOnFailureListener { modelsMs=SystemClock.elapsedRealtime()-laneStarted;bitmap.recycle();android.util.Log.w("EdScanPerf","Triple OCR failed",it);stages.endModels() }
        busy.set(false)
    }
    private var guideLookupPending=false
    private var guideSequence=0
    @JvmOverloads private fun guidedOcr(bitmap:Bitmap?,token:Int,photo:ByteArray?=null,sampleAt:Long=SystemClock.elapsedRealtime()) {
        if(bitmap==null) return
        if(!ocrEnabled || closed || !resumed || token!=generation || !ocrScheduled.compareAndSet(false,true)) { bitmap.recycle();return }
        val started=SystemClock.elapsedRealtime();val sequence=++guideSequence
        var finished=false
        var nameTimeout:Runnable?=null
        trace.event("triple.guideStart sequence=$sequence")
        fun valid()=!finished && !closed && resumed && ocrEnabled && sampleAt>=arbiter.sceneAt && token==generation && sequence==guideSequence && SystemClock.elapsedRealtime()-sampleAt<=2500
        fun finish() { if(!finished) { finished=true;nameTimeout?.let(logHandler::removeCallbacks);bitmap.recycle();finishOcr() } }
        fun attempt(latinOnly:Boolean) {
            val task=if(latinOnly) text.processLatin(InputImage.fromBitmap(bitmap,0)) else text.process(InputImage.fromBitmap(bitmap,0),fixedLanguage)
            task.addOnSuccessListener { reading ->
                if(!valid()) { finish();return@addOnSuccessListener }
                val lines=reading.textBlocks.flatMap { it.lines }.map { it.text }.filter { it.length>=2 }
                trace.event("triple.guideOcr ms=${SystemClock.elapsedRealtime()-started} lines=${lines.size} latinOnly=$latinOnly")
                fun noMatch() {
                    if(latinOnly && valid() && fixedLanguage==null) attempt(false)
                    else { if(!artEnabled && SystemClock.elapsedRealtime()-lastArSeen>1000) arbiter.missing(SystemClock.elapsedRealtime());lastOcrCanonical="";preview.showGuidedName(null);finish() }
                }
                if(lines.isEmpty()) { noMatch();return@addOnSuccessListener }
                guideLookupPending=true;val lookupStart=SystemClock.elapsedRealtime()
                // Models are complete here. Expire name resolution without holding the camera.
                nameTimeout=Runnable { finish() }.also {
                    logHandler.postDelayed(it,(sampleAt+2500-SystemClock.elapsedRealtime()).coerceAtLeast(0))
                }
                CardRepository.get(this).matchLocalOcrText(lines) { name ->
                    nameTimeout?.let(logHandler::removeCallbacks);nameTimeout=null
                    guideLookupPending=false
                    trace.event("triple.guideLookup ms=${SystemClock.elapsedRealtime()-lookupStart} matched=${name!=null} valid=${valid()} latinOnly=$latinOnly")
                    if(!valid()) { finish();return@matchLocalOcrText }
                    if(name==null) noMatch() else {
                        preview.showGuidedName(name.displayName)
                        if(!proposeOcrName(name.canonicalName,sampleAt)) { finish();return@matchLocalOcrText }
                        if(photo!=null) galleryEntries[currentEncounter.toString()]?.takeIf { TripleScanPolicy.agrees(it.target.name,name.canonicalName) && it.photo==null }?.let { it.photo=photo;saveEntry(it) }
                        candidate?.takeIf { it.printing.isBlank() }?.let { current ->
                            val localized=current.copy(label=name.displayName)
                            candidate=localized;upsertGallery(localized);renderResult()
                        }
                        applyTitleLanguage(name.canonicalName,lines,token,currentEncounter,name.displayName,name.language,sampleAt,photo)
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
            trace.event("triple.feedback event=$currentEncounter beep=$played captureAtMs=$acceptedCaptureAt captureToBeepMs=${SystemClock.elapsedRealtime()-acceptedCaptureAt}")
        } catch(error:RuntimeException) { android.util.Log.w("EdScanPerf","Recognition beep unavailable",error) }
        recent.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
    private fun finishOcr() { ocrScheduled.set(false);if(closed) { text.close();languages.close() } }
    override fun onResume() { super.onResume();trace.event("triple.lifecycle state=resume");io.asv.collectorvision.EdScanInferencePriority.acquire(this);resumed=true;logHandler.removeCallbacks(heartbeat);logHandler.post(heartbeat); }
    override fun onPause() {
        trace.event("triple.lifecycle state=pause");entryPopup?.dismiss(); io.asv.collectorvision.EdScanInferencePriority.release(this);resumed=false;logHandler.removeCallbacks(heartbeat);invalidateEvidence();super.onPause() }
    override fun onDestroy() {
        galleryEntries.values.forEach { it.faceToggle?.close() }
        languagePickerRevision++;languagePicker?.dismiss()
        if(setLanguagesLazy.isInitialized()) setLanguages.close()
        if(printingLanguagesLazy.isInitialized()) printingLanguages.close()
        closed=true;generation++;if(photoLanguageLazy.isInitialized()) photoLanguage.close();scanTone?.release();scanTone=null;preview.close();lookup.close();trace.close()
        worker.execute { engine?.close();engine=null };worker.shutdown();languageWorker.shutdown()
        if(!ocrScheduled.get()) { text.close();languages.close() };super.onDestroy()
    }
}
