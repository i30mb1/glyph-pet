package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 1.1's timed egg into 1.2's egg game: [SaveMigration.from11]. */
class SaveMigrationTest {

    private val all = SpeciesCatalog.eggable.map { it.id }
    private val today = 20261116

    private fun old(
        egg: String? = null,
        active: String? = "cat",
        seen: Map<String, Int> = mapOf("cat" to 1),
        supporter: Boolean = false,
    ) = Nursery11(eggSpecies = egg, active = active, seen = seen, supporter = supporter, eggDay = today, hatchedAtMs = 5, hatchedId = active)

    @Test
    fun `a new install starts empty and gets its first egg from the daily grant`() {
        val s = SaveMigration.from11(null, Random(1))
        assertEquals(NurseryState(), s)
        assertEquals(1, NurseryRules.available(NurseryRules.advance(s, 0, Random(1))).size)
    }

    @Test
    fun `a 1_1 player with an egg in progress keeps it as an egg to open, from scratch`() {
        val s = SaveMigration.from11(old(egg = "owl", seen = mapOf("cat" to 2, "fox" to 1)), Random(1))
        assertEquals(listOf("owl"), s.eggs)
        assertTrue(s.presses.isEmpty())
        assertEquals("cat", s.active)
        assertEquals(mapOf("cat" to 2, "fox" to 1), s.seen)
        assertEquals(today, s.grantDay)
        assertEquals(5L, s.hatchedAtMs)
        assertEquals("cat", s.hatchedId)
    }

    @Test
    fun `a 1_1 player waiting for the first egg finds it to open`() {
        val s = SaveMigration.from11(old(egg = "ghost", active = null, seen = emptyMap()), Random(1))
        assertEquals(listOf("ghost"), s.eggs)
        assertNull(s.active)
        assertEquals("ghost", NurseryRules.onMatrix(s))
    }

    @Test
    fun `an egg holding a pet the player already has becomes a missing one`() {
        for (seed in 0 until 50) {
            val s = SaveMigration.from11(old(egg = "cat", seen = mapOf("cat" to 1, "fox" to 3)), Random(seed))
            assertEquals(1, s.eggs.size)
            assertTrue(s.eggs.single() !in setOf("cat", "fox"))
        }
    }

    @Test
    fun `a 1_1 supporter keeps the unlock and has every missing pet as an egg, the waiting one first`() {
        val s = SaveMigration.from11(old(egg = "eye", seen = mapOf("cat" to 1, "bot" to 2), supporter = true), Random(1))
        assertTrue(s.supporter)
        val eggs = NurseryRules.available(s)
        assertEquals("eye", eggs.first())
        // The anomalies too: a supporter has every hero.
        val every = SpeciesCatalog.all.map { it.id }
        assertEquals(every.toSet() - setOf("cat", "bot"), eggs.toSet())
        assertEquals(every.size - 2, eggs.size)
    }

    @Test
    fun `a 1_1 player who has every hero loses the waiting egg and gets no more`() {
        val every = SpeciesCatalog.all.map { it.id }.associateWith { 1 } + ("cat" to 4)
        val s = SaveMigration.from11(old(egg = "ufo", seen = every, supporter = true), Random(1))
        assertTrue(s.eggs.isEmpty())
        assertEquals(every, s.seen)
        assertTrue(NurseryRules.available(NurseryRules.advance(s, System.currentTimeMillis(), Random(1))).isEmpty())
    }

    @Test
    fun `an unknown species in a save is never offered as an egg`() {
        val s = SaveMigration.from11(old(egg = "griffin"), Random(1))
        assertEquals(1, s.eggs.size)
        assertTrue(s.eggs.single() in all)
    }
}
