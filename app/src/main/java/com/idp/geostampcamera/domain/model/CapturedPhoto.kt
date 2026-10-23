package com.idp.geostampcamera.domain.model

data class CapturedPhoto(
    val id: Long,
    val originalPath: String?,
    val stampedPath: String?,
    val publicUri: String?,
    val timestampMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val shortAddress: String?,
    val fullAddress: String?,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val templateId: String?,
    val note: String?,
    val projectName: String?,
    val mapType: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val status: Status
) {
    enum class Status { PENDING, PROCESSING, READY, FAILED }
}
