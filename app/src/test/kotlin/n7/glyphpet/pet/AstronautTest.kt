package n7.glyphpet.pet

import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.DrawnFeel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstronautTest {
    private val hero = Drawn.of("astronaut")!!

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
    fun `nothing of it floats off as a loose bit, in any state`() {
        val cols = listOf("calm", "happy", "habit", "signature", "hungry", "sad", "tired", "asleep", "eating", "loved", "dizzy", "tilt", "charging")
        for (st in 0..2) for (col in cols) Drawn.clip(hero, col, st).forEachIndexed { n, fr ->
            for (g in groups(fr)) assertTrue("stage $st $col frame $n: a loose bit at ${g.map { it % 25 to it / 25 }}", g.size >= 4)
        }
    }

    @Test
    fun `calm it has a bright visor with two dark eyes in it, and each stage is bigger`() {
        val lit = (0..2).map { st -> Drawn.clip(hero, "calm", st)[3].count { it > 0 } }
        assertTrue("the three stages grow: $lit", lit[0] < lit[1] && lit[1] < lit[2])
        for (st in 0..2) {
            val fr = Drawn.clip(hero, "calm", st)[3]
            val visor = fr.indices.filter { fr[it] >= 3300 }
            assertTrue("stage $st has a visor", visor.size >= 4)
            val xs = visor.map { it % 25 }; val ys = visor.map { it / 25 }
            val dark = (ys.min()..ys.max()).sumOf { y -> (xs.min()..xs.max()).count { x -> fr[y * 25 + x] == 0 } }
            assertEquals("stage $st: two dark eyes in the visor", 2, dark)
        }
    }

    @Test
    fun `it drifts after the phone and floats back`() {
        fun frame(clock: Long, x: Float): IntArray {
            DrawnFeel.live = true; DrawnFeel.timeMs = clock; DrawnFeel.tiltX = x; DrawnFeel.tiltY = 1f
            DrawnFeel.wind = 0f; DrawnFeel.startleMs = -1L; DrawnFeel.peek = false
            try { return Drawn.frame("astronaut", "calm", 900, Stage.ADULT) } finally { DrawnFeel.live = false }
        }
        fun centre(fr: IntArray) = fr.indices.filter { fr[it] >= 3300 }.map { it % 25 }.average()
        var clock = 0L
        var fr = frame(clock, 0f)
        val rest = centre(fr)
        repeat(60) { clock += 40; fr = frame(clock, 0.9f) }
        assertTrue("leaned right, it has drifted right: $rest -> ${centre(fr)}", centre(fr) > rest + 2)
        repeat(200) { clock += 40; fr = frame(clock, 0f) }
        assertTrue("level again, it is back near the middle: ${centre(fr)}", kotlin.math.abs(centre(fr) - 12.0) < 2.0)
    }
}
