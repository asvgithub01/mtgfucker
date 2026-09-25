package io.asv.mtgocr.ocrreader

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton

object HoldToDelete {
    @JvmStatic fun wrap(content: View): View {
        val wrapper = FrameLayout(content.context)
        wrapper.layoutParams = content.layoutParams ?: ViewGroup.LayoutParams(-1, -1)
        wrapper.addView(content, FrameLayout.LayoutParams(-1, -1))
        val size = (36 * content.resources.displayMetrics.density).toInt()
        wrapper.addView(ImageButton(content.context).apply {
            tag = "hold_delete_card"
            setImageResource(android.R.drawable.ic_menu_delete)
            contentDescription = context.getString(R.string.hold_delete_card)
            setBackgroundColor(0xDDFFFFFF.toInt())
        }, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START))
        return wrapper
    }
    @JvmStatic fun bind(root: View, action: Runnable) {
        val button = root.findViewWithTag<ImageButton>("hold_delete_card") ?: return
        val oldCancel = button.getTag(R.id.btnOrganizeCard) as? Runnable
        oldCancel?.run()
        var pending: Runnable? = null
        var x = 0f; var y = 0f
        val slop = ViewConfiguration.get(root.context).scaledTouchSlop
        fun cancel() { pending?.let { button.removeCallbacks(it) }; pending = null; button.isPressed = false }
        button.setOnClickListener { action.run() } // Accessibility activation still asks for confirmation.
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancel(); x = event.x; y = event.y; button.isPressed = true
                    pending = Runnable { pending = null; button.isPressed = false; action.run() }
                    button.postDelayed(pending!!, 1000)
                }
                MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x-x) > slop || kotlin.math.abs(event.y-y) > slop) cancel()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
            }
            true
        }
        button.setTag(R.id.btnOrganizeCard, Runnable { cancel() })
        if (oldCancel == null) button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { (button.getTag(R.id.btnOrganizeCard) as? Runnable)?.run() }
        })
    }
}
