package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The bunny's game can be played: leaning it to the carrot and nodding pulls carrots out; so does holding level. */
class BunnyTrickTest {
    private fun play(st: Stage, nods: Boolean): Int {
        val s = SpeciesForms.of("bunny", st)
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        e.haptics = mutableListOf()
        e.tick(80, Scene(species = s, mood = Mood.CONTENT))
        assertTrue(e.trick("bunny", seed = 5))
        var carrots = 0
        for (i in 0 until 600) {
            // Leans one way for a few seconds, holds level, leans the other way; with nods, a sharp one now and then.
            val phase = (i / 25) % 4
            val lean = when (phase) { 0 -> 0.3f; 2 -> -0.3f; else -> 0f }
            val nod = nods && i % 14 < 2
            e.tick(80, Scene(species = s, mood = Mood.CONTENT, tilt = lean, tiltY = if (nod) 0.5f else 1f))
            carrots += e.haptics!!.count { it.kind == HapticKind.FEED }; e.haptics!!.clear()
        }
        return carrots
    }

    @Test
    fun `leaning to the carrot and nodding pulls carrots out`() {
        for (st in Stage.entries) { val n = play(st, nods = true); assertTrue("${st.name}: $n carrots", n >= 3) }
    }

    @Test
    fun `without a nod, holding level beside the carrot pulls it too`() {
        for (st in Stage.entries) { val n = play(st, nods = false); assertTrue("${st.name}: $n carrots", n >= 3) }
    }
}
