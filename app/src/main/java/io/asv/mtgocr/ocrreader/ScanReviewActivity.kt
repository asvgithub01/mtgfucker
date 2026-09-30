package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import android.view.View
import android.view.ViewGroup
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import io.asv.collectorvision.EdScanJobs
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Review is explicit: photo analysis never silently changes inventory. */
class ScanReviewActivity : AppCompatActivity() {
    private val io=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var library: String
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var filter: CheckBox
    private var entries=emptyList<ScanReviewStore.Entry>()
    private var visible=emptyList<ScanReviewStore.Entry>()
    private var sourceFilter="all"
    private var active=false
    private var loading=false
    private val resolving=mutableSetOf<String>()
    private val resolutionErrors=mutableSetOf<String>()
    private val selected=linkedMapOf<String,ScanReviewStore.Entry>()
    private lateinit var applySelected: Button
    private lateinit var selectAll: CheckBox
    private var applying=false
    data class Draft(val original: ScanReviewStore.Entry, val option: CardEditionOption? = null,
        val language: String = original.card.languageCode, val image: String? = null,
        val validated: io.asv.mtgocr.ocrreader.data.CardImageVariant? = null)
    class DraftState : androidx.lifecycle.ViewModel() {
        val values=linkedMapOf<String,Draft>()
        val attemptedPresets=mutableSetOf<String>()
        val proposalOriginals=mutableMapOf<String,Draft>()
        val errors=mutableMapOf<String,String>()
    }
    private val presetPending=mutableSetOf<String>()
    private val presetRetryAfter=mutableMapOf<String,Long>()
    private fun screenEntries(): List<ScanReviewStore.Entry> = visible.drop(list.firstVisiblePosition.coerceAtLeast(0))
        .take((list.lastVisiblePosition-list.firstVisiblePosition+1).coerceIn(1,3))
    private val proposalOriginals by lazy { androidx.lifecycle.ViewModelProvider(this)[DraftState::class.java].proposalOriginals }
    private val rowErrors by lazy { androidx.lifecycle.ViewModelProvider(this)[DraftState::class.java].errors }
    private val reopened=mutableSetOf<String>()
    private fun reviewed(entry: ScanReviewStore.Entry)=entry.photoKey !in reopened &&
        ReviewPresentationPolicy.reviewed(entries.filter { it.photoKey==entry.photoKey })
    private fun report(entry: ScanReviewStore.Entry, message: Int, error: Throwable? = null) {
        applying=false
        rowErrors[entry.card.collectionItemId]=getString(message)
        android.util.Log.w("ScanReview",getString(message),error)
        render()
    }
    private fun notifyFull(message: Int) {
        if(!isFinishing && !isDestroyed) AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok,null).show()
    }
    private val attemptedPresets by lazy { androidx.lifecycle.ViewModelProvider(this)[DraftState::class.java].attemptedPresets }
    private val drafts by lazy { androidx.lifecycle.ViewModelProvider(this)[DraftState::class.java].values }
    private val rowOrder=linkedMapOf<String,Int>()
    private val retained=mutableSetOf<String>()
    private val editGeneration=mutableMapOf<String,Int>()
    private lateinit var analyseButton: Button
    private lateinit var analysisProgress: ProgressBar
    private lateinit var analysisStatus: TextView
    private var batchTag=""
    private var observedBatch: androidx.lifecycle.LiveData<List<androidx.work.WorkInfo>>?=null
    private var analysisRunning=false
    private var signature=""
    private var restoredListState: android.os.Parcelable?=null
    private val rows=ReviewRows()
    private val tick=object: Runnable { override fun run() { if (active) { reload(); handler.postDelayed(this,2500) } } }
    override fun onCreate(state: Bundle?) {
        MagicPalette.applyTheme(this); super.onCreate(state)
        library=LibraryCatalog.activeFile(this)
        sourceFilter=state?.getString("reviewSource") ?: "all"
        state?.getStringArrayList("reviewOrder")?.forEach { rowOrder[it]=rowOrder.size }
        retained.addAll(state?.getStringArrayList("reviewRetained").orEmpty())
        restoredListState=state?.getParcelable("reviewScroll")
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        root.addView(TextView(this).apply {
            setText(R.string.review_flow_help); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
            setOnClickListener { AlertDialog.Builder(this@ScanReviewActivity).setTitle(R.string.postprocess_scans)
                .setMessage(R.string.review_flow_help).setPositiveButton(android.R.string.ok,null).show() }
        })
        analyseButton=Button(this).apply { isEnabled=false; setText(R.string.review_analyse_all); setOnClickListener { analyse(entries) } }
        root.addView(analyseButton)
        analysisProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal)
        root.addView(analysisProgress,LinearLayout.LayoutParams(-1,-2))
        analysisStatus=TextView(this); root.addView(analysisStatus)
        val sources=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        listOf("all" to R.string.review_source_all,"ocr" to R.string.review_source_ocr,"edscan" to R.string.review_source_edscan).forEach { (source,title) ->
            sources.addView(RadioButton(this).apply {
                id=View.generateViewId(); setText(title); isChecked=source==sourceFilter
                setOnClickListener { sourceFilter=source; selected.clear(); render() }
            },RadioGroup.LayoutParams(0,-2,1f))
        }
        root.addView(sources)
        status=TextView(this); root.addView(status)
        filter=CheckBox(this).apply { setText(R.string.review_pending_only); setOnCheckedChangeListener { _,_->render() } }; root.addView(filter)
        val selectionRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        selectAll=CheckBox(this).apply {
            setText(R.string.review_select_all)
            setOnClickListener {
                if(isChecked) {
                    val choices=ReviewApplyPolicy.bulkChoices(visible,selected.values)
                    selected.clear(); choices.forEach { selected[it.key]=it }
                } else selected.clear()
                rows.notifyDataSetChanged(); updateApplyButton()
            }
        }
        selectionRow.addView(selectAll,LinearLayout.LayoutParams(-2,-2))
        applySelected=Button(this).apply { setOnClickListener { applyBatch() } }
        selectionRow.addView(applySelected,LinearLayout.LayoutParams(0,-2,1f))
        selectionRow.visibility=View.GONE; root.addView(selectionRow)
        list=ListView(this); list.adapter=rows; root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { setText(R.string.close); setOnClickListener { onBackPressedDispatcher.onBackPressed() } })
        setContentView(root)
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(applying) return
                if(drafts.isEmpty()) finish()
                else AlertDialog.Builder(this@ScanReviewActivity).setMessage(R.string.review_save_first)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(R.string.review_discard_changes) { _,_-> drafts.clear(); finish() }.show()
            }
        })
        filter.isChecked=state?.getBoolean("reviewFilter") ?: true
        updateApplyButton()
        observeBatch(getSharedPreferences("review_batch",0).getString(library,"").orEmpty())
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putParcelable("reviewScroll",list.onSaveInstanceState())
        state.putStringArrayList("reviewOrder",ArrayList(rowOrder.keys))
        state.putStringArrayList("reviewRetained",ArrayList(retained))
        state.putString("reviewSource",sourceFilter); state.putBoolean("reviewFilter",filter.isChecked)
    }
    override fun onResume() { super.onResume(); active=true; handler.post(tick) }
    override fun onPause() { active=false; handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
    private fun reload() {
        if (loading || applying || isDestroyed || isFinishing) return
        loading=true
        io.execute {
            val result=runCatching { ScanReviewStore.read(applicationContext,library) }.getOrDefault(emptyList())
            runOnUiThread {
                loading=false
                if (!active || isDestroyed || LibraryCatalog.activeFile(this)!=library) return@runOnUiThread
                result.forEach { rowOrder.getOrPut(it.photoKey) { rowOrder.size } }
                entries=result.sortedBy { rowOrder[it.photoKey] }
                analyseButton.isEnabled=entries.isNotEmpty() && !analysisRunning && !applying
                val next=entries.joinToString("|") { "${it.key}:${it.stamp}:${it.state}:${it.card.printingUuid}:${it.card.languageCode}:${it.card.finish}:${it.candidateImage}:${it.candidateLabel}" }
                if(next!=signature) { signature=next; render() }
                presetLanguages()
                val screenPhotos=screenEntries().map { it.photoKey }.toSet()
                result.filter { it.photoKey in screenPhotos && !resolutionErrors.contains(it.key) && it.state!="reviewed" && it.visual && it.candidateId.isNotBlank() && it.candidateLabel.isBlank() }
                    .take((2-resolving.size).coerceAtLeast(0)).forEach(::resolveArt)
            }
        }
    }
    private fun render() {
        val anchor=visible.getOrNull(list.firstVisiblePosition)?.photoKey
        val oldPosition=list.firstVisiblePosition
        val offset=list.getChildAt(0)?.top ?: 0
        val scoped=ReviewPresentationPolicy.primary(entries.filter { sourceFilter=="all" || it.source==sourceFilter })
        visible=scoped.filter { !filter.isChecked || !reviewed(it) }
        status.text=if(scoped.isEmpty()) getString(R.string.review_no_photos) else getString(R.string.review_counts,
            scoped.count { it.state=="conflict" },scoped.count { it.state=="pending" },scoped.size)
        if (!applying) selected.entries.removeAll { (_,old) -> visible.none { ReviewApplyPolicy.sameProposal(old,it) && ReviewApplyPolicy.applicable(it) } }
        rows.notifyDataSetChanged()
        val position=visible.indexOfFirst { it.photoKey==anchor }.takeIf { it>=0 } ?: oldPosition.coerceAtMost((visible.size-1).coerceAtLeast(0))
        list.setSelectionFromTop(position,offset)
        if(visible.isNotEmpty()) restoredListState?.let { list.onRestoreInstanceState(it); restoredListState=null }
        updateApplyButton()
    }
    private fun updateApplyButton() {
        analyseButton.isEnabled=!applying && !analysisRunning && entries.isNotEmpty()
        applySelected.text=getString(R.string.review_apply_selected,selected.size)
        applySelected.isEnabled=!applying && drafts.isEmpty() && selected.isNotEmpty()
        val choices=ReviewApplyPolicy.bulkChoices(visible,selected.values)
        selectAll.isEnabled=!applying && (choices.isNotEmpty() || selected.isNotEmpty())
        selectAll.isChecked=choices.isNotEmpty() && choices.all { selected.containsKey(it.key) }
    }
    private fun label(entry: ScanReviewStore.Entry): CharSequence {
        val state=when(entry.state) {
            "conflict"->R.string.review_conflict; "suggestion"->R.string.review_suggestion
            "consistent"->R.string.review_consistent; "reviewed"->R.string.review_reviewed
            "pending"->R.string.review_pending; else->R.string.review_inconclusive
        }
        val current="${entry.card.name} · ${entry.card.setCode.orEmpty()} #${entry.card.collectorNumber.orEmpty()}"
        val text=android.text.SpannableStringBuilder()
        fun heading(value: String, color: Int) {
            val start=text.length
            text.append(value).append("\n")
            text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),start,text.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(android.text.style.ForegroundColorSpan(color),start,text.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        heading(getString(if(entry.source=="ocr") R.string.review_origin_ocr else R.string.review_origin_edscan),
            if(entry.source=="ocr") 0xFF2196F3.toInt() else 0xFF009688.toInt())
        if(entry.provisional && entry.state!="reviewed") {
            heading(getString(R.string.review_provisional_printing),0xFFEF6C00.toInt())

        } else if(entry.state=="conflict" || entry.state=="suggestion") {
            heading(getString(if(entry.visual) R.string.review_correct_printing else R.string.review_correct_language),0xFFEF6C00.toInt())
            val before=if(entry.visual) current else entry.card.languageCode.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label).orEmpty().ifBlank { "?" }
            val after=if(entry.visual) entry.candidateLabel else entry.language?.let(CardLanguageFlag::label).orEmpty()
            text.append(getString(R.string.review_change_values,before,after)).append("\n")
            if(!entry.visual) text.append(current).append("\n")
        }
        val photoEvidence=entries.filter { it.photoKey==entry.photoKey }
        val languageEvidence=photoEvidence.firstOrNull { !it.visual }
        val titleEvidence=photoEvidence.firstOrNull { it.titleState.isNotBlank() } ?: entry
        val saved=entry.card.languageCode.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label)
            ?: getString(R.string.review_language_unknown)

        if(languageEvidence!=null) {
            val detected=languageEvidence.language?.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label)
                ?: getString(if(entry.state=="pending") R.string.review_pending else R.string.review_language_unknown)
            val start=text.length
            text.append(getString(R.string.review_detected_language,detected)).append("\n")
            text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),start,text.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if(titleEvidence.titleState.isNotBlank()) text.append(getString(when(titleEvidence.titleState) {
            "conflict" -> R.string.review_title_conflict
            "consistent" -> R.string.review_title_matches
            else -> R.string.review_title_unknown
        },titleEvidence.observedTitle)).append("\n")
        text.append(getString(state)).append(" · ").append(getString(if(entry.visual) R.string.review_art else R.string.review_language))
        photoEvidence.filter { it.key!=entry.key && !it.provisional }.forEach { evidence ->
            val stateLabel=when(evidence.state) {
                "pending"->R.string.review_pending; "consistent"->R.string.review_consistent
                "reviewed"->R.string.review_reviewed; "conflict"->R.string.review_conflict
                "suggestion"->R.string.review_suggestion; else->R.string.review_inconclusive
            }
            text.append("\n").append(getString(if(evidence.visual) R.string.review_art else R.string.review_language))
                .append(": ").append(getString(stateLabel))
        }
        // Emphasise actual values, keeping the surrounding instructions readable.
        val emphasized=listOf(current,entry.candidateLabel,saved,
            entry.language?.let(CardLanguageFlag::label).orEmpty()).filter { it.isNotBlank() }
        for(value in emphasized) {
            var offset=text.indexOf(value)
            while(offset>=0) {
                text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),offset,offset+value.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                offset=text.indexOf(value,offset+value.length)
            }
        }
        return text
    }
    private inner class ReviewRows: BaseAdapter() {
        override fun getCount()=visible.size
        override fun getItem(position: Int)=visible[position]
        override fun hasStableIds()=true
        override fun getItemId(position: Int)=(rowOrder[visible[position].photoKey] ?: position).toLong()
        override fun getView(position: Int, old: View?, parent: ViewGroup): View {
            val entry=visible[position]
            val row=old as? LinearLayout ?: LinearLayout(this@ScanReviewActivity).apply {
                orientation=LinearLayout.VERTICAL; setPadding(8,12,8,20)
                addView(LinearLayout(context).apply {
                    orientation=LinearLayout.VERTICAL
                    addView(TextView(context).apply { textSize=16f })
                    addView(LinearLayout(context).apply {
                        gravity=android.view.Gravity.CENTER_VERTICAL
                        addView(TextView(context),LinearLayout.LayoutParams(0,-2,1f))
                        addView(ImageButton(context).apply { contentDescription=getString(R.string.editions); scaleType=ImageView.ScaleType.FIT_CENTER },
                            LinearLayout.LayoutParams((48*resources.displayMetrics.density).toInt(),(48*resources.displayMetrics.density).toInt()))
                    })
                    addView(Button(context))
                    addView(TextView(context))
                    addView(LinearLayout(context).apply {
                        addView(Button(context).apply { setText(R.string.review_apply_changes) },LinearLayout.LayoutParams(0,-2,1f))
                        addView(Button(context).apply { setText(R.string.review_discard_changes) },LinearLayout.LayoutParams(0,-2,1f))
                    })
                    addView(TextView(context).apply { setTextColor(0xFFEF5350.toInt()); setPadding(8,12,8,12) })
                })
                val pictures=LinearLayout(context)
                for (caption in listOf(R.string.review_photo,R.string.review_identified)) {
                    val column=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
                    val editionHeader=LinearLayout(context).apply { gravity=android.view.Gravity.CENTER }
                    editionHeader.addView(ImageView(context).apply { scaleType=ImageView.ScaleType.FIT_CENTER },
                        LinearLayout.LayoutParams((28*resources.displayMetrics.density).toInt(),(28*resources.displayMetrics.density).toInt()))
                    editionHeader.addView(TextView(context).apply { setText(caption); gravity=17; setTypeface(null,android.graphics.Typeface.BOLD) },
                        LinearLayout.LayoutParams(0,-2,1f))
                    column.addView(editionHeader)
                    column.addView(TextView(context).apply { gravity=17; visibility=View.GONE })
                    column.addView(ZoomableImageView(context).apply { setBackgroundColor(0xFF101010.toInt()) },
                        LinearLayout.LayoutParams(-1,(260*resources.displayMetrics.density).toInt()))
                    column.addView(Button(context).apply { setText(R.string.review_zoom) })
                    pictures.addView(column,LinearLayout.LayoutParams(0,-2,1f))
                }
                addView(pictures)
                addView(CheckBox(context).apply { setText(R.string.review_apply) })
                addView(Button(context).apply { setText(R.string.view_card_details) })
                val manual=LinearLayout(context)
                manual.addView(Button(context).apply { setText(R.string.review_apply_changes) },LinearLayout.LayoutParams(0,-2,1f))
                manual.addView(Button(context).apply { setText(R.string.review_discard_changes) },LinearLayout.LayoutParams(0,-2,1f))
                addView(manual)
                addView(Button(context).apply { setText(R.string.review_reanalyse) })
                val actions=LinearLayout(context)
                actions.addView(Button(context).apply { setText(R.string.review_all_correct) },LinearLayout.LayoutParams(0,-2,1f))
                actions.addView(Button(context).apply { setText(R.string.recent_delete) },LinearLayout.LayoutParams(0,-2,1f))
                addView(actions)
                addView(Button(context).apply { setText(R.string.review_retry_catalog) })
            }
            fun enable(view: View) { view.isEnabled=true; if(view is ViewGroup) for(i in 0 until view.childCount) enable(view.getChildAt(i)) }
            enable(row)
            val done=reviewed(entry)
            val draft=drafts[entry.card.collectionItemId]
            val proposal=entries.firstOrNull { it.photoKey==entry.photoKey && it.visual && it.candidateId.isNotBlank() }
            val header=row.getChildAt(0) as LinearLayout
            (header.getChildAt(0) as TextView).text=if(done) "✓ "+getString(R.string.review_reviewed) else label(entry)
            val identity=header.getChildAt(1) as LinearLayout
            val option=draft?.option
            (identity.getChildAt(0) as TextView).text="${option?.displayName ?: entry.card.name} · ${option?.setCode ?: entry.card.setCode} #${option?.collectorNumber ?: entry.card.collectorNumber}"
            (identity.getChildAt(1) as ImageButton).apply {
                SetSymbolLoader.display(this@ScanReviewActivity,option?.setCode ?: entry.card.setCode.orEmpty(),this)
                isEnabled=!applying; setOnClickListener { chooseEdition(entry) }
            }
            (header.getChildAt(2) as Button).apply {
                text=if(draft==null) getString(R.string.review_saved_language,CardLanguageFlag.label(entry.card.languageCode))
                    else getString(R.string.review_selected_language,CardLanguageFlag.label(draft.language))+" · "+getString(R.string.review_unsaved)
                isEnabled=!applying; setOnClickListener { chooseLanguage(entry) }
            }
            val finish=option?.finish ?: entry.card.finish.orEmpty()
            (header.getChildAt(3) as TextView).text=getString(R.string.review_finish_value,
                if(finish=="etched") "etched" else getString(if(CardFinish.isFoil(finish)) R.string.foil else R.string.nonfoil)) +
                if(draft!=null) " · "+getString(R.string.review_unsaved) else ""
            val pictures=row.getChildAt(1) as LinearLayout
            val urls=listOf(android.net.Uri.fromFile(File(EdScanJobs.directory(this@ScanReviewActivity),"${entry.photoKey}.jpg")).toString(),
                draft?.image ?: option?.imageUrl ?: entry.card.imgPath.orEmpty())
            urls.forEachIndexed { index,url ->
                val editionHeader=(pictures.getChildAt(index) as LinearLayout).getChildAt(0) as LinearLayout
                val setCode=if(index==0) entry.card.setCode.orEmpty() else option?.setCode ?: entry.card.setCode.orEmpty()
                val symbol=editionHeader.getChildAt(0) as ImageView
                SetSymbolLoader.display(this@ScanReviewActivity,setCode,symbol)
                symbol.contentDescription=setCode
                (editionHeader.getChildAt(1) as TextView).text=(if(index==1 && proposal!=null && !done)
                    getString(if(proposalOriginals.containsKey(entry.card.collectionItemId)) R.string.review_proposal_first else R.string.review_identified_first)
                    else getString(if(index==0) R.string.review_photo else R.string.review_identified))+
                    setCode.takeIf { it.isNotBlank() }?.let { " · ${it.uppercase()}" }.orEmpty()
                val target=(pictures.getChildAt(index) as LinearLayout).getChildAt(2) as ZoomableImageView
                target.setFoilEffectModes(FoilEffectMode.RAINBOW)
                target.setFoilEffect(index==1 && CardFinish.isFoil(finish))
                val message=(pictures.getChildAt(index) as LinearLayout).getChildAt(1) as TextView
                val unavailable=index==1 && url.isBlank()
                message.visibility=if(unavailable) View.VISIBLE else View.GONE
                if(unavailable) {
                    // No image request exists: never manufacture an endless loading animation.
                    CardImageCache.display(this@ScanReviewActivity,null,target)
                    target.setImageResource(R.drawable.backmtg)
                    message.setText(when(ReviewImageState.missing(entry.visual,entry.state,entry.candidateId,
                        entry.candidateLabel,resolutionErrors.contains(entry.key))) {
                        ReviewImageState.Missing.CATALOG_ERROR -> R.string.review_catalog_error
                        ReviewImageState.Missing.CATALOG_PENDING -> R.string.review_catalog_pending
                        ReviewImageState.Missing.ANALYSIS_PENDING -> R.string.review_pending
                        ReviewImageState.Missing.NO_IDENTIFICATION -> R.string.review_no_identification
                        ReviewImageState.Missing.NO_IMAGE -> R.string.review_no_image
                    })
                } else CardImageCache.display(this@ScanReviewActivity,url,target)
                target.contentDescription=getString(if(index==0) R.string.review_photo else R.string.review_identified)
                target.setOnClickListener {
                    if(index==1 && proposal!=null && !done && !applying) toggleProposal(entry,proposal)
                    else if(url.isNotBlank()) startActivity(Intent(this@ScanReviewActivity,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL,url))
                }
                ((pictures.getChildAt(index) as LinearLayout).getChildAt(3) as Button).apply {
                    isEnabled=url.isNotBlank() && !applying
                    setOnClickListener { startActivity(Intent(this@ScanReviewActivity,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL,url)) }
                }
                target.setOnLongClickListener {
                    if(url.isNotBlank()) startActivity(Intent(this@ScanReviewActivity,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL,url))
                    true
                }
                message.visibility=if(index==1 && proposal!=null && !done) View.VISIBLE else message.visibility
                if(index==1 && proposal!=null && !done) message.setText(if(proposal.state=="inconclusive") R.string.review_weak_proposal else R.string.review_toggle_hint)
            }
            val check=row.getChildAt(2) as CheckBox
            check.visibility=View.GONE
            check.setOnCheckedChangeListener(null)
            check.isEnabled=!applying && draft==null && ReviewApplyPolicy.applicable(entry)
            check.isChecked=selected.containsKey(entry.key)
            check.setOnCheckedChangeListener { _,checked ->
                if(checked) {
                    // Several photographs of a merged row must not apply contradictory corrections.
                    selected.entries.removeAll { it.value.card.collectionItemId==entry.card.collectionItemId }
                    selected[entry.key]=entry
                } else selected.remove(entry.key)
                rows.notifyDataSetChanged(); updateApplyButton()
            }
            (row.getChildAt(3) as Button).apply { isEnabled=!applying; setOnClickListener { open(entry) } }
            row.getChildAt(4).visibility=View.GONE
            val manual=header.getChildAt(4) as LinearLayout
            (manual.getChildAt(0) as Button).apply {
                setText(if(done) R.string.review_reopen else R.string.review_apply_changes)
                isEnabled=!applying && !analysisRunning && !entries.any { it.photoKey==entry.photoKey && it.key in resolving }
                setOnClickListener { if(done) { reopened.add(entry.photoKey); render() } else saveDraft(entry) }
            }
            (manual.getChildAt(1) as Button).apply { visibility=if(draft!=null && !done) View.VISIBLE else View.GONE; isEnabled=!applying && draft!=null; setOnClickListener { drafts.remove(entry.card.collectionItemId); proposalOriginals.remove(entry.card.collectionItemId); rowErrors.remove(entry.card.collectionItemId); render() } }
            (row.getChildAt(5) as Button).apply {
                isEnabled=!applying && !analysisRunning
                setOnClickListener { reanalyse(entry) }
            }
            val actions=row.getChildAt(6) as LinearLayout
            actions.getChildAt(0).visibility=View.GONE
            (actions.getChildAt(1) as Button).apply { isEnabled=!applying; setOnClickListener { delete(entry) } }
            (row.getChildAt(7) as Button).apply {
                visibility=if(resolutionErrors.contains(entry.key)) View.VISIBLE else View.GONE
                isEnabled=!applying && !resolving.contains(entry.key)
                setOnClickListener { resolutionErrors.remove(entry.key); resolveArt(entry); rows.notifyDataSetChanged() }
            }
            val errorText=header.getChildAt(5) as TextView
            if(row.childCount==8) row.addView(View(this@ScanReviewActivity).apply { visibility=View.GONE })
            errorText.text=rowErrors[entry.card.collectionItemId].orEmpty()
            errorText.visibility=if(errorText.text.isNotBlank()) View.VISIBLE else View.GONE
            var ocrChoice=row.getChildAt(9) as? Button
            if(ocrChoice==null) { ocrChoice=Button(this@ScanReviewActivity); row.addView(ocrChoice) }
            val observed=entries.firstOrNull { it.photoKey==entry.photoKey && it.titleState=="conflict" }?.observedTitle.orEmpty()
            ocrChoice.visibility=if(!done && observed.isNotBlank()) View.VISIBLE else View.GONE
            ocrChoice.text=getString(R.string.review_ocr_choice,observed)
            ocrChoice.isEnabled=!applying
            ocrChoice.setOnClickListener { chooseEditionForName(entry,observed) }
            row.alpha=if(done) .55f else 1f
            fun controls(view: View, enabled: Boolean) {
                view.isEnabled=enabled
                if(view is ViewGroup) for(i in 0 until view.childCount) controls(view.getChildAt(i),enabled)
            }
            if(done || applying) {
                controls(header,false); controls(manual,false); controls(actions,false)
                row.getChildAt(5).isEnabled=false
                row.getChildAt(3).isEnabled=!applying
                if(done && !applying) manual.getChildAt(0).isEnabled=true
            }
            return row
        }
    }
    private fun applyBatch() {
        val batch=selected.values.toList()
        AlertDialog.Builder(this).setMessage(getString(R.string.review_apply_confirm,batch.size)+"\n"+getString(R.string.review_language_fallback_notice))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.review_apply) { _,_->
                applying=true; render()
                fun next(index: Int, success: Int) {
                    if(isDestroyed || isFinishing) return
                    if(index==batch.size) {
                        applying=false; updateApplyButton(); reload()
                        Toast.makeText(this,getString(R.string.review_apply_result,success,batch.size-success),Toast.LENGTH_LONG).show()
                        return
                    }
                    applyEntry(batch[index]) { applied ->
                        if(applied) selected.remove(batch[index].key)
                        next(index+1,success+if(applied) 1 else 0)
                    }
                }
                next(0,0)
            }.show()
    }
    private fun applyEntry(entry: ScanReviewStore.Entry, done: (Boolean)->Unit) {
        if(!ReviewApplyPolicy.applicable(entry)) { done(false); return }
        if(!entry.visual) { applyLanguage(entry,done); return }
        val repository=CardRepository.get(this)
        repository.loadCollectorVisionPrinting(entry.candidateId) { options,_ ->
            if(isDestroyed || isFinishing) return@loadCollectorVisionPrinting
            val option=options.firstOrNull { it.finish==entry.card.finish }
            if(option==null) { done(false); return@loadCollectorVisionPrinting }
            fun save(chosen: CardEditionOption, targetLanguage: String = entry.card.languageCode) {
                io.execute {
                    val saved=runCatching {
                        val current=ScanReviewStore.read(this,library).firstOrNull { ReviewApplyPolicy.sameProposal(entry,it) && ReviewApplyPolicy.applicable(it) }
                        if(current!=null) LegacyCollectionStore.applyReviewedPrinting(this,library,entry.card,chosen,targetLanguage) else null
                    }.getOrNull()
                    if(saved!=null) runCatching { ScanReviewStore.acknowledge(this,entry,saved) }
                    runOnUiThread {
                        if(saved!=null) repository.selectEdition(saved.collectionItemId,chosen) { done(true) } else done(false)
                    }
                }
            }
            if(entry.card.languageCode.isBlank()) save(option)
            else repository.loadImageLanguages(option.setCode,option.collectorNumber) { variants,_ ->
                if(isDestroyed || isFinishing) return@loadImageLanguages
                val language=ReviewLanguagePolicy.forEdition(entry.card.languageCode,variants.map { it.languageCode })
                val variant=variants.firstOrNull { it.languageCode==language }
                if(language==null || variant==null) done(false)
                else save(option.copy(imageUrl=variant.imageUrl,
                    displayName=variant.printedName.takeIf { it.isNotBlank() } ?: option.displayName),language)
            }
        }
    }
    private fun resolveArt(entry: ScanReviewStore.Entry) {
        if (!resolving.add(entry.key)) return
        // The repository shares an IO queue. Make a long wait explicit rather than spinning forever.
        val timeout=Runnable {
            if(resolving.contains(entry.key) && !isDestroyed) {
                resolutionErrors.add(entry.key); rows.notifyDataSetChanged()
            }
        }
        handler.postDelayed(timeout,30000)
        CardRepository.get(this).loadCollectorVisionPrinting(entry.candidateId) { options,error ->
            handler.removeCallbacks(timeout)
            if (isDestroyed) return@loadCollectorVisionPrinting
            val option=options.firstOrNull()
            if(option==null) {
                resolving.remove(entry.key); resolutionErrors.add(entry.key)
                android.util.Log.w("ScanReview", "Catalog lookup failed: ${entry.candidateId}",error)
                rows.notifyDataSetChanged()
                return@loadCollectorVisionPrinting
            }
            io.execute {
                val saved=runCatching {
                    ScanReviewStore.write(File(EdScanJobs.directory(this),"${entry.key}.match.json"),JSONObject()
                        .put("candidateId",entry.candidateId).put("printingUuid",option.printingUuid).put("name",option.cardName)
                        .put("set",option.setCode).put("number",option.collectorNumber).put("imageUrl",option.imageUrl.orEmpty()))
                }.isSuccess
                runOnUiThread {
                    resolving.remove(entry.key)
                    if(saved) resolutionErrors.remove(entry.key) else resolutionErrors.add(entry.key)
                    if(!isDestroyed) { rows.notifyDataSetChanged(); reload() }
                }
            }
        }
    }
    private fun chooseEdition(entry: ScanReviewStore.Entry) {
        val names=(listOf(entry.card.name)+entries.filter { it.photoKey==entry.photoKey }.flatMap { listOf(it.candidateLabel.substringBefore(" · "),it.observedTitle.takeIf { _ -> it.titleState=="conflict" }.orEmpty()) }).filter { it.isNotBlank() }.distinct()
        if(names.size>1) AlertDialog.Builder(this).setTitle(R.string.editions).setItems(names.toTypedArray()) { _,index ->
            chooseEditionForName(entry,names[index])
        }.setNegativeButton(android.R.string.cancel,null).show()
        else chooseEditionForName(entry,names.firstOrNull() ?: entry.card.name)
    }
    private fun chooseEditionForName(entry: ScanReviewStore.Entry, name: String) {
        val generation=(editGeneration[entry.card.collectionItemId] ?: 0)+1
        editGeneration[entry.card.collectionItemId]=generation
        val initial=drafts[entry.card.collectionItemId] ?: Draft(entry)
        EditionPicker.showGrid(this,name,"*") { option ->
            // Selecting an edition/finish only edits the draft, never inventory.
            CardRepository.get(this).loadImageLanguages(option.setCode,option.collectorNumber) { variants,error ->
                if(isDestroyed || isFinishing || editGeneration[entry.card.collectionItemId]!=generation) return@loadImageLanguages
                if(error!=null || variants.isEmpty()) {
                    report(entry,R.string.review_catalog_retry,error); return@loadImageLanguages
                }
                val language=initial.language.takeIf { code -> variants.any { it.languageCode==code } }.orEmpty()
                val variant=variants.firstOrNull { it.languageCode==language }
                proposalOriginals.remove(entry.card.collectionItemId)
                drafts[entry.card.collectionItemId]=initial.copy(option=option,language=language,image=variant?.imageUrl ?: option.imageUrl,validated=variant)
                retained.add(entry.photoKey); render()
                if(language.isBlank()) notifyFull(R.string.review_select_language)
            }
        }
    }
    /** Prepare, never persist, a unique OCR suggestion supported by the selected printing. */
    private fun presetLanguages() {
        val byItem=entries.groupBy { it.card.collectionItemId }
        screenEntries().forEach { entry ->
            val id=entry.card.collectionItemId
            if(reviewed(entry)) return@forEach
            val evidence=byItem[id].orEmpty()
            val proposed=ReviewLanguagePolicy.proposedLanguage(entry.card.languageCode,
                evidence.mapNotNull { it.language },evidence.any { it.titleState=="conflict" }) ?: return@forEach
            if(!ReviewLanguagePolicy.hasSelectedPrinting(entry.card.printingUuid,entry.card.setCode,entry.card.collectorNumber) || drafts.containsKey(id)) return@forEach
            val token="$id:${entry.card.printingUuid}:${entry.card.finish}:$proposed"
            if(token in attemptedPresets || System.currentTimeMillis()<(presetRetryAfter[token] ?: 0L) || !presetPending.add(token)) return@forEach
            val generation=editGeneration[id] ?: 0
            CardRepository.get(this).loadImageLanguages(entry.card.setCode.orEmpty(),entry.card.collectorNumber.orEmpty()) { variants,error ->
                presetPending.remove(token)
                if(isDestroyed || isFinishing || LibraryCatalog.activeFile(this)!=library || applying || (editGeneration[id] ?: 0)!=generation || drafts.containsKey(id)) return@loadImageLanguages
                if(entries.none { it.card.collectionItemId==id && it.card.printingUuid==entry.card.printingUuid && it.card.finish==entry.card.finish && it.card.languageCode.isBlank() } ||
                    ReviewLanguagePolicy.proposedLanguage("",entries.filter { it.card.collectionItemId==id }.mapNotNull { it.language },entries.any { it.card.collectionItemId==id && it.titleState=="conflict" })!=proposed) return@loadImageLanguages
                if(error!=null) { presetRetryAfter[token]=System.currentTimeMillis()+30000; return@loadImageLanguages }
                attemptedPresets.add(token)
                val variant=variants.firstOrNull { it.languageCode==proposed } ?: return@loadImageLanguages
                drafts[id]=Draft(entry,language=proposed,image=variant.imageUrl,validated=variant)
                retained.add(entry.photoKey); render()
            }
        }
    }

    private fun chooseLanguage(entry: ScanReviewStore.Entry) {
        val generation=(editGeneration[entry.card.collectionItemId] ?: 0)+1
        editGeneration[entry.card.collectionItemId]=generation
        val draft=drafts[entry.card.collectionItemId] ?: Draft(entry)
        if(draft.option==null && !ReviewLanguagePolicy.hasSelectedPrinting(entry.card.printingUuid,entry.card.setCode,entry.card.collectorNumber)) {
            notifyFull(R.string.review_edition_first); return
        }
        val set=draft.option?.setCode ?: entry.card.setCode.orEmpty()
        val number=draft.option?.collectorNumber ?: entry.card.collectorNumber.orEmpty()
        CardRepository.get(this).loadImageLanguages(set,number) { variants,error ->
            if(isDestroyed || isFinishing || editGeneration[entry.card.collectionItemId]!=generation) return@loadImageLanguages
            if(error!=null || variants.isEmpty()) { report(entry,R.string.review_catalog_retry,error); return@loadImageLanguages }
            AlertDialog.Builder(this).setTitle(R.string.review_choose_language)
                .setItems(variants.map { CardLanguageFlag.label(it.languageCode) }.toTypedArray()) { _,index ->
                    val variant=variants[index]
                    drafts[entry.card.collectionItemId]=draft.copy(language=variant.languageCode,image=variant.imageUrl,validated=variant)
                    retained.add(entry.photoKey); render()
                }.setNegativeButton(android.R.string.cancel,null).show()
        }
    }
    private fun toggleProposal(entry: ScanReviewStore.Entry, proposal: ScanReviewStore.Entry) {
        val id=entry.card.collectionItemId
        val generation=(editGeneration[id] ?: 0)+1; editGeneration[id]=generation
        proposalOriginals.remove(id)?.let { drafts[id]=it; render(); return }
        val original=drafts[id] ?: Draft(entry)
        CardRepository.get(this).loadCollectorVisionPrinting(proposal.candidateId) { options,error ->
            if(isDestroyed || isFinishing || editGeneration[id]!=generation || applying) return@loadCollectorVisionPrinting
            val option=options.firstOrNull { it.finish==(original.option?.finish ?: entry.card.finish) } ?: options.firstOrNull()
            if(option==null) { report(entry,R.string.review_catalog_retry,error); return@loadCollectorVisionPrinting }
            CardRepository.get(this).loadImageLanguages(option.setCode,option.collectorNumber) { variants,failure ->
                if(isDestroyed || isFinishing || editGeneration[id]!=generation || applying) return@loadImageLanguages
                if(failure!=null || variants.isEmpty()) { report(entry,R.string.review_catalog_retry,failure); return@loadImageLanguages }
                val suggested=entries.firstOrNull { it.photoKey==entry.photoKey && !it.visual }?.language
                val variant=variants.firstOrNull { it.languageCode==original.language } ?: variants.firstOrNull { it.languageCode==suggested }
                proposalOriginals[id]=original
                drafts[id]=original.copy(option=option,language=variant?.languageCode.orEmpty(),image=variant?.imageUrl ?: option.imageUrl,validated=variant)
                rowErrors.remove(id); render()
            }
        }
    }

    private fun saveDraft(entry: ScanReviewStore.Entry) {
        if(applying) return
        val id=entry.card.collectionItemId
        val draft=drafts[id] ?: Draft(entry).also { drafts[id]=it }
        if(draft.language.isBlank()) { report(entry,R.string.review_select_language); return }
        rowErrors.remove(id)
        applying=true; render()
        fun save(option: CardEditionOption) {
            fun persist(variant: io.asv.mtgocr.ocrreader.data.CardImageVariant) {
                val localized=option.copy(imageUrl=variant.imageUrl,displayName=variant.printedName.takeIf { it.isNotBlank() } ?: option.displayName)
                io.execute {
                    val result=runCatching { requireNotNull(LegacyCollectionStore.applyReviewedPrinting(applicationContext,library,draft.original.card,localized,draft.language,allowFinishChange=true)) }
                    runOnUiThread {
                        if(isDestroyed || isFinishing) return@runOnUiThread
                        val saved=result.getOrNull()
                        if(saved==null) { report(entry,R.string.review_operation_failed,result.exceptionOrNull()); return@runOnUiThread }
                        // If a later write fails, retries use the now-persisted snapshot, not a stale one.
                        drafts[id]=draft.copy(original=draft.original.copy(card=saved.snapshotForPersistence()),option=localized,validated=variant)
                        CardRepository.get(this).selectReviewedEdition(id,localized) { error ->
                            if(isDestroyed || isFinishing) return@selectReviewedEdition
                            if(error!=null) { report(entry,R.string.review_operation_failed,error); return@selectReviewedEdition }
                            io.execute {
                                val reviewed=runCatching {
                                    val current=ScanReviewStore.read(applicationContext,library)
                                    current.filter { it.photoKey==entry.photoKey && it.card.collectionItemId==id }.forEach {
                                        ScanReviewStore.acknowledge(applicationContext,it,saved)
                                    }
                                    val fresh=ScanReviewStore.read(applicationContext,library)
                                    check(ReviewPresentationPolicy.reviewed(fresh.filter { it.photoKey==entry.photoKey }))
                                    fresh
                                }
                                runOnUiThread {
                                    if(isDestroyed || isFinishing) return@runOnUiThread
                                    applying=false
                                    if(reviewed.isSuccess) {
                                        entries=reviewed.getOrThrow().sortedBy { rowOrder[it.photoKey] }
                                        drafts.remove(id); proposalOriginals.remove(id); selected.remove(entry.key)
                                        reopened.remove(entry.photoKey); retained.remove(entry.photoKey); rowErrors.remove(id)
                                        signature=""; render()
                                    } else report(entry,R.string.review_operation_failed,reviewed.exceptionOrNull())
                                }
                            }
                        }
                    }
                }
            }
            val validated=draft.validated?.takeIf { it.languageCode==draft.language }
            if(validated!=null) persist(validated)
            else CardRepository.get(this).loadImageLanguages(option.setCode,option.collectorNumber) { variants,error ->
                if(isDestroyed || isFinishing) return@loadImageLanguages
                if(error!=null || variants.isEmpty()) { report(entry,R.string.review_catalog_retry,error); return@loadImageLanguages }
                val variant=variants.firstOrNull { it.languageCode==draft.language }
                if(variant==null) report(entry,R.string.review_language_unavailable) else persist(variant)
            }
        }
        if(draft.option!=null) save(draft.option)
        else CardRepository.get(this).loadCachedPrinting(entry.card.name,entry.card.printingUuid,null,entry.card.setCode.orEmpty(),entry.card.collectorNumber.orEmpty()) { options,error ->
            if(isDestroyed || isFinishing) return@loadCachedPrinting
            val option=options.firstOrNull { it.printingUuid==entry.card.printingUuid && it.finish==entry.card.finish }
            if(option!=null) save(option) else report(entry,R.string.review_catalog_retry,error)
        }
    }

    private fun observeBatch(tag: String) {
        if(tag.isBlank() || tag==batchTag) return
        observedBatch?.removeObservers(this); batchTag=tag
        observedBatch=androidx.work.WorkManager.getInstance(this).getWorkInfosByTagLiveData(tag).also { live ->
            live.observe(this) { jobs ->
                val done=jobs.count { it.state.isFinished }
                val failed=jobs.count { it.state==androidx.work.WorkInfo.State.FAILED || it.state==androidx.work.WorkInfo.State.CANCELLED }
                analysisRunning=jobs.any { !it.state.isFinished }
                analysisProgress.max=jobs.size.coerceAtLeast(1); analysisProgress.progress=done
                analysisStatus.text=getString(R.string.review_analysis_progress,done,jobs.size,failed)
                analyseButton.isEnabled=!analysisRunning && !applying
                if(!analysisRunning) { signature=""; reload() }
            }
        }
    }
    private fun analyse(items: List<ScanReviewStore.Entry>) {
        if(analysisRunning || applying) return
        if(drafts.isNotEmpty()) { notifyFull(R.string.review_save_first); return }
        analyseButton.isEnabled=false; analysisRunning=true
        io.execute {
            val result=runCatching { ReviewAnalysisBatch.start(applicationContext,library,items) }
            runOnUiThread {
                if(isDestroyed || isFinishing) return@runOnUiThread
                analysisRunning=false
                result.onSuccess { observeBatch(it) }.onFailure {
                    analyseButton.isEnabled=true; notifyFull(R.string.review_operation_failed)
                }
                signature=""; reload()
            }
        }
    }

    private fun reanalyse(entry: ScanReviewStore.Entry) {
        AlertDialog.Builder(this).setMessage(R.string.review_flow_help)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.review_reanalyse) { _,_->
                analyse(listOf(entry))
            }.show()
    }
    private fun delete(entry: ScanReviewStore.Entry) {
        AlertDialog.Builder(this).setTitle(entry.card.name).setMessage(R.string.recent_delete_confirm)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.recent_delete) { _,_->
                applying=true; render()
                io.execute {
                    val ok=runCatching {
                        if(LibraryCatalog.activeFile(this)!=library) return@runCatching false
                        val current=LegacyCollectionStore.cards(this).firstOrNull { it.collectionItemId==entry.card.collectionItemId }
                        if(current==null || current.printingUuid!=entry.card.printingUuid || current.finish!=entry.card.finish ||
                            current.languageCode!=entry.card.languageCode || current.quantity!=entry.card.quantity) return@runCatching false
                        LegacyCollectionStore.editRecentQuantity(this,library,current.collectionItemId,0,true,entry.card)
                    }.getOrDefault(false)
                    runOnUiThread {
                        if(ok) CardRepository.get(this).clearSelectedEdition(entry.card.collectionItemId)
                        if(!isDestroyed) {
                            applying=false; reload(); updateApplyButton()
                            if(!ok) notifyFull(R.string.review_operation_failed)
                        }
                    }
                }
            }.show()
    }

    private fun open(entry: ScanReviewStore.Entry) {
        val choices=mutableListOf(getString(R.string.review_photo),getString(R.string.view_card_details))

        AlertDialog.Builder(this).setTitle(entry.card.name).setItems(choices.toTypedArray()) { _,index ->
            when(index) {
                0->startActivity(Intent(this,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL,
                    android.net.Uri.fromFile(File(EdScanJobs.directory(this),"${entry.photoKey}.jpg")).toString()))
                1->startActivity(Intent(this,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,entry.card.name)
                    .putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID,entry.card.collectionItemId))


            }
        }.show()
    }
    private fun acknowledge(entry: ScanReviewStore.Entry) {
        applying=true; render()
        io.execute {
            val ok=runCatching {
                val current=ScanReviewStore.read(this,library).firstOrNull { ReviewApplyPolicy.sameProposal(entry,it) }
                    ?: return@runCatching false
                ScanReviewStore.acknowledge(this,entry,current.card)
                true
            }.getOrDefault(false)
            runOnUiThread {
                if(!isDestroyed) {
                    applying=false; reload(); updateApplyButton()
                    if(!ok) notifyFull(R.string.review_operation_failed)
                }
            }
        }
    }
    private fun applyLanguage(entry: ScanReviewStore.Entry, done: (Boolean)->Unit = { reload() }) {
        if(entry.provisional || entry.card.isProvisionalEdition) {
            notifyFull(R.string.review_edition_first); done(false); return
        }
        val language=entry.language ?: run { done(false); return }
        CardRepository.get(this).loadImageLanguages(entry.card.setCode.orEmpty(),entry.card.collectorNumber.orEmpty()) { variants,_ ->
            if(isDestroyed || isFinishing) return@loadImageLanguages
            val variant=variants.firstOrNull { it.languageCode==language }
            if(variant==null) { notifyFull(R.string.review_language_unavailable); done(false); return@loadImageLanguages }
            io.execute {
                var applied=false
                runCatching {
                val valid=ScanReviewStore.read(this,library).any { ReviewApplyPolicy.sameProposal(entry,it) && ReviewApplyPolicy.applicable(it) }
                if(valid && LibraryCatalog.activeFile(this)==library) {
                    val current=LegacyCollectionStore.cards(this).firstOrNull { it.collectionItemId==entry.card.collectionItemId }
                    if(current!=null && current.printingUuid==entry.card.printingUuid && current.finish==entry.card.finish && current.languageCode==entry.card.languageCode) {
                        LegacyCollectionStore.updateLanguageVariant(this,current.collectionItemId,language,variant.imageUrl)?.let {
                            applied=true
                            ScanReviewStore.acknowledge(this,entry,it)
                        }
                    }
                }
                }
                runOnUiThread { done(applied) }
            }
        }
    }
}
