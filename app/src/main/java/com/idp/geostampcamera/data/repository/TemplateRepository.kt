package com.idp.geostampcamera.data.repository

import android.graphics.Color
import com.idp.geostampcamera.data.local.TemplateDao
import com.idp.geostampcamera.data.local.toDomain
import com.idp.geostampcamera.data.local.toEntity
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.util.DateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(
    private val dao: TemplateDao
) {

    fun observeAll(): Flow<List<StampTemplate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): StampTemplate? = dao.findById(id)?.toDomain()

    suspend fun upsert(template: StampTemplate): StampTemplate {
        val stored = template.copy(updatedAtMillis = System.currentTimeMillis())
        dao.upsert(stored.toEntity())
        return stored
    }

    suspend fun duplicate(source: StampTemplate, newName: String): StampTemplate {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            isBuiltIn = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsert(copy.toEntity())
        return copy
    }

    suspend fun deleteCustom(id: String) {
        dao.deleteCustom(id)
    }

    suspend fun ensureDefaults() {
        if (dao.count() == 0) {
            dao.upsertAll(defaults().map { it.toEntity() })
        }
    }

    private fun defaults(): List<StampTemplate> {
        val black = Color.parseColor("#B2000000")
        val white = Color.WHITE
        val accent = Color.parseColor("#F2B134")
        val reportingGreen = Color.parseColor("#B21F9D55")
        val navy = Color.parseColor("#B20F7B8E")
        val base = StampTemplate(
            id = "",
            name = "",
            type = StampTemplate.TemplateType.CLASSIC,
            layout = StampTemplate.LayoutStyle.HORIZONTAL,
            backgroundArgb = black,
            backgroundAlpha = 178,
            textArgb = white,
            accentArgb = accent,
            fontScale = 1f,
            cornerRadiusDp = 8,
            borderDp = 0,
            borderArgb = Color.TRANSPARENT,
            mapSize = StampTemplate.MapSize.MEDIUM,
            coordinateFormat = StampTemplate.CoordFormat.DECIMAL,
            dateFormat = DateFormatter.FORMAT_FULL,
            timeFormat = "hh:mm a",
            timezoneId = null,
            tempUnit = StampTemplate.TempUnit.CELSIUS,
            altitudeUnit = StampTemplate.AltitudeUnit.METERS,
            showMap = true,
            showShortAddress = true,
            showFullAddress = true,
            showCoordinates = true,
            showPlusCode = false,
            showDateTime = true,
            showAltitude = false,
            showAccuracy = false,
            showSpeed = false,
            showBearing = false,
            showCompass = false,
            showMagnetic = false,
            showWeather = false,
            showLogo = false,
            showNote = true,
            note = "Captured by GeoStamp Camera",
            hashtag = "",
            projectName = "",
            businessName = "",
            reference = "",
            logoUri = null,
            isBuiltIn = true
        )
        return listOf(
            base.copy(id = "classic", name = "Classic", type = StampTemplate.TemplateType.CLASSIC),
            base.copy(
                id = "advance", name = "Advance", type = StampTemplate.TemplateType.ADVANCE,
                layout = StampTemplate.LayoutStyle.DETAILED,
                showAltitude = true, showAccuracy = true, showPlusCode = true, showWeather = true
            ),
            base.copy(
                id = "datetime", name = "Date & Time", type = StampTemplate.TemplateType.DATE_TIME,
                layout = StampTemplate.LayoutStyle.STACKED,
                showMap = false, showAltitude = false, mapSize = StampTemplate.MapSize.HIDDEN,
                fontScale = 1.15f
            ),
            base.copy(
                id = "reporting", name = "Reporting", type = StampTemplate.TemplateType.REPORTING,
                layout = StampTemplate.LayoutStyle.DETAILED,
                backgroundArgb = reportingGreen, showAccuracy = true, showAltitude = true,
                showBearing = true, note = "Site inspection by GeoStamp"
            ),
            base.copy(
                id = "navigation", name = "Navigation / Compass", type = StampTemplate.TemplateType.NAVIGATION_COMPASS,
                layout = StampTemplate.LayoutStyle.DETAILED,
                showCompass = true, showMagnetic = true, showBearing = true, showAltitude = true,
                showAccuracy = true
            ),
            base.copy(
                id = "scanlocation", name = "Scan Location (QR)", type = StampTemplate.TemplateType.SCAN_LOCATION,
                layout = StampTemplate.LayoutStyle.DETAILED
            ),
            base.copy(
                id = "minimal", name = "Minimal", type = StampTemplate.TemplateType.CUSTOM,
                layout = StampTemplate.LayoutStyle.MINIMAL, isBuiltIn = true,
                showMap = false, showFullAddress = false, showNote = false,
                mapSize = StampTemplate.MapSize.HIDDEN
            ),
            base.copy(
                id = "travel", name = "Travel", type = StampTemplate.TemplateType.CUSTOM,
                backgroundArgb = navy, hashtag = "#travel", isBuiltIn = true
            ),
            base.copy(
                id = "strip", name = "Compact GPS strip", type = StampTemplate.TemplateType.CUSTOM,
                layout = StampTemplate.LayoutStyle.STRIP, showMap = false,
                mapSize = StampTemplate.MapSize.HIDDEN, showFullAddress = false,
                isBuiltIn = true
            )
        )
    }
}
