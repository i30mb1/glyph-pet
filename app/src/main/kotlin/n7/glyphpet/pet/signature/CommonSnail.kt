package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.sin

/**
 * Snail, "Светящийся след": it crawls slowly a few cells forward (its head side), rests, and creeps back home,
 * leaving a glowing trail on the ground under its foot: bright where it has just been, fading behind it, shimmering.
 */
internal class CommonSnailTrail : Signature {
    companion object {
        const val DURATION = 3000L
        /** Cells it crawls; the crawl out, the rest, the way back. */
        const val CELLS = 3
        const val OUT_MS = 1700L
        const val BACK_AT = 2000L
        const val BACK_MS = 850L
        const val GLOW = 2200f
        const val GLOW_LIFE_MS = 2400f
        const val SHIMMER = 0.15f
    }

    override val durationMs = DURATION
    private val touched = LongArray(Kit.N) { Long.MIN_VALUE }

    override fun start() {
        touched.fill(Long.MIN_VALUE)
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        // Forward is the side its eyes are on.
        val eyesX = s.eyes.map { it.first }.average().takeIf { !it.isNaN() } ?: 0.0
        val forward = if (eyesX < s.width / 2.0) -1 else 1
        val step = when {
            t < OUT_MS -> (t * (CELLS + 1) / OUT_MS).toInt().coerceAtMost(CELLS)
            t < BACK_AT -> CELLS
            t < BACK_AT + BACK_MS -> CELLS - ((t - BACK_AT) * (CELLS + 1) / BACK_MS).toInt().coerceAtMost(CELLS)
            else -> 0
        }
        val dx = input.dx + forward * step
        val ground = CommonKit.groundRow(s, input.dx)
        // The foot: the lit cells of the lowest row mark the ground under them as freshly slimed.
        val low = CommonKit.lowestRow(s)
        val row = s.rows[low]
        for (x in row.indices) {
            if (row[x] == '.') continue
            val mx = s.ox + dx + x
            if (mx in 0 until Kit.N) touched[mx] = t
        }
        val end = Kit.envelope(t, DURATION, 1, 450)
        for (x in 0 until Kit.N) {
            if (touched[x] == Long.MIN_VALUE) continue
            val age = (t - touched[x]) / GLOW_LIFE_MS
            if (age >= 1f) continue
            val shimmer = 1f + SHIMMER * sin(t * 0.012f + x * 1.7f)
            CommonKit.put(frame, x, ground, GLOW * (1f - age) * (1f - age * 0.4f) * shimmer * end)
        }
        Kit.drawPet(frame, s, input.face, input.blink, dx)
    }
}
