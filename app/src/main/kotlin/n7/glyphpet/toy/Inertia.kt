package n7.glyphpet.toy

import kotlin.math.PI
import kotlin.math.abs

/**
 * DEMO: the pet feels the phone moved. A spring holds it at home on the matrix; the phone's own acceleration pushes
 * it the other way, as the floor of a lift does: lifted fast it is pressed down, dropped fast it floats up and lands,
 * jerked sideways it is left behind. Fed at the accelerometer's rate; the toy reads [x] and [y] (cells) each frame.
 */
class Inertia {

    companion object {
        /** The spring's own pace (Hz) and how much it is damped (1 = no bounce at all). */
        private const val HZ = 2.6f
        private const val DAMPING = 0.32f
        private val K = (2 * PI * HZ).toFloat().let { it * it }
        private val C = (2 * DAMPING * 2 * PI * HZ).toFloat()

        /** Cells of push per m/s² (steady state = GAIN / K): a quick lift of 5 m/s² presses about 2 cells. */
        private val GAIN_Y = 2f * K / 5f
        private val GAIN_X = 1.6f * K / 5f

        /** Below this (m/s²) the hand's tremble is ignored, so a still hand keeps a still pet. */
        private const val DEAD = 0.6f

        /** How far it may go: a little into the floor, a good way up, a few cells to each side. */
        const val DOWN_MAX = 1.5f
        const val UP_MAX = 6f
        const val SIDE_MAX = 4f
    }

    /** Matrix cells: +x the way the pet slides for a positive tilt, +y down (toward the bottom row). */
    @Volatile var x = 0f
        private set
    @Volatile var y = 0f
        private set

    private var vx = 0f
    private var vy = 0f

    /** Set once when it comes down from a float high enough to feel; the toy consumes it. */
    @Volatile var landed = false

    private var peak = 0f

    /**
     * One reading: [up] the phone's acceleration along the world's up (m/s², gravity removed), [side] along the
     * device's x edge with the vertical part removed; [dt] seconds since the last.
     */
    fun feed(up: Float, side: Float, dt: Float) {
        // Lifted (up > 0): pressed toward the floor, which is down on the matrix (+y).
        val fy = if (abs(up) < DEAD) 0f else (up - DEAD * Math.signum(up)) * GAIN_Y
        val fx = if (abs(side) < DEAD) 0f else (side - DEAD * Math.signum(side)) * GAIN_X
        vy += (fy - K * y - C * vy) * dt
        vx += (fx - K * x - C * vx) * dt
        y += vy * dt
        x += vx * dt
        if (y > DOWN_MAX) { y = DOWN_MAX; if (vy > 0) vy = 0f }
        if (y < -UP_MAX) { y = -UP_MAX; if (vy < 0) vy = 0f }
        x = x.coerceIn(-SIDE_MAX, SIDE_MAX)
        // Remember how high it floated, and tell of the landing when it is back on its feet.
        if (y < peak) peak = y
        if (peak < -1.5f && y >= -0.2f) {
            landed = true
            peak = 0f
        }
        if (y > -0.2f && peak > -1.5f) peak = minOf(0f, y)
    }

    fun reset() {
        x = 0f; y = 0f; vx = 0f; vy = 0f; peak = 0f; landed = false
    }

    /** [frame] moved by the current offset, rounded to whole cells; what leaves the matrix is lost. */
    fun shift(frame: IntArray, size: Int): IntArray {
        val ox = Math.round(x)
        val oy = Math.round(y)
        if (ox == 0 && oy == 0) return frame
        val out = IntArray(frame.size)
        for (py in 0 until size) {
            val sy = py - oy
            if (sy !in 0 until size) continue
            for (px in 0 until size) {
                val sx = px - ox
                if (sx !in 0 until size) continue
                out[py * size + px] = frame[sy * size + sx]
            }
        }
        // Only the LEDs that exist light up: the matrix is round.
        for (i in out.indices) if (!n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(i % size, i / size)) out[i] = 0
        return out
    }
}
