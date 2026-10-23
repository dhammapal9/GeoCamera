package com.idp.geostampcamera.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.cardview.widget.CardView
import coil.load
import com.idp.geostampcamera.R
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.stamp.StampTextBuilder

class StampPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    init {
        radius = resources.getDimension(R.dimen.radius_md)
        cardElevation = 0f
        LayoutInflater.from(context).inflate(R.layout.view_stamp_preview, this, true)
    }

    fun bind(stamp: StampContext, mapBitmap: Bitmap? = null) {
        val lines = StampTextBuilder.build(stamp)
        val template = stamp.template
        setCardBackgroundColor(withAlpha(template.backgroundArgb, template.backgroundAlpha))
        findViewById<ImageView>(R.id.mapThumb).apply {
            if (template.showMap && mapBitmap != null) {
                setImageBitmap(mapBitmap)
                visibility = VISIBLE
            } else if (template.showMap) {
                setImageDrawable(ColorDrawable(Color.parseColor("#334A5A6E")))
                visibility = VISIBLE
            } else {
                visibility = GONE
            }
        }
        val mapHolder = findViewById<android.view.View>(R.id.mapHolder)
        mapHolder.visibility = if (template.showMap) VISIBLE else GONE
        // Only show the overlay pin while the Google snapshot is loading — once a real
        // map bitmap is drawn, the snapshot already carries its own marker.
        findViewById<ImageView>(R.id.mapPin)?.visibility =
            if (template.showMap && mapBitmap == null) VISIBLE else GONE
        // Logo chip in the top-right of the text column.
        val logoView = findViewById<ImageView>(R.id.stampLogo)
        val logoUri = template.logoUri
        if (template.showLogo && !logoUri.isNullOrBlank()) {
            logoView.visibility = VISIBLE
            logoView.load(Uri.parse(logoUri))
        } else {
            logoView.visibility = GONE
            logoView.setImageDrawable(null)
        }
        val short = findViewById<android.widget.TextView>(R.id.stampShortAddress)
        val full = findViewById<android.widget.TextView>(R.id.stampFullAddress)
        val coords = findViewById<android.widget.TextView>(R.id.stampCoords)
        val time = findViewById<android.widget.TextView>(R.id.stampTime)
        val extras = findViewById<android.widget.TextView>(R.id.stampExtras)
        val note = findViewById<android.widget.TextView>(R.id.stampNote)

        short.setTextColorArgb(template.accentArgb)
        full.setTextColorArgb(withAlpha(template.textArgb, 0xDD))
        coords.setTextColorArgb(withAlpha(template.textArgb, 0xDD))
        time.setTextColorArgb(withAlpha(template.textArgb, 0xDD))
        extras.setTextColorArgb(withAlpha(template.textArgb, 0xCC))
        note.setTextColorArgb(withAlpha(template.textArgb, 0xAA))

        short.setTextOrGone(lines.shortAddress ?: lines.headline)
        full.setTextOrGone(lines.fullAddress)
        coords.setTextOrGone(lines.coordinates ?: lines.plusCode)
        time.setTextOrGone(lines.dateTime ?: lines.weather)
        extras.setTextOrGone(if (lines.extras.isNotEmpty()) lines.extras.joinToString("  •  ") else null)
        note.setTextOrGone(lines.note ?: lines.hashtag)
    }

    private fun android.widget.TextView.setTextColorArgb(argb: Int) { setTextColor(argb) }

    private fun android.widget.TextView.setTextOrGone(text: String?) {
        if (text.isNullOrBlank()) { visibility = GONE } else {
            visibility = VISIBLE
            setText(text)
        }
    }

    private fun withAlpha(argb: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(argb), Color.green(argb), Color.blue(argb)
    )
}
