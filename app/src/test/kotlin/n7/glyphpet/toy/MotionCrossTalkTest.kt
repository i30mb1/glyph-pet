package n7.glyphpet.toy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One motion of the phone, read by several detectors at once: a knock must not be a drop, a shake must not be a rock.
 * The readings are synthetic, at the 100 Hz the accelerometer really runs at ([MotionSensor.ACCEL_PERIOD_US]).
 */
class MotionCrossTalkTest {

    private val gravity = 9.81f
    private val step = 10L

    /** Quiet: the high-passed acceleration near zero, the whole vector at gravity. */
    private fun KnockDetector.quiet(from: Long, ms: Long, high: Float = 0.02f): Int {
        var knocks = 0
        var t = from
        while (t < from + ms) {
            knocks += feed(high, gravity, step / 1000f, t)
            t += step
        }
        return knocks
    }

    /** One tap: a spike of [peak] lasting [spikeMs], then quiet again for [afterMs]. Returns what it was read as. */
    private fun KnockDetector.tap(from: Long, peak: Float, spikeMs: Long = 30, afterMs: Long = 200, magnitude: Float = gravity): Int {
        var knocks = 0
        var t = from
        while (t < from + spikeMs) {
            knocks += feed(peak, magnitude, step / 1000f, t)
            t += step
        }
        knocks += quiet(t, afterMs)
        return knocks
    }

    // --- A knock, and what is not one ----------------------------------------------------------------

    @Test
    fun `a light finger tap on the back is a knock`() {
        // Measured on the phone: a light tap reads 0.47 to 0.69, over MIN_PEAK.
        for (peak in listOf(0.47f, 0.69f, 3f)) {
            val d = KnockDetector()
            d.quiet(0, 1000)
            assertEquals("peak $peak", 1, d.tap(1000, peak, afterMs = KnockDetector.DOUBLE_MS + 200))
        }
    }

    @Test
    fun `typing on the same desk is no knock`() {
        val d = KnockDetector()
        d.quiet(0, 1000)
        // Below MIN_PEAK, over and over.
        var knocks = 0
        var t = 1000L
        repeat(20) {
            knocks += d.tap(t, 0.3f, spikeMs = 20, afterMs = 120)
            t += 140
        }
        assertEquals(0, knocks)
    }

    @Test
    fun `two taps close together are one double knock, further apart two single ones`() {
        val close = KnockDetector()
        close.quiet(0, 1000)
        var doubles = 0
        doubles += close.tap(1000, 3f, afterMs = 150)
        doubles += close.tap(1300, 3f, afterMs = KnockDetector.DOUBLE_MS + 200)
        assertEquals("a pair inside DOUBLE_MS", 2, doubles)

        val apart = KnockDetector()
        apart.quiet(0, 1000)
        var singles = 0
        singles += apart.tap(1000, 3f, afterMs = KnockDetector.DOUBLE_MS + 200)
        singles += apart.tap(2500, 3f, afterMs = KnockDetector.DOUBLE_MS + 200)
        assertEquals("two on their own", 2, singles)
    }

    @Test
    fun `free fall in the middle of a spike is no knock`() {
        val d = KnockDetector()
        d.quiet(0, 1000)
        // The spike of a fall: the whole vector drops below FREE_FALL while the high-passed reading is high.
        assertEquals(0, d.tap(1000, 3f, magnitude = KnockDetector.FREE_FALL - 1f))
    }

    @Test
    fun `a hit far harder than a tap is no knock`() {
        val d = KnockDetector()
        d.quiet(0, 1000)
        assertEquals(0, d.tap(1000, KnockDetector.MAX_PEAK + 5f, afterMs = KnockDetector.DOUBLE_MS + 200))
    }

    @Test
    fun `a phone that keeps ringing after the spike is being handled, not knocked`() {
        val d = KnockDetector()
        d.quiet(0, 1000)
        var knocks = 0
        var t = 1000L
        // The spike, then no quiet at all: the reading stays high.
        while (t < 1400) {
            knocks += d.feed(3f, gravity, step / 1000f, t)
            t += step
        }
        knocks += d.quiet(t, KnockDetector.DOUBLE_MS + 200)
        assertEquals(0, knocks)
    }

    @Test
    fun `after a rejected spike the quiet must come back before a knock counts again`() {
        val d = KnockDetector()
        d.quiet(0, 1000)
        // Handled: a long shove, which raises the background to its own peak.
        var t = 1000L
        while (t < 1600) {
            d.feed(6f, gravity, step / 1000f, t)
            t += step
        }
        // A tap right away, before the background has settled: not taken.
        assertEquals(0, d.tap(t, 3f, afterMs = KnockDetector.DOUBLE_MS + 200))
        // Once it is quiet again, it is.
        d.quiet(t + 1000, 3000)
        assertEquals(1, d.tap(t + 4000, 3f, afterMs = KnockDetector.DOUBLE_MS + 200))
    }

    /** Feeds [ms] of a background trembling at [high] from [from]: the phone still ringing, or a busy desk. */
    private fun KnockDetector.ringing(from: Long, ms: Long, high: Float): Int = quiet(from, ms, high)

