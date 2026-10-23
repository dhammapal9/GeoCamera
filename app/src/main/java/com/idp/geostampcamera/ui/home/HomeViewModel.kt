package com.idp.geostampcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.data.repository.TemplateRepository
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.domain.model.StampTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    photoRepository: PhotoRepository,
    templateRepository: TemplateRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val recent: StateFlow<List<CapturedPhoto>> =
        photoRepository.observeRecent(limit = 10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<StampTemplate>> =
        templateRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastLocation: StateFlow<Triple<Double, Double, Long>?> =
        settingsRepository.lastKnownLocationFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
