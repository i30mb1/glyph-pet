package n7.glyphpet.tools

import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Not a test: dumps the frames of a hungry pet being fed (TRANSITION_OUT=file kind1,kind2...). */
class TransitionDump {
    @Test
    fun dump() {
        val out = System.getenv("TRANSITION_OUT"); assumeTrue(out != null)
        val kinds = (System.getenv("TRANSITION_KINDS") ?: "swarm").split(',')
        val sb = StringBuilder()
        for (k in kinds) {
            val s = SpeciesForms.of(k, Stage.TEEN)
            val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
            sb.append(k).append('\n')
            for (i in 0 until 40) {
                if (i == 12) e.feed(accepted = true)
                val f = e.tick(80, Scene(species = s, mood = Mood.valueOf(System.getenv("TRANSITION_MOOD") ?: "HUNGRY")))
                if (i in 8..24) sb.append(f.joinToString(",")).append('\n')
            }
        }
        File(out!!).writeText(sb.toString())
    }
}
