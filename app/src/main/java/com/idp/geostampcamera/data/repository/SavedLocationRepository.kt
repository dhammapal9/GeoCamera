package com.idp.geostampcamera.data.repository

import com.idp.geostampcamera.data.local.SavedLocationDao
import com.idp.geostampcamera.data.local.SavedLocationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedLocationRepository @Inject constructor(
    private val dao: SavedLocationDao
) {
    fun observeAll(): Flow<List<SavedLocationEntity>> = dao.observeAll()
    suspend fun save(entity: SavedLocationEntity): Long = dao.insert(entity)
    suspend fun delete(id: Long) = dao.delete(id)
}
