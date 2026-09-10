package io.asv.mtgocr.ocrreader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatImageView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.sin

/**
 * Clips the drawable itself to the physical outline of an MTG card.
 *
 * Some scanned printings are rectangular JPEGs with a white matte behind the card's rounded
 * corners. A rounded background/outline on the ImageView is not enough when the drawable uses
 * fitCenter, because the visible image may occupy only part of the view. Computing the drawable's
 * transformed bounds also keeps the mask aligned in fullscreen while pinching and panning.
 */
open class RoundedCardImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val visibleCardBounds = RectF()
    private val foilEdgeBounds = RectF()
    private val cardClipPath = Path()
    private val edgeInset = resources.displayMetrics.density * .35f
    private val density = resources.displayMetrics.density
    private val foilFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val foilGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val foilEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.15f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val shaderMatrix = Matrix()
    private var foilBand: LinearGradient? = null
    private var foilGlow: RadialGradient? = null
    private var foilEdge: SweepGradient? = null
    private var foilAgslOverlay: FoilAgslOverlay? = null
    private var foilPhase = 0f
    private var foilEffectEnabled = false

    /** Enables the shared animated liquid-glass sheen used only by physical foil finishes. */
    fun setFoilEffect(enabled: Boolean) {
        if (foilEffectEnabled == enabled) return
        foilEffectEnabled = enabled
        if (enabled) {
            ensureFoilAgslOverlay()
        } else {
            foilAgslOverlay = null
        }
        updateFoilClockRegistration()
        invalidate()
    }

    internal fun updateFoilPhase(value: Float) {
        foilPhase = value
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFoilClockRegistration()
    }

    override fun onDetachedFromWindow() {
        FoilLiquidGlassClock.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateFoilClockRegistration()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        ensureFoilAgslOverlay()
        foilBand = LinearGradient(
            -width * .48f,
            0f,
            width * .48f,
            height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(40, 92, 238, 255),
                Color.argb(62, 255, 121, 226),
                Color.argb(54, 255, 227, 111),
                Color.argb(42, 119, 255, 194),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .22f, .39f, .57f, .76f, 1f),
            Shader.TileMode.CLAMP
        )
        foilGlow = RadialGradient(
            0f,
            0f,
            maxOf(width, height) * .56f,
            intArrayOf(Color.argb(66, 255, 255, 255), Color.argb(24, 166, 224, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .34f, 1f),
            Shader.TileMode.CLAMP
        )
        foilEdge = SweepGradient(
            width / 2f,
            height / 2f,
            intArrayOf(
                Color.argb(170, 255, 255, 255),
                Color.argb(145, 110, 232, 255),
                Color.argb(130, 255, 129, 225),
                Color.argb(155, 255, 224, 104),
                Color.argb(170, 255, 255, 255)
            ),
            null
        )
    }

    private fun updateFoilClockRegistration() {
        if (foilEffectEnabled && isAttachedToWindow && visibility == View.VISIBLE) {
            FoilLiquidGlassClock.register(this)
        } else {
            FoilLiquidGlassClock.unregister(this)
        }
    }

    private fun ensureFoilAgslOverlay() {
        if (!foilEffectEnabled || foilAgslOverlay != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            foilAgslOverlay = FoilAgslOverlay.createOrNull()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val currentDrawable = drawable
        if (currentDrawable == null || currentDrawable.bounds.isEmpty) {
            super.onDraw(canvas)
            return
        }

        visibleCardBounds.set(currentDrawable.bounds)
        imageMatrix.mapRect(visibleCardBounds)
        // ImageView applies its drawable matrix after translating into the padded content area.
        visibleCardBounds.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        visibleCardBounds.inset(edgeInset, edgeInset)
        if (visibleCardBounds.width() <= 0f || visibleCardBounds.height() <= 0f) {
            super.onDraw(canvas)
            return
        }

        // The slightly generous mask also removes the pale matte included in some card JPEGs.
        // List layouts now fit the whole drawable, so this rounding no longer hides the top edge.
        val radius = min(visibleCardBounds.width() * .07f, visibleCardBounds.height() * .05f)
        cardClipPath.rewind()
        cardClipPath.addRoundRect(visibleCardBounds, radius, radius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(cardClipPath)
        super.onDraw(canvas)
        if (foilEffectEnabled) drawFoilLiquidGlass(canvas, radius)
        canvas.restoreToCount(checkpoint)
    }

    private fun drawFoilLiquidGlass(canvas: Canvas, radius: Float) {
        val band = foilBand ?: return
        val glow = foilGlow ?: return
        val edge = foilEdge ?: return
        val width = visibleCardBounds.width()
        val height = visibleCardBounds.height()

        val agsl = foilAgslOverlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agsl != null) {
            agsl.draw(canvas, visibleCardBounds, radius, foilPhase)
        } else {
            // Lightweight fallback for Android 12 and earlier.
            shaderMatrix.reset()
            shaderMatrix.setTranslate(
                visibleCardBounds.left + width * (-.58f + foilPhase * 1.72f),
                visibleCardBounds.top
            )
            band.setLocalMatrix(shaderMatrix)
            foilFillPaint.shader = band
            canvas.drawRoundRect(visibleCardBounds, radius, radius, foilFillPaint)

            shaderMatrix.reset()
            shaderMatrix.setTranslate(
                visibleCardBounds.left + width * (.12f + .76f * foilPhase),
                visibleCardBounds.top + height * (.28f + .10f * sin(foilPhase * Math.PI * 2).toFloat())
            )
            glow.setLocalMatrix(shaderMatrix)
            foilGlowPaint.shader = glow
            canvas.drawRoundRect(visibleCardBounds, radius, radius, foilGlowPaint)
        }

        shaderMatrix.reset()
        shaderMatrix.setRotate(foilPhase * 360f, width / 2f, height / 2f)
        edge.setLocalMatrix(shaderMatrix)
        foilEdgePaint.shader = edge
        foilEdgeBounds.set(visibleCardBounds)
        foilEdgeBounds.inset(density * .8f, density * .8f)
        canvas.drawRoundRect(foilEdgeBounds, radius, radius, foilEdgePaint)
    }
}

