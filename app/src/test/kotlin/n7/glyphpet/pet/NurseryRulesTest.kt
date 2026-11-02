package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/** Every path of the daily egg game, with a fake clock. Days are local dates in March 2026. */
class NurseryRulesTest {

    private val random = Random(7)
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(day: Int, h: Int, m: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.MARCH, day, h, m, 0)
    }.timeInMillis

    private fun NurseryState.at(ms: Long) = NurseryRules.advance(this, ms, random)

    /** A player whose first pet has hatched on March 1st. */
    private fun withFirstPet(): NurseryState {
        val laid = NurseryState().at(at(1, 10))
        return laid.at(at(1, 10, 5))
    }

    // --- First launch -------------------------------------------------------------------------

    @Test
    fun `first launch lays an egg that hatches in minutes`() {
        val s = NurseryState().at(at(1, 10))
        val egg = assertNotNull(s.egg).let { s.egg!! }
        assertEquals(EggRules.FIRST_HATCH_MS, egg.hatchMs)
        assertNull(s.active)
        assertEquals(0, NurseryRules.eggsLeftToday(s, at(1, 10)))
    }

    @Test
    fun `the first egg hatches after three minutes and the pet goes on the matrix`() {
        val laid = NurseryState().at(at(1, 10))
        val species = laid.egg!!.speciesId
        assertNotNull(laid.at(at(1, 10, 2)).egg)
        val hatched = laid.at(at(1, 10, 3))
        assertNull(hatched.egg)
        assertEquals(species, hatched.active)
        assertEquals(1, hatched.seen[species])
        assertEquals(species, NurseryRules.justHatched(hatched, at(1, 10, 3))?.id)
        assertNull(NurseryRules.justHatched(hatched, at(1, 10, 4)))
    }

    @Test
    fun `free players get no second egg the same day`() {
        val s = withFirstPet()
        assertNull(s.at(at(1, 23, 59)).egg)
        assertNull(s.at(at(2, 5, 59)).egg)
    }

    // --- Days ------------------------------------------------------------------------------------

    @Test
    fun `the next egg arrives at 6 in the morning and takes the day`() {
        val s = withFirstPet()
        assertNull(s.at(at(2, 5, 59)).egg)
        val morning = s.at(at(2, 6))
        val egg = morning.egg!!
        assertEquals(EggRules.HATCH_MS, egg.hatchMs)
        assertEquals(EggStage.WHOLE, EggRules.stage(egg, at(2, 8)))
        assertEquals(EggStage.CRACKED, EggRules.stage(egg, at(2, 11)))
        assertEquals(EggStage.HATCHING, EggRules.stage(egg, at(2, 15)))
        assertNotNull(morning.at(at(2, 15, 59)).egg)
        val evening = morning.at(at(2, 16))
        assertNull(evening.egg)
        assertEquals(egg.speciesId, evening.active)
    }

    @Test
    fun `an egg opened late is not lost, today's egg comes right after it hatches`() {
        val late = withFirstPet().at(at(2, 22))
        assertNotNull(late.egg)
        val nextMorning = late.at(at(3, 7))
        assertEquals("one egg at a time", late.egg, nextMorning.egg)
        val hatched = nextMorning.at(at(3, 8))
        assertNotNull("today's egg follows at once", hatched.egg)
        assertTrue(hatched.egg!!.laidAtMs == at(3, 8))
        assertEquals(0, NurseryRules.eggsLeftToday(hatched, at(3, 8)))
    }

    @Test
    fun `days away do not stack eggs up`() {
        val back = withFirstPet().at(at(9, 12))
        assertNotNull(back.egg)
        assertEquals(1, back.eggDayCount)
        assertEquals(0, NurseryRules.eggsLeftToday(back, at(9, 12)))
    }

    // --- Warming ---------------------------------------------------------------------------------

    @Test
    fun `warming brings the hatch 45 minutes closer, at most every 20 seconds`() {
        val s = withFirstPet().at(at(2, 6))
        val warmed = NurseryRules.warm(s, at(2, 7))!!
        assertEquals(EggRules.WARM_MS, warmed.egg!!.warmMs)
        assertNull("cooldown", NurseryRules.warm(warmed, at(2, 7) + 10_000))
        assertNotNull(NurseryRules.warm(warmed, at(2, 7) + 20_000))
    }

    @Test
    fun `fourteen warm-ups hatch a fresh egg`() {
        var s = withFirstPet().at(at(2, 6))
        var t = at(2, 6)
        repeat(14) { t += 30_000; s = NurseryRules.warm(s, t)!! }
        assertNull(s.at(t).egg)
        assertNotNull(s.at(t).active)
    }

    @Test
    fun `there is nothing to warm without an egg`() {
        assertNull(NurseryRules.warm(withFirstPet(), at(1, 12)))
    }

    // --- Supporter -------------------------------------------------------------------------------

    @Test
    fun `supporters get a second golden egg the same day, not a third`() {
        val s = withFirstPet().copy(supporter = true)
        val first = s.at(at(2, 6))
        assertTrue(first.egg!!.golden)
        val second = first.at(at(2, 16))
        assertNotNull("second egg", second.egg)
        assertTrue(second.egg!!.golden)
        val third = second.at(at(3, 2))
        assertNull("game day still the 2nd until 6:00: no third egg", third.egg)
        assertNotNull(third.at(at(3, 6)).egg)
    }

    @Test
    fun `buying supporter mid-day brings the second egg right away`() {
        val free = withFirstPet()
        assertNull(free.at(at(1, 12)).egg)
        assertNotNull(free.copy(supporter = true).at(at(1, 12)).egg)
    }

    @Test
    fun `instant hatch works once a game day, only for supporters`() {
        val free = withFirstPet().at(at(2, 6))
        assertNull(NurseryRules.hatchNow(free, at(2, 7)))

        val sup = withFirstPet().copy(supporter = true).at(at(2, 6))
        assertTrue(NurseryRules.instantHatchAvailable(sup, at(2, 7)))
        val instant = NurseryRules.hatchNow(sup, at(2, 7))!!
        val hatched = instant.at(at(2, 7))
        assertNotNull(hatched.active)
        val secondEgg = hatched.egg!!
        assertFalse(NurseryRules.instantHatchAvailable(hatched, at(2, 8)))
        assertNull(NurseryRules.hatchNow(hatched, at(2, 8)))
        assertTrue("available again the next game day", NurseryRules.instantHatchAvailable(hatched, at(3, 6)))
        assertEquals(secondEgg, hatched.egg)
    }

    @Test
    fun `losing supporter (refund) goes back to one egg a day`() {
        val s = withFirstPet().copy(supporter = true).at(at(2, 6)).at(at(2, 16)).copy(supporter = false)
        assertEquals(0, NurseryRules.eggsLeftToday(s, at(2, 17)))
    }

    // --- Collection ------------------------------------------------------------------------------

    @Test
    fun `a duplicate counts twice and comes back on the matrix`() {
        val base = withFirstPet()
        val first = base.active!!
        val other = SpeciesCatalog.all.first { it.id != first }.id
        val dup = base.copy(active = other, seen = base.seen + (other to 1), egg = Egg(first, at(2, 6), 0, EggRules.HATCH_MS, false), eggDay = EggRules.dayKey(at(2, 6)), eggDayCount = 1)
        val hatched = dup.at(at(2, 16))
        assertEquals(2, hatched.seen[first])
        assertEquals(first, hatched.active)
    }

    @Test
    fun `only collected pets can be put on the matrix`() {
        val s = withFirstPet()
        val stranger = SpeciesCatalog.all.first { (s.seen[it.id] ?: 0) == 0 }.id
        assertEquals(s, NurseryRules.setActive(s, stranger))
        val friend = s.copy(seen = s.seen + (stranger to 1))
        assertEquals(stranger, NurseryRules.setActive(friend, stranger).active)
    }

    @Test
    fun `every hatched species comes from the catalog`() {
        var s = NurseryState()
        for (day in 1..28) s = s.at(at(day, 6)).at(at(day, 20))
        assertTrue(s.seen.keys.all { SpeciesCatalog.get(it) != null })
        assertEquals(28, s.seen.values.sum())
    }

    // --- Clock -----------------------------------------------------------------------------------

    @Test
    fun `turning the clock back does not hatch or break the egg`() {
        val s = withFirstPet().at(at(2, 10))
        val egg = s.egg!!
        assertEquals(0f, EggRules.progress(egg, at(2, 9)), 0f)
        assertEquals(egg, s.at(at(2, 9)).egg)
    }

    // --- The Glyph button ------------------------------------------------------------------------

    @Test
    fun `the Glyph button does the most useful thing`() {
        assertEquals(ButtonAction.WARM_EGG, ButtonRules.decide(hasPet = false, mood = Mood.CONTENT, eggWaiting = true, satiety = 80f))
        assertEquals(ButtonAction.NOTHING, ButtonRules.decide(hasPet = false, mood = Mood.CONTENT, eggWaiting = false, satiety = 80f))
        assertEquals(ButtonAction.PET, ButtonRules.decide(hasPet = true, mood = Mood.SLEEPING, eggWaiting = true, satiety = 10f))
        assertEquals(ButtonAction.FEED, ButtonRules.decide(hasPet = true, mood = Mood.HUNGRY, eggWaiting = true, satiety = 10f))
        assertEquals(ButtonAction.WARM_EGG, ButtonRules.decide(hasPet = true, mood = Mood.CONTENT, eggWaiting = true, satiety = 50f))
        assertEquals(ButtonAction.FEED, ButtonRules.decide(hasPet = true, mood = Mood.CONTENT, eggWaiting = false, satiety = 50f))
        assertEquals(ButtonAction.PET, ButtonRules.decide(hasPet = true, mood = Mood.HAPPY, eggWaiting = false, satiety = 90f))
    }
}
