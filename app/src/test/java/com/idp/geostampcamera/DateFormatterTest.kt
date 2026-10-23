package com.idp.geostampcamera

import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.util.DateFormatter
import org.junit.Test
import java.util.TimeZone

class DateFormatterTest {

    @Test fun `format uses UTC when requested`() {
        val millis = 0L // 1970-01-01T00:00:00Z
        val text = DateFormatter.format(millis, "yyyy-MM-dd HH:mm", TimeZone.getTimeZone("UTC"))
        assertThat(text).isEqualTo("1970-01-01 00:00")
    }

    @Test fun `format uses IST when requested`() {
        val text = DateFormatter.format(0L, "yyyy-MM-dd HH:mm", TimeZone.getTimeZone("Asia/Kolkata"))
        assertThat(text).isEqualTo("1970-01-01 05:30")
    }
}
