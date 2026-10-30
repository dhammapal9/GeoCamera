package com.idp.geostampcamera.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.idp.geostampcamera.ads.AdsManager
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.data.repository.TemplateRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class GeoStampApp : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var templateRepository: TemplateRepository
    @Inject lateinit var adsManager: AdsManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applyThemeSync()
        appScope.launch {
            runCatching { templateRepository.ensureDefaults() }
                .onFailure { Log.w(TAG, "Default template seed failed", it) }
        }
        // Bootstraps MobileAds + preloads interstitial, rewarded, and app-open ads.
        runCatching {
            adsManager.init()
            adsManager.startAppOpenObserver(this)
        }.onFailure { Log.w(TAG, "AdsManager init failed", it) }
    }

    private fun applyThemeSync() {
        // Blocking read is intentional: the night mode must be set before any activity
        // inflates, otherwise the activity starts in system default and the saved
        // preference only takes effect after a cold restart. On Android 12+ a late
        // setDefaultNightMode() call does not reliably recreate the already-created
        // activity, so the user-visible theme can diverge from the stored preference.
        val theme = runCatching {
            runBlocking { settingsRepository.themeFlow().first() }
        }.getOrNull()
        val mode = when (theme) {
            SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        private const val TAG = "GeoStampApp"
    }
}
