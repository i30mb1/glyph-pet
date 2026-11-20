package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRulesTest {

    private val hour = 60 * 60 * 1000L
    private val noon: (Long) -> Int = { 12 }
    private val midnight: (Long) -> Int = { 0 }

    @Test
    fun `awake pet gets hungry, bored and tired over time`() {
        val start = PetStats(80f, 80f, 80f, 0, 0)
        val later = PetRules.advance(start, 2 * hour, hourAt = noon)
        assertEquals(60f, later.satiety, 0.01f)
        assertEquals(64f, later.joy, 0.01f)
        assertEquals(68f, later.energy, 0.01f)
        assertEquals(2 * hour, later.updatedAtMs)
    }

    @Test
    fun `sleeping pet restores energy and loses needs slower`() {
        val start = PetStats(80f, 80f, 20f, 0, 0)
        val later = PetRules.advance(start, 3 * hour, hourAt = midnight)
        assertEquals(68f, later.energy, 0.01f)
        assertEquals(65f, later.satiety, 0.01f)
    }

    @Test
    fun `needs never leave 0 to 100 even after weeks away`() {
        val start = PetStats(50f, 50f, 50f, 0, 0)
        val later = PetRules.advance(start, 60 * PetStats.DAY_MS, hourAt = noon)
        assertTrue(later.satiety in 0f..100f && later.joy in 0f..100f && later.energy in 0f..100f)
        assertEquals(0f, later.satiety, 0.01f)
    }

    @Test
    fun `time going backwards changes nothing`() {
        val start = PetStats(50f, 50f, 50f, 0, 10 * hour)
        assertEquals(start, PetRules.advance(start, 5 * hour, hourAt = noon))
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
        val later = PetRules.advance(start, 9 * hour, hourAt = noon)
        assertEquals(Mood.SICK, PetRules.mood(later, asleep = false))
        val (fed, ate) = PetRules.feed(later)
        assertTrue(ate)
        assertFalse(PetRules.mood(fed, asleep = false) == Mood.SICK)
    }

    @Test
    fun `night or exhaustion put the pet to sleep`() {
        val rested = PetStats(50f, 50f, 50f, 0, 0)
        // Night is from midnight to 6 (PetRules.isNight): 23:00 is still evening.
        assertTrue(PetRules.isAsleep(rested, 1))
        assertTrue(PetRules.isAsleep(rested, 5))
        assertFalse(PetRules.isAsleep(rested, 23))
        assertFalse(PetRules.isAsleep(rested, PetRules.WAKE_HOUR))
        assertTrue(PetRules.isAsleep(rested.copy(energy = 5f), 12))
        assertFalse(PetRules.isAsleep(rested, 12))
    }

    @Test
    fun `the charger fills energy while the pet stays awake`() {
        val start = PetStats(80f, 80f, 50f, 0, 0)
        val tenMinutes = hour / 6
        val charged = PetRules.advance(start, tenMinutes, charging = true, hourAt = noon)
        val unplugged = PetRules.advance(start, tenMinutes, charging = false, hourAt = noon)
        assertTrue(charged.energy > start.energy)
        assertTrue(unplugged.energy < start.energy)
        // Awake either way: hunger grows at the same pace.
        assertEquals(unplugged.satiety, charged.satiety, 0.001f)
    }
}
