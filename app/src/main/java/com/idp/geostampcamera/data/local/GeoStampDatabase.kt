package com.idp.geostampcamera.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class, TemplateEntity::class, SavedLocationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class GeoStampDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        const val NAME = "geostamp.db"
    }
}
