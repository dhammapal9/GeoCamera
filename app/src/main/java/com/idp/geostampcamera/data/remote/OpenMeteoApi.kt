package com.idp.geostampcamera.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun currentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("current") current: String =
            "temperature_2m,relative_humidity_2m,pressure_msl,wind_speed_10m,wind_direction_10m,weather_code",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val current: Current?
) {
    @JsonClass(generateAdapter = true)
    data class Current(
        val time: String?,
        val temperature_2m: Double?,
        val relative_humidity_2m: Int?,
        val pressure_msl: Double?,
        val wind_speed_10m: Double?,
        val wind_direction_10m: Int?,
        val weather_code: Int?
    )
}
