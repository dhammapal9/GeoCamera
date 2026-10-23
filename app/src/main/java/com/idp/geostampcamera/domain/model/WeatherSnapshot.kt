package com.idp.geostampcamera.domain.model

data class WeatherSnapshot(
    val temperatureC: Double?,
    val humidityPercent: Int?,
    val pressureHpa: Double?,
    val windSpeedMps: Double?,
    val windDirectionDeg: Int?,
    val observedAtMillis: Long?,
    val weatherCode: Int?
)
