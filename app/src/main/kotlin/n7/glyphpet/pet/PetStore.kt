package n7.glyphpet.pet

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import kotlin.random.Random

/** The whole save as the screens see it: needs of the active pet, today's egg, the collection and the supporter unlock. */
data class Nursery(
    val stats: PetStats,
    val active: Species?,
    val egg: Egg?,
    val collection: Map<String, Int>,
    val supporter: Boolean,
    val eggsLeftToday: Int,
    val instantHatchAvailable: Boolean,
    val justHatched: Species?,
)

/**
 * The one save, shared by the app screen and the Glyph Toy service. Every change reads the latest
 * saved state, moves it to now with [PetRules] and [NurseryRules], applies the change and saves,
 * so both sides can act without overwriting each other. All game logic lives in those rules.
 */
class PetStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("pet", Context.MODE_PRIVATE)
    private val battery = context.applicationContext.getSystemService(BatteryManager::class.java)

    fun isCharging(): Boolean = battery?.isCharging == true

    fun asleep(stats: PetStats, nowMs: Long = System.currentTimeMillis()): Boolean =
        PetRules.isAsleep(stats, PetRules.localHour(nowMs), isCharging())

    /** Brings everything up to now and returns it. */
    @Synchronized
    fun nursery(nowMs: Long = System.currentTimeMillis()): Nursery {
        val stats = PetRules.advance(loadStats(nowMs), nowMs, isCharging())
        saveStats(stats)
        val state = NurseryRules.advance(loadState(), nowMs, Random(nowMs))
        saveState(state)
        return Nursery(
            stats = stats,
            active = SpeciesCatalog.get(state.active),
            egg = state.egg,
            collection = SpeciesCatalog.all.associate { it.id to (state.seen[it.id] ?: 0) },
            supporter = state.supporter,
            eggsLeftToday = NurseryRules.eggsLeftToday(state, nowMs),
            instantHatchAvailable = NurseryRules.instantHatchAvailable(state, nowMs),
            justHatched = NurseryRules.justHatched(state, nowMs),
        )
    }

    /** Changes the needs of the active pet. */
    @Synchronized
    fun update(nowMs: Long = System.currentTimeMillis(), change: (PetStats) -> PetStats = { it }): PetStats {
        val stats = change(PetRules.advance(loadStats(nowMs), nowMs, isCharging()))
        saveStats(stats)
        return stats
    }

    /** Feeds the active pet; false when it was full and refused. */
    fun feed(nowMs: Long = System.currentTimeMillis()): Boolean {
        var ate = false
        update(nowMs) { stats -> PetRules.feed(stats).also { ate = it.second }.first }
        return ate
    }

    /** Warms today's egg (long-press); false while cooling down or when there is no egg. */
    @Synchronized
    fun warmEgg(nowMs: Long = System.currentTimeMillis()): Boolean = changeState { NurseryRules.warm(it, nowMs) }

    /** Supporters hatch one egg a day right away. */
    @Synchronized
    fun hatchNow(nowMs: Long = System.currentTimeMillis()): Boolean = changeState { NurseryRules.hatchNow(it, nowMs) }

    /** Makes a collected pet the one on the matrix. */
    @Synchronized
    fun setActive(speciesId: String) {
        saveState(NurseryRules.setActive(loadState(), speciesId))
    }

    @Synchronized
    fun setSupporter(value: Boolean) {
        saveState(loadState().copy(supporter = value))
    }

    private fun changeState(change: (NurseryState) -> NurseryState?): Boolean {
        val next = change(loadState()) ?: return false
        saveState(next)
        return true
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

    private fun loadState(): NurseryState {
        val eggSpecies = prefs.getString(KEY_EGG_SPECIES, null)
        return NurseryState(
            egg = eggSpecies?.let {
                Egg(
                    speciesId = it,
                    laidAtMs = prefs.getLong(KEY_EGG_LAID, 0),
                    warmMs = prefs.getLong(KEY_EGG_WARM, 0),
                    hatchMs = prefs.getLong(KEY_EGG_HATCH_MS, EggRules.HATCH_MS),
                    golden = prefs.getBoolean(KEY_EGG_GOLDEN, false),
                )
            },
            active = prefs.getString(KEY_ACTIVE, null),
            seen = SpeciesCatalog.all.associate { it.id to prefs.getInt(KEY_SEEN + it.id, 0) }.filterValues { it > 0 },
            supporter = prefs.getBoolean(KEY_SUPPORTER, false),
            eggDay = prefs.getInt(KEY_EGG_DAY, 0),
            eggDayCount = prefs.getInt(KEY_EGG_DAY_COUNT, 0),
            // Saves from before this flag existed: any recorded egg day means an egg was laid already.
            everLaid = prefs.getBoolean(KEY_EVER_LAID, prefs.contains(KEY_EGG_DAY)),
            lastWarmMs = prefs.getLong(KEY_LAST_WARM, 0),
            instantDay = prefs.getInt(KEY_INSTANT_DAY, 0),
            hatchedAtMs = prefs.getLong(KEY_HATCHED_AT, 0),
            hatchedId = prefs.getString(KEY_HATCHED_ID, null),
        )
    }

    private fun saveState(s: NurseryState) {
        val e = prefs.edit()
        val egg = s.egg
        if (egg == null) {
            e.remove(KEY_EGG_SPECIES).remove(KEY_EGG_LAID).remove(KEY_EGG_WARM).remove(KEY_EGG_HATCH_MS).remove(KEY_EGG_GOLDEN)
        } else {
            e.putString(KEY_EGG_SPECIES, egg.speciesId)
                .putLong(KEY_EGG_LAID, egg.laidAtMs)
                .putLong(KEY_EGG_WARM, egg.warmMs)
                .putLong(KEY_EGG_HATCH_MS, egg.hatchMs)
                .putBoolean(KEY_EGG_GOLDEN, egg.golden)
        }
        if (s.active != null) e.putString(KEY_ACTIVE, s.active) else e.remove(KEY_ACTIVE)
        s.seen.forEach { (id, count) -> e.putInt(KEY_SEEN + id, count) }
        e.putBoolean(KEY_SUPPORTER, s.supporter)
            .putInt(KEY_EGG_DAY, s.eggDay)
            .putInt(KEY_EGG_DAY_COUNT, s.eggDayCount)
            .putBoolean(KEY_EVER_LAID, s.everLaid)
            .putLong(KEY_LAST_WARM, s.lastWarmMs)
            .putInt(KEY_INSTANT_DAY, s.instantDay)
            .putLong(KEY_HATCHED_AT, s.hatchedAtMs)
        if (s.hatchedId != null) e.putString(KEY_HATCHED_ID, s.hatchedId)
        e.apply()
    }

    private companion object {
        const val KEY_SATIETY = "satiety"
        const val KEY_JOY = "joy"
        const val KEY_ENERGY = "energy"
        const val KEY_BORN = "born_at"
        const val KEY_UPDATED = "updated_at"
        const val KEY_ACTIVE = "active_species"
        const val KEY_SEEN = "seen_"
        const val KEY_EGG_SPECIES = "egg_species"
        const val KEY_EGG_LAID = "egg_laid_at"
        const val KEY_EGG_WARM = "egg_warm"
        const val KEY_EGG_HATCH_MS = "egg_hatch_ms"
        const val KEY_EGG_GOLDEN = "egg_golden"
        const val KEY_EGG_DAY = "egg_day"
        const val KEY_EGG_DAY_COUNT = "egg_day_count"
        const val KEY_EVER_LAID = "ever_laid"
        const val KEY_LAST_WARM = "last_warm_at"
        const val KEY_INSTANT_DAY = "instant_day"
        const val KEY_HATCHED_AT = "hatched_at"
        const val KEY_HATCHED_ID = "hatched_id"
        const val KEY_SUPPORTER = "supporter"
    }
}
