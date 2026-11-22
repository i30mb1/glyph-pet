package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What a pet shows above or around itself (a cuddle's hearts, a burst of joy, the mark of a startle, a notice or a ring)
 * stays on the round matrix: nothing of it is cut by the top edge, at any stage. ([MatrixClip] collects what fell off.)
 */
class TopEdgeTest {

    @Test
    fun `marks round every pet stay on the matrix`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            fun check(name: String, block: () -> Unit) {
                val lost = mutableSetOf<Pair<Int, Int>>()
                MatrixClip.lost = lost
                try { block() } finally { MatrixClip.lost = null }
                val top = lost.filter { (_, y) -> y < 12 }
                // A cell or two may graze the edge for a frame; more is a mark cut off.
                if (top.size > 2) problems += "${s.id} $name: ${top.size} cells cut by the top edge"
            }
            for (st in listOf(PetState.LOVED, PetState.HAPPY, PetState.HUNGRY, PetState.TIRED)) check(st.id) {
                val p = StatePreview(s, st); repeat(50) { p.tick(80) }
            }
            // A startle's ripple spreads out past the edge on purpose; every other mark must fit.
            if (Startles.of(s.kind).mark != Startles.Mark.RINGS) check("clap") {
                val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
                repeat(3) { e.tick(80, Scene(s, Mood.CONTENT)) }
                e.clap(); repeat(22) { e.tick(80, Scene(s, Mood.CONTENT)) }
            }
            check("notice") {
                val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
                repeat(3) { e.tick(80, Scene(s, Mood.CONTENT)) }
                e.notice(); repeat(22) { e.tick(80, Scene(s, Mood.CONTENT)) }
            }
            check("ring") {
                val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
                repeat(25) { e.tick(80, Scene(s, Mood.CONTENT, ringing = true)) }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
