package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The sky over a sad pet and a sleeping one ([Weather]): it comes in and dies away, it never switches on whole. */
class WeatherTest {

    private val cover = ByteArray(25 * 25) { SpeciesRenderer.COVER_NONE }

    private fun drops(frame: IntArray) = frame.count { it > 0 }

    /** [ms] of weather of [kind], the lit count of every frame. */
    private fun run(w: Weather, kind: Weather.Kind, ms: Long, step: Long = 80): List<Int> =
        (0 until (ms / step)).map {
            val f = IntArray(25 * 25)
            w.draw(f, step, kind, gx = 0f, gy = 1f, cover = cover)
            drops(f)
        }

    @Test
    fun `rain gathers instead of appearing whole`() {
        val lit = run(Weather(Random(1)), Weather.Kind.RAIN, 3200)
        assertTrue("the first frame of rain is already full: ${lit.take(6)}", lit.first() <= 2)
        assertTrue("the rain never fills in: ${lit.max()}", lit.max() >= 12)
        // It only ever grows while it is coming in; no frame has most of the shower arrive at once.
        val jump = lit.zipWithNext().maxOf { (a, b) -> b - a }
        assertTrue("the rain arrives in one jump of $jump drops", jump < lit.max() / 2)
    }

    @Test
    fun `rain dies away instead of being switched off`() {
        val w = Weather(Random(2))
        run(w, Weather.Kind.RAIN, 3200)
        val after = run(w, Weather.Kind.NONE, 3200)
        assertTrue("the rain stopped in one frame: ${after.take(4)}", after.first() > 4)
        assertEquals("the rain never ran out", 0, after.last())
    }

    @Test
    fun `snow for a sleeper comes in the same way`() {
        val lit = run(Weather(Random(3)), Weather.Kind.SNOW, 4000)
        assertTrue("the first frame of snow is already full: ${lit.take(6)}", lit.first() <= 2)
        assertTrue("the snow never falls: ${lit.max()}", lit.max() >= 5)
    }

    @Test
    fun `the weather stays on the round matrix`() {
        for (kind in listOf(Weather.Kind.RAIN, Weather.Kind.SNOW)) {
            val w = Weather(Random(4))
            repeat(200) {
                val f = IntArray(25 * 25)
                w.draw(f, 80, kind, gx = 0.6f, gy = 0.8f, cover = cover)
                for (i in f.indices) if (f[i] > 0) {
                    assertTrue("$kind lights ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
                }
            }
        }
    }
}