    @Test
    fun `the second knock of a pair is taken through a background that would stop a first one`() {
        // A background just under MIN_PEAK settles `calm` over QUIET_BEFORE, which is what stops a first knock.
        val busy = 0.35f

        val first = KnockDetector()
        first.quiet(0, 1000)
        first.ringing(1000, 1000, busy)
        assertEquals("a first knock needs a calm moment before it", 0, first.tap(2000, 3f, afterMs = KnockDetector.DOUBLE_MS + 200))

        val pair = KnockDetector()
        pair.quiet(0, 1000)
        var out = pair.tap(1000, 3f, afterMs = 120)
        assertEquals("the first of the pair waits for its partner", 0, out)
        out += pair.ringing(1120, 200, busy)
        out += pair.tap(1320, 3f, afterMs = KnockDetector.DOUBLE_MS + 200)
        assertEquals("a double knock through the ringing", 2, out)
    }

    @Test
    fun `the knock tells the toy when it confirmed, so a shake of the same spike can stand down`() {
        // MotionSensor holds a shake back for SHAKE_CONFIRM_MS to see whether the knock detector takes the same spike.
        val d = KnockDetector()
        d.quiet(0, 1000)
        assertTrue(d.confirmedAt < 0)
        d.tap(1000, 3f, afterMs = KnockDetector.DOUBLE_MS + 200)
        assertTrue("confirmed at ${d.confirmedAt}", d.confirmedAt in 1000..1400)
    }

    // --- A rock, and what is not one -----------------------------------------------------------------

    /** Feeds a sideways swing: [swings] half-periods of [halfMs], leaning [lean] each way, at 50 readings a second. */
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
    fun `shaking the phone is no game of ball`() {
        // A shake is a jolt over MAX_FORCE; it clears the swing count instead of adding to it.
        assertEquals(0, RockDetector().swing(swings = 8, halfMs = 200, lean = 0.4f, force = RockDetector.MAX_FORCE + 1f))
    }

    @Test
    fun `a drop in the middle of a rock starts the swinging over`() {
        val d = RockDetector()
        assertEquals(0, d.swing(swings = RockDetector.SWINGS, halfMs = 400, lean = 0.3f))
        // The hit of a drop, far over MAX_FORCE.
        assertFalse(d.feed(0f, 40f, 1600))
        assertEquals("the swings before it do not count", 0, d.swing(swings = RockDetector.SWINGS, halfMs = 400, lean = 0.3f, startMs = 1620))
    }

    // --- A drop, and what is not one -----------------------------------------------------------------

    /** Feeds [ms] of a constant [magnitude] at 50 Hz from [from]; true if a drop was reported. */
    private fun DropDetector.run(magnitude: Float, from: Long, ms: Long): Boolean {
        var hit = false
        var t = from
        while (t < from + ms) { hit = feed(magnitude, t) || hit; t += 20 }
        return hit
    }

    @Test
    fun `the weightless moments of a shake are far too short to be a fall`() {
        val d = DropDetector()
        // A shake throws the phone about: near weightless for a moment, then a hard reading, over and over.
        var dropped = false
        var t = 0L
        repeat(10) {
            dropped = d.run(0.5f, t, 60) || dropped
            dropped = d.run(30f, t + 60, 60) || dropped
            t += 120
        }
        assertFalse(dropped)
    }

    @Test
    fun `a knock on the back of a phone lying still is no drop`() {
        val d = DropDetector()
        d.run(gravity, 0, 1000)
        // The tap itself: a short reading over gravity, with no fall before it.
        assertFalse(d.run(14f, 1000, 40))
        assertFalse(d.run(gravity, 1040, 500))
    }

    // --- A twirl -------------------------------------------------------------------------------------

    /** Turns the phone at [rate] rad/s for [ms] from [from], at 50 readings a second; how many twirls were reported. */
    private fun TwirlDetector.turn(rate: Float, from: Long, ms: Long): Int {
        var twirls = 0
        var t = from
        while (t < from + ms) {
            if (feed(rate, 0.02f, t)) twirls++
            t += 20
        }
        return twirls
    }

    @Test
    fun `turning the phone round twice on the desk is a twirl, once is not`() {
        val two = TwirlDetector()
        // Two full turns at FAST + a little: 4*PI radians.
        assertEquals(1, two.turn(3f, 0, (4 * Math.PI / 3 * 1000).toLong() + 100))

        val one = TwirlDetector()
        assertEquals(0, one.turn(3f, 0, (2 * Math.PI / 3 * 1000).toLong()))
    }

    @Test
    fun `a slow turn of the phone in the hand is no twirl`() {
        // Below FAST, however long it goes on: the bucket leaks faster than it fills.
        assertEquals(0, TwirlDetector().turn(TwirlDetector.FAST - 0.5f, 0, 20_000))
    }

    @Test
    fun `turning on and on reports a twirl no faster than its cooldown`() {
        val d = TwirlDetector()
        val twirls = d.turn(6f, 0, 10_000)
        // 10 s of fast turning: a twirl at most every COOLDOWN_MS.
        assertTrue("$twirls twirls in 10 s", twirls <= 10_000 / TwirlDetector.COOLDOWN_MS + 1)
        assertTrue("$twirls twirls in 10 s", twirls >= 2)
    }
}
