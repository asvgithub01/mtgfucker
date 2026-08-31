package io.asv.mtgocr.ocrreader

import android.os.Bundle
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
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

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
        adapter = SetCardAdapter { selectedCount ->
            addButton.isEnabled = selectedCount > 0
            addButton.text = getString(R.string.add_selected_cards_count, selectedCount)
            selectAll.isChecked = adapter.areAllSelected()
        }
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

    private fun loadSet() {
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_set_cards)
        repository.loadSet(setCode) result@{ cards, error ->
            progress.visibility = View.GONE
            if (error != null) {
                status.text = error.message ?: getString(R.string.set_cards_error)
                return@result
            }
            repository.ownedPrintingUuids { ownedUuids ->
                adapter.submit(cards, ownedUuids)
                status.text = resources.getQuantityString(R.plurals.set_cards_found, cards.size, cards.size)
            }
        }
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
                startActivity(android.content.Intent(this, Main2Activity::class.java).apply {
                    putExtra(Main2Activity.EXTRA_CARD_NAME, latest.name)
                    putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, latest.collectionItemId)
                })
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
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SetCardAdapter.Holder>() {
    private var sourceItems: List<SetCardOption> = emptyList()
    private var items: List<SetCardOption> = emptyList()
    private val selectedUuids = linkedSetOf<String>()
    private val ownedUuids = linkedSetOf<String>()
    private var sortMode: Int = 0

    fun submit(cards: List<SetCardOption>, owned: Set<String>) {
        sourceItems = cards
        ownedUuids.clear()
        ownedUuids += owned.intersect(cards.map { it.printingUuid }.toSet())
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
            2 -> sourceItems.sortedByDescending { it.price ?: Double.NEGATIVE_INFINITY }
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

    fun areAllSelected(): Boolean = items.isNotEmpty() && items.all { it.printingUuid in selectedUuids }

    private fun newSelectionCount(): Int = selectedUuids.count { it !in ownedUuids }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.set_card_item, parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val owned = item.printingUuid in ownedUuids
        holder.bind(item, item.printingUuid in selectedUuids, owned) {
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
        private val image: ImageView = view.findViewById(R.id.imgSetCard)
        private val foilBadge: ImageView = view.findViewById(R.id.imgFoilBadge)
        private val check: CheckBox = view.findViewById(R.id.checkSetCard)

        fun bind(item: SetCardOption, selected: Boolean, owned: Boolean, toggle: () -> Unit) {
            price.text = item.price?.let { amount ->
                runCatching {
                    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                        currency = Currency.getInstance(item.currency ?: "EUR")
                    }.format(amount)
                }.getOrElse { "%.2f %s".format(amount, item.currency.orEmpty()) }
            } ?: itemView.context.getString(R.string.no_price)
            check.text = if (owned) {
                "${item.cardName} · #${item.collectorNumber} · ${itemView.context.getString(R.string.already_owned)}"
            } else {
                "${item.cardName} · #${item.collectorNumber}"
            }
            check.isChecked = selected
            check.isEnabled = !owned
            foilBadge.visibility = if (CardFinish.isFoil(item.finish)) View.VISIBLE else View.GONE
            CardImageCache.display(itemView.context, item.imageUrl, image)
            itemView.setOnClickListener { if (!owned) toggle() }
            check.setOnClickListener { if (!owned) toggle() }
        }
    }
}
