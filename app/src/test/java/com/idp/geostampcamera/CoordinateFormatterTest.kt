package com.idp.geostampcamera

import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.util.CoordinateFormatter
import org.junit.Test

class CoordinateFormatterTest {

    @Test fun `decimal format uses six digits and hemisphere letters`() {
        val text = CoordinateFormatter.formatDecimal(18.626936, 73.710798)
        assertThat(text).isEqualTo("18.626936° N, 73.710798° E")
    }

    @Test fun `southern and western hemispheres use S and W`() {
        val text = CoordinateFormatter.formatDecimal(-33.868820, -70.642540)
        assertThat(text).contains("° S")
        assertThat(text).contains("° W")
    }

    @Test fun `DMS format uses degree minute second markers`() {
        val text = CoordinateFormatter.formatDms(18.626936, 73.710798)
        assertThat(text).contains("°")
        assertThat(text).contains("'")
        assertThat(text).contains("\"")
        assertThat(text).contains("N")
        assertThat(text).contains("E")
    }

    @Test fun `validation rejects out-of-range values`() {
        assertThat(CoordinateFormatter.isValid(91.0, 0.0)).isFalse()
        assertThat(CoordinateFormatter.isValid(-90.1, 0.0)).isFalse()
        assertThat(CoordinateFormatter.isValid(0.0, 180.1)).isFalse()
        assertThat(CoordinateFormatter.isValid(0.0, -180.1)).isFalse()
        assertThat(CoordinateFormatter.isValid(90.0, 180.0)).isTrue()
    }

    @Test fun `parseDecimal returns null for invalid text`() {
        assertThat(CoordinateFormatter.parseDecimal("abc", "12.3")).isNull()
        assertThat(CoordinateFormatter.parseDecimal("12.3", "")).isNull()
        assertThat(CoordinateFormatter.parseDecimal("12.3", "999")).isNull()
    }

    @Test fun `parseDecimal returns parsed pair for valid text`() {
        val parsed = CoordinateFormatter.parseDecimal("12.3", "45.6")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.first).isEqualTo(12.3)
        assertThat(parsed.second).isEqualTo(45.6)
    }
}
