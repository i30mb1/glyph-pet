package n7.glyphpet.toy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Listens for someone blowing on the phone or clapping near it, only while the pet is live on the matrix. Nothing is
 * recorded or kept: each 20 ms of sound becomes one loudness number and is dropped.
 *
 * Blowing into a microphone is a loud, steady roar; a clap is a short bang out of quiet. So: a level far above the room's
 * own noise for more than [BLOW_FRAMES] frames in a row is wind ([wind], 0..1, eased), and one that is over within
 * [CLAP_MAX_FRAMES] frames after quiet is a clap ([consumeClap]). Typing by the phone is a run of short clicks, each a
 * jump in loudness over the moment before ([consumeKeys], [typing]); a click in such a run is never a clap.
 */
class Mic(private val context: Context) {

    companion object {
        private const val TAG = "PetMic"
        private const val RATE = 16_000
        private const val FRAME = RATE / 50

        /** How far above the room's noise (dB) a sound must be, and never quieter than [MIN_DB] (dBFS). */
        const val OVER_FLOOR_DB = 24f
        const val MIN_DB = -34f

        /** Loud this many frames in a row (20 ms each) is blowing; over within [CLAP_MAX_FRAMES] is a clap. */
        const val BLOW_FRAMES = 6
        const val CLAP_MAX_FRAMES = 4

        /** dB over the threshold that is full wind. */
        const val FULL_DB = 16f

        /**
         * A clap waits this long before it counts, and does not count if the phone's body was jolted this close to it:
         * a tap on the phone clicks in the microphone too, a clap in the air does not shake the phone.
         */
        const val CLAP_WAIT_MS = 300L
        const val JOLT_NEAR_MS = 350L

        /** A click: this many dB over the moment before it, and over the room's noise; no two closer than [KEY_GAP_MS]. */
        const val KEY_JUMP_DB = 8f
        const val KEY_OVER_FLOOR_DB = 12f
        const val KEY_GAP_MS = 70L

        /** This many clicks within [TYPING_WINDOW_MS] is someone typing. */
        const val TYPING_KEYS = 4
        const val TYPING_WINDOW_MS = 1500L

        /** A clap with another click this close before it, or this close after, was a key, not a clap. */
        const val CLAP_ALONE_BEFORE_MS = 700L

        fun allowed(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    @Volatile var wind = 0f
        private set

    @Volatile private var claps = 0

    /** When the last clap was heard (wall clock ms), waiting to be checked against a jolt; 0: none. */
    @Volatile private var clapAtMs = 0L

    /** The clicks heard lately (wall clock ms), and how many since [consumeKeys] was last asked. */
    private val clicks = ArrayDeque<Long>()
    @Volatile private var keys = 0

    /**
     * Tells a tap on the phone from a key: whether the phone's body was jolted near a click at that moment (wall clock
     * ms). A tap clicks in the microphone too, but only a tap shakes the phone; the toy sets it from [MotionSensor].
     */
    @Volatile var tapped: (Long) -> Boolean = { false }

    /** Clicks heard since the last call that were keys, not taps on the phone. */
    fun consumeKeys(): Int {
        val k = keys
        keys = 0
        if (k == 0) return 0
        val now = System.currentTimeMillis()
        val recent = synchronized(clicks) { clicks.filter { now - it < 400 } }
        return minOf(k, recent.count { !tapped(it) })
    }

    /** Someone is typing by the phone: [TYPING_KEYS] clicks within the last [TYPING_WINDOW_MS], taps on the phone not counted. */
    fun typing(nowMs: Long = System.currentTimeMillis()): Boolean {
        val recent = synchronized(clicks) { clicks.filter { nowMs - it < TYPING_WINDOW_MS } }
        // A tap on the phone in this while: someone is stroking the pet, not typing.
        if (recent.any { tapped(it) }) return false
        return recent.size >= TYPING_KEYS
    }

    /** Whether a click other than the one at [at] was heard from [from] to [to] (wall clock ms). */
    private fun otherClick(at: Long, from: Long, to: Long): Boolean = synchronized(clicks) {
        clicks.any { it in from..to && kotlin.math.abs(it - at) > 60 }
    }

    /** The loudness of the last frame and the room's noise (dBFS), for the debug log. */
    @Volatile var levelDb = -90f
        private set
    @Volatile var floorDb = -60f
        private set

    @Volatile private var running = false
    private var thread: Thread? = null

    /**
     * A clap in the air was heard, reported once [CLAP_WAIT_MS] after it. [lastJoltMs]: when the phone's body was last
     * jolted ([MotionSensor.lastJoltMs]); a click that close to a jolt was a tap on the phone and is dropped.
     */
    fun consumeClap(lastJoltMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val at = clapAtMs
        if (at == 0L || nowMs - at < CLAP_WAIT_MS) return false
        clapAtMs = 0L
        claps = 0
        val tap = kotlin.math.abs(lastJoltMs - at) < JOLT_NEAR_MS
        if (tap && n7.glyphpet.BuildConfig.DEBUG) Log.d(TAG, "click with a jolt: a tap, not a clap")
        // One click among others is a key being typed.
        val typed = otherClick(at, at - CLAP_ALONE_BEFORE_MS, at + CLAP_WAIT_MS)
        if (typed && n7.glyphpet.BuildConfig.DEBUG) Log.d(TAG, "click among clicks: typing, not a clap")
        return !tap && !typed
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !allowed(context)) return
        running = true
        thread = Thread({ listen() }, "pet-mic").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread = null
        wind = 0f
    }

    @SuppressLint("MissingPermission")
    private fun listen() {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 8))
        } catch (e: Exception) {
            Log.w(TAG, "no microphone", e); running = false; return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); running = false; return }
        val buf = ShortArray(FRAME)
        var loud = 0
        var quietBefore = true
        var smooth = 0f
        var lastLog = 0L
        var recent = -90f
        var lastClick = 0L
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, FRAME)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
                val db = (20 * log10(sqrt(sum / n) / 32768.0 + 1e-9)).toFloat()
                levelDb = db
                // The room's noise: follows quiet at once, loud only very slowly (about 1 dB a second).
                floorDb = if (db < floorDb) db else (floorDb + 0.02f).coerceAtMost(-40f)
                floorDb = floorDb.coerceAtLeast(-90f)
                val threshold = maxOf(floorDb + OVER_FLOOR_DB, MIN_DB)
                // A click: a sudden jump over the moment before.
                val wall = System.currentTimeMillis()
                if (db > recent + KEY_JUMP_DB && db > floorDb + KEY_OVER_FLOOR_DB && wall - lastClick >= KEY_GAP_MS) {
                    lastClick = wall
                    keys++
                    synchronized(clicks) { clicks.addLast(wall); while (clicks.size > 32) clicks.removeFirst() }
                }
                recent += (db - recent) * 0.3f
                if (db > threshold) {
                    loud++
                } else {
                    if (loud in 1..CLAP_MAX_FRAMES && quietBefore) { claps++; clapAtMs = wall - loud * 20L }
                    quietBefore = loud == 0 || loud <= CLAP_MAX_FRAMES
                    loud = 0
                }
                val target = if (loud >= BLOW_FRAMES) (0.3f + 0.7f * (db - threshold) / FULL_DB).coerceIn(0.3f, 1f) else 0f
                // Quick to rise, a little slower to fall, so a breath between puffs does not drop the wind.
                smooth += (target - smooth) * (if (target > smooth) 0.5f else 0.12f)
                wind = if (smooth < 0.05f) 0f else smooth
                val now = SystemClock.uptimeMillis()
                if (n7.glyphpet.BuildConfig.DEBUG && (now - lastLog >= 250 && (db > threshold || wind > 0f))) {
                    lastLog = now
                    Log.d(TAG, "level=%.1f floor=%.1f thr=%.1f loud=$loud wind=%.2f claps=$claps".format(db, floorDb, threshold, wind))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "microphone stopped", e)
        } finally {
            runCatching { rec.stop() }
            rec.release()
            wind = 0f
        }
    }
}
