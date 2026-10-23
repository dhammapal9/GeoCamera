package com.idp.geostampcamera.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val _navigateToOnboarding = MutableStateFlow(false)
    val navigateToOnboarding: StateFlow<Boolean> = _navigateToOnboarding

    init {
        viewModelScope.launch {
            val completed = settings.onboardingCompleteFlow().first()
            _navigateToOnboarding.value = !completed
        }
    }

    fun onboardingShown() { _navigateToOnboarding.value = false }
}
