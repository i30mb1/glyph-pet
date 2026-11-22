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

/** Not a test: dumps a few seconds of each drawn hero asleep (SLEEP_OUT=file, SLEEP_KINDS=a,b). */
class SleepDump {
    @Test
    fun dump() {
        val out = System.getenv("SLEEP_OUT"); assumeTrue(out != null)
        val sb = StringBuilder()
        for (k in System.getenv("SLEEP_KINDS")!!.split(',')) {
            val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
            sb.append(k).append('\n')
            // Asleep for a while, then touched (fed and stroked): the frame before, and one while it peeks.
            for (i in 0 until 40) {
                if (i == 20) { e.feed(accepted = false); e.pet() }
                val f = e.tick(80, Scene(species = SpeciesForms.of(k, Stage.TEEN), mood = Mood.SLEEPING))
                if (i == 18 || i == 27) sb.append(f.joinToString(",")).appendLine()
            }
        }
        File(out!!).writeText(sb.toString())
    }
}
