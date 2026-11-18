package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The ball the pet plays with when the phone is rocked: a small shaded disc that rolls around the round matrix under
 * gravity, bounces off its edge and fades away after [LIFE_MS]. Positions are in LED cells, sub-pixel; the matrix is a
 * bowl of radius [RIM] around its middle.
 */
class Ball(private val random: Random) {

    companion object {
        private const val SIZE = GlyphMatrixSpec.SIZE
        private const val BRIGHT = SpeciesRenderer.BRIGHT
        private const val CENTER = (SIZE - 1) / 2f

        /** The ball's radius, in cells. */
        const val RADIUS = 1.6f

        /** How far from the middle the ball's edge may go: the round edge of the matrix. */
        const val RIM = 12.2f

        /** How long a ball rolls after it appears or is kicked, the last [FADE_MS] of it fading out. */
        const val LIFE_MS = 6000L
        const val FADE_MS = 900L

        /** Cells/s² at full tilt, how much speed is left after a second, and how much of it after a bounce. */
        const val GRAVITY = 34f
        const val DAMPING = 0.55f
        const val BOUNCE = 0.55f
        const val MAX_SPEED = 26f

        /** How hard a rock kicks a ball that is already out, in cells/s. */
        const val KICK = 11f

        /** The longest physics step: an 80 ms frame runs in three. */
        private const val STEP_S = 1f / 30f

        /** The shaded side and the lit side of the ball, and its edge on the pet's body: always above BODY. */
        private const val DARK = 1500
        private const val LIT = 3600
    }

    var x = CENTER
        private set
    var y = CENTER
        private set
    private var vx = 0f
    private var vy = 0f
    private var ageMs = -1L
    private var squash = 0f
    private var sqx = 0f
    private var sqy = 1f

    /** Whether a ball is out. */
    val active: Boolean get() = ageMs >= 0

    /** Appears at ([atX], [atY]) (moved inside the rim if need be) with a little pop up against gravity ([gx], [gy]). */
    fun spawn(atX: Float, atY: Float, gx: Float, gy: Float) {
        x = atX
        y = atY
        keepInside(bounce = false)
        val (dx, dy) = down(gx, gy)
        val side = random.nextFloat() * 2f - 1f
        vx = -dx * 5f - dy * side * 4f
        vy = -dy * 5f + dx * side * 4f
        squash = 0f
        ageMs = 0
    }

    /** A rock while the ball is out: kicked up against gravity and a little sideways; it rolls on for another life. */
    fun kick(gx: Float, gy: Float) {
        if (!active) return
        val (dx, dy) = down(gx, gy)
        val side = random.nextFloat() * 2f - 1f
        vx += -dx * KICK - dy * side * KICK * 0.6f
        vy += -dy * KICK + dx * side * KICK * 0.6f
        ageMs = 0
    }

    fun clear() {
        ageMs = -1
    }

    /** Moves the ball on by [dtMs] under gravity ([gx], [gy]): -1..1 each, the phone's lean. */
    fun update(dtMs: Long, gx: Float, gy: Float) {
        if (!active) return
        ageMs += dtMs
        if (ageMs >= LIFE_MS) { clear(); return }
        var left = dtMs / 1000f
        while (left > 0f) {
            val dt = minOf(left, STEP_S)
            left -= dt
            vx += gx * GRAVITY * dt
            vy += gy * GRAVITY * dt
            val keep = DAMPING.pow(dt)
            vx *= keep
            vy *= keep
            val speed = hypot(vx, vy)
            if (speed > MAX_SPEED) { vx *= MAX_SPEED / speed; vy *= MAX_SPEED / speed }
            x += vx * dt
            y += vy * dt
            keepInside(bounce = true)
            squash *= 0.0015f.pow(dt)
        }
        if (x.isNaN() || y.isNaN()) { x = CENTER; y = CENTER; vx = 0f; vy = 0f }
    }

    /** Back inside the rim; with [bounce], the speed into the edge turns around, losing some, and the ball squashes. */
    private fun keepInside(bounce: Boolean) {
        val lim = RIM - RADIUS
        val dx = x - CENTER
        val dy = y - CENTER
        val dist = hypot(dx, dy)
        if (dist <= lim || dist == 0f) return
        val nx = dx / dist
        val ny = dy / dist
        x = CENTER + nx * lim
        y = CENTER + ny * lim
        if (!bounce) return
        val vn = vx * nx + vy * ny
        if (vn > 0f) {
            vx -= (1f + BOUNCE) * vn * nx
            vy -= (1f + BOUNCE) * vn * ny
            squash = maxOf(squash, minOf(0.35f, vn / 30f))
            sqx = nx
            sqy = ny
        }
    }

    /** Which way the ball is from [cx]: -1 left, 1 right, 0 about in line (for the pet's eyes). */
    fun side(cx: Float): Int = when {
        !active -> 0
        x - cx > 1.5f -> 1
        x - cx < -1.5f -> -1
        else -> 0
    }

    /**
     * Draws the ball: a disc lit from above (against gravity [gx], [gy]), soft at the edge, squashed along its last
     * bounce. Over the pet's body it is in front, but it never covers the face ([cover] from [SpeciesRenderer.render]):
     * there it passes behind.
     */
    fun draw(frame: IntArray, gx: Float, gy: Float, cover: ByteArray) {
        if (!active) return
        val fade = ((LIFE_MS - ageMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
        if (fade <= 0f) return
        val (dx, dy) = down(gx, gy)
        // Light from above and a little to the left, whichever way is up.
        val lx = -dx * 0.8f - dy * 0.45f
        val ly = -dy * 0.8f + dx * 0.45f
        val s = squash
        val r = RADIUS + 1f
        for (cy in floor(y - r).toInt()..ceil(y + r).toInt()) {
            for (cx in floor(x - r).toInt()..ceil(x + r).toInt()) {
                if (!GlyphMatrixSpec.isVisible(cx, cy)) continue
                val i = cy * SIZE + cx
                if (cover[i] == SpeciesRenderer.COVER_FACE) continue
                val ex = cx - x
                val ey = cy - y
                val along = (ex * sqx + ey * sqy) / (1f - s)
                val across = (-ex * sqy + ey * sqx) / (1f + s * 0.6f)
                val d = sqrt(along * along + across * across)
                val alpha = (RADIUS - d + 0.5f).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                val rr = (d / RADIUS).coerceAtMost(1f)
                val nz = sqrt(1f - rr * rr)
                val lam = ((ex * lx + ey * ly) / RADIUS * 0.6f + nz * 0.55f).coerceIn(0f, 1f)
                val value = ((DARK + (LIT - DARK) * lam) * fade).toInt().coerceIn(0, BRIGHT)
                if (alpha >= 0.5f && cover[i] == SpeciesRenderer.COVER_BODY) {
                    // In front of the body: the ball hides what is behind it, which shows again as it fades.
                    frame[i] = maxOf(value, (frame[i] * (1f - fade)).toInt())
                } else {
                    val v = (value * alpha).toInt()
                    if (v > frame[i]) frame[i] = v
                }
            }
        }
    }

    /** Gravity as a unit vector, straight down when the phone lies flat. */
    private fun down(gx: Float, gy: Float): Pair<Float, Float> = Weather.down(gx, gy)
}
