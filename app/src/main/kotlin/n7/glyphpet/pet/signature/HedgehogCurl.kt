package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Ball
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The hedgehog plays without a ball: rocked, it curls up into a spiky ball itself and rolls around the round matrix
 * with the lean for the ball's life ([Ball.LIFE_MS]), the spines and nose turning as it rolls. Near the end it rolls
 * back home and uncurls. (The Lab's "Клубок", sized to the form.)
 */
class HedgehogCurl {
    companion object {
        const val KIND = "hedgehog"
        /** Ball radius per cell of the form's width (teen ≈ 3.4 cells). */
        const val RADIUS_PER_WIDTH = 0.23f
        const val RIM = 12.3f
        const val GRAVITY = 38f
        const val DAMPING = 0.55f
        const val BOUNCE = 0.5f
        const val KICK = 12f
        /** Curling up and uncurling (crossfade with the pet), and rolling home before that. */
        const val CURL_MS = 300L
        const val HOME_MS = 1100L
        /** Shading of the ball's body, its spines and its shadow. */
        const val DARK = 640f
        const val LIT = 1120f
        const val SPINE = 2400f
        const val SHADOW = 220f

        /** The ball's radius for [species], in cells. */
        fun radius(species: Species): Float = (species.width * RADIUS_PER_WIDTH).coerceIn(2.2f, 4.6f)

        /**
         * The curled-up ball at ([x], [y]) over what [frame] holds: its shadow, the shaded body, the spines and nose
         * turned by [roll], all at [curled] (0..1) strength, the radius shrinking with it. [squash] flattens it along
         * ([sqx], [sqy]) after a hit; ([gx], [gy]) is the way down, for the light and the shadow.
         */
        fun drawBall(
            frame: IntArray,
            species: Species,
            x: Float,
            y: Float,
            roll: Float,
            curled: Float,
            gx: Float,
            gy: Float,
            squash: Float = 0f,
            sqx: Float = 1f,
            sqy: Float = 0f,
        ) {
            if (curled <= 0f) return
            val r = radius(species) * (0.6f + 0.4f * curled)
            val (ddx, ddy) = Kit.down(gx, gy)
            // Light from above, whichever way up is, a little from the left.
            val lx = -ddx * 0.8f - ddy * 0.45f
            val ly = -ddy * 0.8f + ddx * 0.45f
            // The shadow, a little "below" the ball.
            val sx = x + ddx * 1.1f
            val sy = y + ddy * 1.1f
            val box = (r + 2f).roundToInt()
            val cx0 = x.roundToInt()
            val cy0 = y.roundToInt()
            for (py in cy0 - box..cy0 + box) for (px in cx0 - box..cx0 + box) {
                if (px !in 0 until Kit.N || py !in 0 until Kit.N) continue
                val i = py * Kit.N + px
                if (!Kit.VISIBLE[i]) continue
                val ex = px - x
                val ey = py - y
                val along = (ex * sqx + ey * sqy) / (1 - squash)
                val across = (-ex * sqy + ey * sqx) / (1 + squash * 0.6f)
                val r2 = (along * along + across * across) / (r * r)
                if (r2 > 1f) {
                    val hx = px - sx
                    val hy = py - sy
                    if (hx * hx + hy * hy < r * r * 1.05f) frame[i] = maxOf(frame[i], (SHADOW * curled).toInt())
                    continue
                }
                val nz = sqrt(1f - r2)
                val lam = ((ex * lx + ey * ly) / r * 0.6f + nz * 0.6f).coerceAtLeast(0f)
                val edge = ((1f - r2) * 4f).coerceIn(0f, 1f) * curled
                frame[i] = ((DARK + LIT * lam) * edge + frame[i] * (1 - edge)).toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
            }
            // Spines and the nose turn with the rolling: that is how it reads as rolling, not sliding.
            val spines = (r * 2.6f).roundToInt().coerceIn(6, 12)
            for (k in 0 until spines) {
                val a = roll + k * 2f * PI.toFloat() / spines
                SpeciesRenderer.splat(frame, x + cos(a) * (r + 0.7f), y + sin(a) * (r + 0.7f), (SPINE * curled).toInt())
            }
            SpeciesRenderer.splat(frame, x + cos(roll + 1f) * r * 0.47f, y + sin(roll + 1f) * r * 0.47f, (SpeciesRenderer.BRIGHT * curled).toInt())
        }
    }

    private var x = Kit.C
    private var y = Kit.C
    private var vx = 0f
    private var vy = 0f
    private var roll = 0f
    private var squash = 0f
    private var sqx = 1f
    private var sqy = 0f
    private var ageMs = -1L
    private var sinceSpawn = 0L
    private val pet = IntArray(Kit.N * Kit.N)

    val active: Boolean get() = ageMs in 0 until Ball.LIFE_MS

    /** Curls up where the pet is, ([atX], [atY]) its middle, with a little hop against gravity. */
    fun spawn(atX: Float, atY: Float, gx: Float, gy: Float) {
        x = atX; y = atY
        val (dx, dy) = Kit.down(gx, gy)
        vx = -dx * 4f; vy = -dy * 4f
        squash = 0f
        ageMs = 0
        sinceSpawn = 0
    }

    /** Rocked again while curled: a push up against gravity, and another full life. */
    fun kick(gx: Float, gy: Float) {
        if (!active) return
        val (dx, dy) = Kit.down(gx, gy)
        val side = if ((ageMs / 100) % 2 == 0L) 1f else -1f
        vx += -dx * KICK - dy * side * KICK * 0.5f
        vy += -dy * KICK + dx * side * KICK * 0.5f
        ageMs = 0
    }

    fun clear() {
        ageMs = -1
    }

    /** Rolls on under gravity ([gx], [gy]); in its last [HOME_MS] it rolls back to ([homeX], [homeY]). */
    fun update(dtMs: Long, gx: Float, gy: Float, species: Species, homeX: Float, homeY: Float) {
        if (!active) return
        ageMs += dtMs
        sinceSpawn += dtMs
        if (ageMs >= Ball.LIFE_MS) { clear(); return }
        val r = radius(species)
        val left = Ball.LIFE_MS - ageMs
        var rest = dtMs / 1000f
        while (rest > 0f) {
            val dt = minOf(rest, 1f / 30f)
            rest -= dt
            if (left < HOME_MS) {
                // Home: pulled toward the pet's spot, the speed dying away.
                val k = 1f - 0.0005f.pow(dt)
                x += (homeX - x) * k * 1.6f
                y += (homeY - y) * k * 1.6f
                vx *= 0.2f.pow(dt); vy *= 0.2f.pow(dt)
                roll += (homeX - x) * 0.02f
            } else {
                vx += gx * GRAVITY * dt
                vy += gy * GRAVITY * dt
                val keep = DAMPING.pow(dt)
                vx *= keep; vy *= keep
                x += vx * dt; y += vy * dt
                bounce(r)
                val (ddx, ddy) = Kit.down(gx, gy)
                roll += (ddy * vx - ddx * vy) / r * dt
            }
            squash *= 0.002f.pow(dt)
        }
        if (abs(roll) > 1000f) roll %= (2 * PI.toFloat())
        if (x.isNaN() || y.isNaN()) { x = Kit.C; y = Kit.C; vx = 0f; vy = 0f }
    }

    private fun bounce(r: Float) {
        val lim = RIM - r
        val dx = x - Kit.C
        val dy = y - Kit.C
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= lim || dist == 0f) return
        val nx = dx / dist
        val ny = dy / dist
        x = Kit.C + nx * lim; y = Kit.C + ny * lim
        val vn = vx * nx + vy * ny
        if (vn > 0f) {
            vx -= (1 + BOUNCE) * vn * nx
            vy -= (1 + BOUNCE) * vn * ny
            if (vn > 3f) { squash = (vn / 25f).coerceAtMost(0.45f); sqx = nx; sqy = ny }
        }
    }

    /**
     * Draws the whole frame: the rolling ball, crossfading with the pet ([species] with [face] at [dx]) while it curls
     * up at the start and uncurls at the end.
     */
    fun draw(frame: IntArray, species: Species, face: Face, blink: Boolean, dx: Int, gx: Float, gy: Float) {
        val age = ageMs.coerceAtLeast(0)
        val left = Ball.LIFE_MS - age
        val curled = when {
            left < CURL_MS -> left.toFloat() / CURL_MS
            sinceSpawn < CURL_MS -> sinceSpawn.toFloat() / CURL_MS
            else -> 1f
        }.coerceIn(0f, 1f)
        frame.fill(0)
        if (curled < 1f) {
            pet.fill(0)
            SpeciesRenderer.render(species, face, blink = blink, dx = dx, frame = pet)
            for (i in frame.indices) frame[i] = (pet[i] * (1f - curled)).toInt()
        }
        drawBall(frame, species, x, y, roll, curled, gx, gy, squash, sqx, sqy)
    }
}
