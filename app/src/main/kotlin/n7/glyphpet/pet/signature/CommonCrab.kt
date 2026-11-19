package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.sin
import kotlin.random.Random

/**
 * Crab, "Пузыри из песка": bubbles come out of the sand around the crab, rise against gravity with a wobble, grow and
 * pop into a little ring of droplets; near ones bright in front of the crab, far ones dim behind it. Twice it snaps
 * its claws: snap-snap.
 */
internal class CommonCrabBubbles : Signature {
    companion object {
        const val DURATION = 3000L
        const val COUNT = 16
        const val EMIT_MS = 2100L
        /** Rise speed (cells/s) and life (s), each with a random spread on top. */
        const val SPEED = 3.2f
        const val SPEED_SPREAD = 2f
        const val LIFE = 0.6f
        const val LIFE_SPREAD = 0.4f
        const val POP_S = 0.14f
        const val NEAR = 2100f
        const val FAR = 700f
        /** Claw snaps: when each starts and how long a claw stays shut. */
        val SNAPS = longArrayOf(350, 650, 1750, 2050)
        const val SNAP_MS = 150L
    }

    override val durationMs = DURATION
    private val birth = LongArray(COUNT)
    private val offset = FloatArray(COUNT)
    private val speed = FloatArray(COUNT)
    private val life = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)

    init {
        start()
    }

    override fun start() {
        val r = Random(System.nanoTime())
        for (i in 0 until COUNT) {
            birth[i] = i * EMIT_MS / COUNT + r.nextLong(0, 80)
            offset[i] = r.nextFloat() * 2f - 1f
            speed[i] = SPEED + r.nextFloat() * SPEED_SPREAD
            life[i] = LIFE + r.nextFloat() * LIFE_SPREAD
            phase[i] = r.nextFloat() * 6.28f
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val ground = CommonKit.groundRow(s, input.dx)
        val mid = CommonKit.midX(s, input.dx)
        val spread = s.width / 2f + 3f
        val (dx, dy) = Kit.down(input.gx, input.gy)

        fun bubble(i: Int) {
            val age = (t - birth[i]) / 1000f
            if (age < 0f || age > life[i] + POP_S) return
            val near = i % 2 == 0
            val light = if (near) NEAR else FAR
            val a = age.coerceAtMost(life[i])
            val wob = sin(a * 9f + phase[i]) * 0.45f
            val x = mid + offset[i] * spread - dx * speed[i] * a - dy * wob
            val y = ground - 0.2f - dy * speed[i] * a + dx * wob
            if (age <= life[i]) {
                val r = 0.3f + 0.6f * (age / life[i])
                if (r < 0.6f) {
                    SpeciesRenderer.splat(frame, x, y, light.toInt())
                } else {
                    val v = (light * 0.7f).toInt()
                    SpeciesRenderer.splat(frame, x - r, y, v)
                    SpeciesRenderer.splat(frame, x + r, y, v)
                    SpeciesRenderer.splat(frame, x, y - r, v)
                    SpeciesRenderer.splat(frame, x, y + r, v)
                }
            } else {
                // Pop: four droplets fly out and fade.
                val k = (age - life[i]) / POP_S
                val r = 0.9f + 1.1f * k
                val v = (light * 0.55f * (1f - k)).toInt()
                SpeciesRenderer.splat(frame, x - r * 0.7f, y - r * 0.7f, v)
                SpeciesRenderer.splat(frame, x + r * 0.7f, y - r * 0.7f, v)
                SpeciesRenderer.splat(frame, x - r * 0.7f, y + r * 0.7f, v)
                SpeciesRenderer.splat(frame, x + r * 0.7f, y + r * 0.7f, v)
            }
        }

        for (i in 0 until COUNT) if (i % 2 == 1) bubble(i)
        val snapping = SNAPS.any { t in it until it + SNAP_MS }
        val pincers = if (snapping) pincers(s) else emptyList()
        Kit.drawPet(frame, s, input.face, input.blink, input.dx, lift = if (snapping && pincers.isEmpty()) 1 else 0)
        // A snap: each open pincer "o.o" shuts into one bright tip.
        for ((px, py) in pincers) {
            val x = s.ox + input.dx + px
            val y = s.oy + py
            for (cx in intArrayOf(x - 1, x + 1)) {
                if (cx in 0 until Kit.N && y in 0 until Kit.N) frame[y * Kit.N + cx] = 0
            }
            CommonKit.put(frame, x, y, Kit.BRIGHT)
        }
        for (i in 0 until COUNT) if (i % 2 == 0) bubble(i)
    }

    /** The middles of the open pincers ("o.o") in the top rows of [s]'s sprite. */
    private fun pincers(s: Species): List<Pair<Int, Int>> = buildList {
        for (y in 0 until minOf(3, s.height)) {
            val row = s.rows[y]
            for (x in 0 until row.length - 2) {
                if (row[x] == 'o' && row[x + 1] == '.' && row[x + 2] == 'o') add(x + 1 to y)
            }
        }
    }
}
