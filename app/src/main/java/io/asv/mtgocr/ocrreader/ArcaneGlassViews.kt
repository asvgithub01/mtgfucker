package io.asv.mtgocr.ocrreader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Lightweight animated glass used by the legacy View screens.
 *
 * The rich card art remains the content layer; these views only paint the functional layer that
 * floats above it. Keeping the animation here avoids running a shader in every RecyclerView row.
 */
private class ArcaneGlassPainter(
    context: Context,
    private val density: Float,
    private val style: Style,
    private val invalidate: () -> Unit
) {
    enum class Style { CONTROLS, NAVIGATION, SHEET }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val bounds = RectF()
    private val path = Path()
    private var phase = 0f
    private var intensity = 1f
    private var animator: ValueAnimator? = null
    private val baseColor = MagicPalette.resolveColor(context, R.attr.appGlassBaseColor, Color.argb(238, 23, 60, 44))
    private val deepColor = MagicPalette.resolveColor(context, R.attr.appGlassDeepColor, Color.argb(242, 10, 25, 21))
    private val accentColor = MagicPalette.secondaryColor(context)
    private var manaTint = MagicPalette.primaryColor(context)

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(.55f, 1.25f)
        invalidate()
    }

    fun setManaTint(color: Int) {
        manaTint = color
        invalidate()
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (style == Style.SHEET) 6200L else 4800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        val radius = when (style) {
            Style.NAVIGATION -> 30f * density
            Style.CONTROLS -> 22f * density
            Style.SHEET -> 28f * density
        }
        if (style == Style.SHEET) {
            path.addRoundRect(bounds, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        } else {
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(path)

        val baseColors = when (style) {
            Style.CONTROLS -> intArrayOf(scaleAlpha(baseColor, .78f * intensity), scaleAlpha(deepColor, .72f * intensity))
            Style.NAVIGATION -> intArrayOf(scaleAlpha(baseColor, 1.03f), scaleAlpha(deepColor, .98f))
            Style.SHEET -> intArrayOf(scaleAlpha(baseColor, 1.04f), deepColor)
        }
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), baseColors, null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)

        // A slow mana-coloured caustic makes the material feel alive without distracting movement.
        val glowX = width * (.18f + .64f * phase)
        val glowY = if (style == Style.SHEET) height * .06f else height * .34f
        val glowRadius = maxOf(width * .46f, 120f * density)
        paint.shader = RadialGradient(
            glowX,
            glowY,
            glowRadius,
            intArrayOf(withAlpha(manaTint, if (style == Style.SHEET) 74 else 92), withAlpha(manaTint, 22), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)

        // A warm specular ribbon ties the glass to Magic's gold accents.
        val ribbonX = width * (-.2f + 1.25f * phase)
        paint.shader = LinearGradient(
            ribbonX - 80f * density,
            0f,
            ribbonX + 80f * density,
            height.toFloat(),
            intArrayOf(Color.TRANSPARENT, withAlpha(accentColor, 42), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        canvas.restore()

        stroke.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(160, 255, 255, 255), withAlpha(accentColor, 100), Color.argb(105, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, stroke)
        paint.shader = null
        stroke.shader = null
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun scaleAlpha(color: Int, multiplier: Float): Int = withAlpha(
        color,
        (Color.alpha(color) * multiplier).toInt().coerceIn(0, 255)
    )
}

class ArcaneGlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val glass = ArcaneGlassPainter(context, resources.displayMetrics.density, ArcaneGlassPainter.Style.CONTROLS, ::invalidate)

    init {
        setWillNotDraw(false)
        background = null
    }

    fun setGlassIntensity(value: Float) = glass.setIntensity(value)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.start()
    }

    override fun onDetachedFromWindow() {
        glass.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        glass.draw(canvas, width, height)
        super.onDraw(canvas)
    }
}

class ArcaneBottomSheetLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val glass = ArcaneGlassPainter(context, resources.displayMetrics.density, ArcaneGlassPainter.Style.SHEET, ::invalidate)

    init {
        setWillNotDraw(false)
        background = null
    }

    fun setManaTint(color: Int) = glass.setManaTint(color)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.start()
    }

    override fun onDetachedFromWindow() {
        glass.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        glass.draw(canvas, width, height)
        super.onDraw(canvas)
    }
}

class ArcaneBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {
    private val glass = ArcaneGlassPainter(context, resources.displayMetrics.density, ArcaneGlassPainter.Style.NAVIGATION, ::invalidate)

    init {
        setWillNotDraw(false)
        background = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.start()
    }

    override fun onDetachedFromWindow() {
        glass.stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        glass.draw(canvas, width, height)
        super.onDraw(canvas)
    }
}
