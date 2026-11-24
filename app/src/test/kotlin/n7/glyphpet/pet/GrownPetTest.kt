package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A grown-up pet is calm company with no chores: never hungry or sad, on the matrix or away, and its own shows come
 * now and then, not on a loop; in every form the owner may show, once the hero has reached its adult stage
 * ([Nursery.grown], [Scene.grown]). The young still need feeding and play, which is how they grow.
 */
class GrownPetTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    /** [s] on the matrix for [ms], the toy reporting every two seconds. */
    private fun watched(s: PetStats, ms: Long, grown: Boolean): List<PetStats> {
        val seen = mutableListOf<PetStats>()
        var t = s.updatedAtMs
        var x = s.copy(seenAtMs = maxOf(t, 1L))
        while (t < s.updatedAtMs + ms) {
            t = minOf(t + 2000L, s.updatedAtMs + ms)
            x = PetRules.advance(x, t, grown = grown).copy(seenAtMs = t)
            seen += x
        }
        return seen
    }

    private fun mood(s: PetStats) = PetRules.mood(s, PetRules.isAsleep(s))

    @Test
    fun `an adult is never hungry or sad, through hours on the matrix and days away`() {
        val start = PetStats(90f, 90f, 90f, 0, 0)
        val onMatrix = watched(start, 3 * hour, grown = true)
        val moods = onMatrix.map(::mood).toSet()
        assertTrue("no hunger or sadness on the matrix: $moods", Mood.HUNGRY !in moods && Mood.SAD !in moods)
        assertTrue("it still naps when worn out", Mood.SLEEPING in moods)
        for (s in onMatrix) assertTrue(s.satiety >= PetRules.GROWN_SATIETY && s.joy >= PetRules.GROWN_JOY)
        // Left at rest it settles calm, not hopping with joy.
        assertEquals(Mood.CONTENT, mood(onMatrix.last().copy(energy = 90f, napping = false)))

        val away = PetRules.advance(onMatrix.last().copy(seenAtMs = 0), onMatrix.last().updatedAtMs + 3 * PetStats.DAY_MS, grown = true)
        assertEquals(Mood.CONTENT, mood(away))
        assertTrue("never calls for food or play", !CallRules.wants(away, asleep = false))
    }

    @Test
    fun `a pet that grows up hungry is fed and at ease at once`() {
        val hungry = PetStats(5f, 5f, 80f, 0, 1000)
        val now = PetRules.advance(hungry, 1000, grown = true)
        assertEquals(PetRules.GROWN_SATIETY, now.satiety, 0.01f)
        assertEquals(PetRules.GROWN_JOY, now.joy, 0.01f)
    }

    @Test
    fun `a meal or a game still lifts an adult, and it drifts back to calm`() {
        val fed = PetRules.feed(PetStats(PetRules.GROWN_SATIETY, PetRules.GROWN_JOY, 90f, 0, 0)).first
        assertTrue(fed.satiety > PetRules.GROWN_SATIETY)
        val later = watched(PetRules.playing(fed, 60_000L), 30 * minute, grown = true).last()
        assertEquals(PetRules.GROWN_SATIETY, later.satiety, 0.01f)
        assertEquals(PetRules.GROWN_JOY, later.joy, 0.01f)
    }

    @Test
    fun `the young still get hungry`() {
        val s = watched(PetStats(90f, 90f, 90f, 0, 0), hour, grown = false)
        assertTrue(s.any { mood(it) == Mood.HUNGRY })
    }

    /** Ten quiet minutes with [s] content: how many habits started and how many signatures played to the end. */
    private fun quietTen(s: Species, grown: Boolean = false): Pair<Int, Int> {
        val engine = PetEngine(Random(3))
        engine.events = mutableListOf()
        var habits = 0
        var was = false
        var t = 0L
        while (t < 10 * minute) {
            engine.tick(80, Scene(s, Mood.CONTENT, grown = grown))
            val now = engine.shown.habit
            if (now && !was) habits++
            was = now
            t += 80
        }
        return habits to engine.events!!.count { it.kind == PetEvent.Kind.SIGNATURE }
    }

    @Test
    fun `an adult shows its habit about once a minute and its signature every five, a teen keeps its quick pace`() {
        val busy = mutableListOf<String>()
        for (s in SpeciesForms.sprites.filter { it.stage == Stage.ADULT && !it.habit.none }) {
            val (habits, sigs) = quietTen(s)
            // 10 minutes at a habit every 30-60 s: about 10-20, and at most the first signature and one five minutes on.
            if (habits !in 5..20 || sigs > 2) busy += "${s.id}: $habits habits, $sigs signatures"
        }
        assertTrue("adults on a loop:\n" + busy.joinToString("\n"), busy.isEmpty())
        val teen = SpeciesCatalog.get("cat")!!
        val adult = SpeciesForms.sprites.first { it.kind == "cat" && it.stage == Stage.ADULT }
        assertTrue("the teen is livelier than the adult", quietTen(teen).first > quietTen(adult).first)
    }

    @Test
    fun `a grown-up hero shown in its teen form keeps the adult's calm pace`() {
        val busy = mutableListOf<String>()
        for (s in SpeciesForms.sprites.filter { it.stage == Stage.TEEN && !it.habit.none }) {
            val (habits, sigs) = quietTen(s, grown = true)
            if (habits !in 5..20 || sigs > 2) busy += "${s.id}: $habits habits, $sigs signatures"
        }
        assertTrue("grown teens on a loop: " + busy.joinToString("; "), busy.isEmpty())
    }
}
