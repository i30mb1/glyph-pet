package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The moment between two moods ([MoodSigns.FALL_MS]): a pet gives in to sadness with its body before its face
 * changes, and straightens up again the same way. Two still pictures dissolved into one another are not a change
 * of mood, and the atlas has no column for one, so it is checked here.
 */
class MoodTurnTest {

    private val cat = SpeciesForms.of("cat", Stage.ADULT)

    private fun scene(s: Species, mood: Mood) = Scene(species = s, mood = mood)

    /** Runs [ms] of [mood] and returns what each frame showed, with the frame itself. */
    private fun run(engine: PetEngine, s: Species, mood: Mood, ms: Long): List<Pair<Shown, IntArray>> =
        (0 until ms / STEP).map {
            val f = engine.tick(STEP, scene(s, mood)).copyOf()
            engine.shown to f
        }

    private fun settled(engine: PetEngine, s: Species, mood: Mood, ms: Long) {
        repeat((ms / STEP).toInt()) { engine.tick(STEP, scene(s, mood)) }
    }

    @Test
    fun `the cat sinks before its face turns sad`() {
        val engine = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        settled(engine, cat, Mood.CONTENT, 2000)
        val turn = run(engine, cat, Mood.SAD, MoodSigns.FALL_MS)
        val faces = turn.map { it.first.face }
        val lifts = turn.map { it.first.lift }
        assertTrue("the cat never sinks into its sadness: $lifts", lifts.any { it < 0 })
        assertTrue("the face turned sad on the first frame: $faces", faces.first() != Face.SAD)
        assertTrue("the face never turned sad: $faces", faces.last() == Face.SAD)
    }

    @Test
    fun `the turn is a motion, not a dissolve between two still pictures`() {
        val engine = PetEngine(Random(2), autoHabits = false, autoSignatures = false)
        settled(engine, cat, Mood.CONTENT, 2000)
        val turn = run(engine, cat, Mood.SAD, MoodSigns.FALL_MS)
        val held = turn.map { it.second }.zipWithNext().count { (a, b) -> a.contentEquals(b) }
        assertEquals("frames of the turn that stand still", 0, held)
    }

    @Test
    fun `it straightens up again when the sadness passes`() {
        val engine = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
        settled(engine, cat, Mood.CONTENT, 2000)
        settled(engine, cat, Mood.SAD, 4000)
        val back = run(engine, cat, Mood.CONTENT, MoodSigns.FALL_MS)
        assertTrue("it never gave in on the way back: ${back.map { it.first.lift }}", back.any { it.first.lift < 0 })
        assertTrue("the face stayed sad: ${back.map { it.first.face }}", back.last().first.face != Face.SAD)
        assertEquals("it did not come back up", 0, back.last().first.lift)
    }

    @Test
    fun `how a kind falls follows how it is sad`() {
        // Weepers sink where they stand; a wilting one bends instead. No kind does both, and none stays still.
        for (s in SpeciesForms.sprites.distinctBy { it.kind }) {
            val sinks = (0..10).any { MoodSigns.fallLift(s.kind, it / 10f) < 0 }
            val bends = (0..10).any { MoodSigns.fallLean(s.kind, it / 10f) != 0f }
            assertTrue("${s.kind} falls into sadness both ways", !(sinks && bends))
        }
        // Whatever it does with its body, the light goes out of every one of them and comes back.
        assertTrue(MoodSigns.fallDim(MoodSigns.FALL_FACE_AT) < 0.9f)
        assertEquals(1f, MoodSigns.fallDim(0f), 0.001f)
        assertEquals(1f, MoodSigns.fallDim(1f), 0.001f)
    }

    @Test
    fun `the turn never throws a pet off the matrix or blanks it`() {
        for (s in SpeciesForms.sprites) {
            val engine = PetEngine(Random(4), autoHabits = false, autoSignatures = false)
            settled(engine, s, Mood.CONTENT, 1600)
            val calm = engine.tick(STEP, scene(s, Mood.CONTENT)).count { it > 0 }
            for (mood in listOf(Mood.SAD, Mood.CONTENT, Mood.SAD, Mood.HAPPY)) {
                for ((_, f) in run(engine, s, mood, MoodSigns.FALL_MS + 400)) {
                    for (i in f.indices) if (f[i] > 0) {
                        assertTrue("${s.id} lights ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
                    }
                    assertTrue("${s.id} went dark turning to $mood", f.count { it > 0 } > calm / 3)
                }
            }
        }
    }

    @Test
    fun `a sleeper is not made to fall`() {
        val engine = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
        settled(engine, cat, Mood.CONTENT, 2000)
        val sleep = run(engine, cat, Mood.SLEEPING, MoodSigns.FALL_MS)
        assertTrue("a sleeper gives in to its sleep: ${sleep.map { it.first.lift }}", sleep.all { it.first.lift >= 0 })
    }

    private companion object {
        const val STEP = 80L
    }
}
