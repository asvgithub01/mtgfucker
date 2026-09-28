package io.asv.collectorvision

import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaPlayer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.random.Random

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
        androidx.appcompat.app.AlertDialog.Builder(activity).setTitle(R.string.party_settings).setView(panel)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.getSharedPreferences(PREFS, 0).edit().putInt("threshold_step", slider.progress).apply()
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
        val overlay = Confetti(activity)
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        return true
    }
    private class Confetti(private val activity: AppCompatActivity) : View(activity), DefaultLifecycleObserver {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val points = List(75) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
        private val colors = intArrayOf(0xFFFFD54F.toInt(), 0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt())
        private var start = 0L
        private var audio: MediaPlayer? = null
        init { tag = "price_confetti"; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            start = android.os.SystemClock.uptimeMillis()
            activity.lifecycle.addObserver(this)
            audio = runCatching { MediaPlayer.create(context, R.raw.edscan_wow)?.apply { start() } }.getOrNull()
        }
        override fun onDraw(canvas: Canvas) {
            val t = (android.os.SystemClock.uptimeMillis() - start) / 1800f
            if (t >= 1f) { (parent as? ViewGroup)?.removeView(this); return }
            points.forEachIndexed { i, p ->
                paint.color = colors[i % colors.size]
                paint.alpha = ((1f-t) * 255).toInt().coerceIn(0,255)
                val x = width * p[0] + kotlin.math.sin(t * 10 + p[1] * 6) * 20
                val y = height * (t * (0.8f+p[2]*0.4f) - p[1]*0.3f)
                canvas.save(); canvas.rotate(t*360+p[2]*180, x, y)
                canvas.drawRect(x, y, x+8*resources.displayMetrics.density, y+4*resources.displayMetrics.density, paint)
                canvas.restore()
            }
            postInvalidateOnAnimation()
        }
        override fun onStop(owner: LifecycleOwner) { (parent as? ViewGroup)?.removeView(this) }
        override fun onDetachedFromWindow() {
            audio?.release(); audio = null
            activity.lifecycle.removeObserver(this)
            super.onDetachedFromWindow()
        }
    }
}
