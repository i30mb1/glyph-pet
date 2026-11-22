package n7.glyphpet.toy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Reads the accelerometer: [tilt] is the sideways lean (-1..1, positive = the pet slides to the right
 * of the matrix as seen from the back), [tiltY] the forward/back lean (-1..1, positive = the phone stands upright, so
 * down on the matrix is down), [consumeShake] reports a shake once. Together, ([tilt], [tiltY]) is the way "down"
 * points on the matrix as seen from the back: where things fall and roll.
 * With the proximity and light sensors it also tells how the phone lies ([pose]): screen up, in a pocket.
 */
class MotionSensor(context: Context) : SensorEventListener {

    private companion object {
        /** Shakes closer together count as one; short enough that five shakes open an egg in a few seconds. */
        const val SHAKE_GAP_MS = 1200L

        /** Tapping: a jolt of this much (m/s², high-passed) out of a phone held this still, two of them this far apart. */
        const val TAP = 1.6f
        const val TAP_STILL = 0.6f
        const val TAP_GAP_MIN = 90L
        const val TAP_GAP_MAX = 550L

        /** A jolt of the body this strong (m/s², high-passed) is a touch on the phone, not a sound in the air ([lastJoltMs]). */
        const val JOLT = 1.0f

        /** The accelerometer runs at 100 Hz, fast enough for the pet's inertia to follow a quick move. */
        const val ACCEL_PERIOD_US = 10_000

        /** rad/s around the screen's axis that is [spin] 1: half a turn a second. */
        const val SPIN_FULL = 3f

        /** [yaw] at most this far (radians, about 34 degrees), and it slides back to 0 over this time once the turn stops. */
        const val YAW_MAX = 0.6f
        const val YAW_BACK_S = 2f
    }

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /**
     * A magnet near the phone, 0..1 (0: only the Earth's field, about 50 µT; 1: a strong magnet, a magnetic charger), and
     * where the field points on the matrix as seen from the back (radians, 0 to the right, pi/2 down): north, or the magnet.
     */
    @Volatile var magnet = 0f
        private set
    @Volatile var magnetAngle = (-Math.PI / 2).toFloat()
        private set
    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val main = Handler(Looper.getMainLooper())

    @Volatile var tilt: Float = 0f
        private set

    /** DEMO: the pet thrown about by the phone's moves ([Inertia]). */
    val inertia = Inertia()

    /** Smoothed like [tilt]: 1 upright, 0 lying flat, -1 upside down. Upright until the accelerometer says otherwise. */
    @Volatile var tiltY: Float = 1f
        private set

    /**
     * How much the phone trembles: the high-passed acceleration (m/s²) smoothed over about half a second. A phone on a
     * table stays near 0.03, one held in the hand 0.1 and more.
     */
    @Volatile var jitter: Float = 0f
        private set

    /**
     * How fast the phone turns in the plane of its screen, -1..1 smoothed (1 = [SPIN_FULL]): lying on the desk, turned
     * round on it. Positive turns it clockwise as seen from the back (the matrix).
     */
    @Volatile var spin: Float = 0f
        private set

    /**
     * How far the phone has just been turned round the world's vertical axis (radians, -[YAW_MAX]..[YAW_MAX]): positive
     * when its right side (as seen on the matrix) came toward the viewer. The gyroscope's rate projected on gravity, so
     * what the accelerometer cannot see; it slides back to 0 over [YAW_BACK_S] once the turn stops.
     */
    @Volatile var yaw: Float = 0f
        private set

    @Volatile private var gravityZ = 0f
    @Volatile private var near: Boolean? = null
    @Volatile private var lux: Float? = null
    @Volatile private var readings = 0

    private var running = false
    private var shaken = false
    private var lastShakeMs = 0L

    // Stroking: two light taps on the back, a short sharp jolt each.
    private var lastHigh = 0f
    private var lastTapMs = 0L
    private var stroked = false

    private var lastGyroNs = 0L
    private val low = FloatArray(3)
    private var lastAccelNs = 0L

    /** How the phone lies, or null before the accelerometer has said anything. */
    val pose: Pose? get() = if (readings > 0) Pose(gravityZ, near, lux) else null

    fun start() {
        if (running) return
        running = true
        readings = 0
        lastAccelNs = 0L
        jitter = 0f
        lastGyroNs = 0L
        spin = 0f
        yaw = 0f
        gyroscope?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensors?.registerListener(this, it, ACCEL_PERIOD_US) }
        proximity?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        light?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        running = false
        sensors?.unregisterListener(this)
        tilt = 0f
        tiltY = 1f
        spin = 0f
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

    /**
     * When the body was last jolted (wall clock ms): a tap or a knock on the phone. The microphone hears a tap as a
     * click, and this tells it from a clap in the air ([Mic.consumeClap]).
     */
    @Volatile var lastJoltMs = 0L
        private set

    /** The jolts of the last few seconds (wall clock ms): a tap on the phone, not a key on a keyboard beside it. */
    private val jolts = ArrayDeque<Long>()

