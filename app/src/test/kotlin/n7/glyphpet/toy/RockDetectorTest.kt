package n7.glyphpet.toy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RockDetectorTest {

    /** Feeds a sideways swing: [swings] half-periods of [halfMs] each, leaning [lean] each way, at 50 readings a second. */
    private fun RockDetector.swing(swings: Int, halfMs: Long, lean: Float, force: Float = 1f, startMs: Long = 0): Int {
        var rocks = 0
        var t = startMs
        for (i in 0 until swings) {
            val sign = if (i % 2 == 0) 1 else -1
            var k = 0L
            while (k < halfMs) {
                if (feed(sign * lean, force, t)) rocks++
                k += 20; t += 20
            }
        }
        return rocks
    }

    @Test
    fun `a gentle rock left and right plays once`() {
        // Four changes of side (RockDetector.SWINGS), the first swing only setting the side.
        assertEquals(1, RockDetector().swing(swings = RockDetector.SWINGS + 1, halfMs = 500, lean = 0.3f))
        assertEquals("three changes of side are not enough", 0, RockDetector().swing(swings = RockDetector.SWINGS, halfMs = 500, lean = 0.3f))
    }

    @Test
    fun `holding the phone tilted to one side is no rock`() {
        val d = RockDetector()
        var rocks = 0
        for (t in 0L until 5000L step 20) if (d.feed(0.5f, 1f, t)) rocks++
        assertEquals(0, rocks)
    }

    @Test
    fun `small wobbles of the hand are no rock`() {
        assertEquals(0, RockDetector().swing(swings = 10, halfMs = 300, lean = 0.1f))
    }

    @Test
    fun `swings too slow for the window are no rock`() {
        assertEquals(0, RockDetector().swing(swings = RockDetector.SWINGS + 1, halfMs = 1600, lean = 0.4f))
    }

    @Test
    fun `shaking is no rock`() {
        assertEquals(0, RockDetector().swing(swings = 8, halfMs = 200, lean = 0.4f, force = 12f))
    }

    @Test
    fun `rocking on keeps playing, but not faster than the cooldown`() {
        // 20 swings of 0.5 s: 10 s of rocking.
        val rocks = RockDetector().swing(swings = 20, halfMs = 500, lean = 0.3f)
        assertEquals(4, rocks)
    }

    @Test
    fun `a jolt in the middle starts the count over`() {
        val d = RockDetector()
        assertEquals(0, d.swing(swings = 3, halfMs = 500, lean = 0.3f))
        assertFalse(d.feed(0f, 12f, 1500))
        assertEquals(0, d.swing(swings = 3, halfMs = 500, lean = 0.3f, startMs = 1520))
    }
}
