package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetEngineTest {

    private fun lit(frame: IntArray) = frame.count { it > 0 }

    @Test
    fun `every species draws every face inside the round matrix`() {
        for (s in SpeciesCatalog.sprites) {
            assertTrue("${s.id} rows have equal width", s.rows.all { it.length == s.width })
            for (face in Face.entries) {
                val frame = SpeciesRenderer.render(s, face)
                for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
                assertTrue("${s.id} $face is visible", lit(frame) > 20)
            }
        }
    }

    @Test
    fun `species keep most of their body on screen`() {
        for (s in SpeciesCatalog.sprites) {
            val cells = SpeciesRenderer.silhouette(s)
            val visible = cells.count { (x, y) -> GlyphMatrixSpec.isVisible(x, y) }
            assertTrue("${s.id}: $visible of ${cells.size}", visible >= cells.size * 0.9)
        }
    }

    @Test
    fun `faces differ from each other`() {
        val cat = SpeciesCatalog.get("cat")!!
        val idle = SpeciesRenderer.render(cat, Face.IDLE).toList()
        for (face in Face.entries - Face.IDLE) assertTrue(face.name, SpeciesRenderer.render(cat, face).toList() != idle)
    }

    @Test
    fun `wandering pet never leaves the matrix`() {
        val engine = PetEngine(Random(1))
        for (s in SpeciesCatalog.sprites) {
            repeat(200) {
                val frame = engine.tick(80, Scene(s, Mood.HAPPY, tilt = if (it % 60 < 30) 1f else -1f))
                for (i in frame.indices) if (frame[i] > 0) assertTrue(GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
        }
    }

    @Test
    fun `an egg on the matrix is drawn big, and the pet is away`() {
        val egg = EggView(EggStage.WHOLE, golden = false)
        val alone = PetEngine().still(Scene(null, Mood.CONTENT, egg = egg))
        val withPet = PetEngine().still(Scene(SpeciesCatalog.get("ghost"), Mood.CONTENT, egg = egg))
        assertTrue(lit(alone) > 50)
        assertTrue(alone.contentEquals(withPet))
    }

    private fun differing(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }

    @Test
    fun `hungry and sad look clearly different on every species, live and on Always-on`() {
        for (s in SpeciesCatalog.sprites) {
            val idle = SpeciesRenderer.render(s, Face.IDLE)
            val hungry = SpeciesRenderer.render(s, Face.HUNGRY)
            val sad = SpeciesRenderer.render(s, Face.SAD)
            assertTrue("${s.id} hungry vs sad", differing(hungry, sad) >= 12)
            assertTrue("${s.id} hungry vs idle", differing(hungry, idle) >= 12)
            assertTrue("${s.id} sad vs idle", differing(sad, idle) >= 4)
            val stillHungry = PetEngine().still(Scene(s, Mood.HUNGRY))
            val stillSad = PetEngine().still(Scene(s, Mood.SAD))
            assertTrue("${s.id} stills", differing(stillHungry, stillSad) >= 12)
        }
    }

    @Test
    fun `a happy face differs from a calm one on every species, mouth or not`() {
        for (s in SpeciesCatalog.sprites) {
            assertTrue(s.id, differing(SpeciesRenderer.render(s, Face.CONTENT), SpeciesRenderer.render(s, Face.IDLE)) >= 2)
        }
    }

    @Test
    fun `every species has room for its food thought wherever it walks`() {
        for (s in SpeciesCatalog.sprites) {
            val room = ((19 - s.width) / 2).coerceIn(0, 4)
            for (dx in -room..room) assertTrue("${s.id} dx=$dx", SpeciesRenderer.thoughtSpot(s, dx) != null)
        }
    }

    @Test
    fun `the tear runs down`() {
        for (s in SpeciesCatalog.sprites) {
            // Not every kind cries one tear: the rest are sad their own way ([MoodSigns]), checked by changing too.
            val frames = (0 until 5).map { SpeciesRenderer.render(s, Face.SAD, timeMs = it * SpeciesRenderer.TEAR_STEP_MS).toList() }
            if (MoodSigns.sad(s.kind) == MoodSigns.Sad.TEAR) assertEquals(s.id, 5, frames.toSet().size)
            else assertTrue(s.id, frames.toSet().size >= 2)
        }
    }

    /** Lit or not and how bright, as the app's GlyphMatrix draws a frame. */
    private fun appLook(frame: IntArray): Map<Pair<Int, Int>, Float> = PetEngine.toCells(frame).associate { (it.x to it.y) to it.level }

    @Test
    fun `the tear runs down in the app's look too`() {
        for (s in SpeciesCatalog.sprites) {
            val frames = (0 until 5).map { appLook(SpeciesRenderer.render(s, Face.SAD, timeMs = it * SpeciesRenderer.TEAR_STEP_MS)) }
            if (MoodSigns.sad(s.kind) == MoodSigns.Sad.TEAR) assertEquals(s.id, 5, frames.toSet().size)
            else assertTrue(s.id, frames.toSet().size >= 2)
            // and a crying pet differs from a calm one by more than its eyes and mouth (the one-eyed dino: 5)
            val idle = appLook(SpeciesRenderer.render(s, Face.IDLE))
            val sad = appLook(SpeciesRenderer.render(s, Face.SAD))
            val differ = (idle.keys + sad.keys).count { idle[it] != sad[it] }
            assertTrue("${s.id}: $differ", differ >= 5)
        }
    }

    @Test
    fun `the peek opens an eye that shows, not only the Z going away`() {
        for (s in SpeciesCatalog.sprites) {
            val sleep = SpeciesRenderer.render(s, Face.SLEEP)
            val peek = SpeciesRenderer.render(s, Face.PEEK)
            // Only the pet itself: the Z floats above it.
            val onPet = (s.oy * 25 until 625).filter { sleep[it] != peek[it] }
            val step = onPet.maxOfOrNull { kotlin.math.abs(peek[it] - sleep[it]) } ?: 0
            assertTrue("${s.id}: ${onPet.size} LEDs, step $step", onPet.size >= 3 && step >= 2000)
        }
    }

    @Test
    fun `Always-on stills of two moods never look alike`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesCatalog.sprites) for (egg in listOf<EggView?>(null)) {
            val stills = Mood.entries.associateWith { PetEngine().still(Scene(s, it, egg = egg)) }
            for ((a, fa) in stills) for ((b, fb) in stills) {
                if (a.ordinal >= b.ordinal) continue
                // The closest pairs left: a one-eyed or mouthless pet's tear (5-6 LEDs) and a yawn (5: the chameleon,
                // one eye and a mouth at the very edge of its head; 7 for a pet with two).
                val least = if (Mood.CONTENT in listOf(a, b) && (Mood.HAPPY in listOf(a, b) || Mood.TIRED in listOf(a, b))) {
                    if (s.eyes.size < 2) 5 else 7
                } else {
                    5
                }
                if (differing(fa, fb) < least) problems += "${s.id} egg=${egg != null}: $a vs $b ${differing(fa, fb)}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `on the charger Always-on shows the sleeping pet and its charge, whatever the mood, as in 1,1`() {
        for (s in SpeciesCatalog.sprites) {
            fun still(mood: Mood) = PetEngine().still(Scene(s, mood, charging = true, watts = 20f, battery = 0.5f)).toList()
            val asleep = still(Mood.SLEEPING)
            for (mood in Mood.entries) assertEquals("${s.id} $mood", asleep, still(mood))
        }
    }

    @Test
    fun `a happy pet shows its own joy soon, then every 10 to 20 seconds, a calm one never does`() {
        val cat = SpeciesCatalog.get("cat")!!
        fun hops(mood: Mood): List<Long> {
            val engine = PetEngine(Random(11), autoHabits = false)
            val starts = mutableListOf<Long>()
            var wasUp = false
            for (t in 1..750) {
                engine.tick(80, Scene(cat, mood))
                val up = engine.shown.cheer
                if (up && !wasUp) starts += t * 80L
                wasUp = up
            }
            return starts
        }
        val happy = hops(Mood.HAPPY)
        // The first burst comes within a few seconds; then they are spaced like a habit. A burst waits for the
        // signature effect, which has the whole matrix, so a gap across one is longer (PetEngine.updateHop).
        val signature = Signatures.of(cat.kind)?.durationMs ?: 0L
        assertTrue("first at ${happy.firstOrNull()}", (happy.firstOrNull() ?: 99_999) <= 3500)
        assertTrue("${happy.size} bursts in 60 s", happy.size in 3..7)
        happy.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b - a}", b - a in 10_000L..(22_000L + signature)) }
        assertTrue(hops(Mood.CONTENT).isEmpty())
    }

    @Test
    fun `a sleeper opens one eye for a second when stroked, shaken or fed, then sleeps on`() {
        val owl = SpeciesCatalog.get("owl")!!
        val asleep = Scene(owl, Mood.SLEEPING)
        for (poke in listOf<(PetEngine) -> Unit>({ it.pet() }, { it.shake() }, { it.feed(accepted = true) })) {
            val engine = PetEngine(Random(12))
            repeat(5) { engine.tick(80, asleep) }
            assertEquals(Face.SLEEP, engine.shown.face)
            val sleeping = engine.tick(80, asleep)
            poke(engine)
            val peek = engine.tick(80, asleep)
            assertEquals(Face.PEEK, engine.shown.face)
            assertTrue("one eye open", differing(peek, sleeping) > 0)
            var peekMs = 80L
            while (engine.shown.face == Face.PEEK) { engine.tick(80, asleep); peekMs += 80 }
            assertTrue("peek lasted $peekMs ms", peekMs in 900L..1200L)
            assertEquals(Face.SLEEP, engine.shown.face)
            repeat(30) { engine.tick(80, asleep); assertEquals(Face.SLEEP, engine.shown.face) }
        }
    }

    @Test
    fun `a sleeper on the charger peeks too`() {
        val engine = PetEngine(Random(13))
        val fox = SpeciesCatalog.get("fox")!!
        val scene = Scene(fox, Mood.SLEEPING, charging = true, watts = 10f, battery = 0.5f)
        val before = engine.tick(80, scene)
        engine.pet()
        assertTrue(differing(engine.tick(80, scene), before) > 0)
    }

    @Test
    fun `fed, every kind plays its own meal to the end`() {
        for (s in SpeciesCatalog.sprites) {
            val engine = PetEngine(Random(2), autoHabits = false, autoSignatures = false)
            val before = engine.tick(80, Scene(s, Mood.CONTENT))
            engine.feed(accepted = true)
            var eating = 0L
            var changed = false
            var t = 0L
            while (t < 6000) {
                val f = engine.tick(80, Scene(s, Mood.CONTENT))
                if (engine.shown.face == Face.EAT) { eating += 80; if (!f.contentEquals(before)) changed = true }
                t += 80
            }
            val meal = Feasts.of(s.kind)!!.durationMs
            assertTrue("${s.id}: ate for $eating ms of $meal", eating in meal - 160..meal + 160)
            assertTrue(s.id, changed)
            assertTrue(s.id, engine.shown.face != Face.EAT)
        }
    }
}
