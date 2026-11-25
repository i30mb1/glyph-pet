package n7.glyphpet.pet

import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.DrawnFeel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlitchTest {
    private val hero = Drawn.of("glitch")!!

    /** The sizes of the lit groups of [fr], 8-connected, with one cell of each. */
    private fun groups(fr: IntArray): List<List<Int>> {
        val seen = BooleanArray(625)
        val out = ArrayList<List<Int>>()
        for (i in 0 until 625) {
            if (fr[i] <= 0 || seen[i]) continue
            val g = ArrayList<Int>(); val stack = ArrayDeque(listOf(i)); seen[i] = true
            while (stack.isNotEmpty()) {
                val q = stack.removeLast(); g += q
                for (dy in -1..1) for (dx in -1..1) {
                    val x = q % 25 + dx; val y = q / 25 + dy
                    if (x !in 0..24 || y !in 0..24) continue
                    val n = y * 25 + x
                    if (fr[n] > 0 && !seen[n]) { seen[n] = true; stack += n }
                }
            }
            out += g
        }
        return out
    }

    @Test
    fun `no row of it breaks off into a loose bit, in any state`() {
        val cols = listOf("calm", "happy", "habit", "hungry", "sad", "tired", "asleep", "eating", "loved", "dizzy", "tilt", "charging")
        for (st in 0..2) for (col in cols) Drawn.clip(hero, col, st).forEachIndexed { n, fr ->
            for (g in groups(fr)) assertTrue("stage $st $col frame $n: a loose bit at ${g.map { it % 25 to it / 25 }}", g.size >= 4)
        }
    }

    @Test
    fun `each stage is a bigger picture, and asleep it is one line`() {
        val lit = (0..2).map { st -> Drawn.clip(hero, "calm", st)[3].count { it > 0 } }
        assertTrue("the three stages grow: $lit", lit[0] < lit[1] && lit[1] < lit[2])
        for (st in 0..2) {
            val rows = Drawn.clip(hero, "asleep", st)[3].indices.filter { Drawn.clip(hero, "asleep", st)[3][it] > 0 }.map { it / 25 }.toSet()
            assertEquals("stage $st asleep is a single row", 1, rows.size)
        }
    }

    @Test
    fun `its rows lean after the phone on springs and come back to rest`() {
        fun frame(clock: Long, x: Float): IntArray {
            DrawnFeel.live = true; DrawnFeel.timeMs = clock; DrawnFeel.tiltX = x; DrawnFeel.tiltY = 1f
            DrawnFeel.wind = 0f; DrawnFeel.startleMs = -1L; DrawnFeel.peek = false
            // The same moment of the calm clip each time, so only the lean differs.
            try { return Drawn.frame("glitch", "calm", 900, Stage.ADULT) } finally { DrawnFeel.live = false }
        }
        val rest = frame(900, 0f)
        var clock = 900L
        var leaned = rest
        repeat(40) { clock += 40; leaned = frame(clock, 0.9f) }
        assertNotEquals("leaned, its rows have slid", rest.toList(), leaned.toList())
        var back = leaned
        repeat(140) { clock += 40; back = frame(clock, 0f) }
        assertEquals("level again, it is as it was", rest.toList(), back.toList())
    }
}
