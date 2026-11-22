package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Blowing on the pet ([Blows]): it is pushed and comes back, and the wind never cuts what the pet is doing. */
class BlowsTest {

    private fun engine() = PetEngine(Random(7), autoHabits = false, autoSignatures = false)

    private fun leftmost(frame: IntArray): Int {
        var best = 99
        for (i in frame.indices) if (frame[i] >= SpeciesRenderer.BODY / 2) best = minOf(best, i % 25)
        return best
    }

    @Test
    fun `the wind pushes the cloud aside, it squints, and it drifts home after`() {
        for (stage in Stage.entries) {
            val cloud = SpeciesForms.of("cloud", stage)
            val e = engine()
            repeat(20) { e.tick(80, Scene(cloud, Mood.CONTENT)) }
            val home = e.shown.dx
            repeat(15) { e.tick(80, Scene(cloud, Mood.CONTENT, wind = 1f)) }
            assertEquals("${cloud.id} squints into the wind", Blows.FACE, e.shown.face)
            assertTrue("${cloud.id} pushed only ${e.shown.dx - home}", e.shown.dx - home >= 2)
            repeat(60) { e.tick(80, Scene(cloud, Mood.CONTENT)) }
            assertTrue("${cloud.id} back home", kotlin.math.abs(e.shown.dx - home) <= PetEngine.walkRange(cloud).last)
            assertFalse(e.shown.face == Blows.FACE)
        }
    }

    @Test
    fun `the wind lights nothing off the matrix and moves nobody who does not feel it`() {
        val cloud = SpeciesForms.of("cloud", Stage.ADULT)
        val e = engine()
        repeat(40) {
            val f = e.tick(80, Scene(cloud, Mood.CONTENT, wind = 1f))
            for (i in f.indices) if (f[i] > 0) assertTrue("lit off the matrix at $i", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
        val cat = SpeciesForms.of("cat", Stage.ADULT)
        val a = engine()
        val b = engine()
        repeat(30) {
            val fa = a.tick(80, Scene(cat, Mood.CONTENT))
            val fb = b.tick(80, Scene(cat, Mood.CONTENT, wind = 1f))
            assertTrue("the cat felt the wind", fa.contentEquals(fb))
        }
    }

    @Test
    fun `the wind never cuts a habit, and a sleeper does not stir`() {
        val cloud = SpeciesForms.of("cloud", Stage.ADULT)
        val e = engine()
        repeat(5) { e.tick(80, Scene(cloud, Mood.CONTENT)) }
        e.trick()
        e.tick(80, Scene(cloud, Mood.CONTENT))
        assertTrue("the habit started", e.shown.habit)
        val left = leftmost(e.tick(80, Scene(cloud, Mood.CONTENT)))
        repeat(5) { e.tick(80, Scene(cloud, Mood.CONTENT, wind = 1f)) }
        assertTrue("the wind cut the habit", e.shown.habit)
        assertTrue("the wind moved the cloud during its habit", kotlin.math.abs(leftmost(e.tick(80, Scene(cloud, Mood.CONTENT, wind = 1f))) - left) <= 1)

        val s = engine()
        repeat(20) { s.tick(80, Scene(cloud, Mood.SLEEPING)) }
        repeat(10) { s.tick(80, Scene(cloud, Mood.SLEEPING, wind = 1f)) }
        assertEquals(Face.SLEEP, s.shown.face)
    }

    @Test
    fun `a clap startles the pet, and a sleeper only peeks`() {
        val cat = SpeciesForms.of("cat", Stage.ADULT)
        val e = engine()
        repeat(5) { e.tick(80, Scene(cat, Mood.CONTENT)) }
        e.clap()
        e.tick(80, Scene(cat, Mood.CONTENT))
        assertEquals(Face.SCARED, e.shown.face)
        val s = engine()
        repeat(20) { s.tick(80, Scene(cat, Mood.SLEEPING)) }
        s.clap()
        s.tick(80, Scene(cat, Mood.SLEEPING))
        assertEquals(Face.PEEK, s.shown.face)
    }
}
