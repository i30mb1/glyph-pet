package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cloud, "Гроза": rain starts in three depths, every streak falling along gravity (far ones dim, short and slow behind
 * the cloud, near ones bright, long and fast in front of it, never over its face). The cloud darkens; then a bolt of
 * lightning strikes from under it to the edge, and for a frame or two the whole matrix flashes white with the cloud a
 * dark silhouette whose eyes shine. A smaller strike follows; the rain goes on, and the cloud brightens again.
 */
internal class CloudStorm : Signature {
    companion object {
        const val DURATION = 5600L
        /** Per depth (near, middle, far): count, speed (cells/s), streak length (cells), light. */
        val COUNT = intArrayOf(5, 8, 12)
        val SPEED = floatArrayOf(15f, 10f, 6f)
        val LENGTH = floatArrayOf(2.2f, 1.5f, 0.9f)
        val LIGHT = floatArrayOf(1500f, 650f, 300f)
        /** The rain comes in over this long and stops falling in this long before the end. */
        const val RAIN_IN_MS = 900L
        const val RAIN_LAST_MS = 700L
        /** The cloud darkens to this much of its light. */
        const val DARK = 0.45f
        /** The strikes: when, how bright the matrix flashes (frames), how long the bolt shows. */
        val STRIKE_MS = longArrayOf(2200L, 3750L)
        val FLASH = floatArrayOf(2900f, 1500f)
        val FLASH_FRAMES = intArrayOf(2, 1)
        const val BOLT_MS = 420L
    }

    override val durationMs = DURATION
    private val random = Random(53)
    private val back = Canvas()
    private val front = Canvas()
    private val cover = ByteArray(Kit.N * Kit.N)
    private val pet = IntArray(Kit.N * Kit.N)

    /** Streaks: x, y, depth, wait ms (> 0: not falling yet). */
    private val streaks: Array<FloatArray> = run {
        val list = ArrayList<FloatArray>()
        for (z in 0..2) repeat(COUNT[z]) { list += floatArrayOf(0f, 0f, z.toFloat(), 0f) }
        list.toTypedArray()
    }

    /** Per strike: flash frames left (-1: not struck yet), and the bolt's points (x, y pairs). */
    private val flashLeft = IntArray(STRIKE_MS.size)
    private val bolts = Array(STRIKE_MS.size) { FloatArray(0) }

