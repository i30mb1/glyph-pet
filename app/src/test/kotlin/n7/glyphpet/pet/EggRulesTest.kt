package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EggRulesTest {

    private val hour = 60 * 60 * 1000L
    private val egg = Egg("cat", laidAtMs = 0, warmMs = 0, hatchMs = EggRules.HATCH_MS, golden = false)

    @Test
    fun `an egg cracks around midday and hatches by the evening`() {
        assertEquals(EggStage.WHOLE, EggRules.stage(egg, 2 * hour))
        assertEquals(EggStage.CRACKED, EggRules.stage(egg, 6 * hour))
        assertEquals(EggStage.HATCHING, EggRules.stage(egg, 9 * hour))
        assertFalse(EggRules.isReady(egg, 9 * hour))
        assertTrue(EggRules.isReady(egg, 10 * hour))
    }

    @Test
    fun `warming brings the hatch closer`() {
        val warmed = egg.copy(warmMs = 4 * EggRules.WARM_MS)
        assertEquals(EggRules.HATCH_MS - 3 * hour, EggRules.remainingMs(warmed, 0))
    }

    @Test
    fun `supporters get two eggs a day`() {
        assertEquals(1, EggRules.eggsPerDay(supporter = false))
        assertEquals(2, EggRules.eggsPerDay(supporter = true))
    }

    @Test
    fun `rarity odds are close to 70, 25 and 5 percent`() {
        val random = Random(42)
        val counts = Rarity.entries.associateWith { 0 }.toMutableMap()
        repeat(20_000) {
            val rarity = SpeciesCatalog.roll(random).rarity
            counts[rarity] = counts.getValue(rarity) + 1
        }
        val total = counts.values.sum().toFloat()
        assertEquals(0.70f, counts.getValue(Rarity.COMMON) / total, 0.02f)
        assertEquals(0.25f, counts.getValue(Rarity.RARE) / total, 0.02f)
        assertEquals(0.05f, counts.getValue(Rarity.LEGENDARY) / total, 0.01f)
    }

    @Test
    fun `the catalog has 18 pets in three tiers`() {
        assertEquals(18, SpeciesCatalog.all.size)
        assertEquals(10, SpeciesCatalog.all.count { it.rarity == Rarity.COMMON })
        assertEquals(5, SpeciesCatalog.all.count { it.rarity == Rarity.RARE })
        assertEquals(3, SpeciesCatalog.all.count { it.rarity == Rarity.LEGENDARY })
    }
}
