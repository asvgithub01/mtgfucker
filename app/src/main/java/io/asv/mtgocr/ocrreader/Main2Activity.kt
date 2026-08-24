package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.data.OwnedPrintingEntity
import io.asv.mtgocr.ocrreader.model.Biblio
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Shows every MTGJSON printing and finish and stores the owned selection in Room. */
class Main2Activity : AppCompatActivity() {
    private lateinit var repository: CardRepository
    private lateinit var adapter: EditionAdapter
    private lateinit var cardName: String
    private lateinit var collectionItemId: String
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var image: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var type: TextView
    private lateinit var rules: TextView
    private lateinit var bottomSheet: BottomSheetBehavior<View>
    private var selected: OwnedPrintingEntity? = null
    private var displayedOption: CardEditionOption? = null
    private var legacyImageUrl: String = ""
    private var editionOptions: List<CardEditionOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
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
        progress = findViewById(R.id.editionProgress)
        type = findViewById(R.id.txtCardType)
        rules = findViewById(R.id.txtCardRules)
        title.typeface = Typeface.createFromAsset(assets, "title_font.ttf")
        title.text = cardName
        DataUtils.readSerializable<Biblio>(this, "myBiblio.Json")?.cards
            ?.firstOrNull { it.collectionItemId == collectionItemId }
            ?.let { owned ->
                legacyImageUrl = owned.imgPath.orEmpty()
                CardImageCache.display(this, owned.imgPath, image)
                type.text = listOf(owned.setName.orEmpty(), owned.setCode.orEmpty(), owned.finish.orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" · ")
                rules.text = owned.description.orEmpty()
            }

        bottomSheet = BottomSheetBehavior.from(findViewById(R.id.editionsBottomSheet))
        bottomSheet.isFitToContents = false
        bottomSheet.halfExpandedRatio = .5f
        bottomSheet.expandedOffset = (112 * resources.displayMetrics.density).toInt()
        bottomSheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val collapsed = newState == BottomSheetBehavior.STATE_COLLAPSED
                image.animate().scaleX(if (collapsed) 1f else .9f).scaleY(if (collapsed) 1f else .9f).setDuration(240).start()
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val progress = slideOffset.coerceIn(0f, 1f)
                val scale = 1f - (.1f * progress)
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
            onAddCopy = ::addCopy
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
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_editions)
        repository.loadCard(cardName, forcePriceRefresh) result@{ options, error ->
            progress.visibility = View.GONE
            if (error != null) {
                status.text = error.message ?: getString(R.string.editions_error)
                return@result
            }
            editionOptions = options
            adapter.submit(options)
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
        title.text = option.displayName
        type.text = option.typeLine
        rules.text = option.rulesText.ifBlank { getString(R.string.card_rules) }
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
            putExtra(CardImageActivity.EXTRA_EDITION_INDEX, initialIndex)
        })
    }

    private fun formatEditionPrice(option: CardEditionOption): String {
        return option.price?.let { amount ->
            runCatching {
                NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                    currency = Currency.getInstance(option.currency ?: "EUR")
                }.format(amount)
            }.getOrElse { "%.2f %s".format(amount, option.currency.orEmpty()) }
        } ?: getString(R.string.no_price)
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
    private val onAddCopy: (CardEditionOption) -> Unit
) : RecyclerView.Adapter<EditionAdapter.Holder>() {
    private var items: List<CardEditionOption> = emptyList()
    private var selected: OwnedPrintingEntity? = null

    fun submit(newItems: List<CardEditionOption>) {
        items = newItems
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
        holder.bind(option, isSelected, onSelected, onSetClicked, onImageClicked, onAddCopy)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val setName: TextView = view.findViewById(R.id.txtEditionSet)
        private val metadata: TextView = view.findViewById(R.id.txtEditionMetadata)
        private val price: TextView = view.findViewById(R.id.txtEditionPrice)
        private val radio: RadioButton = view.findViewById(R.id.radioOwnedEdition)
        private val image: ImageView = view.findViewById(R.id.imgEditionThumbnail)
        private val addCopy: View = view.findViewById(R.id.btnAddEditionCopy)

        fun bind(
            option: CardEditionOption,
            selected: Boolean,
            onSelected: (CardEditionOption) -> Unit,
            onSetClicked: (CardEditionOption) -> Unit,
            onImageClicked: (CardEditionOption) -> Unit,
            onAddCopy: (CardEditionOption) -> Unit
        ) {
            setName.text = "${option.setName} (${option.setCode})"
            metadata.text = itemView.context.getString(
                R.string.edition_metadata,
                option.collectorNumber,
                option.releaseDate,
                if (option.isFoil) itemView.context.getString(R.string.foil) else itemView.context.getString(R.string.nonfoil)
            )
            price.text = option.price?.let { amount ->
                runCatching {
                    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                        currency = Currency.getInstance(option.currency ?: "EUR")
                    }.format(amount)
                }.getOrElse { "%.2f %s".format(amount, option.currency.orEmpty()) }
            } ?: itemView.context.getString(R.string.no_price)
            CardImageCache.display(itemView.context, option.imageUrl, image)
            radio.isChecked = selected
            itemView.setOnClickListener { onSelected(option) }
            radio.setOnClickListener { onSelected(option) }
            setName.setOnClickListener { onSetClicked(option) }
            image.setOnClickListener { onImageClicked(option) }
            addCopy.setOnClickListener { onAddCopy(option) }
        }
    }
}
