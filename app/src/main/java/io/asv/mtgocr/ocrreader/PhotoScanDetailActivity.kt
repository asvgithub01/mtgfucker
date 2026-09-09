package io.asv.mtgocr.ocrreader

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import io.asv.mtgocr.ocrreader.data.PriceCurrency
import io.asv.mtgocr.ocrreader.data.ScanPrintingPolicy
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.io.File
import java.util.Locale

class PhotoScanDetailActivity : AppCompatActivity() {
    private lateinit var repository: CardRepository
    private lateinit var entry: PhotoScanEntry
    private lateinit var adapter: DetectedCardAdapter
    private lateinit var total: TextView
    private lateinit var summary: TextView
    private lateinit var recycler: RecyclerView
    private var pendingScrollState: Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_scan_detail)
        val id = intent.getStringExtra(EXTRA_PHOTO_SCAN_ID).orEmpty()
        entry = PhotoScanStore.find(this, id) ?: run { finish(); return }
        repository = CardRepository.get(this)
        total = findViewById(R.id.txtPhotoDetailTotal)
        summary = findViewById(R.id.txtPhotoDetailSummary)
        Glide.with(this).load(File(entry.imagePath)).dontAnimate().centerCrop()
            .into(findViewById(R.id.imgPhotoDetail))
        adapter = DetectedCardAdapter(
            onOpen = ::openDetails,
            onGallery = ::openGallery,
            onIncrease = ::increaseOwned,
            onDecrease = ::decreaseOwned,
            onEdition = ::chooseEdition,
            onRemove = ::removeDetected
        )
        recycler = findViewById<RecyclerView>(R.id.photoDetectedCardsRecycler).apply {
            layoutManager = LinearLayoutManager(this@PhotoScanDetailActivity)
            adapter = this@PhotoScanDetailActivity.adapter
        }
        findViewById<Button>(R.id.btnClosePhotoDetail).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddDetectedCard).setOnClickListener { addCardManually() }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::entry.isInitialized) {
            PhotoScanStore.find(this, entry.id)?.let { entry = it }
            render()
            restoreScrollPosition()
        }
    }

    private fun render() {
        val owned = LegacyCollectionStore.cards(this)
        adapter.submit(entry.cards, owned)
        val convertedTotal = entry.cards.sumOf { card ->
            PriceCurrency.convert(this, (card.price ?: 0.0) * card.detectedQuantity, card.currency)
        }
        val formatted = PriceCurrency.format(this, convertedTotal, PriceCurrency.preferred(this))
        total.text = getString(R.string.photo_total_price, formatted)
        val count = entry.cards.sumOf { it.detectedQuantity }
        summary.text = getString(R.string.photo_detail_summary, count)
    }

    private fun increaseOwned(card: PhotoScanCard) {
        val added = LegacyCollectionStore.addCopy(this, card.toEditionOption())
        repository.selectPrinting(added.collectionItemId, card.cardName, card.printingUuid, card.finish)
        render()
    }

    private fun decreaseOwned(card: PhotoScanCard) {
        val owned = LegacyCollectionStore.cards(this).firstOrNull {
            it.printingUuid.orEmpty() == card.printingUuid &&
                photoFinishKey(it.finish) == photoFinishKey(card.finish)
        } ?: return
        LegacyCollectionStore.removeCopy(this, card.toEditionOption(), owned.collectionItemId)
        render()
    }

    private fun chooseEdition(card: PhotoScanCard) {
        Toast.makeText(this, R.string.loading_editions, Toast.LENGTH_SHORT).show()
        repository.loadCard(card.cardName, false, false) { options, error ->
            if (error != null || options.isEmpty()) {
                Toast.makeText(this, R.string.editions_error, Toast.LENGTH_SHORT).show()
                return@loadCard
            }
            val labels = options.map {
                "${it.setName} (${it.setCode}) · #${it.collectorNumber} · ${it.finish} · " +
                    (it.price?.let { price -> PriceCurrency.format(this, price, it.currency ?: PriceCurrency.EUR) }
                        ?: getString(R.string.no_price))
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_photo_edition, card.displayName))
                .setItems(labels) { _, which ->
                    applyEdition(card, options[which])
                    saveAndRender()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun addCardManually() {
        val input = EditText(this).apply {
            hint = getString(R.string.detected_card_name_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_detected_card)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add_detected_card) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                Toast.makeText(this, R.string.loading_card_for_photo, Toast.LENGTH_SHORT).show()
                repository.loadCard(name, false, false) { options, error ->
                    val option = ScanPrintingPolicy.preferred(options) ?: options.firstOrNull()
                    if (error != null || option == null) {
                        Toast.makeText(this, R.string.card_not_found_for_photo, Toast.LENGTH_SHORT).show()
                        return@loadCard
                    }
                    entry.cards.add(option.toPhotoCard())
                    saveAndRender()
                    Toast.makeText(this, R.string.card_added_to_photo, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun removeDetected(card: PhotoScanCard) {
        entry.cards.removeAll { it.id == card.id }
        saveAndRender()
    }

    private fun openDetails(card: PhotoScanCard) {
        ownedCard(card)?.let { owned ->
            rememberScrollPosition()
            startActivity(Intent(this, Main2Activity::class.java).apply {
                putExtra(Main2Activity.EXTRA_CARD_NAME, owned.name)
                putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, owned.collectionItemId)
            })
            return
        }
        AlertDialog.Builder(this)
            .setTitle(card.displayName)
            .setMessage(listOf(
                "${card.setName} (${card.setCode}) · #${card.collectorNumber}",
                card.typeLine,
                card.rulesText
            ).filter { it.isNotBlank() }.joinToString("\n\n"))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openGallery(card: PhotoScanCard) {
        val ownedIds = entry.cards.associate { detected ->
            detected.id to ownedCard(detected)?.collectionItemId.orEmpty()
        }
        rememberScrollPosition()
        if (!CardGalleryLauncher.openPhotoCards(this, entry.cards, card.id, ownedIds)) {
            pendingScrollState = null
        }
    }

    private fun rememberScrollPosition() {
        pendingScrollState = recycler.layoutManager?.onSaveInstanceState()
    }

    private fun restoreScrollPosition() {
        val state = pendingScrollState ?: return
        pendingScrollState = null
        recycler.post { recycler.layoutManager?.onRestoreInstanceState(state) }
    }

    private fun ownedCard(card: PhotoScanCard): CardInfo? = LegacyCollectionStore.cards(this)
        .firstOrNull {
            it.printingUuid.orEmpty() == card.printingUuid &&
                photoFinishKey(it.finish) == photoFinishKey(card.finish)
        }

    private fun saveAndRender() {
        entry.state = PhotoScanEntry.STATE_READY
        PhotoScanStore.save(this, entry)
        render()
    }

    private fun applyEdition(card: PhotoScanCard, option: CardEditionOption) {
        card.cardName = option.cardName
        card.displayName = option.displayName
        card.printingUuid = option.printingUuid
        card.setCode = option.setCode
        card.setName = option.setName
        card.collectorNumber = option.collectorNumber
        card.finish = option.finish
        card.imageUrl = option.imageUrl.orEmpty()
        card.typeLine = option.typeLine
        card.rulesText = option.rulesText
        card.price = option.price
        card.currency = option.currency ?: "EUR"
    }

    private fun CardEditionOption.toPhotoCard() = PhotoScanCard(
        cardName = cardName,
        displayName = displayName,
        detectedQuantity = 1,
        printingUuid = printingUuid,
        setCode = setCode,
        setName = setName,
        collectorNumber = collectorNumber,
        finish = finish,
        imageUrl = imageUrl.orEmpty(),
        typeLine = typeLine,
        rulesText = rulesText,
        price = price,
        currency = currency ?: "EUR"
    )

    companion object {
        const val EXTRA_PHOTO_SCAN_ID = "photoScanId"
    }
}

private class DetectedCardAdapter(
    private val onOpen: (PhotoScanCard) -> Unit,
    private val onGallery: (PhotoScanCard) -> Unit,
    private val onIncrease: (PhotoScanCard) -> Unit,
    private val onDecrease: (PhotoScanCard) -> Unit,
    private val onEdition: (PhotoScanCard) -> Unit,
    private val onRemove: (PhotoScanCard) -> Unit
) : RecyclerView.Adapter<DetectedCardAdapter.Holder>() {
    private var cards: List<PhotoScanCard> = emptyList()
    private var owned: List<CardInfo> = emptyList()

    fun submit(items: List<PhotoScanCard>, ownedCards: List<CardInfo>) {
        cards = items.toList()
        owned = ownedCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.photo_detected_card_item, parent, false)
    )

    override fun getItemCount() = cards.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = cards[position]
        val quantity = owned.filter {
            it.printingUuid.orEmpty() == card.printingUuid &&
                photoFinishKey(it.finish) == photoFinishKey(card.finish)
        }.sumOf { it.quantityCount }
        holder.bind(card, quantity, onOpen, onGallery, onIncrease, onDecrease, onEdition, onRemove)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.imgPhotoDetectedCard)
        private val name: TextView = view.findViewById(R.id.txtPhotoDetectedName)
        private val meta: TextView = view.findViewById(R.id.txtPhotoDetectedMeta)
        private val owned: TextView = view.findViewById(R.id.txtPhotoDetectedOwned)
        private val plus: ImageButton = view.findViewById(R.id.btnPhotoOwnedPlus)
        private val minus: ImageButton = view.findViewById(R.id.btnPhotoOwnedMinus)
        private val edition: Button = view.findViewById(R.id.btnPhotoChangeEdition)
        private val remove: ImageButton = view.findViewById(R.id.btnRemoveDetectedCard)

        fun bind(
            card: PhotoScanCard,
            ownedQuantity: Int,
            open: (PhotoScanCard) -> Unit,
            gallery: (PhotoScanCard) -> Unit,
            increase: (PhotoScanCard) -> Unit,
            decrease: (PhotoScanCard) -> Unit,
            changeEdition: (PhotoScanCard) -> Unit,
            removeCard: (PhotoScanCard) -> Unit
        ) {
            name.text = card.displayName
            val price = card.price?.let {
                PriceCurrency.format(itemView.context, it, card.currency)
            }
                ?: itemView.context.getString(R.string.no_price)
            meta.text = itemView.context.getString(
                R.string.detected_card_meta,
                card.detectedQuantity,
                card.setName,
                card.setCode,
                card.collectorNumber,
                price
            )
            owned.text = itemView.context.getString(R.string.owned_copies, ownedQuantity)
            minus.isEnabled = ownedQuantity > 0
            minus.alpha = if (ownedQuantity > 0) 1f else .35f
            CardImageCache.display(itemView.context, card.imageUrl, image)
            itemView.setOnClickListener { open(card) }
            image.setOnClickListener { gallery(card) }
            plus.setOnClickListener { increase(card) }
            minus.setOnClickListener { if (ownedQuantity > 0) decrease(card) }
            edition.setOnClickListener { changeEdition(card) }
            remove.setOnClickListener { removeCard(card) }
        }
    }
}

private fun photoFinishKey(value: String?): String = when (
    value.orEmpty().trim().lowercase(Locale.ROOT)
) {
    "", "normal", "regular", "non-foil" -> "nonfoil"
    else -> value.orEmpty().trim().lowercase(Locale.ROOT)
}