    override fun start() {
        flashLeft.fill(-1)
        for (s in streaks) {
            s[3] = random.nextFloat() * RAIN_IN_MS
            s[0] = Float.NaN
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val (dx, dy) = Kit.down(input.gx, input.gy)
        val dt = input.dtMs.coerceIn(0, 200) / 1000f
        val sp = input.species
        val t = input.tMs
        val raining = t < DURATION - RAIN_LAST_MS

        back.clear()
        front.clear()
        for (s in streaks) {
            val z = s[2].toInt()
            if (s[0].isNaN()) {
                if (!raining) continue
                s[3] -= input.dtMs
                if (s[3] > 0f) continue
                // In at the high edge, anywhere across.
                val a = (random.nextFloat() - 0.5f) * 22f
                s[0] = Kit.C - dx * 11f - dy * a
                s[1] = Kit.C - dy * 11f + dx * a
            }
            s[0] += dx * SPEED[z] * dt
            s[1] += dy * SPEED[z] * dt
            val ex = s[0] - Kit.C
            val ey = s[1] - Kit.C
            if (ex * ex + ey * ey > 14f * 14f && ex * dx + ey * dy > 0f) {
                s[0] = Float.NaN
                s[3] = random.nextFloat() * 250f
                continue
            }
            val into = if (z == 0) front else back
            var k = 0f
            while (k <= LENGTH[z]) {
                val fade = 1f - 0.6f * k / LENGTH[z]
                into.splat(s[0] - dx * k, s[1] - dy * k, LIGHT[z] * fade * 0.6f)
                k += 0.5f
            }
        }
        back.toFrame(frame)

        // The cloud darkens over the first second and brightens again at the end.
        val dark = Kit.envelope(t, DURATION, 1100, 900)
        val gain = 1f - (1f - DARK) * dark
        Kit.drawPet(frame, sp, input.face, input.blink, input.dx, gain = gain, cover = cover)
        for (i in frame.indices) {
            if (!Kit.VISIBLE[i] || cover[i] == SpeciesRenderer.COVER_FACE) continue
            val v = front.v[i].coerceIn(0f, Kit.BRIGHT).toInt()
            if (v > frame[i]) frame[i] = v
        }

        for (k in STRIKE_MS.indices) {
            val since = t - STRIKE_MS[k]
            if (since < 0) continue
            if (flashLeft[k] < 0) {
                flashLeft[k] = FLASH_FRAMES[k]
                bolts[k] = bolt(sp, input.dx, dx, dy, k)
            }
            if (since < BOLT_MS) {
                // The bolt flickers once, then fades.
                val on = since < 90 || since > 150
                val fade = if (since > 150) 1f - (since - 150f) / (BOLT_MS - 150f) else 1f
                if (on) drawBolt(frame, bolts[k], Kit.BRIGHT * (0.4f + 0.6f * fade))
            }
            if (flashLeft[k] > 0) {
                flashLeft[k]--
                flash(frame, input, FLASH[k])
            }
        }
    }

    /** The whole matrix lit, the cloud a dark silhouette over it with its eye and mouth holes shining. */
    private fun flash(frame: IntArray, input: SignatureInput, light: Float) {
        pet.fill(0)
        cover.fill(SpeciesRenderer.COVER_NONE)
        SpeciesRenderer.render(input.species, input.face, blink = input.blink, dx = input.dx, frame = pet, cover = cover)
        val v = light.toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
        for (i in frame.indices) {
            if (!Kit.VISIBLE[i]) continue
            frame[i] = when {
                cover[i] == SpeciesRenderer.COVER_NONE -> maxOf(v, frame[i])
                pet[i] > 0 -> 0
                else -> SpeciesRenderer.BRIGHT
            }
        }
    }

    /** A zigzag from under the middle of the cloud along gravity to the round edge. */
    private fun bolt(sp: Species, petDx: Int, dx: Float, dy: Float, k: Int): FloatArray {
        val bottom = sp.rows.indexOfLast { r -> r.count { it == '#' } > 3 }.coerceAtLeast(0)
        val side = if (k == 0) 0f else (if (random.nextBoolean()) 3f else -3f)
        var x = sp.ox + petDx + sp.width / 2f + side + (random.nextFloat() - 0.5f) * 2f
        var y = (sp.oy + bottom + 1).toFloat()
        val pts = ArrayList<Float>()
        var guard = 0
        while (guard++ < 40) {
            pts += x
            pts += y
            val ex = x - Kit.C
            val ey = y - Kit.C
            if (sqrt(ex * ex + ey * ey) > 12.6f) break
            val jog = (random.nextFloat() - 0.5f) * 2.4f
            x += dx * 1.6f - dy * jog
            y += dy * 1.6f + dx * jog
        }
        return pts.toFloatArray()
    }

    private fun drawBolt(frame: IntArray, pts: FloatArray, light: Float) {
        var i = 0
        while (i + 3 < pts.size) {
            val x0 = pts[i]
            val y0 = pts[i + 1]
            val x1 = pts[i + 2]
            val y1 = pts[i + 3]
            val steps = 4
            for (s in 0..steps) {
                val k = s / steps.toFloat()
                SpeciesRenderer.put(frame, (x0 + (x1 - x0) * k).toInt(), (y0 + (y1 - y0) * k).toInt(), light.toInt().coerceIn(0, SpeciesRenderer.BRIGHT))
            }
            i += 2
        }
    }
}
