package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bee, "Облёт цветка": a little flower comes up at the bottom middle (a bright cross on a dim stem) and the bee flies
 * round it twice on a slanted ellipse in 3D: on the far side it is small, dim and higher up, hidden behind the
 * flower; on the near side it is big and bright, lower, in front of it. It sets off from its spot and lands back on it.
 */
internal class BeeOrbit : Signature {
    companion object {
        const val DURATION = 6000L
        /** Flying out from its spot and back to it. */
        const val OUT_MS = 650L
        /** Laps round the flower. */
        const val LAPS = 2
        /** The flower's head (centre of the cross), its light and its stem's. */
        const val FLOWER_X = 12f
        const val FLOWER_Y = 17f
        const val FLOWER_LIGHT = 3600f
        const val PETAL_LIGHT = 2300f
        const val STEM_LIGHT = 650f
        /** The ellipse: half width, and how far the bee's feet go up (far) and down (near) of [ORBIT_Y]. */
        const val ORBIT_X = 6.5f
        const val ORBIT_Y = 20f
        const val ORBIT_DEPTH = 2.4f
        /** Far side: the bee shrinks to this height (cells) and this much of its light. */
        const val FAR_HEIGHT = 6.5f
        const val FAR_LIGHT = 0.35f
        /** Near side: at most this tall (cells), so a big form still fits round the flower. */
        const val NEAR_HEIGHT = 13f
        /** The wings' buzz: a slight shiver up and down. */
        const val BUZZ = 0.25f
    }

    override val durationMs = DURATION
    private val out = Canvas()
    private val bee = Canvas()

    override fun draw(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val env = Kit.envelope(t, DURATION, OUT_MS, OUT_MS)
        val lap = ((t - OUT_MS / 2).toFloat() / (DURATION - OUT_MS)).coerceIn(0f, 1f)
        // Smooth laps: slow to set off, slow to come home; it starts and ends at the front.
        val phi = 2f * PI.toFloat() * LAPS * Kit.smooth(lap)
        val z = cos(phi)
        val near = (z + 1f) / 2f

        val h = sp.height.toFloat()
        val sNear = minOf(1f, NEAR_HEIGHT / h)
        val sFar = minOf(sNear * 0.75f, FAR_HEIGHT / h)
        val orbitScale = sFar + (sNear - sFar) * near
        val homeX = sp.ox + input.dx + sp.width / 2f
        val homeY = (sp.oy + sp.height).toFloat()
        val orbitX = FLOWER_X + 0.5f + sin(phi) * ORBIT_X
        val orbitY = ORBIT_Y + z * ORBIT_DEPTH
        val buzz = sin(t / 1000f * 38f) * BUZZ * env
        val ax = homeX + (orbitX - homeX) * env
        val ay = homeY + (orbitY - homeY) * env + buzz
        val scale = 1f + (orbitScale - 1f) * env
        val gain = 1f - (1f - (FAR_LIGHT + (1f - FAR_LIGHT) * near)) * env

        bee.clear()
        if (env <= 0.001f) {
            bee.sprite(Kit.sprite(sp, input.face, input.blink), sp.ox + input.dx, sp.oy)
        } else {
            bee.spriteScaled(Kit.sprite(sp, input.face, input.blink), ax, ay, scale, scale, gain)
        }

        out.clear()
        val front = z >= 0f
        if (front) flower(out, env)
        // The bee hides what is behind it.
        for (i in out.v.indices) {
            val c = bee.cov[i]
            if (c > 0f) out.v[i] = out.v[i] * (1f - c) + bee.v[i]
        }
        if (!front) flower(out, env)
        out.toFrame(frame)
    }

    /** The flower: a bright cross, a dot of light in the middle, a dim stem and two leaves. */
    private fun flower(into: Canvas, env: Float) {
        if (env <= 0.01f) return
        val x = FLOWER_X.toInt()
        val y = FLOWER_Y.toInt()
        for (s in 1..5) into.max(x, y + s, STEM_LIGHT * env)
        into.max(x - 1, y + 3, STEM_LIGHT * env)
        into.max(x + 1, y + 4, STEM_LIGHT * env)
        // The head covers what is behind it.
        for ((cx, cy) in listOf(x to y, x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
            if (cx !in 0 until Kit.N || cy !in 0 until Kit.N) continue
            val i = cy * Kit.N + cx
            into.v[i] = (if (cx == x && cy == y) FLOWER_LIGHT else PETAL_LIGHT) * env
            into.cov[i] = 1f
        }
    }
}
