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

/** Host-only persistence bridge; the native engine remains independent of the collection. */
class CorneliusScanActivity : NativeCollectorVisionActivity() {
    companion object { const val EXTRA_DESTINATION_GROUP = "edscan_destination_group" }
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
    override val autoStartEnabled: Boolean get() = intent.getBooleanExtra("edscan_autostart", true)
    override val autoSaveEnabled = true
    override val sessionLabel: String? get() = group

    override fun onCreate(savedInstanceState: Bundle?) {
        sessionClock = ScanSessionClock(savedInstanceState?.getLong("corneliusScanMillis") ?: 0L)
        group = CorneliusGroups.destination(savedInstanceState?.getString("corneliusGroup"),
            intent.getStringExtra(EXTRA_DESTINATION_GROUP))
        libraryFile = savedInstanceState?.getString("corneliusLibrary") ?: LibraryCatalog.activeFile(this)
        savedInstanceState?.getStringArrayList("corneliusIds")?.let { addedIds.addAll(it) }
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { language.setSelection(it.getInt("corneliusLanguage")); finish.setSelection(it.getInt("corneliusFinish")) }
    }

    override fun addHostControls(container: LinearLayout) {
        container.addView(TextView(this).apply { setText(R.string.cornelius_manual_attributes); textSize = 12f })
        val row = LinearLayout(this)
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@CorneliusScanActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.cornelius_unknown_language)) + languages.drop(1).map { it.uppercase() })
        }
        finish = Spinner(this).apply {
            adapter = ArrayAdapter(this@CorneliusScanActivity, android.R.layout.simple_spinner_dropdown_item, finishes)
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
        container.addView(totalView)
        recent = RecentScansView(this)
        container.addView(recent, LinearLayout.LayoutParams(-1,(104*resources.displayMetrics.density).toInt()))
        container.addView(android.widget.Button(this).apply {
            setText(R.string.postprocess_scans)
            setOnClickListener { startActivity(Intent(this@CorneliusScanActivity, ScanReviewActivity::class.java)) }
        })
    }

    private lateinit var setInput: ScannerSetInput
    private lateinit var nameInput: ScannerNameInput
    private var lockGeneration=0
    private var manualBusy=false
    override val scanResumeAllowed: Boolean get() = !manualBusy
    private var appliedLocks=emptySet<String>()
    private fun updateSetLock() {
        val codes=setInput.codes()
        val token=++lockGeneration
        updateVisualScope(if(codes.isEmpty()) null else emptySet())
        appliedLocks=codes
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

    private fun reserveGroup(): String = synchronized(CorneliusScanActivity::class.java) {
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
    override fun onDestroy() { clockHandler.removeCallbacks(clockTick); identityResolver.close(); saves.shutdown(); super.onDestroy() }
}

internal object CorneliusGroups {
    fun destination(restored: String?, selected: String?): String? =
        restored?.takeIf { it.isNotBlank() } ?: selected?.takeIf { it.isNotBlank() }
    fun next(groups: List<String>, previous: Int): String {
        val highest = groups.mapNotNull { Regex("(?i)^Cornelius (\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val next = maxOf(previous, highest, 0).toLong() + 1
        require(next <= Int.MAX_VALUE)
        return "Cornelius $next"
    }
}
