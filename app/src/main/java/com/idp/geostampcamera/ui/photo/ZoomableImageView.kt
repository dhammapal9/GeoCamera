package com.idp.geostampcamera.ui.photo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView that supports pinch-to-zoom, drag-pan while zoomed, and double-tap toggle.
 * Uses scaleType=MATRIX internally; initial fit matches fitCenter.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val displayMatrix = Matrix()
    private val savedMatrix = Matrix()
    private val values = FloatArray(9)

    private var mode = Mode.NONE
    private val startPoint = PointF()
    private var minScale = 1f
    private val maxScale = 5f
    private var viewWidth = 0
    private var viewHeight = 0

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, DoubleTapListener())

    private enum class Mode { NONE, DRAG, ZOOM }

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetMatrix() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        resetMatrix()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(displayMatrix)
                startPoint.set(event.x, event.y)
                mode = Mode.DRAG
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = Mode.ZOOM
                savedMatrix.set(displayMatrix)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.DRAG && !scaleDetector.isInProgress) {
                    val dx = event.x - startPoint.x
                    val dy = event.y - startPoint.y
                    displayMatrix.set(savedMatrix)
                    displayMatrix.postTranslate(dx, dy)
                    clampTranslation()
                    imageMatrix = displayMatrix
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = Mode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = Mode.NONE
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun resetMatrix() {
        val d = drawable ?: return
        if (viewWidth == 0 || viewHeight == 0) return
        val dWidth = d.intrinsicWidth
        val dHeight = d.intrinsicHeight
        if (dWidth <= 0 || dHeight <= 0) return
        val scale = min(viewWidth.toFloat() / dWidth, viewHeight.toFloat() / dHeight)
        minScale = scale
        displayMatrix.reset()
        displayMatrix.setScale(scale, scale)
        val dx = (viewWidth - dWidth * scale) / 2f
        val dy = (viewHeight - dHeight * scale) / 2f
        displayMatrix.postTranslate(dx, dy)
        imageMatrix = displayMatrix
    }

    private fun clampTranslation() {
        displayMatrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scale = values[Matrix.MSCALE_X]
        val d = drawable ?: return
        val drawableWidth = d.intrinsicWidth * scale
        val drawableHeight = d.intrinsicHeight * scale
        var fixX = 0f
        var fixY = 0f
        if (drawableWidth <= viewWidth) {
            fixX = (viewWidth - drawableWidth) / 2f - transX
        } else {
            if (transX > 0) fixX = -transX
            if (transX + drawableWidth < viewWidth) fixX = viewWidth - drawableWidth - transX
        }
        if (drawableHeight <= viewHeight) {
            fixY = (viewHeight - drawableHeight) / 2f - transY
        } else {
            if (transY > 0) fixY = -transY
            if (transY + drawableHeight < viewHeight) fixY = viewHeight - drawableHeight - transY
        }
        if (fixX != 0f || fixY != 0f) displayMatrix.postTranslate(fixX, fixY)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            displayMatrix.getValues(values)
            val current = values[Matrix.MSCALE_X]
            var factor = detector.scaleFactor
            val target = current * factor
            if (target < minScale) factor = minScale / current
            if (target > maxScale) factor = maxScale / current
            displayMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation()
            imageMatrix = displayMatrix
            return true
        }
    }

    private inner class DoubleTapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            displayMatrix.getValues(values)
            val current = values[Matrix.MSCALE_X]
            if (current > minScale * 1.05f) {
                resetMatrix()
            } else {
                val targetScale = minScale * 2f
                val factor = targetScale / current
                displayMatrix.postScale(factor, factor, e.x, e.y)
                clampTranslation()
                imageMatrix = displayMatrix
            }
            return true
        }
    }
}
