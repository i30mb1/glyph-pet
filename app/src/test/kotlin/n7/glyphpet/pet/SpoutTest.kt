package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whale's spout plays on its own: every drop of the drawing always shows (never bald), waves of light run through
 * it so every drop flares in turn, it stays where the drawing puts it, and a sleeper's is faint.
 */
class SpoutTest {
    private val whales = SpeciesForms.all.filter { it.kind == "whale" }

    @Test
    fun `every whale form has a spout, and its drops are exactly the drawing's, above the body`() {
        assertEquals(3, whales.size)
        for (s in whales) {
            val spout = spoutOf(s)
            val drawn = (0 until spout.rows).flatMap { y -> s.rows[y].indices.filter { s.rows[y][it] == 'o' }.map { it to y } }.toSet()
            assertEquals("${s.id}: the spout's drops", drawn, spout.drops.map { (x, y, _) -> spout.base + x to y }.toSet())
        }
    }

    @Test
    fun `the spout is never bald and every drop flares once a wave`() {
        for (s in whales) for (asleep in listOf(false, true)) {
            val spout = spoutOf(s)
            val step = if (asleep) SpeciesRenderer.Spout.SLEEP_STEP_MS else SpeciesRenderer.Spout.STEP_MS
            val peak = if (asleep) SpeciesRenderer.Spout.SLEEP_PEAK else SpeciesRenderer.Spout.PEAK
            val dim = if (asleep) SpeciesRenderer.Spout.SLEEP_DIM else SpeciesRenderer.Spout.DIM
            val brightest = IntArray(spout.drops.size)
            for (t in 0 until step * spout.steps step 20) {
                val cells = spout.cells(t, asleep)
                assertEquals("${s.id}: drops missing at $t", spout.drops.size, cells.size)
                cells.forEachIndexed { i, (_, _, v) ->
                    assertTrue("${s.id}: a drop under the dim at $t", v >= dim)
                    assertTrue("${s.id}: a drop brighter than the habit's", v < SpeciesRenderer.BRIGHT)
                    brightest[i] = maxOf(brightest[i], v)
                }
            }
            for (v in brightest) assertTrue("${s.id}: a drop never flares (asleep=$asleep): $v", v >= peak * 9 / 10)
        }
    }

    @Test
    fun `a whale at rest draws its spout playing, without it the drawing is still`() {
        val s = whales.first()
        val frames = (0 until 2000L step 70).map { SpeciesRenderer.render(s, Face.IDLE, timeMs = it, spout = true).toList() }.toSet()
        assertTrue("the spout at rest does not play", frames.size > 3)
        val still = (0 until 2000L step 70).map { SpeciesRenderer.render(s, Face.IDLE, timeMs = it).toList() }.toSet()
        assertEquals("without the spout the drawing is still", 1, still.size)
    }

    private fun spoutOf(s: Species): SpeciesRenderer.Spout {
        val spout = SpeciesRenderer.Spout.of(s)
        assertNotNull("${s.id}: no spout", spout)
        return spout!!
    }
}
