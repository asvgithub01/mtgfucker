package io.asv.mtgocr.ocrreader

import android.app.AlertDialog
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.data.SetCardOption
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.model.CardInfo

class SetCollectionActivity : AppCompatActivity() {
    private lateinit var repository: CardRepository
    private lateinit var adapter: SetCardAdapter
    private lateinit var addButton: Button
    private lateinit var selectAll: CheckBox
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var setCode: String
    private lateinit var recycler: RecyclerView
    private var galleryMode = true
    private var pendingScrollState: Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_collection)
        setCode = intent.getStringExtra(EXTRA_SET_CODE).orEmpty()
        val setName = intent.getStringExtra(EXTRA_SET_NAME).orEmpty()
        if (setCode.isBlank()) {
            finish()
            return
        }
        repository = CardRepository.get(this)
        findViewById<TextView>(R.id.txtSetCollectionTitle).text = "$setName ($setCode)"
        progress = findViewById(R.id.setCollectionProgress)
        status = findViewById(R.id.txtSetCollectionStatus)
        addButton = findViewById(R.id.btnAddSetCards)
        selectAll = findViewById(R.id.checkSelectAllSetCards)
        adapter = SetCardAdapter(
            context = this,
            onSelectionChanged = { selectedCount ->
                addButton.isEnabled = selectedCount > 0
                addButton.text = getString(R.string.add_selected_cards_count, selectedCount)
                selectAll.isChecked = adapter.areAllSelected()
            },
            onGalleryClicked = ::openGallery,
            onDetailsClicked = { card ->
                openCardDetails(card)
            }
        )
        galleryMode = getPreferences(MODE_PRIVATE).getBoolean(PREF_SET_GALLERY, true)
        recycler = findViewById<RecyclerView>(R.id.setCollectionRecycler).apply {
            layoutManager = GridLayoutManager(this@SetCollectionActivity, if (galleryMode) 2 else 1)
            adapter = this@SetCollectionActivity.adapter
        }
        findViewById<ImageButton>(R.id.btnSetViewMode).apply {
            fun render() {
                setImageResource(if (galleryMode) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_gallery)
                contentDescription = getString(if (galleryMode) R.string.show_as_list else R.string.show_as_grid)
            }
            render()
            setOnClickListener {
                galleryMode = !galleryMode
                getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_SET_GALLERY, galleryMode).apply()
                recycler.layoutManager = GridLayoutManager(this@SetCollectionActivity, if (galleryMode) 2 else 1)
                adapter.notifyDataSetChanged()
                render()
            }
        }
        findViewById<Spinner>(R.id.spinnerSetSort).apply {
            adapter = ArrayAdapter.createFromResource(
                this@SetCollectionActivity,
                R.array.collection_sort_options,
                R.layout.spinner_item
            ).also { it.setDropDownViewResource(R.layout.spinner_item) }
            onItemSelectedListener = SimpleItemSelectedListener { position ->
                this@SetCollectionActivity.adapter.setSortMode(position)
            }
        }
        selectAll.setOnClickListener { adapter.selectAll(selectAll.isChecked) }
        addButton.setOnClickListener { addSelectedCards() }
        findViewById<Button>(R.id.btnCloseSetCollection).setOnClickListener { finish() }
        loadSet()
    }

    override fun onResume() {
        super.onResume()
        restoreScrollPosition()
    }

    private fun loadSet() {
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_set_cards)
        repository.loadSet(setCode) result@{ cards, error ->
            progress.visibility = View.GONE
            if (error != null) {
                status.text = error.message ?: getString(R.string.set_cards_error)
                return@result
            }
            val ownedCards = LegacyCollectionStore.cards(this)
                .filter { it.printingUuid.orEmpty().isNotBlank() }
                .associateBy { it.printingUuid.orEmpty() }
            adapter.submit(cards, ownedCards)
            val ownedCount = cards.count { it.printingUuid in ownedCards }
            status.text = getString(
                R.string.owned_and_missing_cards,
                cards.size,
                ownedCount,
                cards.size - ownedCount
            )
        }
    }

    private fun openCardDetails(card: SetCardOption) {
        adapter.ownedCard(card.printingUuid)?.let { owned ->
            openOwnedCardDetails(owned)
            return
        }
        val details = buildList {
            add("${card.setName} (${card.setCode}) · #${card.collectorNumber}")
            add(getString(R.string.card_missing_disabled))
            if (card.typeLine.isNotBlank()) add(card.typeLine)
            if (card.rulesText.isNotBlank()) add(card.rulesText)
        }.joinToString("\n\n")
        AlertDialog.Builder(this)
            .setTitle(card.cardName)
            .setMessage(details)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openGallery(card: SetCardOption) {
        rememberScrollPosition()
        val opened = CardGalleryLauncher.openSetCards(
            this,
            adapter.visibleItems(),
            card.printingUuid,
            adapter.ownedCollectionItemIds()
        )
        if (!opened) pendingScrollState = null
    }

    private fun openOwnedCardDetails(card: CardInfo) {
        rememberScrollPosition()
        startActivity(Intent(this, Main2Activity::class.java).apply {
            putExtra(Main2Activity.EXTRA_CARD_NAME, card.name)
            putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, card.collectionItemId)
        })
    }

    private fun rememberScrollPosition() {
        pendingScrollState = recycler.layoutManager?.onSaveInstanceState()
    }

    private fun restoreScrollPosition() {
        val state = pendingScrollState ?: return
        pendingScrollState = null
        recycler.post { recycler.layoutManager?.onRestoreInstanceState(state) }
    }

    private fun addSelectedCards() {
        val selected = adapter.selectedItems()
        if (selected.isEmpty()) return
        val added = LegacyCollectionStore.add(this, selected)
        added.zip(selected).forEach { (card, option) ->
            repository.selectPrinting(
                card.collectionItemId,
                option.cardName,
                option.printingUuid,
                option.finish
            )
        }
        val latest = added.last()
        Snackbar.make(addButton, resources.getQuantityString(R.plurals.cards_added, added.size, added.size), Snackbar.LENGTH_LONG)
            .setAction(R.string.view_card) {
                openOwnedCardDetails(latest)
            }
            .show()
        loadSet()
    }

    companion object {
        const val EXTRA_SET_CODE = "setCode"
        const val EXTRA_SET_NAME = "setName"
        private const val PREF_SET_GALLERY = "set_gallery_mode"
    }
}

