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
    fun `night, charging or exhaustion put the pet to sleep`() {
        val rested = PetStats(50f, 50f, 50f, 0, 0)
        assertTrue(PetRules.isAsleep(rested, 23, charging = false))
        assertTrue(PetRules.isAsleep(rested, 12, charging = true))
        assertTrue(PetRules.isAsleep(rested.copy(energy = 5f), 12, charging = false))
        assertFalse(PetRules.isAsleep(rested, 12, charging = false))
    }
}
