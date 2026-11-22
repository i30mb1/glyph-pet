package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Blowing on the pet ([Blows]): every kind its own way, back home after, and the wind never cuts what the pet is doing. */
class BlowsTest {

    private fun engine(seed: Int = 7) = PetEngine(Random(seed), autoHabits = false, autoSignatures = false)

    private val sprites = SpeciesCatalog.sprites.map { it.kind }
    private val kinds = sprites + n7.glyphpet.pet.drawn.Drawn.all.map { it.kind }

    @Test
    fun `every kind has its own way with the wind`() {
        val missing = kinds.filter { !Blows.handles(it) }
        assertTrue("no way with the wind: $missing", missing.isEmpty())
        // A drawn hero's face is its own drawing: the move and the debris alone must tell every kind apart.
        val ways = Blows.WAYS.entries.groupBy { (_, w) -> w.move to w.debris }.filter { it.value.size > 1 }
        assertTrue("the same way with the wind: ${ways.values.map { g -> g.map { it.key } }}", ways.isEmpty())
    }

    @Test
    fun `the wind moves every pet at every stage, lights nothing off the matrix, and it comes home after`() {
        val problems = mutableListOf<String>()
        for (kind in kinds) for (stage in Stage.entries) {
            val s = SpeciesForms.of(kind, stage)
            val e = engine()
            val calm = engine()
            repeat(10) { e.tick(80, Scene(s, Mood.CONTENT)); calm.tick(80, Scene(s, Mood.CONTENT)) }
            var changed = 0
            repeat(20) {
                val f = e.tick(80, Scene(s, Mood.CONTENT, wind = 1f))
                val c = calm.tick(80, Scene(s, Mood.CONTENT))
                if (!f.contentEquals(c)) changed++
                for (i in f.indices) if (f[i] > 0 && !GlyphMatrixSpec.isVisible(i % 25, i / 25)) problems += "${s.id}: lit off the matrix"
            }
            if (changed < 10) problems += "${s.id}: the wind hardly showed ($changed frames)"
            if (kind in sprites && e.shown.face != Blows.of(kind)!!.face) problems += "${s.id}: face ${e.shown.face} in the wind"
            // Left alone for long enough, it is itself again: at home, whole and as bright as ever.
            // The light of the last frames on either side, added up, so a blink or a flicker at the moment of looking
            // is not "faded".
            repeat(100) { e.tick(80, Scene(s, Mood.CONTENT)) }
            var lastLight = 0L
            var refLight = 0L
            repeat(20) { lastLight += e.tick(80, Scene(s, Mood.CONTENT)).sum(); refLight += calm.tick(80, Scene(s, Mood.CONTENT)).sum() }
            if (kotlin.math.abs(e.shown.dx) > PetEngine.walkRange(s).last) problems += "${s.id}: not home (dx ${e.shown.dx})"
            if (lastLight < refLight * 0.8f) problems += "${s.id}: still faded after the wind"
            if (kind in sprites && e.shown.face == Blows.of(kind)!!.face && Blows.of(kind)!!.face != Face.IDLE) problems += "${s.id}: still makes its wind face"
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the wind never cuts a habit, and a sleeper does not stir`() {
        val problems = mutableListOf<String>()
        for (kind in sprites) {
            val s = SpeciesForms.of(kind, Stage.ADULT)
            if (s.habit.none) continue
            val e = engine()
            repeat(5) { e.tick(80, Scene(s, Mood.CONTENT)) }
            e.trick()
            repeat(3) { e.tick(80, Scene(s, Mood.CONTENT)) }
            if (!e.shown.habit) continue
            repeat(4) { e.tick(80, Scene(s, Mood.CONTENT, wind = 1f)) }
            if (!e.shown.habit) problems += "$kind: the wind cut its habit"

            val z = engine()
            repeat(20) { z.tick(80, Scene(s, Mood.SLEEPING)) }
            repeat(10) { z.tick(80, Scene(s, Mood.SLEEPING, wind = 1f)) }
            if (z.shown.face != Face.SLEEP) problems += "$kind: a sleeper woke to the wind"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
