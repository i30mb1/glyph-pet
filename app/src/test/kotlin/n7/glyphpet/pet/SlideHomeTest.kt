package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * After a slide the pet comes home and lives on. A slide leaves it between cells; once it stood 0.7 of a cell past the
 * end of its walk range, never took the last step and never wandered again (a hungry hedgehog frozen on the desk).
 */
class SlideHomeTest {

    @Test
    fun `a pet left between cells past its walk range walks home and wanders on`() {
        val stuck = mutableListOf<String>()
        for (s in SpeciesForms.sprites) for (slideMs in listOf(120L, 200L, 280L, 360L, 440L, 520L)) for (way in listOf(1f, -1f)) {
            val engine = PetEngine(Random(7))
            // Settle first, then lean a little: a short slide that stops somewhere between cells.
            repeat(10) { engine.tick(40, Scene(s, Mood.HUNGRY)) }
            var t = 0L
            while (t < slideMs) { engine.tick(40, Scene(s, Mood.HUNGRY, tilt = 0.5f * way)); t += 40 }
            // Back on the desk, hungry: no habit or signature to move it, only its own steps.
            val range = PetEngine.walkRange(s)
            val seen = mutableSetOf<Int>()
            repeat(1500) {
                engine.tick(40, Scene(s, Mood.HUNGRY))
                if (it >= 250) seen += engine.shown.dx
            }
            val home = seen.all { it in range }
            // A range of more than one cell must see the pet move within the minute it has.
            val wanders = range.first == range.last || seen.size > 1
            if (!home || !wanders) stuck += "${s.id} slide=${slideMs}ms way=$way range=$range dx=$seen"
        }
        assertTrue("stuck after a slide:\n" + stuck.joinToString("\n"), stuck.isEmpty())
    }
}
