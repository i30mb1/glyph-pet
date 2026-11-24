package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two moods may not be the same picture. On a kind with no mouth (the chick, the owl, the penguin, the eye, the adult
 * dino) the eyes carry the whole expression, so the moods that differ only by the mouth used to carve identically:
 * calm and joy, calm and hunger, joy and a mouthful. [SpeciesRenderer] gives those their own eye shapes.
 *
 * Measured on the carved grid, which is what the matrix is drawn from, so a difference here is a difference the owner
 * can see — not a badge in the corner.
 */
class DistinctFacesTest {

    private val moods = listOf(Face.IDLE, Face.CONTENT, Face.HAPPY, Face.HUNGRY, Face.SAD, Face.EAT)

    private fun grid(s: Species, f: Face) = SpeciesRenderer.carve(s, f).joinToString("|") { String(it) }

    /**
     * The heroes whose eyes sit right against the outline have no body beside them to carve a brow out of: taking a
     * cell there would break the silhouette, which is worse than two moods sharing a picture. For them the mood has to
     * come from somewhere else — the body, or art of their own — and that is a separate piece of work.
     */
    private val noRoom = setOf("owl", "chick.adult", "eye", "penguin.baby")

    private fun cramped(s: Species) = s.kind in noRoom || s.id in noRoom

    @Test
    fun `hunger never looks like calm, where there is room for a brow`() {
        val same = SpeciesForms.sprites.filter { !cramped(it) && grid(it, Face.IDLE) == grid(it, Face.HUNGRY) }
        assertTrue("these cannot show hunger: ${same.map { it.id }}", same.isEmpty())
    }

    @Test
    fun `a mouthful never looks like joy, where there is room for a crease`() {
        val same = SpeciesForms.sprites.filter { !cramped(it) && grid(it, Face.HAPPY) == grid(it, Face.EAT) }
        assertTrue("these eat with a joyful face: ${same.map { it.id }}", same.isEmpty())
    }

    @Test
    fun `a mouthful never looks like sadness`() {
        val same = SpeciesForms.sprites.filter { grid(it, Face.SAD) == grid(it, Face.EAT) }
        assertTrue("these eat with a sad face: ${same.map { it.id }}", same.isEmpty())
    }

    @Test
    fun `few forms are left with two moods in one picture`() {
        val clashes = buildList {
            for (s in SpeciesForms.sprites) {
                val g = moods.associateWith { grid(s, it) }
                for (i in moods.indices) for (j in i + 1 until moods.size) {
                    if (g[moods[i]] == g[moods[j]]) add("${s.id} ${moods[i]}=${moods[j]}")
                }
            }
        }
        // Before the eye shapes were split there were 53 of these over these six moods. What is left is the handful
        // of forms whose eyes sit against the outline, where a brow cannot be carved without breaking the silhouette.
        assertTrue("${clashes.size} clashes:\n" + clashes.take(16).joinToString("\n"), clashes.size <= 18)
    }
}
