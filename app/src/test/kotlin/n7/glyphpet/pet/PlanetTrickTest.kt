package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The planet's cannon game can be played: swinging it from side to side shoots the falling rocks down. */
class PlanetTrickTest {
    @Test
    fun `swinging the cannon side to side breaks rocks, and it draws no rim gauge`() {
        for (st in Stage.entries) {
            val s = SpeciesForms.of("planet", st)
            val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
            e.haptics = mutableListOf()
            e.tick(80, Scene(species = s, mood = Mood.CONTENT))
            assertTrue(e.trick("planet", seed = 5))
            var hits = 0
            for (i in 0 until 500) {
                e.tick(80, Scene(species = s, mood = Mood.CONTENT, tilt = kotlin.math.sin(i / 10f) * 0.9f))
                hits += e.haptics!!.count { it.kind == HapticKind.BALL_HIT }; e.haptics!!.clear()
            }
            assertTrue("${st.name}: $hits rocks", hits >= 4)
        }
    }
}
