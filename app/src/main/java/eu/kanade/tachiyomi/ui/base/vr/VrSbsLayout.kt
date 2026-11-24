package eu.kanade.tachiyomi.ui.base.vr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat

// KMK -->
/** Calibration for [VrSbsLayout], similar to Kodi's zoom / pixel ratio / vertical shift. */
data class VrCalibration(
    /** UI size, 0.3 - 1.5. The layout is given `eye area / uiScale` of room and drawn at this scale. */
    val uiScale: Float = 0.5f,
    /** Fraction of each eye's half of the screen that is used, 0.5 - 1.0. */
    val eyeFill: Float = 0.9f,
    /** Pixel ratio: horizontal stretch, 0.5 - 1.5. */
    val aspect: Float = 1f,
    /** Moves the left image left and the right image right by this many pixels. */
    val separationPx: Float = 0f,
    /** Moves both images down by this many pixels. */
    val offsetYPx: Float = 0f,
)

/**
 * Side-by-side (SBS) layout for phone VR goggles.
 *
 * When [vrEnabled] is true the child is laid out in a virtual size and drawn twice, once for each
 * eye, scaled and positioned by [calibration]. Touches are mapped back onto the single real child
 * and system window insets are ignored so the picture really uses the whole screen.
 */
class VrSbsLayout(context: Context) : FrameLayout(context) {

    var vrEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            requestApplyInsets()
            invalidate()
        }

    var calibration: VrCalibration = VrCalibration()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    private val dividerPaint = Paint().apply { color = Color.BLACK }
    private val touchMatrix = Matrix()

    // Geometry of the current layout pass (window pixels).
    private var scaleX = 1f
    private var scaleY = 1f
    private var contentW = 0f
    private var contentH = 0f

    init {
        // Children are drawn scaled and may be larger than this view, so never clip them.
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
    }

    private fun computeGeometry(width: Int, height: Int) {
        val c = calibration
        contentW = width / 2f * c.eyeFill
        contentH = height * c.eyeFill
        scaleX = c.uiScale * c.aspect
        scaleY = c.uiScale
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!vrEnabled) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        computeGeometry(width, height)

        val virtualW = (contentW / scaleX).toInt().coerceAtLeast(1)
        val virtualH = (contentH / scaleY).toInt().coerceAtLeast(1)
        val childWidth = MeasureSpec.makeMeasureSpec(virtualW, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(virtualH, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidth, childHeight)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!vrEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    /** Top-left corner, in window pixels, of the picture of [eye] (0 = left, 1 = right). */
    private fun eyeOriginX(eye: Int): Float {
        val half = width / 2f
        val sep = calibration.separationPx
        val centerX = half * eye + half / 2f + if (eye == 0) -sep else sep
        return centerX - contentW / 2f
    }

    private fun eyeOriginY(): Float = (height - contentH) / 2f + calibration.offsetYPx

    override fun dispatchDraw(canvas: Canvas) {
        if (!vrEnabled) {
            super.dispatchDraw(canvas)
            return
        }
        val half = width / 2
        canvas.drawColor(Color.BLACK)

        for (eye in 0..1) {
            canvas.save()
            canvas.clipRect(half * eye, 0, if (eye == 0) half else width, height)
            canvas.translate(eyeOriginX(eye), eyeOriginY())
            canvas.scale(scaleX, scaleY)
            super.dispatchDraw(canvas)
            canvas.restore()
        }

        canvas.drawRect(half - 1f, 0f, half + 1f, height.toFloat(), dividerPaint)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!vrEnabled) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val eye = if (ev.x >= width / 2f) 1 else 0
            touchMatrix.reset()
            touchMatrix.postTranslate(-eyeOriginX(eye), -eyeOriginY())
            touchMatrix.postScale(1f / scaleX, 1f / scaleY)
        }
        val mapped = MotionEvent.obtain(ev)
        mapped.transform(touchMatrix)
        val handled = super.dispatchTouchEvent(mapped)
        mapped.recycle()
        return handled
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (!vrEnabled) return super.dispatchApplyWindowInsets(insets)
        // In VR the picture uses the whole screen: children get no system bar / cutout padding.
        val consumed = WindowInsetsCompat.CONSUMED.toWindowInsets() ?: return insets
        for (i in 0 until childCount) {
            val child: View = getChildAt(i)
            child.dispatchApplyWindowInsets(consumed)
        }
        return consumed
    }
}
// KMK <--
