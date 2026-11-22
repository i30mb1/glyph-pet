package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Typing along ([Typing]): every kind its own way, on the matrix, never cutting what the pet is doing, gone after. */
class TypingTest {

    private val kinds = SpeciesCatalog.sprites.map { it.kind } + n7.glyphpet.pet.drawn.Drawn.all.map { it.kind }

    @Test
    fun `every kind types its own way`() {
        val missing = kinds.filter { it !in Typing.STYLES }
        assertTrue("no way of typing: $missing", missing.isEmpty())
        val same = Typing.STYLES.entries.groupBy { (_, s) -> s.move to s.hit }.filter { it.value.size > 1 }
        assertTrue("type the same way: ${same.values.map { g -> g.map { it.key } }}", same.isEmpty())
    }

    @Test
    fun `every pet at every stage plays its keyboard, on the matrix, and puts it away after`() {
        val problems = mutableListOf<String>()
        for (kind in kinds) for (stage in Stage.entries) {
            val s = SpeciesForms.of(kind, stage)
            val e = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
            val calm = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
            repeat(5) { e.tick(80, Scene(s, Mood.CONTENT)); calm.tick(80, Scene(s, Mood.CONTENT)) }
            var changed = 0
            var keyboard = 0
            for (k in 0 until 40) {
                if (k % 3 == 0) e.key()
                val f = e.tick(80, Scene(s, Mood.CONTENT, typing = true))
                val c = calm.tick(80, Scene(s, Mood.CONTENT))
                if (!f.contentEquals(c)) changed++
                if (Typing.KEYS.count { x -> f[Typing.ROW * 25 + x] > 0 } >= Typing.KEYS.size - 2) keyboard++
                for (i in f.indices) if (f[i] > 0 && !GlyphMatrixSpec.isVisible(i % 25, i / 25)) problems += "${s.id}: lit off the matrix"
            }
            if (changed < 30) problems += "${s.id}: typing hardly showed ($changed of 40)"
            if (keyboard < 25) problems += "${s.id}: its keyboard hardly showed ($keyboard of 40)"
            repeat(20) { e.tick(80, Scene(s, Mood.CONTENT)); calm.tick(80, Scene(s, Mood.CONTENT)) }
            val f = e.tick(80, Scene(s, Mood.CONTENT))
            val c = calm.tick(80, Scene(s, Mood.CONTENT))
            val left = Typing.KEYS.count { x -> f[Typing.ROW * 25 + x] > 0 && c[Typing.ROW * 25 + x] == 0 }
            if (left > 0) problems += "${s.id}: its keyboard stayed after the typing ($left keys)"
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `typing starts no habit of its own and cuts none`() {
        val s = SpeciesForms.of("cat", Stage.ADULT)
        val e = PetEngine(Random(3), autoHabits = true, autoSignatures = true)
        for (k in 0 until 600) {
            if (k % 3 == 0) e.key()
            e.tick(80, Scene(s, Mood.CONTENT, typing = true))
            assertTrue("a habit started in the middle of typing at ${k * 80} ms", !e.shown.habit)
        }
        val h = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
        repeat(5) { h.tick(80, Scene(s, Mood.CONTENT)) }
        h.trick()
        repeat(3) { h.tick(80, Scene(s, Mood.CONTENT)) }
        assertTrue(h.shown.habit)
        repeat(4) { h.key(); h.tick(80, Scene(s, Mood.CONTENT, typing = true)) }
        assertTrue("typing cut the habit", h.shown.habit)
    }
}
