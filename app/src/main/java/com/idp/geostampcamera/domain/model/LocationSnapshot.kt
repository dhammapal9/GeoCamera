package com.idp.geostampcamera.domain.model

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val acquiredAtMillis: Long,
    val source: Source,
    val address: AddressDetails? = null
) {
    enum class Source { FUSED, LAST_KNOWN, MANUAL, LEGACY_GPS }

    val isStale: Boolean
        get() = System.currentTimeMillis() - acquiredAtMillis > STALE_THRESHOLD_MS

    companion object {
        const val STALE_THRESHOLD_MS = 2L * 60L * 1000L
    }
}

data class AddressDetails(
    val shortAddress: String,
    val fullAddress: String,
    val city: String?,
    val state: String?,
    val country: String?,
    val countryCode: String?,
    val postalCode: String?
)
