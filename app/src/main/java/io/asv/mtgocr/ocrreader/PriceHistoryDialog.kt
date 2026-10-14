package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.app.AlertDialog
import io.asv.mtgocr.ocrreader.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object PriceHistoryDialog {
    private val worker = Executors.newSingleThreadExecutor()
    @JvmStatic fun showCard(activity: Activity, uuid: String?, finish: String?) {
        if(activity.isFinishing || activity.isDestroyed)return
        val section=PriceHistorySection(activity)
        val scroll=android.widget.ScrollView(activity).apply { addView(section) }
        AlertDialog.Builder(activity).setView(scroll).setPositiveButton(R.string.close,null).show()
        section.bind(uuid.orEmpty(),finish.orEmpty())
    }
    @JvmStatic fun showGroup(activity: Activity, library: String, scope: String) {
        val provider = PriceSourcePreferences.selectedId(activity)
        show(activity) {
            val rows = CardDatabase.get(activity.applicationContext).cardDao().groupPriceHistoryAll(library,scope)
            val label = PriceSourcePreferences.available.first { it.id == provider }.label(activity)
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault())
            listOf(activity.getString(R.string.price_history_group_note)) + rows.mapIndexed { index, row ->
                val previous = rows.getOrNull(index+1)
                val delta = if (previous != null && PriceHistoryPolicy.comparable(previous,row))
                    change(activity,previous.total,row.total,row.currency)
                    else if (previous != null) "\n"+activity.getString(R.string.price_history_composition_changed) else ""
                "${date.format(Date(row.observedAt))} · ${money(row.total,row.currency)}\n" + row.provider.split("+").joinToString(" + ") { PriceSourcePresentation.label(activity,it) } + delta +
                    if (row.missing > 0) "\n"+activity.getString(R.string.price_details_missing,row.missing) else ""
            }.ifEmpty { listOf(activity.getString(R.string.price_history_empty)) }
        }
    }
    private fun money(amount: Double, currency: String) = String.format(Locale.getDefault(),"%.2f %s",amount,currency)
    private fun change(activity: Activity, previous: Double?, current: Double, currency: String): String {
        val delta = PriceHistoryPolicy.delta(previous,current) ?: return ""
        val arrow = if (delta.first > 0.00001) "↑" else if (delta.first < -0.00001) "↓" else "="
        val percent = delta.second?.let { String.format(Locale.getDefault()," (%+.2f%%)",it) }.orEmpty()
        return "\n$arrow ${String.format(Locale.getDefault(),"%+.2f %s",delta.first,currency)}$percent"
    }
    private fun show(activity: Activity, read: () -> List<String>) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.price_history_title)
            .setMessage(R.string.price_details_loading).setPositiveButton(R.string.close,null).show()
        worker.execute {
            val result = runCatching {
                val text=read().joinToString("\n\n")
                android.text.SpannableString(text).apply {
                    Regex("(?m)^[↑↓].*$").findAll(text).forEach { match ->
                        setSpan(android.text.style.ForegroundColorSpan(if(match.value.startsWith("↑")) android.graphics.Color.rgb(98,220,146) else android.graphics.Color.rgb(255,119,129)),match.range.first,match.range.last+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing)
                    dialog.setMessage(result.getOrElse { activity.getString(R.string.price_history_error) })
            }
        }
    }
}
