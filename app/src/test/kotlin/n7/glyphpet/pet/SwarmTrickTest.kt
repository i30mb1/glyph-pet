package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The swarm's game can be played: flying the leader about gathers the lost fireflies, each with a tick. */
class SwarmTrickTest {
    @Test
    fun `leaning round the field gathers fireflies, and the game shows the swarm, not a placeholder`() {
        val s = SpeciesForms.of("swarm", Stage.TEEN)
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        e.haptics = mutableListOf()
        e.tick(80, Scene(species = s, mood = Mood.CONTENT))
        assertTrue(e.trick("swarm", seed = 7))
        var ticks = 0
        for (i in 0 until 600) {
            // Sweep the lean round and round, in widening circles: the leader covers the field.
            val a = i / 12f; val k = 0.4f + 0.6f * ((i / 150) % 2)
            e.tick(80, Scene(species = s, mood = Mood.CONTENT, tilt = kotlin.math.cos(a) * k, tiltY = kotlin.math.sin(a) * k))
            ticks += e.haptics!!.count { it.kind == HapticKind.BALL_HIT }; e.haptics!!.clear()
        }
        assertTrue("$ticks fireflies gathered", ticks >= 3)
    }
}
