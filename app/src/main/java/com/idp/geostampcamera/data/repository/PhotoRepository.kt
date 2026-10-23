package com.idp.geostampcamera.data.repository

import com.idp.geostampcamera.data.local.PhotoDao
import com.idp.geostampcamera.data.local.toDomain
import com.idp.geostampcamera.data.local.toEntity
import com.idp.geostampcamera.domain.model.CapturedPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val dao: PhotoDao
) {
    fun observeAll(): Flow<List<CapturedPhoto>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 10): Flow<List<CapturedPhoto>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeWithCoordinates(): Flow<List<CapturedPhoto>> =
        dao.observeWithCoordinates().map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<CapturedPhoto>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): CapturedPhoto? = dao.findById(id)?.toDomain()

    suspend fun insert(photo: CapturedPhoto): Long = dao.insert(photo.toEntity())
    suspend fun update(photo: CapturedPhoto) = dao.update(photo.toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
}
