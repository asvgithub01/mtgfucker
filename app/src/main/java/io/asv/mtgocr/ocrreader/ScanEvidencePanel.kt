package io.asv.mtgocr.ocrreader

import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.GlideDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.json.JSONObject
import java.io.File

internal object ScanEvidencePanel {
    fun bind(root: View, metadata: List<String>, photos: List<String>) {
        val container = root.findViewById<View>(R.id.scanEvidenceCard)
        val context = root.context
        val records = (0 until maxOf(metadata.size, photos.size)).filter {
            !metadata.getOrNull(it).isNullOrBlank() || !photos.getOrNull(it).isNullOrBlank()
        }.reversed()
        container.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        if (records.isEmpty()) return
        val header = root.findViewById<TextView>(R.id.scanEvidenceHeader)
        val content = root.findViewById<View>(R.id.scanEvidenceContent)
        val photo = root.findViewById<ImageView>(R.id.scanEvidencePhoto)
        val unavailable = root.findViewById<TextView>(R.id.scanEvidencePhotoUnavailable)
        val history = root.findViewById<Spinner>(R.id.scanEvidenceHistory)
        val details = root.findViewById<TextView>(R.id.scanEvidenceMetadata)
        val rawToggle = root.findViewById<TextView>(R.id.scanEvidenceRawToggle)
        val rawView = root.findViewById<TextView>(R.id.scanEvidenceRaw)
        var expanded = false
        var loadedIndex: Int? = null
        var generation = 0
        fun showRecord() {
            val index = records[history.selectedItemPosition.coerceAtLeast(0)]
            if (loadedIndex == index) return
            loadedIndex = index
            val currentGeneration = ++generation
            val raw = metadata.getOrNull(index).orEmpty()
            details.text = ScanEvidenceFormatter.format(context, raw)
            rawView.text = runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)
            rawView.visibility = View.GONE
            rawToggle.setText(R.string.scan_debug_evidence_show_json)
            rawToggle.visibility = if (raw.isBlank()) View.GONE else View.VISIBLE
            Glide.clear(photo)
            photo.setImageDrawable(null)
            // Use the SAME index. Never attach a previous scan's photo to later metadata.
            val path = photos.getOrNull(index).orEmpty()
            val file = photoFile(context.filesDir, path)
            photo.visibility = if (file == null) View.GONE else View.VISIBLE
            unavailable.visibility = if (file == null) View.VISIBLE else View.GONE
            if (file != null) Glide.with(context).load(file).fitCenter().dontAnimate()
                .listener(object : RequestListener<File, GlideDrawable> {
                    override fun onException(e: Exception?, model: File?, target: Target<GlideDrawable>?, isFirstResource: Boolean): Boolean {
                        if (generation == currentGeneration) {
                            photo.visibility = View.GONE
                            unavailable.visibility = View.VISIBLE
                        }
                        return false
                    }
                    override fun onResourceReady(resource: GlideDrawable?, model: File?, target: Target<GlideDrawable>?, isFromMemoryCache: Boolean, isFirstResource: Boolean) = false
                }).into(photo)
        }
        history.adapter = ArrayAdapter(context, R.layout.spinner_item, records.map { index ->
            val json = runCatching { JSONObject(metadata.getOrNull(index).orEmpty()) }.getOrNull()
            context.getString(R.string.scan_debug_evidence_record, index + 1, ScanEvidenceFormatter.date(json))
        }).also { it.setDropDownViewResource(R.layout.spinner_item) }
        history.visibility = if (records.size > 1) View.VISIBLE else View.GONE
        history.onItemSelectedListener = SimpleItemSelectedListener { if (expanded) showRecord() }
        history.setSelection(0)
        rawToggle.setOnClickListener {
            val show = rawView.visibility != View.VISIBLE
            rawView.visibility = if (show) View.VISIBLE else View.GONE
            rawToggle.setText(if (show) R.string.scan_debug_evidence_hide_json else R.string.scan_debug_evidence_show_json)
        }
        fun render() {
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            header.text = context.getString(if (expanded) R.string.hash_scan_saved_evidence_expanded
                else R.string.hash_scan_saved_evidence, records.size)
            if (expanded) showRecord()
        }
        header.setOnClickListener { expanded = !expanded; render() }
        render()
    }

    internal fun photoFile(filesDir: File, relativePath: String): File? = runCatching {
        if (relativePath.isBlank() || File(relativePath).isAbsolute) return null
        val base = File(filesDir, "scan_evidence").canonicalFile
        File(filesDir, relativePath).canonicalFile.takeIf {
            it.path.startsWith(base.path + File.separator) && it.isFile
        }
    }.getOrNull()
}
