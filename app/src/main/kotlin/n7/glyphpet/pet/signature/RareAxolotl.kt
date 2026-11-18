package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Axolotl, "Пузыри": bubbles rise around it, always against gravity, in three depths: far ones dim, small and slow
 * behind it, middle ones behind it too, near ones bright little rings that float in front (never over its face).
 * At the round edge the near and middle ones pop.
 */
internal class AxolotlBubbles : Signature {
    companion object {
        const val DURATION = 5200L
        /** Per depth (near, middle, far): count, speed (cells/s), brightness. */
        val COUNT = intArrayOf(4, 5, 7)
        val SPEED = floatArrayOf(6.5f, 4f, 2.2f)
        val LIGHT = floatArrayOf(2600f, 1300f, 480f)
        /** Where bubbles end: the round edge. A pop lasts [POP_MS]. */
        const val EDGE = 11.6f
        const val POP_MS = 200L
        /** No new bubbles this long before the end, so the last ones float off. */
        const val LAST_MS = 1400L
        /** Bubbles come in over this long at the start. */
        const val STAGGER_MS = 1600L
    }

    override val durationMs = DURATION
    private val random = Random(17)
    private val back = Canvas()
    private val front = Canvas()
    private val cover = ByteArray(Kit.N * Kit.N)

    /** Bubbles: x, y, depth, phase, wait ms (> 0: not out yet), pop age ms (>= 0: popping), alive (1/0). */
    private val bubbles: Array<FloatArray> = run {
        val list = ArrayList<FloatArray>()
        for (z in 0..2) repeat(COUNT[z]) { list += floatArrayOf(0f, 0f, z.toFloat(), 0f, 0f, -1f, 0f) }
        list.toTypedArray()
    }

    override fun start() {
        for (b in bubbles) {
            b[4] = random.nextFloat() * STAGGER_MS
            b[5] = -1f
            b[6] = 0f
            b[3] = random.nextFloat() * 6.28f
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val (dx, dy) = Kit.down(input.gx, input.gy)
        val dt = input.dtMs / 1000f
        val t = input.tMs / 1000f
        val spawning = input.tMs < DURATION - LAST_MS
        back.clear()
        front.clear()
        for (b in bubbles) {
            val z = b[2].toInt()
            if (b[6] == 0f) {
                if (!spawning) continue
                b[4] -= input.dtMs
                if (b[4] > 0f) continue
                spawn(b, dx, dy)
            }
            val into = if (z == 0) front else back
            if (b[5] >= 0f) {
                // Popping: a few specks fly apart and fade.
                b[5] += input.dtMs
                val k = b[5] / POP_MS
                if (k >= 1f) { b[6] = 0f; b[4] = random.nextFloat() * 500f; continue }
                val r = 0.6f + 1.2f * k
                val v = LIGHT[z] * (1f - k) * 0.6f
                into.splat(b[0] + r, b[1], v); into.splat(b[0] - r, b[1], v)
                into.splat(b[0], b[1] + r, v); into.splat(b[0], b[1] - r, v)
                continue
            }
            // Up, against gravity, wobbling across it.
            val sp = SPEED[z]
            val wob = sin(t * (3f - z * 0.6f) + b[3]) * 0.9f * (3 - z) / 3f
            b[0] += (-dx * sp + dy * wob) * dt
            b[1] += (-dy * sp - dx * wob) * dt
            val ex = b[0] - Kit.C
            val ey = b[1] - Kit.C
            val dist = sqrt(ex * ex + ey * ey)
            if (dist > EDGE && (ex * dx + ey * dy) < 0f) {
                // Reached the upper edge: the near and middle ones pop, the far ones just go.
                if (z < 2) b[5] = 0f else { b[6] = 0f; b[4] = random.nextFloat() * 400f }
                continue
            }
            if (dist > 13.5f) { b[6] = 0f; b[4] = 0f; continue }
            val v = LIGHT[z]
            if (z == 0) {
                // A little ring: four points round a dark middle.
                val r = 0.75f
                into.splat(b[0] + r, b[1], v * 0.5f); into.splat(b[0] - r, b[1], v * 0.5f)
                into.splat(b[0], b[1] + r, v * 0.5f); into.splat(b[0], b[1] - r, v * 0.5f)
            } else {
                into.splat(b[0], b[1], v)
            }
        }
        back.toFrame(frame)
        Kit.drawPet(frame, input.species, input.face, input.blink, input.dx, cover = cover)
        for (i in frame.indices) {
            if (!Kit.VISIBLE[i] || cover[i] == SpeciesRenderer.COVER_FACE) continue
            val v = front.v[i].coerceIn(0f, Kit.BRIGHT).toInt()
            if (v > frame[i]) frame[i] = v
        }
    }

    /** Out on the low (downwind) side of the circle, somewhere across it. */
    private fun spawn(b: FloatArray, dx: Float, dy: Float) {
        val a = (random.nextFloat() - 0.5f) * 2.4f
        val r = 9.5f + random.nextFloat() * 2f
        val ca = kotlin.math.cos(a)
        val sa = sin(a)
        b[0] = Kit.C + (dx * ca - dy * sa) * r
        b[1] = Kit.C + (dy * ca + dx * sa) * r
        b[5] = -1f
        b[6] = 1f
    }
}
