package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What wins when two ways of waking the pet arrive at once: [PetEngine] takes one reaction at a time, and keeps the
 * rest waiting so every animation is seen to its end. The toy decides which gestures reach it ([n7.glyphpet.toy.Reactions]);
 * this is what the pet does with them.
 */
class EngineOverridesTest {

    private val cat = SpeciesCatalog.get("cat")!!

    /** Calm but with nothing of its own due: tired keeps the habit and the signature out of the way of the test. */
    private val busyFree = Scene(cat, Mood.TIRED)

    private fun engine(seed: Int = 7) = PetEngine(Random(seed), autoHabits = false, autoSignatures = false)

    /** What [PetEngine.debug] says the reaction is: "action=NOTICE(700ms)" -> "NOTICE". */
    private fun PetEngine.action(): String = Regex("action=(\\w+)\\(").find(debug(cat))!!.groupValues[1]

    /** How many reactions are waiting for whatever plays to end. */
    private fun PetEngine.pending(): Int = Regex("pending=(\\d+)").find(debug(cat))!!.groupValues[1].toInt()

    // --- One reaction over another -------------------------------------------------------------------

    @Test
    fun `a double knock upgrades a single one, and a single one never interrupts a tickle`() {
        val up = engine()
        up.notice()
        up.tick(80, busyFree)
        assertEquals("NOTICE", up.action())
        up.tickle()
        up.tick(80, busyFree)
        assertEquals("a tickle takes over from a knock", "TICKLE", up.action())

        val down = engine()
        down.tickle()
        down.tick(80, busyFree)
        assertEquals("TICKLE", down.action())
        down.notice()
        down.tick(80, busyFree)
        assertEquals("a knock under a tickle changes nothing", "TICKLE", down.action())
        assertEquals("and brings no hop of its own", 0, down.shown.lift)
    }

    @Test
    fun `a knock, a notification and a greeting all wait for a reaction that is already playing`() {
        for (quiet in listOf<(PetEngine) -> Unit>({ it.notice() }, { it.alert() }, { it.greet() })) {
            val e = engine()
            e.shake()
            e.tick(80, busyFree)
            assertEquals("DIZZY", e.action())
            quiet(e)
            e.tick(80, busyFree)
            assertEquals("DIZZY", e.action())
        }
    }

    @Test
    fun `a stroke takes over from any reaction, because the owner's hand is on the pet`() {
        for (before in listOf<(PetEngine) -> Unit>({ it.notice() }, { it.tickle() }, { it.greet() }, { it.shake() })) {
            val e = engine()
            before(e)
            e.tick(80, busyFree)
            e.pet()
            e.tick(80, busyFree)
            assertEquals(Face.LOVE, e.shown.face)
        }
    }

    // --- Waiting their turn ---------------------------------------------------------------------------

    @Test
    fun `reactions that come during a habit wait for it and then play one per frame`() {
        val e = PetEngine(Random(8), autoHabits = false, autoSignatures = false)
        e.trick()
        e.tick(80, Scene(cat, Mood.CONTENT))
        assertTrue(e.shown.habit)
        e.pet()
        e.shake()
        assertEquals("both waiting", 2, e.pending())
        // The habit plays to its end with neither of them showing.
        var t = 80L
        while (e.shown.habit && t < 5000) {
            assertEquals(Face.IDLE, e.shown.face)
            e.tick(80, Scene(cat, Mood.CONTENT))
            t += 80
        }
        // Then one at a time, in the order they came.
        e.tick(80, Scene(cat, Mood.CONTENT))
        assertEquals(Face.LOVE, e.shown.face)
        assertEquals(1, e.pending())
    }

    @Test
    fun `only four reactions wait at a time, the rest are lost`() {
        val e = PetEngine(Random(9), autoHabits = false, autoSignatures = false)
        e.trick()
        e.tick(80, Scene(cat, Mood.CONTENT))
        assertTrue(e.shown.habit)
        repeat(6) { e.pet() }
        assertEquals("the queue holds four", 4, e.pending())
    }

