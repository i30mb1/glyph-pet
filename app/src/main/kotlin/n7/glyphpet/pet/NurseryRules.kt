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
    /**
     * The egg the player put on the matrix in the app: the matrix shows it instead of the pet and the Glyph button
     * cracks it. Null (or an egg no longer there): none, the pet is on the matrix. See [NurseryRules.onMatrix].
     */
    val onMatrix: String? = null,
    /** The last game day ([EggRules.dayKey]) that gave a hero. */
    val grantDay: Int = 0,
    val hatchedAtMs: Long = 0,
    val hatchedId: String? = null,
    /** How far each collected hero has grown, by kind; see [GrowthRules]. */
    val growth: Map<String, Growth> = emptyMap(),
    /** When the minutes together ([GrowthRules.together]) were last counted; 0: not yet. */
    val xpAtMs: Long = 0,
    /** When a pet last grew, and the id of the form it grew into ([SpeciesForms.id]). */
    val evolvedAtMs: Long = 0,
    val evolvedId: String? = null,
)

/** What a press on an egg did. */
enum class PressResult { NONE, CRACKED, HATCHED }

/**
 * The egg-and-collection game, as pure functions of state and time:
 * - on every game day (from 6:00 local) on which the game runs, one hero the player does not have yet becomes
 *   available as an egg, rolled with the rarity weights among the missing ones; days the game does not run give none;
 * - eggs not opened stay; once every hero is collected there are no more;
 * - eggs wait in the app until the player puts one on the matrix ([putOnMatrix]); the very first one (no pet yet) is
 *   on the matrix by itself. While an egg is on it the pet is away;
 * - five long presses of the Glyph button open the egg on the matrix and the hero becomes the active pet, as a baby
 *   ([GrowthRules]);
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

    /**
     * The egg on the matrix: the one the player put there, while it is still there to open. Without a pet the oldest
     * egg is on the matrix by itself, so the very first one needs no step in the app. Null: the pet is on the matrix.
     */
    fun onMatrix(s: NurseryState): String? {
        val eggs = available(s)
        return s.onMatrix?.takeIf { it in eggs } ?: if (s.active == null) eggs.firstOrNull() else null
    }

    /** Puts egg [id] on the matrix, in place of the pet or another egg. Only an egg there is to open. */
    fun putOnMatrix(s: NurseryState, id: String): NurseryState =
        if (id in available(s) && s.onMatrix != id) s.copy(onMatrix = id) else s

    /** Takes the egg off the matrix and the pet comes back; its presses stay. The first egg (no pet yet) stays on. */
    fun takeOffMatrix(s: NurseryState): NurseryState = if (s.onMatrix != null) s.copy(onMatrix = null) else s

    fun presses(s: NurseryState, id: String): Int = s.presses[id] ?: 0

    /**
     * Gives today's hero, once per game day. A
     * clock turned back gives nothing again.
     */
    fun advance(before: NurseryState, nowMs: Long, random: Random): NurseryState {
        val state = before
        val today = EggRules.dayKey(nowMs)
        if (today <= state.grantDay) return state
        val pool = missing(state).filter { it.id !in state.eggs }
        if (pool.isEmpty()) return state.copy(grantDay = today)
        val hero = SpeciesCatalog.roll(random, pool)
        return state.copy(eggs = state.eggs + hero.id, grantDay = today)
    }

    /**
     * One long press of the Glyph button on the egg on the matrix ([onMatrix]). The fifth hatches it: the hero joins
     * the collection and takes the egg's place on the matrix as a baby. Returns the new state, or null when there is
     * no egg on the matrix.
     */
    fun press(s: NurseryState, nowMs: Long): NurseryState? {
        val egg = onMatrix(s) ?: return null
        val n = presses(s, egg) + 1
        if (n < EggRules.PRESSES_TO_HATCH) return s.copy(presses = s.presses + (egg to n))
        return s.copy(
            eggs = s.eggs - egg,
            presses = s.presses - egg,
            onMatrix = null,
            active = egg,
            seen = s.seen + (egg to (s.seen[egg] ?: 0) + 1),
            hatchedAtMs = nowMs,
            hatchedId = egg,
            // The first hero of all: nothing collected before it, so it hatches with a head start toward growing.
            growth = s.growth + (egg to GrowthRules.hatched(EggRules.dayKey(nowMs), first = s.seen.none { it.value > 0 })),
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
     * still missing, else one rolled among the missing, and none when every hero is collected. The day of 1.1's last egg
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

    /**
     * A save from before growth stages: every collected hero without a [Growth] becomes a teen (the form it always had),
     * counting from game day [today]. Saves that already have growth stay as they are.
     */
    fun withGrowth(s: NurseryState, today: Int): NurseryState {
        val missing = s.seen.filter { (id, n) -> n > 0 && id !in s.growth }.keys
        if (missing.isEmpty()) return s
        return s.copy(growth = s.growth + missing.associateWith { GrowthRules.grownUp(today) })
    }
}

enum class ButtonAction { CRACK_EGG, FEED, STIR, NOTHING }

enum class ShakeAction { GROW, DIZZY, NOTHING }

/**
 * The controls, one gesture for one thing: holding the Glyph button feeds the pet, or cracks the egg the player put on
 * the matrix; rocking the phone gently from side to side plays; shaking makes the pet dizzy, or grows a pet that is
 * ready to. While an egg is on the matrix the pet is away: only the button does something.
 */
object ButtonRules {
    /** Holding the Glyph button: cracks the egg on the matrix, else feeds (a full pet refuses); a sleeper only opens one eye. */
    fun decide(hasPet: Boolean, mood: Mood, eggOnMatrix: Boolean): ButtonAction = when {
        eggOnMatrix -> ButtonAction.CRACK_EGG
        !hasPet -> ButtonAction.NOTHING
        mood == Mood.SLEEPING -> ButtonAction.STIR
        else -> ButtonAction.FEED
    }

    /** A shake: grows the pet while it is ready to ([GrowthRules.ready]), otherwise the pet gets dizzy. */
    fun shake(hasPet: Boolean, eggOnMatrix: Boolean, petReady: Boolean): ShakeAction = when {
        !hasPet || eggOnMatrix -> ShakeAction.NOTHING
        petReady -> ShakeAction.GROW
        else -> ShakeAction.DIZZY
    }

    /** Rocking plays with a pet that is awake; a sleeper is only lulled, its needs stay as they are. */
    fun rockPlays(hasPet: Boolean, mood: Mood, eggOnMatrix: Boolean = false): Boolean =
        hasPet && !eggOnMatrix && mood != Mood.SLEEPING
}
