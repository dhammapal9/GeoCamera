package com.idp.geostampcamera.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.data.repository.TemplateRepository
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.domain.model.CompassReading
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.MagneticReading
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.domain.model.WeatherSnapshot
import com.idp.geostampcamera.drive.DriveSyncManager
import com.idp.geostampcamera.location.LocationRepository
import com.idp.geostampcamera.map.MapSnapshotProvider
import com.idp.geostampcamera.sensors.CompassRepository
import com.idp.geostampcamera.stamp.PhotoStampRenderer
import com.idp.geostampcamera.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateRepository: TemplateRepository,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val compassRepository: CompassRepository,
    private val mapSnapshotProvider: MapSnapshotProvider,
    private val renderer: PhotoStampRenderer,
    private val driveSyncManager: DriveSyncManager
) : ViewModel() {

    private val _templates = MutableStateFlow<List<StampTemplate>>(emptyList())
    val templates: StateFlow<List<StampTemplate>> = _templates.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<StampTemplate?>(null)
    val selectedTemplate: StateFlow<StampTemplate?> = _selectedTemplate.asStateFlow()

    private val _location = MutableStateFlow<LocationSnapshot?>(null)
    val location: StateFlow<LocationSnapshot?> = _location.asStateFlow()

    private val _lastWeather = MutableStateFlow<WeatherSnapshot?>(null)
    val lastWeather: StateFlow<WeatherSnapshot?> = _lastWeather.asStateFlow()

    private val _lastCompass = MutableStateFlow<CompassReading?>(null)
    val lastCompass: StateFlow<CompassReading?> = _lastCompass.asStateFlow()
    private val _lastMagnetic = MutableStateFlow<MagneticReading?>(null)
    val lastMagnetic: StateFlow<MagneticReading?> = _lastMagnetic.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CameraEvent> = _events

    init {
        viewModelScope.launch {
            // Combine so a change in EITHER the template list OR the stored default id
            // re-picks the active template. Without this, applying a template from
            // TemplatesFragment doesn't reach the camera (the init block only read the
            // default id once, when _selectedTemplate was first null).
            kotlinx.coroutines.flow.combine(
                templateRepository.observeAll(),
                settingsRepository.defaultTemplateIdFlow()
            ) { list, defaultId -> list to defaultId }
                .collect { (list, defaultId) ->
                    _templates.value = list
                    // Prefer the stored default id (updated by Camera carousel selection,
                    // TemplatesFragment.onApply, or any other call to setDefaultTemplateId).
                    // Fall back to the currently-selected id, then the first available template.
                    val targetId = defaultId ?: _selectedTemplate.value?.id
                    val target = list.firstOrNull { it.id == targetId } ?: list.firstOrNull()
                    // Compare by instance (data class equality) so edits to the same template
                    // id are also reflected — not just id changes.
                    if (target != null && target != _selectedTemplate.value) {
                        _selectedTemplate.value = target
                    }
                }
        }
        viewModelScope.launch {
            compassRepository.compassFlow().collect { _lastCompass.value = it }
        }
        viewModelScope.launch {
            compassRepository.magneticFlow().collect { _lastMagnetic.value = it }
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            val snap = locationRepository.currentLocation()
            // A manual pick is sticky — don't let a stale/in-flight GPS fetch (e.g. the one
            // kicked off in CameraFragment.onViewCreated after returning from LocationPicker)
            // overwrite the point the user explicitly chose. If they want GPS back they can
            // re-open the picker and tap "Use current location".
            if (_location.value?.source == LocationSnapshot.Source.MANUAL) return@launch
            if (snap == null) {
                _location.value = null
                return@launch
            }
            val address = locationRepository.reverseGeocode(snap.latitude, snap.longitude)
            _location.value = snap.copy(address = address)
            _lastWeather.value = weatherRepository.fetch(snap.latitude, snap.longitude)
        }
    }

    fun selectTemplate(template: StampTemplate) {
        _selectedTemplate.value = template
        viewModelScope.launch { settingsRepository.setDefaultTemplateId(template.id) }
    }

    fun setManualLocation(lat: Double, lng: Double) {
        // Emit the fresh coordinates up-front so the camera location pill, stamp preview,
        // and map thumbnail refresh instantly. Reverse-geocode + weather enrich the snapshot
        // on their own coroutines — the UI gets a second emission when the address arrives.
        val now = System.currentTimeMillis()
        _location.value = LocationSnapshot(
            latitude = lat, longitude = lng,
            accuracyMeters = null, altitudeMeters = null,
            speedMps = null, bearingDegrees = null,
            acquiredAtMillis = now,
            source = LocationSnapshot.Source.MANUAL,
            address = null
        )
        _lastWeather.value = null
        viewModelScope.launch {
            val address = locationRepository.reverseGeocode(lat, lng)
            // Only patch the snapshot if the user hasn't picked yet-another location meanwhile.
            _location.value?.takeIf { it.latitude == lat && it.longitude == lng }?.let {
                _location.value = it.copy(address = address)
            }
        }
        viewModelScope.launch {
            _lastWeather.value = weatherRepository.fetch(lat, lng)
        }
    }

    fun onCapturedToFile(
        file: File,
        projectOverride: String? = null,
        noteOverride: String? = null
    ) {
        val template = _selectedTemplate.value ?: run {
            _events.tryEmit(CameraEvent.CaptureFailed("No template selected"))
            return
        }
        val currentLocation = _location.value
        val capturedAt = System.currentTimeMillis()
        _captureState.value = CaptureState.Processing
        viewModelScope.launch {
            runCatching {
                val keepOriginal = settingsRepository.keepOriginalFlow().first()
                val mapType = settingsRepository.mapTypeFlow().first()
                val mapBitmap: Bitmap? = if (template.showMap && currentLocation != null) {
                    val mapSize = (720 * 0.19f).toInt().coerceAtLeast(200)
                    runCatching {
                        mapSnapshotProvider.snapshot(
                            lat = currentLocation.latitude,
                            lng = currentLocation.longitude,
                            widthPx = mapSize,
                            heightPx = mapSize,
                            mapType = mapSnapshotProvider.mapTypeFromTemplate(template, mapType)
                        )
                    }.getOrNull() ?: mapSnapshotProvider.placeholder(mapSize, mapSize)
                } else null
                val effectiveTemplate = template.copy(
                    projectName = projectOverride?.takeIf { it.isNotBlank() } ?: template.projectName,
                    note = noteOverride?.takeIf { it.isNotBlank() } ?: template.note
                )
                val stamp = StampContext(
                    template = effectiveTemplate,
                    captureAtMillis = capturedAt,
                    location = currentLocation,
                    compass = _lastCompass.value,
                    magnetic = _lastMagnetic.value,
                    weather = _lastWeather.value,
                    mapBitmap = mapBitmap
                )
                val result = renderer.render(
                    sourceFile = file,
                    stamp = stamp,
                    keepOriginal = keepOriginal
                )
                maybeSyncToDrive(result.stampedFile)
                val photo = CapturedPhoto(
                    id = 0,
                    originalPath = if (keepOriginal) file.absolutePath else null,
                    stampedPath = result.stampedFile.absolutePath,
                    publicUri = result.publicUri?.toString(),
                    timestampMillis = capturedAt,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    shortAddress = currentLocation?.address?.shortAddress,
                    fullAddress = currentLocation?.address?.fullAddress,
                    accuracyMeters = currentLocation?.accuracyMeters,
                    altitudeMeters = currentLocation?.altitudeMeters,
                    templateId = template.id,
                    note = (noteOverride ?: template.note).ifBlank { null },
                    projectName = (projectOverride ?: template.projectName).ifBlank { null },
                    mapType = mapType,
                    widthPx = result.widthPx,
                    heightPx = result.heightPx,
                    status = CapturedPhoto.Status.READY
                )
                val id = photoRepository.insert(photo)
                _events.emit(CameraEvent.CaptureSaved(id))
            }.onFailure {
                _events.emit(CameraEvent.CaptureFailed(it.message ?: "Save failed"))
            }
            _captureState.value = CaptureState.Idle
        }
    }

    private suspend fun maybeSyncToDrive(stampedFile: File) {
        val location = settingsRepository.storageLocationFlow().first()
        if (location != SettingsRepository.STORAGE_DRIVE) return
        when (val result = driveSyncManager.upload(stampedFile)) {
            is DriveSyncManager.Result.Success -> Unit
            DriveSyncManager.Result.NotConnected -> _events.emit(CameraEvent.DriveReauthNeeded)
            is DriveSyncManager.Result.NeedsReauth -> {
                settingsRepository.clearDriveAccount()
                _events.emit(CameraEvent.DriveReauthNeeded)
            }
            is DriveSyncManager.Result.Failure -> _events.emit(CameraEvent.DriveSyncFailed(result.message))
        }
    }

    sealed class CaptureState {
        data object Idle : CaptureState()
        data object Processing : CaptureState()
    }

    sealed class CameraEvent {
        data class CaptureSaved(val photoId: Long) : CameraEvent()
        data class CaptureFailed(val message: String) : CameraEvent()
        data object DriveReauthNeeded : CameraEvent()
        data class DriveSyncFailed(val message: String) : CameraEvent()
    }
}
