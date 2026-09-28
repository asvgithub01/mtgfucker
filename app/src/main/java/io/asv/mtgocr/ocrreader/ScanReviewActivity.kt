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
    private val rows=ReviewRows()
    private val tick=object: Runnable { override fun run() { if (active) { reload(); handler.postDelayed(this,2500) } } }
    override fun onCreate(state: Bundle?) {
        MagicPalette.applyTheme(this); super.onCreate(state)
        library=LibraryCatalog.activeFile(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        root.addView(Button(this).apply { setText(R.string.postprocess_scans); setOnClickListener { EdScanJobs.postprocess(applicationContext,library); reload() } })
        val sources=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        listOf("all" to R.string.review_source_all,"ocr" to R.string.review_source_ocr,"edscan" to R.string.review_source_edscan).forEach { (source,title) ->
            sources.addView(RadioButton(this).apply {
                id=View.generateViewId(); setText(title); isChecked=source==sourceFilter
                setOnClickListener { sourceFilter=source; selected.clear(); render() }
            },RadioGroup.LayoutParams(0,-2,1f))
        }
        root.addView(sources)
        status=TextView(this); root.addView(status)
        filter=CheckBox(this).apply { setText(R.string.review_only_conflicts); setOnCheckedChangeListener { _,_->render() } }; root.addView(filter)
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
        root.addView(selectionRow)
        list=ListView(this); list.adapter=rows; root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { setText(R.string.close); setOnClickListener { finish() } })
        setContentView(root)
        updateApplyButton()
        EdScanJobs.postprocess(applicationContext,library)
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
                entries=result; render()
                result.filter { !resolutionErrors.contains(it.key) && it.visual && it.candidateId.isNotBlank() && it.candidateLabel.isBlank() }
                    .forEach(::resolveArt)
            }
        }
    }
    private fun render() {
        val scoped=entries.filter { sourceFilter=="all" || it.source==sourceFilter }
        visible=scoped.filter { !filter.isChecked || it.state=="conflict" || it.state=="suggestion" }
        status.text=if(scoped.isEmpty()) getString(R.string.review_no_photos) else getString(R.string.review_counts,
            scoped.count { it.state=="conflict" },scoped.count { it.state=="pending" },scoped.size)
        if (!applying) selected.entries.removeAll { (_,old) -> visible.none { ReviewApplyPolicy.sameProposal(old,it) && ReviewApplyPolicy.applicable(it) } }
        rows.notifyDataSetChanged()
        updateApplyButton()
    }
    private fun updateApplyButton() {
        applySelected.text=getString(R.string.review_apply_selected,selected.size)
        applySelected.isEnabled=!applying && selected.isNotEmpty()
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
            text.append(current).append("\n")
        } else if(entry.state=="conflict" || entry.state=="suggestion") {
            heading(getString(if(entry.visual) R.string.review_correct_printing else R.string.review_correct_language),0xFFEF6C00.toInt())
            val before=if(entry.visual) current else entry.card.languageCode.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label).orEmpty().ifBlank { "?" }
            val after=if(entry.visual) entry.candidateLabel else entry.language?.let(CardLanguageFlag::label).orEmpty()
            text.append(getString(R.string.review_change_values,before,after)).append("\n")
            if(!entry.visual) text.append(current).append("\n")
        } else text.append(current).append("\n")
        val saved=entry.card.languageCode.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label)
            ?: getString(R.string.review_language_unknown)
        text.append(getString(R.string.review_saved_language,saved)).append("\n")
        if(!entry.visual) {
            val detected=entry.language?.takeIf { it.isNotBlank() }?.let(CardLanguageFlag::label)
                ?: getString(if(entry.state=="pending") R.string.review_pending else R.string.review_language_unknown)
            val start=text.length
            text.append(getString(R.string.review_detected_language,detected)).append("\n")
            text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),start,text.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text.append(getString(state)).append(" · ").append(getString(if(entry.visual) R.string.review_art else R.string.review_language))
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
        override fun getItemId(position: Int)=position.toLong()
        override fun getView(position: Int, old: View?, parent: ViewGroup): View {
            val entry=visible[position]
            val row=old as? LinearLayout ?: LinearLayout(this@ScanReviewActivity).apply {
                orientation=LinearLayout.VERTICAL; setPadding(8,12,8,20)
                addView(TextView(context).apply { textSize=16f })
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
                    column.addView(ImageView(context).apply { scaleType=ImageView.ScaleType.FIT_CENTER; setBackgroundColor(0xFF101010.toInt()) },
                        LinearLayout.LayoutParams(-1,(260*resources.displayMetrics.density).toInt()))
                    pictures.addView(column,LinearLayout.LayoutParams(0,-2,1f))
                }
                addView(pictures)
                addView(CheckBox(context).apply { setText(R.string.review_apply) })
                addView(Button(context).apply { setText(R.string.view_card_details) })
                val manual=LinearLayout(context)
                manual.addView(Button(context).apply { setText(R.string.editions) },LinearLayout.LayoutParams(0,-2,1f))
                manual.addView(Button(context).apply { setText(R.string.review_choose_language) },LinearLayout.LayoutParams(0,-2,1f))
                addView(manual)
                addView(Button(context).apply { setText(R.string.review_reanalyse) })
                val actions=LinearLayout(context)
                actions.addView(Button(context).apply { setText(R.string.review_all_correct) },LinearLayout.LayoutParams(0,-2,1f))
                actions.addView(Button(context).apply { setText(R.string.recent_delete) },LinearLayout.LayoutParams(0,-2,1f))
                addView(actions)
                addView(Button(context).apply { setText(R.string.review_retry_catalog) })
            }
            (row.getChildAt(0) as TextView).text=label(entry)
            val pictures=row.getChildAt(1) as LinearLayout
            val urls=listOf(android.net.Uri.fromFile(File(EdScanJobs.directory(this@ScanReviewActivity),"${entry.photoKey}.jpg")).toString(),
                if(entry.visual) entry.candidateImage else entry.card.imgPath.orEmpty())
            urls.forEachIndexed { index,url ->
                val editionHeader=(pictures.getChildAt(index) as LinearLayout).getChildAt(0) as LinearLayout
                val setCode=if(index==0 || !entry.visual) entry.card.setCode.orEmpty() else entry.candidateSet
                val symbol=editionHeader.getChildAt(0) as ImageView
                SetSymbolLoader.display(this@ScanReviewActivity,setCode,symbol)
                symbol.contentDescription=setCode
                (editionHeader.getChildAt(1) as TextView).text=getString(if(index==0) R.string.review_photo else R.string.review_identified)+
                    setCode.takeIf { it.isNotBlank() }?.let { " · ${it.uppercase()}" }.orEmpty()
                val target=(pictures.getChildAt(index) as LinearLayout).getChildAt(2) as ImageView
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
                target.setOnClickListener { if(url.isNotBlank()) startActivity(Intent(this@ScanReviewActivity,CardImageActivity::class.java)
                    .putExtra(CardImageActivity.EXTRA_IMAGE_URL,url)) }
            }
            val check=row.getChildAt(2) as CheckBox
            check.setOnCheckedChangeListener(null)
            check.isEnabled=!applying && ReviewApplyPolicy.applicable(entry)
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
            val manual=row.getChildAt(4) as LinearLayout
            (manual.getChildAt(0) as Button).apply { isEnabled=!applying; setOnClickListener { chooseEdition(entry) } }
            (manual.getChildAt(1) as Button).apply { isEnabled=!applying; setOnClickListener { chooseLanguage(entry) } }
            (row.getChildAt(5) as Button).apply {
                isEnabled=!applying && entries.none { it.photoKey==entry.photoKey && it.visual && it.state=="pending" && it.candidateId.isBlank() }
                setOnClickListener { reanalyse(entry) }
            }
            val actions=row.getChildAt(6) as LinearLayout
            (actions.getChildAt(0) as Button).apply {
                isEnabled=!applying && entry.state!="pending" && entry.state!="reviewed"
                setOnClickListener { AlertDialog.Builder(this@ScanReviewActivity).setMessage(R.string.review_keep_confirm)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.review_all_correct) { _,_-> acknowledge(entry) }.show() }
            }
            (actions.getChildAt(1) as Button).apply { isEnabled=!applying; setOnClickListener { delete(entry) } }
            (row.getChildAt(7) as Button).apply {
                visibility=if(resolutionErrors.contains(entry.key)) View.VISIBLE else View.GONE
                isEnabled=!applying && !resolving.contains(entry.key)
                setOnClickListener { resolutionErrors.remove(entry.key); resolveArt(entry); rows.notifyDataSetChanged() }
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
        EditionPicker.showGrid(this,entry.card.name,entry.card.finish.orEmpty()) { option ->
            if(option.finish!=entry.card.finish) {
                Toast.makeText(this,R.string.review_finish_unavailable,Toast.LENGTH_LONG).show()
                return@showGrid
            }
            CardRepository.get(this).loadImageLanguages(option.setCode,option.collectorNumber) { variants,_ ->
                if(isDestroyed || isFinishing) return@loadImageLanguages
                val language=ReviewLanguagePolicy.forEdition(entry.card.languageCode,variants.map { it.languageCode })
                if(language==null) {
                    Toast.makeText(this,R.string.review_language_unavailable,Toast.LENGTH_LONG).show()
                    return@loadImageLanguages
                }
                val variant=variants.firstOrNull { it.languageCode==language }
                val localized=option.copy(imageUrl=variant?.imageUrl ?: option.imageUrl,
                    displayName=variant?.printedName?.takeIf { it.isNotBlank() } ?: option.displayName)
                applying=true; render()
                io.execute {
                    val saved=runCatching { LegacyCollectionStore.applyReviewedPrinting(this,library,entry.card,localized,language) }.getOrNull()
                    if(saved!=null) runCatching { ScanReviewStore.acknowledge(this,entry,saved) }
                    runOnUiThread {
                        fun finishEdit() {
                            if(isDestroyed || isFinishing) return
                            applying=false; selected.remove(entry.key); reload(); updateApplyButton()
                            if(saved==null) Toast.makeText(this,getString(R.string.review_apply_result,0,1),Toast.LENGTH_LONG).show()
                            else if(language!=entry.card.languageCode) Toast.makeText(this,getString(R.string.review_language_changed,language.uppercase()),Toast.LENGTH_LONG).show()
                        }
                        if(saved!=null) CardRepository.get(this).selectEdition(saved.collectionItemId,localized) { finishEdit() } else finishEdit()
                    }
                }
            }
        }
    }
    private fun chooseLanguage(entry: ScanReviewStore.Entry) {
        CardRepository.get(this).loadImageLanguages(entry.card.setCode.orEmpty(),entry.card.collectorNumber.orEmpty()) { variants,_ ->
            if(isDestroyed || isFinishing) return@loadImageLanguages
            if(variants.isEmpty()) { Toast.makeText(this,R.string.review_language_unavailable,Toast.LENGTH_LONG).show(); return@loadImageLanguages }
            val spinner=Spinner(this).apply {
                layoutParams=LinearLayout.LayoutParams(-1,-2)
                minimumHeight=(56*resources.displayMetrics.density).toInt()
                isClickable=true
            }
            val container=LinearLayout(this).apply {
                setPadding(16,8,16,8); addView(spinner)
                setOnClickListener { spinner.performClick() }
            }
            spinner.adapter=GalleryLanguageSpinnerAdapter(this,variants,variants.map { "${it.languageCode.uppercase()} — ${it.printedName}" })
            spinner.setSelection(variants.indexOfFirst { it.languageCode==entry.card.languageCode }.coerceAtLeast(0))
            AlertDialog.Builder(this).setTitle(R.string.review_choose_language).setView(container)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.review_apply) { _,_->
                    val variant=variants[spinner.selectedItemPosition]
                    applying=true; render()
                    io.execute {
                        val saved=runCatching {
                            if(LibraryCatalog.activeFile(this)!=library) return@runCatching null
                            val current=LegacyCollectionStore.cards(this).firstOrNull { it.collectionItemId==entry.card.collectionItemId }
                            if(current==null || current.printingUuid!=entry.card.printingUuid || current.finish!=entry.card.finish || current.languageCode!=entry.card.languageCode) return@runCatching null
                            LegacyCollectionStore.updateLanguageVariant(this,current.collectionItemId,variant.languageCode,variant.imageUrl)
                        }.getOrNull()
                        if(saved!=null) runCatching { ScanReviewStore.acknowledge(this,entry,saved) }
                        runOnUiThread {
                            if(!isDestroyed && !isFinishing) {
                                applying=false; selected.remove(entry.key); reload(); updateApplyButton()
                                if(saved==null) Toast.makeText(this,getString(R.string.review_apply_result,0,1),Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }.show()
        }
    }

    private fun reanalyse(entry: ScanReviewStore.Entry) {
        AlertDialog.Builder(this).setMessage(R.string.review_reanalyse_confirm)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.review_reanalyse) { _,_->
                applying=true; render()
                io.execute {
                    val ok=runCatching { EdScanJobs.reanalyse(applicationContext,entry.photoKey) }.isSuccess
                    runOnUiThread {
                        if(!isDestroyed) {
                            applying=false; reload(); updateApplyButton()
                            if(!ok) Toast.makeText(this,R.string.review_operation_failed,Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
                            if(!ok) Toast.makeText(this,R.string.review_operation_failed,Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.show()
    }

    private fun open(entry: ScanReviewStore.Entry) {
        val choices=mutableListOf(getString(R.string.review_photo),getString(R.string.view_card_details),getString(R.string.review_keep))
        if(entry.language!=null) choices+=getString(R.string.review_apply_language,entry.language.uppercase())
        AlertDialog.Builder(this).setTitle(entry.card.name).setItems(choices.toTypedArray()) { _,index ->
            when(index) {
                0->startActivity(Intent(this,CardImageActivity::class.java).putExtra(CardImageActivity.EXTRA_IMAGE_URL,
                    android.net.Uri.fromFile(File(EdScanJobs.directory(this),"${entry.photoKey}.jpg")).toString()))
                1->startActivity(Intent(this,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,entry.card.name)
                    .putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID,entry.card.collectionItemId))
                2->AlertDialog.Builder(this).setMessage(R.string.review_keep_confirm).setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(android.R.string.ok) { _,_-> acknowledge(entry) }.show()
                3->applyLanguage(entry)
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
                    if(!ok) Toast.makeText(this,R.string.review_operation_failed,Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun applyLanguage(entry: ScanReviewStore.Entry, done: (Boolean)->Unit = { reload() }) {
        val language=entry.language ?: run { done(false); return }
        CardRepository.get(this).loadImageLanguages(entry.card.setCode.orEmpty(),entry.card.collectorNumber.orEmpty()) { variants,_ ->
            if(isDestroyed || isFinishing) return@loadImageLanguages
            val variant=variants.firstOrNull { it.languageCode==language }
            if(variant==null) { Toast.makeText(this,R.string.review_language_unavailable,Toast.LENGTH_LONG).show(); done(false); return@loadImageLanguages }
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
