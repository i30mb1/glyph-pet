package n7.glyphpet.pet


/** Needs of the pet, each 0..100. Time moves them; actions push them back up. */
data class PetStats(
    val satiety: Float,
    val joy: Float,
    val energy: Float,
    val bornAtMs: Long,
    val updatedAtMs: Long,
    /** The pet is asleep: it sleeps on until its energy is back to [PetRules.NAP_WAKE_ENERGY]. */
    val napping: Boolean = false,
    /** When the toy last said the pet was on the matrix (lit and seen); 0 when it said it was not. See [PetRules.advance]. */
    val seenAtMs: Long = 0,
) {
    fun ageDays(nowMs: Long): Int = ((nowMs - bornAtMs) / DAY_MS).toInt().coerceAtLeast(0)

    companion object {
        const val DAY_MS: Long = 24 * 60 * 60 * 1000L

        /**
         * A newborn shows no food and no joy dots at all: it asks to be fed and played with in its very first minute,
         * the way the toys of 1996 hatched with their meters empty. Just above [PetRules.SICK_SATIETY] and
         * [PetRules.SICK_JOY], so an empty-looking baby is hungry and sad but never ill at birth; its energy is full,
         * so it does not doze off before anyone has met it.
         */
        const val NEWBORN_NEED = 8f

        fun newborn(nowMs: Long): PetStats = PetStats(NEWBORN_NEED, NEWBORN_NEED, 80f, nowMs, nowMs)
    }
}

/** How a need (0..100) shows as LED dots, in the app's needs meters and in the growth rules alike: 0..[MAX]. */
object NeedDots {
    const val MAX = 5

    fun of(value: Float): Int = ((value + 10f) / 20f).toInt().coerceIn(0, MAX)
}

/** How the pet feels, most urgent first: see [PetRules.mood]. Never saved, always worked out from the needs. */
enum class Mood { SLEEPING, HUNGRY, TIRED, SAD, HAPPY, CONTENT }

/**
 * When the pet calls for its owner by itself: a need has run down to [CALL_DOTS] dots or fewer and nobody has come.
 * The toy lights the matrix up and gives one soft [HapticKind.CALL] — the beep of 1996, without the noise.
 *
 * Deliberately rare: with needs falling at [PetRules.AWAY_SATIETY_PER_HOUR] and [PetRules.AWAY_JOY_PER_HOUR], a full pet reaches
 * the threshold in two or three hours away: about when the next short visit is due anyway, so a call is an invitation. A sleeping pet never calls, and
 * [REPEAT_MS] keeps an unanswered one from asking again too soon.
 */
object CallRules {
    /**
     * A need showing no dots at all is worth calling about. Not one dot: a single meal lifts food clear of nothing but
     * not clear of one dot, and a pet that kept calling after it had just been fed would be nagging, not asking.
     */
    const val CALL_DOTS = 0

    /** The pet calls again no sooner than this, however hungry it stays. */
    const val REPEAT_MS = 20 * 60_000L

    /** Needs are checked this often; between checks nothing is asked. */
    const val CHECK_MS = 5 * 60_000L

    /** How many times in a row an unanswered pet keeps calling before it gives up until something changes. */
    const val MAX_UNANSWERED = 3

    /** Whether [stats] are worth a call now: some need is down to [CALL_DOTS], and the pet is awake to ask. */
    fun wants(stats: PetStats, asleep: Boolean): Boolean {
        if (asleep) return false
        return NeedDots.of(stats.satiety) <= CALL_DOTS || NeedDots.of(stats.joy) <= CALL_DOTS
    }
}

