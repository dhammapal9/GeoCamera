package com.idp.geostampcamera.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context
) {
    private val keyTheme = stringPreferencesKey("theme")
    private val keyPhotoQuality = stringPreferencesKey("photoQuality")
    private val keyCoordFormat = stringPreferencesKey("coordFormat")
    private val keyDefaultTemplateId = stringPreferencesKey("defaultTemplateId")
    private val keyMapTypeDefault = stringPreferencesKey("mapTypeDefault")
    private val keyKeepOriginal = booleanPreferencesKey("keepOriginal")
    private val keyShutterSound = booleanPreferencesKey("shutterSound")
    private val keyVolumeAction = stringPreferencesKey("volumeAction")
    private val keyOnboardingComplete = booleanPreferencesKey("onboardingComplete")
    private val keyLastLat = stringPreferencesKey("lastLat")
    private val keyLastLng = stringPreferencesKey("lastLng")
    private val keyLastAcquired = intPreferencesKey("lastAcquired")
    private val keyAdsConsent = booleanPreferencesKey("adsConsent")
    private val keyFilenameOrder = stringPreferencesKey("filenameOrder")
    private val keyFilenameCustom = stringPreferencesKey("filenameCustom")
    private val keyStampOrientation = stringPreferencesKey("stampOrientation")
    private val keyShowAppLogoOnStamp = booleanPreferencesKey("showAppLogoOnStamp")
    private val keyShowCameraIndicator = booleanPreferencesKey("showCameraIndicator")
    private val keyEmailAfterCapture = booleanPreferencesKey("emailAfterCapture")
    private val keyShareAfterCapture = booleanPreferencesKey("shareAfterCapture")
    private val keyStorageLocation = stringPreferencesKey("storageLocation")
    private val keyStorageDriveTreeUri = stringPreferencesKey("storageDriveTreeUri") // retained only to purge stale SAF URIs from legacy installs
    private val keyDriveAccountEmail = stringPreferencesKey("driveAccountEmail")
    private val keyDriveFolderId = stringPreferencesKey("driveFolderId")
    private val keyIsPro = booleanPreferencesKey("isPro")

    fun themeFlow(): Flow<String> = context.settingsStore.data.map { it[keyTheme] ?: THEME_SYSTEM }

    suspend fun setTheme(value: String) {
        context.settingsStore.edit { it[keyTheme] = value }
    }

    fun photoQualityFlow(): Flow<String> = context.settingsStore.data.map { it[keyPhotoQuality] ?: QUALITY_HIGH }
    suspend fun setPhotoQuality(value: String) { context.settingsStore.edit { it[keyPhotoQuality] = value } }

    fun coordFormatFlow(): Flow<String> = context.settingsStore.data.map { it[keyCoordFormat] ?: COORD_DECIMAL }
    suspend fun setCoordFormat(value: String) { context.settingsStore.edit { it[keyCoordFormat] = value } }

    fun defaultTemplateIdFlow(): Flow<String?> = context.settingsStore.data.map { it[keyDefaultTemplateId] }
    suspend fun setDefaultTemplateId(value: String?) {
        context.settingsStore.edit { prefs ->
            if (value == null) prefs.remove(keyDefaultTemplateId) else prefs[keyDefaultTemplateId] = value
        }
    }

    fun mapTypeFlow(): Flow<String> = context.settingsStore.data.map { it[keyMapTypeDefault] ?: MAP_NORMAL }
    suspend fun setMapType(value: String) { context.settingsStore.edit { it[keyMapTypeDefault] = value } }

    fun keepOriginalFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyKeepOriginal] ?: false }
    suspend fun setKeepOriginal(value: Boolean) { context.settingsStore.edit { it[keyKeepOriginal] = value } }

    fun shutterSoundFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyShutterSound] ?: true }
    suspend fun setShutterSound(value: Boolean) { context.settingsStore.edit { it[keyShutterSound] = value } }

    fun volumeActionFlow(): Flow<String> = context.settingsStore.data.map { it[keyVolumeAction] ?: VOLUME_CAPTURE }
    suspend fun setVolumeAction(value: String) { context.settingsStore.edit { it[keyVolumeAction] = value } }

    fun onboardingCompleteFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyOnboardingComplete] ?: false }
    suspend fun setOnboardingComplete(value: Boolean) { context.settingsStore.edit { it[keyOnboardingComplete] = value } }

    fun lastKnownLocationFlow(): Flow<Triple<Double, Double, Long>?> = context.settingsStore.data.map {
        val lat = it[keyLastLat]?.toDoubleOrNull()
        val lng = it[keyLastLng]?.toDoubleOrNull()
        val ts = it[keyLastAcquired]?.toLong()
        if (lat != null && lng != null && ts != null) Triple(lat, lng, ts) else null
    }
    suspend fun setLastKnownLocation(lat: Double, lng: Double, acquiredAt: Long) {
        context.settingsStore.edit {
            it[keyLastLat] = lat.toString()
            it[keyLastLng] = lng.toString()
            it[keyLastAcquired] = (acquiredAt / 1000).toInt()
        }
    }

    fun adsConsentFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyAdsConsent] ?: false }
    suspend fun setAdsConsent(value: Boolean) { context.settingsStore.edit { it[keyAdsConsent] = value } }

    fun filenameConfigFlow(): Flow<Pair<List<com.idp.geostampcamera.util.FilenameBuilder.Token>, String>?> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[keyFilenameOrder] ?: return@map null
            val tokens = raw.split(",").mapNotNull { key ->
                runCatching { com.idp.geostampcamera.util.FilenameBuilder.Token.values().first { it.key == key } }.getOrNull()
            }
            tokens to (prefs[keyFilenameCustom] ?: "")
        }

    suspend fun setFilenameConfig(order: List<com.idp.geostampcamera.util.FilenameBuilder.Token>, custom: String) {
        context.settingsStore.edit {
            it[keyFilenameOrder] = order.joinToString(",") { t -> t.key }
            it[keyFilenameCustom] = custom
        }
    }

    fun stampOrientationFlow(): Flow<String> = context.settingsStore.data.map { it[keyStampOrientation] ?: STAMP_AUTO }
    suspend fun setStampOrientation(value: String) { context.settingsStore.edit { it[keyStampOrientation] = value } }

    fun showAppLogoOnStampFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyShowAppLogoOnStamp] ?: true }
    suspend fun setShowAppLogoOnStamp(value: Boolean) { context.settingsStore.edit { it[keyShowAppLogoOnStamp] = value } }

    fun showCameraIndicatorFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyShowCameraIndicator] ?: false }
    suspend fun setShowCameraIndicator(value: Boolean) { context.settingsStore.edit { it[keyShowCameraIndicator] = value } }

    fun emailAfterCaptureFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyEmailAfterCapture] ?: false }
    suspend fun setEmailAfterCapture(value: Boolean) { context.settingsStore.edit { it[keyEmailAfterCapture] = value } }

    fun shareAfterCaptureFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyShareAfterCapture] ?: false }
    suspend fun setShareAfterCapture(value: Boolean) { context.settingsStore.edit { it[keyShareAfterCapture] = value } }

    fun storageLocationFlow(): Flow<String> = context.settingsStore.data.map { it[keyStorageLocation] ?: STORAGE_PHONE }
    suspend fun setStorageLocation(value: String) { context.settingsStore.edit { it[keyStorageLocation] = value } }

    fun driveAccountEmailFlow(): Flow<String?> = context.settingsStore.data.map { it[keyDriveAccountEmail] }
    suspend fun setDriveAccountEmail(value: String?) {
        context.settingsStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyDriveAccountEmail) else prefs[keyDriveAccountEmail] = value
        }
    }

    fun driveFolderIdFlow(): Flow<String?> = context.settingsStore.data.map { it[keyDriveFolderId] }
    suspend fun setDriveFolderId(value: String?) {
        context.settingsStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyDriveFolderId) else prefs[keyDriveFolderId] = value
        }
    }

    suspend fun clearDriveAccount() {
        context.settingsStore.edit { prefs ->
            prefs.remove(keyDriveAccountEmail)
            prefs.remove(keyDriveFolderId)
            prefs.remove(keyStorageDriveTreeUri)
        }
    }

    fun isProFlow(): Flow<Boolean> = context.settingsStore.data.map { it[keyIsPro] ?: false }
    suspend fun setIsPro(value: Boolean) { context.settingsStore.edit { it[keyIsPro] = value } }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val QUALITY_HIGH = "high"
        const val QUALITY_MEDIUM = "medium"
        const val QUALITY_LOW = "low"

        const val COORD_DECIMAL = "decimal"
        const val COORD_DMS = "dms"

        const val MAP_NORMAL = "normal"
        const val MAP_SATELLITE = "satellite"
        const val MAP_TERRAIN = "terrain"
        const val MAP_HYBRID = "hybrid"

        const val VOLUME_CAPTURE = "capture"
        const val VOLUME_ZOOM = "zoom"
        const val VOLUME_NONE = "none"

        const val STAMP_AUTO = "auto"
        const val STAMP_PORTRAIT = "portrait"
        const val STAMP_LANDSCAPE = "landscape"

        const val STORAGE_PHONE = "phone"
        const val STORAGE_DRIVE = "drive"
    }
}
