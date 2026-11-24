package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A dizzy gag may move the pet about, but it may not paint over its face. The ghost's used to lay a half-bright copy
 * of the whole body a couple of cells aside, which dropped a grey square into each eye slot: on this print a half-lit
 * eye is no eye, and a face drowned in grey reads as a smudge rather than as a dizzy pet.
 *
 * Checked by rendering the pet and running the gag over it: every cell of the eye and mouth slots (with a cell of
 * margin) must come out exactly as the pet drew it.
 */
class GagsTest {

    private val kinds = SpeciesForms.sprites.map { it.kind }.distinct()

    private fun faceCells(s: Species): Set<Int> = buildSet {
        fun slot(left: Int, top: Int, w: Int, h: Int) {
            for (y in top - 1..top + h) for (x in left - 1..left + w) {
                if (x in 0..24 && y in 0..24) add(y * 25 + x)
            }
        }
        s.eyes.forEach { (ex, ey) -> slot(s.ox + ex, s.oy + ey, 2, 2) }
        s.mouth?.let { (mx, my) -> slot(s.ox + mx, s.oy + my, 3, 2) }
    }

    /**
     * A gag that MOVES the pet — the cat spinning, the bot's rows jumping — carries the face along with it, and that
     * is the gag working. What is not allowed is a gag that lays a second, dimmer copy of the pet over the face it
     * already drew: the eye slot then holds a half-lit square, which on this print is neither an eye nor a hole.
     *
     * Caught by looking at the eye slots alone: after the gag, each must still be either as the pet drew it, or moved
     * wholesale — never a value in between the pet's own hole and its own body.
     */
    @Test
    fun `a dizzy gag never leaves a half-lit eye`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            val eyes = buildList {
                s.eyes.forEach { (ex, ey) ->
                    for (dx in 0..1) for (dy in 0..1) {
                        val x = s.ox + ex + dx
                        val y = s.oy + ey + dy
                        if (x in 0..24 && y in 0..24) add(y * 25 + x)
                    }
                }
            }
            if (eyes.isEmpty()) continue
            for (t in 0L until 2600L step 80L) {
                val pet = SpeciesRenderer.render(s, Face.DIZZY, timeMs = t, shadow = false)
                val gagged = pet.copyOf()
                if (!Gags.dizzy(gagged, s, s.ox, s.oy, t)) continue
                // Every value the pet itself puts anywhere on the matrix this frame: a gag may move these around,
                // but a value it invents between them is a blend laid over the face.
                val own = pet.toSortedSet()
                val bad = eyes.filter { gagged[it] !in own }
                if (bad.isNotEmpty()) {
                    problems += "${s.id} t=${t}ms: eye ${bad.first().let { it % 25 to it / 25 }} = ${gagged[bad.first()]}"
                    break
                }
            }
        }
        assertTrue("${problems.size} cases:\n" + problems.take(12).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the gags still play`() {
        // Whatever the fix, the gag must still happen: a pet that answers a shake with nothing is worse than a smudge.
        val playing = SpeciesForms.sprites.count { s ->
            (0L until 2600L step 80L).any { t -> Gags.dizzy(SpeciesRenderer.render(s, Face.DIZZY, timeMs = t), s, s.ox, s.oy, t) }
        }
        assertTrue("no form plays a dizzy gag", playing > 0)
    }
}
