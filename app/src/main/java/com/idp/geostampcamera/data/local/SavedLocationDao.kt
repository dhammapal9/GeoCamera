package com.idp.geostampcamera.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<SavedLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedLocationEntity): Long

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun delete(id: Long)
}
