package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/** The egg game, with a fake clock: a new pet a day as an egg, five presses open it. Days are local dates in March 2026. */
class NurseryRulesTest {

    private val random = Random(7)
    private val all = SpeciesCatalog.eggable.map { it.id }
    /** Every hero in the catalogue, the anomalies too: what a supporter gets, whatever an update adds. */
    private val every = SpeciesCatalog.all.map { it.id }

    private fun at(day: Int, h: Int, m: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.MARCH, day, h, m, 0)
    }.timeInMillis

    private fun NurseryState.at(ms: Long) = NurseryRules.advance(this, ms, random)

    /** Presses the Glyph button [n] times on egg [id], put on the matrix first (null: the egg on the matrix). */
    private fun NurseryState.press(n: Int, id: String? = null, ms: Long = at(1, 12)): NurseryState {
        var s = if (id != null) NurseryRules.putOnMatrix(this, id) else this
        repeat(n) { s = NurseryRules.press(s, ms)!! }
        return s
    }

    /** A player whose first pet hatched on March 1st. */
    private fun withFirstPet(): NurseryState = NurseryState().at(at(1, 10)).press(5, ms = at(1, 10, 1))

    // --- The daily grant ---------------------------------------------------------------------------

    @Test
    fun `a brand-new player starts with an egg and no pet`() {
        val s = NurseryState().at(at(1, 10))
        assertNull(s.active)
        assertEquals(1, NurseryRules.available(s).size)
        assertNotNull("the first egg is on the matrix by itself", NurseryRules.onMatrix(s))
    }

    @Test
    fun `one new pet a game day, from 6 in the morning`() {
        val s = withFirstPet()
        assertTrue(NurseryRules.available(s).isEmpty())
        assertTrue("no second egg the same day", NurseryRules.available(s.at(at(1, 23, 59)).at(at(2, 5, 59))).isEmpty())
        val morning = s.at(at(2, 6))
        assertEquals(1, NurseryRules.available(morning).size)
        assertEquals("only once that day", morning, morning.at(at(2, 22)).at(at(3, 5, 59)))
    }

    @Test
    fun `eggs not opened stay, one more each day the game runs`() {
        var s = withFirstPet()
        for (day in 2..5) s = s.at(at(day, 9))
        assertEquals(4, NurseryRules.available(s).size)
        assertEquals(4, s.eggs.toSet().size)
    }

    @Test
    fun `a day the game did not run gives nothing`() {
        val s = withFirstPet().at(at(2, 9)).at(at(9, 9))
        assertEquals("days 3 to 8 were missed", 2, NurseryRules.available(s).size)
    }

    @Test
    fun `the egg is always a pet the player does not have, never a duplicate`() {
        for (seed in 0 until 30) {
            val r = Random(seed)
            var s = NurseryState()
            for (day in 1..28) {
                s = NurseryRules.advance(s, at(day, 8), r)
                NurseryRules.available(s).forEach { assertEquals("seed $seed day $day", 0, s.seen[it] ?: 0) }
                assertEquals(NurseryRules.available(s).size, NurseryRules.available(s).toSet().size)
                // Opens it most days, keeps it on some.
                val egg = NurseryRules.available(s).firstOrNull()
                if (r.nextInt(3) != 0 && egg != null) s = s.press(5, egg, at(day, 9))
            }
            assertTrue("seed $seed: every count is 0 or 1", s.seen.values.all { it == 1 })
        }
    }

    @Test
    fun `once every hero is collected there are no more eggs`() {
        var s = NurseryState()
        var day = 1
        while (s.seen.size < all.size) {
            val today = s.at(at(day, 8))
            s = today.press(5, NurseryRules.available(today).first(), at(day, 9))
            day++
        }
        assertEquals(all.size + 1, day)
        assertEquals(all.toSet(), s.seen.keys)
        for (d in day..(day + 12)) s = s.at(at(d, 8))
        assertTrue(NurseryRules.available(s).isEmpty())
        assertNull(NurseryRules.onMatrix(s))
        assertNull(NurseryRules.press(s, at(31, 9)))
    }

    @Test
    fun `the last missing pet comes as the egg`() {
        val have = all.drop(1).associateWith { 1 }
        val s = NurseryState(seen = have, active = all[1], grantDay = EggRules.dayKey(at(1, 9))).at(at(2, 9))
        assertEquals(listOf(all[0]), NurseryRules.available(s))
    }

    @Test
    fun `turning the clock back gives no second egg`() {
        val s = withFirstPet().at(at(3, 9))
        assertEquals(s, s.at(at(2, 9)))
        assertEquals(s, s.at(at(3, 6)))
    }

    @Test
    fun `the roll keeps the rarity weights among the missing pets`() {
        val r = Random(5)
        val missing = SpeciesCatalog.eggable.filter { it.id in setOf("cat", "jelly", "eye") } // rare, common, legendary
        val counts = HashMap<String, Int>()
        repeat(30_000) { counts.merge(SpeciesCatalog.roll(r, missing).id, 1, Int::plus) }
        val total = counts.values.sum().toFloat()
        // Each tier's weight is split evenly over its whole tier (SpeciesCatalog.roll), not over the pool drawn from.
        val tier = SpeciesCatalog.eggable.groupingBy { it.rarity }.eachCount()
        fun share(id: String): Float {
            val rarity = SpeciesCatalog.get(id)!!.rarity
            return rarity.weight.toFloat() / tier.getValue(rarity)
        }
        val w = listOf("cat", "jelly", "eye").sumOf { share(it).toDouble() }.toFloat()
        assertEquals(share("jelly") / w, counts.getValue("jelly") / total, 0.02f)
        assertEquals(share("cat") / w, counts.getValue("cat") / total, 0.02f)
        assertEquals(share("eye") / w, counts.getValue("eye") / total, 0.02f)
    }

    // --- Five presses --------------------------------------------------------------------------------

    @Test
    fun `five presses open the egg, each one cracks it further`() {
        val laid = NurseryState().at(at(1, 10))
        val egg = NurseryRules.onMatrix(laid)!!
        val stages = mutableListOf(EggRules.stage(NurseryRules.presses(laid, egg)))
        var s = laid
        repeat(4) {
            val before = s
            s = NurseryRules.press(s, at(1, 10, 1))!!
            assertEquals(PressResult.CRACKED, NurseryRules.result(before, s))
            assertNull("not hatched yet", s.active)
            stages += EggRules.stage(NurseryRules.presses(s, egg))
        }
        assertEquals(listOf(EggStage.WHOLE, EggStage.CRACKED, EggStage.CRACKED_MORE, EggStage.HATCHING, EggStage.HATCHING), stages)
        val before = s
        val hatched = NurseryRules.press(s, at(1, 10, 2))!!
        assertEquals(PressResult.HATCHED, NurseryRules.result(before, hatched))
        assertEquals(egg, hatched.active)
        assertEquals(1, hatched.seen[egg])
        assertTrue(NurseryRules.available(hatched).isEmpty())
        assertTrue("presses forgotten once open", hatched.presses.isEmpty())
        assertEquals(egg, NurseryRules.justHatched(hatched, at(1, 10, 2))?.id)
        assertNull(NurseryRules.justHatched(hatched, at(1, 10, 3)))
    }

    @Test
    fun `the new pet becomes the active one even with a pet already`() {
        val s = withFirstPet().at(at(2, 9))
        val egg = NurseryRules.available(s).first()
        val hatched = s.press(5, egg, at(2, 10))
        assertEquals(egg, hatched.active)
        assertNull("the pet is back on the matrix", NurseryRules.onMatrix(hatched))
    }

    @Test
    fun `with a pet an egg waits in the app until it is put on the matrix`() {
        val s = withFirstPet().at(at(2, 9))
        val egg = NurseryRules.available(s).first()
        assertNull(NurseryRules.onMatrix(s))
        assertNull("the button has no egg to crack", NurseryRules.press(s, at(2, 10)))
        val on = NurseryRules.putOnMatrix(s, egg)
        assertEquals(egg, NurseryRules.onMatrix(on))
        val cracked = on.press(2)
        val off = NurseryRules.takeOffMatrix(cracked)
        assertNull(NurseryRules.onMatrix(off))
        assertEquals("presses stay when it is taken off", 2, NurseryRules.presses(off, egg))
        assertEquals("a collected pet is no egg", s, NurseryRules.putOnMatrix(s, s.active!!))
    }

    @Test
    fun `presses are remembered per egg`() {
        var s = withFirstPet()
        for (day in 2..4) s = s.at(at(day, 9))
        val (a, b, c) = NurseryRules.available(s)
        s = s.press(3, a).press(2, b).press(1, c)
        assertEquals(listOf(3, 2, 1), listOf(a, b, c).map { NurseryRules.presses(s, it) })
        s = s.press(2, a)
        assertEquals(a, s.active)
        assertEquals(listOf(b, c), NurseryRules.available(s))
        assertEquals(listOf(2, 1), listOf(b, c).map { NurseryRules.presses(s, it) })
    }

    @Test
    fun `a stale egg on the matrix leaves the pet there`() {
        val s = withFirstPet().at(at(2, 9)).copy(onMatrix = "nope")
        assertNull(NurseryRules.onMatrix(s))
    }

    @Test
    fun `there is nothing to press without an egg, and a collected pet cannot be pressed`() {
        val s = withFirstPet()
        assertNull(NurseryRules.press(s, at(1, 12)))
        assertEquals(PressResult.NONE, NurseryRules.result(s, null))
    }

    // --- Supporter -----------------------------------------------------------------------------------

    @Test
    fun `a supporter has every missing pet as an egg at once, golden, the anomalies too`() {
        val s = withFirstPet().copy(supporter = true)
        val eggs = NurseryRules.available(s)
        assertEquals(every.size - 1, eggs.size)
        assertEquals(every.toSet() - s.active!!, eggs.toSet())
        // Opening them all, one after the other, the same day: each egg is put on the matrix before its presses.
        var t = s
        repeat(every.size - 1) {
            t = t.press(5, id = NurseryRules.available(t).first(), ms = at(1, 13))
        }
        assertEquals(every.size, t.seen.size)
        assertTrue(NurseryRules.available(t).isEmpty())
    }

    @Test
    fun `buying it mid-game opens every egg right away, the daily ones first`() {
        val free = withFirstPet().at(at(2, 9)).at(at(3, 9))
        val daily = NurseryRules.available(free)
        val sup = free.copy(supporter = true)
        assertEquals(daily, NurseryRules.available(sup).take(2))
        assertEquals(every.size - 1, NurseryRules.available(sup).size)
    }

    @Test
    fun `a refund takes back the extra eggs and keeps the daily ones`() {
        val sup = withFirstPet().copy(supporter = true).at(at(2, 9))
        val daily = sup.eggs
        assertEquals(1, daily.size)
        assertEquals(daily, NurseryRules.available(sup.copy(supporter = false)))
    }

    // --- Collection ----------------------------------------------------------------------------------

    @Test
    fun `only collected pets can be put on the matrix`() {
        val s = withFirstPet()
        val stranger = SpeciesCatalog.eggable.first { (s.seen[it.id] ?: 0) == 0 }.id
        assertEquals(s, NurseryRules.setActive(s, stranger))
        val friend = s.copy(seen = s.seen + (stranger to 1))
        assertEquals(stranger, NurseryRules.setActive(friend, stranger).active)
    }

    // --- The Glyph button ----------------------------------------------------------------------------

    @Test
    fun `holding the Glyph button cracks the egg on the matrix, otherwise feeds`() {
        assertEquals(ButtonAction.CRACK_EGG, ButtonRules.decide(hasPet = false, mood = Mood.CONTENT, eggOnMatrix = true))
        assertEquals(ButtonAction.NOTHING, ButtonRules.decide(hasPet = false, mood = Mood.CONTENT, eggOnMatrix = false))
        assertEquals(ButtonAction.CRACK_EGG, ButtonRules.decide(hasPet = true, mood = Mood.HUNGRY, eggOnMatrix = true))
        assertEquals(ButtonAction.CRACK_EGG, ButtonRules.decide(hasPet = true, mood = Mood.SLEEPING, eggOnMatrix = true))
        assertEquals(ButtonAction.STIR, ButtonRules.decide(hasPet = true, mood = Mood.SLEEPING, eggOnMatrix = false))
        assertEquals(ButtonAction.FEED, ButtonRules.decide(hasPet = true, mood = Mood.HAPPY, eggOnMatrix = false))
    }

    @Test
    fun `a shake makes the pet dizzy, or cracks the egg on the matrix as a press of the button does`() {
        assertEquals(ShakeAction.CRACK_EGG, ButtonRules.shake(hasPet = true, eggOnMatrix = true, petReady = false))
        assertEquals(ShakeAction.CRACK_EGG, ButtonRules.shake(hasPet = false, eggOnMatrix = true, petReady = false))
        assertEquals(ShakeAction.DIZZY, ButtonRules.shake(hasPet = true, eggOnMatrix = false, petReady = false))
        assertEquals(ShakeAction.NOTHING, ButtonRules.shake(hasPet = false, eggOnMatrix = false, petReady = false))
    }
}
