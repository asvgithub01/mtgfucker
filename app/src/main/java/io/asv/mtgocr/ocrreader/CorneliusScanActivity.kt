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
        group = savedInstanceState?.getString("corneliusGroup")
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

    private var saveError: String? = null
    override val persistenceFailureMessage: String? get() = saveError
    override fun persistCandidate(cardId: String, score: Float, callback: (Boolean) -> Unit) {
        saveError = null
        val acceptedDebugKey = debugKey
        val finished: (Boolean) -> Unit = { saved ->
            if (!saved && !isDestroyed) acceptedDebugKey?.let { recent.endPending(it) }
            callback(saved)
        }
        val acceptedPhotoKey = photoKey
        val acceptedProvisionalIds = provisionalIds.toList()
        acceptedDebugKey?.let { io.asv.collectorvision.EdScanDebug.record(applicationContext, it, "request", org.json.JSONObject()
            .put("language", languages[language.selectedItemPosition]).put("finish", finishes[finish.selectedItemPosition])
            .put("library", libraryFile).put("photoKey", acceptedPhotoKey ?: org.json.JSONObject.NULL)) }
        val selectedLanguage = languages[language.selectedItemPosition]
        val selectedFinish = finishes[finish.selectedItemPosition]
        val repository = CardRepository.get(this)
        val lookupStartedAt = android.os.SystemClock.elapsedRealtime()
        repository.loadCollectorVisionPrinting(cardId) { options, error ->
            val lookupMs = android.os.SystemClock.elapsedRealtime() - lookupStartedAt
            if (isFinishing || isDestroyed) { finished(false); return@loadCollectorVisionPrinting }
            val actualFinish = EdScanSavePolicy.finish(selectedFinish, options.map { it.finish }.toSet())
            val option = options.firstOrNull { it.finish == actualFinish }
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
    fun next(groups: List<String>, previous: Int): String {
        val highest = groups.mapNotNull { Regex("(?i)^Cornelius (\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val next = maxOf(previous, highest, 0).toLong() + 1
        require(next <= Int.MAX_VALUE)
        return "Cornelius $next"
    }
}
