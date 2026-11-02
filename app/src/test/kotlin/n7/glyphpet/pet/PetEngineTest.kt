package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetEngineTest {

    private fun lit(frame: IntArray) = frame.count { it > 0 }

    @Test
    fun `every species draws every face inside the round matrix`() {
        for (s in SpeciesCatalog.all) {
            assertTrue("${s.id} rows have equal width", s.rows.all { it.length == s.width })
            for (face in Face.entries) {
                val frame = SpeciesRenderer.render(s, face)
                for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
                assertTrue("${s.id} $face is visible", lit(frame) > 20)
            }
        }
    }

    @Test
    fun `species keep most of their body on screen`() {
        for (s in SpeciesCatalog.all) {
            val cells = SpeciesRenderer.silhouette(s)
            val visible = cells.count { (x, y) -> GlyphMatrixSpec.isVisible(x, y) }
            assertTrue("${s.id}: $visible of ${cells.size}", visible >= cells.size * 0.9)
        }
    }

    @Test
    fun `faces differ from each other`() {
        val cat = SpeciesCatalog.get("cat")!!
        val idle = SpeciesRenderer.render(cat, Face.IDLE).toList()
        for (face in Face.entries - Face.IDLE) assertTrue(face.name, SpeciesRenderer.render(cat, face).toList() != idle)
    }

    @Test
    fun `wandering pet never leaves the matrix`() {
        val engine = PetEngine(Random(1))
        for (s in SpeciesCatalog.all) {
            repeat(200) {
                val frame = engine.tick(80, Scene(s, Mood.HAPPY, tilt = if (it % 60 < 30) 1f else -1f))
                for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
        }
    }

    @Test
    fun `an egg alone is drawn big, beside a pet it is small`() {
        val egg = EggView(EggStage.WHOLE, golden = false)
        val alone = lit(PetEngine().still(Scene(null, Mood.CONTENT, egg = egg)))
        val withPet = PetEngine().still(Scene(SpeciesCatalog.get("ghost"), Mood.CONTENT, egg = egg))
        val petOnly = PetEngine().still(Scene(SpeciesCatalog.get("ghost"), Mood.CONTENT))
        assertTrue(alone > 50)
        assertTrue(lit(withPet) > lit(petOnly))
    }

    @Test
    fun `fed food falls and is eaten`() {
        val engine = PetEngine(Random(2))
        val jelly = SpeciesCatalog.get("jelly")!!
        fun litAbove(frame: IntArray) = (0 until 25 * 5).count { frame[it] > 0 }
        assertEquals(0, litAbove(engine.tick(80, Scene(jelly, Mood.CONTENT))))
        engine.feed(accepted = true)
        assertTrue(litAbove(engine.tick(80, Scene(jelly, Mood.CONTENT))) > 0)
        repeat(30) { engine.tick(80, Scene(jelly, Mood.CONTENT)) }
        assertEquals(0, litAbove(engine.tick(80, Scene(jelly, Mood.CONTENT))))
    }
}
