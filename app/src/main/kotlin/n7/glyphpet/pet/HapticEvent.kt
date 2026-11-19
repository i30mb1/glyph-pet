package n7.glyphpet.pet

/**
 * Something the owner should feel in the hand. The game only names what happened; the toy (toy/Haptics) turns it into
 * a short, soft vibration, so the engine and the signatures stay free of Android APIs.
 */
enum class HapticKind {
    /** The pet took the food. */
    FEED,

    /** A full pet turned the food down: "no-no". */
    REFUSE,

    /** A press cracked the egg a little more; [HapticEvent.strength] grows with each crack. */
    CRACK,

    /** The egg rocks from side to side before it hatches; stronger toward the end. */
    HATCH_SWING,

    /** The crack runs across the egg: a rising rumble up to the flash. */
    HATCH_RISE,

    /** The hatch flash: the shell bursts. */
    HATCH_FLASH,

    /** A shake toward growing, not the last one. */
    GROW_SHAKE,

    /** Every eighth of the evolution's 3D turn, closer and closer together; stronger toward the end. */
    EVOLVE_TURN,

    /** The evolution flash: the new form is there. */
    EVOLVE_FLASH,

    /** The ball hit the round edge hard enough to feel; [HapticEvent.strength] is how hard. */
    BALL_HIT,

    /** The dino landed from its stomp jump. */
    STOMP,

    /** Lightning struck in the cloud's storm. */
    LIGHTNING,

    /** Rocking the phone started a game. */
    PLAY,
}

/**
 * Something the pet finished on the matrix that the toy turns into experience ([XpRules]), collected like the haptics:
 * a [SIGNATURE] played to its end, or a [TRICK] that played for [ms].
 */
data class PetEvent(val kind: Kind, val ms: Long = 0) {
    enum class Kind { SIGNATURE, TRICK }
}

/** One thing to feel: [kind], and how strong within it (0..1). */
data class HapticEvent(val kind: HapticKind, val strength: Float = 1f)
