package n7.glyphpet.pet

import android.content.Context
import android.os.BatteryManager

/**
 * The one saved pet, shared by the app screen and the Glyph Toy service.
 * Every change reads the latest saved state, moves it to now, applies the change and saves it,
 * so both sides can act on the pet without overwriting each other.
 */
class PetStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pet", Context.MODE_PRIVATE)
    private val battery = context.applicationContext.getSystemService(BatteryManager::class.java)

    fun isCharging(): Boolean = battery?.isCharging == true

    @Synchronized
    fun update(nowMs: Long = System.currentTimeMillis(), change: (PetStats) -> PetStats = { it }): PetStats {
        val advanced = PetRules.advance(load(nowMs), nowMs, isCharging())
        val changed = change(advanced)
        save(changed)
        return changed
    }

    /** Feeds the pet; returns false when it was full and refused. */
    fun feed(nowMs: Long = System.currentTimeMillis()): Boolean {
        var ate = false
        update(nowMs) { stats -> PetRules.feed(stats).also { ate = it.second }.first }
        return ate
    }

    fun asleep(stats: PetStats, nowMs: Long = System.currentTimeMillis()): Boolean =
        PetRules.isAsleep(stats, PetRules.localHour(nowMs), isCharging())

    private fun load(nowMs: Long): PetStats {
        if (!prefs.contains(KEY_BORN)) return PetStats.newborn(nowMs).also(::save)
        return PetStats(
            satiety = prefs.getFloat(KEY_SATIETY, 80f),
            joy = prefs.getFloat(KEY_JOY, 80f),
            energy = prefs.getFloat(KEY_ENERGY, 80f),
            bornAtMs = prefs.getLong(KEY_BORN, nowMs),
            updatedAtMs = prefs.getLong(KEY_UPDATED, nowMs),
        )
    }

    private fun save(stats: PetStats) {
        prefs.edit()
            .putFloat(KEY_SATIETY, stats.satiety)
            .putFloat(KEY_JOY, stats.joy)
            .putFloat(KEY_ENERGY, stats.energy)
            .putLong(KEY_BORN, stats.bornAtMs)
            .putLong(KEY_UPDATED, stats.updatedAtMs)
            .apply()
    }

    private companion object {
        const val KEY_SATIETY = "satiety"
        const val KEY_JOY = "joy"
        const val KEY_ENERGY = "energy"
        const val KEY_BORN = "born_at"
        const val KEY_UPDATED = "updated_at"
    }
}
