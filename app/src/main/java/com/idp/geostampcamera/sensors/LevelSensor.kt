package com.idp.geostampcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

/** Signed roll angle in degrees relative to the horizon. 0 = perfectly level. */
@Singleton
class LevelSensor @Inject constructor(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    val isAvailable: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    fun rollDegrees(): Flow<Float> = callbackFlow {
        val sm = sensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) { trySend(0f); awaitClose { }; return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val roll = Math.toDegrees(Math.atan2(-x.toDouble(), y.toDouble())).toFloat()
                trySend(roll)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()
}
