package n7.glyphpet.pet

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import kotlin.math.abs
import kotlin.random.Random
import n7.glyphpet.GameClock

/** The charger as the pet sees it. [watts] is what flows into the battery, [level] is the charge, 0..1. */
data class Power(val plugged: Boolean, val watts: Float, val level: Float) {
    companion object {
        val NONE = Power(plugged = false, watts = 0f, level = 1f)

        /** Shown when the phone charges but does not say how fast. */
        const val UNKNOWN_WATTS = 10f

        /**
         * [currentNow] as BatteryManager reports it: microamperes, of either sign depending on the phone,
         * Int.MIN_VALUE when unsupported.
         */
        fun watts(currentNow: Int, milliVolts: Int): Float {
            if (currentNow == Int.MIN_VALUE || currentNow == 0 || milliVolts <= 0) return UNKNOWN_WATTS
            return (abs(currentNow.toLong()) / 1_000_000f * (milliVolts / 1000f)).coerceAtMost(120f)
        }
    }
}

/**
 * The whole save as the screens see it: needs of the active pet, the eggs, the collection and the supporter unlock.
 * @param eggs heroes ready to hatch, oldest first. They wait in the app until the player puts one on the matrix.
 * @param eggOnMatrix the egg on the matrix ([NurseryRules.onMatrix]): the matrix shows it instead of the pet and the
 *   Glyph button cracks it. Null: the pet is on the matrix.
 * @param presses presses so far, per egg.
 * @param active the active pet as its hero (the teen of [SpeciesCatalog]: collection, names); [activeForm] is the form
 *   of it that lives on the matrix.
 * @param growth how far each collected hero has grown, by kind.
 * @param justEvolved the form a pet just grew into, for a few seconds.
 */
data class Nursery(
    val stats: PetStats,
    val active: Species?,
    val eggs: List<Species>,
    val eggOnMatrix: Species?,
    val presses: Map<String, Int>,
    val collection: Map<String, Int>,
    val supporter: Boolean,
    val justHatched: Species?,
    val activeForm: Species? = active,
    val growth: Map<String, Growth> = emptyMap(),
    val justEvolved: Species? = null,
) {
    fun pressesOf(id: String): Int = presses[id] ?: 0

    /** How far the active pet has grown, or null without a pet. */
    val activeGrowth: Growth? get() = active?.let { growth[it.id] }

    /** The active pet may grow now: shakes grow it. */
    val ready: Boolean get() = activeGrowth?.let { it.ready && it.reached.next != null } == true

    /** How the egg on the matrix looks, or null while the pet is on the matrix. */
    val eggView: EggView? get() = eggOnMatrix?.let { EggView(EggRules.stage(pressesOf(it.id)), supporter, it.rarity) }
}

/** What a meal did: whether the pet [ate] it, and the experience it brought. */
data class FeedResult(val ate: Boolean)

/**
 * The one save, shared by the app screen and the Glyph Toy service. Every change reads the latest
 * saved state, moves it to now with [PetRules] and [NurseryRules], applies the change and saves,
 * so both sides can act without overwriting each other. All game logic lives in those rules.
 */
