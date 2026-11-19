package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cactus, "Цветение": a bud on its top opens into a bright flower, holds a moment, and then the petals let go one by
 * one and fall along gravity in three depth layers: near ones bright and quick in front of the cactus, far ones dim,
 * slow and behind it, all swaying as they fall.
 */
internal class CommonCactusBloom : Signature {
    companion object {
        const val DURATION = 3000L
        const val PETALS = 6
        const val OPEN_MS = 850L
        const val FALL_AT = 1450L
        const val FALL_GAP = 130L
        const val FALL_LIFE_S = 1.3f
        const val RADIUS = 1.7f
        /** Seen at a slant: the flower is a flattened ring. */
        const val FLAT = 0.75f
        const val PETAL = 3200f
        const val CORE = 4000f
        const val BUD = 1800f
        /** Per layer (near, middle, far): fall acceleration in cells/s², light, sway. */
        val ACCEL = floatArrayOf(13f, 8f, 4.5f)
        val LIGHT = floatArrayOf(3300f, 1700f, 750f)
        val SWAY = floatArrayOf(1f, 0.7f, 0.45f)
    }

    override val durationMs = DURATION

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val top = CommonKit.topRow(s)
        val cx = s.ox + input.dx + CommonKit.rowMid(s, top)
        val cy = s.oy + top - 1.6f
        val (dx, dy) = Kit.down(input.gx, input.gy)
        val open = Kit.smooth(t.toFloat() / OPEN_MS)
        val end = Kit.envelope(t, DURATION, 1, 300)

        // Where petal [k] is now, and its light, or null once it is gone.
        fun petal(k: Int): Triple<Float, Float, Float>? {
            val a = k * 2f * PI.toFloat() / PETALS + PI.toFloat() / PETALS
            val hx = cx + cos(a) * RADIUS * open
            val hy = cy + sin(a) * RADIUS * open * FLAT
            val fall = (t - FALL_AT - k * FALL_GAP) / 1000f
            if (fall <= 0f) return Triple(hx, hy, PETAL * open)
            if (fall > FALL_LIFE_S) return null
            val layer = k % 3
            val d = 0.5f * ACCEL[layer] * fall * fall
            val sway = sin(fall * 5f + k) * SWAY[layer] * (fall / 0.3f).coerceAtMost(1f)
            val x = hx + dx * d - dy * sway
            val y = hy + dy * d + dx * sway
            return Triple(x, y, LIGHT[layer] * (1f - fall / FALL_LIFE_S) * end)
        }

        // Far petals behind the cactus.
        for (k in 0 until PETALS) if (k % 3 == 2 && t > FALL_AT + k * FALL_GAP) {
            petal(k)?.let { (x, y, v) -> SpeciesRenderer.splat(frame, x, y, v.toInt()) }
        }
        Kit.drawPet(frame, s, input.face, input.blink, input.dx)
        // The flower's heart: a bud that brightens as it opens, dimming once the petals are gone.
        val fallen = ((t - FALL_AT).toFloat() / (PETALS * FALL_GAP + 400)).coerceIn(0f, 1f)
        SpeciesRenderer.splat(frame, cx, cy, ((BUD + (CORE - BUD) * open) * (1f - fallen) * end).toInt())
        for (k in 0 until PETALS) {
            if (k % 3 == 2 && t > FALL_AT + k * FALL_GAP) continue
            petal(k)?.let { (x, y, v) -> SpeciesRenderer.splat(frame, x, y, v.toInt()) }
        }
    }
}
