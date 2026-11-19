package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Whale, "Аквариум": the matrix becomes a fish tank in three depths. Far back, dim bubbles rise under a wavy water
 * surface that keeps level with the horizon however the phone turns; leaning shifts that back layer the most. In the
 * middle the whale swims slowly to and fro, bobbing. On the glass in front a few bright drops run down along gravity,
 * leaving short tails (never over its face).
 */
internal class WhaleAquarium : Signature {
    companion object {
        const val DURATION = 5600L
        /** How far above the centre the water surface lies (cells, against gravity), its wave and its light. */
        const val SURFACE = 8.2f
        const val WAVE = 0.45f
        const val SURFACE_LIGHT = 620f
        /** Far bubbles: count, speed (cells/s), light. */
        const val BUBBLES = 9
        const val BUBBLE_SPEED = 2.2f
        const val BUBBLE_LIGHT = 380f
        /** Parallax at full lean, cells: the back of the tank, and the whale. Flip [SIGN] if it moves the wrong way. */
        const val FAR_SHIFT = 2.6f
        const val MID_SHIFT = 0.9f
        const val SIGN = 1f
        /** The whale's swim: how far to each side (cells) and its bob. */
        const val SWIM = 3f
        const val BOB = 1f
        /** Drops on the glass: count, speed (cells/s), light. */
        const val DROPS = 4
        const val DROP_SPEED = 2.4f
        const val DROP_LIGHT = 1700f
    }

    override val durationMs = DURATION
    private val random = Random(31)
    private val back = Canvas()
    private val glass = Canvas()
    private val cover = ByteArray(Kit.N * Kit.N)
    private var baseY = Float.NaN

    /** Bubbles: x, y (tank coordinates, before the parallax shift), phase. */
    private val bubbles = Array(BUBBLES) { floatArrayOf(0f, 0f, 0f) }

    /** Drops: x, y, speed factor, wait ms (> 0: not yet on the glass). */
    private val drops = Array(DROPS) { floatArrayOf(0f, 0f, 1f, 0f) }

    override fun start() {
        baseY = Float.NaN
        for (b in bubbles) {
            b[0] = 2f + random.nextFloat() * 21f
            b[1] = 6f + random.nextFloat() * 18f
            b[2] = random.nextFloat() * 6.28f
        }
        for (d in drops) {
            d[3] = 300f + random.nextFloat() * 2200f
            d[0] = Float.NaN
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        if (baseY.isNaN()) baseY = input.gy
        val (dx, dy) = Kit.down(input.gx, input.gy)
        val t = input.tMs / 1000f
        val dt = input.dtMs.coerceIn(0, 200) / 1000f
        val env = Kit.envelope(input.tMs, DURATION, 700, 700)
        val lx = -input.gx.coerceIn(-1f, 1f) * SIGN
        val ly = -(input.gy - baseY).coerceIn(-1f, 1f) * SIGN

        back.clear()
        // Far: the water surface, a wavy line across gravity, SURFACE cells above the centre.
        val sx = Kit.C - dx * SURFACE + lx * FAR_SHIFT * 0.5f
        val sy = Kit.C - dy * SURFACE + ly * FAR_SHIFT * 0.5f
        var s = -13f
        while (s <= 13f) {
            val wave = sin(s * 0.7f + t * 2.4f) * WAVE
            val px = sx - dy * s + dx * wave
            val py = sy + dx * s + dy * wave
            back.splat(px, py, SURFACE_LIGHT * env * 0.55f)
            s += 0.5f
        }
        // Far: bubbles rising against gravity, wobbling; they vanish at the surface.
        for (b in bubbles) {
            val wob = sin(t * 2.1f + b[2]) * 0.5f
            b[0] += (-dx * BUBBLE_SPEED + dy * wob) * dt
            b[1] += (-dy * BUBBLE_SPEED - dx * wob) * dt
            val ex = b[0] - Kit.C
            val ey = b[1] - Kit.C
            val depth = ex * dx + ey * dy
            if (depth < -SURFACE + 0.6f || ex * ex + ey * ey > 170f) respawnBubble(b, dx, dy)
            val tw = 0.7f + 0.3f * sin(t * 3f + b[2])
            back.splat(b[0] + lx * FAR_SHIFT, b[1] + ly * FAR_SHIFT, BUBBLE_LIGHT * tw * env)
        }
        back.toFrame(frame)

        // Middle: the whale, swimming to one side and back, bobbing; it starts and ends at home.
        val swim = sin(2f * PI.toFloat() * input.tMs / DURATION) * SWIM * env
        val bob = sin(t * 2.6f) * BOB * env
        val mx = (swim + lx * MID_SHIFT * env).roundToInt()
        val my = (bob - ly * MID_SHIFT * env).roundToInt()
        Kit.drawPet(frame, input.species, input.face, input.blink, input.dx + mx, lift = my, cover = cover)

        // Near: drops on the glass, running down along gravity with a fading tail.
        glass.clear()
        val spawning = input.tMs < DURATION - 1200
        for (d in drops) {
            if (d[0].isNaN()) {
                if (!spawning) continue
                d[3] -= input.dtMs
                if (d[3] > 0f) continue
                val a = (random.nextFloat() - 0.5f) * 16f
                d[0] = Kit.C - dx * 7f - dy * a
                d[1] = Kit.C - dy * 7f + dx * a
                d[2] = 0.6f + random.nextFloat() * 0.8f
            }
            // A drop on glass sticks a moment, then runs on.
            val go = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * 4f * d[2] + d[2] * 9f))) * d[2]
            d[0] += dx * DROP_SPEED * go * dt
            d[1] += dy * DROP_SPEED * go * dt
            val ex = d[0] - Kit.C
            val ey = d[1] - Kit.C
            if (sqrt(ex * ex + ey * ey) > 12.5f) {
                d[0] = Float.NaN
                d[3] = 200f + random.nextFloat() * 1200f
                continue
            }
            glass.splat(d[0], d[1], DROP_LIGHT * env)
            glass.splat(d[0] - dx * 0.9f, d[1] - dy * 0.9f, DROP_LIGHT * 0.35f * env)
            glass.splat(d[0] - dx * 1.8f, d[1] - dy * 1.8f, DROP_LIGHT * 0.15f * env)
        }
        for (i in frame.indices) {
            if (!Kit.VISIBLE[i] || cover[i] == SpeciesRenderer.COVER_FACE) continue
            val v = glass.v[i].coerceIn(0f, Kit.BRIGHT).toInt()
            if (v > frame[i]) frame[i] = v
        }
    }

    /** Back in at the bottom of the tank (along gravity), somewhere across it. */
    private fun respawnBubble(b: FloatArray, dx: Float, dy: Float) {
        val a = (random.nextFloat() - 0.5f) * 14f
        val r = 7f + random.nextFloat() * 3f
        b[0] = Kit.C + dx * r - dy * a
        b[1] = Kit.C + dy * r + dx * a
    }
}