    @Test
    fun `a hatch plays to its end, whatever the owner does during it`() {
        val e = engine()
        val baby = SpeciesForms.of("cat", Stage.BABY)
        e.hatch(baby, golden = false)
        e.tick(80, Scene(baby, Mood.CONTENT))
        assertTrue(e.playingMoment)
        repeat(3) { e.pet() }
        assertEquals(3, e.pending())
        var t = 80L
        while (e.playingMoment && t < 10_000) {
            assertNull("nothing but the hatch while it plays", e.shown.face)
            e.tick(80, Scene(baby, Mood.CONTENT))
            t += 80
        }
        e.tick(80, Scene(baby, Mood.CONTENT))
        assertEquals("the strokes come out after it", Face.LOVE, e.shown.face)
    }

    // --- The trick, and what cuts it short --------------------------------------------------------------

    @Test
    fun `while a trick plays, everything else waits and the pet keeps its trick face`() {
        val e = engine()
        assertTrue(e.trick(cat.kind))
        e.tick(80, busyFree)
        assertTrue(e.playingTrick)
        e.feed(accepted = true)
        e.notice()
        e.tickle()
        e.greet()
        repeat(20) { e.tick(80, busyFree) }
        assertTrue("the trick plays on", e.playingTrick)
        assertEquals("nothing of theirs showed", "NONE", e.action())
        assertEquals(4, e.pending())
    }

    @Test
    fun `falling asleep cuts a trick short too`() {
        val e = engine()
        e.events = mutableListOf()
        assertTrue(e.trick(cat.kind))
        repeat(5) { e.tick(80, busyFree) }
        e.tick(80, Scene(cat, Mood.SLEEPING))
        assertFalse(e.playingTrick)
        assertEquals(PetEvent.Kind.TRICK, e.events!!.single().kind)
    }

    @Test
    fun `the owner who has had enough ends the trick through its own ending`() {
        val e = engine()
        e.events = mutableListOf()
        assertTrue(e.trick(cat.kind))
        repeat(10) { e.tick(80, busyFree) }
        e.stopTrick()
        // It heads home rather than vanishing: the ending plays, then the plain pet is back.
        assertTrue(e.playingTrick)
        var t = 0L
        while (e.playingTrick && t < 10_000) {
            e.tick(80, busyFree)
            t += 80
        }
        assertFalse("the trick ended in ${t}ms", e.playingTrick)
        assertEquals(PetEvent.Kind.TRICK, e.events!!.single().kind)
    }

    @Test
    fun `a trick nobody asked for is no trick at all`() {
        val e = engine()
        assertFalse("an unknown kind has no trick", e.trick("nope"))
        e.tick(80, busyFree)
        assertFalse(e.playingTrick)
    }

    @Test
    fun `a trick asked for while a habit plays waits, and playingTrick says so`() {
        val e = PetEngine(Random(10), autoHabits = false, autoSignatures = false)
        e.trick()
        e.tick(80, Scene(cat, Mood.CONTENT))
        assertTrue(e.shown.habit)
        assertTrue("the trick was taken", e.trick(cat.kind))
        assertFalse("but it is not playing yet", e.playingTrick)
        // Until it starts, there is nothing for stopTrick to end: the toy would start it again instead.
        e.stopTrick()
        var t = 0L
        while (!e.playingTrick && t < 5000) {
            e.tick(80, Scene(cat, Mood.CONTENT))
            t += 80
        }
        assertTrue("the trick started after the habit", e.playingTrick)
    }

    // --- The signature effect ---------------------------------------------------------------------------

    @Test
    fun `a signature watched to its end is reported, one cut short is not`() {
        val full = PetEngine(Random(11), autoHabits = false, autoSignatures = false)
        full.events = mutableListOf()
        full.signatureNow(seed = 1)
        val calm = Scene(cat, Mood.CONTENT)
        val length = Signatures.of(cat.kind)!!.durationMs
        var t = 0L
        while (t < length + 400) {
            full.tick(80, calm)
            t += 80
        }
        assertEquals(PetEvent.Kind.SIGNATURE, full.events!!.single().kind)

        val cut = PetEngine(Random(11), autoHabits = false, autoSignatures = false)
        cut.events = mutableListOf()
        cut.signatureNow(seed = 1)
        repeat(5) { cut.tick(80, calm) }
        cut.tick(80, Scene(cat, Mood.SLEEPING))
        repeat(3) { cut.tick(80, calm) }
        assertTrue("a signature cut short is never reported", cut.events!!.isEmpty())
    }

