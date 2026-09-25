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
    private var photoKey: String? = null
    override fun acceptedPhoto(key: String?) { photoKey = key }
    private val saves = Executors.newSingleThreadExecutor()
    private lateinit var language: Spinner
    private lateinit var finish: Spinner
    @Volatile private var group: String? = null
    private var libraryFile = ""
    private val addedIds = linkedSetOf<String>()
    private val languages = listOf("", "es", "pt", "en", "fr", "de", "it", "ja", "ko", "ru", "zhs", "zht")
    private val finishes = listOf("nonfoil", "foil", "etched")
    override val priceFinish: String get() = finishes[finish.selectedItemPosition]
    override val priceCurrency: String get() = io.asv.mtgocr.ocrreader.data.PriceCurrency.preferred(this).lowercase()
    override val autoStartEnabled: Boolean get() = intent.getBooleanExtra("edscan_autostart", true)
    override val autoSaveEnabled = true
    override val sessionLabel: String? get() = group

    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun persistCandidate(cardId: String, score: Float, callback: (Boolean) -> Unit) {
        val acceptedPhotoKey = photoKey
        val selectedLanguage = languages[language.selectedItemPosition]
        val selectedFinish = finishes[finish.selectedItemPosition]
        val repository = CardRepository.get(this)
        repository.loadCollectorVisionPrinting(cardId) { options, error ->
            if (isFinishing || isDestroyed) { callback(false); return@loadCollectorVisionPrinting }
            val option = options.firstOrNull { it.finish == selectedFinish }
            if (option == null) {
                Toast.makeText(this, getString(R.string.cornelius_save_error), Toast.LENGTH_LONG).show()
                android.util.Log.w("CorneliusSave", "Exact printing/finish unavailable for $cardId", error)
                callback(false)
                return@loadCollectorVisionPrinting
            }
            saves.execute {
                try {
                    check(LibraryCatalog.activeFile(this) == libraryFile)
                    val sessionGroup = group ?: reserveGroup().also { group = it }
                    val card = LegacyCollectionStore.addCopy(this, option, selectedLanguage,
                        groupName = sessionGroup, expectedLibraryFile = libraryFile)
                    repository.selectEdition(card.collectionItemId, option) {
                        acceptedPhotoKey?.let { io.asv.collectorvision.EdScanJobs.link(applicationContext, it, libraryFile, card.collectionItemId, option.printingUuid) }
                        addedIds += card.collectionItemId
                        setResult(RESULT_OK, Intent().putStringArrayListExtra(RapidEditionScanActivity.EXTRA_SESSION_CARD_IDS, ArrayList(addedIds)))
                        callback(true)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (!isDestroyed) Toast.makeText(this, R.string.cornelius_save_error, Toast.LENGTH_LONG).show()
                        android.util.Log.e("CorneliusSave", "Save failed", e)
                        callback(false)
                    }
                }
            }
        }
    }

    private fun reserveGroup(): String = synchronized(CorneliusScanActivity::class.java) {
        val preferences = getSharedPreferences("cornelius_sessions", MODE_PRIVATE)
        val existing = LegacyCollectionStore.cards(this).flatMap { it.groups }
        val name = CorneliusGroups.next(existing, preferences.getInt(libraryFile, 0))
        check(preferences.edit().putInt(libraryFile, name.substringAfterLast(' ').toInt()).commit())
        name
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("corneliusGroup", group)
        outState.putString("corneliusLibrary", libraryFile)
        outState.putStringArrayList("corneliusIds", ArrayList(addedIds))
        outState.putInt("corneliusLanguage", language.selectedItemPosition)
        outState.putInt("corneliusFinish", finish.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { saves.shutdown(); super.onDestroy() }
}

internal object CorneliusGroups {
    fun next(groups: List<String>, previous: Int): String {
        val highest = groups.mapNotNull { Regex("(?i)^Cornelius (\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        val next = maxOf(previous, highest, 0).toLong() + 1
        require(next <= Int.MAX_VALUE)
        return "Cornelius $next"
    }
}
