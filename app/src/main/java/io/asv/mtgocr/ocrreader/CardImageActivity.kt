package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButtonToggleGroup
import io.asv.mtgocr.ocrreader.data.CardImageVariant
import io.asv.mtgocr.ocrreader.data.CardRepository
import java.util.Locale
import java.util.IdentityHashMap
import java.util.concurrent.Future

class CardImageActivity : AppCompatActivity() {
    private lateinit var image: ZoomableImageView
    private lateinit var pager: ViewPager2
    private lateinit var thumbnails: RecyclerView
    private lateinit var thumbnailsAdapter: ThumbnailAdapter
    private lateinit var pagerStage: FrameLayout
    private lateinit var languageSpinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var price: TextView
    private lateinit var detailsButton: android.widget.ImageButton
    private lateinit var foilBadge: ImageView
    private lateinit var foilEffectControls: View
    private lateinit var foilEffectToggleGroup: MaterialButtonToggleGroup
    private var variants: List<CardImageVariant> = emptyList()
    private var pages: List<EditionImagePage> = emptyList()
    private var galleryToken: String = ""
    private var currentPage = 0
    private var languageRequest = 0
    private var languageLoadTask: Future<*>? = null
    private var foilEffectMask = FoilEffectMode.DEFAULT
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
        image = ZoomableImageView(this)
        pager = findViewById(R.id.galleryPager)
        thumbnails = findViewById(R.id.galleryThumbnails)
        pagerStage = findViewById(R.id.cardPagerStage)
        languageSpinner = findViewById(R.id.cardLanguageSpinner)
        progress = findViewById(R.id.cardLanguageProgress)
        pageStatus = findViewById(R.id.txtCardImagePage)
        price = findViewById(R.id.txtCardImagePrice)
        detailsButton = findViewById(R.id.btnCardImageDetails)
        foilBadge = findViewById(R.id.imgFullscreenFoilBadge)
        foilEffectControls = findViewById(R.id.foilEffectControls)
        foilEffectToggleGroup = findViewById(R.id.foilEffectToggleGroup)
        foilEffectMask = savedInstanceState?.getInt(
            STATE_FOIL_EFFECT_MASK,
            FoilEffectMode.DEFAULT
        ) ?: FoilEffectMode.DEFAULT
        configureFoilEffectControls()
        findViewById<Button>(R.id.btnCloseCardImage).setOnClickListener { finish() }
        detailsButton.setOnClickListener { openCurrentCardDetails() }
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val setCode = intent.getStringExtra(EXTRA_SET_CODE).orEmpty()
        val collectorNumber = intent.getStringExtra(EXTRA_COLLECTOR_NUMBER).orEmpty()
        galleryToken = intent.getStringExtra(EXTRA_GALLERY_TOKEN).orEmpty()
        pages = editionPagesFromIntent(imageUrl, setCode, collectorNumber)
        currentPage = (savedInstanceState?.getInt(STATE_CURRENT_PAGE)
            ?: intent.getIntExtra(EXTRA_EDITION_INDEX, 0))
            .coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        if (pages.isNotEmpty()) {
            setupPager()
        } else {
            progress.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
        outState.putInt(STATE_FOIL_EFFECT_MASK, foilEffectMask)
        super.onSaveInstanceState(outState)
    }

