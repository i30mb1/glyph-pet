package n7.glyphpet.pet

/**
 * How far one hero has grown; one per collected hero in [NurseryState.growth].
 * @param reached the furthest stage it has grown to.
 * @param shown the form that lives on the matrix: any stage up to [reached], the player's pick.
 * @param xp experience gathered toward the next stage ([XpRules.threshold]); 0 for an adult.
 * @param ready it may grow into the next stage now: [GrowthRules.EVOLVE_SHAKES] shakes make it.
 * @param sinceDay the game day ([EggRules.dayKey]) it hatched or last grew.
 * @param evolvePresses shakes so far toward growing, while [ready].
 */
data class Growth(
    val reached: Stage,
    val shown: Stage,
    val xp: Int,
    val ready: Boolean,
    val sinceDay: Int,
    val evolvePresses: Int = 0,
) {
    /** The experience its next stage needs, or null for an adult. */
    val threshold: Int? get() = XpRules.threshold(reached)
}

/** Where experience came from: for the toy's debug log. */
enum class XpSource { TIME, DEBUG }

/**
 * Growing by time together, and by nothing else: a minute with the pet on the screen (the matrix, or the app's Play
 * tab), awake and not hungry, is a minute of experience. Feeding and playing keep it fed and awake, so they count only
 * through that. Short visits of a couple of minutes, a few a day: a baby is ready in [BABY_TO_TEEN] minutes (the
 * first day), a teen [TEEN_TO_ADULT] minutes later (two to four days), well inside the hour or two an owner spends
 * with one pet before moving on to the next.
 */
object XpRules {
    const val BABY_TO_TEEN = 15
    const val TEEN_TO_ADULT = 60

    /** A minute together: the pet on the screen, awake and not hungry. */
    const val PER_MINUTE = 1

    /** Minutes together with a grown-up pet that bring a new egg: an adult's time is not lost. */
    const val EGG_MINUTES = 30

    /**
     * The very first hero of all hatches this far along, so its owner sees an evolution within five minutes of
     * hatching it rather than on another day. Every hero after it starts from 0.
     */
    const val FIRST_HEAD_START = 10

    /** Minutes together count only while the toy reports at least this often; a longer gap was time away. */
    const val MAX_GAP_MS = 3 * 60_000L
    const val MINUTE_MS = 60_000L

    fun threshold(stage: Stage): Int? = when (stage) {
        Stage.BABY -> BABY_TO_TEEN
        Stage.TEEN -> TEEN_TO_ADULT
        Stage.ADULT -> null
    }

    /** Whether the pet gains at all now: awake and not hungry. */
    fun gains(stats: PetStats, asleep: Boolean): Boolean = when (PetRules.mood(stats, asleep)) {
        Mood.SLEEPING, Mood.HUNGRY -> false
        else -> true
    }

    /** A save from before experience: a teen's care days (of 3) become that share of its threshold, a baby starts at 0. */
    fun fromCareDays(stage: Stage, careDays: Int): Int =
        if (stage == Stage.TEEN) TEEN_TO_ADULT * careDays.coerceIn(0, 3) / 3 else 0
}

/** What a shake toward growing did. */
enum class GrowResult { NONE, SHOOK, EVOLVED }

/**
 * Three growth stages, as pure functions of state and time:
 * - a hero hatches as a [Stage.BABY]; it grows by experience ([XpRules]): at [XpRules.BABY_TO_TEEN] a baby is ready,
 *   at [XpRules.TEEN_TO_ADULT] a teen is; only the active pet gains;
 * - an [Stage.ADULT] is final;
 * - a ready pet grows when the phone is shaken [EVOLVE_SHAKES] times; it then lives on the matrix in its new form and
 *   starts from 0;
 * - once grown, the player may put any form up to the one reached on the matrix ([setShown]); growth goes on whatever
 *   is shown.
 */
object GrowthRules {

    const val EVOLVE_SHAKES = 3
    const val JUST_EVOLVED_MS = 8000L

    /**
     * A hero that just hatched on game day [today]. The [first] one of all starts [XpRules.FIRST_HEAD_START] along,
     * so its owner meets an evolution within minutes of hatching it.
     */
    fun hatched(today: Int, first: Boolean = false): Growth =
        Growth(Stage.BABY, Stage.BABY, xp = if (first) XpRules.FIRST_HEAD_START else 0, ready = false, sinceDay = today)

