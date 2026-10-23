package com.idp.geostampcamera.domain.model

import android.content.Context
import com.idp.geostampcamera.R

data class StampTemplate(
    val id: String,
    val name: String,
    val type: TemplateType,
    val layout: LayoutStyle,
    val backgroundArgb: Int,
    val backgroundAlpha: Int, // 0..255
    val textArgb: Int,
    val accentArgb: Int,
    val fontScale: Float,
    val cornerRadiusDp: Int,
    val borderDp: Int,
    val borderArgb: Int,
    val mapSize: MapSize,
    val coordinateFormat: CoordFormat,
    val dateFormat: String,
    val timeFormat: String,
    val timezoneId: String?,
    val tempUnit: TempUnit,
    val altitudeUnit: AltitudeUnit,
    val showMap: Boolean,
    val showShortAddress: Boolean,
    val showFullAddress: Boolean,
    val showCoordinates: Boolean,
    val showPlusCode: Boolean,
    val showDateTime: Boolean,
    val showAltitude: Boolean,
    val showAccuracy: Boolean,
    val showSpeed: Boolean,
    val showBearing: Boolean,
    val showCompass: Boolean,
    val showMagnetic: Boolean,
    val showWeather: Boolean,
    val showLogo: Boolean,
    val showNote: Boolean,
    val note: String,
    val hashtag: String,
    val projectName: String,
    val businessName: String,
    val reference: String,
    val logoUri: String?,
    val isBuiltIn: Boolean = false,
    val fontFamily: FontFamily = FontFamily.SANS,
    val fieldOrder: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun getLocalizedName(context: Context): String {
        if (!isBuiltIn) return name
        val resId = when (id.lowercase()) {
            "classic" -> R.string.template_default_classic
            "advance" -> R.string.template_default_advance
            "datetime", "date_time" -> R.string.template_default_datetime
            "reporting" -> R.string.template_default_reporting
            "navigation" -> R.string.template_default_navigation
            "scanlocation" -> R.string.template_default_scanlocation
            "minimal" -> R.string.template_default_minimal
            "travel" -> R.string.template_default_travel
            "strip" -> R.string.template_default_compactstrip
            else -> null
        }
        return if (resId != null) context.getString(resId) else name
    }

    enum class TemplateType { CLASSIC, ADVANCE, DATE_TIME, REPORTING, NAVIGATION_COMPASS, SCAN_LOCATION, CUSTOM }
    enum class LayoutStyle { HORIZONTAL, STACKED, STRIP, DETAILED, MINIMAL }
    enum class MapSize { SMALL, MEDIUM, LARGE, HIDDEN }
    enum class CoordFormat { DECIMAL, DMS }
    enum class TempUnit { CELSIUS, FAHRENHEIT }
    enum class AltitudeUnit { METERS, FEET }
    enum class FontFamily { SANS, SERIF, MONO, CONDENSED }
}
