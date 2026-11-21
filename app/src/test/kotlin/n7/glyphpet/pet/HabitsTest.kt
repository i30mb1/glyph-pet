package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HabitsTest {

    private fun assertInside(what: String, frame: IntArray) {
        for (i in frame.indices) {
            assertTrue("$what: brightness ${frame[i]}", frame[i] in 0..4095)
            if (frame[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} is off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    /** Runs [ms] of engine time and returns how many frames showed the habit. */
    private fun habitFrames(engine: PetEngine, ms: Long, scene: (Long) -> Scene, each: (Long, PetEngine) -> Unit = { _, _ -> }): Int {
        var n = 0
        var t = 0L
        while (t < ms) {
            t += 80
            each(t, engine)
            assertInside("t=$t", engine.tick(80, scene(t)))
            if (engine.shown.habit) n++
        }
        return n
    }

    @Test
    fun `every species has its own habit of 1 to 1,5 seconds`() {
        val ids = SpeciesCatalog.sprites.map { it.habit.id }
        assertEquals(ids.size, ids.toSet().size)
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} ${s.habit.durationMs}", s.habit.durationMs in 1000L..1500L)
    }

    @Test
    fun `every habit frame stays on the matrix and the habit differs from plain idle`() {
        for (s in SpeciesCatalog.sprites) {
            val idle = SpeciesRenderer.render(s, Face.IDLE).toList()
            var differentMs = 0L
            for (dx in listOf(-4, -2, 0, 2, 4, -3)) for (f in s.habit.frames) {
                assertInside("${s.id} dx=$dx", SpeciesRenderer.render(s, f.face ?: Face.IDLE, dx = dx, habit = f))
            }
            for (f in s.habit.frames) if (SpeciesRenderer.render(s, f.face ?: Face.IDLE, habit = f).toList() != idle) differentMs += f.ms
            assertTrue("${s.id}: only $differentMs ms of ${s.habit.durationMs} differ from idle", differentMs >= s.habit.durationMs * 0.75)
        }
    }

    @Test
    fun `habits of two species never look alike`() {
        // Strip the silhouettes: what each habit adds or removes compared with its own idle frame.
        fun changes(s: Species) = s.habit.frames.map { f ->
            val idle = SpeciesRenderer.render(s, Face.IDLE)
            val h = SpeciesRenderer.render(s, f.face ?: Face.IDLE, habit = f)
            h.indices.filter { h[it] != idle[it] }.toSet()
        }.toSet()
        val all = SpeciesCatalog.sprites.associate { it.id to changes(it) }
        for ((a, ca) in all) for ((b, cb) in all) if (a < b) assertFalse("$a and $b", ca == cb)
    }

    @Test
    fun `the habit plays now and then, only after the pet was left alone for a while`() {
        for (s in SpeciesCatalog.sprites) for (mood in listOf(Mood.CONTENT, Mood.HAPPY)) {
            val engine = PetEngine(Random(21))
            val starts = mutableListOf<Long>()
            var was = false
            habitFrames(engine, 120_000, { Scene(s, mood) }) { t, e ->
                if (e.shown.habit && !was) starts += t
                was = e.shown.habit
            }
            assertTrue("${s.id} $mood: ${starts.size} habits", starts.size >= 2)
            assertTrue("${s.id} $mood first at ${starts.first()}", starts.first() >= PetEngine.QUIET_MS)
            // Each one waits for the quiet again, after the habit or the signature before it.
            starts.zipWithNext().forEach { (a, b) ->
                assertTrue("${s.id} $mood gap ${b - a}", b - a >= PetEngine.QUIET_MS + s.habit.durationMs)
            }
        }
    }

    @Test
    fun `the signature comes by itself about once a minute, small habits fill the quiet between`() {
        for (s in SpeciesCatalog.sprites) {
            if (!Signatures.has(s.kind)) continue
            val engine = PetEngine(Random(24))
            var sigs = 0
            var habits = 0
            var wasSig = false
            var wasHabit = false
            var t = 0L
            while (t < 200_000) {
                t += 80
                engine.tick(80, Scene(s, Mood.CONTENT))
                val sig = engine.playingSignature
                if (sig && !wasSig) sigs++
                if (engine.shown.habit && !wasHabit) habits++
                wasSig = sig
                wasHabit = engine.shown.habit
            }
            // 200 s: the first at about 20 s, then one a minute or a little more (it waits for the quiet).
            // (Debug builds bring it every 30 s, to be seen sooner.)
            val expected = if (n7.glyphpet.BuildConfig.DEBUG) 3..8 else 2..4
            assertTrue("${s.id}: $sigs signatures", sigs in expected)
            if (!s.habit.none) assertTrue("${s.id}: $habits habits, $sigs signatures", habits >= sigs)
        }
    }

    @Test
    fun `no habit during a reaction, asleep, on the charger, sliding or in other moods`() {
        for (s in SpeciesCatalog.sprites) {
            val busy = mapOf<String, Pair<(Long) -> Scene, (Long, PetEngine) -> Unit>>(
                "stroked" to Pair({ _ -> Scene(s, Mood.CONTENT) }, { t, e -> if (t % 1200 == 80L) e.pet() }),
                "dizzy" to Pair({ _ -> Scene(s, Mood.HAPPY) }, { t, e -> if (t % 2000 == 80L) e.shake() }),
                "asleep" to Pair({ _ -> Scene(s, Mood.SLEEPING) }, { _, _ -> }),
                "charging" to Pair({ _ -> Scene(s, Mood.CONTENT, charging = true, watts = 20f, battery = 0.5f) }, { _, _ -> }),
                "sliding" to Pair({ t -> Scene(s, Mood.CONTENT, tilt = if ((t / 1000) % 2 == 0L) 1f else -1f) }, { _, _ -> }),
                "hungry" to Pair({ _ -> Scene(s, Mood.HUNGRY) }, { _, _ -> }),
            )
            for ((what, stage) in busy) {
                val (scene, poke) = stage
                // A trick asked for every second is refused too.
                val n = habitFrames(PetEngine(Random(22)), 20_000, scene) { t, e -> poke(t, e); if (t % 1000 == 0L) e.trick() }
                assertEquals("${s.id} $what", 0, n)
            }
        }
    }

    /** Every habit frame in play order, sampled every 10 ms the way the engine picks them. */
    private fun played(s: Species): List<HabitFrame> =
        (0 until s.habit.durationMs step 10).mapNotNull { s.habit.frameAt(it) }.distinct()

    /** LEDs a habit frame drawn at [dx] loses off the round matrix that the plain pet standing there keeps. */
    private fun lostBy(s: Species, f: HabitFrame, dx: Int): List<Pair<Int, Int>> {
        val idleLost = mutableListOf<Pair<Int, Int>>()
        SpeciesRenderer.render(s, Face.IDLE, dx = dx, clipped = idleLost)
        val lost = mutableListOf<Pair<Int, Int>>()
        SpeciesRenderer.render(s, f.face ?: Face.IDLE, dx = dx, habit = f, clipped = lost)
        return lost - idleLost.toSet()
    }

    @Test
    fun `no habit loses LEDs off the round matrix, at home and wherever the engine plays it`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesCatalog.sprites) {
            val spots = PetEngine.habitSpots(s)
            if (0 !in spots) problems += "${s.id}: the habit does not fit at home"
            if (spots.size < 2) problems += "${s.id}: the habit fits only at $spots"
            for (dx in listOf(0) + spots) for ((n, f) in played(s).withIndex()) {
                val new = lostBy(s, f, dx)
                if (new.isNotEmpty()) problems += "${s.id} dx=$dx frame $n loses $new"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        // The engine only starts a habit where it fits: every habit frame of long runs, redrawn where it was shown.
        for (s in SpeciesCatalog.sprites) for (seed in 0 until 3) {
            val engine = PetEngine(Random(30 + seed))
            var t = 0L
            var habits = 0
            while (t < 90_000) {
                t += 80
                val mood = if ((t / 20_000) % 2 == 0L) Mood.CONTENT else Mood.HAPPY
                engine.tick(80, Scene(s, mood))
                val f = engine.shown.step ?: continue
                habits++
                val new = lostBy(s, f, engine.shown.dx)
                assertTrue("${s.id} seed=$seed t=$t dx=${engine.shown.dx} loses $new", new.isEmpty())
            }
            assertTrue("${s.id} seed=$seed: $habits habit frames", habits > 8)
        }
    }

    @Test
    fun `the toast stays inside its toaster`() {
        val toast = SpeciesCatalog.get("toast")!!
        for (dx in listOf(0, -3, 3)) {
            for ((n, f) in played(toast).withIndex()) {
                val toaster = f.over.maxByOrNull { it.rows.size }?.takeIf { it.rows.size >= 5 } ?: continue
                val bottom = toast.oy + toaster.y + toaster.rows.lastIndex
                val frame = SpeciesRenderer.render(toast, f.face ?: Face.IDLE, dx = dx, habit = f)
                val below = frame.indices.filter { frame[it] > 0 && it / 25 > bottom }.map { it % 25 to it / 25 }
                assertTrue("dx=$dx frame $n: lit below the toaster $below", below.isEmpty())
            }
        }
    }

    @Test
    fun `a reaction waits for the habit to end, then plays at once`() {
        val frog = SpeciesCatalog.get("frog")!!
        val engine = PetEngine(Random(23))
        engine.trick()
        engine.tick(80, Scene(frog, Mood.CONTENT))
        assertTrue(engine.shown.habit)
        engine.pet()
        // The habit is always seen to its end: the stroke waits in the queue (see PetEngine.react).
        var t = 0L
        while (engine.shown.habit && t < 5000) {
            engine.tick(80, Scene(frog, Mood.CONTENT))
            t += 80
        }
        assertTrue("the habit ended in ${t}ms", t in frog.habit.durationMs..(frog.habit.durationMs + 500))
        engine.tick(80, Scene(frog, Mood.CONTENT))
        assertEquals(Face.LOVE, engine.shown.face)
    }
}
