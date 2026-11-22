package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A clap ([Startles]): every kind startles its own way, whole and on the matrix, and is itself again after. */
class StartlesTest {

    private val kinds = SpeciesCatalog.sprites.map { it.kind }

    @Test
    fun `every kind startles its own way`() {
        val missing = kinds.filter { it !in Startles.STYLES }
        assertTrue("no startle of its own: $missing", missing.isEmpty())
        val same = Startles.STYLES.entries.groupBy { (_, s) -> Triple(s.move, s.face, s.mark) }.filter { it.value.size > 1 }
        assertTrue("startled the same way: ${same.values.map { g -> g.map { it.key } }}", same.isEmpty())
    }

    @Test
    fun `a clap shows on every pet at every stage, on the matrix, and passes`() {
        val problems = mutableListOf<String>()
        for (kind in kinds) for (stage in Stage.entries) {
            val s = SpeciesForms.of(kind, stage)
            val style = Startles.of(kind)
            val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            val calm = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            repeat(5) { e.tick(80, Scene(s, Mood.CONTENT)); calm.tick(80, Scene(s, Mood.CONTENT)) }
            e.clap()
            var changed = 0
            val ticks = (style.durationMs / 80).toInt()
            repeat(ticks) {
                val f = e.tick(80, Scene(s, Mood.CONTENT))
                val c = calm.tick(80, Scene(s, Mood.CONTENT))
                if (!f.contentEquals(c)) changed++
                for (i in f.indices) if (f[i] > 0 && !GlyphMatrixSpec.isVisible(i % 25, i / 25)) problems += "${s.id}: lit off the matrix"
                if (it == 2 && e.shown.face != style.face) problems += "${s.id}: face ${e.shown.face}, not ${style.face}"
            }
            if (changed < ticks / 2) problems += "${s.id}: the clap hardly showed ($changed of $ticks)"
            repeat(15) { e.tick(80, Scene(s, Mood.CONTENT)) }
            if (e.shown.face != Face.IDLE) problems += "${s.id}: still ${e.shown.face} after the startle"
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `a sleeper only peeks at a clap`() {
        for (kind in kinds) {
            val s = SpeciesForms.of(kind, Stage.ADULT)
            val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            repeat(20) { e.tick(80, Scene(s, Mood.SLEEPING)) }
            e.clap()
            e.tick(80, Scene(s, Mood.SLEEPING))
            assertEquals(kind, Face.PEEK, e.shown.face)
        }
    }
}
