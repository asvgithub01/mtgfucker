package io.asv.collectorvision

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.*
import kotlin.random.Random

internal data class ScanPartyOptions(
    val balloons: Boolean = false,
    val trumpets: Boolean = false,
    val disco: Boolean = false
) {
    val durationMillis: Long get() = if (balloons || trumpets || disco) 4000L else 1800L
}

/** One bounded, touch-through overlay; replacing it also releases its sound. */
internal class ScanPartyView(
    private val activity: AppCompatActivity,
    private val options: ScanPartyOptions
) : View(activity), DefaultLifecycleObserver {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val density = resources.displayMetrics.density
    private val points = List(75) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
    private val colors = intArrayOf(0xFFFFD54F.toInt(), 0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt())
    private var startedAt = 0L
    private var audio: MediaPlayer? = null

    init {
        tag = "price_confetti"
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startedAt = SystemClock.uptimeMillis()
        activity.lifecycle.addObserver(this)
        // A bundled original groove, no network or synthesis on the camera/UI thread.
        audio = runCatching { MediaPlayer.create(context, if (options.disco) R.raw.scan_disco else R.raw.edscan_wow) }.getOrNull()
        runCatching { audio?.start() }.onFailure { audio?.release(); audio = null }
    }

    override fun onDraw(canvas: Canvas) {
        val seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f
        val progress = seconds * 1000 / options.durationMillis
        if (progress >= 1f) { (parent as? ViewGroup)?.removeView(this); return }
        val fade = ((1f - progress) * 5).coerceIn(0f, 1f)
        if (options.disco) drawDisco(canvas, seconds, fade)
        if (options.balloons) drawBalloons(canvas, progress, fade)
        if (options.trumpets) drawTrumpets(canvas, seconds, fade)
        // Base confetti is never conditional on the extra effects.
        points.forEachIndexed { i, p ->
            paint.color = colors[i % colors.size]
            paint.alpha = ((1f - progress) * 255).toInt()
            val x = width * p[0] + sin(progress * 10 + p[1] * 6) * 20 * density
            val y = height * (progress * (0.8f + p[2] * 0.4f) - p[1] * 0.3f)
            canvas.save(); canvas.rotate(progress * 360 + p[2] * 180, x, y)
            canvas.drawRect(x, y, x + 8 * density, y + 4 * density, paint)
            canvas.restore()
        }
        postInvalidateOnAnimation()
    }

    private fun drawBalloons(canvas: Canvas, t: Float, fade: Float) {
        repeat(12) { i ->
            val p = points[i]
            val radius = (18 + p[2] * 10) * density
            val x = width * ((i + .5f) / 12) + sin(t * 8 + i) * 12 * density
            val y = height + radius * 3 + p[1] * height * .25f - t * (height * 1.65f + radius * 6)
            paint.color = colors[i % colors.size]; paint.alpha = (235 * fade).toInt()
            canvas.drawOval(x - radius, y - radius * 1.3f, x + radius, y + radius * 1.3f, paint)
            path.reset(); path.moveTo(x, y + radius * 1.25f)
            path.lineTo(x - 4 * density, y + radius * 1.5f); path.lineTo(x + 4 * density, y + radius * 1.5f); path.close()
            canvas.drawPath(path, paint)
            paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (100 * fade).toInt()
            canvas.drawOval(x - radius * .55f, y - radius * .85f, x - radius * .2f, y - radius * .15f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            path.reset(); path.moveTo(x, y + radius * 1.4f)
            path.cubicTo(x - 15 * density, y + radius * 2, x + 15 * density, y + radius * 2.5f, x, y + radius * 3)
            canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
    }

    private fun drawTrumpets(canvas: Canvas, seconds: Float, fade: Float) {
        val unit = min(density, width / 360f)
        val entrance = (seconds / .35f).coerceIn(0f, 1f)
        repeat(2) { side ->
            canvas.save()
            canvas.translate(if (side == 0) 0f else width.toFloat(), height * .62f)
            canvas.scale(if (side == 0) unit else -unit, unit)
            canvas.translate(-125 * (1 - entrance), 0f)
            canvas.rotate(-22f + sin(seconds * 7) * 3)
            paint.color = 0xFFFFCA28.toInt(); paint.alpha = (255 * fade).toInt()
            canvas.drawRoundRect(8f, -7f, 90f, 7f, 5f, 5f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f
            canvas.drawRoundRect(22f, 0f, 72f, 28f, 12f, 12f, paint)
            paint.style = Paint.Style.FILL
            path.reset(); path.moveTo(75f, -7f); path.quadTo(103f, -9f, 118f, -29f)
            path.lineTo(118f, 29f); path.quadTo(103f, 9f, 75f, 7f); path.close()
            canvas.drawPath(path, paint)
            paint.color = 0xFFFFF59D.toInt(); paint.alpha = (255 * fade).toInt()
            canvas.drawOval(111f, -30f, 125f, 30f, paint)
            paint.color = 0xFF9B6500.toInt(); paint.alpha = (255 * fade).toInt()
            canvas.drawOval(115f, -24f, 123f, 24f, paint)
            repeat(3) { valve ->
                canvas.drawRect(35f + valve * 12, -20f, 40f + valve * 12, -4f, paint)
                canvas.drawRect(32f + valve * 12, -23f, 43f + valve * 12, -19f, paint)
            }
            // Dedicated particles originate at each bell, rather than reusing the rain.
            repeat(30) { i ->
                val p = points[i + 12]
                val age = (seconds * .8f + p[0]) % 1f
                val x = 124 + age * (90 + p[1] * 100)
                val y = age * (p[2] - .5f) * 170 + age * age * 70
                paint.color = colors[i % colors.size]; paint.alpha = ((1 - age) * 255 * fade).toInt()
                canvas.save(); canvas.rotate(age * 540, x, y)
                canvas.drawRect(x, y, x + 6, y + 3, paint); canvas.restore()
            }
            canvas.restore()
        }
    }

    private fun drawDisco(canvas: Canvas, seconds: Float, fade: Float) {
        val cx = width / 2f
        val radius = min(38 * density, width * .12f)
        val cy = radius + 28 * density
        // Smooth moving beams, no strobe or full-screen flashing.
        repeat(6) { i ->
            val angle = seconds * .55f + i * PI.toFloat() / 3
            val reach = max(width, height) * 1.3f
            paint.color = colors[i % colors.size]; paint.alpha = (24 * fade).toInt()
            path.reset(); path.moveTo(cx, cy)
            path.lineTo(cx + cos(angle - .10f) * reach, cy + sin(angle - .10f) * reach)
            path.lineTo(cx + cos(angle + .10f) * reach, cy + sin(angle + .10f) * reach); path.close()
            canvas.drawPath(path, paint)
        }
        paint.color = 0xFFE0E7EF.toInt(); paint.alpha = (230 * fade).toInt(); paint.strokeWidth = 2 * density
        canvas.drawLine(cx, 0f, cx, cy - radius, paint)
        paint.color = 0xFF3E4658.toInt(); paint.alpha = (255 * fade).toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        // Project a rotating sphere: only front-facing mirror tiles are drawn.
        for (row in -4..4) {
            val latitude = row * PI.toFloat() / 10
            val ring = cos(latitude) * radius
            for (col in 0..19) {
                val longitude = col * PI.toFloat() / 10 + seconds * 1.4f
                val facing = cos(longitude)
                if (facing <= 0) continue
                val x = cx + sin(longitude) * ring
                val y = cy + sin(latitude) * radius
                val brightness = (110 + 145 * facing * (.5f + .5f * cos(longitude - seconds))).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.rgb(brightness, brightness, 255)
                paint.alpha = (255 * fade).toInt()
                val halfW = max(.5f, ring * .12f * facing)
                canvas.drawRect(x - halfW, y - radius * .10f, x + halfW, y + radius * .10f, paint)
            }
        }
        repeat(18) { i ->
            val p = points[i]
            val x = width * (.5f + .48f * sin(seconds * .7f + p[0] * 6.28f))
            val y = height * (.2f + .75f * p[1])
            paint.color = colors[i % colors.size]; paint.alpha = (90 * fade).toInt()
            canvas.drawCircle(x, y, (3 + p[2] * 5) * density, paint)
        }
    }

    override fun onStop(owner: LifecycleOwner) { (parent as? ViewGroup)?.removeView(this) }
    override fun onDetachedFromWindow() {
        audio?.release(); audio = null
        activity.lifecycle.removeObserver(this)
        super.onDetachedFromWindow()
    }
}
