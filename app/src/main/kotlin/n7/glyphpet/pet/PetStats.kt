package n7.glyphpet.pet

import java.util.Calendar

/** Needs of the pet, each 0..100. Time moves them; actions push them back up. */
data class PetStats(
    val satiety: Float,
    val joy: Float,
    val energy: Float,
    val bornAtMs: Long,
    val updatedAtMs: Long,
    /** A daytime nap is on: the pet sleeps until its energy is back to [PetRules.NAP_WAKE_ENERGY]. */
    val napping: Boolean = false,
) {
    fun ageDays(nowMs: Long): Int = ((nowMs - bornAtMs) / DAY_MS).toInt().coerceAtLeast(0)

    companion object {
        const val DAY_MS: Long = 24 * 60 * 60 * 1000L
        fun newborn(nowMs: Long): PetStats = PetStats(80f, 80f, 80f, nowMs, nowMs)
    }
}

/** How the pet feels, most urgent first: see [PetRules.mood]. Never saved, always worked out from the needs. */
enum class Mood { SLEEPING, SICK, HUNGRY, TIRED, SAD, HAPPY, CONTENT }

/** The rules of pet life. Pure functions: the time source is passed in, so they are easy to test. */
object PetRules {
    const val SATIETY_PER_HOUR = 10f
    const val JOY_PER_HOUR = 8f
    const val ENERGY_AWAKE_PER_HOUR = 6f
    const val ENERGY_ASLEEP_PER_HOUR = 16f

    /**
     * A pet that runs out of energy in the day (below [NAP_ENERGY]) takes a short nap: its energy comes back at
     * [ENERGY_NAP_PER_HOUR] and it wakes once it reaches [NAP_WAKE_ENERGY] (from empty, about 12 minutes), so it does
     * not doze off again right after waking.
     */
    const val NAP_ENERGY = 10f
    const val NAP_WAKE_ENERGY = 30f
    const val ENERGY_NAP_PER_HOUR = 150f

    /** The charger fills the pet up much faster than any sleep: from empty to full in about 3 minutes. */
    const val ENERGY_CHARGE_PER_HOUR = 2000f

    const val FEED_SATIETY = 35f
    const val FEED_JOY = 5f
    const val FULL = 90f
    const val PLAY_JOY = 15f
    const val PLAY_ENERGY = 3f

    /** Below both of these the pet is sick: nobody fed or played with it for about half a day. Food cures it. */
    const val SICK_SATIETY = 5f
    const val SICK_JOY = 5f

    private const val STEP_MS = 10 * 60 * 1000L
    private const val MAX_CATCH_UP_MS = 14 * PetStats.DAY_MS
    private const val HOUR_MS = 60 * 60 * 1000f

    val localHour: (Long) -> Int = { ms -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY) }

    fun isNight(hour: Int): Boolean = hour >= 23 || hour < 7

    /** A pet on the charger stays awake during the day: it works out, see [ChargeRenderer]. */
    fun isAsleep(stats: PetStats, hour: Int): Boolean =
        isNight(hour) || stats.energy < NAP_ENERGY || (stats.napping && stats.energy < NAP_WAKE_ENERGY)

    /**
     * Moves [stats] forward to [nowMs] in 10-minute steps. [charging] counts only for the latest step:
     * the charger fills the pet's energy at [ENERGY_CHARGE_PER_HOUR], awake or asleep, faster than any sleep.
     */
    fun advance(stats: PetStats, nowMs: Long, charging: Boolean = false, hourAt: (Long) -> Int = localHour): PetStats {
        if (nowMs <= stats.updatedAtMs) return stats
        var s = stats.copy(updatedAtMs = maxOf(stats.updatedAtMs, nowMs - MAX_CATCH_UP_MS))
        while (s.updatedAtMs < nowMs) {
            val stepEnd = minOf(s.updatedAtMs + STEP_MS, nowMs)
            val hours = (stepEnd - s.updatedAtMs) / HOUR_MS
            val hour = hourAt(s.updatedAtMs)
            val asleep = isAsleep(s, hour)
            val nap = asleep && !isNight(hour)
            val charged = charging && stepEnd == nowMs
            val hungry = s.satiety < 25f
            val gain = when {
                charged -> ENERGY_CHARGE_PER_HOUR
                nap -> ENERGY_NAP_PER_HOUR
                asleep -> ENERGY_ASLEEP_PER_HOUR
                else -> -ENERGY_AWAKE_PER_HOUR
            }
            // A nap fills only up to its waking point; the rest of the step is spent awake. The charger has no such cap.
            val energy = (s.energy + gain * hours).let { if (nap && !charged) minOf(it, maxOf(s.energy, NAP_WAKE_ENERGY)) else it }.clamp()
            s = s.copy(
                satiety = (s.satiety - SATIETY_PER_HOUR * hours * (if (asleep) 0.5f else 1f)).clamp(),
                joy = (s.joy - JOY_PER_HOUR * hours * (if (hungry) 1.5f else 1f) * (if (asleep) 0.3f else 1f)).clamp(),
                energy = energy,
                updatedAtMs = stepEnd,
                napping = !isNight(hourAt(stepEnd)) && (energy < NAP_ENERGY || (nap && energy < NAP_WAKE_ENERGY)),
            )
        }
        return s
    }

    /** Returns the new stats and whether the pet ate (a full pet refuses). */
    fun feed(stats: PetStats): Pair<PetStats, Boolean> {
        if (stats.satiety >= FULL) return stats to false
        return stats.copy(satiety = (stats.satiety + FEED_SATIETY).clamp(), joy = (stats.joy + FEED_JOY).clamp()) to true
    }

    fun play(stats: PetStats): PetStats =
        stats.copy(joy = (stats.joy + PLAY_JOY).clamp(), energy = (stats.energy - PLAY_ENERGY).clamp())

    /** A drop hurts: some joy is gone, and the pet wears a plaster until [hurtUntil]. */
    const val DROP_JOY = 10f
    const val WAKE_HOUR = 7

    fun drop(stats: PetStats): PetStats = stats.copy(joy = (stats.joy - DROP_JOY).clamp())

    /**
     * The plaster stays until tomorrow: until the morning ([WAKE_HOUR]) after the next night. A drop in the small hours
     * heals that same morning, as long as at least [MIN_HURT_MS] is left; otherwise it heals the morning after.
     */
    fun hurtUntil(nowMs: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, WAKE_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (c.timeInMillis - nowMs < MIN_HURT_MS) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    const val MIN_HURT_MS = 4 * 60 * 60 * 1000L

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