    /** Whether the body was jolted within [nearMs] of [atMs] (wall clock ms). */
    fun joltedNear(atMs: Long, nearMs: Long): Boolean = synchronized(jolts) { jolts.any { kotlin.math.abs(it - atMs) <= nearMs } }

    /** Two light taps on the back (a stroke), reported once. */
    fun consumeStroke(): Boolean = synchronized(this) { stroked.also { stroked = false } }

    /** A sharp little jolt out of stillness is a tap; two of them close together, a stroke. */
    private fun watchTap(high: Float, wall: Long) {
        if (high > JOLT) {
            // One tap jolts for a few readings: kept once.
            if (wall - lastJoltMs > 60) synchronized(jolts) { jolts.addLast(wall); while (jolts.size > 12) jolts.removeFirst() }
            lastJoltMs = wall
        }
        val tap = high > TAP && lastHigh < TAP / 2 && jitter < TAP_STILL
        lastHigh = high
        if (!tap) return
        val gap = wall - lastTapMs
        lastTapMs = wall
        if (gap in TAP_GAP_MIN..TAP_GAP_MAX && wall - lastShakeMs > SHAKE_GAP_MS) {
            lastTapMs = 0L
            synchronized(this) { stroked = true }
        }
    }




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
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val (bx, by, bz) = event.values
                val strength = sqrt(bx * bx + by * by + bz * bz)
                magnet += (((strength - 60f) / 140f).coerceIn(0f, 1f) - magnet) * 0.2f
                // Seen from the back the device's +x edge is on the left, and +y is the top of the matrix.
                if (bx * bx + by * by > 4f) magnetAngle = kotlin.math.atan2(-by, -bx)
                return
            }
            Sensor.TYPE_GYROSCOPE -> {
                onGyro(event)
                return
            }
        }
        val (ax, ay, az) = event.values
        val dt = if (lastAccelNs == 0L) 0.01f else ((event.timestamp - lastAccelNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastAccelNs = event.timestamp
        // Smoothing by time, not by reading, so it stays the same at any sensor rate (tau of 0.8 and 0.85 per 20 ms).
        val kTilt = 1f - exp(-dt / 0.09f)
        // Seen from the back, the device's +x edge is on the viewer's left: lifting it tips the pet right.
        val lean = (ax / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tilt += (lean - tilt) * kTilt
        // The device's +y edge is the top of the matrix from either side: upright, gravity pulls toward the bottom row.
        val upright = (ay / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        tiltY += (upright - tiltY) * kTilt
        gravityZ = if (readings == 0) az else gravityZ + (az - gravityZ) * (1f - exp(-dt / 0.12f))
        // High-pass: the acceleration minus its slow average (gravity and the hand's slow moves).
        if (readings == 0) { low[0] = ax; low[1] = ay; low[2] = az }
        val kLow = 1f - exp(-dt / 0.25f)
        low[0] += (ax - low[0]) * kLow
        low[1] += (ay - low[1]) * kLow
        low[2] += (az - low[2]) * kLow
        val hx = ax - low[0]
        val hy = ay - low[1]
        val hz = az - low[2]
        val high = sqrt(hx * hx + hy * hy + hz * hz)
        val wallNow = System.currentTimeMillis()
        watchTap(high, wallNow)
        jitter += (high - jitter) * (1f - exp(-dt / 0.6f))
        // DEMO inertia: the moved phone's acceleration along the world's up (the slow average points up while still),
        // and along the device's x edge with that vertical part taken out.
        val gLen = sqrt(low[0] * low[0] + low[1] * low[1] + low[2] * low[2])
        if (readings > 0 && gLen > 4f) {
            val up = (hx * low[0] + hy * low[1] + hz * low[2]) / gLen
            val side = hx - up * low[0] / gLen
            inertia.feed(up, side, dt)
        }
        readings++

        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val now = event.timestamp / 1_000_000
        val force = magnitude - SensorManager.GRAVITY_EARTH
        val wall = System.currentTimeMillis()
        if (force > 9f && wall - lastShakeMs > SHAKE_GAP_MS) {
            lastShakeMs = wall
            synchronized(this) { shaken = true }
        }
    }

    private fun onGyro(event: SensorEvent) {
        val dt = if (lastGyroNs == 0L) 0.02f else ((event.timestamp - lastGyroNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastGyroNs = event.timestamp
        // The gyroscope's z is counterclockwise seen from the screen, so clockwise seen from the matrix.
        val wz = event.values[2]
        spin += ((wz / SPIN_FULL).coerceIn(-1f, 1f) - spin) * (1f - exp(-dt / 0.25f))
        // The turn round the vertical: the rate along "up" (the slow accelerometer, which reads up while still).
        // Counterclockwise from above takes the matrix's right side (the device's -x end) away from the viewer.
        val g = sqrt(low[0] * low[0] + low[1] * low[1] + low[2] * low[2])
        if (readings > 0 && g > 4f) {
            val up = (event.values[0] * low[0] + event.values[1] * low[1] + wz * low[2]) / g
            yaw = ((yaw - up * dt) * exp(-dt / YAW_BACK_S)).coerceIn(-YAW_MAX, YAW_MAX)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
