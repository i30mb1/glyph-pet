package n7.glyphpet.pet

import kotlin.random.Random

/** Everything saved about eggs and the collection. Pure data: [NurseryRules] moves it through time. */
data class NurseryState(
    val egg: Egg? = null,
    val active: String? = null,
    val seen: Map<String, Int> = emptyMap(),
    val supporter: Boolean = false,
    /** Game day ([EggRules.dayKey]) the eggs in [eggDayCount] were laid on. */
    val eggDay: Int = 0,
    val eggDayCount: Int = 0,
    val everLaid: Boolean = false,
    val lastWarmMs: Long = 0,
    val instantDay: Int = 0,
    val hatchedAtMs: Long = 0,
    val hatchedId: String? = null,
)

/**
 * The egg-and-collection game, as pure functions of state and time:
 * - one egg at a time; a new one is laid once a game day starts (6:00 local), up to 1 a day (2 for supporters);
 * - an egg not hatched yesterday is not lost: today's comes as soon as it hatches;
 * - days away do not stack eggs up;
 * - the very first egg hatches in minutes; the hatched pet joins the collection and goes on the matrix.
 */
object NurseryRules {

    const val JUST_HATCHED_MS = 8000L

    fun advance(state: NurseryState, nowMs: Long, random: Random): NurseryState {
        var s = state
        val egg = s.egg
        if (egg != null && EggRules.isReady(egg, nowMs)) {
            s = s.copy(
                egg = null,
                active = egg.speciesId,
                seen = s.seen + (egg.speciesId to (s.seen[egg.speciesId] ?: 0) + 1),
                hatchedAtMs = nowMs,
                hatchedId = egg.speciesId,
            )
        }
        if (s.egg == null && eggsLeftToday(s, nowMs) > 0) {
            val today = EggRules.dayKey(nowMs)
            val laid = if (s.eggDay == today) s.eggDayCount else 0
            val species = SpeciesCatalog.roll(random)
            s = s.copy(
                egg = Egg(species.id, nowMs, 0, if (s.everLaid) EggRules.HATCH_MS else EggRules.FIRST_HATCH_MS, s.supporter),
                eggDay = today,
                eggDayCount = laid + 1,
                everLaid = true,
            )
        }
        return s
    }

    fun eggsLeftToday(s: NurseryState, nowMs: Long): Int {
        val laid = if (s.eggDay == EggRules.dayKey(nowMs)) s.eggDayCount else 0
        return (EggRules.eggsPerDay(s.supporter) - laid).coerceAtLeast(0)
    }

    /** A warm-up, or null when there is no egg or the last one was too recent. */
    fun warm(s: NurseryState, nowMs: Long): NurseryState? {
        val egg = s.egg ?: return null
        if (nowMs - s.lastWarmMs < EggRules.WARM_COOLDOWN_MS) return null
        return s.copy(egg = egg.copy(warmMs = egg.warmMs + EggRules.WARM_MS), lastWarmMs = nowMs)
    }

    fun instantHatchAvailable(s: NurseryState, nowMs: Long): Boolean =
        s.supporter && s.egg != null && s.instantDay != EggRules.dayKey(nowMs)

    /** Supporters hatch one egg a day right away; null when not allowed. */
    fun hatchNow(s: NurseryState, nowMs: Long): NurseryState? {
        val egg = s.egg ?: return null
        if (!instantHatchAvailable(s, nowMs)) return null
        return s.copy(egg = egg.copy(warmMs = egg.hatchMs), instantDay = EggRules.dayKey(nowMs))
    }

    /** Only collected pets can live on the matrix. */
    fun setActive(s: NurseryState, speciesId: String): NurseryState =
        if ((s.seen[speciesId] ?: 0) > 0) s.copy(active = speciesId) else s

    fun justHatched(s: NurseryState, nowMs: Long): Species? =
        if (nowMs - s.hatchedAtMs in 0..JUST_HATCHED_MS) SpeciesCatalog.get(s.hatchedId) else null
}

enum class ButtonAction { WARM_EGG, FEED, PET, NOTHING }

/** What the single Glyph button does right now: the most useful thing. */
object ButtonRules {
    fun decide(hasPet: Boolean, mood: Mood, eggWaiting: Boolean, satiety: Float): ButtonAction = when {
        !hasPet -> if (eggWaiting) ButtonAction.WARM_EGG else ButtonAction.NOTHING
        mood == Mood.SLEEPING -> ButtonAction.PET
        mood == Mood.HUNGRY -> ButtonAction.FEED
        eggWaiting -> ButtonAction.WARM_EGG
        satiety < 70f -> ButtonAction.FEED
        else -> ButtonAction.PET
    }
}
