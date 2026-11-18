package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dino, "Топот": two stomps. It crouches, jumps high (its shadow on the ground shrinks and fades the higher it is),
 * and lands with a thud: the whole matrix shakes for a few frames and dust specks fly out from its feet and settle.
 */
internal class DinoStomp : Signature {
    companion object {
        const val DURATION = 4400L
        /** When each jump leaves the ground, how long it flies and how high (rows). */
        val JUMPS = longArrayOf(500L, 2500L)
        val HEIGHTS = intArrayOf(6, 4)
        const val FLIGHT = 800L
        const val CROUCH = 250L
        /** The shake after a landing: how long, and how often the frame jumps a cell. */
        const val SHAKE_MS = 420L
        const val SHAKE_STEP = 70L
        /** Dust specks per landing, how long they live, how bright they start. */
        const val DUST = 9
        const val DUST_MS = 900L
        const val DUST_LIGHT = 1500f
        const val SHADOW = 360f
    }

    override val durationMs = DURATION
    private val random = Random(7)
    private val scene = IntArray(Kit.N * Kit.N)
    private val out = Canvas()

    /** Dust: x, y, vx, vy, age ms (age < 0: none). */
    private val dust = Array(DUST * 2) { floatArrayOf(0f, 0f, 0f, 0f, -1f) }
    private var landed = BooleanArray(JUMPS.size)

    override fun start() {
        landed = BooleanArray(JUMPS.size)
        dust.forEach { it[4] = -1f }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        var lift = 0
        var height = 0f
        var shakeLeft = -1L
        for (j in JUMPS.indices) {
            val p = t - JUMPS[j]
            if (p in 0 until FLIGHT) {
                height = HEIGHTS[j] * sin(PI.toFloat() * p / FLIGHT)
                lift = height.toInt()
            } else if (p in -CROUCH until 0) {
                lift = -1 // the crouch: a row down
            }
            if (p >= FLIGHT && !landed[j]) {
                landed[j] = true
                kickDust(sp, input.dx, j)
            }
            val since = p - FLIGHT
            if (since in 0 until SHAKE_MS) shakeLeft = SHAKE_MS - since
        }

        // Feet: the lowest lit row and its span, where the shadow goes.
        val feetRow = sp.rows.indexOfLast { r -> r.any { it != '.' } }
        val feet = sp.rows[feetRow]
        val first = feet.indexOfFirst { it != '.' }
        val last = feet.indexOfLast { it != '.' }
        val cx = sp.ox + input.dx + (first + last) / 2f
        var groundY = sp.oy + feetRow + 1
        if (groundY >= Kit.N || !Kit.VISIBLE[groundY * Kit.N + cx.toInt().coerceIn(0, Kit.N - 1)]) groundY--

        scene.fill(0)
        // The shadow: wide and dark on the ground, narrower and fainter the higher the dino is.
        val hn = (height / HEIGHTS[0]).coerceIn(0f, 1f)
        val hw = maxOf(1.5f, (last - first + 1) / 2f + 1f) * (1f - 0.5f * hn)
        val level = SHADOW * (1f - 0.65f * hn)
        for (x in (cx - hw - 1).toInt()..(cx + hw + 1).toInt()) {
            val d = kotlin.math.abs(x - cx) / hw
            val a = ((1f - d) * 2f).coerceIn(0f, 1f)
            if (a > 0f) n7.glyphpet.pet.SpeciesRenderer.put(scene, x, groundY, (level * a).toInt())
        }
        // Crouching (lift -1) it sits a row lower, its feet sunk into the ground.
        Kit.drawPet(scene, sp, input.face, input.blink, input.dx, lift = lift)

        // Dust specks fly out and fall back, fading.
        out.clear()
        val dt = input.dtMs / 1000f
        for (d in dust) {
            if (d[4] < 0f) continue
            d[4] += input.dtMs
            if (d[4] > DUST_MS) { d[4] = -1f; continue }
            d[3] += 22f * dt
            d[0] += d[2] * dt
            d[1] += d[3] * dt
            if (d[1] > groundY) { d[1] = groundY.toFloat(); d[3] = 0f; d[2] *= 0.6f }
            val k = 1f - d[4] / DUST_MS
            out.splat(d[0], d[1], DUST_LIGHT * k)
        }
        for (i in scene.indices) scene[i] = maxOf(scene[i], out.v[i].coerceIn(0f, Kit.BRIGHT).toInt())

        // The thud: the whole frame jumps a cell this way and that, less and less.
        var sx = 0
        var sy = 0
        if (shakeLeft > 0) {
            val step = (shakeLeft / SHAKE_STEP).toInt()
            sx = if (step % 2 == 0) 1 else -1
            sy = if (step % 3 == 0) 1 else 0
            if (shakeLeft < SHAKE_MS / 3) sy = 0
        }
        for (y in 0 until Kit.N) for (x in 0 until Kit.N) {
            val i = y * Kit.N + x
            if (!Kit.VISIBLE[i]) { frame[i] = 0; continue }
            val fx = x - sx
            val fy = y - sy
            frame[i] = if (fx in 0 until Kit.N && fy in 0 until Kit.N) scene[fy * Kit.N + fx] else 0
        }
    }

    private fun kickDust(sp: n7.glyphpet.pet.Species, dx: Int, jump: Int) {
        val feetRow = sp.rows.indexOfLast { r -> r.any { it != '.' } }
        val feet = sp.rows[feetRow]
        val first = feet.indexOfFirst { it != '.' }
        val last = feet.indexOfLast { it != '.' }
        val y = (sp.oy + feetRow).toFloat()
        for (k in 0 until DUST) {
            val d = dust[jump * DUST + k]
            val left = k % 2 == 0
            d[0] = sp.ox + dx + (if (left) first - 0.5f else last + 0.5f)
            d[1] = y
            val speed = 4f + random.nextFloat() * 6f
            d[2] = if (left) -speed else speed
            d[3] = -(4f + random.nextFloat() * 7f)
            d[4] = 0f
        }
    }
}