/**
 * The rules of pet life. Pure functions: the time source is passed in, so they are easy to test.
 *
 * Built for short visits: a couple of minutes on the matrix at a time, a few times a day, an hour or two in all before
 * the owner moves on to the next pet. So there are two clocks:
 * - away (the matrix dark, the phone face up or in a pocket): food and joy drift down slowly in real time
 *   ([AWAY_SATIETY_PER_HOUR], [AWAY_JOY_PER_HOUR]) and the pet rests, its energy back within a minute or two. Whoever
 *   comes back after an hour or two finds it rested but hungry or bored: there is something to do at once.
 * - on the matrix: everything moves in minutes ([SEEN_PER_HOUR]), so even a two-minute visit sees it change.
 * One action is one dot: a meal gives a dot of food ([FEED_SATIETY]); a game costs a dot of energy and half a dot of
 * food when it starts ([PLAY_ENERGY], [PLAY_SATIETY]) and gives a dot of joy for every [PLAY_JOY_EVERY_MS] it is played. Worn out, it sleeps on the matrix for well under a minute
 * ([SLEEP_ENERGY_PER_HOUR]); the charger fills it faster still, awake. Nothing is ever lost for good: no illness, no
 * death, a neglected pet only gets sad and waits.
 */
object PetRules {
    /** One dot of the [NeedDots.MAX] a meter shows: what one meal or one game is worth. */
    const val DOT = 100f / NeedDots.MAX

    /** Away: a dot of food every 30 minutes, a dot of joy every 40, and a dot of energy back every 20 seconds. */
    const val AWAY_SATIETY_PER_HOUR = DOT * 2
    const val AWAY_JOY_PER_HOUR = DOT * 1.5f
    const val AWAY_ENERGY_PER_HOUR = DOT * 180

    /** On the matrix, awake: food, joy and energy each lose a dot every four minutes. */
    const val SEEN_PER_HOUR = DOT * 15

    /** Asleep on the matrix it fills a dot of energy every ten seconds; the charger, a dot every five. */
    const val SLEEP_ENERGY_PER_HOUR = DOT * 360
    const val CHARGE_ENERGY_PER_HOUR = DOT * 720

    /** A pet that runs out of energy (below [NAP_ENERGY]) sleeps until it is nearly full again ([NAP_WAKE_ENERGY]). */
    const val NAP_ENERGY = 10f
    const val NAP_WAKE_ENERGY = 90f

    const val FEED_SATIETY = DOT
    const val FULL = 90f
    /** A dot of joy for every this long of a game, given as it is played ([playing]). */
    const val PLAY_JOY_EVERY_MS = 10_000L
    const val PLAY_ENERGY = DOT
    const val PLAY_SATIETY = DOT / 2
    /** Stroked (the hold menu's heart): half a dot of joy. */
    const val PET_JOY = DOT / 2
    /** Too tired to play below this: the mood is [Mood.TIRED]. */
    const val TIRED = 20f
    const val HUNGRY = 25f

    /** Time counts as on the matrix only this long after the toy last said so; a longer silence was time away. */
    const val SEEN_GAP_MS = 3 * 60_000L

    private const val AWAY_STEP_MS = 10 * 60 * 1000L
    private const val SEEN_STEP_MS = 5_000L
    private const val MAX_CATCH_UP_MS = 14 * PetStats.DAY_MS
    private const val HOUR_MS = 60 * 60 * 1000f

    /**
     * The pet sleeps when it is worn out, and at no hour of the clock: a night owl who wants to play at three in the
     * morning finds it awake. A pet on the charger stays awake whatever its energy: it works out, see [ChargeRenderer].
     */
    fun isAsleep(stats: PetStats): Boolean =
        stats.energy < NAP_ENERGY || (stats.napping && stats.energy < NAP_WAKE_ENERGY)

