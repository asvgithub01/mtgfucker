package io.asv.mtgocr.ocrreader

import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.*
import androidx.appcompat.app.AppCompatActivity
import io.asv.mtgocr.ocrreader.data.CardRepository

/** Shared multi-set picker. Text remains editable; selected symbols are not the source of truth. */
class ScannerSetInput @JvmOverloads constructor(private val activity: AppCompatActivity, val input: MultiAutoCompleteTextView,
    private val preferenceName: String, private val changed: Runnable, private val editing: Runnable = Runnable {}) {
    private val symbols=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
    init {
        val parent=input.parent as LinearLayout
        parent.addView(symbols, parent.indexOfChild(input))
        input.threshold=1; input.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        input.setText(activity.getSharedPreferences(preferenceName,0).getString("locked_set",""))
        CardRepository.get(activity).loadSetCatalog(false) { sets,_ ->
            if(!activity.isDestroyed) input.setAdapter(EditionSearchAdapter(activity,sets.map { EditionSearchAdapter.Entry(it.code,it.name) }))
        }
        input.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { render(); editing.run() }
            override fun afterTextChanged(s:Editable?) {}
        })
        input.setOnItemClickListener { _,_,_,_ -> commit() }
        input.setOnFocusChangeListener { _,focused -> if(!focused) commit() }
        input.setOnEditorActionListener { _,_,_ -> commit(); false }
        render()
    }
    private fun commit() {
        activity.getSharedPreferences(preferenceName,0).edit().putString("locked_set",input.text.toString().trim()).apply()
        changed.run()
    }
    private fun render() {
        symbols.removeAllViews()
        codes().take(3).forEach { code ->
            val icon=ImageView(activity).apply { contentDescription=code; scaleType=ImageView.ScaleType.FIT_CENTER }
            val size=(28*activity.resources.displayMetrics.density).toInt()
            symbols.addView(icon,LinearLayout.LayoutParams(size,size))
            SetSymbolLoader.display(activity,code,icon,android.R.drawable.ic_menu_edit)
        }
        symbols.visibility=if(codes().isEmpty()) View.GONE else View.VISIBLE
    }
    fun codes(): Set<String> = parse(input.text.toString())
    companion object {
        @JvmStatic fun parse(value: String): Set<String> = value.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
    }
}
