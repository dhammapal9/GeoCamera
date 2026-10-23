package com.idp.geostampcamera

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.domain.model.AddressDetails
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.stamp.StampTextBuilder
import com.idp.geostampcamera.util.DateFormatter
import org.junit.Test

class StampTextBuilderTest {

    private fun template(block: (StampTemplate) -> StampTemplate = { it }): StampTemplate = block(
        StampTemplate(
            id = "test",
            name = "test",
            type = StampTemplate.TemplateType.CLASSIC,
            layout = StampTemplate.LayoutStyle.HORIZONTAL,
            backgroundArgb = Color.BLACK,
            backgroundAlpha = 180,
            textArgb = Color.WHITE,
            accentArgb = Color.YELLOW,
            fontScale = 1f,
            cornerRadiusDp = 8,
            borderDp = 0,
            borderArgb = Color.TRANSPARENT,
            mapSize = StampTemplate.MapSize.MEDIUM,
            coordinateFormat = StampTemplate.CoordFormat.DECIMAL,
            dateFormat = DateFormatter.FORMAT_SHORT,
            timeFormat = "HH:mm",
            timezoneId = "UTC",
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
            showNote = false,
            note = "",
            hashtag = "",
            projectName = "",
            businessName = "",
            reference = "",
            logoUri = null,
            isBuiltIn = false
        )
    )

    private val sampleLocation = LocationSnapshot(
        latitude = 10.0, longitude = 20.0,
        accuracyMeters = 5f, altitudeMeters = 123.0,
        speedMps = null, bearingDegrees = null,
        acquiredAtMillis = 0L,
        source = LocationSnapshot.Source.FUSED,
        address = AddressDetails("Short", "Full", "City", "State", "Country", "XX", "0000")
    )

    @Test fun `fields hidden by template are not emitted`() {
        val context = StampContext(
            template = template { it.copy(showShortAddress = false, showFullAddress = false, showCoordinates = false, showDateTime = false) },
            captureAtMillis = 0L,
            location = sampleLocation,
            compass = null, magnetic = null, weather = null, mapBitmap = null
        )
        val lines = StampTextBuilder.build(context)
        assertThat(lines.shortAddress).isNull()
        assertThat(lines.fullAddress).isNull()
        assertThat(lines.coordinates).isNull()
        assertThat(lines.dateTime).isNull()
    }

    @Test fun `missing location produces null coordinate line even if enabled`() {
        val context = StampContext(
            template = template(),
            captureAtMillis = 0L,
            location = null,
            compass = null, magnetic = null, weather = null, mapBitmap = null
        )
        val lines = StampTextBuilder.build(context)
        assertThat(lines.coordinates).isNull()
    }
}
