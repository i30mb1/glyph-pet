package n7.glyphpet.toy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SightTest {

    @Test
    fun `screen up on a table is unseen, screen down is seen even with the front covered`() {
        assertTrue(Sight.unseen(Pose(gravityZ = 9.8f)))
        assertTrue(Sight.unseen(Pose(gravityZ = 9.8f, near = true, lux = 0f)))
        // The Glyph owner's way of watching the matrix: screen down on the table, the proximity sensor covered.
        assertFalse(Sight.unseen(Pose(gravityZ = -9.8f, near = true, lux = 0f)))
        assertFalse(Sight.unseen(Pose(gravityZ = -9.8f)))
    }

    @Test
    fun `in a pocket the front is covered and dark and the phone stands upright`() {
        assertTrue(Sight.unseen(Pose(gravityZ = 1f, near = true, lux = 2f)))
        assertTrue(Sight.unseen(Pose(gravityZ = 1f, near = true, lux = null)))
        // In the hand, upright: the front is free.
        assertFalse(Sight.unseen(Pose(gravityZ = 1f, near = false, lux = 2f)))
        // A finger over the sensor in a lit room is not a pocket.
        assertFalse(Sight.unseen(Pose(gravityZ = 1f, near = true, lux = 300f)))
        assertFalse(Sight.unseen(Pose(gravityZ = 1f)))
    }

    @Test
    fun `the gate hides only after a while and shows again quickly`() {
        val gate = HideGate(hideAfterMs = 1500, showAfterMs = 250)
        assertFalse(gate.update(true, 0))
        assertFalse(gate.update(true, 1400))
        assertTrue(gate.update(true, 1500))
        assertTrue(gate.update(false, 1600))
        assertFalse(gate.update(false, 1850))
    }

    @Test
    fun `a short moment unseen does not hide`() {
        val gate = HideGate(hideAfterMs = 1500)
        gate.update(true, 0)
        gate.update(false, 1000)
        assertFalse(gate.update(true, 1200))
        assertFalse(gate.update(true, 2600))
        assertTrue(gate.update(true, 2700))
    }

    /** Feeds [ms] of a constant [magnitude] at 50 Hz from [from]; true if a drop was reported. */
    private fun DropDetector.run(magnitude: Float, from: Long, ms: Long): Boolean {
        var hit = false
        var t = from
        while (t < from + ms) { hit = feed(magnitude, t) || hit; t += 20 }
        return hit
    }

    @Test
    fun `a fall and a hard hit is a drop, reported once`() {
        val d = DropDetector()
        assertFalse(d.run(9.8f, 0, 500))
        assertFalse(d.run(0.5f, 500, 300))
        assertTrue(d.feed(40f, 800))
        assertFalse(d.feed(40f, 820))
    }

    @Test
    fun `a soft landing, a short weightless moment of a shake or a late hit are not drops`() {
        val soft = DropDetector()
        soft.run(0.5f, 0, 300)
        assertFalse(soft.run(14f, 300, 1000))

        val shake = DropDetector()
        shake.run(1f, 0, 80)
        assertFalse(shake.feed(40f, 80))

        val late = DropDetector()
        late.run(0.5f, 0, 300)
        late.run(9.8f, 300, 900)
        assertFalse(late.feed(40f, 1200))
    }

    @Test
    fun `a second drop right after the first is ignored`() {
        val d = DropDetector(cooldownMs = 4000)
        d.run(0.5f, 0, 300)
        assertTrue(d.feed(40f, 300))
        d.run(0.5f, 400, 300)
        assertFalse(d.feed(40f, 700))
        d.run(0.5f, 5000, 300)
        assertTrue(d.feed(40f, 5300))
    }
}
