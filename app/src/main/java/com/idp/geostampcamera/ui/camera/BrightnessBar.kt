package com.idp.geostampcamera.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vertical brightness/exposure indicator.
 *
 *  - Top of the view = +max EV
 *  - Center = 0 EV
 *  - Bottom = -max EV
 *
 * Draws a thin track, a filled band from center up (amber) or center down (blue tint),
 * and a thumb at the current position. Supports drag + direct taps anywhere on the track.
 */
class BrightnessBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Range is -1..+1 where 0 == centre. */
    private var level: Float = 0f
    private var listener: ((Float) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val trackWidthPx = 4f * density
    private val thumbRadiusPx = 10f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val fillPaintBright = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")   // brand accent light (amber)
        style = Paint.Style.FILL
    }
    private val fillPaintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A78BFA")   // brand tertiary (violet) for "darker"
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    fun setLevel(newLevel: Float, notify: Boolean = false) {
        val clamped = newLevel.coerceIn(-1f, 1f)
        if (clamped == level) return
        level = clamped
        invalidate()
        if (notify) listener?.invoke(level)
    }

    fun getLevel(): Float = level

    fun setOnLevelChangeListener(action: (Float) -> Unit) { listener = action }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val halfTrack = trackWidthPx / 2f
        val radius = halfTrack

        // Full track
        val trackRect = RectF(cx - halfTrack, 0f, cx + halfTrack, h)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // Fill from centre to the thumb (above = amber, below = blue)
        val thumbY = cy - level * (h / 2f - thumbRadiusPx)
        if (level > 0f) {
            val fillRect = RectF(cx - halfTrack, thumbY, cx + halfTrack, cy)
            canvas.drawRoundRect(fillRect, radius, radius, fillPaintBright)
        } else if (level < 0f) {
            val fillRect = RectF(cx - halfTrack, cy, cx + halfTrack, thumbY)
            canvas.drawRoundRect(fillRect, radius, radius, fillPaintDark)
        }

        // Centre tick marker so user knows where 0 EV is
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            strokeWidth = 2f * density
        }
        canvas.drawLine(cx - 8f * density, cy, cx + 8f * density, cy, tickPaint)

        // Thumb
        canvas.drawCircle(cx, thumbY, thumbRadiusPx, thumbPaint)
        canvas.drawCircle(cx, thumbY, thumbRadiusPx, thumbRing)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val h = height.toFloat()
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                // y=0 (top) -> level +1, y=height (bottom) -> level -1
                val newLevel = (1f - 2f * (event.y / h)).coerceIn(-1f, 1f)
                setLevel(newLevel, notify = true)
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
