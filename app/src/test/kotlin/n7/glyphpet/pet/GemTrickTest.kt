package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The gem's game can be played: sweeping the reflected beam round catches the stars on the rim. */
class GemTrickTest {
    @Test
    fun `sweeping the beam slowly round catches stars`() {
        for (st in Stage.entries) {
            val s = SpeciesForms.of("gem", st)
            val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
            e.haptics = mutableListOf()
            e.tick(80, Scene(species = s, mood = Mood.CONTENT))
            assertTrue(e.trick("gem", seed = 5))
            var stars = 0
            for (i in 0 until 600) {
                // Steps the lean slowly across, pausing at each step, as a player hunting for the star does.
                val step = ((i / 8) % 40) / 20f - 1f
                e.tick(80, Scene(species = s, mood = Mood.CONTENT, tilt = step))
                stars += e.haptics!!.count { it.kind == HapticKind.BALL_HIT }; e.haptics!!.clear()
            }
            assertTrue("${st.name}: $stars stars", stars >= 3)
        }
    }
}
