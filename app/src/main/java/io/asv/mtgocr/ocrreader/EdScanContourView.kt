package io.asv.mtgocr.ocrreader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View

/** Main-thread overlay, at most one redraw/update and one expiry callback per scan. */
internal class EdScanContourView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=3*resources.displayMetrics.density }
    private val path=Path()
    private var points:FloatArray?=null
    private var captured=0L
    private var drawnWidth=0
    private var drawnHeight=0
    private val expire=Runnable { clearContour() }
    init { isClickable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun clearContour() { removeCallbacks(expire);points=null;invalidate() }
    fun showContour(next:FloatArray?,confirmed:Boolean,captureAt:Long) {
        val now=SystemClock.elapsedRealtime()
        if(next==null || !EdScanContourGeometry.valid(next) || !EdScanContourGeometry.fresh(captureAt,now)) { clearContour();return }
        points=next.copyOf();captured=captureAt;drawnWidth=width;drawnHeight=height
        paint.color=if(confirmed) Color.GREEN else Color.YELLOW
        removeCallbacks(expire);postDelayed(expire,EdScanContourGeometry.MAX_AGE_MS-(now-captureAt));invalidate()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val p=points ?: return
        if(width!=drawnWidth || height!=drawnHeight || !EdScanContourGeometry.fresh(captured,SystemClock.elapsedRealtime())) return
        path.reset();path.moveTo(p[0],p[1]);for(i in 1..3) path.lineTo(p[i*2],p[i*2+1]);path.close()
        canvas.drawPath(path,paint)
    }
    override fun onDetachedFromWindow() { clearContour();super.onDetachedFromWindow() }
}
