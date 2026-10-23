package com.idp.geostampcamera

import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.util.PlusCodes
import org.junit.Test

class PlusCodesTest {

    @Test fun `encode returns separator in middle`() {
        val code = PlusCodes.encode(47.0, 8.0)
        assertThat(code).contains("+")
        assertThat(code.length).isAtLeast(10)
    }

    @Test fun `encode returns empty for invalid coordinates`() {
        assertThat(PlusCodes.encode(99.0, 0.0)).isEmpty()
    }

    @Test fun `cardinal from bearing wraps correctly`() {
        assertThat(PlusCodes.cardinalFromBearing(0f)).isEqualTo("N")
        assertThat(PlusCodes.cardinalFromBearing(45f)).isEqualTo("NE")
        assertThat(PlusCodes.cardinalFromBearing(90f)).isEqualTo("E")
        assertThat(PlusCodes.cardinalFromBearing(180f)).isEqualTo("S")
        assertThat(PlusCodes.cardinalFromBearing(270f)).isEqualTo("W")
        assertThat(PlusCodes.cardinalFromBearing(359f)).isEqualTo("N")
        assertThat(PlusCodes.cardinalFromBearing(-45f)).isEqualTo("NW")
    }
}
