package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The first minutes of a new owner: a newborn that asks to be fed straight away, a pet that calls when a need runs
 * out, and a first hero close enough to growing that its owner sees an evolution the same evening.
 */
class FirstMinutesTest {

    private val newborn = PetStats.newborn(0)

    @Test
    fun `a newborn shows no food and no joy dots, but is neither ill nor asleep`() {
        assertEquals(0, NeedDots.of(newborn.satiety))
        assertEquals(0, NeedDots.of(newborn.joy))
        assertEquals(Mood.HUNGRY, PetRules.mood(newborn, asleep = false))
        assertFalse(PetRules.isAsleep(newborn))
    }

    @Test
    fun `feeding the newborn fills it a dot`() {
        val (fed, ate) = PetRules.feed(newborn)
        assertTrue(ate)
        assertEquals(NeedDots.of(newborn.satiety) + 1, NeedDots.of(fed.satiety))
    }

    @Test
    fun `a newborn asks for its owner at once, and stops once it is fed and played with`() {
        assertTrue(CallRules.wants(newborn, asleep = false))
        assertFalse("a sleeping pet never calls", CallRules.wants(newborn, asleep = true))
        // Fed, then a mini-game: a dot of joy.
        val cared = PetRules.play(PetRules.feed(newborn).first)
        assertFalse(CallRules.wants(cared, asleep = false))
    }

    @Test
    fun `a full pet calls after two or three hours away, an invitation to the next visit`() {
        val full = newborn.copy(satiety = 100f, joy = 100f, energy = 100f)
        var hours = 0
        var s = full
        while (!CallRules.wants(s, asleep = false) && hours < 48) {
            hours++
            s = PetRules.advance(s, hours * 60L * 60_000L)
        }
        assertTrue("called after $hours h, expected two or three, about when a visit is due anyway", hours in 2..3)
    }

    @Test
    fun `the first hero of all hatches close to growing, the next one starts from nothing`() {
        val first = GrowthRules.hatched(today = 0, first = true)
        assertEquals(XpRules.FIRST_HEAD_START, first.xp)
        assertFalse("it still has to be earned", first.ready)
        assertEquals(0, GrowthRules.hatched(today = 0).xp)

        // Five minutes together carry it the rest of the way.
        val left = XpRules.BABY_TO_TEEN - first.xp
        assertTrue("$left minutes left after the head start", left in 3..5)
    }

    @Test
    fun `only the very first egg of all gets the head start`() {
        val fresh = NurseryState(eggs = listOf("cat"))
        val hatched = NurseryRules.press(fresh, nowMs = 0)!!.let { s ->
            (1 until EggRules.PRESSES_TO_HATCH).fold(s) { acc, _ -> NurseryRules.press(acc, 0) ?: acc }
        }
        assertEquals(XpRules.FIRST_HEAD_START, hatched.growth.getValue("cat").xp)

        // The second hero comes to an owner who already has one: no head start.
        val next = NurseryRules.advance(hatched.copy(eggs = listOf("frog")), nowMs = 0, random = Random(1))
        val both = (1..EggRules.PRESSES_TO_HATCH).fold(NurseryRules.putOnMatrix(next, "frog")) { acc, _ ->
            NurseryRules.press(acc, 0) ?: acc
        }
        assertEquals(0, both.growth.getValue("frog").xp)
    }
}
