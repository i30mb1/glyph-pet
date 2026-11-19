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
 * down on the matrix is down), [consumeShake] reports a shake once, [consumeDrop] a drop once,
 * [consumeRock] a gentle rock from side to side once ([RockDetector]). Together, ([tilt], [tiltY]) is the way "down"
 * points on the matrix as seen from the back: where things fall and roll.
 * With the proximity and light sensors it also tells how the phone lies ([pose]): screen up, in a pocket.
 */
class MotionSensor(context: Context) : SensorEventListener {

    private companion object {
        /** Shakes closer together count as one; short enough that five shakes open an egg in a few seconds. */
        const val SHAKE_GAP_MS = 1200L

        /** The accelerometer runs at 100 Hz, fast enough to catch the short spike of a knock. */
        const val ACCEL_PERIOD_US = 10_000

        /** A shake is confirmed this long after its spike, unless the spike turned out to be a knock. */
        const val SHAKE_CONFIRM_MS = 200L

        /** rad/s around the screen's axis that is [spin] 1: half a turn a second. */
        const val SPIN_FULL = 3f

        /** [yaw] at most this far (radians, about 34 degrees), and it slides back to 0 over this time once the turn stops. */
        const val YAW_MAX = 0.6f
        const val YAW_BACK_S = 2f
    }

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val main = Handler(Looper.getMainLooper())

    @Volatile var tilt: Float = 0f
        private set

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
    private var dropped = false
    private var rocked = false
    private var lastShakeMs = 0L
    private var shakeAt = -1L
    private var knock = 0
    private val knocks = KnockDetector()
    private val twirls = TwirlDetector()
    private var twirled = false
    private var lastGyroNs = 0L
    private val drops = DropDetector()
    private val rocks = RockDetector()
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
        shakeAt = -1L
        knocks.reset()
        twirls.reset()
        lastGyroNs = 0L
        spin = 0f
        yaw = 0f
        gyroscope?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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

    @Synchronized
    fun consumeDrop(): Boolean = dropped.also { dropped = false }

    /** A knock on the desk or the back of the phone ([KnockDetector]): 1 single, 2 double, 0 none; once. */
    @Synchronized
    fun consumeKnock(): Int = knock.also { knock = 0 }

    /** The phone twirled round in the plane of its screen ([TwirlDetector]); once. */
    @Synchronized
    fun consumeTwirl(): Boolean = twirled.also { twirled = false }

    @Synchronized
    fun consumeRock(): Boolean = rocked.also { rocked = false }

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
        jitter += (high - jitter) * (1f - exp(-dt / 0.6f))
        readings++

        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val now = event.timestamp / 1_000_000
        if (drops.feed(magnitude, now)) synchronized(this) { dropped = true }

        val force = magnitude - SensorManager.GRAVITY_EARTH
        if (rocks.feed(tilt, force, now)) synchronized(this) { rocked = true }
        val knocked = knocks.feed(high, magnitude, dt, now)
        if (knocked > 0) synchronized(this) { knock = maxOf(knock, knocked) }
        // A hard knock on the back can be as strong as a shake for a moment: a shake waits to see that it was none.
        val wall = System.currentTimeMillis()
        if (force > 9f && shakeAt < 0 && wall - lastShakeMs > SHAKE_GAP_MS) shakeAt = now
        if (shakeAt >= 0 && knocks.confirmedAt >= shakeAt) shakeAt = -1
        if (shakeAt >= 0 && now - shakeAt >= SHAKE_CONFIRM_MS) {
            shakeAt = -1
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
        if (twirls.feed(wz, dt, event.timestamp / 1_000_000)) synchronized(this) { twirled = true }
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
