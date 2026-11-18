package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Weather behind the pet, in three layers for depth: near drops are bright and fast, far ones dim and slow. They flow
 * along gravity (tilt the phone and the rain falls the other way), drawn between LEDs ([SpeciesRenderer.splat]), and
 * come back in on the upwind side of the round matrix once they leave it. Only on LEDs the pet leaves dark.
 */
class Weather(private val random: Random) {

    enum class Kind { NONE, RAIN, SNOW }

    /**
     * One kind of weather: how many particles in the near, middle and far layer, their speed (cells/s), their
     * brightness, the sideways sway (cells/s, the near layer; less further away) and whether a particle trails a
     * short streak behind it.
     */
    private class Look(
        val layers: IntArray,
        val speed: FloatArray,
        val value: IntArray,
        val sway: Float,
        val streak: Boolean,
    )

    companion object {
        private const val SIZE = GlyphMatrixSpec.SIZE
        private const val CENTER = (SIZE - 1) / 2f

        /** Particles further from the middle than this have left the matrix; new ones come in at [SPAWN_R]. */
        private const val GONE_R = 13.2f
        private const val SPAWN_R = 12.8f

        /** A sad pet: rain, ~25 drops, the near ones short streaks. */
        private val RAIN = Look(
            layers = intArrayOf(6, 9, 10),
            speed = floatArrayOf(15f, 9.5f, 5.5f),
            value = intArrayOf(1000, 520, 240),
            sway = 0f,
            streak = true,
        )

        /** A sleeper: a calm, sparse, dim snow drifting down at night. */
        private val SNOW = Look(
            layers = intArrayOf(3, 5, 6),
            speed = floatArrayOf(2.4f, 1.5f, 0.85f),
            value = intArrayOf(460, 260, 140),
            sway = 0.7f,
            streak = false,
        )

        private const val MAX = 25

        /**
         * Gravity ([gx], [gy], the phone's lean) as a unit vector on the matrix. Lying flat it says little, so it
         * blends toward straight down.
         */
        fun down(gx: Float, gy: Float): Pair<Float, Float> {
            val len = hypot(gx, gy)
            val w = (len / 0.35f).coerceIn(0f, 1f)
            val dx = if (len > 1e-4f) gx / len * w else 0f
            val dy = (if (len > 1e-4f) gy / len * w else 0f) + (1f - w)
            val l = hypot(dx, dy)
            return if (l > 1e-4f && !l.isNaN()) (dx / l) to (dy / l) else 0f to 1f
        }
    }

    private var kind = Kind.NONE
    private var count = 0
    private val px = FloatArray(MAX)
    private val py = FloatArray(MAX)
    private val layer = IntArray(MAX)
    private val phase = FloatArray(MAX)
    private var timeS = 0f
    private val skip = BooleanArray(SIZE * SIZE)

    /**
     * Moves the weather on by [dtMs] and draws it into [frame], behind everything already lit there and behind the
     * pet's silhouette ([cover] from [SpeciesRenderer.render], so its dark eye holes stay dark).
     */
    fun draw(frame: IntArray, dtMs: Long, kind: Kind, gx: Float, gy: Float, cover: ByteArray) {
        if (kind != this.kind) start(kind)
        if (kind == Kind.NONE) return
        val look = if (kind == Kind.RAIN) RAIN else SNOW
        val dt = (dtMs / 1000f).coerceIn(0f, 0.25f)
        timeS += dt
        val (dx, dy) = down(gx, gy)
        for (i in frame.indices) skip[i] = frame[i] > 0 || cover[i] != SpeciesRenderer.COVER_NONE
        for (k in 0 until count) {
            val z = layer[k]
            val sp = look.speed[z]
            // Snow sways across the fall, the near flakes most.
            val sway = if (look.sway > 0f) sin(timeS * 1.3f + phase[k]) * look.sway * (3 - z) / 3f else 0f
            px[k] += (dx * sp - dy * sway) * dt
            py[k] += (dy * sp + dx * sway) * dt
            if (hypot(px[k] - CENTER, py[k] - CENTER) > GONE_R) respawn(k, dx, dy)
            val v = look.value[z]
            SpeciesRenderer.splat(frame, px[k], py[k], v, skip)
            if (look.streak && z < 2) SpeciesRenderer.splat(frame, px[k] - dx * 0.9f, py[k] - dy * 0.9f, v * 2 / 5, skip)
        }
    }

    /** A new kind of weather: its particles scattered all over the matrix, so it does not start empty. */
    private fun start(next: Kind) {
        kind = next
        count = 0
        if (next == Kind.NONE) return
        val look = if (next == Kind.RAIN) RAIN else SNOW
        for (z in 0..2) repeat(look.layers[z]) {
            if (count >= MAX) return@repeat
            val a = random.nextFloat() * 2f * PI.toFloat()
            val r = sqrt(random.nextFloat()) * 12f
            px[count] = CENTER + cos(a) * r
            py[count] = CENTER + sin(a) * r
            layer[count] = z
            phase[count] = random.nextFloat() * 6.28f
            count++
        }
    }

    /** Back in on the upwind side: on the rim, somewhere within about 70 degrees of straight up the gravity. */
    private fun respawn(k: Int, dx: Float, dy: Float) {
        val a = atan2(-dy, -dx) + (random.nextFloat() - 0.5f) * 2.4f
        px[k] = CENTER + cos(a) * SPAWN_R
        py[k] = CENTER + sin(a) * SPAWN_R
    }
}
