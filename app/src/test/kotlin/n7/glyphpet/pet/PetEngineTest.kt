package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetEngineTest {

    @Test
    fun `frames stay inside the round matrix`() {
        val engine = PetEngine(Random(1))
        repeat(500) {
            val frame = engine.tick(80, Mood.HAPPY, tilt = if (it % 50 < 25) 1f else -1f)
            for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    @Test
    fun `fed food falls and is eaten`() {
        val engine = PetEngine(Random(2))
        fun litAbovePet(frame: IntArray) = (0 until 25 * 9).count { frame[it] > 0 }
        assertEquals(0, litAbovePet(engine.tick(80, Mood.CONTENT, 0f)))
        engine.feed(accepted = true)
        assertTrue(litAbovePet(engine.tick(80, Mood.CONTENT, 0f)) > 0)
        repeat(25) { engine.tick(80, Mood.CONTENT, 0f) }
        assertEquals(0, litAbovePet(engine.tick(80, Mood.CONTENT, 0f)))
    }

    @Test
    fun `sleeping pet is dim and sends z`() {
        val engine = PetEngine(Random(3))
        val frame = engine.tick(80, Mood.SLEEPING, 0f)
        assertTrue(frame.any { it > PetEngine.BRIGHT * 0.9 })
        assertTrue(frame.none { it == PetEngine.BODY })
    }

    @Test
    fun `still frame for always-on has the pet in the middle`() {
        val frame = PetEngine().still(Mood.CONTENT)
        assertEquals(PetEngine.BODY, frame[(19 - 9) * 25 + 12])
    }
}
