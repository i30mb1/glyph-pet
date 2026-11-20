package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRulesTest {

    private val hour = 60 * 60 * 1000L
    @Test
    fun `awake pet gets hungry and bored over time`() {
        // Sleep runs on its own, much faster clock (a dot every ten minutes, see the cycle test below): over two hours
        // the pet has slept more than once, so only food and joy fall the whole way.
        val start = PetStats(80f, 80f, 80f, 0, 0)
        val later = PetRules.advance(start, 2 * hour)
        // Food and joy only fall; asleep they fall slower, so two hours cost a little less than two hours awake.
        assertTrue("food fell: ${later.satiety}", later.satiety in 55f..75f)
        assertTrue("joy fell: ${later.joy}", later.joy in 58f..78f)
        assertEquals(2 * hour, later.updatedAtMs)
    }

    @Test
    fun `the pet spends a dot of sleep every ten minutes and fills one a minute`() {
        val perDot = 100f / NeedDots.MAX
        assertEquals("a dot spent every 10 min", 10f, perDot / PetRules.ENERGY_AWAKE_PER_HOUR * 60f, 0.01f)
        assertEquals("a dot filled every minute", 1f, perDot / PetRules.ENERGY_SLEEP_PER_HOUR * 60f, 0.01f)
        assertEquals("a dot filled every 10 s on the charger", 10f, perDot / PetRules.ENERGY_CHARGE_PER_HOUR * 3600f, 0.01f)
    }

    @Test
    fun `a worn-out pet sleeps itself nearly full again, a few minutes at a time`() {
        // Below NAP_ENERGY it is asleep: its energy climbs to NAP_WAKE_ENERGY and it wakes there.
        val start = PetStats(80f, 80f, 5f, 0, 0)
        val minutes = 10 * 60_000L
        val slept = PetRules.advance(start, minutes)
        assertEquals("nearly full after ten minutes", PetRules.NAP_WAKE_ENERGY, slept.energy, 0.01f)
        assertFalse("and awake again", PetRules.isAsleep(slept))
        // Asleep its other needs fall slower than awake.
        val awake = PetRules.advance(start.copy(energy = 100f), minutes)
        assertTrue("joy is spared while it sleeps", slept.joy > awake.joy)
    }

    @Test
    fun `needs never leave 0 to 100 even after weeks away`() {
        val start = PetStats(50f, 50f, 50f, 0, 0)
        val later = PetRules.advance(start, 60 * PetStats.DAY_MS)
        assertTrue(later.satiety in 0f..100f && later.joy in 0f..100f && later.energy in 0f..100f)
        assertEquals(0f, later.satiety, 0.01f)
    }

    @Test
    fun `time going backwards changes nothing`() {
        val start = PetStats(50f, 50f, 50f, 0, 10 * hour)
        assertEquals(start, PetRules.advance(start, 5 * hour))
    }

    @Test
    fun `a full pet refuses food`() {
        val (_, ate) = PetRules.feed(PetStats(95f, 50f, 50f, 0, 0))
        assertFalse(ate)
        val (fed, ateHungry) = PetRules.feed(PetStats(20f, 50f, 50f, 0, 0))
        assertTrue(ateHungry)
        assertEquals(55f, fed.satiety, 0.01f)
    }

    @Test
    fun `mood follows the most urgent need`() {
        assertEquals(Mood.SLEEPING, PetRules.mood(PetStats(10f, 10f, 10f, 0, 0), asleep = true))
        assertEquals(Mood.HUNGRY, PetRules.mood(PetStats(10f, 90f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.TIRED, PetRules.mood(PetStats(60f, 90f, 10f, 0, 0), asleep = false))
        assertEquals(Mood.SAD, PetRules.mood(PetStats(60f, 10f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.HAPPY, PetRules.mood(PetStats(60f, 90f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.CONTENT, PetRules.mood(PetStats(60f, 50f, 90f, 0, 0), asleep = false))
    }

    @Test
    fun `a pet with almost no food and no joy is sick, right after sleep in order`() {
        assertEquals(Mood.SICK, PetRules.mood(PetStats(4f, 4f, 90f, 0, 0), asleep = false))
        assertEquals("sick beats tired", Mood.SICK, PetRules.mood(PetStats(4f, 4f, 15f, 0, 0), asleep = false))
        assertEquals("sleep beats sick", Mood.SLEEPING, PetRules.mood(PetStats(4f, 4f, 5f, 0, 0), asleep = true))
        assertEquals("both thresholds", Mood.HUNGRY, PetRules.mood(PetStats(4f, PetRules.SICK_JOY, 90f, 0, 0), asleep = false))
        assertEquals(Mood.SAD, PetRules.mood(PetStats(PetRules.SICK_SATIETY + 30f, 4f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.HUNGRY, PetRules.mood(PetStats(PetRules.SICK_SATIETY, 4f, 90f, 0, 0), asleep = false))
    }

    @Test
    fun `half a day without care makes the pet sick and one meal cures it`() {
        val start = PetStats(60f, 50f, 90f, 0, 0)
        val later = PetRules.advance(start, 9 * hour)
        assertEquals(Mood.SICK, PetRules.mood(later, asleep = false))
        val (fed, ate) = PetRules.feed(later)
        assertTrue(ate)
        assertFalse(PetRules.mood(fed, asleep = false) == Mood.SICK)
    }

    @Test
    fun `only being worn out puts the pet to sleep, at any hour of the night or the day`() {
        val rested = PetStats(50f, 50f, 50f, 0, 0)
        assertFalse(rested.napping)
        assertFalse("a rested pet is awake whatever the clock says", PetRules.isAsleep(rested))
        assertTrue(PetRules.isAsleep(rested.copy(energy = PetRules.NAP_ENERGY - 1f)))
        // Once asleep it sleeps on until it is nearly full, so it does not doze off again right after waking.
        val dozing = rested.copy(energy = PetRules.NAP_WAKE_ENERGY - 1f, napping = true)
        assertTrue(PetRules.isAsleep(dozing))
        assertFalse(PetRules.isAsleep(dozing.copy(energy = PetRules.NAP_WAKE_ENERGY)))
    }

    @Test
    fun `the charger fills energy while the pet stays awake`() {
        val start = PetStats(80f, 80f, 50f, 0, 0)
        val tenMinutes = hour / 6
        val charged = PetRules.advance(start, tenMinutes, charging = true)
        val unplugged = PetRules.advance(start, tenMinutes, charging = false)
        assertTrue(charged.energy > start.energy)
        assertTrue(unplugged.energy < start.energy)
        // Awake either way: hunger grows at the same pace.
        assertEquals(unplugged.satiety, charged.satiety, 0.001f)
    }
}
