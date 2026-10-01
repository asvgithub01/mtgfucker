package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.*
import android.text.*
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.CardNameSuggestion

/** Same name suggestions, speech and explicit add action in OCR and EdScan. */
class ScannerNameInput @JvmOverloads constructor(private val activity: AppCompatActivity, private val input: EditText,
    private val list: ListView, mic: View, add: View, private val submit: java.util.function.Consumer<String>, private val shouldSuggest: java.util.function.BooleanSupplier = java.util.function.BooleanSupplier { true }): DefaultLifecycleObserver {
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    private var suppress=false
    private var values=emptyList<CardNameSuggestion>()
    private val adapter=ArrayAdapter<String>(activity,R.layout.name_suggestion_item)
    private val speech=activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            input.setText(it); input.setSelection(input.length())
        }
    }
    init {
        activity.lifecycle.addObserver(this)
        list.adapter=adapter
        list.setOnItemClickListener { _,_,position,_ -> values.getOrNull(position)?.let {
            suppress=true; input.setText(it.displayName); input.setSelection(input.length()); suppress=false
            hide(); input.clearFocus()
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken,0)
        } }
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { if(!suppress && shouldSuggest.asBoolean) suggest(s.toString()) }
        })
        add.setOnClickListener { submit.accept(input.text.toString().trim()) }
        input.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE) { submit.accept(input.text.toString().trim()); true } else false }
        mic.setOnClickListener {
            try { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,java.util.Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT,activity.getString(R.string.speak_card_name_prompt))) }
            catch(_:android.content.ActivityNotFoundException) { Toast.makeText(activity,R.string.speech_recognition_unavailable,Toast.LENGTH_LONG).show() }
        }
    }
    private fun suggest(value:String) {
        hide(); val query=value.trim(); if(query.length<2 || !input.isShown) return
        val token=generation
        handler.postDelayed({ CardRepository.get(activity).suggestCardNames(query) { found ->
            if(token==generation && !activity.isDestroyed && input.isShown && input.text.toString().trim()==query) {
                values=found; adapter.clear(); adapter.addAll(found.map { activity.getString(R.string.suggestion_with_language,it.displayName,it.language) })
                list.visibility=if(found.isEmpty()) View.GONE else View.VISIBLE
            }
        } },180)
    }
    fun hide() { generation++; handler.removeCallbacksAndMessages(null); values=emptyList(); adapter.clear(); list.visibility=View.GONE }
    fun clear() { input.text.clear(); hide() }
    override fun onPause(owner:LifecycleOwner) { hide() }
    override fun onDestroy(owner:LifecycleOwner) { hide(); speech.unregister() }
}
