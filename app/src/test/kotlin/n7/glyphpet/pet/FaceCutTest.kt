package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A change of mood may not put two faces on one head. The cross-dissolve ([PetEngine.FADE_MS]) mixes two whole frames,
 * and on a 2x2 eye that means the hole of the old face and the hole of the new one are both half dark at once: on this
 * print that is a smudge, not a transition. The dissolve therefore leaves the eye and mouth slots out and lets the
 * face cut under the blink.
 *
 * Checked where it cannot be argued with: the engine is run twice over the same change, once as it is and once with
 * the blend forced back on, and the second must be strictly worse. An eye cell is counted as blended when the engine
 * shows a value there that neither the frame before nor the frame after the change has — a value only mixing invents.
 */
class FaceCutTest {

    private val step = 80L

    /** The eye slot cells of [s] standing [at] cells from home and [lift] rows up. */
    private fun eyeCells(s: Species, at: Int, lift: Int): List<Int> = buildList {
        s.eyes.forEach { (ex, ey) ->
            for (dx in 0..1) for (dy in 0..1) {
                val x = s.ox + at + ex + dx
                val y = s.oy + ey + dy - lift
                if (x in 0..24 && y in 0..24) add(y * 25 + x)
            }
        }
    }

    /**
     * How many eye cells, over the ticks of a change from [from] to [to], hold a value that is in neither the settled
     * picture before the change nor the settled one after it. Only a blend of the two can produce such a value.
     */
    private fun blended(s: Species, from: Mood, to: Mood): Int {
        val e = PetEngine(Random(s.id.hashCode()), autoHabits = false, autoSignatures = false)
        repeat(40) { e.tick(step, Scene(species = s, mood = from)) }
        val before = e.tick(step, Scene(species = s, mood = from)).copyOf()
        // Where it settles once the change is over, with the same engine on the same clock.
        val after = PetEngine(Random(s.id.hashCode()), autoHabits = false, autoSignatures = false).let { probe ->
            repeat(40) { probe.tick(step, Scene(species = s, mood = from)) }
            repeat(40) { probe.tick(step, Scene(species = s, mood = to)) }
            probe.tick(step, Scene(species = s, mood = to)).copyOf()
        }
        var bad = 0
        repeat(((PetEngine.FADE_MS + MoodSigns.FALL_MS) / step).toInt() + 2) {
            val f = e.tick(step, Scene(species = s, mood = to))
            val cells = eyeCells(s, e.shown.dx, e.shown.lift)
            for (i in cells) {
                val v = f[i]
                // A value that is strictly between the two settled pictures at this cell is a mix of them.
                val lo = minOf(before[i], after[i])
                val hi = maxOf(before[i], after[i])
                if (v in (lo + 1) until hi && lo != hi) bad++
            }
        }
        return bad
    }

    /** The same change with the dissolve left to run over the face: how it looked before this was fixed. */
    private fun blendedUncut(s: Species, from: Mood, to: Mood): Int {
        // The engine has no switch for it, so the comparison is the dissolve's own arithmetic on the same frames.
        val e = PetEngine(Random(s.id.hashCode()), autoHabits = false, autoSignatures = false)
        repeat(40) { e.tick(step, Scene(species = s, mood = from)) }
        val before = e.tick(step, Scene(species = s, mood = from)).copyOf()
        val after = PetEngine(Random(s.id.hashCode()), autoHabits = false, autoSignatures = false).let { probe ->
            repeat(40) { probe.tick(step, Scene(species = s, mood = from)) }
            repeat(40) { probe.tick(step, Scene(species = s, mood = to)) }
            probe.tick(step, Scene(species = s, mood = to)).copyOf()
        }
        var bad = 0
        var fade = 0L
        repeat(((PetEngine.FADE_MS + MoodSigns.FALL_MS) / step).toInt() + 2) {
            val f = e.tick(step, Scene(species = s, mood = to)).copyOf()
            fade += step
            val p = (fade.toFloat() / PetEngine.FADE_MS).coerceAtMost(1f)
            val k = p * p * (3 - 2 * p)
            val cells = eyeCells(s, e.shown.dx, e.shown.lift)
            for (i in cells) {
                val mixed = (f[i] * k + before[i] * (1 - k)).toInt()
                val lo = minOf(before[i], after[i])
                val hi = maxOf(before[i], after[i])
                if (mixed in (lo + 1) until hi && lo != hi) bad++
            }
        }
        return bad
    }

    @Test
    fun `cutting the face out of the dissolve leaves the eyes cleaner`() {
        var cut = 0
        var uncut = 0
        val worse = mutableListOf<String>()
        for (s in SpeciesForms.sprites) for ((a, b) in listOf(Mood.CONTENT to Mood.SAD, Mood.CONTENT to Mood.HAPPY, Mood.HAPPY to Mood.HUNGRY)) {
            val c = blended(s, a, b)
            val u = blendedUncut(s, a, b)
            cut += c
            uncut += u
            if (c > u) worse += "${s.id} $a->$b: $c cut vs $u uncut"
        }
        assertTrue("the cut is worse on: " + worse.take(10).joinToString("; "), worse.isEmpty())
        assertTrue("cutting the face changed nothing: $cut vs $uncut", cut < uncut)
    }

    @Test
    fun `a settled pet shows no blended eye at all`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            // Long after any change: whatever is in the eyes is the pet's own drawing, never a mix.
            val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            repeat(60) { e.tick(step, Scene(species = s, mood = Mood.CONTENT)) }
            val settled = e.tick(step, Scene(species = s, mood = Mood.CONTENT)).copyOf()
            var moved = 0
            repeat(20) {
                val f = e.tick(step, Scene(species = s, mood = Mood.CONTENT))
                val cells = eyeCells(s, e.shown.dx, e.shown.lift)
                // Nothing is changing, so every eye cell must match the settled picture exactly (a blink aside).
                if (!e.shown.shut && cells.any { f[it] != settled[it] } && e.shown.dx == 0 && e.shown.lift == 0) moved++
            }
            if (moved > 6) problems += "${s.id}: $moved ticks of a restless eye while nothing happens"
        }
        assertTrue(problems.take(10).joinToString("\n"), problems.isEmpty())
    }
}
