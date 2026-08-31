package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardImageVariant
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.data.OwnedPrintingEntity
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardCondition
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.Future

/** Shows every MTGJSON printing and finish and stores the owned selection in Room. */
class Main2Activity : AppCompatActivity() {
    private lateinit var repository: CardRepository
    private lateinit var adapter: EditionAdapter
    private lateinit var cardName: String
    private lateinit var collectionItemId: String
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var image: ImageView
    private lateinit var foilBadge: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var type: TextView
    private lateinit var rules: TextView
    private lateinit var languageSpinner: Spinner
    private lateinit var languageProgress: ProgressBar
    private lateinit var conditionSpinner: Spinner
    private lateinit var conditionPrice: TextView
    private lateinit var sheetView: ArcaneBottomSheetLayout
    private lateinit var bottomSheet: BottomSheetBehavior<View>
    private var selected: OwnedPrintingEntity? = null
    private var displayedOption: CardEditionOption? = null
    private var legacyImageUrl: String = ""
    private var editionOptions: List<CardEditionOption> = emptyList()
    private var languageVariants: List<CardImageVariant> = emptyList()
    private var languageRequest = 0
    private var cardLoadTask: Future<*>? = null
    private var languageLoadTask: Future<*>? = null
    private var ownedCondition: String = CardCondition.NEAR_MINT

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        cardName = intent.getStringExtra(EXTRA_CARD_NAME).orEmpty()
        collectionItemId = intent.getStringExtra(EXTRA_COLLECTION_ITEM_ID).orEmpty()
        if (cardName.isBlank() || collectionItemId.isBlank()) {
            finish()
            return
        }

