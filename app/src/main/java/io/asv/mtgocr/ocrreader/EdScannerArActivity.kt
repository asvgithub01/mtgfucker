package io.asv.mtgocr.ocrreader



import android.content.Intent

import android.os.Bundle

import android.widget.ArrayAdapter

import android.widget.LinearLayout

import android.widget.Spinner

import android.widget.TextView

import android.widget.Toast

import io.asv.collectorvision.NativeCollectorVisionActivity

import io.asv.mtgocr.ocrreader.data.CardRepository

import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore

import java.util.concurrent.Executors



/** Isolated POC host copied from EdScan at 818c5dc. Native inference and save policy are unchanged. */

class EdScannerArActivity : NativeCollectorVisionActivity() {

    companion object { const val EXTRA_DESTINATION_GROUP = "edscan_destination_group" }

    override val usesExternalCamera=true

    override val automaticIdlePauseEnabled=false

    @Volatile private var batchMode=false

    private var arPreview: EdScannerArPreview?=null

    private var arSlot: android.widget.FrameLayout?=null

    private var arTargetId=""

    private var arTarget: io.asv.mtgocr.ocrreader.data.CardEditionOption?=null

    private var selectedArPackage="FRA"

    private val arPackagePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->

        if(result.resultCode==RESULT_OK) result.data?.getStringExtra("package_id")?.let { id ->

            selectedArPackage=id

            getSharedPreferences("edscan_ar",MODE_PRIVATE).edit().putString("preload_set",id).apply()

            if(batchMode) loadLastArBatch()

            else findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<android.widget.CheckBox>("ar_preload_latest").isChecked=true

        }

    }

    private fun chooseArPackage(id: String?=null) {

        if(!PremiumAccess.isEnabled(this)) { Toast.makeText(this,R.string.premium_required,Toast.LENGTH_LONG).show();return }

        arPackagePicker.launch(Intent(this,ArDownloadsActivity::class.java).putExtra("choose",true).putExtra("package_id",id))

    }

    private val arPermission=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {

        arPreview?.retryStart()

    }

    private val warmedCandidates=linkedSetOf<String>()

    override fun prepareVisualCandidate(cardId: String) {

        if(batchMode || !warmedCandidates.add(cardId)) return

        if(warmedCandidates.size>16) warmedCandidates.remove(warmedCandidates.first())

        CardRepository.get(this).loadLocalArReference(cardId) { printing,image ->

            if(!isDestroyed && !isFinishing && printing!=null && !image.isNullOrBlank())

                arPreview?.prepareReference(printing,image)

        }

    }

    private fun openAr() {

        val target=arTarget ?: return

        if(isDestroyed || isFinishing) return

        val currentPrice=findViewById<android.view.ViewGroup>(android.R.id.content)

            .findViewWithTag<TextView>("cornelius_price")?.text?.toString().orEmpty()

        arPreview?.updateTarget(Intent().putExtra("printing",target.printingUuid).putExtra("image",target.imageUrl)

            .putExtra("label","${target.cardName} · ${target.setCode} #${target.collectorNumber}")

            .putExtra("price",currentPrice).putExtra("scryfallId",arTargetId)

            .putExtra("finish",target.finish).putExtra("currency",priceCurrency)

            .putExtra("pricePending",target.price==null && !currentPrice.contains("\n")))

    }

    private fun installArLayout() {

        val root=findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout

        (recent.parent as? android.view.ViewGroup)?.removeView(recent)

        val options=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }

        val children=(0 until root.childCount).map { root.getChildAt(it) }

        root.removeAllViews(); root.setPadding(0,0,0,0)

        children.filterNot { it is androidx.camera.view.PreviewView }.forEach { options.addView(it) }

        val scroll=android.widget.ScrollView(this).apply { tag="ar_options"; addView(options); visibility=android.view.View.GONE }

        val toolbar=LinearLayout(this)

        toolbar.addView(android.widget.Button(this).apply {

            tag="ar_options_toggle"; setText(R.string.edscan_ar_options)

            setOnClickListener {

                val opening=scroll.visibility!=android.view.View.VISIBLE

                scroll.visibility=if(opening) android.view.View.VISIBLE else android.view.View.GONE

                setText(if(opening) R.string.edscan_ar_hide_options else R.string.edscan_ar_options)

                if(!opening) {

                    currentFocus?.clearFocus()

                    (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken,0)

                }

            }

        },LinearLayout.LayoutParams(0,-2,1f))

        toolbar.addView(android.widget.Button(this).apply { setText(io.asv.collectorvision.R.string.cv_native_close); setOnClickListener { finish() } })

        root.addView(toolbar)

        recent.tag="ar_recent"

        root.addView(recent,LinearLayout.LayoutParams(-1,(104*resources.displayMetrics.density).toInt()))

        val slot=android.widget.FrameLayout(this).also { arSlot=it }

        root.addView(slot,LinearLayout.LayoutParams(-1,0,1f))

        root.addView(scroll,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*.38f).toInt()))

        arPreview=EdScannerArPreview(this,Intent(),{ acceptsExternalFrame && !batchMode },{ image,rotation,release ->

            submitExternalFrame(rotation,{

                val crop=image.cropRect

                val planes=image.planes.map { ArYuvRgb.Plane(it.buffer,it.rowStride,it.pixelStride) }

                val pixels=ArYuvRgb.pixels(crop.width(),crop.height(),crop.left,crop.top,planes[0],planes[1],planes[2])

                val raw=android.graphics.Bitmap.createBitmap(pixels,crop.width(),crop.height(),android.graphics.Bitmap.Config.ARGB_8888)

                if(rotation==0) raw else android.graphics.Bitmap.createBitmap(raw,0,0,raw.width,raw.height,

                    android.graphics.Matrix().apply { postRotate(rotation.toFloat()) },true).also { if(it!==raw) raw.recycle() }

            },release)

        },options).also { slot.addView(it,android.widget.FrameLayout.LayoutParams(-1,-1)) }

        options.addView(android.widget.CheckBox(this).apply {

            tag="ar_preload_latest"; setText(R.string.edscan_ar_preload_mode); isChecked=batchMode

            setTextColor(android.graphics.Color.WHITE)

            setOnCheckedChangeListener { button,enabled ->

                if(enabled==batchMode) return@setOnCheckedChangeListener

                if(scanSavePending) { button.isChecked=batchMode; Toast.makeText(this@EdScannerArActivity,io.asv.collectorvision.R.string.cv_native_saving,Toast.LENGTH_SHORT).show(); return@setOnCheckedChangeListener }

                batchMode=enabled

                getSharedPreferences("edscan_ar",MODE_PRIVATE).edit().putBoolean("preload_latest",enabled).apply()

                if(enabled) { pauseScanner(); loadLastArBatch() }

                else { batchLoadGeneration++; arPreview?.setPreloadedBatch(null,emptyList()); resumeScanner() }

            }

        })

        options.addView(android.widget.Button(this).apply {

            tag="ar_preload_select"; setText(R.string.edscan_ar_preload_select)

            setOnClickListener {

                if(scanSavePending) return@setOnClickListener

                chooseArPackage()

            }

        })

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)

            arPermission.launch(android.Manifest.permission.CAMERA)

    }

    private var batchLoadGeneration=0

    private fun loadLastArBatch() {

        val generation=++batchLoadGeneration

        val started=android.os.SystemClock.elapsedRealtime()

        arPreview?.setPreloadedBatch("",emptyList())

        // Legacy batch access is only retained for explicit diagnostic tests.

        if(!intent.hasExtra("edscan_preload_group")) {

            if(!PremiumAccess.isEnabled(this)) { arPreview?.showPreloadError(getString(R.string.premium_required));return }

            val id=selectedArPackage

            val app=applicationContext

            ArPackageStore.worker.execute {

                val result=runCatching { ArPackageStore.installed(app,id) }

                runOnUiThread {

                    if(isDestroyed || !batchMode || generation!=batchLoadGeneration) return@runOnUiThread

                    val installed=result.getOrNull()

                    if(installed==null) {

                        arPreview?.showPreloadError(getString(R.string.ar_package_missing,id))

                        androidx.appcompat.app.AlertDialog.Builder(this)

                            .setMessage(getString(R.string.ar_package_missing,id))

                            .setPositiveButton(R.string.ar_download_selected) { _,_ -> chooseArPackage(id) }

                            .setNegativeButton(android.R.string.cancel,null).show()

                        return@runOnUiThread

                    }

                    CardRepository.get(this).loadArPackagePrices(installed.references.associate { it.scryfallId to it.finish }) { prices ->

                        if(isDestroyed || !batchMode || generation!=batchLoadGeneration || !PremiumAccess.isEnabled(this)) return@loadArPackagePrices

                        val entries=installed.references.map { ref ->

                            val price=prices[ref.scryfallId]?.let { io.asv.mtgocr.ocrreader.data.PriceCurrency.format(this,it.first,it.second) }

                                ?: getString(io.asv.collectorvision.R.string.cv_native_price_unavailable)

                            ArPreloadCard(ref.printing,ref.image,ref.label,"${ref.label}\n$price · ${ref.finish}")

                        }

                        arPreview?.setPreloadedBatch(installed.entry.name,entries,started,installed.database)

                    }

                }

            }

            return

        }

        saves.execute {

            val cards=if(LibraryCatalog.activeFile(this)==libraryFile) LegacyCollectionStore.cards(this) else emptyList()

            val preferences=getSharedPreferences("edscan_ar",MODE_PRIVATE)

            val requested=intent.getStringExtra("edscan_preload_group") ?: preferences.getString("preload_group_$libraryFile",null)

            val latest=requested?.takeIf { name -> cards.any { name in it.groups } } ?: ArPreloadBatch.latestGroup(cards)

            if(latest!=null) preferences.edit().putString("preload_group_$libraryFile",latest).apply()

            val selected=latest?.let { ArPreloadBatch.select(cards,it) }.orEmpty()

            val entries=selected.map { card ->

                val amount=io.asv.mtgocr.ocrreader.data.PriceCurrency.amountOrNull(this,card)

                val price=amount?.let { io.asv.mtgocr.ocrreader.data.PriceCurrency.format(this,it,io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this)) }

                    ?: getString(io.asv.collectorvision.R.string.cv_native_price_unavailable)

                val label="${card.name} · ${card.setCode} #${card.collectorNumber}"

                ArPreloadCard(card.printingUuid,card.imgPath,label,"$label\n$price · ${card.finish}")

            }

            runOnUiThread { if(!isDestroyed && batchMode) arPreview?.setPreloadedBatch(latest ?: getString(R.string.edscan_ar_preload_empty),entries) }

        }

    }

    private lateinit var recent: RecentScansView

    private var debugKey: String? = null

    override fun acceptedDebug(key: String) { debugKey = key; if (::recent.isInitialized) recent.beginPending(key) }

    override fun acceptedPreview(bitmap: android.graphics.Bitmap) {

        debugKey?.let { recent.setPendingPreview(it, bitmap) }

    }

    private val identityResolver by lazy { EdScanIdentityResolver(this) }

    private var provisionalIds = emptyList<String>()

    override fun printingIdentities(): Map<String,String> = identityResolver.identities

    override fun requestPrintingIdentities(ids: List<String>) { identityResolver.request(ids) }

    override fun acceptedProvisional(ids: List<String>) { provisionalIds = ids }

    private var photoKey: String? = null

    override fun acceptedPhoto(key: String?) { photoKey = key }

    private val saves = Executors.newSingleThreadExecutor()

    private lateinit var language: Spinner

    private lateinit var finish: Spinner

    @Volatile private var group: String? = null

    private var libraryFile = ""

    private val addedIds = linkedSetOf<String>()

    private val sessionCards = linkedMapOf<String, io.asv.mtgocr.ocrreader.model.CardInfo>()

    private var recentGeneration = 0

    private lateinit var totalView: TextView

    private var sessionClock = ScanSessionClock()

    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var sessionSummary = EdScanSessionTotal(0, 0.0, 0)

    private val clockTick = object : Runnable {

        override fun run() {

            if (isDestroyed) return

            renderSessionTotal()

            clockHandler.postDelayed(this, 1000)

        }

    }

    override fun scanningStateChanged(running: Boolean) {

        val now = android.os.SystemClock.elapsedRealtime()

        clockHandler.removeCallbacks(clockTick)

        if (running) { sessionClock.resume(now); clockHandler.post(clockTick) }

        else { sessionClock.pause(now); renderSessionTotal() }

    }

    private fun renderSessionTotal() {

        if (!::totalView.isInitialized) return

        totalView.text = getString(io.asv.collectorvision.R.string.edscan_session_total, sessionSummary.copies,

            io.asv.mtgocr.ocrreader.data.PriceCurrency.format(this, sessionSummary.amount,

                io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this)), sessionSummary.unpricedCopies,

            ScanSessionClock.format(sessionClock.elapsed(android.os.SystemClock.elapsedRealtime())))

    }

    override val hostProvidesPrices = true

    private val languages = listOf("", "es", "pt", "en", "fr", "de", "it", "ja", "ko", "ru", "zhs", "zht")

    private val finishes = listOf("nonfoil", "foil", "etched")

    override val priceFinish: String get() = finishes[finish.selectedItemPosition]

    override val priceCurrency: String get() = io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this).lowercase()

    override val autoStartEnabled: Boolean get() = !batchMode && intent.getBooleanExtra("edscan_autostart", true)

    override val autoSaveEnabled: Boolean get() = !batchMode && !(BuildConfig.DEBUG && intent.getBooleanExtra("edscan_test_no_save",false))

    override val sessionLabel: String? get() = group



    override fun onCreate(savedInstanceState: Bundle?) {

        sessionClock = ScanSessionClock(savedInstanceState?.getLong("corneliusScanMillis") ?: 0L)

        group = CorneliusGroups.destination(savedInstanceState?.getString("corneliusGroup"),

            intent.getStringExtra(EXTRA_DESTINATION_GROUP))

        libraryFile = savedInstanceState?.getString("corneliusLibrary") ?: LibraryCatalog.activeFile(this)

        savedInstanceState?.getStringArrayList("corneliusIds")?.let { addedIds.addAll(it) }

        selectedArPackage=intent.getStringExtra("ar_package_id") ?: getSharedPreferences("edscan_ar",MODE_PRIVATE).getString("preload_set","FRA") ?: "FRA"

        batchMode=intent.getBooleanExtra("edscan_preload_last",getSharedPreferences("edscan_ar",MODE_PRIVATE).getBoolean("preload_latest",false))

        super.onCreate(savedInstanceState)

        installArLayout()

        if(batchMode) { if(!intent.hasExtra("ar_package_id") && setInput.codes().size==1) selectedArPackage=setInput.codes().single();pauseScanner();loadLastArBatch() }
        else if(!intent.hasExtra("edscan_preload_last") && PremiumAccess.isEnabled(this) && setInput.codes().size==1 && ArPackageStore.installedVersion(this,setInput.codes().single())!=null) {
            selectedArPackage=setInput.codes().single()
            findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<android.widget.CheckBox>("ar_preload_latest").isChecked=true
        }


        savedInstanceState?.let { language.setSelection(it.getInt("corneliusLanguage")); finish.setSelection(it.getInt("corneliusFinish")) }

    }



    override fun addHostControls(container: LinearLayout) {

        container.addView(TextView(this).apply { setText(R.string.edscan_ar_help); textSize=13f })

        container.addView(TextView(this).apply { setText(R.string.cornelius_manual_attributes); textSize = 12f })

        val row = LinearLayout(this)

        language = Spinner(this).apply {

            adapter = ArrayAdapter(this@EdScannerArActivity, android.R.layout.simple_spinner_dropdown_item,

                listOf(getString(R.string.cornelius_unknown_language)) + languages.drop(1).map { it.uppercase() })

        }

        finish = Spinner(this).apply {

            adapter = ArrayAdapter(this@EdScannerArActivity, android.R.layout.simple_spinner_dropdown_item, finishes)

        }

        row.addView(language, LinearLayout.LayoutParams(0, -2, 1f))

        row.addView(finish, LinearLayout.LayoutParams(0, -2, 1f))

        container.addView(row)

        val setRow=LinearLayout(this)

        val lock=android.widget.MultiAutoCompleteTextView(this).apply {

            hint=getString(R.string.edition_search_hint); setSingleLine(true)

            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE

        }

        setRow.addView(lock,LinearLayout.LayoutParams(0,-2,1f)); container.addView(setRow)

        setInput=ScannerSetInput(this,lock,"edscan_input",Runnable { updateSetLock() },Runnable {

            lockGeneration++; updateVisualScope(emptySet())

        })

        updateSetLock()

        val nameRow=LinearLayout(this)

        val name=android.widget.EditText(this).apply { hint=getString(R.string.card_name_hint); setSingleLine(true); imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE }

        val mic=android.widget.ImageButton(this).apply { setImageResource(R.drawable.ic_mic); imageTintList=android.content.res.ColorStateList.valueOf(name.currentTextColor); contentDescription=getString(R.string.speak_card_name) }

        val add=android.widget.ImageButton(this).apply { setImageResource(R.drawable.ic_add); contentDescription=getString(R.string.add_typed_card) }

        val size=(48*resources.displayMetrics.density).toInt()

        nameRow.addView(name,LinearLayout.LayoutParams(0,size,1f)); nameRow.addView(mic,LinearLayout.LayoutParams(size,size)); nameRow.addView(add,LinearLayout.LayoutParams(size,size))

        container.addView(nameRow)

        val suggestions=android.widget.ListView(this).apply { visibility=android.view.View.GONE }

        container.addView(suggestions,LinearLayout.LayoutParams(-1,(120*resources.displayMetrics.density).toInt()))

        nameInput=ScannerNameInput(this,name,suggestions,mic,add,java.util.function.Consumer { addManualName(it) })

    }



    override fun addHostRecentControls(container: LinearLayout) {

        totalView = TextView(this).apply { textSize = 14f }
        totalView.setOnClickListener { PriceDetailsDialog.show(this, addedIds.mapNotNull { sessionCards[it] }) }

        container.addView(totalView)

        recent = RecentScansView(this)

        container.addView(recent, LinearLayout.LayoutParams(-1,(104*resources.displayMetrics.density).toInt()))

        container.addView(android.widget.Button(this).apply {

            setText(R.string.postprocess_scans)

            setOnClickListener { startActivity(Intent(this@EdScannerArActivity, ScanReviewActivity::class.java)) }

        })

    }



    private lateinit var setInput: ScannerSetInput

    private lateinit var nameInput: ScannerNameInput

    private var lockGeneration=0

    private var manualBusy=false

    override val scanResumeAllowed: Boolean get() = !manualBusy && !batchMode

    private var appliedLocks=emptySet<String>()
    private var lastArScope:Set<String>?=null

    private fun updateSetLock() {

        val codes=setInput.codes()

        val token=++lockGeneration

        updateVisualScope(if(codes.isEmpty()) null else emptySet())

        appliedLocks=codes

        if(arPreview!=null && codes!=lastArScope) {
            lastArScope=codes
            if(codes.size==1 && PremiumAccess.isEnabled(this)) {
                selectedArPackage=codes.single()
                if(batchMode) loadLastArBatch()
                else if(ArPackageStore.installedVersion(this,selectedArPackage)!=null) {
                    findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<android.widget.CheckBox>("ar_preload_latest").isChecked=true
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage(getString(R.string.ar_package_missing,selectedArPackage))
                        .setPositiveButton(R.string.ar_download_selected) { _,_ -> chooseArPackage(selectedArPackage) }
                        .setNegativeButton(android.R.string.cancel,null).show()
                }
            } else if(codes.size>1 && batchMode) {
                batchLoadGeneration++; arPreview?.setPreloadedBatch("",emptyList());arPreview?.showPreloadError(getString(R.string.ar_one_package))
            }
        }
        if(codes.isEmpty()) return

        CardRepository.get(this).loadVisualSetIds(codes) { ids,error ->

            if(isDestroyed || token!=lockGeneration) return@loadVisualSetIds

            if(error!=null || ids.isEmpty()) {

                Toast.makeText(this,R.string.scan_card_not_found_in_selected_set,Toast.LENGTH_LONG).show()

                return@loadVisualSetIds

            }

            updateVisualScope(ids)

        }

    }

    private fun addManualName(name: String) {

        if(manualBusy || scanSavePending) { Toast.makeText(this,R.string.experimental_scan_saving_card,Toast.LENGTH_SHORT).show(); return }

        if(name.isBlank()) { Toast.makeText(this,R.string.empty_scan_name,Toast.LENGTH_SHORT).show(); return }

        // Stop inference while selecting/saving so the live candidate cannot overwrite manual state.

        pauseScanner(); manualBusy=true

        updateSetLock()

        val requestedSets=setInput.codes()

        val requestedFinish=finishes[finish.selectedItemPosition]

        val repo=CardRepository.get(this)

        repo.loadCard(name,false,false) { options,error ->

            if(isDestroyed || isFinishing) return@loadCard

            val allowed=io.asv.mtgocr.ocrreader.data.ScanSetLockPolicy.expand(requestedSets)

            val option=options.filter { it.finish==requestedFinish && (allowed.isEmpty() || it.setCode.uppercase() in allowed) }.maxByOrNull { it.releaseDate }

            if(error!=null || option==null || (requestedSets!=setInput.codes() || requestedFinish!=finishes[finish.selectedItemPosition])) {

                manualBusy=false; Toast.makeText(this,R.string.scan_card_not_found_in_selected_set,Toast.LENGTH_LONG).show(); return@loadCard

            }

            repo.loadVisualId(option.printingUuid) { id ->

                if(isDestroyed || isFinishing) return@loadVisualId

                if(id==null || (requestedSets!=setInput.codes() || requestedFinish!=finishes[finish.selectedItemPosition])) { manualBusy=false; Toast.makeText(this,R.string.scan_card_not_found,Toast.LENGTH_LONG).show(); return@loadVisualId }

                debugKey=null; photoKey=null; provisionalIds=emptyList()

                persistCandidate(id,1f) { ok ->

                    manualBusy=false

                    if(ok && !isDestroyed) { nameInput.clear(); manualScanFeedback() }

                }

            }

        }

    }



    private var saveError: String? = null

    override val persistenceFailureMessage: String? get() = saveError

    override fun persistCandidate(cardId: String, score: Float, callback: (Boolean) -> Unit) {

        saveError = null

        val acceptedDebugKey = debugKey

        val finished: (Boolean) -> Unit = { saved ->

            if (!saved && !isDestroyed) acceptedDebugKey?.let { recent.endPending(it) }

            callback(saved)

        }

        val acceptedLocks=appliedLocks.toSet()

        val acceptedPhotoKey = photoKey

        val acceptedProvisionalIds = provisionalIds.toList()

        acceptedDebugKey?.let { io.asv.collectorvision.EdScanDebug.record(applicationContext, it, "request", org.json.JSONObject()

            .put("language", languages[language.selectedItemPosition]).put("finish", finishes[finish.selectedItemPosition])

            .put("lockedSetCodes",org.json.JSONArray(acceptedLocks.toList()))

            .put("library", libraryFile).put("photoKey", acceptedPhotoKey ?: org.json.JSONObject.NULL)) }

        val selectedLanguage = languages[language.selectedItemPosition]

        val selectedFinish = finishes[finish.selectedItemPosition]

        val repository = CardRepository.get(this)

        val lookupStartedAt = android.os.SystemClock.elapsedRealtime()

        repository.loadCollectorVisionPrinting(cardId) { options, error ->

            val lookupMs = android.os.SystemClock.elapsedRealtime() - lookupStartedAt

            if (isFinishing || isDestroyed) { finished(false); return@loadCollectorVisionPrinting }

            val actualFinish = EdScanSavePolicy.finish(selectedFinish, options.map { it.finish }.toSet())

            val allowedSets=io.asv.mtgocr.ocrreader.data.ScanSetLockPolicy.expand(acceptedLocks)

            val option = options.firstOrNull { it.finish == actualFinish && (allowedSets.isEmpty() || it.setCode.uppercase() in allowedSets) }

            if (option == null) {

                acceptedDebugKey?.let { io.asv.collectorvision.EdScanDebug.record(applicationContext, it, "error", org.json.JSONObject()

                    .put("reason", "exact_printing_or_finish_unavailable").put("detail", error?.message ?: "")

                    .put("case", EdScanSavePolicy.errorCase(cardId, options.map { it.finish }.toSet(), selectedFinish))

                    .put("requestedFinish", selectedFinish).put("availableFinishes", org.json.JSONArray(options.map { it.finish }))) }

                val case = EdScanSavePolicy.errorCase(cardId, options.map { it.finish }.toSet(), selectedFinish)

                saveError = getString(when(case) {

                    3 -> R.string.edscan_error_back

                    2 -> R.string.edscan_error_finish

                    else -> R.string.edscan_error_catalog

                })

                Toast.makeText(this, saveError, Toast.LENGTH_LONG).show()

                android.util.Log.w("CorneliusSave", "Exact printing/finish unavailable for $cardId", error)

                finished(false)

                return@loadCollectorVisionPrinting

            }

            arTarget=option; arTargetId=cardId

            resolvedAcceptedFinish(cardId, option.finish)

            val localAmount = option.price?.takeIf { it.isFinite() && it >= 0 }

            if (localAmount != null) {

                val currency = option.currency.orEmpty().ifBlank { "EUR" }

                resolvedLocalPrice(cardId, option.finish, "${option.cardName} · ${option.setCode} #${option.collectorNumber}",

                    io.asv.mtgocr.ocrreader.data.PriceCurrency.convert(this, localAmount, currency),

                    io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this),

                    io.asv.mtgocr.ocrreader.data.PriceCurrency.amountIn(this,

                        io.asv.mtgocr.ocrreader.model.CardInfo("", "$localAmount $currency", "", "", "1"), "EUR") ?: localAmount, option.priceProvider.orEmpty())

            } else requestAcceptedPrice(cardId)

            // Visual feedback must not wait for legacy/Room persistence or remote prices.

            openAr()

            val queuedAt = android.os.SystemClock.elapsedRealtime()

            saves.execute {

                val saveStartedAt = android.os.SystemClock.elapsedRealtime()

                val queueMs = saveStartedAt - queuedAt

                try {

                    check(LibraryCatalog.activeFile(this) == libraryFile)

                    acceptedPhotoKey?.let { key ->

                        val metadata=java.io.File(io.asv.collectorvision.EdScanJobs.directory(this),"$key.json")

                        ScanReviewStore.json(metadata)?.let { json ->

                            ScanReviewStore.write(metadata,json.put("lockedSetCodes",org.json.JSONArray(acceptedLocks.toList())))

                        }

                    }

                    val sessionGroup = group ?: reserveGroup().also { group = it }

                    val card = LegacyCollectionStore.addCopy(this, option, selectedLanguage,

                        groupName = sessionGroup, expectedLibraryFile = libraryFile, mergeExisting = false)

                    if (acceptedProvisionalIds.isNotEmpty()) {

                        ScanReviewStore.writeProvisional(this, requireNotNull(acceptedDebugKey), acceptedPhotoKey,

                            libraryFile, card.collectionItemId, acceptedProvisionalIds)

                    }

                    val selectedAt = android.os.SystemClock.elapsedRealtime()

                    val localSaveMs = selectedAt - saveStartedAt

                    repository.selectEdition(card.collectionItemId, option) {

                        acceptedDebugKey?.let { io.asv.collectorvision.EdScanDebug.record(applicationContext, it, "stored", org.json.JSONObject()

                            .put("collectionItemId", card.collectionItemId).put("printingUuid", option.printingUuid)

                            .put("lookupMs", lookupMs).put("queueMs", queueMs).put("localSaveMs", localSaveMs)

                            .put("selectionMs", android.os.SystemClock.elapsedRealtime()-selectedAt).put("localPrice", localAmount ?: org.json.JSONObject.NULL)

                            .put("name", option.cardName).put("set", option.setCode).put("number", option.collectorNumber)

                            .put("language", selectedLanguage).put("finish", option.finish).put("requestedFinish", selectedFinish).put("group", sessionGroup).put("library", libraryFile)) }

                        acceptedPhotoKey?.let { io.asv.collectorvision.EdScanJobs.link(applicationContext, it, libraryFile, card.collectionItemId, option.printingUuid) }

                        if (localAmount == null) {

                        val priceWork = EdScanStoredPriceWorker.enqueue(this, libraryFile, card.collectionItemId, cardId, option.printingUuid, option.finish)

                        androidx.work.WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(priceWork).observe(this) { jobs ->

                            if (jobs.any { it.state == androidx.work.WorkInfo.State.SUCCEEDED }) refreshRecent()

                        }

                        }

                        addedIds += card.collectionItemId

                        recentGeneration++ // Invalidate any older disk snapshot before displaying this saved copy.

                        sessionCards[card.collectionItemId] = card.snapshotForPersistence()

                        acceptedDebugKey?.let { recent.completePending(it, card.collectionItemId, false) }

                        renderRecent()

                        setResult(RESULT_OK, Intent().putStringArrayListExtra(RapidEditionScanActivity.EXTRA_SESSION_CARD_IDS, ArrayList(addedIds)))

                        finished(true)

                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        if (!isDestroyed) Toast.makeText(this, R.string.cornelius_save_error, Toast.LENGTH_LONG).show()

                        android.util.Log.e("CorneliusSave", "Save failed", e)

                        finished(false)

                    }

                }

            }

        }

    }



    override fun onResume() { super.onResume(); if (::recent.isInitialized) refreshRecent() }

    private fun renderRecent() {

        val cards = addedIds.mapNotNull { sessionCards[it] }
        io.asv.mtgocr.ocrreader.data.SelectedPrices.ensure(this, cards, Runnable {
            if (!isFinishing && !isDestroyed) renderRecent()
        })

        recent.submit(cards) { card -> RecentScanActions.show(this, card) { action -> editRecent(card, action) } }

        val priced = cards.map { card -> card.quantityCount to io.asv.mtgocr.ocrreader.data.PriceCurrency.amountOrNull(this, card) }

        sessionSummary = EdScanSessionTotal.calculate(priced)

        renderSessionTotal()

    }

    private fun refreshRecent() {

        val version = ++recentGeneration

        val ids = addedIds.toList()

        saves.execute {

            val cards = if (LibraryCatalog.activeFile(this)==libraryFile) LegacyCollectionStore.cards(this) else emptyList()

            val byId = cards.associateBy { it.collectionItemId }

            runOnUiThread {

                if (!isDestroyed && !isFinishing && version == recentGeneration) {

                    sessionCards.clear()

                    ids.forEach { id -> byId[id]?.let { sessionCards[id] = it } }

                    renderRecent()

                }

            }

        }

    }

    private fun editRecent(card: io.asv.mtgocr.ocrreader.model.CardInfo, action: Int) {

        if (action==4) {

            startActivity(Intent(this,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,card.name)

                .putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID,card.collectionItemId)); return

        }

        if (action==2) {

            val target=if(CardFinish.isFoil(card.finish)) "nonfoil" else "foil"

            CardRepository.get(this).loadCard(card.name,false,false) { options,_ ->

                if(isDestroyed || isFinishing) return@loadCard

                val option=options.firstOrNull { it.printingUuid==card.printingUuid && it.finish==target }

                if(option==null) { Toast.makeText(this,R.string.review_finish_unavailable,Toast.LENGTH_LONG).show(); return@loadCard }

                saves.execute {

                    if(LibraryCatalog.activeFile(this)==libraryFile) {

                        val current=LegacyCollectionStore.cards(this).firstOrNull { it.collectionItemId==card.collectionItemId }

                        if(current!=null && current.printingUuid==card.printingUuid && current.finish==card.finish) {

                            LegacyCollectionStore.updateSelectedEdition(this,current.collectionItemId,option)

                            CardRepository.get(this).selectEdition(current.collectionItemId,option) {}

                        }

                    }

                    runOnUiThread { if(!isDestroyed) refreshRecent() }

                }

            }

            return

        }

        val delete=action==3 || (action==1 && card.quantityCount<=1)

        fun change() {

            saves.execute {

                LegacyCollectionStore.editRecentQuantity(this,libraryFile,card.collectionItemId,if(action==0) 1 else -1,delete)

                runOnUiThread { if(!isDestroyed) refreshRecent() }

            }

        }

        if(delete) androidx.appcompat.app.AlertDialog.Builder(this).setTitle(card.name)

            .setMessage(R.string.recent_delete_confirm).setNegativeButton(android.R.string.cancel,null)

            .setPositiveButton(android.R.string.ok) { _,_-> change() }.show() else change()

    }



    private fun reserveGroup(): String = synchronized(EdScannerArActivity::class.java) {

        val preferences = getSharedPreferences("cornelius_sessions", MODE_PRIVATE)

        val existing = LegacyCollectionStore.cards(this).flatMap { it.groups }

        val name = CorneliusGroups.next(existing, preferences.getInt(libraryFile, 0))

        check(preferences.edit().putInt(libraryFile, name.substringAfterLast(' ').toInt()).commit())

        name

    }



    override fun onSaveInstanceState(outState: Bundle) {

        outState.putLong("corneliusScanMillis", sessionClock.elapsed(android.os.SystemClock.elapsedRealtime()))

        outState.putString("corneliusGroup", group)

        outState.putString("corneliusLibrary", libraryFile)

        outState.putStringArrayList("corneliusIds", ArrayList(addedIds))

        outState.putInt("corneliusLanguage", language.selectedItemPosition)

        outState.putInt("corneliusFinish", finish.selectedItemPosition)

        super.onSaveInstanceState(outState)

    }

    override fun onDestroy() { arPreview?.close(); arPreview=null; clockHandler.removeCallbacks(clockTick); identityResolver.close(); saves.shutdown(); super.onDestroy() }

}
