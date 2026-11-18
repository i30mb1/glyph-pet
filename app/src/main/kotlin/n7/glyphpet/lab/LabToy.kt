package n7.glyphpet.lab

import android.content.Context
import android.util.Log

/**
 * Debug builds only: the Lab on the Glyph toy. Shows one depth experiment at a time with the hedgehog, reads its own
 * accelerometer while live, and briefly shows the experiment's number when it changes. Never touches the save.
 */
class LabToy(private val context: Context) {

    companion object {
        /** The Lab runs faster than the pet (25 fps), so motion looks smooth. */
        const val FRAME_MS = 40L

        /** How long the number of a newly picked experiment stays, ms. */
        const val NUMBER_MS = 1000L
        private const val NUMBER_FADE_MS = 200L
        private const val TAG = "GlyphLab"
    }

    private val sensor = LabSensor(context)
    private val resting = LabSensor(null)
    private val experiments: List<Experiment> = listOf(
        BallExperiment(),
        VoxelExperiment(),
        WindowExperiment(),
        SnowExperiment(),
        HopExperiment(),
        LenticularExperiment(),
        DistanceExperiment(),
    )
    private val layer = Layer()
    private val digits = Layer()
    private val frame = IntArray(Layer.N * Layer.N)

    var running = false
        private set
    private var current = -1
    private var clockMs = 0L
    private var numberLeftMs = 0L

    fun start() {
        if (running) return
        running = true
        sensor.start()
    }

    fun stop() {
        if (!running) return
        running = false
        sensor.stop()
    }

    /** The Glyph button: the next experiment, wrapping around. */
    fun next() {
        LabPrefs.setIndex(context, LabPrefs.index(context) + 1)
        pick()
    }

    /** One live frame after [dtMs]. */
    fun frame(dtMs: Long): IntArray = safely {
        pick()
        val dt = dtMs.coerceIn(0, 250)
        clockMs += dt
        sensor.decay(dt / 1000f)
        layer.clear()
        experiments[current].render(layer, dt / 1000f, clockMs / 1000f, sensor)
        if (numberLeftMs > 0) {
            numberLeftMs -= dt
            digits.clear()
            Layer.number(current + 1, digits)
            // The number covers the experiment, then fades into it.
            val k = (numberLeftMs.coerceAtLeast(0) / NUMBER_FADE_MS.toFloat()).coerceIn(0f, 1f)
            for (i in layer.v.indices) layer.v[i] = layer.v[i] * (1 - k) + digits.v[i] * k
        }
        layer.toFrame(frame)
    }

    /** Always-on: a still frame of the current experiment, as if the phone were held upright and calm. */
    fun still(): IntArray = safely {
        pick()
        numberLeftMs = 0
        layer.clear()
        experiments[current].render(layer, 0f, 1.2f, resting)
        layer.toFrame(frame)
    }

    /** Follows the index in the preferences, so the app's buttons switch too. */
    private fun pick() {
        val index = LabPrefs.index(context)
        if (index == current) return
        current = index
        experiments[index].reset()
        clockMs = 0
        numberLeftMs = NUMBER_MS
    }

    private inline fun safely(block: () -> IntArray): IntArray = try {
        block()
    } catch (e: Exception) {
        Log.e(TAG, "Lab frame failed", e)
        frame.fill(0)
        frame
    }
}
