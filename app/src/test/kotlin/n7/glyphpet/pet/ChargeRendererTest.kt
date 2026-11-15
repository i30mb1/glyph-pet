package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChargeRendererTest {

    private fun state(watts: Float = 18f, level: Float = 0.6f, still: Boolean = false, time: Long = 0) =
        ChargeState(timeMs = time, spin = time * 0.012, pace = time * 0.003, watts = watts, level = level, still = still)

    private fun lit(frame: IntArray) = frame.count { it > 0 }

    private fun rimLit(frame: IntArray) = ChargeRenderer.RING.count { (x, y) -> frame[y * 25 + x] > 0 }

    private fun charging(species: Species, watts: Float = 18f) =
        Scene(species, Mood.CONTENT, charging = true, watts = watts, battery = 0.5f)

    @Test
    fun `the rim is one closed loop of edge LEDs`() {
        assertEquals(68, ChargeRenderer.RING.size)
        assertEquals(ChargeRenderer.RING.size, ChargeRenderer.RING.toSet().size)
        assertEquals(12 to 0, ChargeRenderer.RING.first())
    }

    @Test
    fun `every species shows every charging state inside the round matrix`() {
        for (s in SpeciesCatalog.all) {
            val states = buildList<Pair<ChargeState, Long?>> {
                for (level in listOf(0f, 0.37f, 1f)) for (watts in listOf(0f, 5f, 65f)) for (t in 0L..4000L step 480) {
                    add(state(watts, level, time = t) to null)
                }
                add(state(still = true) to null)
                add(state(level = 1f, still = true) to null)
                add(state().copy(dx = -3, stand = false) to null)
                add(state().copy(reaction = Face.DIZZY) to null)
                add(state().copy(needy = true) to null)
                for (plug in 0L..ChargeRenderer.PLUG_MS step 150) add(state() to plug)
            }
            for ((st, plug) in states) {
                val frame = ChargeRenderer.render(s, st, plug)
                for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
                assertTrue("${s.id} $st plug=$plug", lit(frame) > 20)
            }
        }
    }

    @Test
    fun `the wheel rim grows with the battery and is complete at 100`() {
        val cat = SpeciesCatalog.get("cat")!!
        val low = rimLit(ChargeRenderer.render(cat, state(level = 0.2f, still = true)))
        val high = rimLit(ChargeRenderer.render(cat, state(level = 0.8f, still = true)))
        val full = rimLit(ChargeRenderer.render(cat, state(level = 1f)))
        assertTrue("$low < $high", low < high)
        assertEquals(ChargeRenderer.RING.size, full)
    }

    @Test
    fun `a filling pet gets brighter as the battery fills`() {
        val slime = SpeciesCatalog.get("slime")!!
        fun charged(level: Float) = ChargeRenderer.render(slime, state(level = level, still = true)).count { it == ChargeRenderer.FULL }
        assertTrue(charged(0.2f) < charged(0.5f))
        assertTrue(charged(0.5f) < charged(0.9f))
    }

    @Test
    fun `more watts change the picture more often`() {
        val cat = SpeciesCatalog.get("cat")!!
        fun changes(watts: Float): Int {
            val engine = PetEngine(Random(3))
            val frames = (0 until 60).map { engine.tick(80, charging(cat, watts)).toList() }
            return frames.zipWithNext().count { (a, b) -> a != b }
        }
        assertTrue(changes(65f) > changes(5f))
    }

    @Test
    fun `charging still frame is calm and an unplugged pet has no rim`() {
        val engine = PetEngine(Random(4))
        val fox = SpeciesCatalog.get("fox")!!
        assertEquals(engine.still(charging(fox)).toList(), engine.still(charging(fox)).toList())
        assertTrue(rimLit(engine.still(charging(fox))) > 20)
        assertEquals(0, rimLit(engine.still(Scene(fox, Mood.CONTENT))))
    }

    @Test
    fun `plugging in startles the pet for a moment`() {
        val engine = PetEngine(Random(5))
        val bunny = SpeciesCatalog.get("bunny")!!
        engine.tick(80, Scene(bunny, Mood.CONTENT))
        assertEquals(ChargeRenderer.RING.size, rimLit(engine.tick(80, charging(bunny))))
        repeat(40) { engine.tick(80, charging(bunny)) }
        assertFalse(rimLit(engine.tick(80, charging(bunny))) == ChargeRenderer.RING.size)
    }

    @Test
    fun `a pet fed on the charger eats without leaving its indicator`() {
        val engine = PetEngine(Random(6))
        val cat = SpeciesCatalog.get("cat")!!
        repeat(5) { engine.tick(80, charging(cat)) }
        engine.feed(accepted = true)
        val frame = engine.tick(80, charging(cat))
        assertTrue(rimLit(frame) > 20)
        // No food falls from the top: the upper rows hold only the rim.
        assertEquals(0, (0 until 25 * 5).count { frame[it] > 0 && (it % 25 to it / 25) !in ChargeRenderer.RING })
    }

    @Test
    fun `watts come from microamperes of either sign`() {
        assertEquals(18f, Power.watts(currentNow = 4_000_000, milliVolts = 4500), 0.01f)
        assertEquals(18f, Power.watts(currentNow = -4_000_000, milliVolts = 4500), 0.01f)
        assertEquals(Power.UNKNOWN_WATTS, Power.watts(currentNow = Int.MIN_VALUE, milliVolts = 4500), 0.01f)
    }
}
