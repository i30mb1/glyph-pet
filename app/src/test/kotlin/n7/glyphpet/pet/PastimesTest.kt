package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * An adult's pastimes ([Pastimes]): each plays whole and clean on the real engine, and the pet does them now and then,
 * a few minutes apart and never the same one twice in a row.
 */
class PastimesTest {

    private val adults = SpeciesForms.sprites.filter { it.stage == Stage.ADULT && Pastimes.of(it).isNotEmpty() }

    @Test
    fun `every kind with pastimes has at least two of its own, apart from its habit, and only the adult has them`() {
        assertTrue(Pastimes.kinds.isNotEmpty())
        for (kind in Pastimes.kinds) {
            val adult = SpeciesForms.of(kind, Stage.ADULT)
            val ids = Pastimes.of(adult).map { it.id }
            assertTrue("$kind: $ids", ids.size >= 2 && ids.toSet().size == ids.size && adult.habit.id !in ids)
            assertTrue(Pastimes.of(SpeciesForms.of(kind, Stage.TEEN)).isEmpty())
            assertTrue(Pastimes.of(SpeciesForms.of(kind, Stage.BABY)).isEmpty())
        }
    }

    @Test
    fun `a pastime that redraws the body starts and ends on the calm pose, so the dissolve never mixes two pictures`() {
        val bad = mutableListOf<String>()
        for (s in adults) for (h in Pastimes.of(s)) {
            if (!h.redrawsBody) continue
            for (end in listOf(h.frames.first(), h.frames.last())) {
                val calm = (end.rows == null || end.rows == s.rows) && end.dx == 0 && end.dy == 0 && end.over.isEmpty()
                if (!calm || end.ms < PetEngine.FADE_MS) bad += "${s.id}/${h.id}"
            }
        }
        assertTrue("not calm at an end: $bad", bad.isEmpty())
    }

    @Test
    fun `every pastime plays whole on the matrix, with no stray bits`() {
        val bad = mutableListOf<String>()
        for (s in adults) for (h in Pastimes.of(s)) {
            val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            val scene = Scene(s, Mood.CONTENT)
            val calm = HashSet<Int>()
            repeat(8) { val fr = e.tick(80, scene); fr.indices.filter { fr[it] > 0 }.forEach { calm += it } }
            e.pastime(h.id)
            var played = 0L
            var guard = 0
            while (guard++ < 400) {
                val fr = e.tick(80, scene)
                if (e.pastimeNow == h.id) played += 80 else if (played > 0) break
                if (fr.indices.any { fr[it] > 0 && !GlyphMatrixSpec.isVisible(it % 25, it / 25) }) bad += "${s.id}/${h.id}: off the matrix"
                if (strayBit(fr, calm)) bad += "${s.id}/${h.id}: a stray bit"
            }
            if (played < h.durationMs - 160) bad += "${s.id}/${h.id}: played $played of ${h.durationMs} ms"
        }
        assertTrue(bad.distinct().joinToString("\n"), bad.isEmpty())
    }

    /** A connected group of 1-3 lit LEDs that was not there in the calm pose. */
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
    fun `a calm adult does its pastimes now and then, a few minutes apart, never one twice in a row`() {
        for (s in adults) {
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
            assertTrue("${s.id}: every pastime comes in 20 minutes: $ids", ids.toSet() == Pastimes.of(s).map { it.id }.toSet())
            assertTrue("${s.id}: the first not before a minute: $starts", starts.first().first >= PetEngine.PASTIME_FIRST_MS)
            starts.zipWithNext().forEach { (a, b) ->
                assertTrue("${s.id}: the same one twice in a row: $ids", a.second != b.second)
                assertTrue("${s.id}: two pastimes too close: $starts", b.first - a.first >= PetEngine.PASTIME_EVERY_MIN_MS)
            }
            // And in between it still does its own habit.
            assertEquals(s.habit.none, false)
        }
    }

    @Test
    fun `a teen of a grown hero has no pastimes, so its quiet is only its habit`() {
        val teen = SpeciesCatalog.get("cat")!!
        val e = PetEngine(Random(11), autoSignatures = false)
        var t = 0L
        while (t < 10 * 60_000L) {
            e.tick(80, Scene(teen, Mood.CONTENT, grown = true))
            assertEquals(null, e.pastimeNow)
            t += 80
        }
    }
}
