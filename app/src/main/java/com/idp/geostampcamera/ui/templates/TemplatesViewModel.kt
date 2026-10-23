package com.idp.geostampcamera.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.data.repository.TemplateRepository
import com.idp.geostampcamera.domain.model.StampTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val templates: StateFlow<List<StampTemplate>> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mark [template] as the camera's default so the next capture uses it. */
    fun apply(template: StampTemplate) {
        viewModelScope.launch { settings.setDefaultTemplateId(template.id) }
    }

    fun duplicate(template: StampTemplate, newName: String) {
        viewModelScope.launch { repository.duplicate(template, newName) }
    }

    fun delete(template: StampTemplate) {
        viewModelScope.launch { if (!template.isBuiltIn) repository.deleteCustom(template.id) }
    }
}
