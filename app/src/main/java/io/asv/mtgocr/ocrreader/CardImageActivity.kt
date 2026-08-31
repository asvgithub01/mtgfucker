package io.asv.mtgocr.ocrreader

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.data.CardImageVariant
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.Locale
import java.util.IdentityHashMap
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.sign

class CardImageActivity : AppCompatActivity() {
    private lateinit var image: ZoomableImageView
    private lateinit var previousImage: ZoomableImageView
    private lateinit var nextImage: ZoomableImageView
    private lateinit var pagerStage: FrameLayout
    private lateinit var languageSpinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var foilBadge: ImageView
    private var variants: List<CardImageVariant> = emptyList()
    private var pages: List<EditionImagePage> = emptyList()
    private var currentPage = 0
    private var languageRequest = 0
    private var languageLoadTask: Future<*>? = null
    private var pageTurnAnimating = false
    private var activeDragDirection = 0
    private val boundPages = IdentityHashMap<ZoomableImageView, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_card_image)
        image = findViewById(R.id.fullscreenCardImage)
        previousImage = findViewById(R.id.previousCardImage)
        nextImage = findViewById(R.id.nextCardImage)
        pagerStage = findViewById(R.id.cardPagerStage)
        languageSpinner = findViewById(R.id.cardLanguageSpinner)
        progress = findViewById(R.id.cardLanguageProgress)
        pageStatus = findViewById(R.id.txtCardImagePage)
        foilBadge = findViewById(R.id.imgFullscreenFoilBadge)
        findViewById<Button>(R.id.btnCloseCardImage).setOnClickListener { finish() }
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val setCode = intent.getStringExtra(EXTRA_SET_CODE).orEmpty()
        val collectorNumber = intent.getStringExtra(EXTRA_COLLECTOR_NUMBER).orEmpty()
        pages = editionPagesFromIntent(imageUrl, setCode, collectorNumber)
        currentPage = (savedInstanceState?.getInt(STATE_CURRENT_PAGE)
            ?: intent.getIntExtra(EXTRA_EDITION_INDEX, 0))
            .coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        configurePageGestures()
        if (pages.isNotEmpty()) {
            showInitialPage()
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
        val finishes = intent.getStringArrayListExtra(EXTRA_EDITION_FINISHES).orEmpty()
        val completeCount = minOf(urls.size, labels.size, sets.size, collectors.size)
        if (completeCount > 0) {
            return (0 until completeCount).mapNotNull { index ->
                urls[index].takeIf { it.isNotBlank() }?.let {
                    EditionImagePage(
                        it,
                        labels[index],
                        sets[index],
                        collectors[index],
                        prices.getOrNull(index) ?: getString(R.string.no_price),
                        finishes.getOrNull(index).orEmpty()
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
                    getString(R.string.no_price),
                    intent.getStringExtra(EXTRA_FINISH).orEmpty()
                )
            )
        }.orEmpty()
    }

    private fun configurePageGestures() {
        image.onSwipe = null
        image.onPageDrag = ::renderPageDrag
        image.onPageDragEnd = ::finishPageDrag
        image.onPageDragCancel = ::animateTurnBack
        image.isEnabled = true
        image.isClickable = true
        image.contentDescription = getString(R.string.card_image)
        image.bringToFront()
        foilBadge.bringToFront()
    }

    private fun showInitialPage() {
        val page = pages[currentPage]
        image.resetZoom()
        CardImageCache.display(this, page.imageUrl, image)
        boundPages[image] = currentPage
        resetView(image, 1f)
        prepareAdjacentPages()
        onPageSettled()
    }

    private fun renderPageDrag(fraction: Float) {
        if (pageTurnAnimating || fraction == 0f) return
        image.animate().cancel()
        previousImage.animate().cancel()
        nextImage.animate().cancel()
        val direction = if (fraction < 0f) 1 else -1
        if (activeDragDirection != 0 && activeDragDirection != direction) {
            resetView(targetImage(activeDragDirection), 0f)
        }
        activeDragDirection = direction
        val targetIndex = currentPage + direction
        val target = targetImage(direction)
        val progress = abs(fraction).coerceIn(0f, 1f)
        val hasReadyTarget = targetIndex in pages.indices &&
            boundPages[target] == targetIndex && target.drawable != null
        val cameraDistance = resources.displayMetrics.density * 12_000f
        image.cameraDistance = cameraDistance

        if (!hasReadyTarget) {
            resetView(previousImage, 0f)
            resetView(nextImage, 0f)
            val resistance = fraction * .16f
            image.alpha = 1f
            image.scaleX = 1f
            image.scaleY = 1f
            image.translationX = pagerStage.width * resistance * .32f
            image.rotationY = resistance * 28f
            return
        }

        val other = if (direction > 0) previousImage else nextImage
        resetView(other, 0f)
        target.cameraDistance = cameraDistance
        // Interactive cover-flow: the adjacent card remains visibly parked at the edge while
        // the current card follows the finger. There is no cross-fade through black.
        val side = if (direction > 0) 1f else -1f
        target.alpha = .42f + (.58f * progress)
        target.rotationY = -side * 26f * (1f - progress)
        target.translationX = side * pagerStage.width * .78f * (1f - progress)
        target.scaleX = .92f + (.08f * progress)
        target.scaleY = target.scaleX

        image.alpha = 1f - (.12f * progress)
        image.rotationY = -side * 20f * progress
        image.translationX = -side * pagerStage.width * .62f * progress
        image.scaleX = 1f - (.025f * progress)
        image.scaleY = image.scaleX
        foilBadge.alpha = (1f - progress * 1.35f).coerceAtLeast(0f)
    }

    private fun finishPageDrag(fraction: Float, @Suppress("UNUSED_PARAMETER") velocityX: Float) {
        if (pageTurnAnimating) return
        val direction = activeDragDirection.takeIf { it != 0 } ?: if (fraction < 0f) 1 else -1
        val targetIndex = currentPage + direction
        val target = targetImage(direction)
        val targetReady = targetIndex in pages.indices &&
            boundPages[target] == targetIndex && target.drawable != null
        // Distance, not a short fling, decides the page. Reversing the finger is handled as an
        // explicit cancellation by ZoomableImageView rather than becoming an opposite page turn.
        val shouldCommit = PageTurnPolicy.shouldCommit(fraction, targetReady)
        if (!shouldCommit) {
            animateTurnBack()
            return
        }

        pageTurnAnimating = true
        image.isEnabled = false
        val turnSign = sign(fraction).takeIf { it != 0f } ?: if (direction > 0) -1f else 1f
        val remaining = (1f - abs(fraction)).coerceIn(.18f, 1f)
        val duration = (220L * remaining).toLong().coerceAtLeast(110L)
        image.animate().cancel()
        target.animate().cancel()
        image.animate()
            .alpha(0f)
            .rotationY(turnSign * 92f)
            .translationX(turnSign * pagerStage.width * .22f)
            .scaleX(.92f)
            .scaleY(.92f)
            .setDuration(duration)
            .start()
        target.animate()
            .alpha(1f)
            .rotationY(0f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .withEndAction { completePageTurn(direction) }
            .start()
    }

    private fun animateTurnBack() {
        val views = listOf(image, previousImage, nextImage)
        views.forEach { it.animate().cancel() }
        val direction = activeDragDirection
        if (direction == 0) {
            resetTurnImmediately()
            return
        }
        val target = targetImage(direction)
        val other = if (direction > 0) previousImage else nextImage
        resetView(other, 0f)
        val side = if (direction > 0) 1f else -1f
        image.animate()
            .alpha(1f)
            .rotationY(0f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .start()
        target.animate()
            .alpha(0f)
            .rotationY(-side * 26f)
            .translationX(side * pagerStage.width * .78f)
            .scaleX(.92f)
            .scaleY(.92f)
            .setDuration(180L)
            .withEndAction {
                resetView(target, 0f)
                activeDragDirection = 0
            }
            .start()
        foilBadge.animate().alpha(1f).setDuration(160L).start()
    }

    private fun resetTurnImmediately() {
        resetView(image, 1f)
        if (previousImage !== image) resetView(previousImage, 0f)
        if (nextImage !== image) resetView(nextImage, 0f)
    }

    private fun completePageTurn(direction: Int) {
        val outgoing = image
        if (direction > 0) {
            image = nextImage
            nextImage = previousImage
            previousImage = outgoing
        } else {
            image = previousImage
            previousImage = nextImage
            nextImage = outgoing
        }
        currentPage += direction
        activeDragDirection = 0
        pageTurnAnimating = false
        resetView(image, 1f)
        resetView(previousImage, 0f)
        resetView(nextImage, 0f)
        image.resetZoom()
        configurePageGestures()
        prepareAdjacentPages()
        onPageSettled()
    }

    private fun targetImage(direction: Int): ZoomableImageView =
        if (direction > 0) nextImage else previousImage

    private fun prepareAdjacentPages() {
        bindPreview(previousImage, currentPage - 1)
        bindPreview(nextImage, currentPage + 1)
    }

    private fun bindPreview(target: ZoomableImageView, pageIndex: Int) {
        target.onSwipe = null
        target.onPageDrag = null
        target.onPageDragEnd = null
        target.onPageDragCancel = null
        target.isEnabled = false
        target.isClickable = false
        target.contentDescription = null
        resetView(target, 0f)
        val page = pages.getOrNull(pageIndex)
        val nextUrl = page?.imageUrl.orEmpty()
        val previousUrl = target.getTag(R.id.card_image_cache_url) as? String
        if (previousUrl != nextUrl) {
            // The view may still contain the page it represented before the three buffers rotated.
            // Clear it while fully transparent so an old bitmap can never be treated as a ready
            // adjacent page and flash for one frame under the user's finger.
            Glide.clear(target)
            target.setImageDrawable(null)
        }
        boundPages[target] = pageIndex
        CardImageCache.display(this, page?.imageUrl, target)
    }

    private fun resetView(target: View, alpha: Float) {
        target.animate().cancel()
        target.alpha = alpha
        target.rotationY = 0f
        target.translationX = 0f
        target.scaleX = 1f
        target.scaleY = 1f
    }

    private fun onPageSettled() {
        val page = pages[currentPage]
        pageStatus.text = getString(
            R.string.image_page_status,
            currentPage + 1,
            pages.size,
            page.label,
            page.priceLabel
        )
        foilBadge.visibility = if (CardFinish.isFoil(page.finish)) View.VISIBLE else View.GONE
        foilBadge.alpha = 1f
        loadLanguages(page)
    }

    private fun loadLanguages(page: EditionImagePage) {
        languageLoadTask?.cancel(true)
        val request = ++languageRequest
        variants = emptyList()
        languageSpinner.onItemSelectedListener = null
        languageSpinner.visibility = View.GONE
        if (page.setCode.isBlank() || page.collectorNumber.isBlank()) {
            progress.visibility = View.GONE
            return
        }
        progress.visibility = View.VISIBLE
        languageLoadTask = CardRepository.get(this).loadImageLanguages(page.setCode, page.collectorNumber) { loaded, _ ->
            if (isFinishing || isDestroyed) return@loadImageLanguages
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

    override fun onDestroy() {
        languageLoadTask?.cancel(true)
        if (::previousImage.isInitialized && ::nextImage.isInitialized && ::image.isInitialized) {
            listOf(image, previousImage, nextImage).distinct().forEach {
                Glide.clear(it)
                it.setImageDrawable(null)
            }
        }
        super.onDestroy()
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
        const val EXTRA_FINISH = "finish"
        const val EXTRA_EDITION_IMAGE_URLS = "editionImageUrls"
        const val EXTRA_EDITION_LABELS = "editionLabels"
        const val EXTRA_EDITION_SET_CODES = "editionSetCodes"
        const val EXTRA_EDITION_COLLECTOR_NUMBERS = "editionCollectorNumbers"
        const val EXTRA_EDITION_PRICES = "editionPrices"
        const val EXTRA_EDITION_FINISHES = "editionFinishes"
        const val EXTRA_EDITION_INDEX = "editionIndex"
        private const val STATE_CURRENT_PAGE = "currentPage"
    }
}

private data class EditionImagePage(
    val imageUrl: String,
    val label: String,
    val setCode: String,
    val collectorNumber: String,
    val priceLabel: String,
    val finish: String
)
