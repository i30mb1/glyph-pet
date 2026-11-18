package n7.glyphpet.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Lab's own accelerometer, in matrix coordinates as seen from the BACK of the phone (x to the right, y down).
 * Seen from the back the device's +x edge is on the viewer's left, so the matrix x axis is the device's -x; the
 * matrix y axis (down) is the device's -y. The accelerometer reports "up", gravity is its opposite, which gives
 * gravity in matrix coordinates = (ax, ay) / g.
 *
 * [gx], [gy]: smoothed gravity in g (phone upright: gy ≈ 1, things fall down the matrix).
 * [tiltX], [tiltY]: lean relative to how the phone is usually held (y against a slow baseline), -1..1, for parallax.
 * [shake]: how hard the phone is being shaken right now, in g above rest, decaying.
 * [consumeJolt]: a hard shake once, with the direction of the jolt.
 */
class LabSensor(context: Context?) : SensorEventListener {

    private companion object {
        const val SMOOTH_S = 0.07f
        const val BASELINE_S = 3.5f
        const val JOLT_G = 0.9f
        const val JOLT_GAP_NS = 350_000_000L
    }

    private val sensors = context?.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var gx = 0f; private set
    var gy = 0f; private set
    private var baseY = 0f
    var shake = 0f; private set

    val tiltX: Float get() = gx.coerceIn(-1f, 1f)
    val tiltY: Float get() = (gy - baseY).coerceIn(-1f, 1f)

    private var running = false
    private var lastNs = 0L
    private var joltX = 0f
    private var joltY = 0f
    private var jolted = false
    private var lastJoltNs = 0L

    fun start() {
        if (running) return
        running = true
        lastNs = 0L
        try {
            accelerometer?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            sensors?.unregisterListener(this)
        } catch (_: Exception) {
        }
        gx = 0f; gy = 0f; baseY = 0f; shake = 0f; jolted = false
    }

    /** The last hard shake as a direction × strength (in g), or null. */
    fun consumeJolt(): Pair<Float, Float>? {
        if (!jolted) return null
        jolted = false
        return joltX to joltY
    }

    /** Lets the shake level fade between sensor events too. */
    fun decay(dt: Float) {
        shake *= exp(-dt * 4f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        val rx = event.values[0] / SensorManager.GRAVITY_EARTH
        val ry = event.values[1] / SensorManager.GRAVITY_EARTH
        val rz = event.values[2] / SensorManager.GRAVITY_EARTH
        val ns = event.timestamp
        if (lastNs == 0L) {
            gx = rx; gy = ry; baseY = ry
            lastNs = ns
            return
        }
        val dt = ((ns - lastNs) / 1e9f).coerceIn(0f, 0.2f)
        lastNs = ns
        val k = 1f - exp(-dt / SMOOTH_S)
        // The high-passed part (raw minus smoothed) is the jolt of a shake.
        val hx = rx - gx
        val hy = ry - gy
        gx += hx * k
        gy += hy * k
        baseY += (gy - baseY) * (1f - exp(-dt / BASELINE_S))

        val force = kotlin.math.abs(sqrt(rx * rx + ry * ry + rz * rz) - 1f)
        if (force > shake) shake = force
        if (force > JOLT_G && ns - lastJoltNs > JOLT_GAP_NS) {
            lastJoltNs = ns
            val l = sqrt(hx * hx + hy * hy).coerceAtLeast(0.001f)
            // Like gravity, the high-passed reading is the push that things inside feel: the phone jerks one way,
            // they are thrown the other, which is the direction of this vector in matrix coordinates.
            joltX = hx / l * force
            joltY = hy / l * force
            jolted = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
