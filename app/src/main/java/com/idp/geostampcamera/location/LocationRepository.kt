package com.idp.geostampcamera.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.domain.model.AddressDetails
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.util.AppDispatchers
import com.idp.geostampcamera.util.PermissionUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationRepository @Inject constructor(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val settingsRepository: SettingsRepository
) {

    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val persistScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    fun hasPermission(): Boolean = PermissionUtils.hasAnyLocation(context)

    fun isLocationEnabled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) { false }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun currentLocation(timeoutMs: Long = 10_000): LocationSnapshot? {
        if (!hasPermission() || !isLocationEnabled()) return lastKnown()
        val fresh = withTimeoutOrNull(timeoutMs) { requestFused() }
        return fresh ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFused(): LocationSnapshot? = suspendCancellableCoroutine { cont: CancellableContinuation<LocationSnapshot?> ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .build()
        fused.getCurrentLocation(request, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    persistLastKnown(loc)
                    cont.resume(toSnapshot(loc, LocationSnapshot.Source.FUSED))
                } else cont.resume(null)
            }
            .addOnFailureListener { cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): LocationSnapshot? = withContext(dispatchers.io) {
        if (hasPermission()) {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            val best = providers.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null) {
                persistLastKnown(best)
                return@withContext toSnapshot(best, LocationSnapshot.Source.LAST_KNOWN)
            }
        }
        storedLast()
    }

    suspend fun storedLast(): LocationSnapshot? {
        val triple = runCatching { settingsRepository.lastKnownLocationFlow().first() }.getOrNull() ?: return null
        val (lat, lng, ts) = triple
        return LocationSnapshot(
            latitude = lat, longitude = lng,
            accuracyMeters = null, altitudeMeters = null,
            speedMps = null, bearingDegrees = null,
            acquiredAtMillis = ts, source = LocationSnapshot.Source.LAST_KNOWN
        )
    }

    private fun persistLastKnown(location: Location) {
        persistScope.launch {
            runCatching {
                settingsRepository.setLastKnownLocation(
                    location.latitude, location.longitude, location.time
                )
            }
        }
    }

    private fun toSnapshot(location: Location, source: LocationSnapshot.Source): LocationSnapshot =
        LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            acquiredAtMillis = if (location.time > 0) location.time else System.currentTimeMillis(),
            source = source
        )

    suspend fun reverseGeocode(lat: Double, lng: Double): AddressDetails? = withContext(dispatchers.io) {
        if (!Geocoder.isPresent()) return@withContext null
        val geo = Geocoder(context, Locale.getDefault())
        runCatching {
            @Suppress("DEPRECATION")
            geo.getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()?.let { address ->
            val lines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }
            val full = lines.joinToString(separator = ", ").ifBlank { address.featureName.orEmpty() }
            val short = listOfNotNull(address.locality, address.adminArea, address.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { address.featureName.orEmpty() }
            AddressDetails(
                shortAddress = short,
                fullAddress = full,
                city = address.locality,
                state = address.adminArea,
                country = address.countryName,
                countryCode = address.countryCode,
                postalCode = address.postalCode
            )
        }
    }
}
