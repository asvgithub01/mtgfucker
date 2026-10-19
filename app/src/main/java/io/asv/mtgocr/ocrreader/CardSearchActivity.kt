package io.asv.mtgocr.ocrreader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.CardNameSuggestion

/** Catalogue search never creates an owned printing or alters the collection. */
class CardSearchActivity:AppCompatActivity() {
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    companion object { const val EXTRA_PHOTO_ID="addToPhotoId" }
    private var selectionGeneration=0
    private var saving=false
    private var suggestions=emptyList<CardNameSuggestion>()
    override fun onCreate(savedInstanceState:Bundle?) {
        CollectionPopupChrome.prepare(this);super.onCreate(savedInstanceState)
        val root=CollectionPopupChrome.install(this,R.string.buy_search,compact=true)
        val searchPanel=CollectionPopupChrome.panel(this)
        searchPanel.addView(CollectionPopupChrome.label(this,getString(R.string.buy_search)))
        val inputRow=LinearLayout(this).apply { gravity=android.view.Gravity.CENTER_VERTICAL }
        val query=AutoCompleteTextView(this).apply {
            id=R.id.txtCardSearch;hint=getString(R.string.search_collection_hint);threshold=2;isSingleLine=true
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setTextColor(CollectionPopupChrome.color(this@CardSearchActivity,R.attr.appGlassTextColor))
            setHintTextColor(android.graphics.Color.LTGRAY)
        }
        inputRow.addView(query,LinearLayout.LayoutParams(0,CollectionPopupChrome.dp(this,48),1f))
        searchPanel.addView(inputRow);root.addView(searchPanel)
        val photoId=intent.getStringExtra(EXTRA_PHOTO_ID)
        val status=CollectionPopupChrome.label(this,"")
        var preview:PhotoCardPreview?=null
        if(photoId!=null) {
            preview=PhotoCardPreview(this) { option ->
                if(!saving) {
                    saving=true
                    StorageOperations.worker.execute {
                        val saved=runCatching { PhotoScanStore.addManual(this,photoId,option.toPhotoCard()) }.getOrDefault(false)
                        runOnUiThread {
                            saving=false
                            if(saved) { setResult(RESULT_OK);finish() }
                            else Toast.makeText(this,R.string.photo_draft_unavailable,Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            root.addView(status)
            root.addView(ScrollView(this).apply { addView(preview) })
        }
        query.doAfterTextChanged { text ->
            selectionGeneration++;preview?.clear();status.text=""
            val expected=++generation;handler.removeCallbacksAndMessages(null)
            if(text.toString().trim().length>=2)handler.postDelayed({
                CardRepository.get(this).suggestCardNames(text.toString().trim()) { rows ->
                    if(expected!=generation || isFinishing || isDestroyed)return@suggestCardNames
                    suggestions=rows;query.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,rows.map { it.displayName }));query.showDropDown()
                }
            },300)
        }
        fun open(name:String) {
            if(name.isBlank() || saving)return
            generation++;handler.removeCallbacksAndMessages(null);query.dismissDropDown()
            if(photoId==null) {
                startActivity(Intent(this,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,name.trim()).putExtra("browseOnly",true))
                return
            }
            val expected=++selectionGeneration
            preview?.clear();status.setText(R.string.loading_card_for_photo)
            query.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(query.windowToken,0)
            var shown=false
            CardRepository.get(this).loadCard(name.trim(),deliverEditionsBeforePrices=true) { options,error ->
                if(expected!=selectionGeneration || isFinishing || isDestroyed || shown)return@loadCard
                val option=io.asv.mtgocr.ocrreader.data.ScanPrintingPolicy.preferred(options) ?: options.firstOrNull()
                if(error!=null || option==null) { status.setText(R.string.card_not_found_for_photo);return@loadCard }
                shown=true;status.text="";preview?.show(option)
            }
        }
        query.setOnItemClickListener { parent,_,position,_ ->
            val value=parent.getItemAtPosition(position).toString()
            open(suggestions.firstOrNull { it.displayName==value }?.canonicalName ?: value)
        }
        inputRow.addView(CollectionPopupChrome.icon(this,android.R.drawable.ic_menu_search,R.string.buy_search) { open(query.text.toString()) }.apply { id=R.id.btnCardSearch })
        query.setOnEditorActionListener { _,_,_->open(query.text.toString());true }
    }
    override fun onDestroy() { generation++;selectionGeneration++;handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
