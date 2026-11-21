package n7.glyphpet.pet

import java.util.Calendar
import kotlin.math.cos
import kotlin.math.PI

/**
 * How each anomaly ([Rarity.ANOMALY]) is found: never in the daily egg, it comes as an egg of its own the moment the
 * player does its thing. Pure: [PetStore.anomalies] feeds it the facts and grants the eggs.
 */
object AnomalyUnlocks {

    /** What the game knows right now. [deskMs]: how long the phone has lain screen down without a break. */
    data class Facts(
        val charging: Boolean,
        val battery: Float,
        val stats: PetStats?,
        val collected: Set<String>,
        val adults: Int,
        val nowMs: Long,
        val desk: Boolean,
        val deskMs: Long,
    )

    val SWARM_FLYERS = listOf("bee", "bat", "owl", "ufo")
    const val DRAGON_ADULTS = 5
    const val MIMIC_EGGS = 10
    const val LAVA_STILL_MS = 10 * 60_000L
    private const val FULL = 99f
    /** The full moon counts while the moon is lit this much (some five days round it). */
    private const val FULL_MOON_LIT = 0.9

    /** Today's moon, 0 new, 0.5 full, from a known new moon and the synodic month. */
    fun moonPhase(nowMs: Long): Double {
        val days = nowMs / 86_400_000.0 - 10962.76
        return ((days / 29.530588) % 1.0 + 1.0) % 1.0
    }
    fun moonLit(nowMs: Long): Double = (1 - cos(2 * PI * moonPhase(nowMs))) / 2
    fun fullMoon(nowMs: Long) = moonLit(nowMs) >= FULL_MOON_LIT

    /** Whole days until the full-moon window opens (0 while it is open). */
    fun daysToFullMoon(nowMs: Long): Int {
        var d = 0
        while (d < 31 && !fullMoon(nowMs + d * 86_400_000L)) d++
        return d
    }

    private fun nearMidnight(nowMs: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = nowMs }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return m >= 23 * 60 + 30 || m < 30
    }

    /** Whether [kind]'s condition is met right now. */
    fun met(kind: String, f: Facts): Boolean = when (kind) {
        "phoenix" -> f.charging && f.battery >= 0.995f
        "blackhole" -> f.stats != null && f.stats.satiety >= FULL && f.stats.joy >= FULL && f.stats.energy >= FULL
        "swarm" -> SWARM_FLYERS.all { it in f.collected }
        "dragon" -> f.adults >= DRAGON_ADULTS
        "mimic" -> f.collected.size >= MIMIC_EGGS
        "moonrabbit" -> fullMoon(f.nowMs)
        "schrodinger" -> f.desk && nearMidnight(f.nowMs)
        "lavalamp" -> f.desk && f.deskMs >= LAVA_STILL_MS
        else -> false
    }

    /** How far along the player is, as "have/need", where it can be counted; null where it cannot. */
    fun progress(kind: String, f: Facts): Pair<Int, Int>? = when (kind) {
        "swarm" -> SWARM_FLYERS.count { it in f.collected } to SWARM_FLYERS.size
        "dragon" -> minOf(f.adults, DRAGON_ADULTS) to DRAGON_ADULTS
        "mimic" -> minOf(f.collected.size, MIMIC_EGGS) to MIMIC_EGGS
        else -> null
    }

    /** The hint in English (the Russian one is the hero's own [n7.glyphpet.pet.drawn.Anomaly.unlock]). */
    val HINTS_EN = mapOf(
        "phoenix" to "Put the phone on charge and wait for 100%",
        "blackhole" to "Fill food, joy and energy to the top at once",
        "swarm" to "Collect every flyer: bee, bat, owl and UFO",
        "dragon" to "Raise five pets to adults",
        "mimic" to "Hatch 10 eggs: the next one comes as a chest",
        "moonrabbit" to "Visit your pet at full moon (a window of about 5 days)",
        "schrodinger" to "Lay the phone screen down between 23:30 and 00:30",
        "lavalamp" to "Lay the phone screen down and leave it for 10 minutes",
    )
}
