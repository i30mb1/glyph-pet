package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each kind's own open eyes (SpeciesRenderer.ownEyes): where they show, and that a glance never breaks them. */
class EyeStylesTest {

    private val styled = setOf("cat", "fox", "hedgehog", "crab", "ghost", "bat", "bee", "shroom", "dino", "axolotl")

    @Test
    fun `the kinds with their own eyes show them when grown`() {
        val adults = SpeciesForms.sprites.filter { it.stage == Stage.ADULT && it.kind in styled }
        val missing = adults.filter { !SpeciesRenderer.ownEyes(it) }.map { it.id }
        assertTrue("no own eyes for $missing", missing.isEmpty())
        val others = SpeciesForms.sprites.filter { it.kind !in styled && SpeciesRenderer.ownEyes(it) }.map { it.id }
        assertEquals("own eyes where none were drawn", emptyList<String>(), others)
    }

    @Test
    fun `own eyes stay their own shape when the pet looks aside`() {
        // Every look the engine may give (safeEyeDx) draws the same eye, moved, or falls back to round: never half.
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.sprites.filter { SpeciesRenderer.ownEyes(it) }) {
            val home = SpeciesRenderer.carve(s, Face.IDLE)
            for (look in -2..2) {
                val d = SpeciesRenderer.safeEyeDx(s, s.rows, look)
                val g = SpeciesRenderer.carve(s, Face.IDLE, eyeDx = d)
                for ((ex, ey) in s.eyes) for (y in ey - 1..ey + 2) for (x in ex - 1..ex + 2) {
                    val was = home.getOrNull(y)?.getOrNull(x) ?: continue
                    val now = g.getOrNull(y)?.getOrNull(x + d) ?: continue
                    // Cells that the moved box does not reach may differ; inside it the shape must match or be round.
                    if (was != now && s.rows.getOrNull(y)?.getOrNull(x + d) == '#' && was != '#' && now != '#' ) problems += "${s.id} look=$d at $x,$y: '$was' became '$now'"
                }
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }
}
