package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Changes of state that flow instead of cutting, on top of the cross-dissolve ([PetEngine.FADE_MS]): a new face through
 * a blink, a drowsy moment falling asleep and waking, a crouch before a hop, the pet dimming back gently.
 */
class TransitionsTest {

    private val step = 40L

    private fun engine() = PetEngine(Random(1), autoHabits = false, autoSignatures = false)

    private fun run(e: PetEngine, scene: Scene, ms: Long): List<Shown> = (0 until ms / step).map { e.tick(step, scene); e.shown }

    @Test
    fun `shut eyes are shut whatever the face`() {
        val faces = Face.values().filter { it != Face.PEEK }
        for (s in SpeciesForms.sprites) for (face in faces) {
            val shut = SpeciesRenderer.carve(s, face, shut = true)
            val closed = SpeciesRenderer.carve(s, Face.IDLE, blink = true)
            for ((ex, ey) in s.eyes) for (a in 0..1) for (b in 0..1) {
                val x = ex + a
                val y = ey + b
                assertEquals("${s.id} $face eye cell $x,$y", closed.getOrNull(y)?.getOrNull(x), shut.getOrNull(y)?.getOrNull(x))
            }
        }
    }

    @Test
    fun `a new face comes through a short blink`() {
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        val e = engine()
        run(e, Scene(s, Mood.CONTENT), 2000)
        val after = run(e, Scene(s, Mood.HAPPY), 600)
        val shut = after.takeWhile { it.shut }
        assertTrue("no blink into the new face", shut.isNotEmpty())
        assertTrue("the blink is short", shut.size * step <= PetEngine.FACE_BLINK_MS + step)
        assertFalse("the eyes open again", after.last().shut)
        assertEquals(Face.CONTENT, after.last().face)
    }

    @Test
    fun `no blink into eyes that are shut anyway`() {
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        val e = engine()
        run(e, Scene(s, Mood.CONTENT), 2000)
        assertTrue(run(e, Scene(s, Mood.TIRED), 600).none { it.shut })
    }

    @Test
    fun `falling asleep and waking pass through a drowsy moment`() {
        for (s in SpeciesForms.sprites.filter { it.stage == Stage.TEEN }) {
            val e = engine()
            run(e, Scene(s, Mood.CONTENT), 1500)
            val sleeping = run(e, Scene(s, Mood.SLEEPING), 1500).map { it.face }
            assertEquals("${s.id} dozes first", Face.TIRED, sleeping.first())
            assertEquals("${s.id} then sleeps", Face.SLEEP, sleeping.last())
            assertTrue("${s.id} dozes a moment", sleeping.takeWhile { it == Face.TIRED }.size * step in PetEngine.DOZE_MS - step..PetEngine.DOZE_MS + step)
            val waking = run(e, Scene(s, Mood.CONTENT), 1500).map { it.face }
            assertEquals("${s.id} wakes drowsy", Face.TIRED, waking.first())
            assertEquals("${s.id} then is itself", PetEngine.moodFace(Mood.CONTENT), waking.last())
        }
    }

    @Test
    fun `a pet that starts asleep does not doze into it`() {
        val s = SpeciesForms.of("cat", Stage.TEEN)
        assertEquals(Face.SLEEP, run(engine(), Scene(s, Mood.SLEEPING), 200).first().face)
    }

    @Test
    fun `a hop starts with a crouch`() {
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        val e = engine()
        run(e, Scene(s, Mood.CONTENT), 1000)
        val lifts = run(e, Scene(s, Mood.CONTENT, toward = 1f), 800).map { it.lift }.filter { it != 0 }
        assertTrue("no hop: $lifts", lifts.any { it > 0 })
        assertEquals("the hop crouches first: $lifts", -1, lifts.first())
    }

    @Test
    fun `shrinking back dims gently`() {
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        val e = engine()
        run(e, Scene(s, Mood.CONTENT), 1000)
        val near = e.tick(step, Scene(s, Mood.CONTENT)).sum()
        val sums = (0 until 12).map { e.tick(step, Scene(s, Mood.CONTENT, toward = -1f)).sum() }
        // No single frame drops by the whole dim at once.
        val drops = (listOf(near) + sums).zipWithNext { a, b -> (a - b).toFloat() / near }
        assertTrue("dims in one step: $drops", drops.all { it < (1 - PetEngine.BACK_DIM) * 0.75f })
        assertTrue("never dims: ${sums.last()} vs $near", sums.last() < near * 0.9f)
    }
}
