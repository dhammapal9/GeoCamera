package com.idp.geostampcamera.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY isBuiltIn DESC, updatedAtMillis DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun findById(id: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TemplateEntity>)

    @Query("DELETE FROM templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: String)
}
