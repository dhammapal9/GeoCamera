package com.idp.geostampcamera.util

import com.idp.geostampcamera.domain.model.CapturedPhoto
import java.text.SimpleDateFormat
import java.util.Locale

object FilenameBuilder {

    enum class Token(val key: String, val label: String) {
        DATETIME("datetime", "Date & time"),
        PROJECT("project", "Project"),
        ADDRESS("address", "Short address"),
        LATLNG("latlng", "Latitude × Longitude"),
        PLUSCODE("pluscode", "Plus Code"),
        TEMPLATE("template", "Template name"),
        CUSTOM("custom", "Custom text"),
        SEQ("seq", "Sequence number")
    }

    data class Config(val order: List<Token>, val custom: String, val separator: String = "_")

    val DEFAULT = Config(listOf(Token.DATETIME, Token.ADDRESS), "")

    fun build(config: Config, photo: CapturedPhoto, sequence: Int): String {
        val parts = config.order.mapNotNull { token ->
            when (token) {
                Token.DATETIME -> SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date(photo.timestampMillis))
                Token.PROJECT -> photo.projectName?.sanitised()
                Token.ADDRESS -> photo.shortAddress?.sanitised()
                Token.LATLNG -> if (photo.latitude != null && photo.longitude != null)
                    "${"%.4f".format(Locale.US, photo.latitude)}_${"%.4f".format(Locale.US, photo.longitude)}" else null
                Token.PLUSCODE -> if (photo.latitude != null && photo.longitude != null)
                    PlusCodes.encode(photo.latitude, photo.longitude).replace("+", "").takeIf { it.isNotBlank() } else null
                Token.TEMPLATE -> photo.templateId?.sanitised()
                Token.CUSTOM -> config.custom.takeIf { it.isNotBlank() }?.sanitised()
                Token.SEQ -> sequence.toString().padStart(4, '0')
            }
        }
        val joined = parts.filter { it.isNotBlank() }.joinToString(config.separator)
        return if (joined.isBlank()) "GeoStamp" else joined
    }

    private fun String.sanitised(): String =
        replace(Regex("[^A-Za-z0-9\\-_]+"), "")
            .trim('_', '-')
            .take(32)
}
