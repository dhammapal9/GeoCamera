package com.idp.geostampcamera.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val status: String
)
