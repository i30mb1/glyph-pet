package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Drawn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A drawn hero's pastimes ([n7.glyphpet.pet.drawn.DrawnPastime]): each a clip of its own that the grown-up plays whole on
 * the real engine, moving, without stray bits, now and then and never the same one twice in a row.
 */
class DrawnPastimesTest {

    private val heroes = Drawn.all.filter { it.pastimes.isNotEmpty() }

    @Test
    fun `a drawn hero's pastimes are two of its own, apart from its states`() {
        val states = Drawn.COLS.map { it.first }.toSet()
        for (a in heroes) {
            val ids = a.pastimes.map { it.id }
            assertTrue("${a.kind}: $ids", ids.size >= 2 && ids.toSet().size == ids.size && ids.none { it in states })
            assertTrue("${a.kind}: lengths", a.pastimes.all { it.ms in 1600..8000 })
        }
    }

    /** Every LED the calm hero lights at some moment of two calm loops: its own moving bits are not strays. */
    private fun calmLit(s: Species): Set<Int> {
        val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
        val lit = HashSet<Int>()
        repeat((2 * Drawn.length("calm") / 80).toInt()) { val fr = e.tick(80, Scene(s, Mood.CONTENT)); fr.indices.filter { fr[it] > 0 }.forEach { lit += it } }
        return lit
    }

    @Test
    fun `every drawn pastime plays whole, moving, on the matrix and with no stray bits`() {
        val bad = mutableListOf<String>()
        for (a in heroes) {
            val s = SpeciesForms.of(a.kind, Stage.ADULT)
            val calm = calmLit(s)
            for (p in a.pastimes) {
                val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
                val scene = Scene(s, Mood.CONTENT)
                repeat(8) { e.tick(80, scene) }
                e.pastime(p.id)
                var played = 0L
                val seen = HashSet<String>()
                var guard = 0
                while (guard++ < 200) {
                    val fr = e.tick(80, scene)
                    if (e.pastimeNow == p.id) { played += 80; seen += fr.contentToString() } else if (played > 0) break
                    if (fr.indices.any { fr[it] > 0 && !GlyphMatrixSpec.isVisible(it % 25, it / 25) }) bad += "${a.kind}/${p.id}: off the matrix"
                    if (strayBit(fr, calm)) bad += "${a.kind}/${p.id}: a stray bit"
                }
                if (played < p.ms - 160) bad += "${a.kind}/${p.id}: played $played of ${p.ms} ms"
                if (seen.size < 6) bad += "${a.kind}/${p.id}: hardly moves (${seen.size} different frames)"
            }
        }
        assertTrue(bad.distinct().joinToString("\n"), bad.isEmpty())
    }

    /** A connected group of 1-3 lit LEDs that the calm hero never lights. */
    private fun strayBit(fr: IntArray, calm: Set<Int>): Boolean {
        val seen = BooleanArray(fr.size)
        for (i in fr.indices) {
            if (fr[i] <= 0 || seen[i]) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque(listOf(i))
            seen[i] = true
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                group += c
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = c % 25 + dx
                    val ny = c / 25 + dy
                    if (nx !in 0 until 25 || ny !in 0 until 25) continue
                    val n = ny * 25 + nx
                    if (!seen[n] && fr[n] > 0) { seen[n] = true; stack += n }
                }
            }
            if (group.size <= 3 && group.any { it !in calm }) return true
        }
        return false
    }

    @Test
    fun `a calm drawn adult does its pastimes now and then, never one twice in a row`() {
        // A few heroes, not all: twenty minutes of a whole-matrix drawing is slow to simulate.
        for (a in heroes.take(3)) {
            val s = SpeciesForms.of(a.kind, Stage.ADULT)
            val e = PetEngine(Random(11), autoSignatures = false)
            val starts = mutableListOf<Pair<Long, String>>()
            var was: String? = null
            var t = 0L
            while (t < 20 * 60_000L) {
                e.tick(80, Scene(s, Mood.CONTENT))
                val now = e.pastimeNow
                if (now != null && now != was) starts += t to now
                was = now
                t += 80
            }
            val ids = starts.map { it.second }
            assertTrue("${a.kind}: every pastime comes in 20 minutes: $ids", ids.toSet() == a.pastimes.map { it.id }.toSet())
            starts.zipWithNext().forEach { (x, y) ->
                assertTrue("${a.kind}: twice in a row: $ids", x.second != y.second)
                assertTrue("${a.kind}: too close: $starts", y.first - x.first >= PetEngine.PASTIME_EVERY_MIN_MS)
            }
        }
    }
}
