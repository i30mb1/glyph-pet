package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chameleon, "Маскировка": dim leaves fill the background, shifting with the lean. The chameleon takes on the
 * leaves: its body sinks to their brightness and pattern until only its eye is left, then it comes back.
 */
internal class ChameleonHide : Signature {
    companion object {
        const val DURATION = 6000L
        /** Leaves fade in, the body blends in, stays hidden, comes back, leaves fade out (ms marks). */
        const val HIDE_FROM = 800L
        const val HIDDEN_AT = 2200L
        const val SHOW_FROM = 3900L
        const val SHOWN_AT = 5100L
        /** How far into the leaves the body goes when hidden (1 = exactly their light). */
        const val BLEND = 0.9f
        /** The leaves shift this far at full lean; flip [SIGN] if they move the wrong way. */
        const val SHIFT = 2.4f
        const val SIGN = 1f
        const val LEAF_LIGHT = 520f
        /** The faint light between the leaves, so a hidden body is not just black. */
        const val UNDER = 90f
    }

    override val durationMs = DURATION
    private val leaves = Canvas()
    private var baseY = Float.NaN

    /** Leaves: x, y, angle, half length. A loose tiling a bit wider than the matrix, for the shift. */
    private val pattern = run {
        val r = kotlin.random.Random(41)
        val list = ArrayList<FloatArray>()
        var y = -2f
        var row = 0
        while (y < 27f) {
            var x = -3f + (row % 2) * 2.5f
            while (x < 28f) {
                list += floatArrayOf(x + r.nextFloat() * 1.5f, y + r.nextFloat() * 1.2f, -0.9f + r.nextFloat() * 1.8f + (row % 2) * 1.2f, 1.8f + r.nextFloat() * 1.2f)
                x += 5f
            }
            y += 4f
            row++
        }
        list.toTypedArray()
    }

    override fun start() {
        baseY = Float.NaN
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        if (baseY.isNaN()) baseY = input.gy
        val t = input.tMs / 1000f
        val tx = -input.gx.coerceIn(-1f, 1f) * SHIFT * SIGN
        val ty = -(input.gy - baseY).coerceIn(-1f, 1f) * SHIFT * SIGN
        val env = Kit.envelope(input.tMs, DURATION, 700, 700)
        val tm = input.tMs
        val hide = when {
            tm < HIDE_FROM -> 0f
            tm < HIDDEN_AT -> Kit.smooth((tm - HIDE_FROM).toFloat() / (HIDDEN_AT - HIDE_FROM))
            tm < SHOW_FROM -> 1f
            tm < SHOWN_AT -> 1f - Kit.smooth((tm - SHOW_FROM).toFloat() / (SHOWN_AT - SHOW_FROM))
            else -> 0f
        }

        leaves.clear()
        for ((k, l) in pattern.withIndex()) {
            val cx = l[0] + tx + sin(t * 1.1f + k) * 0.15f
            val cy = l[1] + ty
            val a = l[2]
            val ca = cos(a)
            val sa = sin(a)
            val len = l[3]
            var s = -len
            while (s <= len) {
                val half = 0.75f * (1f - (s / len) * (s / len))
                // The midrib a little brighter than the blade.
                leaves.splat(cx + ca * s, cy + sa * s, LEAF_LIGHT * 0.75f)
                if (half > 0.3f) {
                    leaves.splat(cx + ca * s - sa * half, cy + sa * s + ca * half, LEAF_LIGHT * 0.45f)
                    leaves.splat(cx + ca * s + sa * half, cy + sa * s - ca * half, LEAF_LIGHT * 0.45f)
                }
                s += 0.6f
            }
        }
        for (i in leaves.v.indices) leaves.v[i] = (UNDER + leaves.v[i].coerceAtMost(LEAF_LIGHT * 1.3f)) * env
        leaves.toFrame(frame)

        // The chameleon, its body mixed toward the leaves behind each cell; the bright eye ring and the eye stay.
        val sp = input.species
        val grid = SpeciesRenderer.carve(sp, input.face, input.blink)
        val ox = sp.ox + input.dx
        val oy = sp.oy
        val mix = hide * BLEND
        for (y in grid.indices) for (x in grid[y].indices) {
            val src = sp.rows[y].getOrNull(x) ?: '.'
            if (src == '.') continue
            val mx = ox + x
            val my = oy + y
            if (mx !in 0 until Kit.N || my !in 0 until Kit.N) continue
            val i = my * Kit.N + mx
            if (!Kit.VISIBLE[i]) continue
            frame[i] = when (grid[y][x]) {
                '#' -> (Kit.BODY * (1f - mix) + leaves.v[i] * mix).toInt()
                'o' -> SpeciesRenderer.BRIGHT
                // The eye stays a dark hole; the mouth's line fills with leaves too.
                else -> if (sp.eyes.any { (ex, ey) -> x - ex in 0..1 && y - ey in 0..1 }) 0 else (leaves.v[i] * mix).toInt()
            }.coerceIn(0, SpeciesRenderer.BRIGHT)
        }
    }
}
