package com.idp.geostampcamera.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        strokeWidth = 1.5f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = w / 3f
        val stepY = h / 3f
        canvas.drawLine(stepX, 0f, stepX, h, paint)
        canvas.drawLine(stepX * 2, 0f, stepX * 2, h, paint)
        canvas.drawLine(0f, stepY, w, stepY, paint)
        canvas.drawLine(0f, stepY * 2, w, stepY * 2, paint)
    }
}
