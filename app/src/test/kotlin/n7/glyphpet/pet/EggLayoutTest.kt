package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The small egg in the corner and the pet in the middle: they never share an LED, and the pet still lives its life. */
class EggLayoutTest {

    private fun cells(frame: IntArray): Set<Pair<Int, Int>> = frame.indices.filter { frame[it] > 0 }.map { it % 25 to it / 25 }.toSet()

    private fun assertValid(what: String, f: IntArray) {
        assertEquals(625, f.size)
        for (i in f.indices) {
            assertTrue("$what: value ${f[i]}", f[i] in 0..4095)
            if (f[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} off the round matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    /** The egg as drawn on top of the pet: exactly one of its two pictures (upright or rocking), at [spot]. */
    private fun assertEggDrawn(what: String, engine: PetEngine, frame: IntArray, view: EggView, spot: EggSpot) {
        val egg = frame.indices.filter { frame[it] != engine.petLayer[it] }.map { it % 25 to it / 25 }.toSet()
        val pictures = listOf(false, true).map { tipped -> cells(IntArray(625).also { EggRenderer.mini(it, view, spot, tipped) }) }
        assertTrue("$what: egg drawn as $egg", egg in pictures)
    }

    @Test
    fun `the small egg sits in the bottom-right corner of the round outline, whole`() {
        val egg = EggRenderer.miniCells(EggLayout.EDGE)
        assertTrue(EggRenderer.fits(EggLayout.EDGE))
        assertTrue(EggRenderer.fits(EggLayout.CHARGER))
        // As low and as far right as it goes: one row lower or one column further right and it no longer fits.
        assertFalse(EggRenderer.fits(EggSpot(EggLayout.EDGE.left, EggLayout.EDGE.top + 1)))
        assertFalse(EggRenderer.fits(EggSpot(EggLayout.EDGE.left + 1, EggLayout.EDGE.top)))
        assertTrue(egg.all { (x, y) -> x > 12 && y > 12 })
    }

    @Test
    fun `every species keeps its home spot beside the egg, can walk, and has somewhere to play its habit`() {
        for (s in SpeciesCatalog.all) {
            val range = EggLayout.walkRange(s)
            assertTrue("${s.id} home", 0 in range)
            assertTrue("${s.id} walks: $range", range.count() >= 3)
            assertTrue("${s.id} inside its usual walk", range.first >= PetEngine.walkRange(s).first && range.last <= PetEngine.walkRange(s).last)
            val spots = EggLayout.habitSpots(s)
            assertTrue("${s.id} habit spots $spots", spots.isNotEmpty())
            assertTrue(spots.all { it in range && it in PetEngine.habitSpots(s) })
        }
    }

    @Test
    fun `away from home the pet keeps a dark LED between itself and the egg, but for the frog`() {
        val near = EggRenderer.miniCells(EggLayout.EDGE).flatMap { (x, y) -> (-1..1).flatMap { a -> (-1..1).map { b -> x + a to y + b } } }.toSet()
        val tight = SpeciesCatalog.all.filter { s -> EggLayout.walkRange(s).any { it != 0 && !EggLayout.clear(s, it, gap = 1) } }
        assertEquals(listOf("frog"), tight.map { it.id })
        for (s in SpeciesCatalog.all - tight.toSet()) for (dx in EggLayout.walkRange(s)) {
            if (dx == 0) continue
            for (face in Face.entries) {
                val hit = cells(SpeciesRenderer.render(s, face, dx = dx)).filter { it in near }
                assertTrue("${s.id} dx=$dx $face: $hit", hit.isEmpty())
            }
        }
    }

    @Test
    fun `in its walk no face, blink, hop, tear, shake, food or face extra of any species touches the egg`() {
        val egg = EggRenderer.miniCells(EggLayout.EDGE)
        for (s in SpeciesCatalog.all) for (dx in EggLayout.walkRange(s)) {
            for (face in Face.entries) for (blink in listOf(false, true)) for (lift in 0..2) for (step in 0 until 5) {
                val f = SpeciesRenderer.render(s, face, blink = blink, dx = dx, lift = lift, timeMs = step * SpeciesRenderer.TEAR_STEP_MS)
                val hit = cells(f).filter { it in egg }
                assertTrue("${s.id} dx=$dx $face blink=$blink lift=$lift step=$step: $hit", hit.isEmpty())
            }
        }
        for (s in SpeciesCatalog.all) for (dx in EggLayout.habitSpots(s)) for (f in s.habit.frames) {
            val hit = cells(SpeciesRenderer.render(s, f.face ?: Face.IDLE, dx = dx, habit = f)).filter { it in egg }
            assertTrue("${s.id} habit at dx=$dx: $hit", hit.isEmpty())
        }
    }

    @Test
    fun `long random runs with an egg in every stage - tilts, food, strokes, shakes, habits, hops - never put the pet on the egg`() {
        val moods = listOf(Mood.CONTENT, Mood.HAPPY, Mood.CONTENT, Mood.HAPPY, Mood.HUNGRY, Mood.SAD, Mood.TIRED, Mood.SICK, Mood.SLEEPING)
        val egg = EggRenderer.miniCells(EggLayout.EDGE)
        for (s in SpeciesCatalog.all) for (stage in EggStage.entries) {
            val r = Random(stage.ordinal * 977 + s.id.hashCode())
            val engine = PetEngine(Random(stage.ordinal + 3))
            val view = EggView(stage, golden = stage.ordinal % 2 == 1)
            var mood = Mood.CONTENT
            var moodNo = 0
            var tilt = 0f
            val spots = HashSet<Int>()
            var habits = 0
            var hops = 0
            repeat(2700) { step ->
                // The last 15 s are calm and happy (the habit and the hop must show beside the egg); before that, about
                // every 5 s a reaction, every 4 s another mood, every 6 s another tilt.
                if (step == 2500) { mood = Mood.HAPPY; tilt = 0f; engine.trick() }
                when (if (step >= 2500) -1 else r.nextInt(300)) {
                    0 -> engine.feed(r.nextBoolean())
                    1 -> engine.pet()
                    2 -> engine.shake()
                    3, 4 -> engine.trick()
                    5, 6 -> engine.crack()
                    7 -> engine.celebrate()
                    in 8..13 -> mood = moods[++moodNo % moods.size]
                    in 14..17 -> tilt = listOf(0f, 0f, 0f, 0f, 0.5f, -0.5f, 1f, -1f)[r.nextInt(8)]
                }
                val frame = engine.tick(listOf(16L, 80L, 80L, 80L, 120L)[r.nextInt(5)], Scene(s, mood, tilt = tilt, egg = view))
                val what = "${s.id} $stage step=$step $mood tilt=$tilt"
                assertValid(what, frame)
                val shared = cells(engine.petLayer).filter { it in egg }
                assertTrue("$what: pet on the egg at $shared", shared.isEmpty())
                assertEggDrawn(what, engine, frame, view, EggLayout.EDGE)
                spots += engine.shown.dx
                if (engine.shown.habit) habits++
                if (engine.shown.lift > 0) hops++
            }
            assertTrue("${s.id} $stage: the pet walks while the egg waits, stood at $spots", spots.size >= 3)
            assertTrue("${s.id} $stage: no habit played", habits > 0)
            assertTrue("${s.id} $stage: never hopped", hops > 0)
        }
    }

    @Test
    fun `a habit stops if an egg turns up where it would touch it`() {
        for (s in SpeciesCatalog.all) {
            val far = PetEngine.habitSpots(s).filter { it !in EggLayout.habitSpots(s) }
            if (far.isEmpty()) continue
            val engine = PetEngine(Random(9), autoHabits = false)
            // Walk to a spot where the habit would touch the egg: slide there, then start the trick.
            var guard = 0
            while (engine.shown.dx != far.last() && guard++ < 200) engine.tick(80, Scene(s, Mood.CONTENT, tilt = if (far.last() > 0) 1f else -1f))
            if (engine.shown.dx != far.last()) continue
            engine.trick()
            engine.tick(80, Scene(s, Mood.CONTENT))
            if (!engine.shown.habit) continue
            val egg = EggRenderer.miniCells(EggLayout.EDGE)
            repeat(20) {
                engine.tick(80, Scene(s, Mood.CONTENT, egg = EggView(EggStage.WHOLE, false)))
                assertTrue(s.id, cells(engine.petLayer).none { it in egg })
            }
        }
    }

    @Test
    fun `on the charger the egg keeps off the rim and clear of all six indicators`() {
        assertTrue(EggRenderer.miniCells(EggLayout.CHARGER).none { it in ChargeRenderer.RING })
        assertEquals(ChargeWay.entries.toSet(), SpeciesCatalog.all.map { it.charge }.toSet())
        val egg = EggRenderer.miniCells(EggLayout.CHARGER)
        for (s in SpeciesCatalog.all) {
            assertTrue("${s.id} ${s.charge}", EggLayout.chargeClear(s))
            // And as the engine plays it: plugged in, every speed and level, fed, stroked, shaken, asleep, pressed.
            val r = Random(s.id.hashCode())
            val engine = PetEngine(Random(1))
            engine.tick(80, Scene(s, Mood.CONTENT))
            var mood = Mood.CONTENT
            repeat(1200) { step ->
                when (r.nextInt(40)) {
                    0 -> engine.feed(r.nextBoolean())
                    1 -> engine.pet()
                    2 -> engine.shake()
                    3 -> engine.crack()
                    4 -> mood = listOf(Mood.CONTENT, Mood.HUNGRY, Mood.SAD, Mood.SICK, Mood.SLEEPING)[r.nextInt(5)]
                }
                val view = EggView(EggStage.entries[step % 4], step % 3 == 0)
                val scene = Scene(s, mood, charging = step % 300 > 5, watts = (step % 7) * 10f, battery = (step % 11) / 10f, egg = view)
                val frame = engine.tick(80, scene)
                val what = "${s.id} ${s.charge} step=$step"
                assertValid(what, frame)
                val spot = EggLayout.spot(scene.charging)
                val shared = cells(engine.petLayer).filter { it in EggRenderer.miniCells(spot) }
                assertTrue("$what: on the egg at $shared", shared.isEmpty())
                if (scene.charging) assertTrue(what, cells(engine.petLayer).none { it in egg })
            }
            // Always-on on the charger.
            for (mood2 in Mood.entries) {
                val e = PetEngine()
                e.still(Scene(s, mood2, charging = true, watts = 20f, battery = 0.4f, egg = EggView(EggStage.CRACKED, true)))
                assertTrue("${s.id} still $mood2", cells(e.petLayer).none { it in egg })
            }
        }
    }

    @Test
    fun `Always-on shows the pet at home and the egg in its corner, apart`() {
        val egg = EggRenderer.miniCells(EggLayout.EDGE)
        for (s in SpeciesCatalog.all) for (mood in Mood.entries) for (stage in EggStage.entries) {
            val e = PetEngine()
            val f = e.still(Scene(s, mood, egg = EggView(stage, false)))
            assertValid("${s.id} $mood $stage", f)
            assertTrue("${s.id} $mood", cells(e.petLayer).none { it in egg })
            assertEquals("the pet stands at home", SpeciesRenderer.render(s, moodStill(s, mood)).toList(), e.petLayer.toList())
        }
    }

    private fun moodStill(s: Species, mood: Mood): Face = when (mood) {
        Mood.HAPPY -> Face.HAPPY
        Mood.TIRED -> if (SpeciesRenderer.showsMouth(s)) Face.YAWN else Face.TIRED
        else -> PetEngine.moodFace(mood)
    }

    @Test
    fun `a press shows at once - the small egg rocks, the big one wobbles`() {
        for (stage in EggStage.entries) for (golden in listOf(false, true)) {
            val view = EggView(stage, golden)
            for (species in listOf(null, SpeciesCatalog.get("cat"))) {
                val engine = PetEngine(Random(2), autoHabits = false)
                val scene = Scene(species, Mood.SLEEPING, egg = view)
                // Past any idle wobble, so the egg is at rest.
                var rest = engine.tick(80, scene)
                var guard = 0
                while (guard++ < 200) {
                    val next = engine.tick(80, scene)
                    if (next.contentEquals(rest)) break
                    rest = next
                }
                engine.crack()
                val frames = (0 until 7).map { engine.tick(80, scene) }
                assertTrue("$stage golden=$golden pet=${species?.id}", frames.any { !it.contentEquals(rest) })
                assertFalse("it rocks, it does not just change once", frames.all { it.contentEquals(frames[0]) })
            }
        }
    }

    @Test
    fun `the big egg alone shows each stage in the middle`() {
        val looks = EggStage.entries.map { st -> PetEngine().still(Scene(null, Mood.CONTENT, egg = EggView(st, false))).toList() }
        assertEquals(4, looks.toSet().size)
        for (l in looks) {
            val lit = l.indices.filter { l[it] > 0 }
            val cx = lit.sumOf { it % 25 } / lit.size.toDouble()
            val cy = lit.sumOf { it / 25 } / lit.size.toDouble()
            assertEquals(12.0, cx, 1.0)
            assertEquals(12.0, cy, 1.5)
        }
    }
}
