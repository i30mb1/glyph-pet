package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The pet answering the phone ([Rings], [Notices]): every kind its own way, over everything, and on the matrix. */
class PetCallsTest {

    private val kinds = SpeciesCatalog.sprites.map { it.kind } + n7.glyphpet.pet.drawn.Drawn.all.map { it.kind }

    private fun engine() = PetEngine(Random(9), autoHabits = false, autoSignatures = false)

    @Test
    fun `every kind answers a ring and a notification its own way`() {
        assertTrue("no ring: ${kinds.filter { it !in Rings.STYLES }}", kinds.all { it in Rings.STYLES })
        assertTrue("no notice: ${kinds.filter { it !in Notices.STYLES }}", kinds.all { it in Notices.STYLES })
        val rings = Rings.STYLES.entries.groupBy { (_, s) -> s.move to s.mark }.filter { it.value.size > 1 }
        assertTrue("ring the same way: ${rings.values.map { g -> g.map { it.key } }}", rings.isEmpty())
        val notices = Notices.STYLES.entries.groupBy { (_, s) -> s.move to s.mark }.filter { it.value.size > 1 }
        assertTrue("notice the same way: ${notices.values.map { g -> g.map { it.key } }}", notices.isEmpty())
    }

    @Test
    fun `rings and notices show on every pet at every stage, on the matrix, and pass`() {
        val problems = mutableListOf<String>()
        for (kind in kinds) for (stage in Stage.entries) {
            val s = SpeciesForms.of(kind, stage)
            for (ring in listOf(true, false)) {
                val e = engine()
                val calm = engine()
                repeat(5) { e.tick(80, Scene(s, Mood.CONTENT)); calm.tick(80, Scene(s, Mood.CONTENT)) }
                if (!ring) e.notice()
                var changed = 0
                repeat(20) {
                    val f = e.tick(80, Scene(s, Mood.CONTENT, ringing = ring))
                    val c = calm.tick(80, Scene(s, Mood.CONTENT))
                    if (!f.contentEquals(c)) changed++
                    for (i in f.indices) if (f[i] > 0 && !GlyphMatrixSpec.isVisible(i % 25, i / 25)) problems += "${s.id}: lit off the matrix"
                }
                if (changed < 14) problems += "${s.id} ${if (ring) "ring" else "notice"}: hardly showed ($changed of 20)"
            }
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `a ring pauses a game, a notice waits for it, and a notice ends a habit`() {
        val cat = SpeciesForms.of("cat", Stage.ADULT)
        val g = engine()
        repeat(3) { g.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue(g.trick("cat", seed = 1))
        repeat(5) { g.tick(80, Scene(cat, Mood.CONTENT)) }
        repeat(50) { g.tick(80, Scene(cat, Mood.CONTENT, ringing = true)) }
        assertTrue("the ring ended the game", g.playingTrick)
        g.notice()
        val f = g.tick(80, Scene(cat, Mood.CONTENT))
        assertTrue("a notice broke into the game", g.playingTrick && f.any { it > 0 })

        val h = engine()
        repeat(5) { h.tick(80, Scene(cat, Mood.CONTENT)) }
        h.trick()
        repeat(3) { h.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue(h.shown.habit)
        h.notice()
        repeat(25) { h.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue("the habit came back after the notice", !h.shown.habit)
    }
}