        repository = CardRepository.get(this)
        title = findViewById(R.id.txtEditionTitle)
        status = findViewById(R.id.txtEditionStatus)
        image = findViewById(R.id.imgEditionDetail)
        foilBadge = findViewById(R.id.imgDetailFoilBadge)
        progress = findViewById(R.id.editionProgress)
        type = findViewById(R.id.txtCardType)
        rules = findViewById(R.id.txtCardRules)
        languageSpinner = findViewById(R.id.detailLanguageSpinner)
        languageProgress = findViewById(R.id.detailLanguageProgress)
        conditionSpinner = findViewById(R.id.detailConditionSpinner)
        conditionPrice = findViewById(R.id.txtConditionPrice)
        title.typeface = Typeface.createFromAsset(assets, "title_font.ttf")
        title.text = cardName
        val ownedCard = DataUtils.readSerializable<Biblio>(this, "myBiblio.Json")?.cards
            ?.firstOrNull { it.collectionItemId == collectionItemId }
        ownedCard?.let { owned ->
                ownedCondition = owned.condition
                legacyImageUrl = owned.imgPath.orEmpty()
                CardImageCache.display(this, owned.imgPath, image)
                foilBadge.visibility = if (CardFinish.isFoil(owned.finish)) View.VISIBLE else View.GONE
                type.text = listOf(owned.setName.orEmpty(), owned.setCode.orEmpty(), owned.finish.orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" · ")
                rules.text = owned.description.orEmpty()
            }
        conditionSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.card_condition_labels).toList()
        ).also { it.setDropDownViewResource(R.layout.spinner_item) }
        conditionSpinner.setSelection(CardCondition.indexOf(ownedCondition), false)
        refreshOwnedConditionPrice()
        conditionSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            val codes = CardCondition.codes()
            if (position !in codes.indices || codes[position] == ownedCondition) return@SimpleItemSelectedListener
            ownedCondition = codes[position]
            LegacyCollectionStore.updateCondition(this, collectionItemId, ownedCondition)
            refreshOwnedConditionPrice()
            setResult(RESULT_OK)
        }

        sheetView = findViewById(R.id.editionsBottomSheet)
        bottomSheet = BottomSheetBehavior.from(sheetView)
        bottomSheet.isFitToContents = false
        bottomSheet.halfExpandedRatio = .52f
        // Even fully expanded, keep enough of the illustration visible to preserve context.
        val expandedOffset = (188 * resources.displayMetrics.density).toInt()
        bottomSheet.expandedOffset = expandedOffset
        // A MATCH_PARENT sheet moved down by expandedOffset would extend the same amount below
        // the screen. RecyclerView would then consider that invisible area part of its viewport,
        // leaving the final edition partly cut and impossible to reveal completely.
        findViewById<View>(R.id.cardDetailRoot).doOnLayout { root ->
            val visibleSheetHeight = (root.height - expandedOffset).coerceAtLeast(1)
            if (sheetView.layoutParams.height != visibleSheetHeight) {
                sheetView.layoutParams = sheetView.layoutParams.apply { height = visibleSheetHeight }
            }
        }
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val collapsed = newState == BottomSheetBehavior.STATE_COLLAPSED
                image.animate().scaleX(if (collapsed) 1f else .96f).scaleY(if (collapsed) 1f else .96f).setDuration(280).start()
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val progress = slideOffset.coerceIn(0f, 1f)
                val scale = 1f - (.04f * progress)
                image.scaleX = scale
                image.scaleY = scale
            }
        })

        adapter = EditionAdapter(
            onSelected = { option ->
                repository.selectEdition(collectionItemId, option) {
                    LegacyCollectionStore.updateSelectedEdition(this, collectionItemId, option)
                    selected = OwnedPrintingEntity(
                        collectionItemId, option.cardName, option.printingUuid, option.finish, System.currentTimeMillis()
                    )
                    adapter.setSelected(selected)
                    showSelectedImage(option)
                    refreshOwnedConditionPrice()
                    setResult(RESULT_OK)
                    Toast.makeText(this, getString(R.string.edition_selected, option.setCode, option.finish), Toast.LENGTH_SHORT).show()
                }
            },
            onSetClicked = { option ->
                startActivity(Intent(this, SetCollectionActivity::class.java).apply {
                    putExtra(SetCollectionActivity.EXTRA_SET_CODE, option.setCode)
                    putExtra(SetCollectionActivity.EXTRA_SET_NAME, option.setName)
                })
            },
            onImageClicked = ::openCardImage,
            onAddCopy = ::addCopy,
            onRemoveCopy = ::removeCopy
        )
        image.setOnClickListener {
            displayedOption?.let(::openCardImage) ?: if (legacyImageUrl.isNotBlank()) {
                startActivity(Intent(this, CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL, legacyImageUrl))
            } else Unit
        }
        findViewById<RecyclerView>(R.id.editionsRecycler).apply {
            layoutManager = LinearLayoutManager(this@Main2Activity)
            adapter = this@Main2Activity.adapter
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefreshPrices).setOnClickListener { loadCard(forcePriceRefresh = true) }

        repository.selectedEdition(collectionItemId) {
            selected = it
            adapter.setSelected(it)
            loadCard(forcePriceRefresh = false)
        }
    }

    private fun loadCard(forcePriceRefresh: Boolean) {
        cardLoadTask?.cancel(true)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_editions)
        cardLoadTask = repository.loadCard(
            cardName,
            forcePriceRefresh,
            deliverEditionsBeforePrices = true
        ) result@{ options, error ->
            if (isFinishing || isDestroyed) return@result
            progress.visibility = View.GONE
            if (error != null) {
                status.text = error.message ?: getString(R.string.editions_error)
                return@result
            }
            editionOptions = options
            adapter.submit(options, currentCopyCounts())
            status.text = resources.getQuantityString(R.plurals.editions_found, options.size, options.size)
            val chosen = selected?.let { own ->
                options.firstOrNull { it.printingUuid == own.printingUuid && it.finish == own.finish }
            } ?: options.firstOrNull()
            chosen?.let { option ->
                showSelectedImage(option)
                if (selected?.let { it.printingUuid == option.printingUuid && it.finish == option.finish } == true) {
                    LegacyCollectionStore.updateSelectedEdition(this, collectionItemId, option)
                    setResult(RESULT_OK)
                }
            }
        }
    }

    private fun showSelectedImage(option: CardEditionOption) {
        displayedOption = option
        CardImageCache.display(this, option.imageUrl, image)
        foilBadge.visibility = if (option.isFoil) View.VISIBLE else View.GONE
        title.text = option.displayName
        type.text = option.typeLine
        rules.text = option.rulesText.ifBlank { getString(R.string.card_rules) }
        sheetView.setManaTint(MagicPalette.primaryColor(this))
        loadLanguages(option)
        refreshOwnedConditionPrice()
    }

    private fun refreshOwnedConditionPrice() {
        val card = DataUtils.readSerializable<Biblio>(this, "myBiblio.Json")?.cards
            ?.firstOrNull { it.collectionItemId == collectionItemId }
        val value = card?.price?.takeIf { it.isNotBlank() } ?: getString(R.string.no_price)
        conditionPrice.text = getString(R.string.card_condition_price, value)
    }

    private fun addCopy(option: CardEditionOption) {
        val added = runCatching { LegacyCollectionStore.addCopy(this, option) }.getOrElse { error ->
            Snackbar.make(
                findViewById(R.id.cardDetailRoot),
                error.message ?: getString(R.string.copy_add_error),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        // The legacy collection is already durably updated. Do not delay visible confirmation
        // behind Room's single-thread queue (which may still be refreshing prices).
        setResult(RESULT_OK)
        status.text = getString(R.string.copy_count_saved, added.quantityCount)
        adapter.setCopyCounts(currentCopyCounts())
        Snackbar.make(
            findViewById(R.id.cardDetailRoot),
            getString(R.string.copy_added, option.displayName, added.quantityCount),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.view_card) {
            displayedOption = option
            showSelectedImage(option)
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }.show()
        repository.selectEdition(added.collectionItemId, option) {
            // Room mirrors the selected printing for queries; the physical copy was persisted
            // synchronously above and must not depend on this asynchronous callback.
        }
    }

    private fun removeCopy(option: CardEditionOption) {
        val removed = LegacyCollectionStore.removeCopy(this, option, collectionItemId) ?: run {
            Snackbar.make(findViewById(R.id.cardDetailRoot), R.string.no_copies_to_remove, Snackbar.LENGTH_SHORT).show()
            return
        }
        setResult(RESULT_OK)
        if (removed.remainingQuantity == 0 && removed.removedCollectionItemId == collectionItemId) {
            Toast.makeText(this, R.string.last_copy_removed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        adapter.setCopyCounts(currentCopyCounts())
        status.text = getString(R.string.copy_count_saved, removed.remainingQuantity)
        Snackbar.make(
            findViewById(R.id.cardDetailRoot),
            getString(R.string.copy_removed, option.displayName, removed.remainingQuantity),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun currentCopyCounts(): Map<String, Int> {
        val collection = DataUtils.readSerializable<Biblio>(this, "myBiblio.Json") ?: return emptyMap()
        return collection.cards.groupBy { editionKey(it.printingUuid.orEmpty(), it.finish.orEmpty()) }
            .mapValues { (_, cards) -> cards.sumOf { it.quantityCount } }
    }

    private fun loadLanguages(option: CardEditionOption) {
        languageLoadTask?.cancel(true)
        val request = ++languageRequest
        languageVariants = emptyList()
        languageSpinner.onItemSelectedListener = null
        languageSpinner.visibility = View.GONE
        if (option.setCode.isBlank() || option.collectorNumber.isBlank()) {
            languageProgress.visibility = View.GONE
            return
        }
        languageProgress.visibility = View.VISIBLE
        languageLoadTask = repository.loadImageLanguages(option.setCode, option.collectorNumber) { loaded, _ ->
            if (isFinishing || isDestroyed) return@loadImageLanguages
            if (request != languageRequest) return@loadImageLanguages
            languageProgress.visibility = View.GONE
            if (loaded.isEmpty()) return@loadImageLanguages
            languageVariants = loaded
            languageSpinner.adapter = ArrayAdapter(
                this,
                R.layout.spinner_item,
                loaded.map { "${languageLabel(it.languageCode)} — ${it.printedName}" }
            ).also { it.setDropDownViewResource(R.layout.spinner_item) }
            languageSpinner.visibility = View.VISIBLE
            val preferred = loaded.indexOfFirst { it.imageUrl == option.imageUrl }
                .takeIf { it >= 0 }
                ?: loaded.indexOfFirst { it.languageCode == "en" }.coerceAtLeast(0)
            languageSpinner.setSelection(preferred, false)
            languageSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
                languageVariants.getOrNull(position)?.let { variant ->
                    CardImageCache.displayKeepingCurrent(this, variant.imageUrl, image)
                }
            }
        }
    }

    override fun onDestroy() {
        cardLoadTask?.cancel(true)
        languageLoadTask?.cancel(true)
        findViewById<RecyclerView>(R.id.editionsRecycler)?.adapter = null
        if (::image.isInitialized) {
            Glide.clear(image)
            image.setImageDrawable(null)
        }
        super.onDestroy()
    }

    private fun languageLabel(code: String): String = when (code) {
        "zhs" -> "Chino simplificado"
        "zht" -> "Chino tradicional"
        "phyrexian" -> "Phyrexiano"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
    }

    private fun openCardImage(option: CardEditionOption) {
        val pages = editionOptions
            .filter { !it.imageUrl.isNullOrBlank() }
            .groupBy { it.printingUuid }
            .values
            .map { finishes ->
                if (finishes.first().printingUuid == option.printingUuid) {
                    finishes.firstOrNull { it.finish == option.finish } ?: finishes.first()
                } else {
                    finishes.firstOrNull { it.finish == "nonfoil" }
                        ?: finishes.firstOrNull { it.price != null }
                        ?: finishes.first()
                }
            }
            .ifEmpty { listOf(option) }
        val initialIndex = pages.indexOfFirst { it.printingUuid == option.printingUuid }.coerceAtLeast(0)
        startActivity(Intent(this, CardImageActivity::class.java).apply {
            putExtra(CardImageActivity.EXTRA_IMAGE_URL, option.imageUrl)
            putExtra(CardImageActivity.EXTRA_SET_CODE, option.setCode)
            putExtra(CardImageActivity.EXTRA_COLLECTOR_NUMBER, option.collectorNumber)
            putExtra(CardImageActivity.EXTRA_FINISH, option.finish)
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_IMAGE_URLS,
                ArrayList(pages.map { it.imageUrl.orEmpty() })
            )
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_LABELS,
                ArrayList(pages.map { "${it.setName} (${it.setCode}) · #${it.collectorNumber}" })
            )
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_SET_CODES,
                ArrayList(pages.map { it.setCode })
            )
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_COLLECTOR_NUMBERS,
                ArrayList(pages.map { it.collectorNumber })
            )
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_PRICES,
                ArrayList(pages.map(::formatEditionPrice))
            )
            putStringArrayListExtra(
                CardImageActivity.EXTRA_EDITION_FINISHES,
                ArrayList(pages.map { it.finish })
            )
            putExtra(CardImageActivity.EXTRA_EDITION_INDEX, initialIndex)
        })
    }

    private fun formatEditionPrice(option: CardEditionOption): String {
        val value = option.price?.let { amount ->
            runCatching {
                NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                    currency = Currency.getInstance(option.currency ?: "EUR")
                }.format(amount)
            }.getOrElse { "%.2f %s".format(amount, option.currency.orEmpty()) }
        } ?: getString(R.string.no_price)
        return getString(R.string.near_mint_price_value, value)
    }

    companion object {
        const val EXTRA_CARD_NAME = "cardName"
        const val EXTRA_COLLECTION_ITEM_ID = "collectionItemId"
    }
}

