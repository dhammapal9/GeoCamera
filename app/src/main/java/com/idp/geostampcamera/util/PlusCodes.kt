package com.idp.geostampcamera.util

import kotlin.math.floor

/**
 * Minimal Open Location Code encoder (full codes only). Based on the public algorithm
 * reference. Precision ~10 characters => about 13.9m x 13.9m at the equator.
 */
object PlusCodes {

    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val PADDING = '0'
    private const val PAIR_CODE_LENGTH = 10
    private const val LATITUDE_MAX = 90
    private const val LONGITUDE_MAX = 180
    private const val PAIR_PRECISION = 20L * 20L * 20L * 20L * 20L

    fun encode(latitude: Double, longitude: Double, codeLength: Int = PAIR_CODE_LENGTH): String {
        require(codeLength in 2..15 && codeLength % 2 == 0) { "Invalid code length" }
        if (!CoordinateFormatter.isValid(latitude, longitude)) return ""
        val lat = clipLatitude(latitude)
        val lng = normalizeLongitude(longitude)
        val latVal = ((lat + LATITUDE_MAX) * PAIR_PRECISION).toLong()
        val lngVal = ((lng + LONGITUDE_MAX) * PAIR_PRECISION).toLong()
        val code = StringBuilder()
        var tempLat = latVal
        var tempLng = lngVal
        for (i in 0 until codeLength / 2) {
            val latDigit = (tempLat % 20).toInt()
            val lngDigit = (tempLng % 20).toInt()
            tempLat /= 20
            tempLng /= 20
            code.insert(0, CODE_ALPHABET[lngDigit])
            code.insert(0, CODE_ALPHABET[latDigit])
        }
        if (code.length < SEPARATOR_POSITION) {
            while (code.length < SEPARATOR_POSITION) code.append(PADDING)
            code.append(SEPARATOR)
        } else {
            code.insert(SEPARATOR_POSITION, SEPARATOR)
        }
        return code.toString()
    }

    private fun clipLatitude(latitude: Double): Double =
        latitude.coerceIn(-LATITUDE_MAX.toDouble(), LATITUDE_MAX.toDouble() - 1e-9)

    private fun normalizeLongitude(longitude: Double): Double {
        var l = longitude
        while (l < -LONGITUDE_MAX) l += LONGITUDE_MAX * 2
        while (l >= LONGITUDE_MAX) l -= LONGITUDE_MAX * 2
        return l
    }

    fun cardinalFromBearing(bearingDeg: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((bearingDeg % 360 + 360) % 360)
        val idx = (floor((normalized / 45.0) + 0.5).toInt()) % 8
        return dirs[idx]
    }
}
