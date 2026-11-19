package n7.glyphpet.pet.trick

import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Stage
import kotlin.math.abs
import kotlin.math.floor

/**
 * Planet: spin the ray-cast planet's bands with the sideways lean (see [MythicSpinTrick]); the adult's moon is swept
 * along and races round faster the faster the planet turns, with a tick every orbit it completes.
 */
internal class PlanetTrick : MythicSpinTrick() {
    companion object {
        const val MOON_GEAR = 1.4f
    }

    override fun extra(input: SignatureInput, turned: Float, dt: Float) {
        val before = view.moonTurn
        view.moonTurn += abs(turned) * MOON_GEAR
        if (input.species.stage == Stage.ADULT && floor(view.moonTurn / TWO_PI) != floor(before / TWO_PI)) {
            gate.feel(input, HapticKind.BALL_HIT, 0.5f)
        }
    }
}
