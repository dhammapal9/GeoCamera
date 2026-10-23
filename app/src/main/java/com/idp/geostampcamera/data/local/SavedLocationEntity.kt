package com.idp.geostampcamera.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val shortAddress: String?,
    val fullAddress: String?,
    val createdAtMillis: Long
)
