package com.idp.geostampcamera.ui.mapview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.domain.model.CapturedPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    photoRepository: PhotoRepository
) : ViewModel() {
    val photos: StateFlow<List<CapturedPhoto>> =
        photoRepository.observeWithCoordinates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
