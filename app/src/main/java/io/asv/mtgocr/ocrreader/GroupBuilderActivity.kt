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
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.text.Normalizer
import java.util.Locale

class GroupBuilderActivity : AppCompatActivity() {
    private lateinit var collection: Biblio
    private lateinit var adapter: GroupCardsAdapter
    private lateinit var groupName: String
    private lateinit var count: TextView
    private var sortMode = 0
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_builder)
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty().trim()
        collection = DataUtils.readSerializable(this, "myBiblio.Json") ?: run { finish(); return }
        if (groupName.isBlank()) { finish(); return }
        sortMode = intent.getIntExtra(EXTRA_SORT, 0)
        query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        val title = findViewById<TextView>(R.id.txtGroupBuilderTitle)
        title.text = getString(R.string.choose_group_cards, groupName)
        title.typeface = Typeface.createFromAsset(assets, "title_font.ttf")
        count = findViewById(R.id.txtGroupSelectionCount)
        adapter = GroupCardsAdapter(assets.let { Typeface.createFromAsset(it, "title_font.ttf") }) { updateCount() }
        findViewById<RecyclerView>(R.id.groupCardsRecycler).apply {
            layoutManager = LinearLayoutManager(this@GroupBuilderActivity)
            adapter = this@GroupBuilderActivity.adapter
        }
        findViewById<View>(R.id.btnCloseGroupBuilder).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSaveGroup).setOnClickListener { save() }
        findViewById<Spinner>(R.id.spinnerGroupCardSort).apply {
            adapter = ArrayAdapter.createFromResource(this@GroupBuilderActivity, R.array.collection_sort_options, R.layout.spinner_item).also {
                it.setDropDownViewResource(R.layout.spinner_item)
            }
            setSelection(sortMode)
            onItemSelectedListener = SimpleItemSelectedListener { position -> sortMode = position; refresh() }
        }
        findViewById<EditText>(R.id.txtGroupCardSearch).apply {
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); refresh() }
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
                3 -> cards.sortedWith(compareBy<CardInfo> { it.setName.orEmpty().lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) })
                else -> cards.sortedBy { it.addedAt }
            }
        }
        adapter.submit(visible)
        updateCount()
    }

    private fun updateCount() { count.text = getString(R.string.selected_cards_count, adapter.selectedIds.size) }

    private fun save() {
        collection.cards.forEach { card -> if (card.collectionItemId in adapter.selectedIds) card.addGroup(groupName) }
        DataUtils.saveSerializable(this, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        Toast.makeText(this, getString(R.string.group_created, groupName, adapter.selectedIds.size), Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    companion object {
        const val EXTRA_GROUP_NAME = "groupName"
        const val EXTRA_SORT = "sort"
        const val EXTRA_QUERY = "query"
    }
}

private class GroupCardsAdapter(private val titleTypeface: Typeface, private val changed: () -> Unit) : RecyclerView.Adapter<GroupCardsAdapter.Holder>() {
    private var items: List<CardInfo> = emptyList()
    val selectedIds = linkedSetOf<String>()
    fun submit(cards: List<CardInfo>) { items = cards; notifyDataSetChanged() }
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.group_card_item, parent, false), titleTypeface)
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        holder.bind(card, card.collectionItemId in selectedIds) {
            if (!selectedIds.add(card.collectionItemId)) selectedIds.remove(card.collectionItemId)
            holder.check.isChecked = card.collectionItemId in selectedIds
            changed()
        }
    }
    class Holder(view: View, typeface: Typeface) : RecyclerView.ViewHolder(view) {
        val check: CheckBox = view.findViewById(R.id.checkGroupCard)
        private val image: ImageView = view.findViewById(R.id.imgGroupCard)
        private val name: TextView = view.findViewById<TextView>(R.id.txtGroupCardName).also { it.typeface = typeface }
        private val edition: TextView = view.findViewById(R.id.txtGroupCardEdition)
        private val quantity: TextView = view.findViewById<TextView>(R.id.txtGroupCardQuantity).also { it.typeface = typeface }
        fun bind(card: CardInfo, selected: Boolean, toggle: () -> Unit) {
            name.text = card.name
            edition.text = listOf(card.setName.orEmpty(), card.setCode.orEmpty(), card.finish.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            quantity.text = card.quantityCount.toString()
            check.isChecked = selected
            CardImageCache.display(itemView.context, card.imgPath, image)
            itemView.setOnClickListener { toggle() }
            check.setOnClickListener { toggle() }
        }
    }
}
