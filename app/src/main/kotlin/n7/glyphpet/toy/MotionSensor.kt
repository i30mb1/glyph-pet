package n7.glyphpet.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Reads the accelerometer: [tilt] is the sideways lean (-1..1, positive = the pet slides to the right
 * of the matrix as seen from the back), [consumeShake] reports a shake once.
 */
class MotionSensor(context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile var tilt: Float = 0f
        private set

    private var shaken = false
    private var lastShakeMs = 0L

    fun start() {
        accelerometer?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensors?.unregisterListener(this)
        tilt = 0f
    }

    @Synchronized
    fun consumeShake(): Boolean = shaken.also { shaken = false }

    override fun onSensorChanged(event: SensorEvent) {
        val (ax, ay, az) = event.values
        // Seen from the back, the device's +x edge is on the viewer's left: lifting it tips the pet right.
        val lean = (ax / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tilt = tilt * 0.8f + lean * 0.2f

        val force = sqrt(ax * ax + ay * ay + az * az) - SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (force > 9f && now - lastShakeMs > 2500) {
            lastShakeMs = now
            synchronized(this) { shaken = true }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
