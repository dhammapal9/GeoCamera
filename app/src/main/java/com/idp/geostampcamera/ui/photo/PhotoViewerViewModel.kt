package com.idp.geostampcamera.ui.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.stamp.RegenerateStampUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val regenerateStamp: RegenerateStampUseCase
) : ViewModel() {
    private val _photo = MutableStateFlow<CapturedPhoto?>(null)
    val photo: StateFlow<CapturedPhoto?> = _photo

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    sealed class Event {
        data class Regenerated(val id: Long) : Event()
        data class Error(val message: String) : Event()
    }

    fun load(id: Long) {
        viewModelScope.launch { _photo.value = photoRepository.get(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { photoRepository.delete(id) }
    }

    fun regenerateWithCurrentDefault() {
        val photo = _photo.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val templateId = settingsRepository.defaultTemplateIdFlow().first()
                ?: photo.templateId
            if (templateId == null) {
                _events.emit(Event.Error("No template selected"))
                _busy.value = false
                return@launch
            }
            val result = regenerateStamp(photo.id, templateId)
            result.onSuccess {
                _photo.value = it
                _events.emit(Event.Regenerated(it.id))
            }.onFailure {
                _events.emit(Event.Error(it.message ?: "Failed to regenerate"))
            }
            _busy.value = false
        }
    }
}
