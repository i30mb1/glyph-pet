package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Raccoon, "Полоскун": it produces something shiny out of thin air, dunks it in a puddle at its feet (rings spread
 * from every dunk), holds it up to the light and turns it until it glints, and tucks it away behind its back.
 */
internal class CommonRaccoonWash : Signature {
    companion object {
        const val DURATION = 3000L

        /** The find, the three dunks, the look at the light and the way behind its back. */
        const val FOUND_MS = 400L
        const val DUNK_FROM = 500L
        const val DUNK_MS = 420L
        const val DUNKS = 3
        const val LIGHT_AT = DUNK_FROM + DUNKS * DUNK_MS
        const val LIGHT_MS = 780L
        const val HIDE_AT = LIGHT_AT + LIGHT_MS

        /** The puddle: how wide it lies under the paws and how dim its water is. */
        const val PUDDLE = 3.5f
        const val WATER = 1100f

        /** A ring from a dunk: how long it lives, how fast it spreads (cells a second) and how bright its crest is. */
        const val RING_MS = 620f
        const val RING_SPEED = 4.5f
        const val RING = 2600f

        /** How far above the ground the shiny is held while it is washed and up in the light. */
        const val HELD = 1.4f
        const val UP = 8f
    }

    override val durationMs = DURATION

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val ground = CommonKit.groundRow(s, input.dx)
        val mid = CommonKit.midX(s, input.dx)
        // The paws reach out past the pet's side, so the shiny is always against clear matrix, never lost on the body.
        val handX = mid + s.width / 2f - 1f

        // The puddle lies under the paws the whole time, dimmest at its edges. On whole cells: a splatted line of
        // water spreads over two rows and goes too dim to read.
        val pool = handX.roundToInt()
        for (d in -PUDDLE.toInt()..PUDDLE.toInt()) {
            val fade = 1f - abs(d) / (PUDDLE + 1f)
            CommonKit.put(frame, pool + d, ground, WATER * fade)
        }

        // Where the shiny is now, and whether it is under the water this frame.
        var shinyY = ground - HELD
        var shinyX = handX
        var dunked = false
        var glint = false
        when {
            t < FOUND_MS -> {
                // Produced out of thin air: it fades in over the paws.
                shinyY = ground - HELD - 0.8f
            }
            t < LIGHT_AT -> {
                val k = ((t - DUNK_FROM) % DUNK_MS).toFloat() / DUNK_MS
                // Down into the water and back up, twice as fast down as up.
                val dip = if (k < 0.5f) k * 2f else (1f - k) * 2f
                shinyY = ground - HELD + dip * (HELD + 0.6f)
                dunked = dip > 0.75f
            }
            t < HIDE_AT -> {
                val k = (t - LIGHT_AT).toFloat() / LIGHT_MS
                shinyY = ground - HELD - Kit.smooth(k) * (UP - HELD)
                // Turned in the light: it catches it twice on the way up, each time for a good long moment.
                glint = sin(k * 12.6f) > 0.35f
            }
            else -> {
                // Behind its back: down and in toward the body, where the pet draws over it, dimming as it goes.
                val k = (t - HIDE_AT).toFloat() / (DURATION - HIDE_AT)
                shinyX = handX - k * (handX - mid)
                shinyY = ground - UP + k * (UP - HELD)
            }
        }

        // Rings over the water from every dunk already made.
        for (i in 0 until DUNKS) {
            val at = DUNK_FROM + i * DUNK_MS + DUNK_MS / 2
            val age = (t - at).toFloat()
            if (age < 0f || age > RING_MS) continue
            val r = (age / 1000f * RING_SPEED).roundToInt()
            val v = RING * (1f - age / RING_MS)
            CommonKit.put(frame, pool - r, ground, v)
            CommonKit.put(frame, pool + r, ground, v)
        }

        // Up in the light it is in front of the pet; down in the puddle the pet's paws are in front of it.
        val fade = if (t < FOUND_MS) t.toFloat() / FOUND_MS else if (t > HIDE_AT) 1f - (t - HIDE_AT).toFloat() / (DURATION - HIDE_AT) else 1f
        val light = Kit.BRIGHT * fade.coerceIn(0f, 1f) * (if (dunked) 0.45f else 1f)
        if (t >= HIDE_AT) shiny(frame, shinyX, shinyY, light, glint)
        val face = if (t in LIGHT_AT until HIDE_AT) Face.HAPPY else input.face
        Kit.drawPet(frame, s, face, input.blink, input.dx)
        if (t < HIDE_AT) shiny(frame, shinyX, shinyY, light, glint)
    }

    /**
     * The shiny: one bright LED, with four sparks around it when it catches the light. On whole cells, not splatted:
     * a speck spread over two LEDs reads as a smudge, not as something held in a paw.
     */
    private fun shiny(frame: IntArray, x: Float, y: Float, light: Float, glint: Boolean) {
        if (light <= 0f) return
        val cx = x.roundToInt()
        val cy = y.roundToInt()
        SpeciesRenderer.put(frame, cx, cy, light.toInt())
        if (!glint) return
        val v = (light * 0.45f).toInt()
        for ((sx, sy) in listOf(cx - 2 to cy, cx + 2 to cy, cx to cy - 2, cx to cy + 2)) {
            SpeciesRenderer.put(frame, sx, sy, v)
        }
    }
}
