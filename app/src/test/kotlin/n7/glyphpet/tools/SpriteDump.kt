package n7.glyphpet.tools

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.Stage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Dumps a pet's real frames (faces) as JSON brightness grids for design mockups. Runs only with SPRITE_OUT set. */
class SpriteDump {
    @Test
    fun dump() {
        val out = System.getenv("SPRITE_OUT") ?: return assumeTrue(false)
        val kind = System.getenv("SPRITE_KIND") ?: "cat"
        val sb = StringBuilder("{")
        for ((i, st) in listOf(Stage.BABY, Stage.TEEN, Stage.ADULT).withIndex()) {
            val s = SpeciesForms.of(kind, st)
            if (i > 0) sb.append(',')
            sb.append("\"${st.name.lowercase()}\":{")
            listOf("idle" to Face.IDLE, "happy" to Face.HAPPY, "scared" to Face.SCARED, "sleep" to Face.SLEEP, "content" to Face.CONTENT).forEachIndexed { j, (n, f) ->
                if (j > 0) sb.append(',')
                val fr = SpeciesRenderer.render(s, f, badges = false)
                sb.append("\"$n\":[").append(fr.joinToString(",") { (it / 40).toString() }).append(']')
            }
            sb.append('}')
        }
        File(out).writeText(sb.append('}').toString())
    }
}
