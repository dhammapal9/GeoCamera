package com.idp.geostampcamera.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

object CoordinateFormatter {

    fun formatDecimal(lat: Double, lng: Double): String {
        val latHem = if (lat >= 0) "N" else "S"
        val lngHem = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.6f° %s, %.6f° %s",
            abs(lat), latHem, abs(lng), lngHem
        )
    }

    fun formatDms(lat: Double, lng: Double): String {
        val latHem = if (lat >= 0) "N" else "S"
        val lngHem = if (lng >= 0) "E" else "W"
        return "${toDms(abs(lat))}$latHem, ${toDms(abs(lng))}$lngHem"
    }

    private fun toDms(absValue: Double): String {
        val d = floor(absValue).toInt()
        val minsFull = (absValue - d) * 60.0
        val m = floor(minsFull).toInt()
        val s = (minsFull - m) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"", d, m, s)
    }

    fun isValid(lat: Double, lng: Double): Boolean =
        lat in -90.0..90.0 && lng in -180.0..180.0

    fun parseDecimal(latText: String, lngText: String): Pair<Double, Double>? {
        val lat = latText.trim().toDoubleOrNull() ?: return null
        val lng = lngText.trim().toDoubleOrNull() ?: return null
        return if (isValid(lat, lng)) lat to lng else null
    }
}
