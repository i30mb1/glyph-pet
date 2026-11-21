package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The album opens a state only when the live pet really shows it ([Discovery]). */
class DiscoveryTest {

    @Test
    fun `each frame opens what it shows and nothing else`() {
        assertEquals(setOf(PetState.EATING), Discovery.seenNow(Shown(Face.EAT), false, false))
        assertEquals(setOf(PetState.ASLEEP), Discovery.seenNow(Shown(Face.PEEK), false, false))
        assertTrue(PetState.TRICK in Discovery.seenNow(Shown(null, habit = true), false, false))
        assertTrue(PetState.CHARGING in Discovery.seenNow(Shown(null, charging = true), false, false))
        val calm = Discovery.seenNow(Shown(Face.IDLE), false, false)
        assertEquals(setOf(PetState.CALM), calm)
        assertTrue(PetState.SIGNATURE in Discovery.seenNow(Shown(Face.IDLE), true, false))
        assertTrue(PetState.GAME in Discovery.seenNow(Shown(Face.IDLE), false, true))
    }

    /** Runs [engine] for [ms] in [mood] and collects what it showed. */
    private fun seen(engine: PetEngine, species: Species, mood: Mood, ms: Long, start: PetEngine.() -> Unit = {}): Set<PetState> {
        val out = mutableSetOf<PetState>()
        engine.tick(40, Scene(species, mood))
        engine.start()
        var t = 0L
        while (t < ms) {
            engine.tick(40, Scene(species, mood))
            out += Discovery.seenNow(engine)
            t += 40
        }
        return out
    }

    @Test
    fun `the live engine opens a meal, a cuddle, dizzy stars, the signature and the game`() {
        val fox = SpeciesForms.of("fox", Stage.TEEN)
        assertTrue(PetState.EATING in seen(PetEngine(), fox, Mood.CONTENT, 3000) { feed(true) })
        assertTrue(PetState.LOVED in seen(PetEngine(), fox, Mood.CONTENT, 1500) { pet() })
        assertTrue(PetState.DIZZY in seen(PetEngine(), fox, Mood.CONTENT, 1500) { shake() })
        assertTrue(PetState.SIGNATURE in seen(PetEngine(), fox, Mood.CONTENT, 1500) { signatureNow() })
        assertTrue(PetState.GAME in seen(PetEngine(), fox, Mood.HAPPY, 1500) { trick("fox") })
        assertTrue(PetState.HUNGRY in seen(PetEngine(), fox, Mood.HUNGRY, 1000))
        assertTrue(PetState.ASLEEP in seen(PetEngine(), fox, Mood.SLEEPING, 1000))
    }

    @Test
    fun `a calm pet left alone does not open its game or its meal`() {
        val fox = SpeciesForms.of("fox", Stage.TEEN)
        val calm = seen(PetEngine(), fox, Mood.CONTENT, 3000)
        assertFalse(PetState.GAME in calm)
        assertFalse(PetState.EATING in calm)
    }
}
