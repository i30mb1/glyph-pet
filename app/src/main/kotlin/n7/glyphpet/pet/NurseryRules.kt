package n7.glyphpet.pet

import kotlin.random.Random

/** Everything saved about eggs and the collection. Pure data: [NurseryRules] moves it through time. */
data class NurseryState(
    val active: String? = null,
    /** How many times each hero hatched. Since 1.2 each hatches once; 1.1 saves can hold higher counts, kept as they are. */
    val seen: Map<String, Int> = emptyMap(),
    val supporter: Boolean = false,
    /** The heroes given as the day's egg and not opened yet, oldest first. */
    val eggs: List<String> = emptyList(),
    /** Presses so far, per egg. */
    val presses: Map<String, Int> = emptyMap(),
    /** The egg the player picked in the app; null (or one no longer there) means the oldest. */
    val selected: String? = null,
    /** The last game day ([EggRules.dayKey]) that gave a hero. */
    val grantDay: Int = 0,
    val hatchedAtMs: Long = 0,
    val hatchedId: String? = null,
)

/** What a press on an egg did. */
enum class PressResult { NONE, CRACKED, HATCHED }

/**
 * The egg-and-collection game, as pure functions of state and time:
 * - on every game day (from 6:00 local) on which the game runs, one hero the player does not have yet becomes
 *   available as an egg, rolled with the rarity weights among the missing ones; days the game does not run give none;
 * - eggs not opened stay; once all 18 are collected there are no more;
 * - five presses open an egg and the hero becomes the active pet;
 * - a supporter has every missing hero available as an egg at once.
 */
object NurseryRules {

    const val JUST_HATCHED_MS = 8000L

    private fun has(s: NurseryState, id: String) = (s.seen[id] ?: 0) > 0

    /** The heroes not collected yet, in catalogue order. */
    fun missing(s: NurseryState): List<Species> = SpeciesCatalog.all.filter { !has(s, it.id) }

    /**
     * Eggs ready to open, oldest first: the daily ones, then, for a supporter, every other missing hero. The
     * supporter's extra eggs are not saved, so a refund takes them back and leaves the daily ones.
     */
    fun available(s: NurseryState): List<String> {
        val daily = s.eggs.filter { SpeciesCatalog.get(it) != null && !has(s, it) }.distinct()
        if (!s.supporter) return daily
        return daily + missing(s).map { it.id }.filter { it !in daily }
    }

    /** The egg in the player's hands: the one picked in the app, else the oldest. Null when there is nothing to hatch. */
    fun current(s: NurseryState): String? {
        val eggs = available(s)
        return s.selected?.takeIf { it in eggs } ?: eggs.firstOrNull()
    }

    fun presses(s: NurseryState, id: String): Int = s.presses[id] ?: 0

    /** Gives today's hero, once per game day. A clock turned back gives nothing again. */
    fun advance(state: NurseryState, nowMs: Long, random: Random): NurseryState {
        val today = EggRules.dayKey(nowMs)
        if (today <= state.grantDay) return state
        val pool = missing(state).filter { it.id !in state.eggs }
        if (pool.isEmpty()) return state.copy(grantDay = today)
        val hero = SpeciesCatalog.roll(random, pool)
        return state.copy(eggs = state.eggs + hero.id, grantDay = today)
    }

    /**
     * One press on egg [id], or, with null, on the egg in the hands (the Glyph button). A press on a named egg (a tap
     * in the app) also puts that egg in the hands. The fifth press hatches it: the hero joins the collection and goes
     * on the matrix. Returns the new state, or null when there is no such egg.
     */
    fun press(s: NurseryState, id: String?, nowMs: Long): NurseryState? {
        val egg = id ?: current(s) ?: return null
        if (egg !in available(s)) return null
        val n = presses(s, egg) + 1
        if (n < EggRules.PRESSES_TO_HATCH) {
            return s.copy(presses = s.presses + (egg to n), selected = if (id != null) egg else s.selected)
        }
        return s.copy(
            eggs = s.eggs - egg,
            presses = s.presses - egg,
            selected = null,
            active = egg,
            seen = s.seen + (egg to (s.seen[egg] ?: 0) + 1),
            hatchedAtMs = nowMs,
            hatchedId = egg,
        )
    }

    /** What [press] did, comparing the state before and after. */
    fun result(before: NurseryState, after: NurseryState?): PressResult = when {
        after == null -> PressResult.NONE
        after.seen != before.seen -> PressResult.HATCHED
        else -> PressResult.CRACKED
    }

    /** Only collected pets can live on the matrix. */
    fun setActive(s: NurseryState, speciesId: String): NurseryState =
        if (has(s, speciesId)) s.copy(active = speciesId) else s

    fun justHatched(s: NurseryState, nowMs: Long): Species? =
        if (nowMs - s.hatchedAtMs in 0..JUST_HATCHED_MS) SpeciesCatalog.get(s.hatchedId) else null
}

/** What version 1.1 saved about eggs and the collection. Its egg hatched by itself on a timer. */
data class Nursery11(
    val eggSpecies: String?,
    val active: String?,
    val seen: Map<String, Int>,
    val supporter: Boolean,
    /** The game day of 1.1's last egg. */
    val eggDay: Int,
    val hatchedAtMs: Long,
    val hatchedId: String?,
)

object SaveMigration {
    /**
     * A 1.1 save in the 1.2 game. Collected heroes (with their counts), the active pet and the supporter unlock stay.
     * An egg that was waiting becomes an available egg of a hero the player does not have: the same one if it is
     * still missing, else one rolled among the missing, and none when all 18 are collected. The day of 1.1's last egg
     * counts as already given, so the player gets no second hero that day. [old] null: a new install.
     */
    fun from11(old: Nursery11?, random: Random): NurseryState {
        if (old == null) return NurseryState()
        val seen = old.seen.filterValues { it > 0 }
        val base = NurseryState(
            active = old.active,
            seen = seen,
            supporter = old.supporter,
            grantDay = old.eggDay,
            hatchedAtMs = old.hatchedAtMs,
            hatchedId = old.hatchedId,
        )
        val waiting = old.eggSpecies ?: return base
        val egg = waiting.takeIf { SpeciesCatalog.get(it) != null && (seen[it] ?: 0) == 0 }
            ?: NurseryRules.missing(base).takeIf { it.isNotEmpty() }?.let { SpeciesCatalog.roll(random, it).id }
        return base.copy(eggs = listOfNotNull(egg))
    }
}

enum class ButtonAction { CRACK_EGG, FEED, PET, NOTHING }

/** What the single Glyph button does right now: the most useful thing. */
object ButtonRules {
    fun decide(hasPet: Boolean, mood: Mood, eggWaiting: Boolean, satiety: Float): ButtonAction = when {
        // An egg is opened by five presses, so it comes first: at night and for a hungry pet too.
        eggWaiting -> ButtonAction.CRACK_EGG
        !hasPet -> ButtonAction.NOTHING
        mood == Mood.SLEEPING -> ButtonAction.PET
        mood == Mood.SICK || mood == Mood.HUNGRY -> ButtonAction.FEED
        satiety < 70f -> ButtonAction.FEED
        else -> ButtonAction.PET
    }
}
