package com.idp.geostampcamera.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class LevelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        strokeWidth = 2f
    }

    private var rollDegrees: Float = 0f

    fun setRoll(degrees: Float) {
        rollDegrees = degrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.18f

        // Horizon guide
        canvas.drawLine(cx - radius, cy, cx + radius, cy, guidePaint)

        // Rolled line in brand accent when level, red when steeply off
        val absRoll = abs(rollDegrees)
        linePaint.color = when {
            absRoll < 2f -> Color.parseColor("#1F9D55")
            absRoll < 10f -> Color.parseColor("#F2B134")
            else -> Color.parseColor("#D23B3B")
        }
        canvas.save()
        canvas.rotate(rollDegrees, cx, cy)
        canvas.drawLine(cx - radius * 0.9f, cy, cx + radius * 0.9f, cy, linePaint)
        canvas.restore()
    }
}
