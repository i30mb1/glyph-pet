package n7.glyphpet.toy

import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.ShakeAction

/**
 * What the sensors saw in one frame, already read off [MotionSensor]: a shake, a twirl, a gentle rock, and how many
 * knocks (0 none, 1 single, 2 double).
 */
data class Gestures(
    val shake: Boolean = false,
    val twirl: Boolean = false,
    val rock: Boolean = false,
    val knocks: Int = 0,
    /** The owner picked the phone up: [Reactions.posture] says when, the frame after decides whether to greet. */
    val pickedUp: Boolean = false,
)

/** The world as the toy knows it this frame: whether there is a pet to react, how it feels and how the phone is held. */
data class Attention(
    val hasPet: Boolean,
    val eggOnMatrix: Boolean = false,
    val ready: Boolean = false,
    val mood: Mood = Mood.CONTENT,
    val posture: Posture = Posture.HAND,
    /** The ball is already out: another rock only kicks it, and brings neither stats nor experience. */
    val playingBall: Boolean = false,
)

/**
 * One thing the pet does about a gesture. The toy turns each into its store, engine and haptic calls, in the order
 * they come: that order is what [Reactions.frame] decides.
 */
sealed interface Reaction {
    /** A shake while the pet is ready to grow: one shake toward its evolution. */
    data object Grow : Reaction

    /** The phone was twirled round and round: dizzy stars. */
    data object Dizzy : Reaction

    /** Rocked from side to side: the ball comes out. [freshGame] a new game, not a kick to the ball already rolling. */
    data class Play(val freshGame: Boolean, val xp: Boolean) : Reaction

    /** One knock: the pet looks up at the viewer and hops. */
    data object Notice : Reaction

    /** A double knock: a giggling wiggle, with [joy] unless it was cheered up a moment ago. */
    data class Tickle(val joy: Boolean) : Reaction

    /** The owner has the phone in hand again: a happy hop and a soft tick. */
    data object Greet : Reaction
}

/**
 * The pet's answer to the gestures of one frame: which of them it takes, in which order, and how often each may bring
 * it joy or experience. Pure, so the toy's whole way of reading the owner can be tried without a phone ([PetToyService]
 * keeps the sensors, the store and the matrix).
 *
 * The order is the order the pet notices things in: a shake, then a twirl, a rock, a greeting, and the knock last.
 * Several of them in one frame all count: the pet plays them one after another (see [n7.glyphpet.pet.PetEngine.react]).
 */
class Reactions {

    companion object {
        /** A double knock cheers the pet up by [TICKLE_JOY] at most this often. */
        const val TICKLE_JOY_MS = 3000L
        const val TICKLE_JOY = 1f

        /** A new game of ball brings experience at most this often (see [n7.glyphpet.pet.XpRules.BALL_COOLDOWN_MS]). */
        const val BALL_XP_MS = n7.glyphpet.pet.XpRules.BALL_COOLDOWN_MS

        /** The pet greets the owner who picks the phone up at most this often. */
        const val GREET_EVERY_MS = 120_000L

        /** A pose must have lasted this long before leaving it for the hand counts as being picked up. */
        const val GREET_AFTER_MS = 3000L

        /** The screen went off with the phone still in hand: the posture is read again this much later. */
        const val GREET_CHECK_MS = 800L
    }

    private val postures = PostureTracker()

    /** When each of these last happened (uptime, ms). Long before any frame, so the first one of each always counts. */
    private var ballXpAt = Long.MIN_VALUE / 2
    private var tickleJoyAt = Long.MIN_VALUE / 2
    private var greetedAt = Long.MIN_VALUE / 2

    private var posture = Posture.HAND
    private var postureSince = 0L
    private var greetCheckAt = 0L

    /** Whether the screen was on last frame; null before the first one, which is no change either way. */
    private var screenWasOn: Boolean? = null

    /** How the phone is held, as of the last [posture] call. */
    val heldAs: Posture get() = posture

