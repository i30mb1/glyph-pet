package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Frog, "Пруд": a pond opens behind the frog. Far back, dim rings of ripples spread and fade; nearer, two lily pads
 * float. Leaning the phone shifts each layer by its depth (the ripples most, the pads less), while the frog sits still
 * in front of it all.
 */
internal class FrogPond : Signature {
    companion object {
        const val DURATION = 5500L
        /** How far the ripples shift at full lean, in cells; the pads shift [PAD_DEPTH] of that. */
        const val SHIFT = 3.2f
        const val PAD_DEPTH = 0.5f
        /** Flip to -1 if the pond seems to move the wrong way on the phone. */
        const val SIGN = 1f
        /** Ripple rings: how fast they grow (cells/s), how far, and their light; seen at a slant, so flattened. */
        const val RING_SPEED = 2.6f
        const val RING_MAX = 7f
        const val RING_LIGHT = 420f
        const val RING_FLAT = 0.42f
        const val PAD_LIGHT = 700f
    }

    override val durationMs = DURATION
    private val out = Canvas()
    private var baseY = Float.NaN

    /** Ripple centres (x, y, phase in s). */
    private val ripples = arrayOf(floatArrayOf(6f, 16f, 0f), floatArrayOf(18.5f, 10f, 1.1f), floatArrayOf(13f, 21f, 2.0f))

    /** Lily pads (x, y, half width). */
    private val pads = arrayOf(floatArrayOf(4.5f, 19.5f, 2.4f), floatArrayOf(20f, 15.5f, 2f))

    override fun start() {
        baseY = Float.NaN
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        if (baseY.isNaN()) baseY = input.gy
        val tx = -input.gx.coerceIn(-1f, 1f) * SHIFT * SIGN
        val ty = -(input.gy - baseY).coerceIn(-1f, 1f) * SHIFT * SIGN
        val env = Kit.envelope(input.tMs, DURATION, 700, 700)
        val t = input.tMs / 1000f
        out.clear()

        // Far: the ripples, three rings each, younger ones brighter.
        for (r in ripples) {
            for (ring in 0..2) {
                val age = ((t + r[2] + ring * RING_MAX / RING_SPEED / 3f) * RING_SPEED) % RING_MAX
                if (age < 0.5f) continue
                val fade = (1f - age / RING_MAX)
                val value = RING_LIGHT * fade * env
                val steps = (age * 5f).toInt().coerceIn(8, 40)
                for (s in 0 until steps) {
                    val a = s * 2f * PI.toFloat() / steps
                    out.splat(r[0] + tx + cos(a) * age, r[1] + ty + sin(a) * age * RING_FLAT, value / 2f)
                }
            }
        }
        // Middle: the lily pads, a flat disc with a notch, swaying a little; they hide the ripples behind them.
        for ((k, p) in pads.withIndex()) {
            val cx = p[0] + tx * PAD_DEPTH + sin(t * 0.9f + k) * 0.3f
            val cy = p[1] + ty * PAD_DEPTH
            val hw = p[2]
            val hh = hw * 0.55f
            for (y in (cy - hh - 1).toInt()..(cy + hh + 1).toInt()) for (x in (cx - hw - 1).toInt()..(cx + hw + 1).toInt()) {
                if (x !in 0 until Kit.N || y !in 0 until Kit.N) continue
                val ex = (x - cx) / hw
                val ey = (y - cy) / hh
                val d = ex * ex + ey * ey
                if (d > 1.3f) continue
                // The notch: a wedge cut toward the upper right.
                if (ex > 0.1f && ey < 0f && -ey > ex * 0.6f) continue
                val a = ((1.3f - d) * 2.5f).coerceIn(0f, 1f)
                val i = y * Kit.N + x
                out.v[i] = out.v[i] * (1f - a) + PAD_LIGHT * a * env
            }
        }
        out.toFrame(frame)
        Kit.drawPet(frame, input.species, input.face, input.blink, input.dx)
    }
}
