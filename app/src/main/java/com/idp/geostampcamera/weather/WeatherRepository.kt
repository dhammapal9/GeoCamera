package com.idp.geostampcamera.weather

import com.idp.geostampcamera.data.remote.OpenMeteoApi
import com.idp.geostampcamera.domain.model.WeatherSnapshot
import com.idp.geostampcamera.util.AppDispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class WeatherRepository @Inject constructor(
    private val api: OpenMeteoApi,
    private val dispatchers: AppDispatchers
) {
    private data class CacheEntry(val lat: Double, val lng: Double, val fetchedAt: Long, val value: WeatherSnapshot)

    private val mutex = Mutex()
    private var cache: CacheEntry? = null

    suspend fun fetch(lat: Double, lng: Double): WeatherSnapshot? = withContext(dispatchers.io) {
        mutex.withLock {
            val existing = cache
            val ageOk = existing != null && System.currentTimeMillis() - existing.fetchedAt < CACHE_TTL_MS
            val nearSame = existing != null && abs(existing.lat - lat) < 0.05 && abs(existing.lng - lng) < 0.05
            if (ageOk && nearSame) return@withLock existing!!.value
            val fresh = runCatching { api.currentWeather(lat, lng) }.getOrNull()?.let { toSnapshot(it) }
            if (fresh != null) cache = CacheEntry(lat, lng, System.currentTimeMillis(), fresh)
            fresh
        }
    }

    private fun toSnapshot(resp: com.idp.geostampcamera.data.remote.OpenMeteoResponse): WeatherSnapshot {
        val c = resp.current
        val observedMs = c?.time?.let { parseIso(it, resp.timezone) }
        return WeatherSnapshot(
            temperatureC = c?.temperature_2m,
            humidityPercent = c?.relative_humidity_2m,
            pressureHpa = c?.pressure_msl,
            windSpeedMps = c?.wind_speed_10m,
            windDirectionDeg = c?.wind_direction_10m,
            observedAtMillis = observedMs,
            weatherCode = c?.weather_code
        )
    }

    private fun parseIso(iso: String, tz: String?): Long? = runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        sdf.timeZone = tz?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getTimeZone("UTC")
        sdf.parse(iso)?.time
    }.getOrNull()

    companion object {
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
    }
}
