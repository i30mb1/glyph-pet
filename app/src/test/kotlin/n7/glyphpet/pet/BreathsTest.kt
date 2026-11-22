package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The heroes that breathe at rest ([Breaths]): each moves a part of itself, never its face, never off the matrix. */
class BreathsTest {

    private val breathers = SpeciesForms.sprites.filter { Breaths.has(it.kind) }
    private val times = (0L until 6000L step 100L)

    private fun atRest(s: Species, face: Face, t: Long) = SpeciesRenderer.render(s, face, timeMs = t, spout = true)

    private fun faceCells(s: Species): List<Int> = buildList {
        s.eyes.forEach { (ex, ey) -> for (a in 0..1) for (b in 0..1) add(s.ox + ex + a to s.oy + ey + b) }
        s.mouth?.let { (mx, my) -> for (a in 0..2) for (b in 0..1) add(s.ox + mx + a to s.oy + my + b) }
    }.filter { (x, y) -> GlyphMatrixSpec.isVisible(x, y) }.map { (x, y) -> y * 25 + x }

    @Test
    fun `the breathers breathe in all their forms`() {
        assertEquals(setOf("frog", "jelly", "cloud", "slime", "axolotl", "ghost", "cat"), breathers.map { it.kind }.toSet())
        val still = breathers.filter { s -> times.map { atRest(s, Face.IDLE, it).toList() }.toSet().size == 1 }
        // A baby ghost's hem has no teeth to ripple yet.
        assertTrue("these do not move: ${still.map { it.id }}", still.all { it.kind == "ghost" && it.stage == Stage.BABY })
    }

    @Test
    fun `breathing never moves the face`() {
        // The same moment with and without the breath: the tears and sleep signs move on their own, the breath must not.
        val problems = mutableListOf<String>()
        for (s in breathers) for (face in listOf(Face.IDLE, Face.CONTENT, Face.SLEEP, Face.SAD)) {
            val cells = faceCells(s)
            for (t in times) {
                val with = atRest(s, face, t)
                val without = SpeciesRenderer.render(s, face, timeMs = t, spout = true, breathe = false)
                val moved = cells.filter { with[it] != without[it] }
                if (moved.isNotEmpty()) { problems += "${s.id} $face t=$t: face cells ${moved.map { it % 25 to it / 25 }} change"; break }
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `breathing stays on the matrix and within its brightness`() {
        for (s in breathers) for (face in listOf(Face.IDLE, Face.SLEEP)) for (t in times) {
            val f = atRest(s, face, t)
            for (i in f.indices) {
                assertTrue("${s.id} t=$t value ${f[i]}", f[i] in 0..SpeciesRenderer.BRIGHT)
                if (f[i] > 0) assertTrue("${s.id} t=$t lights ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
        }
    }

    @Test
    fun `a pet busy with something else does not breathe`() {
        for (s in breathers) {
            val frames = times.map { SpeciesRenderer.render(s, Face.IDLE, timeMs = it).toList() }.toSet()
            assertEquals("${s.id} breathes while not at rest", 1, frames.size)
        }
    }

    @Test
    fun `asleep the breath is fainter`() {
        fun depth(s: Species, face: Face) = times.maxOf { t ->
            val with = atRest(s, face, t)
            val without = SpeciesRenderer.render(s, face, timeMs = t, spout = true, breathe = false)
            with.indices.sumOf { kotlin.math.abs(with[it] - without[it]) }
        }
        for (s in breathers.filter { it.kind != "ghost" }) {
            assertTrue("${s.id}: the sleeping breath is not fainter", depth(s, Face.SLEEP) < depth(s, Face.IDLE))
        }
    }
}
