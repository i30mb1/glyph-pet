package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every kind takes a stroke its own way ([Cuddles]): no shared heart, no two alike, all on the matrix. */
class CuddlesTest {

    @Test
    fun `every kind has its own cuddle, and no two share both the move and the touch`() {
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} has no cuddle", Cuddles.has(s.kind))
        val styles = SpeciesCatalog.sprites.map { Cuddles.of(it.kind) }
        assertEquals("two kinds cuddle alike", styles.size, styles.toSet().size)
    }

    @Test
    fun `every kind has its own joy, never its cuddle, and no two alike`() {
        for (s in SpeciesCatalog.sprites) {
            assertTrue("${s.id} has no joy", Cuddles.hasJoy(s.kind))
            assertTrue("${s.id}: its joy is its cuddle", Cuddles.joyOf(s.kind) != Cuddles.of(s.kind))
        }
        val joys = SpeciesCatalog.sprites.map { Cuddles.joyOf(it.kind) }
        assertEquals("two kinds rejoice alike", joys.size, joys.toSet().size)
    }

    /** What [s] looks like over a stroke, frame by frame. */
    private fun stroked(s: Species): List<List<Int>> {
        val engine = PetEngine(kotlin.random.Random(1))
        val scene = Scene(s, Mood.CONTENT)
        repeat(3) { engine.tick(80, scene) }
        engine.pet()
        return (0 until (Cuddles.DURATION_MS / 80).toInt()).map { engine.tick(80, scene).toList() }
    }

    @Test
    fun `a stroke moves on the matrix for every form, stays inside the round matrix, and ends`() {
        for (s in SpeciesForms.sprites) {
            val frames = stroked(s)
            assertTrue("${s.id}: the cuddle does not move", frames.toSet().size > 3)
            for (f in frames) for (i in f.indices) if (f[i] > 0) {
                assertTrue("${s.id}: LED ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
        }
    }
}
