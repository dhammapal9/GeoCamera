package com.idp.geostampcamera.stamp

import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.util.CoordinateFormatter
import com.idp.geostampcamera.util.DateFormatter
import com.idp.geostampcamera.util.PlusCodes
import java.util.Locale
import java.util.TimeZone

/** Produces the textual lines for a stamp from an immutable [StampContext]. */
object StampTextBuilder {

    data class StampLines(
        val headline: String?,
        val shortAddress: String?,
        val fullAddress: String?,
        val coordinates: String?,
        val plusCode: String?,
        val dateTime: String?,
        val weather: String?,
        val extras: List<String>,
        val note: String?,
        val hashtag: String?
    )

    fun build(context: StampContext): StampLines {
        val t = context.template
        val loc = context.location

        val headline = when {
            t.projectName.isNotBlank() -> t.projectName
            t.businessName.isNotBlank() -> t.businessName
            else -> null
        }

        val shortAddr = if (t.showShortAddress) loc?.address?.shortAddress.orEmptyOrNull() else null
        val fullAddr = if (t.showFullAddress) loc?.address?.fullAddress.orEmptyOrNull() else null

        val coords = if (t.showCoordinates && loc != null) {
            when (t.coordinateFormat) {
                StampTemplate.CoordFormat.DECIMAL -> "Lat ${"%.6f".format(Locale.US, loc.latitude)}°  Long ${"%.6f".format(Locale.US, loc.longitude)}°"
                StampTemplate.CoordFormat.DMS -> CoordinateFormatter.formatDms(loc.latitude, loc.longitude)
            }
        } else null

        val plus = if (t.showPlusCode && loc != null) {
            PlusCodes.encode(loc.latitude, loc.longitude).takeIf { it.isNotBlank() }?.let { "Plus Code: $it" }
        } else null

        val dt = if (t.showDateTime) {
            val tz = t.timezoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
            DateFormatter.formatWithTz(context.captureAtMillis, t.dateFormat, tz)
        } else null

        val weather = if (t.showWeather && context.weather != null) {
            buildString {
                context.weather.temperatureC?.let {
                    val value = when (t.tempUnit) {
                        StampTemplate.TempUnit.CELSIUS -> "${"%.1f".format(Locale.US, it)}°C"
                        StampTemplate.TempUnit.FAHRENHEIT -> "${"%.1f".format(Locale.US, it * 9 / 5 + 32)}°F"
                    }
                    append(value)
                }
                context.weather.humidityPercent?.let { append("  RH ").append(it).append("%") }
                context.weather.pressureHpa?.let { append("  ${"%.0f".format(Locale.US, it)}hPa") }
                context.weather.windSpeedMps?.let { append("  Wind ${"%.1f".format(Locale.US, it)}m/s") }
            }.ifBlank { null }
        } else null

        val extras = buildList {
            if (t.showAltitude && loc?.altitudeMeters != null) {
                val value = when (t.altitudeUnit) {
                    StampTemplate.AltitudeUnit.METERS -> "${"%.1f".format(Locale.US, loc.altitudeMeters)} m"
                    StampTemplate.AltitudeUnit.FEET -> "${"%.0f".format(Locale.US, loc.altitudeMeters * 3.28084)} ft"
                }
                add("Alt $value")
            }
            if (t.showAccuracy && loc?.accuracyMeters != null) add("Acc ±${"%.0f".format(Locale.US, loc.accuracyMeters)} m")
            if (t.showSpeed && loc?.speedMps != null) add("Speed ${"%.1f".format(Locale.US, loc.speedMps)} m/s")
            if (t.showBearing && loc?.bearingDegrees != null) {
                add("Bearing ${"%.0f".format(Locale.US, loc.bearingDegrees)}° ${PlusCodes.cardinalFromBearing(loc.bearingDegrees)}")
            }
            if (t.showCompass && context.compass != null) {
                add("Heading ${"%.0f".format(Locale.US, context.compass.headingDegrees)}° ${context.compass.cardinal}")
            }
            if (t.showMagnetic && context.magnetic != null) {
                add("Magnetic ${"%.1f".format(Locale.US, context.magnetic.microteslas)} µT")
            }
        }

        val note = if (t.showNote) t.note.trim().takeIf { it.isNotEmpty() } else null
        val hashtag = t.hashtag.trim().takeIf { it.isNotEmpty() }

        return StampLines(
            headline = headline,
            shortAddress = shortAddr,
            fullAddress = fullAddr,
            coordinates = coords,
            plusCode = plus,
            dateTime = dt,
            weather = weather,
            extras = extras,
            note = note,
            hashtag = hashtag
        )
    }

    private fun String?.orEmptyOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }
}
