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

    private fun NurseryState.fed(ms: Long) = GrowthRules.markFed(this, ms)

    private fun NurseryState.sick(ms: Long) = GrowthRules.markSick(this, ms)

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

    /** The first pet grown into a teen on the morning of March 2nd. */
    private fun withTeen(): NurseryState = withFirstPet().at(at(2, 7)).shake(3, at(2, 7, 1)).first

    /** Feeds the active pet on each of [days] (and nothing else), then moves to the morning after the last one. */
    private fun NurseryState.careFor(vararg days: Int): NurseryState {
        var s = this
        for (d in days) s = s.at(at(d, 9)).fed(at(d, 9, 5))
        return s.at(at(days.last() + 1, 7))
    }

    // --- Hatching and the baby -----------------------------------------------------------------------

    @Test
    fun `a hatch makes a baby and the matrix shows it`() {
        val s = withFirstPet()
        val kind = s.active!!
        assertEquals(Growth(Stage.BABY, Stage.BABY, careDays = 0, ready = false, sinceDay = day(1)), s.grownOf())
        val form = GrowthRules.activeForm(s)!!
        assertEquals("$kind.baby", form.id)
        assertEquals(kind, form.kind)
        assertEquals(Stage.BABY, form.stage)
        assertSame(SpeciesForms.of(kind, Stage.BABY), form)
        assertFalse(GrowthRules.ready(s))
    }

    @Test
    fun `a baby is ready from 6 the next morning`() {
        val s = withFirstPet()
        assertFalse(s.at(at(1, 23, 59)).grownOf().ready)
        assertFalse(s.at(at(2, 5, 59)).grownOf().ready)
        assertTrue(s.at(at(2, 6)).grownOf().ready)
        assertTrue(GrowthRules.ready(s.at(at(2, 6))))
        assertTrue("days later too", s.at(at(9, 12)).grownOf().ready)
    }

    @Test
    fun `every baby gets ready, not only the active one`() {
        var s = withFirstPet().copy(supporter = true)
        val first = s.active!!
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(1, 11))!! }
        val second = s.active!!
        assertTrue(first != second)
        val morning = s.at(at(2, 7))
        assertTrue(morning.grownOf(first).ready)
        assertTrue(morning.grownOf(second).ready)
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
        val ready = withFirstPet().at(at(2, 7))
        val kind = ready.active!!
        val (one, r1) = ready.shake(1, at(2, 7, 1))
        assertEquals(GrowResult.SHOOK, r1)
        assertEquals(1, one.grownOf().evolvePresses)
        val (two, r2) = one.shake(1, at(2, 7, 1))
        assertEquals(GrowResult.SHOOK, r2)
        assertEquals(Stage.BABY, two.grownOf().reached)
        val (teen, r3) = two.shake(1, at(2, 7, 2))
        assertEquals(GrowResult.EVOLVED, r3)
        assertEquals(Growth(Stage.TEEN, Stage.TEEN, careDays = 0, ready = false, sinceDay = day(2)), teen.grownOf())
        assertSame(SpeciesCatalog.get(kind), GrowthRules.activeForm(teen))
        assertEquals(kind, GrowthRules.justEvolved(teen, at(2, 7, 2))?.id)
        assertNull(GrowthRules.justEvolved(teen, at(2, 7, 2) + GrowthRules.JUST_EVOLVED_MS + 1))
        assertEquals("no more shakes count", GrowResult.NONE, GrowthRules.shake(teen, at(2, 8)).second)
    }

    // --- Care days of a teen -------------------------------------------------------------------------

    @Test
    fun `a fed teen that never got sick counts the day at the next morning`() {
        val s = withTeen()
        val fed = s.at(at(2, 9)).fed(at(2, 9, 5))
        assertEquals("not before the day is over", 0, fed.at(at(3, 5, 59)).grownOf().careDays)
        assertEquals(1, fed.at(at(3, 6)).grownOf().careDays)
    }

    @Test
    fun `a day without food or with sickness does not count, and nothing is lost`() {
        var s = withTeen().careFor(2)
        assertEquals(1, s.grownOf().careDays)
        s = s.at(at(4, 7)) // day 3: not fed
        assertEquals(1, s.grownOf().careDays)
        s = s.fed(at(4, 9)).sick(at(4, 20)).at(at(5, 7)) // day 4: fed, but sick in the evening
        assertEquals(1, s.grownOf().careDays)
        s = s.sick(at(5, 8)).fed(at(5, 9)).at(at(6, 7)) // day 5: sick in the morning, then fed
        assertEquals(1, s.grownOf().careDays)
        assertFalse(s.grownOf().ready)
    }

    @Test
    fun `days the game did not run just do not count`() {
        val s = withTeen().careFor(2).at(at(3, 9)).fed(at(3, 10)).at(at(9, 12))
        assertEquals("days 4 to 8 missed", 2, s.grownOf().careDays)
    }

    @Test
    fun `three care days make a teen ready, then three shakes grow it into an adult`() {
        val s = withTeen().careFor(2, 3)
        assertFalse(s.grownOf().ready)
        val ready = s.careFor(5)
        assertEquals(3, ready.grownOf().careDays)
        assertTrue(ready.grownOf().ready)
        assertEquals("no more counting once ready", 3, ready.careFor(6, 7).grownOf().careDays)
        val (adult, r) = ready.shake(GrowthRules.EVOLVE_SHAKES, at(6, 12))
        assertEquals(GrowResult.EVOLVED, r)
        val kind = adult.active!!
        assertEquals(Growth(Stage.ADULT, Stage.ADULT, careDays = 0, ready = false, sinceDay = day(6)), adult.grownOf())
        assertEquals("$kind.adult", GrowthRules.activeForm(adult)?.id)
        assertEquals("$kind.adult", GrowthRules.justEvolved(adult, at(6, 12))?.id)
    }

    @Test
    fun `an adult is final`() {
        val adult = withTeen().careFor(2, 3, 4).shake(3, at(5, 12)).first
        assertEquals(Stage.ADULT, adult.grownOf().reached)
        val later = adult.careFor(5, 6, 7, 8)
        assertEquals(adult.grownOf(), later.grownOf())
        assertSame(later, GrowthRules.makeReady(later, later.active!!))
        assertSame(later, GrowthRules.evolve(later, later.active!!, at(9, 12)))
        assertEquals(GrowResult.NONE, GrowthRules.shake(later, at(9, 12)).second)
    }

    @Test
    fun `only the active pet counts care days`() {
        var s = withTeen().copy(supporter = true)
        val teen = s.active!!
        // A second pet hatches and goes on the matrix: the teen waits.
        s = NurseryRules.putOnMatrix(s, NurseryRules.available(s).first())
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(2, 8))!! }
        val baby = s.active!!
        s = s.careFor(2, 3, 4)
        assertEquals(0, s.grownOf(teen).careDays)
        assertEquals("a baby counts no care days", 0, s.grownOf(baby).careDays)
        // Back on the matrix it counts again.
        s = NurseryRules.setActive(s, teen).careFor(5)
        assertEquals(1, s.grownOf(teen).careDays)
    }

    @Test
    fun `putting another pet on the matrix starts the day's care over`() {
        var s = withTeen().copy(supporter = true)
        val teen = s.active!!
        s = NurseryRules.putOnMatrix(s, NurseryRules.available(s).first())
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at(2, 8))!! }
        // The teen was fed today while active, then the player puts it back: the meal before the switch is not its.
        s = NurseryRules.setActive(s, teen).at(at(3, 9)).fed(at(3, 9))
        assertTrue(s.care.fed)
        val other = SpeciesCatalog.all.first { it.id != teen && (s.seen[it.id] ?: 0) > 0 }.id
        s = NurseryRules.setActive(s, other)
        assertFalse(s.care.fed)
        s = NurseryRules.setActive(s, teen).at(at(4, 7))
        assertEquals(0, s.grownOf(teen).careDays)
        assertEquals("the same pet again changes nothing", s, NurseryRules.setActive(s, teen))
    }

    @Test
    fun `care is not marked without a pet or on a clock turned back`() {
        val empty = NurseryState().at(at(1, 10))
        assertEquals(empty, empty.fed(at(1, 11)))
        val s = withTeen().at(at(3, 9))
        assertEquals(s, s.fed(at(2, 9)))
        assertEquals(s, s.sick(at(2, 9)))
    }

    @Test
    fun `a meal on a new day before anything else counts the day before first`() {
        val s = withTeen().at(at(2, 9)).fed(at(2, 9))
        // No advance at all on day 3's morning: the meal itself rolls the day over.
        val next = s.fed(at(3, 9))
        assertEquals(1, next.grownOf().careDays)
        assertEquals(CareDay(day(3), fed = true), next.care)
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
        val shownBaby = GrowthRules.setShown(teen, teen.active!!, Stage.BABY).careFor(2, 3, 4)
        assertTrue(shownBaby.grownOf().ready)
        assertEquals(Stage.BABY, shownBaby.grownOf().shown)
        val adult = shownBaby.shake(3, at(5, 12)).first
        assertEquals(Stage.ADULT, adult.grownOf().shown)
    }

    // --- Controls and saves --------------------------------------------------------------------------

    @Test
    fun `a shake grows a ready pet, and does nothing while an egg is on the matrix`() {
        assertEquals(ShakeAction.GROW, ButtonRules.shake(hasPet = true, eggOnMatrix = false, petReady = true))
        assertEquals(ShakeAction.NOTHING, ButtonRules.shake(hasPet = true, eggOnMatrix = true, petReady = true))
        assertEquals(ShakeAction.NOTHING, ButtonRules.shake(hasPet = false, eggOnMatrix = true, petReady = true))
        assertEquals(ShakeAction.NOTHING, ButtonRules.shake(hasPet = false, eggOnMatrix = false, petReady = true))
        assertEquals(ShakeAction.DIZZY, ButtonRules.shake(hasPet = true, eggOnMatrix = false, petReady = false))
        // In the game: on day 2 a new egg waits in the app and the baby is ready, the shakes grow the baby, the egg stays.
        val s = withFirstPet().at(at(2, 7))
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
        assertEquals(GrowResult.EVOLVED, ready.shake(3, at(1, 12)).second)
    }

    @Test
    fun `heroes collected before growth become teens, saves with growth stay`() {
        val old = NurseryState(active = "cat", seen = mapOf("cat" to 2, "fox" to 1))
        val s = SaveMigration.withGrowth(old, day(5))
        val teen = Growth(Stage.TEEN, Stage.TEEN, careDays = 0, ready = false, sinceDay = day(5))
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
