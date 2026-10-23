package com.idp.geostampcamera.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.domain.model.CapturedPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    enum class Sort { NEWEST, OLDEST }

    /** Which tab the selection mode is scoped to, or null if not in selection mode. */
    enum class Scope { PHOTOS, VIDEOS }

    private val query = MutableStateFlow("")
    private val _sort = MutableStateFlow(Sort.NEWEST)
    val sortOrder: StateFlow<Sort> = _sort

    private val _selectionScope = MutableStateFlow<Scope?>(null)
    val selectionScope: StateFlow<Scope?> = _selectionScope.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedVideoIds: StateFlow<Set<Long>> = _selectedVideoIds.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<CapturedPhoto>> =
        combine(
            query.flatMapLatest { q ->
                if (q.isBlank()) photoRepository.observeAll() else photoRepository.search(q)
            },
            _sort
        ) { list, order ->
            when (order) {
                Sort.NEWEST -> list.sortedByDescending { it.timestampMillis }
                Sort.OLDEST -> list.sortedBy { it.timestampMillis }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(text: String) { query.value = text }
    fun setSort(order: Sort) { _sort.value = order }

    fun delete(id: Long) { viewModelScope.launch { photoRepository.delete(id) } }

    // ---------- Selection mode ----------

    fun togglePhotoSelection(id: Long) {
        _selectionScope.value = Scope.PHOTOS
        _selectedPhotoIds.value = _selectedPhotoIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
        if (_selectedPhotoIds.value.isEmpty()) exitSelection()
    }

    fun toggleVideoSelection(id: Long) {
        _selectionScope.value = Scope.VIDEOS
        _selectedVideoIds.value = _selectedVideoIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
        if (_selectedVideoIds.value.isEmpty()) exitSelection()
    }

    fun selectAllPhotos(ids: Collection<Long>) {
        _selectionScope.value = Scope.PHOTOS
        _selectedPhotoIds.value = ids.toSet()
    }

    fun selectAllVideos(ids: Collection<Long>) {
        _selectionScope.value = Scope.VIDEOS
        _selectedVideoIds.value = ids.toSet()
    }

    fun exitSelection() {
        _selectionScope.value = null
        _selectedPhotoIds.value = emptySet()
        _selectedVideoIds.value = emptySet()
    }

    /** Deletes every photo currently in the selection set, then exits selection mode. */
    fun deleteSelectedPhotos() {
        val ids = _selectedPhotoIds.value.toList()
        if (ids.isEmpty()) { exitSelection(); return }
        viewModelScope.launch {
            ids.forEach { photoRepository.delete(it) }
            exitSelection()
        }
    }

    /**
     * Deletes selected MediaStore videos via ContentResolver. On API 29+ this will throw
     * [android.app.RecoverableSecurityException] when the user hasn't granted write access
     * to the picked items; the fragment catches it and launches the system consent intent.
     */
    fun deleteSelectedVideos(onResult: (Int, List<Uri>) -> Unit) {
        val ids = _selectedVideoIds.value.toList()
        if (ids.isEmpty()) { exitSelection(); return }
        viewModelScope.launch {
            val baseUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uris = ids.map { ContentUris.withAppendedId(baseUri, it) }
            val deleted = withContext(Dispatchers.IO) {
                var count = 0
                uris.forEach { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                        .onSuccess { if (it > 0) count++ }
                }
                count
            }
            exitSelection()
            onResult(deleted, uris)
        }
    }
}