    /**
     * Moves [stats] forward to [nowMs]: up to [SEEN_GAP_MS] after [PetStats.seenAtMs] it was on the matrix, the rest
     * of the time away. [charging] counts for the time on the matrix: the charger fills it at [CHARGE_ENERGY_PER_HOUR].
     */
    fun advance(stats: PetStats, nowMs: Long, charging: Boolean = false): PetStats {
        if (nowMs <= stats.updatedAtMs) return stats
        var s = stats.copy(updatedAtMs = maxOf(stats.updatedAtMs, nowMs - MAX_CATCH_UP_MS))
        val seenUntil = if (stats.seenAtMs > 0) stats.seenAtMs + SEEN_GAP_MS else 0L
        while (s.updatedAtMs < nowMs) {
            val seen = s.updatedAtMs < seenUntil
            val stepEnd = minOf(s.updatedAtMs + if (seen) SEEN_STEP_MS else AWAY_STEP_MS, nowMs, if (seen) seenUntil else nowMs)
            s = step(s, stepEnd, seen, charging && seen)
        }
        return s
    }

    private fun step(s: PetStats, stepEnd: Long, seen: Boolean, charged: Boolean): PetStats {
        val hours = (stepEnd - s.updatedAtMs) / HOUR_MS
        val asleep = isAsleep(s)
        val hungry = s.satiety < HUNGRY
        val food: Float
        val joy: Float
        val gain: Float
        if (seen) {
            food = SEEN_PER_HOUR * (if (asleep) 0.5f else 1f)
            joy = if (asleep) 0f else SEEN_PER_HOUR
            gain = when {
                charged -> CHARGE_ENERGY_PER_HOUR
                asleep -> SLEEP_ENERGY_PER_HOUR
                else -> -SEEN_PER_HOUR
            }
        } else {
            food = AWAY_SATIETY_PER_HOUR
            joy = AWAY_JOY_PER_HOUR
            gain = AWAY_ENERGY_PER_HOUR
        }
        // A sleep on the matrix fills only up to its waking point; the charger and rest away have no such cap.
        val raw = s.energy + gain * hours
        val energy = (if (seen && asleep && !charged) minOf(raw, maxOf(s.energy, NAP_WAKE_ENERGY)) else raw).clamp()
        return s.copy(
            satiety = (s.satiety - food * hours).clamp(),
            joy = (s.joy - joy * hours * (if (hungry) 2f else 1f)).clamp(),
            energy = energy,
            updatedAtMs = stepEnd,
            napping = energy < NAP_ENERGY || (asleep && energy < NAP_WAKE_ENERGY),
        )
    }

    /** Returns the new stats and whether the pet ate a dot of food (a full pet refuses). */
    fun feed(stats: PetStats): Pair<PetStats, Boolean> {
        if (stats.satiety >= FULL) return stats to false
        return stats.copy(satiety = (stats.satiety + FEED_SATIETY).clamp()) to true
    }

    /** Whether it is awake and has the energy for a game. */
    fun canPlay(stats: PetStats): Boolean = !isAsleep(stats) && stats.energy >= TIRED

    /** A game starts: it costs a dot of energy and half a dot of food. The joy comes as it is played ([playing]). */
    fun play(stats: PetStats): PetStats = stats.copy(
        energy = (stats.energy - PLAY_ENERGY).clamp(),
        satiety = (stats.satiety - PLAY_SATIETY).clamp(),
    )

    /** [ms] more of a game: a dot of joy every [PLAY_JOY_EVERY_MS]. */
    fun playing(stats: PetStats, ms: Long): PetStats =
        stats.copy(joy = (stats.joy + DOT * ms.coerceAtLeast(0) / PLAY_JOY_EVERY_MS).clamp())

    /** Stroked: half a dot of joy. */
    fun stroke(stats: PetStats): PetStats = stats.copy(joy = (stats.joy + PET_JOY).clamp())

    fun mood(stats: PetStats, asleep: Boolean): Mood = when {
        asleep -> Mood.SLEEPING
        stats.satiety < HUNGRY -> Mood.HUNGRY
        stats.energy < TIRED -> Mood.TIRED
        stats.joy < 25f -> Mood.SAD
        stats.joy > 70f && stats.satiety > 50f -> Mood.HAPPY
        else -> Mood.CONTENT
    }

    private fun Float.clamp(): Float = coerceIn(0f, 100f)
}
