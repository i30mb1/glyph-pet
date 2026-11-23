package n7.glyphpet.pet

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.util.Calendar
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory

/**
 * Saves written by version 1.1 (versionCode 5) must load into the same pet, needs and collection; 1.1's waiting egg
 * becomes an egg to open (see [SaveMigration]).
 * The keys and types below are copied from PetStore.saveStats / saveState as committed in "Release 1.1"
 * (git show 42a56be:app/src/main/kotlin/n7/glyphpet/pet/PetStore.kt), written literally so a later rename of a key
 * in PetStore breaks this test instead of silently dropping players' saves.
 */
class PetStoreOldSaveTest {

    /** A plain in-memory SharedPreferences, enough for PetStore. */
    private class MemoryPrefs : SharedPreferences {
        val map = HashMap<String, Any?>()
        override fun getAll(): Map<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?) = if (map.containsKey(key)) map[key] as String? else defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?) = if (map.containsKey(key)) map[key] as Set<String>? else defValues
        override fun getInt(key: String, defValue: Int) = map[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as Boolean? ?: defValue
        override fun contains(key: String) = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val puts = HashMap<String, Any?>()
            val removes = HashSet<String>()
            var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                removes.forEach { map.remove(it) }
                map.putAll(puts)
            }
        }
    }

    /**
     * Just enough of a Context for PetStore: its "pet" prefs and no BatteryManager (not charging).
     * Context.getSystemService(Class) is final in android.jar, so the fake cannot be written in this file. It is compiled
     * at test time against the unit-test android.jar, which AGP generates without final modifiers.
     */
    private fun fakeContext(prefs: SharedPreferences): Context {
        val dir = createTempDirectory("fakectx").toFile()
        val src = File(dir, "reviewfake/FakeCtx.java").apply { parentFile!!.mkdirs() }
        src.writeText(
            """
            package reviewfake;
            import android.content.*;
            public class FakeCtx extends ContextWrapper {
                private final SharedPreferences prefs;
                public FakeCtx(SharedPreferences prefs) { super(null); this.prefs = prefs; }
                @Override public Context getApplicationContext() { return this; }
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    if (!"pet".equals(name)) throw new IllegalArgumentException(name);
                    return prefs;
                }
                @Override public <T> T getSystemService(Class<T> serviceClass) { return null; }
            }
            """.trimIndent(),
        )
        val androidJar = File(Context::class.java.protectionDomain!!.codeSource.location.toURI()).path
        val javac = ToolProvider.getSystemJavaCompiler() ?: error("tests need a JDK")
        assertEquals(0, javac.run(null, null, null, "-cp", androidJar, "-d", dir.path, src.path))
        val loader = URLClassLoader(arrayOf(dir.toURI().toURL()), javaClass.classLoader)
        return loader.loadClass("reviewfake.FakeCtx").getConstructor(SharedPreferences::class.java).newInstance(prefs) as Context
    }

    /** 1.1's timed egg, as it was saved. */
    private class Egg11(val speciesId: String, val laidAtMs: Long, val warmMs: Long, val hatchMs: Long, val golden: Boolean)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    /** Exactly what 1.1's saveStats + saveState put into the "pet" prefs. */
    private fun write11(
        prefs: MemoryPrefs,
        stats: PetStats,
        egg: Egg11?,
        active: String?,
        seen: Map<String, Int>,
        supporter: Boolean,
        eggDay: Int,
        eggDayCount: Int,
        everLaid: Boolean?,
        lastWarmMs: Long,
        instantDay: Int,
        hatchedAtMs: Long,
        hatchedId: String?,
    ) {
        val m = prefs.map
        m["satiety"] = stats.satiety
        m["joy"] = stats.joy
        m["energy"] = stats.energy
        m["born_at"] = stats.bornAtMs
        m["updated_at"] = stats.updatedAtMs
        if (egg != null) {
            m["egg_species"] = egg.speciesId
            m["egg_laid_at"] = egg.laidAtMs
            m["egg_warm"] = egg.warmMs
            m["egg_hatch_ms"] = egg.hatchMs
            m["egg_golden"] = egg.golden
        }
        if (active != null) m["active_species"] = active
        seen.forEach { (id, count) -> m["seen_$id"] = count }
        m["supporter"] = supporter
        m["egg_day"] = eggDay
        m["egg_day_count"] = eggDayCount
        if (everLaid != null) m["ever_laid"] = everLaid
        m["last_warm_at"] = lastWarmMs
        m["instant_day"] = instantDay
        m["hatched_at"] = hatchedAtMs
        if (hatchedId != null) m["hatched_id"] = hatchedId
    }

    /** The keys 1.1 used for its timed egg; 1.2 reads them once and removes them. */
    private val oldEggKeys = listOf(
        "egg_species", "egg_laid_at", "egg_warm", "egg_hatch_ms", "egg_golden",
        "egg_day", "egg_day_count", "ever_laid", "last_warm_at", "instant_day",
    )

    @Test
    fun `a 1_1 supporter's save loads into the same pet, needs and collection, the waiting egg is an egg to open`() {
        val now = at(2026, 11, 16, 14, 30)
        val stats = PetStats(satiety = 62.5f, joy = 41.25f, energy = 77f, bornAtMs = now - 9 * PetStats.DAY_MS, updatedAtMs = now)
        val egg = Egg11(speciesId = "owl", laidAtMs = now - 2 * 60 * 60 * 1000L, warmMs = 3 * 45 * 60_000L, hatchMs = 10 * 60 * 60_000L, golden = true)
        val seen = mapOf("cat" to 3, "fox" to 1, "ufo" to 2, "bot" to 1)
        val prefs = MemoryPrefs()
        write11(
            prefs, stats, egg, active = "fox", seen = seen, supporter = true,
            eggDay = EggRules.dayKey(now), eggDayCount = 2, everLaid = true, lastWarmMs = now - 60_000,
            instantDay = EggRules.dayKey(now), hatchedAtMs = now - PetStats.DAY_MS, hatchedId = "fox",
        )
        val before = HashMap(prefs.map)

        val store = PetStore(fakeContext(prefs))
        val n = store.nursery(now)

        assertEquals(stats, n.stats)
        assertEquals("fox", n.active?.id)
        assertEquals(SpeciesCatalog.all.associate { it.id to (seen[it.id] ?: 0) }, n.collection)
        assertTrue(n.supporter)
        assertNull(n.justHatched)
        assertEquals(Mood.CONTENT, PetRules.mood(n.stats, asleep = false))
        // The owl it was hatching is the oldest egg, waiting in the app, not cracked yet; as a supporter every other
        // missing pet is there too. The pet stays on the matrix.
        assertEquals("owl", n.eggs.first().id)
        assertEquals(SpeciesCatalog.all.map { it.id }.toSet() - seen.keys, n.eggs.map { it.id }.toSet())
        assertNull(n.eggOnMatrix)
        // Needs, collection counts and the pet are saved under the same keys with the same values; 1.1's egg keys are gone.
        val kept = before.keys.filter { it !in oldEggKeys }
        for (k in kept) assertEquals(k, before[k], prefs.map[k])
        for (k in oldEggKeys) assertFalse(k, prefs.map.containsKey(k))
        // Loaded again: the same, nothing migrated twice, no second egg the same day.
        val again = PetStore(fakeContext(prefs)).nursery(now + 60_000)
        assertEquals(n.eggs, again.eggs)
        assertEquals(n.collection, again.collection)
        // Put on the matrix (golden: a supporter's), five presses open the owl.
        store.putEggOnMatrix("owl", nowMs = now + 110_000)
        assertEquals(EggView(EggStage.WHOLE, golden = true, rarity = Rarity.RARE), store.nursery(now + 110_000).eggView)
        repeat(4) { assertEquals(PressResult.CRACKED, store.pressEgg(nowMs = now + 120_000)) }
        assertEquals(PressResult.HATCHED, store.pressEgg(nowMs = now + 120_000))
        val hatched = store.nursery(now + 121_000)
        assertEquals("owl", hatched.active?.id)
        assertEquals(1, hatched.collection["owl"])
        assertEquals("owl", hatched.justHatched?.id)
    }

    @Test
    fun `a 1_1 free player's egg in progress of a pet they have becomes an egg of a missing pet, kept across days`() {
        val now = at(2026, 11, 16, 11)
        val prefs = MemoryPrefs()
        write11(
            prefs, PetStats(70f, 70f, 70f, now - 4 * PetStats.DAY_MS, now), Egg11("cat", now - 3_600_000, 0, 10 * 3_600_000L, false),
            active = "cat", seen = mapOf("cat" to 2, "frog" to 1), supporter = false,
            eggDay = EggRules.dayKey(now), eggDayCount = 1, everLaid = true, lastWarmMs = 0, instantDay = 0, hatchedAtMs = 0, hatchedId = null,
        )
        val store = PetStore(fakeContext(prefs))
        val n = store.nursery(now)
        assertEquals(1, n.eggs.size)
        val egg = n.eggs.first()
        assertTrue(egg.id !in setOf("cat", "frog"))
        assertEquals(2, n.collection["cat"])
        assertEquals("cat", n.active?.id)
        // Two presses today, the egg waits overnight, tomorrow a second egg joins it.
        store.putEggOnMatrix(egg.id, nowMs = now + 500)
        store.pressEgg(nowMs = now + 1000)
        store.pressEgg(nowMs = now + 2000)
        val tomorrow = store.nursery(now + PetStats.DAY_MS)
        assertEquals(2, tomorrow.eggs.size)
        assertEquals(egg.id, tomorrow.eggOnMatrix?.id)
        assertEquals(2, tomorrow.pressesOf(egg.id))
        assertEquals(EggStage.CRACKED_MORE, tomorrow.eggView?.stage)
    }

    @Test
    fun `a new install starts with an egg in the middle and no pet`() {
        val now = at(2026, 11, 16, 20)
        val prefs = MemoryPrefs()
        val n = PetStore(fakeContext(prefs)).nursery(now)
        assertNull(n.active)
        assertEquals(1, n.eggs.size)
        assertEquals(EggView(EggStage.WHOLE, golden = false, rarity = n.eggs.first().rarity), n.eggView)
        assertEquals(4, prefs.map["save_version"])
    }

    @Test
    fun `every species id a 1_1 save can hold is still in the catalogue`() {
        val ids11 = listOf(
            "cat", "ghost", "bot", "frog", "dino", "jelly", "bunny", "chick", "shroom",
            "cloud", "owl", "fox", "cactus", "penguin", "slime", "eye", "toast", "ufo",
        )
        for (id in ids11) {
            val now = at(2026, 11, 16, 12)
            val prefs = MemoryPrefs()
            write11(
                prefs, PetStats(50f, 50f, 50f, now - PetStats.DAY_MS, now), egg = null, active = id, seen = mapOf(id to 1),
                supporter = false, eggDay = EggRules.dayKey(now), eggDayCount = 1, everLaid = true, lastWarmMs = 0,
                instantDay = 0, hatchedAtMs = 0, hatchedId = null,
            )
            val n = PetStore(fakeContext(prefs)).nursery(now)
            assertEquals(id, n.active?.id)
            assertEquals(1, n.collection[id])
        }
    }

    @Test
    fun `a 1_1 save from before the ever_laid flag gets today's egg on a new day`() {
        val now = at(2026, 11, 16, 9)
        val prefs = MemoryPrefs()
        write11(
            prefs, PetStats(80f, 80f, 80f, now - 3 * PetStats.DAY_MS, now), egg = null, active = "cat", seen = mapOf("cat" to 1),
            supporter = false, eggDay = EggRules.dayKey(now) - 1, eggDayCount = 1, everLaid = null, lastWarmMs = 0,
            instantDay = 0, hatchedAtMs = 0, hatchedId = null,
        )
        val n = PetStore(fakeContext(prefs)).nursery(now)
        // A new game day since 1.1's last egg: today's pet arrives as an egg, one the player does not have.
        assertEquals(1, n.eggs.size)
        assertTrue(n.eggs.first().id != "cat")
        assertEquals("cat", n.active?.id)
    }

    @Test
    fun `a 1_1 save of a neglected pet loads as the same needs and is now sick, and food cures it`() {
        val now = at(2026, 11, 16, 15)
        val prefs = MemoryPrefs()
        val stats = PetStats(satiety = 3f, joy = 2f, energy = 60f, bornAtMs = now - 5 * PetStats.DAY_MS, updatedAtMs = now)
        write11(
            prefs, stats, egg = null, active = "slime", seen = mapOf("slime" to 1),
            supporter = false, eggDay = EggRules.dayKey(now), eggDayCount = 1, everLaid = true, lastWarmMs = 0,
            instantDay = 0, hatchedAtMs = 0, hatchedId = null,
        )
        val store = PetStore(fakeContext(prefs))
        val n = store.nursery(now)
        assertEquals(stats, n.stats)
        assertEquals(Mood.HUNGRY, PetRules.mood(n.stats, asleep = false))
        assertTrue(store.feed(now).ate)
        assertTrue(store.feed(now).ate)
        val fed = store.nursery(now)
        // 3 + two meals of a dot each: no longer hungry, only sad.
        assertEquals(Mood.SAD, PetRules.mood(fed.stats, asleep = false))
        assertEquals(43f, prefs.map["satiety"])
        assertEquals(2f, prefs.map["joy"])
    }

    /** Exactly what 1.2's saveStats + saveState put into the "pet" prefs (save_version 2, before growth stages). */
    private fun write12(prefs: MemoryPrefs, now: Long, active: String?, seen: Map<String, Int>, eggs: List<String> = emptyList()) {
        val m = prefs.map
        m["satiety"] = 70f
        m["joy"] = 70f
        m["energy"] = 70f
        m["born_at"] = now - 3 * PetStats.DAY_MS
        m["updated_at"] = now
        if (active != null) m["active_species"] = active
        seen.forEach { (id, count) -> m["seen_$id"] = count }
        m["save_version"] = 2
        m["supporter"] = false
        m["eggs"] = eggs.joinToString(",")
        m["egg_presses"] = ""
        m["grant_day"] = EggRules.dayKey(now)
        m["hatched_at"] = now - PetStats.DAY_MS
        if (active != null) m["hatched_id"] = active
    }

    @Test
    fun `a 1_2 save loads with every collected pet grown up as a teen, as it always looked`() {
        val now = at(2026, 11, 18, 12)
        val prefs = MemoryPrefs()
        write12(prefs, now, active = "fox", seen = mapOf("fox" to 1, "cat" to 3, "ufo" to 1), eggs = listOf("owl"))
        val n = PetStore(fakeContext(prefs)).nursery(now)
        val teen = Growth(Stage.TEEN, Stage.TEEN, xp = 0, ready = false, sinceDay = EggRules.dayKey(now))
        assertEquals(mapOf("fox" to teen, "cat" to teen, "ufo" to teen), n.growth)
        assertEquals("fox", n.active?.id)
        assertSame(SpeciesCatalog.get("fox"), n.activeForm)
        assertEquals(teen, n.activeGrowth)
        assertFalse(n.ready)
        assertEquals("owl", n.eggs.firstOrNull()?.id)
        assertEquals(4, prefs.map["save_version"])
        assertEquals(listOf("fox", "cat", "ufo").map { "$it:TEEN:TEEN:0:0:${EggRules.dayKey(now)}:0" }.toSet(), (prefs.map["growth"] as String).split(',').toSet())
        // Loaded again days later: the same teens, the day they became teens kept.
        val later = PetStore(fakeContext(prefs)).nursery(now + 3 * PetStats.DAY_MS)
        assertEquals(teen, later.growth["cat"])
    }

    @Test
    fun `a 1_1 save gets teens too`() {
        val now = at(2026, 11, 18, 12)
        val prefs = MemoryPrefs()
        write11(
            prefs, PetStats(50f, 50f, 50f, now - PetStats.DAY_MS, now), egg = null, active = "eye", seen = mapOf("eye" to 1),
            supporter = false, eggDay = EggRules.dayKey(now), eggDayCount = 1, everLaid = true, lastWarmMs = 0,
            instantDay = 0, hatchedAtMs = 0, hatchedId = null,
        )
        val n = PetStore(fakeContext(prefs)).nursery(now)
        assertEquals(Stage.TEEN, n.activeGrowth?.reached)
        assertSame(SpeciesCatalog.get("eye"), n.activeForm)
    }

    @Test
    fun `growth is saved - a hatched baby grows up by shakes the next day and keeps the form picked`() {
        val day1 = at(2026, 11, 18, 10)
        val prefs = MemoryPrefs()
        val store = PetStore(fakeContext(prefs))
        store.nursery(day1)
        repeat(EggRules.PRESSES_TO_HATCH) { store.pressEgg(nowMs = day1 + 1000) }
        val hatched = PetStore(fakeContext(prefs)).nursery(day1 + 2000)
        val kind = hatched.active!!.id
        assertEquals("$kind.baby", hatched.activeForm?.id)
        assertEquals("the first hero of all, so with its head start", GrowthRules.hatched(EggRules.dayKey(day1), first = true), hatched.activeGrowth)
        assertEquals(GrowResult.NONE, store.growShake(day1 + 3000))

        val day2 = day1 + PetStats.DAY_MS
        // Its threshold of experience makes it ready; the debug button is the shortest way to it here.
        store.debugXp(XpRules.BABY_TO_TEEN, day2)
        assertTrue(PetStore(fakeContext(prefs)).nursery(day2).ready)
        assertEquals(GrowResult.SHOOK, store.growShake(day2))
        assertEquals(GrowResult.SHOOK, PetStore(fakeContext(prefs)).growShake(day2 + 1))
        assertEquals(GrowResult.EVOLVED, store.growShake(day2 + 2))
        val teen = PetStore(fakeContext(prefs)).nursery(day2 + 3)
        assertEquals(kind, teen.activeForm?.id)
        assertEquals(kind, teen.justEvolved?.id)
        assertEquals(Stage.TEEN, teen.activeGrowth?.reached)

        store.setShown(kind, Stage.BABY, day2 + 4)
        assertEquals("$kind.baby", PetStore(fakeContext(prefs)).nursery(day2 + 5).activeForm?.id)
        store.setShown(kind, Stage.ADULT, day2 + 6)
        assertEquals("not reached yet", "$kind.baby", PetStore(fakeContext(prefs)).nursery(day2 + 7).activeForm?.id)
    }

    @Test
    fun `meals bring no experience, minutes together on the screen do`() {
        val now = at(2026, 11, 18, 12)
        val prefs = MemoryPrefs()
        write12(prefs, now, active = "cat", seen = mapOf("cat" to 1))
        prefs.map["satiety"] = 60f
        val store = PetStore(fakeContext(prefs))
        assertTrue(store.feed(now).ate)
        assertEquals(0, store.nursery(now).activeGrowth?.xp)
        // Five minutes on the screen, the toy reporting every two seconds: the whole minutes since the first report.
        var t = now
        while (t < now + 5 * 60_000L) {
            t += 2000L
            store.together(seen = true, nowMs = t)
        }
        assertEquals(4, store.nursery(t).activeGrowth?.xp)
    }

    @Test
    fun `a sick pet gains nothing, and 1_2's care keys are gone from the save`() {
        val now = at(2026, 11, 18, 12)
        val prefs = MemoryPrefs()
        write12(prefs, now, active = "cat", seen = mapOf("cat" to 1))
        // 1.2 counted care days; the keys are dropped on the first save (see PetStore.OLD_KEYS).
        prefs.map["care_day"] = EggRules.dayKey(now)
        prefs.map["care_fed"] = true
        prefs.map["care_sick"] = true
        prefs.map["satiety"] = 2f
        prefs.map["joy"] = 2f
        val store = PetStore(fakeContext(prefs))
        assertEquals(Mood.HUNGRY, PetRules.mood(store.nursery(now).stats, asleep = false))
        assertEquals("a hungry pet earns nothing from time", 0, store.gain(XpRules.PER_MINUTE, now))
        for (key in listOf("care_day", "care_fed", "care_sick")) assertFalse(key, prefs.map.containsKey(key))
        // Two meals of a dot each and it is no longer hungry: then time counts again.
        store.feed(now + 1000)
        store.feed(now + 2000)
        assertEquals(XpRules.PER_MINUTE, store.gain(XpRules.PER_MINUTE, now + 3000))
    }

    @Test
    fun `a save from before experience turns its care days into experience`() {
        val now = at(2026, 11, 18, 12)
        val prefs = MemoryPrefs()
        write12(prefs, now, active = "cat", seen = mapOf("cat" to 1))
        // Version 3: growth rows held care days in the fourth field. Two of the three days a teen needed.
        prefs.map["save_version"] = 3
        prefs.map["growth"] = "cat:TEEN:TEEN:2:0:${EggRules.dayKey(now)}:0"
        val n = PetStore(fakeContext(prefs)).nursery(now)
        assertEquals(XpRules.fromCareDays(Stage.TEEN, 2), n.activeGrowth?.xp)
        assertEquals(4, prefs.map["save_version"])
        // Read once more from the rewritten save: the experience stays as it is, not converted twice.
        assertEquals(XpRules.fromCareDays(Stage.TEEN, 2), PetStore(fakeContext(prefs)).nursery(now + 1000).activeGrowth?.xp)
    }
}
