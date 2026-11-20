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
enum class XpSource { TIME, FEED, BALL, TRICK, SIGNATURE, DEBUG }

/**
 * Growing by experience: all the numbers in one place. Only the active pet gains, and only while it is awake and
 * neither hungry nor sick; while all three needs are full (5 of 5 dots) every gain counts [FULL_MULTIPLIER] times.
 */
object XpRules {
    const val BABY_TO_TEEN = 100
    const val TEEN_TO_ADULT = 300

    /** A minute together: the pet on the matrix (live or Always-on), awake. */
    const val PER_MINUTE = 1
    /** A meal eaten while food showed [FEED_MAX_DOTS] dots or fewer. */
    const val FEED = 10
    const val FEED_MAX_DOTS = 3
    /** A new ball game, at most once per [BALL_COOLDOWN_MS]. */
    const val BALL = 5
    const val BALL_COOLDOWN_MS = 30_000L
    /** A trick: [TRICK_PER] for every [TRICK_EVERY_MS] it played, up to [TRICK_MAX]. */
    const val TRICK_PER = 1
    const val TRICK_EVERY_MS = 5_000L
    const val TRICK_MAX = 12
    /** The signature effect watched to the end on the live matrix. */
    const val SIGNATURE = 3
    const val FULL_MULTIPLIER = 3

    /**
     * The very first hero of all hatches this far along toward growing, so its owner sees an evolution in the first
     * ten minutes rather than on the third evening: feeding the hungry newborn ([FEED]), a game of ball ([BALL]) and
     * one trick ([TRICK_MAX]) cover what is left of [BABY_TO_TEEN] between them. Every hero after it starts from 0.
     */
    const val FIRST_HEAD_START = 75

    /** Minutes together count only while the toy reports at least this often; a longer gap was time away. */
    const val MAX_GAP_MS = 3 * 60_000L
    const val MINUTE_MS = 60_000L

    fun threshold(stage: Stage): Int? = when (stage) {
        Stage.BABY -> BABY_TO_TEEN
        Stage.TEEN -> TEEN_TO_ADULT
        Stage.ADULT -> null
    }

    fun trick(playedMs: Long): Int = ((playedMs.coerceAtLeast(0) / TRICK_EVERY_MS) * TRICK_PER).toInt().coerceAtMost(TRICK_MAX)

    /** Whether the pet gains at all now: awake, not hungry, not sick. */
    fun gains(stats: PetStats, asleep: Boolean): Boolean = when (PetRules.mood(stats, asleep)) {
        Mood.SLEEPING, Mood.SICK, Mood.HUNGRY -> false
        else -> true
    }

    /** [base] as it counts now: times [FULL_MULTIPLIER] while every need shows all its dots. */
    fun multiplied(base: Int, stats: PetStats): Int {
        val full = NeedDots.of(stats.satiety) == NeedDots.MAX && NeedDots.of(stats.joy) == NeedDots.MAX &&
            NeedDots.of(stats.energy) == NeedDots.MAX
        return if (full) base * FULL_MULTIPLIER else base
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
        return if (gains) gain(moved, minutes * perMinute) else moved to 0
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
