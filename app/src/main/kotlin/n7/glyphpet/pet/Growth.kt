package n7.glyphpet.pet

/**
 * How far one hero has grown; one per collected hero in [NurseryState.growth].
 * @param reached the furthest stage it has grown to.
 * @param shown the form that lives on the matrix: any stage up to [reached], the player's pick.
 * @param careDays care days counted toward the next stage (only a teen counts them, see [GrowthRules]).
 * @param ready it may grow into the next stage now: [GrowthRules.EVOLVE_SHAKES] shakes make it.
 * @param sinceDay the game day ([EggRules.dayKey]) it hatched or last grew.
 * @param evolvePresses shakes so far toward growing, while [ready].
 */
data class Growth(
    val reached: Stage,
    val shown: Stage,
    val careDays: Int,
    val ready: Boolean,
    val sinceDay: Int,
    val evolvePresses: Int = 0,
)

/**
 * The care the active pet got on the current game day [day] ([EggRules.dayKey]; 0 before the first one): whether it
 * was [fed] and whether it got [sick]. At the next game day it is counted (or not) and starts over.
 */
data class CareDay(val day: Int = 0, val fed: Boolean = false, val sick: Boolean = false)

/** What a shake toward growing did. */
enum class GrowResult { NONE, SHOOK, EVOLVED }

/**
 * Three growth stages, as pure functions of state and time:
 * - a hero hatches as a [Stage.BABY] and is ready to grow on the first game day (from 6:00) after the hatch day;
 * - a [Stage.TEEN] is ready after [TEEN_CARE_DAYS] care days: game days on which it was the active pet, was fed at
 *   least once and never got sick. A missed day just does not count, nothing is ever lost; only the active pet counts;
 * - an [Stage.ADULT] is final;
 * - a ready pet grows when the phone is shaken [EVOLVE_SHAKES] times; it then lives on the matrix in its new form;
 * - once grown, the player may put any form up to the one reached on the matrix ([setShown]); growth goes on whatever
 *   is shown.
 * The game day rolls over in [advance], which [NurseryRules.advance] runs first.
 */
object GrowthRules {

    const val TEEN_CARE_DAYS = 3
    const val EVOLVE_SHAKES = 3
    const val JUST_EVOLVED_MS = 8000L

    /** A hero that just hatched on game day [today]. */
    fun hatched(today: Int): Growth = Growth(Stage.BABY, Stage.BABY, careDays = 0, ready = false, sinceDay = today)

    /** A hero collected before growth existed: a teen, as it always looked, counting from [today]. */
    fun grownUp(today: Int): Growth = Growth(Stage.TEEN, Stage.TEEN, careDays = 0, ready = false, sinceDay = today)

    private fun has(s: NurseryState, kind: String) = (s.seen[kind] ?: 0) > 0

    /** The growth of a collected [kind]; a teen if the save has none for it. Null for a hero not collected. */
    fun of(s: NurseryState, kind: String): Growth? = s.growth[kind] ?: if (has(s, kind)) grownUp(0) else null

    /** The form of the active pet that lives on the matrix, or null without a pet. */
    fun activeForm(s: NurseryState): Species? {
        val kind = SpeciesCatalog.get(s.active)?.id ?: return null
        return SpeciesForms.of(kind, of(s, kind)?.shown ?: Stage.TEEN)
    }

    /** Whether the active pet may grow now. */
    fun ready(s: NurseryState): Boolean = s.active?.let { s.growth[it] }?.let { it.ready && it.reached.next != null } == true

