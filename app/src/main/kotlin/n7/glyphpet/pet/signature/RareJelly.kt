package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Jelly, "Глубоководье": the deep sea. Glowing specks drift slowly along gravity in three depths: far ones dim, tiny
 * and slowest, middle ones brighter, near ones bright and quickest, floating in front (never over its face); leaning
 * shifts the far ones most. The jelly pulses: its body glows up and dims in a slow beat with a faint halo at each
 * beat, and it rises and sinks twice.
 */
internal class JellyDeep : Signature {
    companion object {
        const val DURATION = 5600L
        /** Per depth (near, middle, far): count, speed (cells/s), light, parallax at full lean (cells). */
        val COUNT = intArrayOf(4, 6, 9)
        val SPEED = floatArrayOf(1.6f, 0.95f, 0.5f)
        val LIGHT = floatArrayOf(1700f, 700f, 280f)
        val SHIFT = floatArrayOf(0.4f, 1.3f, 2.4f)
        /** Flip to -1 if the specks seem to move the wrong way on the phone. */
        const val SIGN = 1f
        /** The pulse: one beat this long; the body between [DIM] and 1 of its light; the halo at the peak. */
        const val BEAT_MS = 1400f
        const val DIM = 0.5f
        const val HALO = 420f
        /** Rising and sinking: how high (cells) and how many times. */
        const val RISE = 2.5f
        const val RISES = 2
    }

    override val durationMs = DURATION
    private val random = Random(41)
    private val back = Canvas()
    private val front = Canvas()
    private val cover = ByteArray(Kit.N * Kit.N)
    private var baseY = Float.NaN

    /** Specks: x, y, depth, phase. */
    private val specks: Array<FloatArray> = run {
        val list = ArrayList<FloatArray>()
        for (z in 0..2) repeat(COUNT[z]) { list += floatArrayOf(0f, 0f, z.toFloat(), 0f) }
        list.toTypedArray()
    }

    override fun start() {
        baseY = Float.NaN
        for (p in specks) {
            // Anywhere on the matrix to begin with, so the sea is full from the first frame.
            val a = random.nextFloat() * 6.28f
            val r = sqrtUnit(random.nextFloat()) * 11.5f
            p[0] = Kit.C + cos(a) * r
            p[1] = Kit.C + sin(a) * r
            p[3] = random.nextFloat() * 6.28f
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
        front.clear()
        for (p in specks) {
            val z = p[2].toInt()
            val sway = sin(t * (1.1f + z * 0.3f) + p[3]) * 0.4f
            p[0] += (dx * SPEED[z] + dy * sway) * dt
            p[1] += (dy * SPEED[z] - dx * sway) * dt
            val ex = p[0] - Kit.C
            val ey = p[1] - Kit.C
            if (ex * ex + ey * ey > 13f * 13f && ex * dx + ey * dy > 0f) {
                // Gone past the low edge: back in at the high edge, somewhere across.
                val a = (random.nextFloat() - 0.5f) * 18f
                p[0] = Kit.C - dx * 8.5f - dy * a
                p[1] = Kit.C - dy * 8.5f + dx * a
            }
            val glow = 0.55f + 0.45f * sin(t * (1.7f + z * 0.4f) + p[3] * 2f)
            val into = if (z == 0) front else back
            into.splat(p[0] + lx * SHIFT[z] * env, p[1] + ly * SHIFT[z] * env, LIGHT[z] * glow * env)
        }
        back.toFrame(frame)

        // The beat: quick to glow up, slow to dim, like a bell pulsing.
        val ph = (input.tMs % BEAT_MS.toLong()) / BEAT_MS
        val beat = if (ph < 0.25f) Kit.smooth(ph / 0.25f) else 1f - Kit.smooth((ph - 0.25f) / 0.75f)
        val gain = 1f - (1f - (DIM + (1f - DIM) * beat)) * env
        val sp = input.species
        val maxRise = minOf(RISE, (sp.oy - 1).toFloat()).coerceAtLeast(0f)
        val rise = maxRise * (0.5f - 0.5f * cos(2f * PI.toFloat() * RISES * input.tMs / DURATION)) * env
        Kit.drawPet(frame, sp, input.face, input.blink, input.dx, lift = rise.roundToInt(), gain = gain, cover = cover)

        // A faint halo round the bell at each beat.
        val halo = HALO * beat * env
        if (halo > 20f) {
            val n = Kit.N
            for (y in 0 until n) for (x in 0 until n) {
                val i = y * n + x
                if (!Kit.VISIBLE[i] || cover[i] != SpeciesRenderer.COVER_NONE) continue
                val near = (x > 0 && cover[i - 1] != SpeciesRenderer.COVER_NONE) ||
                    (x < n - 1 && cover[i + 1] != SpeciesRenderer.COVER_NONE) ||
                    (y > 0 && cover[i - n] != SpeciesRenderer.COVER_NONE) ||
                    (y < n - 1 && cover[i + n] != SpeciesRenderer.COVER_NONE)
                if (near && halo > frame[i]) frame[i] = halo.toInt()
            }
        }
        for (i in frame.indices) {
            if (!Kit.VISIBLE[i] || cover[i] == SpeciesRenderer.COVER_FACE) continue
            val v = front.v[i].coerceIn(0f, Kit.BRIGHT).toInt()
            if (v > frame[i]) frame[i] = v
        }
    }

    /** Square root for an even spread over a disc. */
    private fun sqrtUnit(k: Float): Float = kotlin.math.sqrt(k.coerceIn(0f, 1f))
}
