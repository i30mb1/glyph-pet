package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The 54 forms of [SpeciesForms]: well-formed, and every face, habit frame and charging frame is a valid frame. */
class FormsTest {

    private fun assertValid(what: String, f: IntArray) {
        assertEquals(625, f.size)
        for (i in f.indices) {
            assertTrue("$what: value ${f[i]}", f[i] in 0..4095)
            if (f[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} off the round matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    private val babiesAndAdults = SpeciesForms.all.filter { it.stage != Stage.TEEN }

    @Test
    fun `three forms per hero, the teens are the catalogue itself`() {
        assertEquals(54, SpeciesForms.all.size)
        assertEquals(54, SpeciesForms.all.map { it.id }.toSet().size)
        for (teen in SpeciesCatalog.all) {
            val forms = SpeciesForms.all(teen.id)
            assertEquals(Stage.entries, forms.map { it.stage })
            assertTrue(forms.all { it.kind == teen.id && it.name == teen.name && it.rarity == teen.rarity && it.inverted == teen.inverted })
            assertSame(teen, SpeciesForms.of(teen.id, Stage.TEEN))
            assertEquals(Stage.TEEN, teen.stage)
            assertEquals(teen.id, teen.kind)
            assertEquals("${teen.id}.baby", forms[0].id)
            assertEquals("${teen.id}.adult", forms[2].id)
            assertSame(forms[0], SpeciesForms.get("${teen.id}.baby"))
        }
    }

    @Test
    fun `babies and adults are plain sprites with their slots on them`() {
        for (s in babiesAndAdults) {
            assertTrue(s.id, s.rows.all { it.length == s.width })
            assertTrue(s.id, s.rows.all { row -> row.all { it in "#o." } })
            assertTrue(s.id, s.extras.isEmpty() && s.replaces.isEmpty() && s.tearFrom == null)
            assertTrue("${s.id} eyes", s.eyes.isNotEmpty())
            for ((ex, ey) in s.eyes) assertTrue("${s.id} eye $ex,$ey", ex >= 0 && ey >= 0 && ex + 1 < s.width && ey + 1 < s.height)
            s.mouth?.let { (mx, my) -> assertTrue("${s.id} mouth", mx >= 0 && my >= 0 && mx + 2 < s.width && my + 1 < s.height) }
            assertTrue("${s.id} home spot", s.ox >= 0 && s.oy >= 0 && s.ox + s.width <= 25 && s.oy + s.height <= 25)
            // Nothing of the plain pet at home is lost off the round matrix.
            val lost = mutableListOf<Pair<Int, Int>>()
            SpeciesRenderer.render(s, Face.IDLE, clipped = lost)
            assertTrue("${s.id} clipped at $lost", lost.isEmpty())
        }
    }

    @Test
    fun `babies fill up on the charger, adults charge the teen's way unless it is drawn on the teen`() {
        val teenOnly = setOf(ChargeWay.BAR, ChargeWay.STORM, ChargeWay.BEAM, ChargeWay.IRIS)
        for (teen in SpeciesCatalog.all) {
            assertEquals(ChargeWay.FILL, SpeciesForms.of(teen.id, Stage.BABY).charge)
            val adult = SpeciesForms.of(teen.id, Stage.ADULT).charge
            assertEquals(teen.id, if (teen.charge in teenOnly) ChargeWay.FILL else teen.charge, adult)
        }
    }

    @Test
    fun `habits by stage - the teen's own, a wiggle for babies, adults keep theirs only where it fits`() {
        for (teen in SpeciesCatalog.all) {
            assertEquals(teen.habit.id, Habits.forStage(teen.id, Stage.TEEN, teen.rows).id)
            assertEquals("wiggle", SpeciesForms.of(teen.id, Stage.BABY).habit.id)
            val adult = SpeciesForms.of(teen.id, Stage.ADULT).habit.id
            assertTrue("${teen.id}: $adult", adult == "wiggle" || adult == teen.habit.id)
        }
        assertEquals("vanish", SpeciesForms.of("ghost", Stage.ADULT).habit.id)
        assertEquals("light_show", SpeciesForms.of("ufo", Stage.ADULT).habit.id)
        assertEquals("wiggle", Habits.forStage("nope", Stage.TEEN, listOf("#")).id)
        for (s in babiesAndAdults) assertTrue("${s.id} ${s.habit.durationMs}", s.habit.durationMs in 1000L..1500L)
    }

    @Test
    fun `every face of every form renders inside the matrix`() {
        for (s in SpeciesForms.all) {
            for (face in Face.entries) for (blink in listOf(false, true)) {
                val grid = SpeciesRenderer.carve(s, face, blink = blink)
                assertEquals(s.height, grid.size)
                for (dx in PetEngine.walkRange(s)) for (lift in 0..2) for (step in 0 until 5) {
                    val f = SpeciesRenderer.render(
                        s, face, blink = blink, dx = dx, lift = lift, timeMs = step * SpeciesRenderer.TEAR_STEP_MS, hurt = step == 1,
                    )
                    assertValid("${s.id} $face dx=$dx lift=$lift step=$step", f)
                }
            }
            SpeciesRenderer.plasterSpot(s)
            SpeciesRenderer.thoughtSpot(s, 0)
        }
    }

    @Test
    fun `every habit frame of every form renders inside the matrix`() {
        for (s in SpeciesForms.all) for (dx in -4..4) for (f in s.habit.frames) for (face in listOf(Face.IDLE, Face.CONTENT, Face.HAPPY)) {
            assertValid("${s.id} habit dx=$dx", SpeciesRenderer.render(s, f.face ?: face, dx = dx, habit = f, lift = 1))
        }
    }

    @Test
    fun `every form charges inside the matrix`() {
        fun state(watts: Float, level: Float, t: Long, still: Boolean = false) =
            ChargeState(timeMs = t, spin = t * 0.012, pace = t * 0.003, watts = watts, level = level, still = still)
        for (s in SpeciesForms.all) {
            val states = buildList<Pair<ChargeState, Long?>> {
                for (level in listOf(0f, 0.37f, 1f)) for (watts in listOf(0f, 5f, 65f)) for (t in 0L..4000L step 480) add(state(watts, level, t) to null)
                add(state(18f, 0.6f, 0, still = true) to null)
                add(state(18f, 1f, 0, still = true) to null)
                add(state(18f, 0.6f, 0).copy(stand = false) to null)
                add(state(18f, 0.6f, 0).copy(reaction = Face.DIZZY) to null)
                add(state(18f, 0.6f, 0).copy(needy = true, need = Face.SAD) to null)
                for (plug in 0L..ChargeRenderer.PLUG_MS step 150) add(state(18f, 0.6f, 0) to plug)
            }
            for ((st, plug) in states) {
                val f = ChargeRenderer.render(s, st, plug)
                assertValid("${s.id} $st plug=$plug", f)
                assertTrue("${s.id} charging shows something", f.count { it > 0 } > 15)
            }
        }
    }

    @Test
    fun `the engine plays every form in every mood, with and without an egg, on and off the charger`() {
        for (s in SpeciesForms.all) {
            for (mood in Mood.entries) for (egg in listOf(null, EggView(EggStage.CRACKED, golden = true))) for (charging in listOf(false, true)) {
                val engine = PetEngine(Random(s.id.hashCode() + mood.ordinal))
                val scene = Scene(s, mood, charging = charging, egg = egg, watts = 20f, battery = 0.5f, hurt = mood == Mood.SAD)
                var t = 0L
                while (t < 6000) {
                    t += 80
                    when (t) {
                        800L -> engine.feed(accepted = true)
                        2000L -> engine.pet()
                        2800L -> engine.shake()
                        3600L -> engine.celebrate()
                        4400L -> engine.trick()
                        5200L -> engine.drop()
                    }
                    assertValid("${s.id} $mood egg=${egg != null} charging=$charging t=$t", engine.tick(80, scene.copy(tilt = if (t in 1000L..1500L) 0.8f else 0f)))
                }
                assertValid("${s.id} $mood still", engine.still(scene))
            }
            // Its habit plays when there is room for it.
            val engine = PetEngine(Random(3))
            var played = false
            var t = 0L
            while (t < 30_000 && !played) {
                t += 80
                engine.tick(80, Scene(s, Mood.CONTENT))
                played = engine.shown.habit
            }
            assertTrue("${s.id}: its habit never played", played || PetEngine.habitSpots(s).isEmpty())
        }
    }
}