    /**
     * Moves growth to [nowMs]. On a new game day the care of the last tracked day is counted for the active pet (a fed,
     * never sick teen gets a care day) and a fresh day starts; babies hatched on an earlier day become ready. A clock
     * turned back changes nothing.
     */
    fun advance(s: NurseryState, nowMs: Long): NurseryState {
        val today = EggRules.dayKey(nowMs)
        var growth = s.growth
        var care = s.care
        if (today > care.day) {
            val active = s.active
            val g = active?.let { growth[it] }
            if (care.day != 0 && g != null && care.fed && !care.sick && g.reached == Stage.TEEN && !g.ready) {
                val days = g.careDays + 1
                growth = growth + (active to g.copy(careDays = days, ready = days >= TEEN_CARE_DAYS))
            }
            care = CareDay(today)
        }
        if (growth.values.any { it.reached == Stage.BABY && !it.ready && today > it.sinceDay }) {
            growth = growth.mapValues { (_, g) -> if (g.reached == Stage.BABY && today > g.sinceDay) g.copy(ready = true) else g }
        }
        return if (growth == s.growth && care == s.care) s else s.copy(growth = growth, care = care)
    }

    /** The active pet accepted a meal today. */
    fun markFed(s: NurseryState, nowMs: Long): NurseryState = mark(s, nowMs) { it.copy(fed = true) }

    /** The active pet was seen sick today: the day does not count. */
    fun markSick(s: NurseryState, nowMs: Long): NurseryState = mark(s, nowMs) { it.copy(sick = true) }

    /** Marks today's care, after rolling the day over if a new one began. A clock turned back marks nothing. */
    private fun mark(s: NurseryState, nowMs: Long, change: (CareDay) -> CareDay): NurseryState {
        val now = advance(s, nowMs)
        if (now.active == null || now.care.day != EggRules.dayKey(nowMs)) return now
        val marked = change(now.care)
        return if (marked == now.care) now else now.copy(care = marked)
    }

    /** Another pet goes on the matrix: today's care starts over for it. */
    internal fun switched(s: NurseryState, active: String?): NurseryState =
        if (active == s.active) s else s.copy(care = CareDay(s.care.day))

    /**
     * [kind] grows into its next stage and is shown in it, if it is [Growth.ready]; otherwise nothing changes. Marks
     * the moment for [justEvolved].
     */
    fun evolve(s: NurseryState, kind: String, nowMs: Long): NurseryState {
        val g = s.growth[kind] ?: return s
        val next = g.reached.next ?: return s
        if (!g.ready) return s
        val grown = Growth(next, next, careDays = 0, ready = false, sinceDay = EggRules.dayKey(nowMs))
        return s.copy(growth = s.growth + (kind to grown), evolvedAtMs = nowMs, evolvedId = SpeciesForms.id(kind, next))
    }

    /** A shake while the active pet is ready: counted, and the [EVOLVE_SHAKES]th grows it. */
    fun shake(s: NurseryState, nowMs: Long): Pair<NurseryState, GrowResult> {
        val kind = s.active ?: return s to GrowResult.NONE
        if (!ready(s)) return s to GrowResult.NONE
        val g = s.growth.getValue(kind)
        val n = g.evolvePresses + 1
        if (n < EVOLVE_SHAKES) return s.copy(growth = s.growth + (kind to g.copy(evolvePresses = n))) to GrowResult.SHOOK
        return evolve(s, kind, nowMs) to GrowResult.EVOLVED
    }

    /** Puts the [stage] form of [kind] on the matrix; only a stage it has reached. */
    fun setShown(s: NurseryState, kind: String, stage: Stage): NurseryState {
        val g = of(s, kind) ?: return s
        if (stage > g.reached || stage == g.shown) return s
        return s.copy(growth = s.growth + (kind to g.copy(shown = stage)))
    }

    /** Debug builds: [kind] may grow right now. */
    fun makeReady(s: NurseryState, kind: String): NurseryState {
        val g = of(s, kind) ?: return s
        if (g.reached.next == null || g.ready) return s
        return s.copy(growth = s.growth + (kind to g.copy(ready = true)))
    }

    /** The form a pet just grew into, for a few seconds after it did. */
    fun justEvolved(s: NurseryState, nowMs: Long): Species? =
        if (nowMs - s.evolvedAtMs in 0..JUST_EVOLVED_MS) SpeciesForms.get(s.evolvedId) else null
}
