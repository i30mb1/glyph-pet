package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/** Three growth stages with a fake clock: [GrowthRules] through [NurseryRules]. Days are local dates in March 2026. */
class GrowthRulesTest {

    /** Shared on purpose, as in [NurseryRulesTest]: each new player below may roll a different first pet. */
    private val random = Random(7)

    private fun at(day: Int, h: Int, m: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.MARCH, day, h, m, 0)
    }.timeInMillis

    private fun day(d: Int) = EggRules.dayKey(at(d, 12))

    private fun NurseryState.at(ms: Long) = NurseryRules.advance(this, ms, random)

    private fun NurseryState.grownOf(kind: String = active!!) = growth.getValue(kind)

    /** A player whose first pet hatched on March 1st at 10:01. */
    private fun withFirstPet(): NurseryState {
        var s = NurseryState().at(at(1, 10))
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(1, 10, 1))!! }
        return s
    }

    /** Shakes the phone [n] times; returns the state and what the last shake did. */
    private fun NurseryState.shake(n: Int, ms: Long): Pair<NurseryState, GrowResult> {
        var s = this
        var r = GrowResult.NONE
        repeat(n) { s.let { GrowthRules.shake(it, ms) }.also { s = it.first; r = it.second } }
        return s to r
    }

    /** The active pet earns [amount] experience, as the toy awards it (see [PetStore.gain]). */
    private fun NurseryState.earn(amount: Int) = GrowthRules.gain(this, amount).first

    /** The first pet grown into a teen once it is ready, on March 2nd. */
    private fun withTeen(): NurseryState = withFirstPet().earn(XpRules.BABY_TO_TEEN).shake(3, at(2, 7, 1)).first

    // --- Hatching and the baby -----------------------------------------------------------------------

    /** The experience the very first hero hatches with, so its owner meets an evolution soon (see [XpRules.FIRST_HEAD_START]). */
    private val headStart = XpRules.FIRST_HEAD_START

    /** A little experience at a time: a couple of minutes together. */
    private val STEP = 2

    @Test
    fun `a hatch makes a baby and the matrix shows it`() {
        val s = withFirstPet()
        val kind = s.active!!
        assertEquals(Growth(Stage.BABY, Stage.BABY, xp = headStart, ready = false, sinceDay = day(1)), s.grownOf())
        val form = GrowthRules.activeForm(s)!!
        assertEquals("$kind.baby", form.id)
        assertEquals(kind, form.kind)
        assertEquals(Stage.BABY, form.stage)
        assertSame(SpeciesForms.of(kind, Stage.BABY), form)
        assertFalse(GrowthRules.ready(s))
    }

    @Test
    fun `the first hero hatches with a head start, every hero after it from zero`() {
        var s = withFirstPet().copy(supporter = true)
        val first = s.active!!
        assertEquals(headStart, s.grownOf(first).xp)
        s = NurseryRules.putOnMatrix(s, NurseryRules.available(s).first())
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(1, 11))!! }
        val second = s.active!!
        assertTrue(first != second)
        assertEquals("a later hero starts from zero", 0, s.grownOf(second).xp)
    }

    @Test
    fun `a baby is ready once it has earned its hundred`() {
        val s = withFirstPet()
        assertEquals(XpRules.BABY_TO_TEEN, s.grownOf().threshold)
        assertFalse(s.earn(XpRules.BABY_TO_TEEN - headStart - 1).grownOf().ready)
        val grown = s.earn(XpRules.BABY_TO_TEEN - headStart)
        assertTrue(grown.grownOf().ready)
        assertTrue(GrowthRules.ready(grown))
        assertEquals("never over the threshold", XpRules.BABY_TO_TEEN, grown.grownOf().xp)
    }

    @Test
    fun `experience is counted in little steps, and a ready pet earns no more`() {
        var s = withFirstPet()
        repeat((XpRules.BABY_TO_TEEN - headStart) / STEP) { s = s.earn(STEP) }
        s = s.earn(XpRules.BABY_TO_TEEN)
        assertEquals(XpRules.BABY_TO_TEEN, s.grownOf().xp)
        assertTrue(s.grownOf().ready)
        // Whatever the owner does now, the pet waits for its shakes.
        val (more, gained) = GrowthRules.gain(s, STEP)
        assertEquals(0, gained)
        assertSame(s, more)
    }

    @Test
    fun `only the pet on the matrix earns, and never while an egg is there`() {
        var s = withFirstPet().copy(supporter = true)
        val first = s.active!!
        // A second pet hatches and becomes the active one; the first waits with what it had.
        s = NurseryRules.putOnMatrix(s, NurseryRules.available(s).first())
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(1, 11))!! }
        val second = s.active!!
        assertTrue(first != second)
        s = s.earn(STEP)
        assertEquals(STEP, s.grownOf(second).xp)
        assertEquals("the pet that waits earns nothing", headStart, s.grownOf(first).xp)
        // An egg on the matrix sends the pet away: nothing it does counts.
        val away = NurseryRules.putOnMatrix(s, NurseryRules.available(s).first())
        assertEquals(0, GrowthRules.gain(away, STEP).second)
        // Nor does a player without a pet at all.
        val empty = NurseryState().at(at(1, 10))
        assertEquals(0, GrowthRules.gain(empty, STEP).second)
    }

    @Test
    fun `a pet grows only when ready`() {
        val s = withFirstPet()
        val kind = s.active!!
        assertSame(s, GrowthRules.evolve(s, kind, at(1, 12)))
        val (shaken, result) = GrowthRules.shake(s, at(1, 12))
        assertEquals(GrowResult.NONE, result)
        assertSame(s, shaken)
        assertSame("not collected", s, GrowthRules.evolve(s, "nope", at(1, 12)))
    }

    @Test
    fun `three shakes grow a ready baby into a teen, shown at once and celebrated`() {
        val ready = withFirstPet().earn(XpRules.BABY_TO_TEEN)
        val kind = ready.active!!
        val (one, r1) = ready.shake(1, at(2, 7, 1))
        assertEquals(GrowResult.SHOOK, r1)
        assertEquals(1, one.grownOf().evolvePresses)
        val (two, r2) = one.shake(1, at(2, 7, 1))
        assertEquals(GrowResult.SHOOK, r2)
        assertEquals(Stage.BABY, two.grownOf().reached)
        val (teen, r3) = two.shake(1, at(2, 7, 2))
        assertEquals(GrowResult.EVOLVED, r3)
        assertEquals(Growth(Stage.TEEN, Stage.TEEN, xp = 0, ready = false, sinceDay = day(2)), teen.grownOf())
        assertSame(SpeciesCatalog.get(kind), GrowthRules.activeForm(teen))
        assertEquals(kind, GrowthRules.justEvolved(teen, at(2, 7, 2))?.id)
        assertNull(GrowthRules.justEvolved(teen, at(2, 7, 2) + GrowthRules.JUST_EVOLVED_MS + 1))
        assertEquals("no more shakes count", GrowResult.NONE, GrowthRules.shake(teen, at(2, 8)).second)
    }

    // --- The teen's three hundred --------------------------------------------------------------------

    @Test
    fun `a teen needs three hundred, then three shakes grow it into an adult`() {
        val teen = withTeen()
        assertEquals(XpRules.TEEN_TO_ADULT, teen.grownOf().threshold)
        val half = teen.earn(XpRules.TEEN_TO_ADULT / 2)
        assertFalse(half.grownOf().ready)
        val ready = half.earn(XpRules.TEEN_TO_ADULT)
        assertEquals(XpRules.TEEN_TO_ADULT, ready.grownOf().xp)
        assertTrue(ready.grownOf().ready)
        val (adult, r) = ready.shake(GrowthRules.EVOLVE_SHAKES, at(6, 12))
        assertEquals(GrowResult.EVOLVED, r)
        val kind = adult.active!!
        assertEquals(Growth(Stage.ADULT, Stage.ADULT, xp = 0, ready = false, sinceDay = day(6)), adult.grownOf())
        assertEquals("$kind.adult", GrowthRules.activeForm(adult)?.id)
        assertEquals("$kind.adult", GrowthRules.justEvolved(adult, at(6, 12))?.id)
    }

    @Test
    fun `an adult is final`() {
        val adult = withTeen().earn(XpRules.TEEN_TO_ADULT).shake(3, at(5, 12)).first
        assertEquals(Stage.ADULT, adult.grownOf().reached)
        assertNull("nothing left to grow into", adult.grownOf().threshold)
        val later = adult.earn(XpRules.TEEN_TO_ADULT)
        assertEquals(adult.grownOf(), later.grownOf())
        assertSame(later, GrowthRules.makeReady(later, later.active!!))
        assertSame(later, GrowthRules.evolve(later, later.active!!, at(9, 12)))
        assertEquals(GrowResult.NONE, GrowthRules.shake(later, at(9, 12)).second)
    }

    // --- Minutes together ----------------------------------------------------------------------------

    @Test
    fun `whole minutes on the matrix bring experience, the part-minute waits`() {
        val s = withFirstPet().copy(xpAtMs = at(2, 9))
        val (none, atOnce) = GrowthRules.together(s, at(2, 9), gains = true, perMinute = XpRules.PER_MINUTE)
        assertEquals(0, atOnce)
        assertSame(s, none)
        val (counted, gained) = GrowthRules.together(s, at(2, 9) + 150_000, gains = true, perMinute = XpRules.PER_MINUTE)
        assertEquals("two whole minutes of the two and a half", 2 * XpRules.PER_MINUTE, gained)
        assertEquals(headStart + 2 * XpRules.PER_MINUTE, counted.grownOf().xp)
        assertEquals("the half minute is kept for next time", at(2, 9) + 120_000, counted.xpAtMs)
    }

    @Test
    fun `a pet that earns nothing still has its minutes counted off`() {
        val s = withFirstPet().copy(xpAtMs = at(2, 9))
        val (moved, gained) = GrowthRules.together(s, at(2, 9) + 120_000, gains = false, perMinute = XpRules.PER_MINUTE)
        assertEquals(0, gained)
        assertEquals(headStart, moved.grownOf().xp)
        assertEquals(at(2, 9) + 120_000, moved.xpAtMs)
    }

    @Test
    fun `a long gap, a clock turned back or a first run start the counting afresh`() {
        val s = withFirstPet().copy(xpAtMs = at(2, 9))
        for (now in listOf(at(2, 9) + XpRules.MAX_GAP_MS + 1, at(2, 8))) {
            val (fresh, gained) = GrowthRules.together(s, now, gains = true, perMinute = XpRules.PER_MINUTE)
            assertEquals("now=$now", 0, gained)
            assertEquals(now, fresh.xpAtMs)
            assertEquals(headStart, fresh.grownOf().xp)
        }
        // A save that never counted a minute yet: the first frame only sets the clock.
        val first = withFirstPet()
        assertEquals(0, first.xpAtMs)
        val (started, gained) = GrowthRules.together(first, at(2, 9), gains = true, perMinute = XpRules.PER_MINUTE)
        assertEquals(0, gained)
        assertEquals(at(2, 9), started.xpAtMs)
    }

    // --- What a gain is worth ------------------------------------------------------------------------

    @Test
    fun `a sleeping, sick or hungry pet gains nothing from time`() {
        assertFalse(XpRules.gains(PetStats(80f, 80f, 80f, 0, 0), asleep = true))
        assertFalse(XpRules.gains(PetStats(2f, 2f, 80f, 0, 0), asleep = false))
        assertFalse(XpRules.gains(PetStats(10f, 80f, 80f, 0, 0), asleep = false))
        assertTrue(XpRules.gains(PetStats(80f, 80f, 80f, 0, 0), asleep = false))
    }

    @Test
    fun `care days of an old save turn into the experience they were worth`() {
        // Version 3 counted three care days for a teen; they become its 300 (see PetStore.parseGrowth).
        assertEquals(0, XpRules.fromCareDays(Stage.TEEN, 0))
        assertEquals(XpRules.TEEN_TO_ADULT / 3, XpRules.fromCareDays(Stage.TEEN, 1))
        assertEquals(XpRules.TEEN_TO_ADULT, XpRules.fromCareDays(Stage.TEEN, 3))
        assertEquals("never more than the threshold", XpRules.TEEN_TO_ADULT, XpRules.fromCareDays(Stage.TEEN, 9))
        assertEquals("a baby's day was never counted", 0, XpRules.fromCareDays(Stage.BABY, 3))
        assertEquals(0, XpRules.fromCareDays(Stage.ADULT, 3))
    }

    // --- The form on the matrix ----------------------------------------------------------------------

    @Test
    fun `any reached form can be shown, never one not reached yet`() {
        val baby = withFirstPet()
        val kind = baby.active!!
        assertSame(baby, GrowthRules.setShown(baby, kind, Stage.TEEN))
        assertSame(baby, GrowthRules.setShown(baby, kind, Stage.ADULT))
        assertSame(baby, GrowthRules.setShown(baby, "nope", Stage.BABY))
        val teen = withTeen()
        val teenKind = teen.active!!
        val shownBaby = GrowthRules.setShown(teen, teenKind, Stage.BABY)
        assertEquals(Stage.BABY, shownBaby.grownOf().shown)
        assertEquals(Stage.TEEN, shownBaby.grownOf().reached)
        assertEquals("$teenKind.baby", GrowthRules.activeForm(shownBaby)?.id)
        assertSame(shownBaby, GrowthRules.setShown(shownBaby, teenKind, Stage.ADULT))
        assertEquals(teenKind, GrowthRules.activeForm(GrowthRules.setShown(shownBaby, teenKind, Stage.TEEN))?.id)
    }

    @Test
    fun `growth goes on whatever form is shown, and growing shows the new form`() {
        val teen = withTeen()
        val shownBaby = GrowthRules.setShown(teen, teen.active!!, Stage.BABY).earn(XpRules.TEEN_TO_ADULT)
        assertTrue(shownBaby.grownOf().ready)
        assertEquals(Stage.BABY, shownBaby.grownOf().shown)
        val adult = shownBaby.shake(3, at(5, 12)).first
        assertEquals(Stage.ADULT, adult.grownOf().shown)
    }

    // --- Controls and saves --------------------------------------------------------------------------

    @Test
    fun `a shake grows a ready pet, and cracks the egg instead while one is on the matrix`() {
        assertEquals(ShakeAction.GROW, ButtonRules.shake(hasPet = true, eggOnMatrix = false, petReady = true))
        assertEquals(ShakeAction.CRACK_EGG, ButtonRules.shake(hasPet = true, eggOnMatrix = true, petReady = true))
        assertEquals(ShakeAction.CRACK_EGG, ButtonRules.shake(hasPet = false, eggOnMatrix = true, petReady = true))
        assertEquals(ShakeAction.NOTHING, ButtonRules.shake(hasPet = false, eggOnMatrix = false, petReady = true))
        assertEquals(ShakeAction.DIZZY, ButtonRules.shake(hasPet = true, eggOnMatrix = false, petReady = false))
        // In the game: on day 2 a new egg waits in the app and the baby is ready, the shakes grow the baby, the egg stays.
        val s = withFirstPet().at(at(2, 7)).earn(XpRules.BABY_TO_TEEN)
        assertTrue(NurseryRules.available(s).isNotEmpty() && GrowthRules.ready(s))
        val grown = s.shake(3, at(2, 8)).first
        assertEquals(NurseryRules.available(s), NurseryRules.available(grown))
        assertEquals(0, NurseryRules.presses(grown, NurseryRules.available(grown).first()))
    }

    @Test
    fun `the debug button makes the active pet ready`() {
        val s = withFirstPet()
        val ready = GrowthRules.makeReady(s, s.active!!)
        assertTrue(GrowthRules.ready(ready))
        assertEquals(XpRules.BABY_TO_TEEN, ready.grownOf().xp)
        assertEquals(GrowResult.EVOLVED, ready.shake(3, at(1, 12)).second)
    }

    @Test
    fun `heroes collected before growth become teens, saves with growth stay`() {
        val old = NurseryState(active = "cat", seen = mapOf("cat" to 2, "fox" to 1))
        val s = SaveMigration.withGrowth(old, day(5))
        val teen = Growth(Stage.TEEN, Stage.TEEN, xp = 0, ready = false, sinceDay = day(5))
        assertEquals(mapOf("cat" to teen, "fox" to teen), s.growth)
        assertSame(SpeciesCatalog.get("cat"), GrowthRules.activeForm(s))
        assertSame(s, SaveMigration.withGrowth(s, day(9)))
        assertEquals("a new install has nothing to grow", NurseryState(), SaveMigration.withGrowth(NurseryState(), day(5)))
        val mixed = old.copy(growth = mapOf("cat" to GrowthRules.hatched(day(4))))
        assertEquals(GrowthRules.hatched(day(4)), SaveMigration.withGrowth(mixed, day(5)).growth["cat"])
        assertEquals(teen, SaveMigration.withGrowth(mixed, day(5)).growth["fox"])
        // A 1.1 save goes the same way after its own migration.
        val from11 = SaveMigration.withGrowth(
            SaveMigration.from11(Nursery11(null, "owl", mapOf("owl" to 1), false, day(4), 0, null), Random(1)), day(5),
        )
        assertEquals(mapOf("owl" to teen), from11.growth)
    }
}
