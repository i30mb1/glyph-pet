package n7.glyphpet.toy

import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.ShakeAction

/** What the sensors saw in one frame, already read off [MotionSensor]: for now only whether the phone was shaken. */
data class Gestures(
    val shake: Boolean = false,
)

/** The world as the toy knows it this frame: whether there is a pet to react, how it feels and how the phone is held. */
data class Attention(
    val hasPet: Boolean,
    val eggOnMatrix: Boolean = false,
    val ready: Boolean = false,
    val mood: Mood = Mood.CONTENT,
    val posture: Posture = Posture.HAND,
)

/**
 * One thing the pet does about a gesture. The toy turns each into its store, engine and haptic calls, in the order
 * they come: that order is what [Reactions.frame] decides.
 */
sealed interface Reaction {
    /** A shake while the pet is ready to grow: one shake toward its evolution. */
    data object Grow : Reaction

    /** A shake while it is not: dizzy stars, so the shake always shows it was felt. */
    data object Dizzy : Reaction
}

/**
 * The pet's answer to the gestures of one frame. Pure, so the toy's way of reading the owner can be tried without a
 * phone ([PetToyService] keeps the sensors, the store and the matrix). The pet always answers: whatever the screen
 * shows, a shake is a shake.
 */
class Reactions {

    private val postures = PostureTracker()

    /** How the phone is held, as of the last [posture] call. */
    var heldAs: Posture = Posture.HAND
        private set

    /** Reads how the phone is held this frame. */
    fun posture(pose: Pose?, jitter: Float, now: Long): Posture {
        heldAs = postures.update(pose, jitter, now)
        return heldAs
    }

    /** Starts over: the sensors were just turned on again, so no pose is known. */
    fun reset() {
        postures.reset()
        heldAs = Posture.HAND
    }

    /** What the pet does about [gestures], in the order it does them. A gesture nobody is there for brings nothing. */
    fun frame(gestures: Gestures, at: Attention): List<Reaction> {
        if (!gestures.shake) return emptyList()
        val shake = ButtonRules.shake(hasPet = at.hasPet, eggOnMatrix = at.eggOnMatrix, petReady = at.ready)
        return when {
            shake == ShakeAction.GROW -> listOf(Reaction.Grow)
            at.hasPet && !at.eggOnMatrix -> listOf(Reaction.Dizzy)
            else -> emptyList()
        }
    }
}