private class SetCardAdapter(
    private val context: android.content.Context,
    private val onSelectionChanged: (Int) -> Unit,
    private val onGalleryClicked: (SetCardOption) -> Unit,
    private val onDetailsClicked: (SetCardOption) -> Unit
) : RecyclerView.Adapter<SetCardAdapter.Holder>() {
    private var sourceItems: List<SetCardOption> = emptyList()
    private var items: List<SetCardOption> = emptyList()
    private val selectedUuids = linkedSetOf<String>()
    private val ownedUuids = linkedSetOf<String>()
    private var ownedCardsByPrinting: Map<String, CardInfo> = emptyMap()
    private var sortMode: Int = 0

    fun submit(cards: List<SetCardOption>, owned: Map<String, CardInfo>) {
        sourceItems = cards
        ownedCardsByPrinting = owned
        ownedUuids.clear()
        ownedUuids += owned.keys.intersect(cards.map { it.printingUuid }.toSet())
        selectedUuids.clear()
        selectedUuids += ownedUuids
        applySort()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun setSortMode(mode: Int) {
        sortMode = mode
        applySort()
        notifyDataSetChanged()
    }

    private fun applySort() {
        items = when (sortMode) {
            1 -> sourceItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.cardName })
            2 -> sourceItems.sortedByDescending {
                it.price?.let { price -> PriceCurrency.convert(context, price, it.currency.orEmpty()) }
                    ?: Double.NEGATIVE_INFINITY
            }
            3 -> sourceItems.sortedWith { left, right ->
                val bySet = left.setName.compareTo(right.setName, ignoreCase = true)
                if (bySet != 0) {
                    bySet
                } else {
                    val byNumber = (left.collectorNumber.toIntOrNull() ?: Int.MAX_VALUE)
                        .compareTo(right.collectorNumber.toIntOrNull() ?: Int.MAX_VALUE)
                    if (byNumber != 0) byNumber else left.collectorNumber.compareTo(right.collectorNumber)
                }
            }
            else -> sourceItems
        }
    }

    fun selectAll(selected: Boolean) {
        selectedUuids.clear()
        selectedUuids += ownedUuids
        if (selected) selectedUuids += items.map { it.printingUuid }
        notifyDataSetChanged()
        onSelectionChanged(newSelectionCount())
    }

    fun selectedItems(): List<SetCardOption> = items.filter {
        it.printingUuid in selectedUuids && it.printingUuid !in ownedUuids
    }

    fun visibleItems(): List<SetCardOption> = items.toList()

    fun ownedCard(printingUuid: String): CardInfo? = ownedCardsByPrinting[printingUuid]

    fun ownedCollectionItemIds(): Map<String, String> = ownedCardsByPrinting
        .mapValues { (_, card) -> card.collectionItemId }

    fun areAllSelected(): Boolean = items.isNotEmpty() && items.all { it.printingUuid in selectedUuids }

    private fun newSelectionCount(): Int = selectedUuids.count { it !in ownedUuids }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.set_card_item, parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val owned = item.printingUuid in ownedUuids
        holder.bind(
            item,
            item.printingUuid in selectedUuids,
            owned,
            onGalleryClicked,
            onDetailsClicked
        ) {
            if (!selectedUuids.add(item.printingUuid)) selectedUuids.remove(item.printingUuid)
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) notifyItemChanged(currentPosition)
            onSelectionChanged(newSelectionCount())
        }
        holder.itemView.animate().cancel()
        holder.itemView.alpha = .45f
        holder.itemView.scaleX = .93f
        holder.itemView.scaleY = .93f
        holder.itemView.rotationY = if (position % 2 == 0) -4f else 4f
        holder.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f).setDuration(320L).start()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val price: TextView = view.findViewById(R.id.txtSetCardPrice)
        private val image: RoundedCardImageView = view.findViewById(R.id.imgSetCard)
        private val foilBadge: ImageView = view.findViewById(R.id.imgFoilBadge)
        private val check: CheckBox = view.findViewById(R.id.checkSetCard)
        private val gallery: ImageButton = view.findViewById(R.id.btnSetCardGallery)
        private val details: ImageButton = view.findViewById(R.id.btnSetCardDetails)

        fun bind(
            item: SetCardOption,
            selected: Boolean,
            owned: Boolean,
            openGallery: (SetCardOption) -> Unit,
            openDetails: (SetCardOption) -> Unit,
            toggle: () -> Unit
        ) {
            price.text = item.price?.let { amount ->
                PriceCurrency.format(itemView.context, amount, item.currency ?: PriceCurrency.EUR)
            } ?: itemView.context.getString(R.string.no_price)
            check.text = if (owned) {
                "${item.cardName} · #${item.collectorNumber} · ${itemView.context.getString(R.string.already_owned)}"
            } else {
                "${item.cardName} · #${item.collectorNumber}"
            }
            check.isChecked = selected
            check.isEnabled = !owned
            foilBadge.visibility = if (CardFinish.isFoil(item.finish)) View.VISIBLE else View.GONE
            image.setFoilEffect(CardFinish.isFoil(item.finish))
            CardImageCache.display(itemView.context, item.imageUrl, image)
            image.alpha = if (owned) 1f else .38f
            image.colorFilter = if (owned) null else ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
            image.contentDescription = itemView.context.getString(
                if (owned) R.string.open_card_gallery else R.string.card_missing_disabled
            )
            image.setOnClickListener { openGallery(item) }
            gallery.setOnClickListener { openGallery(item) }
            details.isEnabled = true
            details.alpha = 1f
            details.setOnClickListener { openDetails(item) }
            itemView.setOnClickListener { if (owned) openDetails(item) else toggle() }
            check.setOnClickListener { if (!owned) toggle() }
        }
    }
}
