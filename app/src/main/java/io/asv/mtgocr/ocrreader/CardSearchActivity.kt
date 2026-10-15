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
        query.doAfterTextChanged { text ->
            val expected=++generation;handler.removeCallbacksAndMessages(null)
            if(text.toString().trim().length>=2)handler.postDelayed({
                CardRepository.get(this).suggestCardNames(text.toString().trim()) { rows ->
                    if(expected!=generation || isFinishing || isDestroyed)return@suggestCardNames
                    suggestions=rows;query.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,rows.map { it.displayName }));query.showDropDown()
                }
            },300)
        }
        fun open(name:String) { if(name.isNotBlank()) { generation++;handler.removeCallbacksAndMessages(null);query.dismissDropDown()
            startActivity(Intent(this,Main2Activity::class.java).putExtra(Main2Activity.EXTRA_CARD_NAME,name.trim()).putExtra("browseOnly",true)) } }
        query.setOnItemClickListener { parent,_,position,_ ->
            val value=parent.getItemAtPosition(position).toString()
            open(suggestions.firstOrNull { it.displayName==value }?.canonicalName ?: value)
        }
        inputRow.addView(CollectionPopupChrome.icon(this,android.R.drawable.ic_menu_search,R.string.buy_search) { open(query.text.toString()) }.apply { id=R.id.btnCardSearch })
        query.setOnEditorActionListener { _,_,_->open(query.text.toString());true }
    }
    override fun onDestroy() { generation++;handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
