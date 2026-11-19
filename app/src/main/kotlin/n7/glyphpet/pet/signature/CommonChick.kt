package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.roundToInt

/**
 * Chick, "Зёрнышки": three grains drop one after another to the ground around its feet, bounce once, and the chick
 * pecks them one by one: it leans toward a grain, bobs down onto it, and the grain is gone.
 */
internal class CommonChickGrains : Signature {
    companion object {
        const val DURATION = 3000L
        /** Where the grains land, cells from the chick's middle, and when each starts to fall. */
        val OFFSETS = floatArrayOf(-3f, 1f, 4f)
        const val DROP_GAP = 180L
        const val FALL_MS = 420L
        const val FALL_FROM = 9f
        const val BOUNCE_MS = 140L
        /** The pecks: the first one, the gap between them, how long each takes. */
        const val PECK_AT = 1000L
        const val PECK_GAP = 620L
        const val PECK_MS = 420L
        const val GRAIN = 1000f
    }

    override val durationMs = DURATION

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val ground = CommonKit.groundRow(s, input.dx)
        val mid = CommonKit.midX(s, input.dx)
        var lean = 0
        var lift = 0
        for (i in OFFSETS.indices) {
            val gx = (mid + OFFSETS[i]).roundToInt()
            val peck = t - (PECK_AT + i * PECK_GAP)
            // The peck: lean toward the grain, bob down onto it, come back up.
            if (peck in 0 until PECK_MS) {
                lean = ((gx - mid) / 2f).roundToInt().coerceIn(-2, 2)
                if (peck in 140 until 300) lift = -1
            }
            if (peck >= 220) continue // eaten
            val fall = t - i * DROP_GAP
            if (fall < 0) continue
            val y = when {
                fall < FALL_MS -> {
                    val k = fall.toFloat() / FALL_MS
                    ground - FALL_FROM * (1f - k * k)
                }
                fall < FALL_MS + BOUNCE_MS -> {
                    val k = (fall - FALL_MS).toFloat() / BOUNCE_MS
                    ground - 1f * 4f * k * (1f - k)
                }
                else -> ground.toFloat()
            }
            if (y >= ground - 0.01f) CommonKit.put(frame, gx, ground, GRAIN)
            else n7.glyphpet.pet.SpeciesRenderer.splat(frame, gx.toFloat(), y, GRAIN.toInt())
        }
        Kit.drawPet(frame, s, input.face, input.blink, input.dx + lean, lift = lift)
    }
}
