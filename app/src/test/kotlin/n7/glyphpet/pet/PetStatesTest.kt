package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetStatesTest {

    private val dt = 80L

    private class Run(val frames: List<IntArray>, val shown: List<Shown>)

    private fun play(preview: StatePreview, ms: Long): Run {
        val frames = mutableListOf<IntArray>()
        val shown = mutableListOf<Shown>()
        repeat((ms / dt).toInt()) {
            frames += preview.tick(dt)
            shown += preview.shown
        }
        return Run(frames, shown)
    }

    /** Whether a frame that showed [s] shows what [state] claims. */
    private fun shows(state: PetState, s: Shown): Boolean = when (state) {
        PetState.TRICK -> s.habit
        PetState.SIGNATURE, PetState.GAME -> true
        PetState.CHARGING -> s.charging
        PetState.HAPPY -> s.face == Face.CONTENT
        PetState.ASLEEP -> s.face == Face.SLEEP || s.face == Face.PEEK
        else -> s.face == state.stage.face
    }

    private fun span(preview: StatePreview) = if (preview.loopMs > 0) 3 * preview.loopMs else 8000L

    @Test
    fun `the states come in the order the app shows them, with stable ids`() {
        assertEquals(
            listOf("calm", "signature", "happy", "hungry", "sad", "tired", "asleep", "eating", "game", "loved", "dizzy", "trick", "charging"),
            PetState.entries.map { it.id },
        )
    }

    @Test
    fun `every state of every species stays on the matrix`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val p = StatePreview(s, st)
            for ((n, f) in play(p, span(p)).frames.withIndex()) {
                assertEquals(625, f.size)
                // A game may go dark on purpose for a moment (the bat in its cave).
                if (st != PetState.GAME) assertTrue("${s.id} ${st.id} frame $n is empty", f.count { it > 0 } > 20)
                for (i in f.indices) {
                    assertTrue(f[i] in 0..4095)
                    if (f[i] > 0) assertTrue("${s.id} ${st.id} frame $n", GlyphMatrixSpec.isVisible(i % 25, i / 25))
                }
            }
        }
    }

    @Test
    fun `every staged scene shows the face it claims`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val p = StatePreview(s, st)
            val run = play(p, span(p))
            val share = run.shown.count { shows(st, it) }.toFloat() / run.shown.size
            val least = when (st) {
                PetState.SIGNATURE -> 1f
                PetState.CALM, PetState.HUNGRY, PetState.SAD, PetState.CHARGING -> 0.95f
                PetState.HAPPY, PetState.TIRED, PetState.ASLEEP -> 0.8f
                else -> 0.15f
            }
            assertTrue("${s.id} ${st.id}: $share", share >= least)
        }
    }

    @Test
    fun `a happy preview shows its joy, an asleep one peeks, a tired one yawns`() {
        for (s in SpeciesCatalog.all) {
            assertTrue(s.id, play(StatePreview(s, PetState.HAPPY), 6000).shown.any { it.cheer })
            assertTrue(s.id, play(StatePreview(s, PetState.ASLEEP), 6000).shown.any { it.face == Face.PEEK })
            assertTrue(s.id, play(StatePreview(s, PetState.TIRED), 8000).shown.any { it.face == Face.YAWN })
            assertFalse(s.id, play(StatePreview(s, PetState.CALM), 20_000).shown.any { it.habit || it.lift > 0 })
        }
    }

    @Test
    fun `short reactions are triggered again every loop and end in between`() {
        // The mini-game is no short reaction: it plays on until the preview sends it home.
        val cued = PetState.entries.filter { it.stage.cue != null && it != PetState.GAME }
        assertEquals(
            listOf(PetState.SIGNATURE, PetState.ASLEEP, PetState.EATING, PetState.LOVED, PetState.DIZZY, PetState.TRICK),
            cued,
        )
        for (s in SpeciesCatalog.all) for (st in cued) {
            val p = StatePreview(s, st)
            assertTrue(p.loopMs >= 2000)
            val run = play(p, 4 * p.loopMs)
            val signature = { x: Shown ->
                when (st) {
                    PetState.ASLEEP -> x.face == Face.PEEK
                    PetState.TRICK -> x.habit
                    else -> x.face == st.stage.face
                }
            }
            // The signature effect has the whole matrix, and the engine does not report it: it plays on its own clock.
            val effect = if (st == PetState.SIGNATURE) Signatures.of(s.kind)?.durationMs ?: 0L else 0L
            val perLoop = (p.loopMs / dt).toInt()
            fun showsIt(indexInLoop: Int, x: Shown): Boolean =
                if (st == PetState.SIGNATURE) indexInLoop * dt in st.stage.cueFirstMs until (st.stage.cueFirstMs + effect) else signature(x)
            for (loop in 0 until 4) {
                val window = run.shown.subList(loop * perLoop, minOf((loop + 1) * perLoop, run.shown.size))
                assertTrue("${s.id} ${st.id} loop $loop shows it", window.indices.any { showsIt(it, window[it]) })
                assertTrue("${s.id} ${st.id} loop $loop rests", window.indices.any { !showsIt(it, window[it]) })
            }
        }
    }

    @Test
    fun `a preview repeats exactly from reset`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val p = StatePreview(s, st)
            val first = play(p, 5000).frames.map { it.toList() }
            p.reset()
            val again = play(p, 5000).frames.map { it.toList() }
            assertEquals("${s.id} ${st.id}", first, again)
            assertEquals("a fresh preview is the same", first, play(StatePreview(s, st), 5000).frames.map { it.toList() })
        }
    }

    @Test
    fun `the still of a page that is not playing shows its state`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val still = StatePreview(s, st).still()
            assertEquals(625, still.size)
            assertTrue("${s.id} ${st.id} still is empty", still.count { it > 0 } > 20)
            for (i in still.indices) {
                assertTrue(still[i] in 0..4095)
                if (still[i] > 0) assertTrue("${s.id} ${st.id} still", GlyphMatrixSpec.isVisible(i % 25, i / 25))
            }
            // It is one of the frames the playing page shows while the state is on.
            val p = StatePreview(s, st)
            val run = play(p, maxOf(p.loopMs, 4000) + 1000)
            val showing = run.frames.indices.filter { st.isShownBy(run.shown[it]) }.map { run.frames[it].toList() }
            assertTrue("${s.id} ${st.id}", still.toList() in showing)
            assertTrue("${s.id} ${st.id} agrees with the test's reading", run.shown.filter { st.isShownBy(it) } == run.shown.filter { shows(st, it) })
        }
    }

    /** How the app's GlyphMatrix draws a frame: lit cells and their levels. */
    private fun appLook(frame: IntArray) = PetEngine.toCells(frame).associate { (it.x to it.y) to it.level }

    @Test
    fun `every still tells its state apart from calm on the matrix`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesCatalog.all) {
            val calmRaw = StatePreview(s, PetState.CALM).still()
            for (st in PetState.entries - PetState.CALM) {
                val still = StatePreview(s, st).still()
                // What the matrix draws: the player's real view. (The app's look clamps everything at body brightness
                // or more to one white, so a habit that only recolours the body would read as no change there.)
                val differ = still.indices.count { still[it] != calmRaw[it] }
                // The closest left (4-6): the frog's tongue (the app draws the part over its body white on white), a
                // one-eyed or mouthless pet's tear, the bot's scan line, the eye's open mouth.
                if (differ < 4) problems += "${s.id} ${st.id}: $differ LEDs differ from calm"
                // The pet itself is there, not a faded trace of it.
                if (still.count { it >= PetEngine.BODY / 2 } < 20) problems += "${s.id} ${st.id} is faded"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the happy still is caught in its joy and the trick still on a full-bright habit frame`() {
        for (s in SpeciesCatalog.all) {
            val happy = StatePreview(s, PetState.HAPPY).still()
            val trick = StatePreview(s, PetState.TRICK).still()
            fun caught(st: PetState, still: IntArray, ok: (Shown) -> Boolean): Boolean {
                val p = StatePreview(s, st)
                repeat(((maxOf(p.loopMs, 4000) + 1000) / dt).toInt()) { if (p.tick(dt).contentEquals(still)) return ok(p.shown) }
                return false
            }
            assertTrue(s.id, caught(PetState.HAPPY, happy) { it.lift > 0 || it.cheer })
            assertTrue(s.id, caught(PetState.TRICK, trick) { (it.step?.body ?: 0f) >= 0.5f })
        }
    }

    @Test
    fun `taking a still does not disturb the playing preview`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val plain = StatePreview(s, st)
            val first = play(plain, 4000).frames.map { it.toList() }
            val p = StatePreview(s, st)
            val a = play(p, 2000).frames
            p.still()
            val b = play(p, 2000).frames
            assertEquals("${s.id} ${st.id}", first, (a + b).map { it.toList() })
        }
    }

    @Test
    fun `no two states of a species look the same`() {
        for (s in SpeciesCatalog.all) {
            val looks = PetState.entries.associateWith { st ->
                play(StatePreview(s, st), 8000).frames.map { it.toList() }.toSet()
            }
            for ((a, fa) in looks) for ((b, fb) in looks) {
                if (a.ordinal >= b.ordinal) continue
                // A good part of what they show is their own (calm moments between reactions may match).
                val own = (fa - fb).size + (fb - fa).size
                assertTrue("${s.id}: ${a.id} vs ${b.id}: $own of ${(fa + fb).size}", own >= (fa + fb).size * 0.3)
            }
        }
    }
}
