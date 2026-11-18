package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer

/**
 * Ghost, "Лентикулярка": a lenticular card. Leaned one way ([HIDE_SIDE]) only its eyes are left floating in the dark,
 * leaned the other way it is all there; held upright it flickers slowly between the two. Each change sweeps across in
 * column shutters, every other column first, the way a lenticular picture flips.
 */
internal class GhostLenticular : Signature {
    companion object {
        const val DURATION = 5500L
        /** Sideways lean that flips the picture, and the hysteresis around it. */
        const val FLIP = 0.28f
        const val HYSTERESIS = 0.06f
        /** -1: leaning so "down" points left hides it; 1: leaning right does. */
        const val HIDE_SIDE = -1f
        /** One shutter sweep, and how long each picture holds while upright. */
        const val SWITCH_MS = 320L
        const val FLICKER_MS = 1300L
        /** The ghost always ends whole, this long before the end. */
        const val END_WHOLE_MS = 700L
        /** What is left of the body when only the eyes show, and the eyes' brightness then. */
        const val HIDDEN_BODY = 60
        const val HIDDEN_EYES = 3200
    }

    override val durationMs = DURATION
    private var shown = 0 // 0 whole, 1 eyes only
    private var previous = 0
    private var progress = 1f
    private val whole = IntArray(Kit.N * Kit.N)
    private val eyes = IntArray(Kit.N * Kit.N)

    override fun start() {
        shown = 0; previous = 0; progress = 1f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val lean = input.gx * HIDE_SIDE
        val upright = kotlin.math.abs(input.gx) < FLIP - HYSTERESIS
        val want = when {
            input.tMs > DURATION - END_WHOLE_MS -> 0
            shown == 1 && lean > FLIP - HYSTERESIS -> 1
            shown == 0 && lean > FLIP + HYSTERESIS -> 1
            lean < -FLIP + HYSTERESIS && !upright -> 0
            upright -> if ((input.tMs / FLICKER_MS) % 2 == 1L) 1 else 0
            else -> shown
        }
        if (want != shown) {
            previous = shown
            shown = want
            progress = 0f
        }
        progress = (progress + input.dtMs.toFloat() / SWITCH_MS).coerceIn(0f, 1f)

        whole.fill(0)
        Kit.drawPet(whole, input.species, input.face, input.blink, input.dx)
        drawEyes(eyes, input)

        val a = if (previous == 0) whole else eyes
        val b = if (shown == 0) whole else eyes
        // The sweep runs the way the phone leans toward.
        val fromRight = input.gx < 0f
        for (x in 0 until Kit.N) {
            val pos = if (fromRight) Kit.N - 1 - x else x
            val threshold = (x % 2) * 0.5f + pos / Kit.N.toFloat() * 0.5f
            val src = if (progress > threshold) b else a
            for (y in 0 until Kit.N) frame[y * Kit.N + x] = src[y * Kit.N + x]
        }
    }

    /** The eyes alone: the cells carved out of the face's eye slots, lit, over a faint trace of the body. */
    private fun drawEyes(into: IntArray, input: SignatureInput) {
        into.fill(0)
        val sp = input.species
        val grid = SpeciesRenderer.carve(sp, input.face, input.blink, mouth = false)
        val ox = sp.ox + input.dx
        val oy = sp.oy
        for (y in grid.indices) for (x in grid[y].indices) {
            val src = sp.rows[y].getOrNull(x) ?: '.'
            if (src == '.') continue
            val inEye = sp.eyes.any { (ex, ey) -> x - ex in 0..1 && y - ey in 0..1 }
            val carved = grid[y][x]
            val value = when {
                inEye && carved == (if (sp.inverted) 'o' else '.') -> HIDDEN_EYES
                else -> HIDDEN_BODY
            }
            SpeciesRenderer.put(into, ox + x, oy + y, value)
        }
    }
}