/**
 * Procedural holographic foil for Android 13+. It is drawn directly through Canvas rather than
 * using RenderEffect, so the card bitmap is not copied or reprocessed for every animation frame.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class FoilAgslOverlay private constructor() {
    private val shader = RuntimeShader(SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = this@FoilAgslOverlay.shader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    fun draw(canvas: Canvas, bounds: RectF, radius: Float, phase: Float) {
        shader.setFloatUniform("uOrigin", bounds.left, bounds.top)
        shader.setFloatUniform("uSize", bounds.width(), bounds.height())
        shader.setFloatUniform("uPhase", phase)
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    companion object {
        fun createOrNull(): FoilAgslOverlay? = try {
            FoilAgslOverlay()
        } catch (_: IllegalArgumentException) {
            null
        }

        private const val SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uPhase;

            half3 spectrum(float value) {
                float3 offsets = float3(0.00, 0.33, 0.67);
                return half3(0.56 + 0.44 * cos(6.28318 * (value + offsets)));
            }

            half4 main(float2 fragCoord) {
                float2 uv = clamp((fragCoord - uOrigin) / uSize, 0.0, 1.0);
                float2 p = uv - 0.5;
                p.x *= uSize.x / max(uSize.y, 1.0);

                // Intersecting waves emulate microscopic foil grooves rather than a flat gradient.
                float flowA = sin((p.x * 5.2 + p.y * 3.1
                    + sin(p.y * 8.0 - uPhase * 6.28318) * 0.34) * 6.28318);
                float flowB = sin((length(p + float2(-0.18, 0.12)) * 8.5
                    - uPhase * 1.7 + flowA * 0.08) * 6.28318);
                float interference = 0.5 + 0.5 * (flowA * 0.58 + flowB * 0.42);

                float diagonal = uv.x * 0.82 + uv.y * 0.36;
                float sweepCenter = -0.22 + uPhase * 1.55;
                float sweep = pow(max(0.0, 1.0 - abs(diagonal - sweepCenter) / 0.24), 2.4);
                float caustic = pow(0.5 + 0.5 * sin(
                    (uv.x * 2.6 - uv.y * 3.7 + interference * 0.27 + uPhase) * 6.28318
                ), 5.0);

                float hue = fract(
                    uv.x * 0.48 - uv.y * 0.26 + interference * 0.16 + uPhase * 0.18
                );
                half3 rainbow = spectrum(hue);
                float alpha = 0.022
                    + interference * 0.025
                    + sweep * 0.145
                    + caustic * 0.052;
                half3 color = mix(half3(1.0), rainbow, 0.76) * half(alpha);
                return half4(color, half(alpha));
            }
        """
    }
}

/** One clock drives every visible foil card, avoiding a ValueAnimator per RecyclerView row. */
private object FoilLiquidGlassClock {
    private val views = Collections.newSetFromMap(
        WeakHashMap<RoundedCardImageView, Boolean>()
    )
    private var animator: ValueAnimator? = null

    fun register(view: RoundedCardImageView) {
        views.add(view)
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3_800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { frame ->
                val phase = frame.animatedValue as Float
                views.forEach { it.updateFoilPhase(phase) }
                if (views.isEmpty()) stop()
            }
            start()
        }
    }

    fun unregister(view: RoundedCardImageView) {
        views.remove(view)
        if (views.isEmpty()) stop()
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }
}
