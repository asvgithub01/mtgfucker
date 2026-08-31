package io.asv.mtgocr.ocrreader

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.asv.mtgocr.ocrreader.data.DeckCatalogStore
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.text.Normalizer
import java.util.Locale

class GroupBuilderActivity : AppCompatActivity() {
    private lateinit var collection: Biblio
    private lateinit var adapter: DeckCardsAdapter
    private lateinit var deckName: String
    private lateinit var formatId: String
    private lateinit var count: TextView
    private var sortMode = 0
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_builder)
        deckName = intent.getStringExtra(EXTRA_DECK_NAME)
            .orEmpty().ifBlank { intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty() }.trim()
        formatId = intent.getStringExtra(EXTRA_FORMAT_ID).orEmpty().ifBlank { "free" }
        collection = DataUtils.readSerializable(this, "myBiblio.Json") ?: run { finish(); return }
        if (deckName.isBlank()) { finish(); return }
        sortMode = intent.getIntExtra(EXTRA_SORT, 0)
        query = intent.getStringExtra(EXTRA_QUERY).orEmpty()

        val rule = DeckFormatRules.byId(formatId)
        findViewById<TextView>(R.id.txtGroupBuilderTitle).apply {
            text = getString(R.string.choose_deck_cards, deckName)
            typeface = Typeface.createFromAsset(assets, "title_font.ttf")
        }
        findViewById<TextView>(R.id.txtDeckFormatRules).text = "${rule.label} · ${rule.summary}"
        count = findViewById(R.id.txtGroupSelectionCount)
        adapter = DeckCardsAdapter(
            Typeface.createFromAsset(assets, "title_font.ttf"),
            rule.maximumSideboard > 0
        ) { updateCount() }
        adapter.initialize(collection.cards, deckName)
        findViewById<RecyclerView>(R.id.groupCardsRecycler).apply {
            layoutManager = LinearLayoutManager(this@GroupBuilderActivity)
            adapter = this@GroupBuilderActivity.adapter
        }
        findViewById<View>(R.id.btnCloseGroupBuilder).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSaveGroup).setOnClickListener { save() }
        findViewById<Spinner>(R.id.spinnerGroupCardSort).apply {
            adapter = ArrayAdapter.createFromResource(
                this@GroupBuilderActivity,
                R.array.collection_sort_options,
                R.layout.spinner_item
            ).also { it.setDropDownViewResource(R.layout.spinner_item) }
            setSelection(sortMode)
            onItemSelectedListener = SimpleItemSelectedListener { position -> sortMode = position; refresh() }
        }
        findViewById<EditText>(R.id.txtGroupCardSearch).apply {
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty(); refresh()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        refresh()
    }

    private fun refresh() {
        val normalizedQuery = normalize(query)
        val visible = collection.cards.filter {
            normalizedQuery.isBlank() || normalize("${it.name} ${it.setName.orEmpty()} ${it.setCode.orEmpty()}").contains(normalizedQuery)
        }.let { cards ->
            when (sortMode) {
                1 -> cards.sortedBy { it.name.lowercase(Locale.ROOT) }
                2 -> cards.sortedByDescending { it.priceM?.toDoubleOrNull() ?: 0.0 }
                3 -> cards.sortedWith(
                    compareBy<CardInfo> { it.setName.orEmpty().lowercase(Locale.ROOT) }
                        .thenBy { collectorSortKey(it.collectorNumber) }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
                else -> cards.sortedBy { it.addedAt }
            }
        }
        adapter.submit(visible)
        updateCount()
    }

    private fun updateCount() {
        val main = collection.cards.filter { adapter.selectedZones[it.collectionItemId] == false }.sumOf { it.quantityCount }
        val sideboard = collection.cards.filter { adapter.selectedZones[it.collectionItemId] == true }.sumOf { it.quantityCount }
        count.text = getString(R.string.deck_selection_count, main, sideboard)
    }

    private fun save() {
        collection.cards.forEach { card ->
            card.removeDeck(deckName)
            adapter.selectedZones[card.collectionItemId]?.let { sideboard -> card.setDeckZone(deckName, sideboard) }
        }
        DeckCatalogStore.upsert(this, collection, deckName, formatId)
        DataUtils.saveSerializable(this, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        val main = collection.cards.filter { it.decks.contains(deckName) && !it.isSideboardForDeck(deckName) }.sumOf { it.quantityCount }
        val side = collection.cards.filter { it.isSideboardForDeck(deckName) }.sumOf { it.quantityCount }
        Toast.makeText(this, getString(R.string.deck_saved, deckName, main, side), Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    companion object {
        const val EXTRA_DECK_NAME = "deckName"
        const val EXTRA_GROUP_NAME = "groupName"
        const val EXTRA_FORMAT_ID = "formatId"
        const val EXTRA_SORT = "sort"
        const val EXTRA_QUERY = "query"
    }
}

private class DeckCardsAdapter(
    private val titleTypeface: Typeface,
    private val allowSideboard: Boolean,
    private val changed: () -> Unit
) : RecyclerView.Adapter<DeckCardsAdapter.Holder>() {
    private var items: List<CardInfo> = emptyList()
    val selectedZones = linkedMapOf<String, Boolean>()

    fun initialize(cards: List<CardInfo>, deckName: String) {
        cards.filter { it.decks.contains(deckName) }.forEach {
            selectedZones[it.collectionItemId] = allowSideboard && it.isSideboardForDeck(deckName)
        }
    }

    fun submit(cards: List<CardInfo>) { items = cards; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.group_card_item, parent, false), titleTypeface
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        holder.bind(card, selectedZones[card.collectionItemId], allowSideboard) { action ->
            when (action) {
                DeckRowAction.TOGGLE_SELECTED -> {
                    if (selectedZones.containsKey(card.collectionItemId)) selectedZones.remove(card.collectionItemId)
                    else selectedZones[card.collectionItemId] = false
                }
                DeckRowAction.TOGGLE_ZONE -> if (allowSideboard) {
                    selectedZones[card.collectionItemId] = !(selectedZones[card.collectionItemId] ?: false)
                }
            }
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) notifyItemChanged(current)
            changed()
        }
    }

    class Holder(view: View, typeface: Typeface) : RecyclerView.ViewHolder(view) {
        private val check: CheckBox = view.findViewById(R.id.checkGroupCard)
        private val image: ImageView = view.findViewById(R.id.imgGroupCard)
        private val foilBadge: ImageView = view.findViewById(R.id.imgFoilBadge)
        private val name: TextView = view.findViewById<TextView>(R.id.txtGroupCardName).also { it.typeface = typeface }
        private val edition: TextView = view.findViewById(R.id.txtGroupCardEdition)
        private val quantity: TextView = view.findViewById<TextView>(R.id.txtGroupCardQuantity).also { it.typeface = typeface }
        private val zone: TextView = view.findViewById(R.id.btnDeckZone)

        fun bind(card: CardInfo, selectedZone: Boolean?, allowSideboard: Boolean, action: (DeckRowAction) -> Unit) {
            name.text = card.name
            edition.text = listOf(card.setName.orEmpty(), card.setCode.orEmpty(), card.finish.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            quantity.text = card.quantityCount.toString()
            foilBadge.visibility = if (CardFinish.isFoil(card.finish)) View.VISIBLE else View.GONE
            check.isChecked = selectedZone != null
            zone.visibility = if (selectedZone != null && allowSideboard) View.VISIBLE else View.INVISIBLE
            zone.text = itemView.context.getString(if (selectedZone == true) R.string.sideboard else R.string.main_deck)
            CardImageCache.display(itemView.context, card.imgPath, image)
            itemView.setOnClickListener { action(DeckRowAction.TOGGLE_SELECTED) }
            check.setOnClickListener { action(DeckRowAction.TOGGLE_SELECTED) }
            zone.setOnClickListener { action(DeckRowAction.TOGGLE_ZONE) }
        }
    }
}

private enum class DeckRowAction { TOGGLE_SELECTED, TOGGLE_ZONE }

private fun collectorSortKey(value: String?): String {
    val raw = value.orEmpty().trim().lowercase(Locale.ROOT)
    val digits = raw.takeWhile { it.isDigit() }
    return if (digits.isNotEmpty()) digits.padStart(12, '0') + raw.drop(digits.length) else "~~~~~~~~~~~~$raw"
}
