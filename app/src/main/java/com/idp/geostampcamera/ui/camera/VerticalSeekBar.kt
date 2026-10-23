package com.idp.geostampcamera.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * SeekBar oriented vertically with top = max, bottom = min.
 *
 * Unlike `android:rotation="270"` on a plain SeekBar this view:
 *   - measures itself correctly (not relying on parent to rotate hit-testing)
 *   - delivers touch events on its drawn region
 *   - calls `setProgress(progress, fromUser = true)` so listeners get the fromUser flag
 */
class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun onDraw(c: Canvas) {
        c.rotate(-90f)
        c.translate(-height.toFloat(), 0f)
        super.onDraw(c)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val fraction = (1f - (event.y / height.toFloat())).coerceIn(0f, 1f)
                val newProgress = (fraction * max).toInt()
                if (newProgress != progress) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setProgress(newProgress, true)
                    } else {
                        @Suppress("DEPRECATION")
                        progress = newProgress
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
