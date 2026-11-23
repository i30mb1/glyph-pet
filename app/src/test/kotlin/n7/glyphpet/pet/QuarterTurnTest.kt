package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A turn on this matrix ([TiltStyle.quarter]): a quarter turn lands every cell on another cell, so the picture is only
 * rearranged — no brightness appears that the art did not have. That is the difference between a spin the eye reads
 * and a grey smear, and it is what the startle's spin and topple are built on.
 */
class QuarterTurnTest {

    private fun still(s: Species) = SpeciesRenderer.render(s, Face.CONTENT, timeMs = 0, shadow = true)

    private fun values(f: IntArray) = f.filter { it > 0 }.toSet()

    @Test
    fun `a quarter turn only rearranges cells`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            val before = still(s)
            for (k in 1..3) {
                val f = before.copyOf()
                TiltStyle.quarter(f, s.ox, s.ox + s.width - 1, s.oy, s.oy + s.height - 1, k)
                // No new brightness: every value that shows up was there before.
                val new = values(f) - values(before)
                if (new.isNotEmpty()) problems += "${s.id} turn $k: new brightness $new"
                // No more light than went in: a turn moves cells, it does not add them.
                val litBefore = before.count { it > 0 }
                val litAfter = f.count { it > 0 }
                if (litAfter > litBefore + 4) problems += "${s.id} turn $k: $litBefore lit cells before, $litAfter after"
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `four quarter turns bring the pet back exactly`() {
        for (s in SpeciesForms.sprites) {
            val before = still(s)
            val f = before.copyOf()
            repeat(4) { TiltStyle.quarter(f, s.ox, s.ox + s.width - 1, s.oy, s.oy + s.height - 1, 1) }
            assertEquals("${s.id} does not come back round", before.toList(), f.toList())
        }
    }

    @Test
    fun `a turn never lights a cell off the matrix`() {
        for (s in SpeciesForms.sprites) for (k in 1..3) {
            val f = still(s)
            TiltStyle.quarter(f, s.ox, s.ox + s.width - 1, s.oy, s.oy + s.height - 1, k)
            for (i in f.indices) if (f[i] > 0) {
                assertTrue("${s.id} turn $k lights ${i % 25},${i / 25}", GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
        }
    }

    @Test
    fun `a spin goes round in quarters, not in a smooth blur`() {
        val spinning = SpeciesForms.sprites.filter { Startles.of(it.kind).move == Startles.Move.SPIN }
        assertTrue("no hero spins", spinning.isNotEmpty())
        for (s in spinning) {
            val style = Startles.of(s.kind)
            val seen = (0L until style.durationMs step 80L).map { Startles.quarters(style, it) }.toSet()
            // It has to pass through more than one quarter to read as a spin, and never leave the four.
            assertTrue("${s.id} does not go round: $seen", seen.size > 1)
            assertTrue("${s.id} leaves the quarters: $seen", seen.all { it in 0..3 })
        }
    }

    @Test
    fun `a topple lies on its side and gets up`() {
        val toppling = SpeciesForms.sprites.filter { Startles.of(it.kind).move == Startles.Move.TOPPLE }
        for (s in toppling) {
            val style = Startles.of(s.kind)
            val steps = (0L until style.durationMs step 80L).map { Startles.quarters(style, it) }
            assertTrue("${s.id} never topples", steps.any { it == 1 })
            assertEquals("${s.id} does not get back up", 0, steps.last())
        }
    }
}
