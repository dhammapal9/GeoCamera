package com.idp.geostampcamera.domain.model

data class CompassReading(
    val headingDegrees: Float,
    val cardinal: String,
    val isReliable: Boolean
)

data class MagneticReading(
    val microteslas: Float,
    val isReliable: Boolean
)