    // --- The ball ----------------------------------------------------------------------------------------

    @Test
    fun `a second game while the ball is out kicks it instead of bringing another`() {
        val e = engine()
        e.play()
        repeat(3) { e.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue(e.playingBall)
        e.play()
        repeat(3) { e.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue("still one ball", e.playingBall)
    }

    @Test
    fun `a sleeping pet gets no ball, and a trick clears the one already out`() {
        val asleep = engine()
        asleep.play()
        repeat(3) { asleep.tick(80, Scene(cat, Mood.SLEEPING)) }
        assertFalse(asleep.playingBall)

        val tricked = engine()
        tricked.play()
        repeat(3) { tricked.tick(80, Scene(cat, Mood.CONTENT)) }
        assertTrue(tricked.playingBall)
        tricked.trick(cat.kind)
        tricked.tick(80, Scene(cat, Mood.CONTENT))
        assertFalse("the trick has the matrix to itself", tricked.playingBall)
    }

    // --- The sleeper ---------------------------------------------------------------------------------------

    @Test
    fun `whatever the owner does to a sleeper, it only opens one eye`() {
        val asleep = Scene(cat, Mood.SLEEPING)
        val pokes = listOf<Pair<String, (PetEngine) -> Unit>>(
            "stroked" to { it.pet() },
            "fed" to { it.feed(accepted = true) },
            "refused" to { it.feed(accepted = false) },
            "shaken" to { it.shake() },
            "knocked" to { it.notice() },
            "tickled" to { it.tickle() },
            "greeted" to { it.greet() },
            "alerted" to { it.alert() },
            "surging" to { it.surge() },
        )
        for ((what, poke) in pokes) {
            val e = engine()
            repeat(3) { e.tick(80, asleep) }
            assertEquals(Face.SLEEP, e.shown.face)
            poke(e)
            e.tick(80, asleep)
            assertEquals(what, Face.PEEK, e.shown.face)
            // And the eye closes again by itself.
            var t = 0L
            while (e.shown.face == Face.PEEK && t < 3000) {
                e.tick(80, asleep)
                t += 80
            }
            assertEquals("$what for ${t}ms", Face.SLEEP, e.shown.face)
            assertTrue("$what peeked ${t}ms", t in 800L..1200L)
        }
    }

    // --- An egg on the matrix, and the charger -----------------------------------------------------------

    @Test
    fun `with an egg on the matrix the pet is away, and nothing the owner does is drawn`() {
        val egg = Scene(cat, Mood.CONTENT, egg = EggView(EggStage.CRACKED, golden = false))
        val e = engine()
        val before = e.tick(80, egg)
        assertNull(e.shown.face)
        for (poke in listOf<(PetEngine) -> Unit>({ it.pet() }, { it.notice() }, { it.tickle() }, { it.greet() }, { it.play() })) {
            poke(e)
            val after = e.tick(80, egg)
            assertNull("the pet is away", e.shown.face)
            assertTrue("the egg is all there is", after.contentEquals(before) || e.shown.face == null)
        }
    }

    @Test
    fun `on the charger a reaction shows but no habit does`() {
        val charging = Scene(cat, Mood.CONTENT, charging = true, watts = 15f, battery = 0.5f)
        val e = PetEngine(Random(12), autoHabits = false, autoSignatures = false)
        val plain = e.tick(80, charging)
        assertTrue(e.shown.charging)
        e.trick()
        repeat(10) { e.tick(80, charging) }
        assertFalse("a habit is dropped on the charger", e.shown.habit)
        e.pet()
        val loved = e.tick(80, charging)
        assertFalse("the stroke shows in the charge indicator", loved.contentEquals(plain))
    }
}