class PetStore(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences("pet", Context.MODE_PRIVATE)
    private val battery = app.getSystemService(BatteryManager::class.java)

    fun isCharging(): Boolean = battery?.isCharging == true

    /** Cable state, charging speed and battery level right now. Costs a system call: read it every second or two, not every frame. */
    fun power(): Power {
        val sticky = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return Power.NONE
        val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = (sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, scale).toFloat() / scale).coerceIn(0f, 1f)
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val watts = if (plugged && status == BatteryManager.BATTERY_STATUS_CHARGING) {
            Power.watts(
                currentNow = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE,
                milliVolts = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            )
        } else {
            0f
        }
        return Power(plugged, watts, if (full) 1f else level)
    }

    /** The pet sleeps when it is worn out, at any hour ([PetRules.isAsleep]); [nowMs] is kept for the callers' sake. */
    @Suppress("UNUSED_PARAMETER")
    fun asleep(stats: PetStats, nowMs: Long = GameClock.now()): Boolean = PetRules.isAsleep(stats)

    /** Brings everything up to now and returns it. */
    @Synchronized
    fun nursery(nowMs: Long = GameClock.now()): Nursery {
        val stats = PetRules.advance(loadStats(nowMs), nowMs, isCharging())
        saveStats(stats)
        val state = NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs))
        saveState(state)
        return Nursery(
            stats = stats,
            active = SpeciesCatalog.get(state.active),
            eggs = NurseryRules.available(state).mapNotNull(SpeciesCatalog::get),
            eggOnMatrix = SpeciesCatalog.get(NurseryRules.onMatrix(state)),
            presses = state.presses,
            collection = SpeciesCatalog.all.associate { it.id to (state.seen[it.id] ?: 0) },
            supporter = state.supporter,
            justHatched = NurseryRules.justHatched(state, nowMs),
            activeForm = GrowthRules.activeForm(state),
            growth = state.seen.keys.mapNotNull { id -> GrowthRules.of(state, id)?.let { id to it } }.toMap(),
            justEvolved = GrowthRules.justEvolved(state, nowMs),
        )
    }

    /** Changes the needs of the active pet. */
    @Synchronized
    fun update(nowMs: Long = GameClock.now(), change: (PetStats) -> PetStats = { it }): PetStats {
        val stats = change(PetRules.advance(loadStats(nowMs), nowMs, isCharging()))
        saveStats(stats)
        return stats
    }

    /** Feeds the active pet a dot of food; [FeedResult.ate] is false when it was full and refused. */
    @Synchronized
    fun feed(nowMs: Long = GameClock.now()): FeedResult {
        var ate = false
        update(nowMs) { stats -> PetRules.feed(stats).also { ate = it.second }.first }
        return FeedResult(ate)
    }

    /**
     * A game, if the pet has the energy for one ([PetRules.canPlay]): it costs a dot of energy and half a dot of food
     * ([PetRules.play]); its joy comes while it is played ([playing]). Returns false, changing nothing, when it is too tired.
     */
    @Synchronized
    fun play(nowMs: Long = GameClock.now()): Boolean {
        var played = false
        update(nowMs) { if (PetRules.canPlay(it)) PetRules.play(it).also { played = true } else it }
        return played
    }

    /** [ms] more of a game played: its joy ([PetRules.playing]). */
    @Synchronized
    fun playing(ms: Long, nowMs: Long = GameClock.now()) {
        if (ms > 0) update(nowMs) { PetRules.playing(it, ms) }
    }

    /** Stroked: half a dot of joy ([PetRules.stroke]). */
    @Synchronized
    fun stroke(nowMs: Long = GameClock.now()) {
        update(nowMs, PetRules::stroke)
    }

    /**
     * The active pet earns [base] experience, if it gains now (awake and not hungry, see [XpRules.gains]). Returns
     * what it really gained; never throws.
     */
    @Synchronized
    fun gain(base: Int, nowMs: Long = GameClock.now()): Int = runCatching {
        val stats = update(nowMs)
        if (!XpRules.gains(stats, asleep(stats, nowMs))) return@runCatching 0
        val (after, gained) = GrowthRules.gain(NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs)), base)
        if (gained > 0) saveState(after)
        gained
    }.getOrDefault(0)

    /**
     * The toy reports that the pet is on the matrix now ([seen]: lit, not away for an egg, nobody turned it dark):
     * the whole minutes since the last report count as time together ([GrowthRules.together]). Returns the experience
     * gained; never throws.
     */
    @Synchronized
    fun together(seen: Boolean, nowMs: Long = GameClock.now()): Int = runCatching {
        // The needs run on the matrix's fast clock from now on while it is seen, on the slow one once it is not.
        val stats = update(nowMs) { it.copy(seenAtMs = if (seen) nowMs else 0L) }
        val gains = seen && XpRules.gains(stats, asleep(stats, nowMs))
        val before = loadState(nowMs)
        val (after, gained) = GrowthRules.together(before, nowMs, gains, XpRules.PER_MINUTE)
        if (after != before) saveState(after)
        gained
    }.getOrDefault(0)

    /**
     * Grants the egg of every anomaly whose condition is met now ([AnomalyUnlocks]) and the player has neither hatched
     * nor been given yet. Returns the kinds granted.
     */
    @Synchronized
    fun anomalies(charging: Boolean, battery: Float, desk: Boolean, deskMs: Long, nowMs: Long = GameClock.now()): List<String> = runCatching {
        val state = loadState(nowMs)
        val facts = facts(state, charging, battery, desk, deskMs, nowMs)
        val new = n7.glyphpet.pet.drawn.Drawn.anomalies.map { it.kind }
            .filter { (state.seen[it] ?: 0) == 0 && it !in state.eggs && AnomalyUnlocks.met(it, facts) }
        if (new.isNotEmpty()) saveState(state.copy(eggs = new + state.eggs))
        new
    }.getOrDefault(emptyList())

    /** The facts [AnomalyUnlocks] reads, for the nursery's hints. */
    fun anomalyFacts(nowMs: Long = GameClock.now()): AnomalyUnlocks.Facts =
        facts(loadState(nowMs), false, 0f, false, 0L, nowMs)

    private fun facts(state: NurseryState, charging: Boolean, battery: Float, desk: Boolean, deskMs: Long, nowMs: Long) = AnomalyUnlocks.Facts(
        charging = charging, battery = battery, stats = runCatching { loadStats(nowMs) }.getOrNull(),
        collected = state.seen.filterValues { it > 0 }.keys,
        adults = state.growth.values.count { it.reached == Stage.ADULT },
        nowMs = nowMs, desk = desk, deskMs = deskMs,
    )

    /** A shake while the active pet is ready to grow: counts it, the last one grows the pet (see [GrowthRules.shake]). */
    @Synchronized
    fun growShake(nowMs: Long = GameClock.now()): GrowResult {
        val (after, result) = GrowthRules.shake(NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs)), nowMs)
        saveState(after)
        return result
    }

    /** Puts the [stage] form of [kind] on the matrix, if that hero has grown that far. */
    @Synchronized
    fun setShown(kind: String, stage: Stage, nowMs: Long = GameClock.now()) {
        saveState(GrowthRules.setShown(loadState(nowMs), kind, stage))
    }

    /** The ids of the states of form [formId] the owner has seen ([Discovery]); they stay open for good. */
    fun found(formId: String): Set<String> = prefs.getStringSet(KEY_FOUND + formId, null)?.toSet() ?: emptySet()

    /** Opens [states] of form [formId] in the album; true when one was new. */
    @Synchronized
    fun discover(formId: String, states: Collection<PetState>): Boolean {
        val have = found(formId)
        val new = states.map { it.id }.filter { it !in have }
        if (new.isEmpty()) return false
        prefs.edit().putStringSet(KEY_FOUND + formId, have + new).apply()
        return true
    }

    /** Debug builds: every state of every form open. */
    fun discoverAll() {
        for (form in SpeciesForms.all) discover(form.id, PetState.entries)
    }

    /** Debug builds: the active pet gets [amount] experience at once, whatever its needs. */
    @Synchronized
    fun debugXp(amount: Int, nowMs: Long = GameClock.now()) {
        saveState(GrowthRules.gain(loadState(nowMs), amount).first)
    }

    /**
     * Debug builds: everything open at once. Every hero collected and grown up (the form on the matrix stays as it
     * was), no eggs waiting, a pet on the matrix.
     */
    @Synchronized
    fun unlockAll(nowMs: Long = GameClock.now()) {
        val s = loadState(nowMs)
        val today = EggRules.dayKey(nowMs)
        val growth = SpeciesCatalog.all.associate { hero ->
            hero.id to (s.growth[hero.id]?.copy(reached = Stage.ADULT, xp = 0, ready = false, evolvePresses = 0)
                ?: Growth(Stage.ADULT, Stage.ADULT, 0, false, today))
        }
        val open = s.copy(
            seen = SpeciesCatalog.all.associate { it.id to maxOf(1, s.seen[it.id] ?: 0) },
            growth = growth,
            eggs = emptyList(),
            presses = emptyMap(),
            onMatrix = null,
        )
        saveState(if (open.active == null) NurseryRules.setActive(open, SpeciesCatalog.all.first().id) else open)
    }

    /** One long press of the Glyph button on the egg on the matrix; the fifth hatches it. */
    @Synchronized
    fun pressEgg(nowMs: Long = GameClock.now()): PressResult {
        val before = NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs))
        val after = NurseryRules.press(before, nowMs)
        saveState(after ?: before)
        return NurseryRules.result(before, after)
    }

    /** Puts egg [speciesId] on the matrix in place of the pet (see [NurseryRules.putOnMatrix]). */
    @Synchronized
    fun putEggOnMatrix(speciesId: String, nowMs: Long = GameClock.now()) {
        saveState(NurseryRules.putOnMatrix(NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs)), speciesId))
    }

    /** Takes the egg off the matrix: the pet comes back, the egg keeps its presses. */
    @Synchronized
    fun takeEggOffMatrix(nowMs: Long = GameClock.now()) {
        saveState(NurseryRules.takeOffMatrix(loadState(nowMs)))
    }

    /** Makes a collected pet the one on the matrix. */
    @Synchronized
    fun setActive(speciesId: String, nowMs: Long = GameClock.now()) {
        saveState(NurseryRules.setActive(loadState(nowMs), speciesId))
    }

    /** Setting: the matrix goes dark while the phone lies screen up or is in a pocket. On by default. */
    var hideWhenUnseen: Boolean
        get() = prefs.getBoolean(KEY_HIDE_UNSEEN, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_UNSEEN, value).apply()

    /** Setting: the phone answers the pet with short, soft vibrations (feeding, cracking, growing, play). On by default. */
    var vibration: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    @Synchronized
    fun setSupporter(value: Boolean, nowMs: Long = GameClock.now()) {
        saveState(loadState(nowMs).copy(supporter = value))
    }

    private fun loadStats(nowMs: Long): PetStats {
        if (!prefs.contains(KEY_BORN)) return PetStats.newborn(nowMs).also(::saveStats)
        return PetStats(
            satiety = prefs.getFloat(KEY_SATIETY, 80f),
            joy = prefs.getFloat(KEY_JOY, 80f),
            energy = prefs.getFloat(KEY_ENERGY, 80f),
            bornAtMs = prefs.getLong(KEY_BORN, nowMs),
            updatedAtMs = prefs.getLong(KEY_UPDATED, nowMs),
            napping = prefs.getBoolean(KEY_NAPPING, false),
            seenAtMs = prefs.getLong(KEY_SEEN_AT, 0),
        )
    }

    private fun saveStats(stats: PetStats) {
        prefs.edit()
            .putFloat(KEY_SATIETY, stats.satiety)
            .putFloat(KEY_JOY, stats.joy)
            .putFloat(KEY_ENERGY, stats.energy)
            .putLong(KEY_BORN, stats.bornAtMs)
            .putLong(KEY_UPDATED, stats.updatedAtMs)
            .putBoolean(KEY_NAPPING, stats.napping)
            .putLong(KEY_SEEN_AT, stats.seenAtMs)
            .apply()
    }

    /**
     * The saved nursery; a save from 1.1 (or none at all) goes through [SaveMigration] once and is saved in the current
     * format. Heroes collected before growth stages (1.2 saves) become teens ([SaveMigration.withGrowth]).
     */
    private fun loadState(nowMs: Long): NurseryState {
        val today = EggRules.dayKey(nowMs)
        val version = prefs.getInt(KEY_VERSION, 0)
        if (version >= VERSION_EGGS) {
            val loaded = NurseryState(
                active = prefs.getString(KEY_ACTIVE, null),
                seen = loadSeen(),
                supporter = prefs.getBoolean(KEY_SUPPORTER, false),
                eggs = prefs.getString(KEY_EGGS, null).orEmpty().split(',').filter { it.isNotEmpty() },
                presses = prefs.getString(KEY_PRESSES, null).orEmpty().split(',').mapNotNull { entry ->
                    val (id, n) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    n.toIntOrNull()?.let { id to it }
                }.toMap(),
                onMatrix = prefs.getString(KEY_ON_MATRIX, null),
                grantDay = prefs.getInt(KEY_GRANT_DAY, 0),
                hatchedAtMs = prefs.getLong(KEY_HATCHED_AT, 0),
                hatchedId = prefs.getString(KEY_HATCHED_ID, null),
                growth = prefs.getString(KEY_GROWTH, null).orEmpty().split(',')
                    .mapNotNull { parseGrowth(it, careDays = version < VERSION_XP) }.toMap(),
                xpAtMs = prefs.getLong(KEY_XP_AT, 0),
                evolvedAtMs = prefs.getLong(KEY_EVOLVED_AT, 0),
                evolvedId = prefs.getString(KEY_EVOLVED_ID, null),
            )
            val grown = SaveMigration.withGrowth(loaded, today)
            if (version < VERSION || grown !== loaded) saveState(grown)
            return grown
        }
        val old = if (OLD_MARKS.any(prefs::contains) || loadSeen().isNotEmpty()) {
            Nursery11(
                eggSpecies = prefs.getString(KEY_OLD_EGG_SPECIES, null),
                active = prefs.getString(KEY_ACTIVE, null),
                seen = loadSeen(),
                supporter = prefs.getBoolean(KEY_SUPPORTER, false),
                eggDay = prefs.getInt(KEY_OLD_EGG_DAY, 0),
                hatchedAtMs = prefs.getLong(KEY_HATCHED_AT, 0),
                hatchedId = prefs.getString(KEY_HATCHED_ID, null),
            )
        } else {
            null
        }
        val migrated = SaveMigration.withGrowth(SaveMigration.from11(old, Random(nowMs)), today)
        saveState(migrated)
        return migrated
    }

    private fun loadSeen(): Map<String, Int> =
        SpeciesCatalog.all.associate { it.id to prefs.getInt(KEY_SEEN + it.id, 0) }.filterValues { it > 0 }

    private fun saveState(s: NurseryState) {
        val e = prefs.edit()
        OLD_KEYS.forEach { e.remove(it) }
        if (s.active != null) e.putString(KEY_ACTIVE, s.active) else e.remove(KEY_ACTIVE)
        s.seen.forEach { (id, count) -> e.putInt(KEY_SEEN + id, count) }
        e.putInt(KEY_VERSION, VERSION)
            .putBoolean(KEY_SUPPORTER, s.supporter)
            .putString(KEY_EGGS, s.eggs.joinToString(","))
            .putString(KEY_PRESSES, s.presses.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putInt(KEY_GRANT_DAY, s.grantDay)
            .putLong(KEY_HATCHED_AT, s.hatchedAtMs)
        if (s.onMatrix != null) e.putString(KEY_ON_MATRIX, s.onMatrix) else e.remove(KEY_ON_MATRIX)
        if (s.hatchedId != null) e.putString(KEY_HATCHED_ID, s.hatchedId)
        e.putString(KEY_GROWTH, s.growth.entries.joinToString(",") { (kind, g) -> formatGrowth(kind, g) })
            .putLong(KEY_XP_AT, s.xpAtMs)
            .putLong(KEY_EVOLVED_AT, s.evolvedAtMs)
        if (s.evolvedId != null) e.putString(KEY_EVOLVED_ID, s.evolvedId)
        e.apply()
    }

    /** One hero's growth as "kind:reached:shown:xp:ready:sinceDay:evolvePresses", e.g. "cat:TEEN:BABY:180:0:20261118:0". */
    private fun formatGrowth(kind: String, g: Growth): String =
        listOf(kind, g.reached.name, g.shown.name, g.xp, if (g.ready) 1 else 0, g.sinceDay, g.evolvePresses).joinToString(":")

    /**
     * The reverse of [formatGrowth]; null for an entry it cannot read, which then counts as never saved. A save from
     * before experience ([careDays]) holds care days in the fourth field: they become experience ([XpRules.fromCareDays]).
     */
    private fun parseGrowth(entry: String, careDays: Boolean): Pair<String, Growth>? {
        val p = entry.split(':').takeIf { it.size == 7 } ?: return null
        val reached = Stage.entries.firstOrNull { it.name == p[1] } ?: return null
        val shown = Stage.entries.firstOrNull { it.name == p[2] }?.takeIf { it <= reached } ?: reached
        val n = (p[3].toIntOrNull() ?: 0).coerceAtLeast(0)
        val need = XpRules.threshold(reached) ?: 0
        return p[0] to Growth(
            reached = reached,
            shown = shown,
            xp = (if (careDays) XpRules.fromCareDays(reached, n) else n).coerceAtMost(need),
            ready = p[4] == "1",
            sinceDay = p[5].toIntOrNull() ?: 0,
            evolvePresses = p[6].toIntOrNull() ?: 0,
        )
    }

    private companion object {
        /** 1.2: eggs opened by presses. Saves without this key are from 1.1 (or new). */
        const val VERSION_EGGS = 2

        /**
         * Growth by experience. Version 3 brought growth stages ([KEY_GROWTH]; a version 2 save has none, its heroes
         * become teens) and holds care days where experience is now.
         */
        const val VERSION_XP = 4
        const val VERSION = VERSION_XP
        const val KEY_VERSION = "save_version"

        const val KEY_SATIETY = "satiety"
        const val KEY_JOY = "joy"
        const val KEY_ENERGY = "energy"
        const val KEY_BORN = "born_at"
        const val KEY_UPDATED = "updated_at"
        const val KEY_NAPPING = "napping"
        const val KEY_SEEN_AT = "seen_at"
        const val KEY_FOUND = "found_"
        const val KEY_ACTIVE = "active_species"
        const val KEY_SEEN = "seen_"
        const val KEY_SUPPORTER = "supporter"
        const val KEY_HATCHED_AT = "hatched_at"
        const val KEY_HATCHED_ID = "hatched_id"
        const val KEY_EGGS = "eggs"
        const val KEY_PRESSES = "egg_presses"
        /** The egg on the matrix. Saves without it have none: the pet stays on the matrix. */
        const val KEY_ON_MATRIX = "egg_on_matrix"
        const val KEY_GRANT_DAY = "grant_day"
        const val KEY_HIDE_UNSEEN = "hide_unseen"
        const val KEY_VIBRATION = "vibration"
        const val KEY_GROWTH = "growth"
        const val KEY_XP_AT = "xp_at"
        const val KEY_EVOLVED_AT = "evolved_at"
        const val KEY_EVOLVED_ID = "evolved_id"

        // Keys of older saves, removed on the next save. 1.1's timed egg is read once by the migration first.
        const val KEY_OLD_EGG_SPECIES = "egg_species"
        const val KEY_OLD_EGG_DAY = "egg_day"
        val OLD_KEYS = listOf(
            KEY_OLD_EGG_SPECIES, "egg_laid_at", "egg_warm", "egg_hatch_ms", "egg_golden",
            KEY_OLD_EGG_DAY, "egg_day_count", "ever_laid", "last_warm_at", "instant_day",
            // Before eggs were put on the matrix: the egg in the hands, which sat in the corner beside the pet.
            "egg_selected",
            // Before growth by experience: the care of the day.
            "care_day", "care_fed", "care_sick",
            // Before the drop was dropped: the plaster the pet wore until the next morning.
            "hurt_until",
        )

        /** Any of these means 1.1 ran and saved its nursery. */
        val OLD_MARKS = listOf(KEY_OLD_EGG_DAY, KEY_OLD_EGG_SPECIES, KEY_ACTIVE, KEY_SUPPORTER)
    }
}
