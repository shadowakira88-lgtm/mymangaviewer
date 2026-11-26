package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

// KMK -->
/**
 * A loupe drawn as a normal view on top of the reader. Because it is part of the view tree, the VR
 * side-by-side layout draws it once per eye at the same spot, so both eyes see the same lens and it
 * moves in both eyes together (the framework Magnifier is a separate window and cannot be mirrored).
 */
class MagnifierLensView(context: Context, private val source: View) : View(context) {

    private val density = resources.displayMetrics.density
    private val radius = 110f * density
    private val zoom = 2.2f

    var lensX = 0f
        private set
    var lensY = 0f
        private set

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val clip = Path()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
    }
    private val refresh = object : Runnable {
        override fun run() {
            if (visibility == VISIBLE) {
                invalidate()
                postDelayed(this, 400)
            }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    fun show() {
        if (width > 0) {
            lensX = width / 2f
            lensY = height / 2f
        }
        visibility = VISIBLE
        removeCallbacks(refresh)
        post(refresh)
        invalidate()
    }

    fun hide() {
        removeCallbacks(refresh)
        visibility = GONE
    }

    fun moveBy(dx: Float, dy: Float) {
        lensX = (lensX + dx).coerceIn(0f, width.toFloat())
        lensY = (lensY + dy).coerceIn(0f, height.toFloat())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lensX = lensX.coerceIn(0f, w.toFloat())
        lensY = lensY.coerceIn(0f, h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val side = (radius * 2).toInt()
        val bmp = bitmap ?: Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also {
            bitmap = it
            bitmapCanvas = Canvas(it)
        }
        val c = bitmapCanvas ?: return
        c.drawColor(Color.BLACK)
        c.save()
        c.scale(zoom, zoom)
        // Lens centre -> middle of the small bitmap; source is drawn in its own coordinates.
        c.translate(radius / zoom - lensX + source.left, radius / zoom - lensY + source.top)
        val drawn = try {
            source.draw(c)
            true
        } catch (_: Exception) {
            false // e.g. a hardware bitmap on a software canvas: show the plain lens frame
        }
        c.restore()

        canvas.save()
        clip.reset()
        clip.addCircle(lensX, lensY, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        if (drawn) canvas.drawBitmap(bmp, lensX - radius, lensY - radius, null)
        canvas.restore()
        canvas.drawCircle(lensX, lensY, radius, ring)
    }
}
// KMK <--
