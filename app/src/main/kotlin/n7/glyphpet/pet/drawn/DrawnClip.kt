package n7.glyphpet.pet.drawn

import n7.glyphpet.pet.HapticEvent
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput

/**
 * A drawn hero's clip played as a [Signature]: its game (a trick), its signature effect or its meal. It has the whole
 * matrix for [durationMs], looping its clip, and plays along with the lean of the phone.
 */
class DrawnClip(private val kind: String, private val col: String, override val durationMs: Long) : Signature {
    private var lastBeat = -1L

    override fun draw(frame: IntArray, input: SignatureInput) {
        Drawn.frame(kind, col, input.tMs, input.species.stage, input.gx.coerceIn(-1f, 1f), frame)
        // A game ticks on the beat of its clip: a little tap each time round, like points adding up.
        if (col == "game") {
            val beat = input.tMs / 1600
            if (beat != lastBeat && lastBeat >= 0) input.feel(HapticEvent(HapticKind.PLAY, 0.5f))
            lastBeat = beat
        }
    }

    companion object {
        const val GAME_MS = 45_000L
        fun game(kind: String) = DrawnClip(kind, "game", GAME_MS)
        fun signature(kind: String) = DrawnClip(kind, "signature", Drawn.length("signature"))
        fun meal(kind: String) = DrawnClip(kind, "eating", Drawn.length("eating"))
    }
}