    /** A hero collected before growth existed: a teen, as it always looked, counting from [today]. */
    fun grownUp(today: Int): Growth = Growth(Stage.TEEN, Stage.TEEN, xp = 0, ready = false, sinceDay = today)

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
     * The active pet (on the matrix, not away for an egg) gains [amount], already multiplied, toward its next stage;
     * reaching the threshold makes it [Growth.ready]. A ready pet or an adult gains nothing more. Returns the new state
     * and what was really gained.
     */
    fun gain(s: NurseryState, amount: Int): Pair<NurseryState, Int> {
        if (amount <= 0) return s to 0
        val kind = s.active ?: return s to 0
        if (NurseryRules.onMatrix(s) != null) return s to 0
        val g = of(s, kind) ?: return s to 0
        val need = g.threshold ?: return s to 0
        if (g.ready) return s to 0
        val xp = (g.xp + amount).coerceAtMost(need)
        return s.copy(growth = s.growth + (kind to g.copy(xp = xp, ready = xp >= need))) to (xp - g.xp)
    }

    /**
     * Minutes together, moved to [nowMs]: whole minutes since the last count, each worth [perMinute] when [gains]
     * (the pet on the matrix, awake, fed and well). A gap longer than [XpRules.MAX_GAP_MS], or a clock turned back,
     * starts counting afresh. Returns the new state and the experience gained.
     */
    fun together(s: NurseryState, nowMs: Long, gains: Boolean, perMinute: Int): Pair<NurseryState, Int> {
        val since = nowMs - s.xpAtMs
        if (s.xpAtMs <= 0 || since < 0 || since > XpRules.MAX_GAP_MS) return s.copy(xpAtMs = nowMs) to 0
        val minutes = (since / XpRules.MINUTE_MS).toInt()
        if (minutes <= 0) return s to 0
        val moved = s.copy(xpAtMs = s.xpAtMs + minutes * XpRules.MINUTE_MS)
        if (!gains) return moved to 0
        // An adult has nowhere left to grow: its minutes go toward a new egg instead, while there are heroes to find.
        val kind = moved.active
        val g = kind?.let { of(moved, it) }
        if (g != null && g.threshold == null && NurseryRules.onMatrix(moved) == null) return eggTime(moved, minutes, nowMs) to 0
        return gain(moved, minutes * perMinute)
    }

    /** Whether minutes with an adult can still bring an egg: some hero the daily egg could bring is still missing. */
    fun eggsLeft(s: NurseryState): Boolean = NurseryRules.missing(s).any { it.id !in s.eggs }

    /** [minutes] more with an adult; each [XpRules.EGG_MINUTES] give an egg of a hero not collected yet. */
    fun eggTime(s: NurseryState, minutes: Int, nowMs: Long): NurseryState {
        if (!eggsLeft(s)) return if (s.eggMinutes != 0) s.copy(eggMinutes = 0) else s
        var state = s.copy(eggMinutes = s.eggMinutes + minutes)
        while (state.eggMinutes >= XpRules.EGG_MINUTES) {
            val pool = NurseryRules.missing(state).filter { it.id !in state.eggs }
            if (pool.isEmpty()) return state.copy(eggMinutes = 0)
            val hero = SpeciesCatalog.roll(kotlin.random.Random(nowMs + state.eggs.size), pool)
            state = state.copy(eggs = state.eggs + hero.id, eggMinutes = state.eggMinutes - XpRules.EGG_MINUTES)
        }
        return state
    }

    /**
     * [kind] grows into its next stage and is shown in it, if it is [Growth.ready]; otherwise nothing changes. Marks
     * the moment for [justEvolved].
     */
    fun evolve(s: NurseryState, kind: String, nowMs: Long): NurseryState {
        val g = s.growth[kind] ?: return s
        val next = g.reached.next ?: return s
        if (!g.ready) return s
        val grown = Growth(next, next, xp = 0, ready = false, sinceDay = EggRules.dayKey(nowMs))
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
        val need = g.threshold ?: return s
        if (g.ready) return s
        return s.copy(growth = s.growth + (kind to g.copy(xp = need, ready = true)))
    }

    /** The form a pet just grew into, for a few seconds after it did. */
    fun justEvolved(s: NurseryState, nowMs: Long): Species? =
        if (nowMs - s.evolvedAtMs in 0..JUST_EVOLVED_MS) SpeciesForms.get(s.evolvedId) else null
}