    /**
     * Reads how the phone is held this frame, and tells whether the owner has just picked it up: out of the pocket or
     * off the desk, or the screen turned off with the phone still in hand. A pose counts as left only once it lasted
     * [GREET_AFTER_MS], so a hand reaching past the phone is no greeting.
     */
    fun posture(pose: Pose?, jitter: Float, screenOn: Boolean, unlocked: Boolean, appInFront: Boolean, now: Long): Boolean {
        val wasOn = screenWasOn
        screenWasOn = screenOn
        val before = posture
        posture = postures.update(pose, jitter, screenOn, unlocked, appInFront, now)
        var pickedUp = false
        if (wasOn == true && !screenOn) greetCheckAt = now + GREET_CHECK_MS
        if (posture != before) {
            if (posture == Posture.HAND && (before == Posture.DESK || before == Posture.AWAY) && now - postureSince >= GREET_AFTER_MS) {
                pickedUp = true
            }
            postureSince = now
        }
        if (greetCheckAt > 0 && now >= greetCheckAt) {
            greetCheckAt = 0
            if (posture == Posture.HAND) pickedUp = true
        }
        return pickedUp
    }

    /** Starts over: the sensors were just turned on again, so no pose is known and nothing is on cooldown but time. */
    fun reset() {
        postures.reset()
        posture = Posture.HAND
        greetCheckAt = 0
        screenWasOn = null
    }

    /**
     * What the pet does about [gestures] at [now], in the order it does them. A gesture nobody is there for (no pet,
     * an egg on the matrix in its place) brings nothing; while the owner is busy with the screen
     * ([Posture.USING_PHONE]) the phone moves with the hand, so none of it is play.
     */
    fun frame(now: Long, gestures: Gestures, at: Attention): List<Reaction> {
        val out = mutableListOf<Reaction>()
        // Busy with the screen in another app: the phone moves with the hand, and none of it is play.
        val using = at.posture == Posture.USING_PHONE
        if (gestures.shake && !using) {
            // Shaking is no game, and it only grows a pet that is ready: dizzy stars come from twirling the phone.
            val shake = ButtonRules.shake(hasPet = at.hasPet, eggOnMatrix = at.eggOnMatrix, petReady = at.ready)
            if (shake == ShakeAction.GROW) out += Reaction.Grow
        }
        if (gestures.twirl && !using && at.hasPet && !at.eggOnMatrix) out += Reaction.Dizzy
        if (gestures.rock && !using && ButtonRules.rockPlays(hasPet = at.hasPet, mood = at.mood, eggOnMatrix = at.eggOnMatrix)) {
            // A new game cheers it up and tires it a little; a kick to the ball already rolling changes nothing.
            val fresh = !at.playingBall
            val xp = fresh && now - ballXpAt >= BALL_XP_MS
            if (xp) ballXpAt = now
            out += Reaction.Play(freshGame = fresh, xp = xp)
        }
        if (gestures.pickedUp && greets(now, at)) {
            greetedAt = now
            out += Reaction.Greet
        }
        // A knock reaches the pet on the desk beside it or through the back of the phone in hand, nowhere else.
        val knocking = at.posture == Posture.DESK || at.posture == Posture.HAND
        if (gestures.knocks > 0 && knocking && at.hasPet && !at.eggOnMatrix) {
            if (gestures.knocks >= 2) {
                // A sleeper only opens one eye: it is tickled, but nothing changes for it.
                val joy = at.mood != Mood.SLEEPING && now - tickleJoyAt >= TICKLE_JOY_MS
                if (joy) tickleJoyAt = now
                out += Reaction.Tickle(joy = joy)
            } else {
                out += Reaction.Notice
            }
        }
        return out
    }

    /** Whether a pet that was just picked up greets its owner now; only ever in the hand, which is where it was. */
    private fun greets(now: Long, at: Attention): Boolean =
        at.posture == Posture.HAND && at.hasPet && !at.eggOnMatrix && at.mood != Mood.SLEEPING &&
            now - greetedAt >= GREET_EVERY_MS
}
