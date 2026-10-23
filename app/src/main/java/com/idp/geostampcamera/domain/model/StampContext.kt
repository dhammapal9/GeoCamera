package com.idp.geostampcamera.domain.model

import android.graphics.Bitmap

/** Immutable snapshot handed to the renderer; nothing in here should change post-capture. */
data class StampContext(
    val template: StampTemplate,
    val captureAtMillis: Long,
    val location: LocationSnapshot?,
    val compass: CompassReading?,
    val magnetic: MagneticReading?,
    val weather: WeatherSnapshot?,
    val mapBitmap: Bitmap?
)