private class EditionAdapter(
    private val onSelected: (CardEditionOption) -> Unit,
    private val onSetClicked: (CardEditionOption) -> Unit,
    private val onImageClicked: (CardEditionOption) -> Unit,
    private val onAddCopy: (CardEditionOption) -> Unit,
    private val onRemoveCopy: (CardEditionOption) -> Unit
) : RecyclerView.Adapter<EditionAdapter.Holder>() {
    private var items: List<CardEditionOption> = emptyList()
    private var selected: OwnedPrintingEntity? = null
    private var copyCounts: Map<String, Int> = emptyMap()

    fun submit(newItems: List<CardEditionOption>, newCopyCounts: Map<String, Int>) {
        items = newItems
        copyCounts = newCopyCounts
        notifyDataSetChanged()
    }

    fun setCopyCounts(newCopyCounts: Map<String, Int>) {
        copyCounts = newCopyCounts
        notifyDataSetChanged()
    }

    fun setSelected(newSelected: OwnedPrintingEntity?) {
        selected = newSelected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.edition_item, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val option = items[position]
        val isSelected = selected?.let { it.printingUuid == option.printingUuid && it.finish == option.finish } == true
        holder.bind(
            option,
            isSelected,
            copyCounts[editionKey(option.printingUuid, option.finish)] ?: 0,
            onSelected,
            onSetClicked,
            onImageClicked,
            onAddCopy,
            onRemoveCopy
        )
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val setName: TextView = view.findViewById(R.id.txtEditionSet)
        private val metadata: TextView = view.findViewById(R.id.txtEditionMetadata)
        private val price: TextView = view.findViewById(R.id.txtEditionPrice)
        private val radio: RadioButton = view.findViewById(R.id.radioOwnedEdition)
        private val image: ImageView = view.findViewById(R.id.imgEditionThumbnail)
        private val foilBadge: ImageView = view.findViewById(R.id.imgFoilBadge)
        private val addCopy: ImageButton = view.findViewById(R.id.btnAddEditionCopy)
        private val removeCopy: ImageButton = view.findViewById(R.id.btnRemoveEditionCopy)
        private val copyCount: TextView = view.findViewById(R.id.txtEditionCopyCount)

        fun bind(
            option: CardEditionOption,
            selected: Boolean,
            quantity: Int,
            onSelected: (CardEditionOption) -> Unit,
            onSetClicked: (CardEditionOption) -> Unit,
            onImageClicked: (CardEditionOption) -> Unit,
            onAddCopy: (CardEditionOption) -> Unit,
            onRemoveCopy: (CardEditionOption) -> Unit
        ) {
            setName.text = "${option.setName} (${option.setCode})"
            metadata.text = itemView.context.getString(
                R.string.edition_metadata,
                option.collectorNumber,
                option.releaseDate,
                if (option.isFoil) itemView.context.getString(R.string.foil) else itemView.context.getString(R.string.nonfoil)
            )
            val priceValue = option.price?.let { amount ->
                runCatching {
                    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                        currency = Currency.getInstance(option.currency ?: "EUR")
                    }.format(amount)
                }.getOrElse { "%.2f %s".format(amount, option.currency.orEmpty()) }
            } ?: itemView.context.getString(R.string.no_price)
            price.text = itemView.context.getString(R.string.near_mint_price_value, priceValue)
            CardImageCache.display(itemView.context, option.imageUrl, image)
            foilBadge.visibility = if (option.isFoil) View.VISIBLE else View.GONE
            itemView.setBackgroundResource(if (option.isFoil) R.drawable.bg_arcane_foil_row else R.drawable.bg_arcane_edition_row)
            radio.isChecked = selected
            copyCount.text = quantity.toString()
            removeCopy.isEnabled = quantity > 0
            removeCopy.alpha = if (quantity > 0) 1f else .35f
            itemView.setOnClickListener { onSelected(option) }
            radio.setOnClickListener { onSelected(option) }
            setName.setOnClickListener { onSetClicked(option) }
            image.setOnClickListener { onImageClicked(option) }
            addCopy.setOnClickListener {
                // A short asymmetric squash plus a gold fill reads as liquid feedback rather than
                // a generic scale bounce, while persistence still happens immediately.
                addCopy.isEnabled = false
                addCopy.setColorFilter(Color.rgb(255, 216, 112))
                addCopy.animate()
                    .scaleX(.76f)
                    .scaleY(1.14f)
                    .rotation(14f)
                    .setDuration(110L)
                    .withEndAction {
                        addCopy.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(260L)
                            .withEndAction {
                                addCopy.clearColorFilter()
                                addCopy.isEnabled = true
                            }
                            .start()
                    }
                    .start()
                onAddCopy(option)
            }
            removeCopy.setOnClickListener {
                if (quantity <= 0) return@setOnClickListener
                removeCopy.isEnabled = false
                removeCopy.animate().scaleX(.76f).scaleY(.76f).rotation(-14f).setDuration(100L)
                    .withEndAction {
                        removeCopy.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(220L).start()
                    }.start()
                onRemoveCopy(option)
            }
        }
    }
}

private fun editionKey(printingUuid: String, finish: String): String {
    val normalizedFinish = when (val value = finish.trim().lowercase(Locale.ROOT)) {
        "", "normal", "regular", "non-foil" -> "nonfoil"
        else -> value
    }
    return "${printingUuid.trim()}|$normalizedFinish"
}
