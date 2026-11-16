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
 * @param eggs heroes ready to hatch, oldest first.
 * @param egg the egg in the player's hands: the matrix shows it and the Glyph button cracks it. Null: nothing to hatch.
 * @param presses presses so far, per egg.
 */
data class Nursery(
    val stats: PetStats,
    val active: Species?,
    val eggs: List<Species>,
    val egg: Species?,
    val presses: Map<String, Int>,
    val collection: Map<String, Int>,
    val supporter: Boolean,
    val justHatched: Species?,
) {
    fun pressesOf(id: String): Int = presses[id] ?: 0

    /** How the egg in the hands looks on the matrix, or null when there is nothing to hatch. */
    val eggView: EggView? get() = egg?.let { EggView(EggRules.stage(pressesOf(it.id)), supporter) }
}

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

    fun asleep(stats: PetStats, nowMs: Long = GameClock.now()): Boolean =
        PetRules.isAsleep(stats, PetRules.localHour(nowMs))

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
            egg = SpeciesCatalog.get(NurseryRules.current(state)),
            presses = state.presses,
            collection = SpeciesCatalog.all.associate { it.id to (state.seen[it.id] ?: 0) },
            supporter = state.supporter,
            justHatched = NurseryRules.justHatched(state, nowMs),
        )
    }

    /** Changes the needs of the active pet. */
    @Synchronized
    fun update(nowMs: Long = GameClock.now(), change: (PetStats) -> PetStats = { it }): PetStats {
        val stats = change(PetRules.advance(loadStats(nowMs), nowMs, isCharging()))
        saveStats(stats)
        return stats
    }

    /** Feeds the active pet; false when it was full and refused. */
    fun feed(nowMs: Long = GameClock.now()): Boolean {
        var ate = false
        update(nowMs) { stats -> PetRules.feed(stats).also { ate = it.second }.first }
        return ate
    }

    /**
     * One press on egg [speciesId] (a tap on it in the app, which also puts it in the hands), or with null on the
     * egg in the hands (the Glyph button).
     */
    @Synchronized
    fun pressEgg(speciesId: String? = null, nowMs: Long = GameClock.now()): PressResult {
        val before = NurseryRules.advance(loadState(nowMs), nowMs, Random(nowMs))
        val after = NurseryRules.press(before, speciesId, nowMs)
        saveState(after ?: before)
        return NurseryRules.result(before, after)
    }

    /** Makes a collected pet the one on the matrix. */
    @Synchronized
    fun setActive(speciesId: String, nowMs: Long = GameClock.now()) {
        saveState(NurseryRules.setActive(loadState(nowMs), speciesId))
    }

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
        )
    }

    private fun saveStats(stats: PetStats) {
        prefs.edit()
            .putFloat(KEY_SATIETY, stats.satiety)
            .putFloat(KEY_JOY, stats.joy)
            .putFloat(KEY_ENERGY, stats.energy)
            .putLong(KEY_BORN, stats.bornAtMs)
            .putLong(KEY_UPDATED, stats.updatedAtMs)
            .apply()
    }

    /** The saved nursery; a save from 1.1 (or none at all) goes through [SaveMigration] once and is saved in the 1.2 format. */
    private fun loadState(nowMs: Long): NurseryState {
        if (prefs.getInt(KEY_VERSION, 0) >= VERSION) {
            return NurseryState(
                active = prefs.getString(KEY_ACTIVE, null),
                seen = loadSeen(),
                supporter = prefs.getBoolean(KEY_SUPPORTER, false),
                eggs = prefs.getString(KEY_EGGS, null).orEmpty().split(',').filter { it.isNotEmpty() },
                presses = prefs.getString(KEY_PRESSES, null).orEmpty().split(',').mapNotNull { entry ->
                    val (id, n) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    n.toIntOrNull()?.let { id to it }
                }.toMap(),
                selected = prefs.getString(KEY_SELECTED, null),
                grantDay = prefs.getInt(KEY_GRANT_DAY, 0),
                hatchedAtMs = prefs.getLong(KEY_HATCHED_AT, 0),
                hatchedId = prefs.getString(KEY_HATCHED_ID, null),
            )
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
        val migrated = SaveMigration.from11(old, Random(nowMs))
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
        if (s.selected != null) e.putString(KEY_SELECTED, s.selected) else e.remove(KEY_SELECTED)
        if (s.hatchedId != null) e.putString(KEY_HATCHED_ID, s.hatchedId)
        e.apply()
    }

    private companion object {
        /** 1.2: eggs opened by presses. Saves without this key are from 1.1 (or new). */
        const val VERSION = 2
        const val KEY_VERSION = "save_version"

        const val KEY_SATIETY = "satiety"
        const val KEY_JOY = "joy"
        const val KEY_ENERGY = "energy"
        const val KEY_BORN = "born_at"
        const val KEY_UPDATED = "updated_at"
        const val KEY_ACTIVE = "active_species"
        const val KEY_SEEN = "seen_"
        const val KEY_SUPPORTER = "supporter"
        const val KEY_HATCHED_AT = "hatched_at"
        const val KEY_HATCHED_ID = "hatched_id"
        const val KEY_EGGS = "eggs"
        const val KEY_PRESSES = "egg_presses"
        const val KEY_SELECTED = "egg_selected"
        const val KEY_GRANT_DAY = "grant_day"

        // 1.1's timed egg: read once by the migration, then removed.
        const val KEY_OLD_EGG_SPECIES = "egg_species"
        const val KEY_OLD_EGG_DAY = "egg_day"
        val OLD_KEYS = listOf(
            KEY_OLD_EGG_SPECIES, "egg_laid_at", "egg_warm", "egg_hatch_ms", "egg_golden",
            KEY_OLD_EGG_DAY, "egg_day_count", "ever_laid", "last_warm_at", "instant_day",
        )

        /** Any of these means 1.1 ran and saved its nursery. */
        val OLD_MARKS = listOf(KEY_OLD_EGG_DAY, KEY_OLD_EGG_SPECIES, KEY_ACTIVE, KEY_SUPPORTER)
    }
}
