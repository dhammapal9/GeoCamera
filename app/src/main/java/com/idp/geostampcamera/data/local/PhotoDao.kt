package com.idp.geostampcamera.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun findById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestampMillis DESC")
    fun observeWithCoordinates(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PhotoEntity): Long

    @Update
    suspend fun update(entity: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    @Query("SELECT * FROM photos WHERE shortAddress LIKE '%' || :query || '%' OR fullAddress LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY timestampMillis DESC")
    fun search(query: String): Flow<List<PhotoEntity>>
}
