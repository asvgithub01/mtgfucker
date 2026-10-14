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
        MagicPalette.applyTheme(this);super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) };setContentView(root)
        CollectionScreenBackground.install(this)
        root.addView(Button(this).apply { setText(R.string.close);setOnClickListener { finish() } })
        root.addView(TextView(this).apply { setText(R.string.buy_search);textSize=24f })
        val query=AutoCompleteTextView(this).apply { hint=getString(R.string.buy_search);threshold=2;isSingleLine=true };root.addView(query)
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
        root.addView(Button(this).apply { setText(R.string.buy_search);setOnClickListener { open(query.text.toString()) } })
        query.setOnEditorActionListener { _,_,_->open(query.text.toString());true }
    }
    override fun onDestroy() { generation++;handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
