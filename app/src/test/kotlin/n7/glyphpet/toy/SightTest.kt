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
}
