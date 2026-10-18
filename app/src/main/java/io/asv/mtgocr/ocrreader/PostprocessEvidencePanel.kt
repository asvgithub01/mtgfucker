package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Premium diagnostics only. No reanalysis, selection, persistence or collection mutation. */
internal object PostprocessEvidencePanel {
    private val worker=Executors.newSingleThreadExecutor()
    fun bind(activity:Activity,library:String,item:String):Future<*>? {
        val section=activity.findViewById<View>(R.id.postprocessSection)
        section.visibility=View.GONE
        if(!PremiumAccess.isEnabled(activity) || item.isBlank()) return null
        section.visibility=View.VISIBLE
        val body=activity.findViewById<View>(R.id.postprocessBody)
        val text=activity.findViewById<TextView>(R.id.postprocessText)
        val picker=activity.findViewById<Spinner>(R.id.postprocessDocuments)
        text.setText(R.string.postprocess_loading)
        activity.findViewById<View>(R.id.postprocessHeader).setOnClickListener {
            body.visibility=if(body.visibility==View.VISIBLE) View.GONE else View.VISIBLE
        }
        return worker.submit {
            val result=runCatching { PostprocessEvidenceStore.read(File(activity.filesDir,"edscan"),library,item) }
            if(Thread.currentThread().isInterrupted) return@submit
            activity.runOnUiThread {
                if(activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if(!PremiumAccess.isEnabled(activity) || LibraryCatalog.activeFile(activity)!=library) {
                    section.visibility=View.GONE;return@runOnUiThread
                }
                val docs=result.getOrDefault(emptyList())
                picker.visibility=if(docs.isEmpty()) View.GONE else View.VISIBLE
                if(docs.isEmpty()) text.setText(if(result.isFailure) R.string.postprocess_error else R.string.postprocess_empty)
                else {
                    picker.adapter=ArrayAdapter(activity,R.layout.spinner_item,docs.map { it.name })
                        .also { it.setDropDownViewResource(R.layout.spinner_item) }
                    picker.onItemSelectedListener=SimpleItemSelectedListener { i -> docs.getOrNull(i)?.let { text.text=it.text } }
                    text.text=docs.first().text
                }
            }
        }
    }
}