    private fun editionPagesFromIntent(
        fallbackUrl: String?,
        fallbackSetCode: String,
        fallbackCollectorNumber: String
    ): List<EditionImagePage> {
        CardGalleryStore.get(galleryToken)?.takeIf { it.isNotEmpty() }?.let { stored ->
            return stored.map { page ->
                EditionImagePage(
                    page.imageUrl,
                    page.label,
                    page.setCode,
                    page.collectorNumber,
                    page.priceLabel.ifBlank { getString(R.string.no_price) },
                    page.finish,
                    page.cardName,
                    page.collectionItemId
                )
            }
        }
        val urls = intent.getStringArrayListExtra(EXTRA_EDITION_IMAGE_URLS).orEmpty()
        val labels = intent.getStringArrayListExtra(EXTRA_EDITION_LABELS).orEmpty()
        val sets = intent.getStringArrayListExtra(EXTRA_EDITION_SET_CODES).orEmpty()
        val collectors = intent.getStringArrayListExtra(EXTRA_EDITION_COLLECTOR_NUMBERS).orEmpty()
        val prices = intent.getStringArrayListExtra(EXTRA_EDITION_PRICES).orEmpty()
        val finishes = intent.getStringArrayListExtra(EXTRA_EDITION_FINISHES).orEmpty()
        val cardNames = intent.getStringArrayListExtra(EXTRA_CARD_NAMES).orEmpty()
        val collectionItemIds = intent.getStringArrayListExtra(EXTRA_COLLECTION_ITEM_IDS).orEmpty()
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
                        finishes.getOrNull(index).orEmpty(),
                        cardNames.getOrNull(index).orEmpty(),
                        collectionItemIds.getOrNull(index).orEmpty()
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
                    intent.getStringExtra(EXTRA_FINISH).orEmpty(),
                    "",
                    ""
                )
            )
        }.orEmpty()
    }

    private fun setupPager() {
        val initialPage = currentPage
        pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        pager.offscreenPageLimit = 1
        pager.adapter = object : RecyclerView.Adapter<ImageHolder>() {
            override fun getItemCount() = pages.size
            override fun onCreateViewHolder(parent: ViewGroup, type: Int) = ImageHolder(ZoomableImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                contentDescription = getString(R.string.card_image)
                // No custom page gestures: the native pager owns horizontal swipes at base zoom.
                onSwipe = null; onPageDrag = null; onPageDragEnd = null; onPageDragCancel = null
            })
            override fun onBindViewHolder(holder: ImageHolder, position: Int) {
                holder.photo.resetZoom()
                boundPages[holder.photo] = position
                applyFoilEffect(holder.photo, pages[position])
                CardImageCache.display(this@CardImageActivity, pages[position].imageUrl, holder.photo)
            }
            override fun onViewAttachedToWindow(holder: ImageHolder) {
                if (holder.bindingAdapterPosition == currentPage) {
                    image = holder.photo
                    onPageSettled()
                }
            }
            override fun onViewRecycled(holder: ImageHolder) {
                boundPages.remove(holder.photo)
                Glide.clear(holder.photo)
                holder.photo.setImageDrawable(null)
            }
        }
        thumbnailsAdapter = ThumbnailAdapter()
        thumbnails.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        thumbnails.adapter = thumbnailsAdapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val old = currentPage
                currentPage = position
                languageLoadTask?.cancel(true); languageRequest++
                thumbnailsAdapter.notifyItemChanged(old)
                thumbnailsAdapter.notifyItemChanged(position)
                thumbnails.smoothScrollToPosition(position)
                pager.post {
                    if (isDestroyed || currentPage != position) return@post
                    val holder = (pager.getChildAt(0) as RecyclerView).findViewHolderForAdapterPosition(position) as? ImageHolder
                    if (holder != null) { image = holder.photo; image.resetZoom(); onPageSettled() }
                }
            }
        })
        pager.setCurrentItem(initialPage, false)
        thumbnails.scrollToPosition(currentPage)
    }

    private class ImageHolder(val photo: ZoomableImageView) : RecyclerView.ViewHolder(photo)
    private inner class ThumbnailAdapter : RecyclerView.Adapter<ThumbnailHolder>() {
        override fun getItemCount() = pages.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): ThumbnailHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams((76*resources.displayMetrics.density).toInt(), -1)
                setPadding(4,4,4,4)
            }
            val picture = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val label = TextView(parent.context).apply { setTextColor(Color.WHITE); textSize = 12f; gravity = android.view.Gravity.CENTER; maxLines = 1 }
            row.addView(picture, LinearLayout.LayoutParams(-1,0,1f))
            row.addView(label, LinearLayout.LayoutParams(-1,-2))
            return ThumbnailHolder(row,picture,label)
        }
        override fun onBindViewHolder(holder: ThumbnailHolder, position: Int) {
            val page = pages[position]
            CardImageCache.display(this@CardImageActivity, page.imageUrl, holder.picture)
            holder.label.text = page.priceLabel
            holder.itemView.isSelected = position == currentPage
            holder.itemView.setBackgroundColor(if (position == currentPage) 0xFF566A85.toInt() else Color.TRANSPARENT)
            holder.itemView.contentDescription = "${position+1}/${pages.size}: ${page.label}, ${page.priceLabel}"
            holder.itemView.setOnClickListener { pager.setCurrentItem(position, false) }
        }
        override fun onViewRecycled(holder: ThumbnailHolder) { Glide.clear(holder.picture) }
    }
    private class ThumbnailHolder(view: View, val picture: ImageView, val label: TextView) : RecyclerView.ViewHolder(view)

    private fun onPageSettled() {
        val page = pages[currentPage]
        pageStatus.text = getString(
            R.string.image_page_status,
            currentPage + 1,
            pages.size,
            page.label
        )
        price.text = page.priceLabel
        price.setTextColor(
            ContextCompat.getColor(this,
                if (page.priceLabel == getString(R.string.no_price)) {
                    R.color.scan_total_incomplete
                } else {
                    R.color.scan_total_complete
                }
            )
        )
        detailsButton.visibility = if (
            page.cardName.isNotBlank() && page.collectionItemId.isNotBlank()
        ) View.VISIBLE else View.GONE
        val foil = CardFinish.isFoil(page.finish)
        foilBadge.visibility = if (foil) View.VISIBLE else View.GONE
        foilEffectControls.visibility = if (foil) View.VISIBLE else View.GONE
        foilBadge.alpha = 1f
        applyFoilEffect(image, page)
        loadLanguages(page)
    }

    private fun configureFoilEffectControls() {
        val buttons = listOf(
            R.id.toggleFoilHolographic to FoilEffectMode.HOLOGRAPHIC,
            R.id.toggleFoilRainbow to FoilEffectMode.RAINBOW,
            R.id.toggleFoilRainbowAlt to FoilEffectMode.RAINBOW_ALT,
            R.id.toggleFoilIridescent to FoilEffectMode.IRIDESCENT
        )
        buttons.forEach { (buttonId, mode) ->
            foilEffectToggleGroup.checkState(buttonId, FoilEffectMode.isEnabled(foilEffectMask, mode))
        }
        foilEffectToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val mode = buttons.firstOrNull { it.first == checkedId }?.second
                ?: return@addOnButtonCheckedListener
            foilEffectMask = FoilEffectMode.withMode(foilEffectMask, mode, isChecked)
            refreshVisibleFoilEffects()
        }
    }

    private fun MaterialButtonToggleGroup.checkState(buttonId: Int, checked: Boolean) {
        if (checked) check(buttonId) else uncheck(buttonId)
    }

    private fun refreshVisibleFoilEffects() {
        boundPages.keys.toList().forEach { target ->
            val pageIndex = boundPages[target]
            applyFoilEffect(target, pageIndex?.let(pages::getOrNull))
        }
    }

    private fun applyFoilEffect(target: ZoomableImageView, page: EditionImagePage?) {
        target.setFoilEffectModes(foilEffectMask)
        target.setFoilEffect(
            page?.let { CardFinish.isFoil(it.finish) } == true && foilEffectMask != 0
        )
    }

    private fun openCurrentCardDetails() {
        val page = pages.getOrNull(currentPage) ?: return
        if (page.cardName.isBlank() || page.collectionItemId.isBlank()) return
        startActivity(Intent(this, Main2Activity::class.java).apply {
            putExtra(Main2Activity.EXTRA_CARD_NAME, page.cardName)
            putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, page.collectionItemId)
        })
        // The gallery is only an intermediate view. Removing it from the task means Back from
        // the detail returns directly to the card list at the position from which it was opened.
        finish()
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
            languageSpinner.adapter = GalleryLanguageSpinnerAdapter(this, loaded, labels)
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
        boundPages.keys.toList().forEach { Glide.clear(it); it.setImageDrawable(null) }
        boundPages.clear()
        if (isFinishing && galleryToken.isNotBlank()) CardGalleryStore.remove(galleryToken)
        super.onDestroy()
    }

    private fun languageLabel(code: String): String = when (code) {
        "zhs" -> getString(R.string.language_chinese_simplified)
        "zht" -> getString(R.string.language_chinese_traditional)
        "phyrexian" -> getString(R.string.language_phyrexian)
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
        const val EXTRA_CARD_NAMES = "galleryCardNames"
        const val EXTRA_COLLECTION_ITEM_IDS = "galleryCollectionItemIds"
        const val EXTRA_GALLERY_TOKEN = "galleryToken"
        const val EXTRA_EDITION_INDEX = "editionIndex"
        private const val STATE_CURRENT_PAGE = "currentPage"
        private const val STATE_FOIL_EFFECT_MASK = "foilEffectMask"
    }
}

private data class EditionImagePage(
    val imageUrl: String,
    val label: String,
    val setCode: String,
    val collectorNumber: String,
    val priceLabel: String,
    val finish: String,
    val cardName: String,
    val collectionItemId: String
)
