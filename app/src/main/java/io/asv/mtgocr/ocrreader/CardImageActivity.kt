package io.asv.mtgocr.ocrreader

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.asv.mtgocr.ocrreader.data.CardImageVariant
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.Locale

class CardImageActivity : AppCompatActivity() {
    private lateinit var image: ZoomableImageView
    private lateinit var languageSpinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private var variants: List<CardImageVariant> = emptyList()
    private var pages: List<EditionImagePage> = emptyList()
    private var currentPage = 0
    private var languageRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_card_image)
        image = findViewById(R.id.fullscreenCardImage)
        languageSpinner = findViewById(R.id.cardLanguageSpinner)
        progress = findViewById(R.id.cardLanguageProgress)
        pageStatus = findViewById(R.id.txtCardImagePage)
        findViewById<Button>(R.id.btnCloseCardImage).setOnClickListener { finish() }
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val setCode = intent.getStringExtra(EXTRA_SET_CODE).orEmpty()
        val collectorNumber = intent.getStringExtra(EXTRA_COLLECTOR_NUMBER).orEmpty()
        pages = editionPagesFromIntent(imageUrl, setCode, collectorNumber)
        currentPage = (savedInstanceState?.getInt(STATE_CURRENT_PAGE)
            ?: intent.getIntExtra(EXTRA_EDITION_INDEX, 0))
            .coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        image.onSwipe = { direction -> showRelativePage(direction) }
        if (pages.isNotEmpty()) {
            showCurrentPage(animateDirection = 0)
        } else {
            progress.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
        super.onSaveInstanceState(outState)
    }

    private fun editionPagesFromIntent(
        fallbackUrl: String?,
        fallbackSetCode: String,
        fallbackCollectorNumber: String
    ): List<EditionImagePage> {
        val urls = intent.getStringArrayListExtra(EXTRA_EDITION_IMAGE_URLS).orEmpty()
        val labels = intent.getStringArrayListExtra(EXTRA_EDITION_LABELS).orEmpty()
        val sets = intent.getStringArrayListExtra(EXTRA_EDITION_SET_CODES).orEmpty()
        val collectors = intent.getStringArrayListExtra(EXTRA_EDITION_COLLECTOR_NUMBERS).orEmpty()
        val prices = intent.getStringArrayListExtra(EXTRA_EDITION_PRICES).orEmpty()
        val completeCount = minOf(urls.size, labels.size, sets.size, collectors.size)
        if (completeCount > 0) {
            return (0 until completeCount).mapNotNull { index ->
                urls[index].takeIf { it.isNotBlank() }?.let {
                    EditionImagePage(
                        it,
                        labels[index],
                        sets[index],
                        collectors[index],
                        prices.getOrNull(index) ?: getString(R.string.no_price)
                    )
                }
            }
        }
        return fallbackUrl?.takeIf { it.isNotBlank() }?.let {
            listOf(
                EditionImagePage(
                    it,
                    fallbackSetCode,
                    fallbackSetCode,
                    fallbackCollectorNumber,
                    getString(R.string.no_price)
                )
            )
        }.orEmpty()
    }

    private fun showRelativePage(direction: Int) {
        val target = currentPage + direction
        if (target !in pages.indices) {
            image.animate().translationX((-direction * 24 * resources.displayMetrics.density))
                .setDuration(90L).withEndAction {
                    image.animate().translationX(0f).setDuration(120L).start()
                }.start()
            return
        }
        currentPage = target
        showCurrentPage(animateDirection = direction)
    }

    private fun showCurrentPage(animateDirection: Int) {
        val page = pages[currentPage]
        image.resetZoom()
        if (animateDirection == 0) {
            CardImageCache.displayKeepingCurrent(this, page.imageUrl, image)
        } else {
            image.animate().cancel()
            image.alpha = 1f
            image.translationX = 0f
            CardImageCache.displayKeepingCurrent(this, page.imageUrl, image)
        }
        pageStatus.text = getString(
            R.string.image_page_status,
            currentPage + 1,
            pages.size,
            page.label,
            page.priceLabel
        )
        pages.getOrNull(currentPage - 1)?.let { CardImageCache.prefetch(this, it.imageUrl) }
        pages.getOrNull(currentPage + 1)?.let { CardImageCache.prefetch(this, it.imageUrl) }
        loadLanguages(page)
    }

    private fun loadLanguages(page: EditionImagePage) {
        val request = ++languageRequest
        variants = emptyList()
        languageSpinner.onItemSelectedListener = null
        languageSpinner.visibility = View.GONE
        if (page.setCode.isBlank() || page.collectorNumber.isBlank()) {
            progress.visibility = View.GONE
            return
        }
        progress.visibility = View.VISIBLE
        CardRepository.get(this).loadImageLanguages(page.setCode, page.collectorNumber) { loaded, _ ->
            if (request != languageRequest) return@loadImageLanguages
            progress.visibility = View.GONE
            if (loaded.isEmpty()) return@loadImageLanguages
            variants = loaded
            val labels = loaded.map { "${languageLabel(it.languageCode)} — ${it.printedName}" }
            languageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            languageSpinner.visibility = View.VISIBLE
            val preferred = loaded.indexOfFirst { it.imageUrl == page.imageUrl }
                .takeIf { it >= 0 }
                ?: loaded.indexOfFirst { it.languageCode == "en" }.coerceAtLeast(0)
            languageSpinner.setSelection(preferred)
            languageSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
                variants.getOrNull(position)?.let {
                    CardImageCache.displayKeepingCurrent(this, it.imageUrl, image)
                }
            }
        }
    }

    private fun languageLabel(code: String): String = when (code) {
        "zhs" -> "Chino simplificado"
        "zht" -> "Chino tradicional"
        "phyrexian" -> "Phyrexiano"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val EXTRA_IMAGE_URL = "imageUrl"
        const val EXTRA_SET_CODE = "setCode"
        const val EXTRA_COLLECTOR_NUMBER = "collectorNumber"
        const val EXTRA_EDITION_IMAGE_URLS = "editionImageUrls"
        const val EXTRA_EDITION_LABELS = "editionLabels"
        const val EXTRA_EDITION_SET_CODES = "editionSetCodes"
        const val EXTRA_EDITION_COLLECTOR_NUMBERS = "editionCollectorNumbers"
        const val EXTRA_EDITION_PRICES = "editionPrices"
        const val EXTRA_EDITION_INDEX = "editionIndex"
        private const val STATE_CURRENT_PAGE = "currentPage"
    }
}

private data class EditionImagePage(
    val imageUrl: String,
    val label: String,
    val setCode: String,
    val collectorNumber: String,
    val priceLabel: String
)
