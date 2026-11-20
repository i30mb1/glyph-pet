package n7.glyphpet.pet.feast

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.signature.CommonKit
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Raccoon, "Сначала помыть": it will not eat a thing before it has rinsed it. The piece falls in front of its paws, it
 * picks it up, dunks it three times in the puddle under it (rings spreading from every dunk), shakes the drops off,
 * rubs it between its paws until it shines, and only then eats it, bite by bite.
 */
internal class RaccoonWashFood : Signature {
    companion object {
        /** The phases, in the order they play, each ending at the time given. */
        const val FALL_MS = 320L
        const val GRAB_MS = 560L
        const val DIP_MS = 1640L
        const val DROPS_MS = 2040L
        const val RUB_MS = 2700L
        const val EAT_MS = 3220L
        const val DURATION = EAT_MS

        const val DIPS = 3
        /** The puddle: how wide it lies under the paws and how dim its water is. */
        const val PUDDLE = 3
        const val WATER = 1100f
        /** A ring from a dunk: how long it lives, how fast it spreads (cells a second) and how bright its crest is. */
        const val RING_MS = 560f
        const val RING_SPEED = 4.5f
        const val RING = 2600f

        /** How high the piece is held over the puddle, and how far down a dunk takes it. */
        const val HELD = 2.5f
        const val DIP_DEPTH = 2.5f
        /** Drops shaken off: how many and how far they fall. */
        const val DROPS = 3
        const val DROP_FALL = 3.5f
    }

    override val durationMs = DURATION

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val ground = CommonKit.groundRow(s, input.dx)
        val mid = CommonKit.midX(s, input.dx)
        // The paws reach out past the pet's side: the food is always against clear matrix, never lost on the body.
        val handX = (mid + s.width / 2f - 1f).roundToInt()

        // The puddle is only there while it washes.
        val washing = t in GRAB_MS until DROPS_MS
        if (washing) {
            for (d in -PUDDLE..PUDDLE) {
                CommonKit.put(frame, handX + d, ground, WATER * (1f - abs(d) / (PUDDLE + 1f)))
            }
            for (i in 0 until DIPS) {
                val at = GRAB_MS + (i * 2 + 1) * (DIP_MS - GRAB_MS) / (DIPS * 2)
                val age = (t - at).toFloat()
                if (age < 0f || age > RING_MS) continue
                val r = (age / 1000f * RING_SPEED).roundToInt()
                val v = RING * (1f - age / RING_MS)
                CommonKit.put(frame, handX - r, ground, v)
                CommonKit.put(frame, handX + r, ground, v)
            }
        }

        // Where the piece is and how big it still is (it shrinks away as the pet eats it).
        var y = ground - HELD
        var size = 2
        var face = input.face
        when {
            t < FALL_MS -> {
                // Falls in from the top, down to the paws.
                y = -2f + (t.toFloat() / FALL_MS) * (ground - HELD + 2f)
                size = 2
            }
            t < GRAB_MS -> {
                // Caught in both paws and lifted a little toward the muzzle.
                // Content, not HAPPY: that face jumps the pet two rows, away from the puddle drawn under its paws.
                face = Face.CONTENT
                y = ground - HELD - 0.5f
            }
            t < DIP_MS -> {
                // Three dunks: down into the water and back up.
                val each = (DIP_MS - GRAB_MS) / DIPS
                val k = ((t - GRAB_MS) % each).toFloat() / each
                val dip = if (k < 0.5f) k * 2f else (1f - k) * 2f
                y = ground - HELD + dip * DIP_DEPTH
            }
            t < DROPS_MS -> {
                // Shaken: drops fly off downward and fade out.
                val k = (t - DIP_MS).toFloat() / (DROPS_MS - DIP_MS)
                for (i in 0 until DROPS) {
                    val a = (k * 1.6f - i * 0.2f).coerceIn(0f, 1f)
                    if (a <= 0f) continue
                    val dy = a * DROP_FALL
                    val dx = (i - 1) * 1.2f * a
                    SpeciesRenderer.splat(frame, handX + dx, ground - HELD + dy, (Kit.BRIGHT * 0.5f * (1f - a)).toInt())
                }
            }
            t < RUB_MS -> {
                // Rubbed between the paws: it jiggles and starts to shine, blinking every other frame.
                // Content, not HAPPY: that face jumps the pet two rows, away from the puddle drawn under its paws.
                face = Face.CONTENT
                val k = (t - DROPS_MS).toFloat() / (RUB_MS - DROPS_MS)
                y = ground - HELD - 0.5f + (if (((t - DROPS_MS) / 110) % 2 == 0L) 0f else 0.6f)
                size = if (k > 0.5f && ((t - DROPS_MS) / 90) % 2 == 0L) 3 else 2
            }
            else -> {
                // Eaten: the piece shrinks to nothing, the mouth working. [Face.YAWN] is the open mouth without the
                // food badge [Face.EAT] hangs beside the pet: this meal draws its own piece.
                face = if ((t / 160) % 2 == 0L) Face.YAWN else Face.CONTENT
                val k = (t - RUB_MS).toFloat() / (EAT_MS - RUB_MS)
                y = ground - HELD - 1f
                size = (2f * (1f - k)).roundToInt()
            }
        }

        Kit.drawPet(frame, s, face, input.blink, input.dx)
        piece(frame, handX, y, size, if (washing && y > ground - 1f) 0.45f else 1f)
    }

    /** The piece in the paws: a [size] by [size] bright block, dimmed by [gain] while it is under the water. */
    private fun piece(frame: IntArray, x: Int, y: Float, size: Int, gain: Float) {
        if (size <= 0) return
        val v = (Kit.BRIGHT * gain).toInt()
        val top = y.roundToInt()
        val left = x - (size - 1) / 2
        for (a in 0 until size) for (b in 0 until size) SpeciesRenderer.put(frame, left + a, top + b, v)
    }
}
