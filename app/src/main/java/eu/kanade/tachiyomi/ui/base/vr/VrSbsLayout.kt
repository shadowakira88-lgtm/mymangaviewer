package eu.kanade.tachiyomi.ui.base.vr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.FrameLayout

// KMK -->
/**
 * Side-by-side (SBS) layout for phone VR goggles.
 *
 * When [vrEnabled] is true the child is laid out at half the width and drawn twice, once for each
 * eye. Touches on the right half are mapped back onto the single real child.
 */
class VrSbsLayout(context: Context) : FrameLayout(context) {

    var vrEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    private val dividerPaint = Paint().apply { color = Color.BLACK }
    private var touchShift = 0f

    init {
        // Needed so dispatchDraw is called even when there is no background.
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!vrEnabled) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val halfSpec = MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(halfSpec, heightSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!vrEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }
        val half = (right - left) / 2
        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, half, bottom - top)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!vrEnabled) {
            super.dispatchDraw(canvas)
            return
        }
        val half = width / 2
        canvas.drawColor(Color.BLACK)

        // Left eye
        canvas.save()
        canvas.clipRect(0, 0, half, height)
        super.dispatchDraw(canvas)
        canvas.restore()

        // Right eye
        canvas.save()
        canvas.clipRect(half, 0, width, height)
        canvas.translate(half.toFloat(), 0f)
        super.dispatchDraw(canvas)
        canvas.restore()

        canvas.drawRect(half - 1f, 0f, half + 1f, height.toFloat(), dividerPaint)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!vrEnabled) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            touchShift = if (ev.x >= width / 2f) -(width / 2f) else 0f
        }
        val shifted = MotionEvent.obtain(ev)
        shifted.offsetLocation(touchShift, 0f)
        val handled = super.dispatchTouchEvent(shifted)
        shifted.recycle()
        return handled
    }
}
// KMK <--
