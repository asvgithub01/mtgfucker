package io.asv.collectorvision

import android.content.Context
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

internal data class EdScanTuning(val score: Float = .55f, val margin: Float = .04f, val readings: Int = 2) {
    fun apply(gate: CameraVisionAcceptanceGate) = gate.configureFast(score, margin, readings)
    fun description(context: Context): String = context.getString(R.string.edscan_tuning_values, score, margin, readings)
    companion object {
        fun read(context: Context): EdScanTuning {
            val prefs = context.getSharedPreferences("cornelius_scanner", 0)
            return EdScanTuning(prefs.getInt("fast_score_percent", 55).coerceIn(50,95) / 100f,
                prefs.getInt("fast_margin_percent", 4).coerceIn(0,20) / 100f,
                if (prefs.getBoolean("fast_two_readings", true)) 2 else 1)
        }
        fun show(activity: AppCompatActivity, onApply: (EdScanTuning) -> Unit) {
            val current = read(activity)
            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt(); setPadding(pad,pad,pad,pad)
            }
            val scoreLabel = TextView(activity)
            val marginLabel = TextView(activity)
            val score = SeekBar(activity).apply { max = 45; progress = kotlin.math.round(current.score*100).toInt()-50; contentDescription=activity.getString(R.string.edscan_tuning_score_label) }
            val margin = SeekBar(activity).apply { max = 20; progress = kotlin.math.round(current.margin*100).toInt(); contentDescription=activity.getString(R.string.edscan_tuning_margin_label) }
            fun labels() {
                scoreLabel.text = activity.getString(R.string.edscan_tuning_score, (score.progress+50)/100f)
                marginLabel.text = activity.getString(R.string.edscan_tuning_margin, margin.progress/100f)
            }
            val listener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) { labels() }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            }
            score.setOnSeekBarChangeListener(listener); margin.setOnSeekBarChangeListener(listener); labels()
            val twice = CheckBox(activity).apply { setText(R.string.edscan_tuning_twice); isChecked=current.readings==2 }
            panel.addView(scoreLabel); panel.addView(score); panel.addView(marginLabel); panel.addView(margin); panel.addView(twice)
            panel.addView(TextView(activity).apply { setText(R.string.edscan_tuning_hint) })
            panel.addView(Button(activity).apply {
                setText(R.string.edscan_tuning_reset)
                setOnClickListener { score.progress=5; margin.progress=4; twice.isChecked=true }
            })
            AlertDialog.Builder(activity).setTitle(R.string.edscan_tuning_title).setView(panel)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    activity.getSharedPreferences("cornelius_scanner",0).edit()
                        .putInt("fast_score_percent",score.progress+50).putInt("fast_margin_percent",margin.progress)
                        .putBoolean("fast_two_readings",twice.isChecked).apply()
                    onApply(read(activity))
                }.show()
        }
    }
}
