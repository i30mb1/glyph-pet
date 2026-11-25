package n7.glyphpet.pet

import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.DrawnFeel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BlackHoleTest {
    private val hole = Drawn.of("blackhole")!!
    private val core = floatArrayOf(2.5f, 3.5f, 4.5f)

    /** The lit groups of [fr], 8-connected. */
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
    fun `nothing small hangs loose round the hole in any state, only its eyes inside the dark core`() {
        val cols = listOf("calm", "happy", "habit", "hungry", "sad", "tired", "asleep", "eating", "loved", "dizzy", "tilt", "charging")
        for (st in 0..2) for (col in cols) Drawn.clip(hole, col, st).forEachIndexed { n, fr ->
            for (g in groups(fr)) {
                if (g.size >= 4) continue
                val inCore = g.all { hypot(it % 25 - 12f, it / 25 - 12f) <= core[st] + 1.1f }
                assertTrue("stage $st $col frame $n: a loose bit at ${g.map { it % 25 to it / 25 }}", inCore)
            }
        }
    }

    @Test
    fun `calm it has two bright eyes in a dark core inside a whole ring`() {
        for (st in 0..2) {
            val fr = Drawn.clip(hole, "calm", st)[5]
            val eyes = groups(fr).filter { g -> g.size <= 2 && g.all { hypot(it % 25 - 12f, it / 25 - 12f) <= core[st] } }
            assertEquals("stage $st eyes", 2, eyes.size)
            assertTrue("stage $st eyes are bright", eyes.all { g -> g.all { fr[it] >= 3500 } })
            assertEquals("stage $st the middle is dark", 0, fr[12 * 25 + 12])
        }
    }

    @Test
    fun `the disc leans after the phone on a spring and comes back to rest`() {
        fun frame(clock: Long, x: Float, y: Float): IntArray {
            DrawnFeel.live = true; DrawnFeel.timeMs = clock; DrawnFeel.tiltX = x; DrawnFeel.tiltY = y
            DrawnFeel.wind = 0f; DrawnFeel.startleMs = -1L; DrawnFeel.peek = false
            try { return Drawn.frame("blackhole", "calm", 400, Stage.ADULT) } finally { DrawnFeel.live = false }
        }
        val rest = frame(0, 0f, 1f)
        var clock = 0L
        var leaned = rest
        repeat(40) { clock += 40; leaned = frame(clock, 0.9f, 0.2f) }
        assertNotEquals("leaned, the disc lies another way", rest.toList(), leaned.toList())
        var back = leaned
        repeat(120) { clock += 40; back = frame(clock, 0f, 1f) }
        assertEquals("level again, it is as it was", rest.toList(), back.toList())
    }
}
