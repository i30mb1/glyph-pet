package n7.glyphpet.pet

import java.util.Calendar

/** Needs of the pet, each 0..100. Time moves them; actions push them back up. */
data class PetStats(
    val satiety: Float,
    val joy: Float,
    val energy: Float,
    val bornAtMs: Long,
    val updatedAtMs: Long,
) {
    fun ageDays(nowMs: Long): Int = ((nowMs - bornAtMs) / DAY_MS).toInt().coerceAtLeast(0)

    companion object {
        const val DAY_MS: Long = 24 * 60 * 60 * 1000L
        fun newborn(nowMs: Long): PetStats = PetStats(80f, 80f, 80f, nowMs, nowMs)
    }
}

enum class Mood { SLEEPING, HUNGRY, TIRED, SAD, HAPPY, CONTENT }

/** The rules of pet life. Pure functions: the time source is passed in, so they are easy to test. */
object PetRules {
    const val SATIETY_PER_HOUR = 10f
    const val JOY_PER_HOUR = 8f
    const val ENERGY_AWAKE_PER_HOUR = 6f
    const val ENERGY_ASLEEP_PER_HOUR = 16f

    const val FEED_SATIETY = 35f
    const val FEED_JOY = 5f
    const val FULL = 90f
    const val PLAY_JOY = 15f
    const val PLAY_ENERGY = 3f
    const val SHAKE_JOY = 4f

    private const val STEP_MS = 10 * 60 * 1000L
    private const val MAX_CATCH_UP_MS = 14 * PetStats.DAY_MS
    private const val HOUR_MS = 60 * 60 * 1000f

    val localHour: (Long) -> Int = { ms -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY) }

    fun isNight(hour: Int): Boolean = hour >= 23 || hour < 7

    fun isAsleep(stats: PetStats, hour: Int, charging: Boolean): Boolean =
        isNight(hour) || charging || stats.energy < 10f

    /** Moves [stats] forward to [nowMs] in 10-minute steps; [charging] counts only for the latest step. */
    fun advance(stats: PetStats, nowMs: Long, charging: Boolean = false, hourAt: (Long) -> Int = localHour): PetStats {
        if (nowMs <= stats.updatedAtMs) return stats
        var s = stats.copy(updatedAtMs = maxOf(stats.updatedAtMs, nowMs - MAX_CATCH_UP_MS))
        while (s.updatedAtMs < nowMs) {
            val stepEnd = minOf(s.updatedAtMs + STEP_MS, nowMs)
            val hours = (stepEnd - s.updatedAtMs) / HOUR_MS
            val asleep = isAsleep(s, hourAt(s.updatedAtMs), charging && stepEnd == nowMs)
            val hungry = s.satiety < 25f
            s = s.copy(
                satiety = (s.satiety - SATIETY_PER_HOUR * hours * (if (asleep) 0.5f else 1f)).clamp(),
                joy = (s.joy - JOY_PER_HOUR * hours * (if (hungry) 1.5f else 1f) * (if (asleep) 0.3f else 1f)).clamp(),
                energy = (s.energy + (if (asleep) ENERGY_ASLEEP_PER_HOUR else -ENERGY_AWAKE_PER_HOUR) * hours).clamp(),
                updatedAtMs = stepEnd,
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

    fun shake(stats: PetStats): PetStats = stats.copy(joy = (stats.joy + SHAKE_JOY).clamp())

    fun mood(stats: PetStats, asleep: Boolean): Mood = when {
        asleep -> Mood.SLEEPING
        stats.satiety < 25f -> Mood.HUNGRY
        stats.energy < 20f -> Mood.TIRED
        stats.joy < 25f -> Mood.SAD
        stats.joy > 70f && stats.satiety > 50f -> Mood.HAPPY
        else -> Mood.CONTENT
    }

    private fun Float.clamp(): Float = coerceIn(0f, 100f)
}
