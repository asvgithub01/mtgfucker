package io.asv.collectorvision

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle

object PriceCelebration {
    private const val PREFS = "valuable_scan_celebrations"
    internal fun thresholdForProgress(progress: Int): Double = (progress.coerceIn(0, 39) + 1) * 0.5
    internal fun progressForThreshold(threshold: Double): Int =
        if (threshold.isFinite()) kotlin.math.round(threshold * 2 - 1).toInt().coerceIn(0, 39) else 19
    @JvmStatic fun threshold(context: android.content.Context): Double =
        thresholdForProgress(context.getSharedPreferences(PREFS, 0).getInt("threshold_step", 19))
    @JvmStatic @JvmOverloads fun eligible(euros: Double, threshold: Double = 10.0) =
        euros.isFinite() && threshold.isFinite() && threshold in 0.5..20.0 && euros > threshold
    @JvmStatic fun showSettings(activity: AppCompatActivity) {
        val panel = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val prefs = activity.getSharedPreferences(PREFS, 0)
        fun option(key: String, label: Int) = CheckBox(activity).apply {
            setText(label)
            isChecked = prefs.getBoolean(key, false)
        }
        val balloons = option("balloons", R.string.party_balloons)
        val trumpets = option("trumpets", R.string.party_trumpets)
        val disco = option("disco", R.string.party_disco)
        val label = android.widget.TextView(activity)
        val slider = android.widget.SeekBar(activity).apply { max = 39; progress = progressForThreshold(threshold(activity)) }
        fun updateLabel() { label.text = activity.getString(R.string.party_threshold_value, thresholdForProgress(slider.progress)) }
        updateLabel()
        slider.contentDescription = activity.getString(R.string.party_settings)
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, fromUser: Boolean) { updateLabel() }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
        })
        panel.addView(label); panel.addView(slider)
        panel.addView(android.widget.TextView(activity).apply { setText(R.string.party_threshold_hint) })
        panel.addView(android.widget.TextView(activity).apply { setText(R.string.party_effects_hint) })
        panel.addView(balloons); panel.addView(trumpets); panel.addView(disco)
        androidx.appcompat.app.AlertDialog.Builder(activity).setTitle(R.string.party_settings).setView(ScrollView(activity).apply { addView(panel) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.getSharedPreferences(PREFS, 0).edit().putInt("threshold_step", slider.progress)
                    .putBoolean("balloons", balloons.isChecked)
                    .putBoolean("trumpets", trumpets.isChecked)
                    .putBoolean("disco", disco.isChecked).apply()
            }.show()
    }
    @JvmStatic fun show(activity: AppCompatActivity, scanKey: String, euros: Double): Boolean {
        if (!eligible(euros, threshold(activity)) || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        val prefs = activity.getSharedPreferences("valuable_scan_celebrations", 0)
        val history = prefs.getString("recent", "").orEmpty().split('|').filter { it.isNotBlank() }
        if (scanKey in history) return false
        prefs.edit().putString("recent", (history + scanKey).takeLast(500).joinToString("|")).apply()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findViewWithTag<View>("price_confetti")?.let { root.removeView(it) }
        val overlay = ScanPartyView(activity, ScanPartyOptions(
            balloons = prefs.getBoolean("balloons", false),
            trumpets = prefs.getBoolean("trumpets", false),
            disco = prefs.getBoolean("disco", false)))
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        return true
    }
}
