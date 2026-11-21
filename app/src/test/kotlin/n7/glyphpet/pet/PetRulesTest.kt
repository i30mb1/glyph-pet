package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The needs, built for short visits (see [PetRules]): away they drift slowly and the pet rests, on the matrix they move
 * in minutes, and one action is one dot.
 */
class PetRulesTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private fun dots(v: Float) = NeedDots.of(v)

    /** [s] on the matrix from its [PetStats.updatedAtMs] for [ms], the toy reporting every two seconds. */
    private fun watched(s: PetStats, ms: Long, charging: Boolean = false): PetStats {
        var t = s.updatedAtMs
        var x = s.copy(seenAtMs = maxOf(t, 1L)) // 0 would mean "not seen"
        while (t < s.updatedAtMs + ms) {
            t = minOf(t + 2000L, s.updatedAtMs + ms)
            x = PetRules.advance(x, t, charging).copy(seenAtMs = t)
        }
        return x
    }

    @Test
    fun `away, a dot of food goes every half hour and a dot of joy every forty minutes, and it rests`() {
        assertEquals(30f, PetRules.DOT / PetRules.AWAY_SATIETY_PER_HOUR * 60f, 0.01f)
        assertEquals(40f, PetRules.DOT / PetRules.AWAY_JOY_PER_HOUR * 60f, 0.01f)
        val start = PetStats(90f, 90f, 20f, 0, 0)
        val later = PetRules.advance(start, hour)
        assertEquals("two dots of food in an hour", 50f, later.satiety, 0.01f)
        assertEquals("a dot and a half of joy", 60f, later.joy, 0.01f)
        assertEquals("rested", 100f, later.energy, 0.01f)
    }

    @Test
    fun `whoever comes back after two hours finds it rested, but hungry or bored`() {
        val full = PetStats(90f, 90f, 30f, 0, 0)
        val back = PetRules.advance(full, 2 * hour)
        assertTrue("food ${back.satiety}", dots(back.satiety) <= 2)
        assertTrue("joy ${back.joy}", dots(back.joy) <= 3)
        assertEquals(NeedDots.MAX, dots(back.energy))
    }

    @Test
    fun `on the matrix, every need loses a dot in four minutes`() {
        val start = PetStats(90f, 90f, 90f, 0, 0)
        val later = watched(start, 4 * minute)
        assertEquals(70f, later.satiety, 0.5f)
        assertEquals(70f, later.joy, 0.5f)
        assertEquals(70f, later.energy, 0.5f)
    }

    @Test
    fun `the matrix clock stops when the toy stops saying it is seen`() {
        val start = PetStats(90f, 90f, 90f, 0, 0, seenAtMs = 0)
        // Last seen at 0, then nothing for an hour: three minutes count on the matrix, the rest away.
        val later = PetRules.advance(start.copy(seenAtMs = 1), hour)
        val away = PetRules.advance(start, hour)
        assertTrue("a little more food spent: ${later.satiety} vs ${away.satiety}", later.satiety < away.satiety)
        assertTrue(away.satiety - later.satiety < PetRules.DOT)
    }

    @Test
    fun `one meal is one dot, and a full pet refuses`() {
        val (_, ate) = PetRules.feed(PetStats(95f, 50f, 50f, 0, 0))
        assertFalse(ate)
        for (s in 0..4) {
            val before = PetStats(s * PetRules.DOT + 8f, 50f, 50f, 0, 0)
            val (fed, ok) = PetRules.feed(before)
            assertTrue(ok)
            assertEquals("a dot more", dots(before.satiety) + 1, dots(fed.satiety))
        }
    }

    @Test
    fun `a game is a dot of joy for a dot of energy and half a dot of food, and a tired pet does not play`() {
        val s = PetStats(60f, 40f, 60f, 0, 0)
        val after = PetRules.play(s)
        assertEquals(60f, after.joy, 0.01f)
        assertEquals(40f, after.energy, 0.01f)
        assertEquals(50f, after.satiety, 0.01f)
        assertTrue(PetRules.canPlay(s))
        assertFalse(PetRules.canPlay(s.copy(energy = PetRules.TIRED - 1f)))
        assertEquals(50f, PetRules.stroke(s).joy, 0.01f)
    }

    @Test
    fun `a worn-out pet sleeps on the matrix for well under a minute, and wakes nearly full`() {
        val start = PetStats(80f, 80f, 5f, 0, 0)
        assertTrue(PetRules.isAsleep(start))
        var s = start
        var ms = 0L
        while (PetRules.isAsleep(s) && ms < 5 * minute) {
            s = watched(s, 2000L)
            ms += 2000L
        }
        assertTrue("slept ${ms / 1000} s", ms in 20_000L..60_000L)
        assertTrue(s.energy >= PetRules.NAP_WAKE_ENERGY - 1f)
        assertEquals("joy kept while it slept", 80f, s.joy, 0.01f)
    }

    @Test
    fun `a two-minute visit after two hours away, hungry, fed, a few games, then tired and asleep`() {
        // Left full and played out two hours ago.
        var s = PetRules.advance(PetStats(90f, 90f, 40f, 0, 0), 2 * hour)
        assertEquals("comes back hungry", Mood.HUNGRY, PetRules.mood(s, PetRules.isAsleep(s)))
        // Fed until it refuses.
        var meals = 0
        while (true) {
            val (fed, ok) = PetRules.feed(s)
            if (!ok) break
            s = fed
            meals++
            s = watched(s, 5_000L)
        }
        assertTrue("a few presses: $meals", meals in 2..5)
        // Games while it has the energy, half a minute each.
        var games = 0
        while (PetRules.canPlay(s) && games < 10) {
            s = watched(PetRules.play(s), 30_000L)
            games++
        }
        assertTrue("a few games before it is tired: $games", games in 3..5)
        assertTrue("happy after them", dots(s.joy) >= 4)
        // Worn out by the last one, it falls asleep, and in under a minute it is up again.
        while (!PetRules.isAsleep(s) && s.energy > 0f) s = watched(s, 10_000L)
        assertTrue(PetRules.isAsleep(s))
        s = watched(s, 60_000L)
        assertFalse("awake again", PetRules.isAsleep(s))
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
    fun `mood follows the most urgent need`() {
        assertEquals(Mood.SLEEPING, PetRules.mood(PetStats(10f, 10f, 10f, 0, 0), asleep = true))
        assertEquals(Mood.HUNGRY, PetRules.mood(PetStats(10f, 90f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.TIRED, PetRules.mood(PetStats(60f, 90f, 10f, 0, 0), asleep = false))
        assertEquals(Mood.SAD, PetRules.mood(PetStats(60f, 10f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.HAPPY, PetRules.mood(PetStats(60f, 90f, 90f, 0, 0), asleep = false))
        assertEquals(Mood.CONTENT, PetRules.mood(PetStats(60f, 50f, 90f, 0, 0), asleep = false))
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
    fun `the charger fills energy on the matrix while the pet stays awake`() {
        val start = PetStats(80f, 80f, 50f, 0, 0)
        val charged = watched(start, minute, charging = true)
        val unplugged = watched(start, minute, charging = false)
        assertTrue(charged.energy > start.energy)
        assertTrue(unplugged.energy < start.energy)
        assertEquals("awake either way: hunger grows at the same pace", unplugged.satiety, charged.satiety, 0.001f)
    }
}
