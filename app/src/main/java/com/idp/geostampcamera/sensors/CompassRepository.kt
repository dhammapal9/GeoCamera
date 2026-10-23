package com.idp.geostampcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.idp.geostampcamera.domain.model.CompassReading
import com.idp.geostampcamera.domain.model.MagneticReading
import com.idp.geostampcamera.util.PlusCodes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompassRepository @Inject constructor(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    val hasAccelerometer: Boolean get() =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    val hasMagnetic: Boolean get() =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

    fun compassFlow(): Flow<CompassReading> = callbackFlow {
        val sm = sensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sm == null || accel == null || mag == null) {
            trySend(CompassReading(0f, "N", isReliable = false))
            awaitClose { }
            return@callbackFlow
        }
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        val R = FloatArray(9)
        val I = FloatArray(9)
        val orientation = FloatArray(3)
        var reliable = true

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> smoothCopy(gravity, e.values)
                    Sensor.TYPE_MAGNETIC_FIELD -> smoothCopy(geomag, e.values)
                }
                if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                    SensorManager.getOrientation(R, orientation)
                    val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val norm = ((azimuthDeg % 360) + 360) % 360
                    trySend(CompassReading(norm, PlusCodes.cardinalFromBearing(norm), reliable))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()

    fun magneticFlow(): Flow<MagneticReading> = callbackFlow {
        val sm = sensorManager
        val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sm == null || mag == null) {
            trySend(MagneticReading(0f, isReliable = false))
            awaitClose { }
            return@callbackFlow
        }
        var reliable = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val m = kotlin.math.sqrt(
                    e.values[0] * e.values[0] +
                        e.values[1] * e.values[1] +
                        e.values[2] * e.values[2]
                )
                trySend(MagneticReading(m, reliable))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }
        sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()

    private fun smoothCopy(dst: FloatArray, src: FloatArray) {
        val alpha = 0.3f
        for (i in 0..2) dst[i] = dst[i] + alpha * (src[i] - dst[i])
    }
}
