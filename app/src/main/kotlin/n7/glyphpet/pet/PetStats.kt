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
enum class Mood { SLEEPING, SICK, HUNGRY, TIRED, SAD, HAPPY, CONTENT }

/**
 * When the pet calls for its owner by itself: a need has run down to [CALL_DOTS] dots or fewer and nobody has come.
 * The toy lights the matrix up and gives one soft [HapticKind.CALL] — the beep of 1996, without the noise.
 *
 * Deliberately rare: with needs falling at [PetRules.SATIETY_PER_HOUR] and [PetRules.JOY_PER_HOUR], a full pet reaches
 * the threshold in seven to nine hours, so a call comes two or three times a day. A sleeping pet never calls, and
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

/** The rules of pet life. Pure functions: the time source is passed in, so they are easy to test. */
object PetRules {
    const val SATIETY_PER_HOUR = 10f
    const val JOY_PER_HOUR = 8f
    const val ENERGY_AWAKE_PER_HOUR = 120f

    /**
     * A pet that runs out of energy (below [NAP_ENERGY]) sleeps until it is nearly full again ([NAP_WAKE_ENERGY]), so
     * it does not doze off again right after waking. Awake it spends a dot of its five every ten minutes
     * ([ENERGY_AWAKE_PER_HOUR]) and asleep it fills one a minute, so it is awake about forty minutes and asleep four.
     */
    const val NAP_ENERGY = 10f
    const val NAP_WAKE_ENERGY = 90f
    const val ENERGY_SLEEP_PER_HOUR = 1200f

    /** The charger fills the pet faster than its own sleep: a dot of its five every ten seconds. */
    const val ENERGY_CHARGE_PER_HOUR = 7200f

    const val FEED_SATIETY = 35f
    const val FEED_JOY = 5f
    const val FULL = 90f

    /** Below both of these the pet is sick: nobody fed or played with it for about half a day. Food cures it. */
    const val SICK_SATIETY = 5f
    const val SICK_JOY = 5f

    private const val STEP_MS = 10 * 60 * 1000L
    private const val MAX_CATCH_UP_MS = 14 * PetStats.DAY_MS
    private const val HOUR_MS = 60 * 60 * 1000f

    /**
     * The pet sleeps when it is worn out, and at no hour of the clock: a night owl who wants to play at three in the
     * morning finds it awake, as long as it has slept its [NAP_ENERGY] to [NAP_WAKE_ENERGY] some time before.
     * A pet on the charger stays awake whatever its energy: it works out, see [ChargeRenderer].
     */
    fun isAsleep(stats: PetStats): Boolean =
        stats.energy < NAP_ENERGY || (stats.napping && stats.energy < NAP_WAKE_ENERGY)

    /**
     * Moves [stats] forward to [nowMs] in 10-minute steps. [charging] counts only for the latest step:
     * the charger fills the pet's energy at [ENERGY_CHARGE_PER_HOUR], awake or asleep, faster than any sleep.
     */
    fun advance(stats: PetStats, nowMs: Long, charging: Boolean = false): PetStats {
        if (nowMs <= stats.updatedAtMs) return stats
        var s = stats.copy(updatedAtMs = maxOf(stats.updatedAtMs, nowMs - MAX_CATCH_UP_MS))
        while (s.updatedAtMs < nowMs) {
            val stepEnd = minOf(s.updatedAtMs + STEP_MS, nowMs)
            val hours = (stepEnd - s.updatedAtMs) / HOUR_MS
            val asleep = isAsleep(s)
            val charged = charging && stepEnd == nowMs
            val hungry = s.satiety < 25f
            val gain = when {
                charged -> ENERGY_CHARGE_PER_HOUR
                asleep -> ENERGY_SLEEP_PER_HOUR
                else -> -ENERGY_AWAKE_PER_HOUR
            }
            // A sleep fills only up to its waking point; the rest of the step is spent awake. The charger has no such cap.
            val energy = (s.energy + gain * hours).let { if (asleep && !charged) minOf(it, maxOf(s.energy, NAP_WAKE_ENERGY)) else it }.clamp()
            s = s.copy(
                satiety = (s.satiety - SATIETY_PER_HOUR * hours * (if (asleep) 0.5f else 1f)).clamp(),
                joy = (s.joy - JOY_PER_HOUR * hours * (if (hungry) 1.5f else 1f) * (if (asleep) 0.3f else 1f)).clamp(),
                energy = energy,
                updatedAtMs = stepEnd,
                napping = energy < NAP_ENERGY || (asleep && energy < NAP_WAKE_ENERGY),
            )
        }
        return s
    }

    /** Returns the new stats and whether the pet ate (a full pet refuses). */
    fun feed(stats: PetStats): Pair<PetStats, Boolean> {
        if (stats.satiety >= FULL) return stats to false
        return stats.copy(satiety = (stats.satiety + FEED_SATIETY).clamp(), joy = (stats.joy + FEED_JOY).clamp()) to true
    }

    fun mood(stats: PetStats, asleep: Boolean): Mood = when {
        asleep -> Mood.SLEEPING
        stats.satiety < SICK_SATIETY && stats.joy < SICK_JOY -> Mood.SICK
        stats.satiety < 25f -> Mood.HUNGRY
        stats.energy < 20f -> Mood.TIRED
        stats.joy < 25f -> Mood.SAD
        stats.joy > 70f && stats.satiety > 50f -> Mood.HAPPY
        else -> Mood.CONTENT
    }

    private fun Float.clamp(): Float = coerceIn(0f, 100f)
}
