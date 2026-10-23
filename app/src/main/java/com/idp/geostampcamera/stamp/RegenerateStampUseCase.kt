package com.idp.geostampcamera.stamp

import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.data.repository.TemplateRepository
import com.idp.geostampcamera.domain.model.AddressDetails
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.map.MapSnapshotProvider
import com.idp.geostampcamera.weather.WeatherRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class RegenerateStampUseCase @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val renderer: PhotoStampRenderer,
    private val mapSnapshotProvider: MapSnapshotProvider,
    private val weatherRepository: WeatherRepository
) {

    suspend operator fun invoke(photoId: Long, templateId: String): Result<CapturedPhoto> = runCatching {
        val existing = photoRepository.get(photoId) ?: error("Photo not found")
        val source = existing.originalPath?.let { File(it) }?.takeIf { it.exists() }
            ?: existing.stampedPath?.let { File(it) }?.takeIf { it.exists() }
            ?: error("Source file unavailable")
        val template = templateRepository.get(templateId) ?: error("Template not found")
        val location = if (existing.latitude != null && existing.longitude != null) {
            LocationSnapshot(
                latitude = existing.latitude,
                longitude = existing.longitude,
                accuracyMeters = existing.accuracyMeters,
                altitudeMeters = existing.altitudeMeters,
                speedMps = null,
                bearingDegrees = null,
                acquiredAtMillis = existing.timestampMillis,
                source = LocationSnapshot.Source.MANUAL,
                address = AddressDetails(
                    shortAddress = existing.shortAddress.orEmpty(),
                    fullAddress = existing.fullAddress.orEmpty(),
                    city = null, state = null, country = null, countryCode = null, postalCode = null
                )
            )
        } else null
        val mapType = settingsRepository.mapTypeFlow().first()
        val mapBitmap = if (template.showMap && location != null) {
            val size = 320
            runCatching {
                mapSnapshotProvider.snapshot(
                    location.latitude, location.longitude, size, size,
                    mapSnapshotProvider.mapTypeFromTemplate(template, mapType)
                )
            }.getOrNull() ?: mapSnapshotProvider.placeholder(size, size)
        } else null
        val weather = if (template.showWeather && location != null)
            weatherRepository.fetch(location.latitude, location.longitude) else null
        val stamp = StampContext(
            template = template,
            captureAtMillis = existing.timestampMillis,
            location = location,
            compass = null, magnetic = null, weather = weather,
            mapBitmap = mapBitmap
        )
        val keepOriginal = settingsRepository.keepOriginalFlow().first()
        val result = renderer.render(source, stamp, keepOriginal)
        val updated = existing.copy(
            stampedPath = result.stampedFile.absolutePath,
            publicUri = result.publicUri?.toString() ?: existing.publicUri,
            templateId = template.id,
            widthPx = result.widthPx,
            heightPx = result.heightPx,
            status = CapturedPhoto.Status.READY
        )
        photoRepository.update(updated)
        updated
    }
}
