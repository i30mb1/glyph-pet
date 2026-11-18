package n7.glyphpet.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * Reads the accelerometer: [tilt] is the sideways lean (-1..1, positive = the pet slides to the right
 * of the matrix as seen from the back), [consumeShake] reports a shake once, [consumeDrop] a drop once.
 * With the proximity and light sensors it also tells how the phone lies ([pose]): screen up, in a pocket.
 */
class MotionSensor(context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val main = Handler(Looper.getMainLooper())

    @Volatile var tilt: Float = 0f
        private set

    @Volatile private var gravityZ = 0f
    @Volatile private var near: Boolean? = null
    @Volatile private var lux: Float? = null
    @Volatile private var readings = 0

    private var running = false
    private var shaken = false
    private var dropped = false
    private var lastShakeMs = 0L
    private val drops = DropDetector()

    /** How the phone lies, or null before the accelerometer has said anything. */
    val pose: Pose? get() = if (readings > 0) Pose(gravityZ, near, lux) else null

    fun start() {
        if (running) return
        running = true
        readings = 0
        accelerometer?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        proximity?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        light?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        running = false
        sensors?.unregisterListener(this)
        tilt = 0f
        near = null
        lux = null
        readings = 0
    }

    /**
     * For a single frame (Always-on): how the phone lies, read within [timeoutMs]. While the sensors run already the
     * answer is immediate. Null if the accelerometer said nothing in time.
     */
    fun readPose(timeoutMs: Long = 300, done: (Pose?) -> Unit) {
        if (running && readings > 0) { done(pose); return }
        val wasRunning = running
        start()
        main.postDelayed({
            val p = pose
            if (!wasRunning) stop()
            done(p)
        }, timeoutMs)
    }

    @Synchronized
    fun consumeShake(): Boolean = shaken.also { shaken = false }

    @Synchronized
    fun consumeDrop(): Boolean = dropped.also { dropped = false }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val range = event.sensor.maximumRange
                near = event.values[0] < minOf(range, 5f)
                return
            }
            Sensor.TYPE_LIGHT -> {
                lux = event.values[0]
                return
            }
        }
        val (ax, ay, az) = event.values
        // Seen from the back, the device's +x edge is on the viewer's left: lifting it tips the pet right.
        val lean = (ax / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tilt = tilt * 0.8f + lean * 0.2f
        gravityZ = if (readings == 0) az else gravityZ * 0.85f + az * 0.15f
        readings++

        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val now = event.timestamp / 1_000_000
        if (drops.feed(magnitude, now)) synchronized(this) { dropped = true }

        val force = magnitude - SensorManager.GRAVITY_EARTH
        val wall = System.currentTimeMillis()
        if (force > 9f && wall - lastShakeMs > 2500) {
            lastShakeMs = wall
            synchronized(this) { shaken = true }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
