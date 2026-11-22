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
    fun `every form shows every charging state inside the round matrix`() {
        for (s in SpeciesForms.sprites) {
            val states = buildList<Pair<ChargeState, Long?>> {
                for (level in listOf(0f, 0.37f, 1f)) for (watts in listOf(0f, 5f, 65f)) for (t in 0L..4000L step 480) {
                    add(state(watts, level, time = t) to null)
                }
                add(state(still = true) to null)
                add(state(level = 1f, still = true) to null)
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
    fun `every teen and adult has its own charging, and it shows more as the battery fills`() {
        for (s in SpeciesForms.sprites.filter { it.stage != Stage.BABY }) {
            val own = KindCharge.way(s) != null || KindCharge2.way(s) != null ||
                (s.charge != ChargeWay.WHEEL && s.charge != ChargeWay.FILL)
            assertTrue("${s.id} shares the wheel or the fill", own)
            // The newest ways, placed from the sprite's box: the more charge, the more of the indicator lights.
            if (KindCharge2.way(s) == null) continue
            fun shown(level: Float) = ChargeRenderer.render(s, state(watts = 0f, level = level)).sum()
            assertTrue("${s.id}: ${shown(0.2f)} < ${shown(0.9f)}", shown(0.2f) < shown(0.9f))
        }
    }

    @Test
    fun `a filling baby gets brighter as the battery fills`() {
        val slime = SpeciesForms.of("slime", Stage.BABY)
        fun charged(level: Float) = ChargeRenderer.render(slime, state(level = level, still = true)).count { it == ChargeRenderer.FULL }
        assertTrue(charged(0.2f) < charged(0.5f))
        assertTrue(charged(0.5f) < charged(0.9f))
    }

    @Test
    fun `more watts beat faster`() {
        val cat = SpeciesCatalog.get("cat")!!
        // The charging speed is the rhythm, not the number of frames that differ: a pulse that eases up and down
        // changes every frame at any speed. Counted as the peaks of the whole picture's light.
        fun beats(watts: Float): Int {
            val engine = PetEngine(Random(3))
            val light = (0 until 120).map { engine.tick(80, charging(cat, watts)).sum() }
            return light.windowed(3).count { (a, b, c) -> b > a && b >= c }
        }
        assertTrue(beats(65f) > beats(5f))
    }

    @Test
    fun `charging still frame is calm and differs from an unplugged pet`() {
        val engine = PetEngine(Random(4))
        val fox = SpeciesCatalog.get("fox")!!
        assertEquals(engine.still(charging(fox)).toList(), engine.still(charging(fox)).toList())
        assertTrue(engine.still(charging(fox)).toList() != engine.still(Scene(fox, Mood.CONTENT)).toList())
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
    fun `a pet fed on the charger stays on its charging picture`() {
        val engine = PetEngine(Random(6))
        val cat = SpeciesCatalog.get("cat")!!
        repeat(5) { engine.tick(80, charging(cat)) }
        engine.feed(accepted = true)
        engine.tick(80, charging(cat))
        assertTrue(engine.shown.charging)
    }

    @Test
    fun `watts come from microamperes of either sign`() {
        assertEquals(18f, Power.watts(currentNow = 4_000_000, milliVolts = 4500), 0.01f)
        assertEquals(18f, Power.watts(currentNow = -4_000_000, milliVolts = 4500), 0.01f)
        assertEquals(Power.UNKNOWN_WATTS, Power.watts(currentNow = Int.MIN_VALUE, milliVolts = 4500), 0.01f)
    }
}
