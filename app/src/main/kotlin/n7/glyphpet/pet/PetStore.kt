package n7.glyphpet.pet

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import kotlin.random.Random

/** The whole save: needs of the active pet, today's egg, the collection and the supporter unlock. */
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
 * saved state, moves it to now (needs drift, eggs get laid and hatch), applies the change and saves,
 * so both sides can act without overwriting each other.
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
        val edit = prefs.edit()

        var egg = loadEgg()
        if (egg != null && EggRules.isReady(egg, nowMs)) {
            val key = KEY_SEEN + egg.speciesId
            edit.putInt(key, prefs.getInt(key, 0) + 1)
                .putString(KEY_ACTIVE, egg.speciesId)
                .putLong(KEY_HATCHED_AT, nowMs)
                .putString(KEY_HATCHED_ID, egg.speciesId)
            clearEgg(edit)
            egg = null
        }

        val today = EggRules.dayKey(nowMs)
        val laidToday = if (prefs.getInt(KEY_EGG_DAY, 0) == today) prefs.getInt(KEY_EGG_DAY_COUNT, 0) else 0
        val supporter = prefs.getBoolean(KEY_SUPPORTER, false)
        val allowance = EggRules.eggsPerDay(supporter)
        var laid = laidToday
        if (egg == null && laid < allowance) {
            val firstEver = prefs.getString(KEY_ACTIVE, null) == null && !prefs.contains(KEY_EGG_SPECIES)
            val species = SpeciesCatalog.roll(Random(nowMs))
            egg = Egg(species.id, nowMs, 0, if (firstEver) EggRules.FIRST_HATCH_MS else EggRules.HATCH_MS, supporter)
            saveEgg(edit, egg)
            laid += 1
            edit.putInt(KEY_EGG_DAY, today).putInt(KEY_EGG_DAY_COUNT, laid)
        }
        edit.apply()

        val hatchedAt = prefs.getLong(KEY_HATCHED_AT, 0)
        val justHatched = if (nowMs - hatchedAt in 0..JUST_HATCHED_MS) SpeciesCatalog.get(prefs.getString(KEY_HATCHED_ID, null)) else null
        return Nursery(
            stats = stats,
            active = SpeciesCatalog.get(prefs.getString(KEY_ACTIVE, null)),
            egg = egg,
            collection = SpeciesCatalog.all.associate { it.id to prefs.getInt(KEY_SEEN + it.id, 0) },
            supporter = supporter,
            eggsLeftToday = (allowance - laid).coerceAtLeast(0),
            instantHatchAvailable = supporter && egg != null && prefs.getInt(KEY_INSTANT_DAY, 0) != today,
            justHatched = justHatched,
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
    fun warmEgg(nowMs: Long = System.currentTimeMillis()): Boolean {
        val egg = loadEgg() ?: return false
        if (nowMs - prefs.getLong(KEY_LAST_WARM, 0) < EggRules.WARM_COOLDOWN_MS) return false
        prefs.edit().putLong(KEY_EGG_WARM, egg.warmMs + EggRules.WARM_MS).putLong(KEY_LAST_WARM, nowMs).apply()
        return true
    }

    /** Supporters hatch one egg a day right away. */
    @Synchronized
    fun hatchNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val egg = loadEgg() ?: return false
        val today = EggRules.dayKey(nowMs)
        if (!prefs.getBoolean(KEY_SUPPORTER, false) || prefs.getInt(KEY_INSTANT_DAY, 0) == today) return false
        prefs.edit().putLong(KEY_EGG_WARM, egg.hatchMs).putInt(KEY_INSTANT_DAY, today).apply()
        return true
    }

    /** Makes a collected pet the one on the matrix. */
    fun setActive(speciesId: String) {
        if (prefs.getInt(KEY_SEEN + speciesId, 0) > 0) prefs.edit().putString(KEY_ACTIVE, speciesId).apply()
    }

    fun setSupporter(value: Boolean) {
        prefs.edit().putBoolean(KEY_SUPPORTER, value).apply()
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

    private fun loadEgg(): Egg? {
        val species = prefs.getString(KEY_EGG_SPECIES, null) ?: return null
        return Egg(
            speciesId = species,
            laidAtMs = prefs.getLong(KEY_EGG_LAID, 0),
            warmMs = prefs.getLong(KEY_EGG_WARM, 0),
            hatchMs = prefs.getLong(KEY_EGG_HATCH_MS, EggRules.HATCH_MS),
            golden = prefs.getBoolean(KEY_EGG_GOLDEN, false),
        )
    }

    private fun saveEgg(edit: SharedPreferences.Editor, egg: Egg) {
        edit.putString(KEY_EGG_SPECIES, egg.speciesId)
            .putLong(KEY_EGG_LAID, egg.laidAtMs)
            .putLong(KEY_EGG_WARM, egg.warmMs)
            .putLong(KEY_EGG_HATCH_MS, egg.hatchMs)
            .putBoolean(KEY_EGG_GOLDEN, egg.golden)
    }

    private fun clearEgg(edit: SharedPreferences.Editor) {
        edit.remove(KEY_EGG_SPECIES).remove(KEY_EGG_LAID).remove(KEY_EGG_WARM).remove(KEY_EGG_HATCH_MS).remove(KEY_EGG_GOLDEN)
    }

    private companion object {
        const val JUST_HATCHED_MS = 8000L
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
        const val KEY_LAST_WARM = "last_warm_at"
        const val KEY_INSTANT_DAY = "instant_day"
        const val KEY_HATCHED_AT = "hatched_at"
        const val KEY_HATCHED_ID = "hatched_id"
        const val KEY_SUPPORTER = "supporter"
    }
}
