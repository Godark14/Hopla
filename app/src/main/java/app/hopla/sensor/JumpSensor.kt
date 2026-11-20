package app.hopla.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AccelSample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

/** Source de données accéléromètre exposée en Flow (≈100 Hz). */
class JumpSensor(context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = accelerometer != null

    fun samples(): Flow<AccelSample> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            close(IllegalStateException("Aucun accéléromètre"))
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(AccelSample(event.values[0], event.values[1], event.values[2], event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SAMPLING_PERIOD_US)
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 10_000
    }
}
