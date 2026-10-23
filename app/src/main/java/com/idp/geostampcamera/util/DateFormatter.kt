package com.idp.geostampcamera.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateFormatter {
    const val FORMAT_FULL = "EEEE, dd/MM/yyyy hh:mm a"
    const val FORMAT_SHORT = "dd/MM/yyyy HH:mm"
    const val FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ssXXX"

    fun format(timestampMillis: Long, pattern: String, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = timeZone
        return sdf.format(Date(timestampMillis))
    }

    fun formatWithTz(timestampMillis: Long, pattern: String, timeZone: TimeZone): String {
        val base = format(timestampMillis, pattern, timeZone)
        val tzName = timeZone.getDisplayName(false, TimeZone.SHORT, Locale.getDefault())
        return "$base $tzName"
    }
}
