package n7.glyphpet.toy

import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.XpRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every way of waking the pet up, and how they come out when several arrive at once: [Reactions] is the whole of what
 * the toy makes of the sensors. Time is always an explicit uptime in ms, as the toy's frames are.
 */
class ReactionsTest {

    /** A pet awake on the matrix, the phone in hand: everything the owner does reaches it. */
    private val awake = Attention(hasPet = true)

    private fun one(gestures: Gestures, at: Attention = awake, now: Long = 10_000): List<Reaction> =
        Reactions().frame(now, gestures, at)

    // --- One gesture at a time, in every state ------------------------------------------------------

    @Test
    fun `without a pet nothing the owner does reaches anybody`() {
        val nobody = Attention(hasPet = false, ready = true)
        for (g in everyGesture()) assertEquals("$g", emptyList<Reaction>(), one(g, nobody))
    }

    @Test
    fun `while an egg is on the matrix the pet is away, and nothing reaches it`() {
        val away = Attention(hasPet = true, eggOnMatrix = true, ready = true)
        for (g in everyGesture()) assertEquals("$g", emptyList<Reaction>(), one(g, away))
    }

    @Test
    fun `busy with the screen in another app, the phone moves with the hand and none of it is play`() {
        val using = awake.copy(posture = Posture.USING_PHONE, ready = true)
        // The knock needs the desk or the hand too, so nothing at all gets through.
        for (g in everyGesture()) assertEquals("$g", emptyList<Reaction>(), one(g, using))
    }

    @Test
    fun `a knock only reaches the pet through the desk beside it or the back of the phone in hand`() {
        for (posture in Posture.entries) {
            val knocks = one(Gestures(knocks = 1), awake.copy(posture = posture))
            val tickles = one(Gestures(knocks = 2), awake.copy(posture = posture))
            if (posture == Posture.DESK || posture == Posture.HAND) {
                assertEquals("$posture", listOf(Reaction.Notice), knocks)
                assertEquals("$posture", listOf(Reaction.Tickle(joy = true)), tickles)
            } else {
                assertEquals("$posture", emptyList<Reaction>(), knocks)
                assertEquals("$posture", emptyList<Reaction>(), tickles)
            }
        }
    }

    @Test
    fun `a shake grows a pet that is ready, and is no game for one that is not`() {
        assertEquals(listOf(Reaction.Grow), one(Gestures(shake = true), awake.copy(ready = true)))
        // Shaking is no game: dizzy stars come from twirling the phone.
        assertEquals(emptyList<Reaction>(), one(Gestures(shake = true), awake.copy(ready = false)))
    }

    @Test
    fun `rocking plays with a pet that is awake, and a kick to the ball already out is no new game`() {
        assertEquals(listOf(Reaction.Play(freshGame = true, xp = true)), one(Gestures(rock = true)))
        assertEquals(
            listOf(Reaction.Play(freshGame = false, xp = false)),
            one(Gestures(rock = true), awake.copy(playingBall = true)),
        )
        assertEquals("a sleeper is only lulled", emptyList<Reaction>(), one(Gestures(rock = true), awake.copy(mood = Mood.SLEEPING)))
    }

    @Test
    fun `a sleeper giggles at a double knock but nothing changes for it`() {
        val asleep = awake.copy(mood = Mood.SLEEPING)
        assertEquals(listOf(Reaction.Tickle(joy = false)), one(Gestures(knocks = 2), asleep))
        assertEquals("one knock still turns its head", listOf(Reaction.Notice), one(Gestures(knocks = 1), asleep))
        assertEquals("a sleeper is not greeted", emptyList<Reaction>(), one(Gestures(pickedUp = true), asleep))
    }

    @Test
    fun `a twirl makes the pet dizzy in every mood, asleep too`() {
        // The one gesture with no mood of its own to mind: a sleeper gets its stars (see PetEngine.stir).
        for (mood in Mood.entries) {
            assertEquals("$mood", listOf(Reaction.Dizzy), one(Gestures(twirl = true), awake.copy(mood = mood)))
        }
    }

    @Test
    fun `nobody can see the matrix, but the phone is still in the owner's hand`() {
        // AWAY is where the matrix is not seen; it is no gate on the motion itself, and the owner may have put the
        // phone face up on the desk right beside them. (The toy does not even ask while it draws nothing: see
        // PetToyService.renderLive.)
        val away = awake.copy(posture = Posture.AWAY)
        assertEquals(listOf(Reaction.Dizzy), one(Gestures(twirl = true), away))
        assertEquals(listOf(Reaction.Play(freshGame = true, xp = true)), one(Gestures(rock = true), away))
        assertEquals("a knock does not reach it there", emptyList<Reaction>(), one(Gestures(knocks = 1), away))
    }

    // --- How often each may bring joy or experience --------------------------------------------------

    @Test
    fun `a new game of ball brings experience at most every half minute, and a kick none`() {
        val r = Reactions()
        assertEquals(Reaction.Play(freshGame = true, xp = true), r.frame(1000, Gestures(rock = true), awake).single())
        // Too soon for more experience, but it is still a new game: the pet is cheered up again.
        val soon = r.frame(1000 + Reactions.BALL_XP_MS - 1, Gestures(rock = true), awake).single()
        assertEquals(Reaction.Play(freshGame = true, xp = false), soon)
        val later = r.frame(1000 + Reactions.BALL_XP_MS, Gestures(rock = true), awake).single()
        assertEquals(Reaction.Play(freshGame = true, xp = true), later)
        assertEquals(XpRules.BALL_COOLDOWN_MS, Reactions.BALL_XP_MS)
    }

    @Test
    fun `a double knock cheers the pet up at most every three seconds, but giggles every time`() {
        val r = Reactions()
        assertEquals(Reaction.Tickle(joy = true), r.frame(1000, Gestures(knocks = 2), awake).single())
        assertEquals(Reaction.Tickle(joy = false), r.frame(1000 + Reactions.TICKLE_JOY_MS - 1, Gestures(knocks = 2), awake).single())
        assertEquals(Reaction.Tickle(joy = true), r.frame(1000 + Reactions.TICKLE_JOY_MS, Gestures(knocks = 2), awake).single())
    }

    @Test
    fun `the pet greets its owner at most every two minutes`() {
        val r = Reactions()
        assertEquals(listOf(Reaction.Greet), r.frame(1000, Gestures(pickedUp = true), awake))
        assertEquals(emptyList<Reaction>(), r.frame(1000 + Reactions.GREET_EVERY_MS - 1, Gestures(pickedUp = true), awake))
        assertEquals(listOf(Reaction.Greet), r.frame(1000 + Reactions.GREET_EVERY_MS, Gestures(pickedUp = true), awake))
    }

    @Test
    fun `a greeting the pet could not give is lost, not kept for later`() {
        val r = Reactions()
        // Asleep when the phone was picked up: nothing, and the next pick-up may greet at once.
        assertEquals(emptyList<Reaction>(), r.frame(1000, Gestures(pickedUp = true), awake.copy(mood = Mood.SLEEPING)))
        assertEquals(listOf(Reaction.Greet), r.frame(2000, Gestures(pickedUp = true), awake))
    }

    @Test
    fun `the first of everything always counts, however long the toy has been running`() {
        // The cooldowns start long before any frame, so a toy just started owes nothing.
        for (now in listOf(0L, 1L, 500_000L)) {
            val r = Reactions()
            assertEquals("$now", listOf(Reaction.Greet), r.frame(now, Gestures(pickedUp = true), awake))
            assertEquals("$now", Reaction.Tickle(joy = true), r.frame(now, Gestures(knocks = 2), awake).single())
            assertEquals("$now", Reaction.Play(freshGame = true, xp = true), r.frame(now, Gestures(rock = true), awake).single())
        }
    }

    // --- Gestures that arrive together ---------------------------------------------------------------

    @Test
    fun `the pet notices the gestures of one frame in its own order, the knock last`() {
        val all = Gestures(shake = true, twirl = true, rock = true, knocks = 2, pickedUp = true)
        assertEquals(
            listOf(
                Reaction.Grow,
                Reaction.Dizzy,
                Reaction.Play(freshGame = true, xp = true),
                Reaction.Greet,
                Reaction.Tickle(joy = true),
            ),
            one(all, awake.copy(ready = true)),
        )
    }

    @Test
    fun `a shake and a twirl of the same wrist both count`() {
        // A vigorous shake can turn the phone twice round as well: neither detector cancels the other, and the pet
        // takes both (the engine plays them one after another, see PetEngine.react).
        val both = one(Gestures(shake = true, twirl = true), awake.copy(ready = true))
        assertEquals(listOf(Reaction.Grow, Reaction.Dizzy), both)
    }

    @Test
    fun `a double knock is a tickle, never also a single one`() {
        assertEquals(listOf(Reaction.Tickle(joy = true)), one(Gestures(knocks = 2)))
        assertEquals(listOf(Reaction.Notice), one(Gestures(knocks = 1)))
        assertEquals(emptyList<Reaction>(), one(Gestures(knocks = 0)))
    }

    // --- Being picked up -----------------------------------------------------------------------------

    /** The phone lying still screen down on the desk, as [PostureTracker] reads it. */
    private val onDesk = Pose(gravityZ = -9.8f)

    /** In the hand, upright. */
    private val inHand = Pose(gravityZ = 0f)

    /**
     * Holds [pose] from [from] for [ms] at 12 fps, the toy's frame rate, and returns every moment it read as being
     * picked up.
     */
    private fun Reactions.hold(
        pose: Pose?,
        from: Long,
        ms: Long,
        jitter: Float,
        screenOn: Boolean = false,
        unlocked: Boolean = false,
        appInFront: Boolean = false,
    ): List<Long> {
        val out = mutableListOf<Long>()
        var t = from
        while (t < from + ms) {
            if (posture(pose, jitter, screenOn, unlocked, appInFront, t)) out += t
            t += 80
        }
        return out
    }

    @Test
    fun `picked up off the desk, the pet notices once`() {
        val r = Reactions()
        // Lying still long enough to count as the desk (PostureTracker.SETTLE_MS), then lifted.
        assertTrue(r.hold(onDesk, 0, 5000, jitter = 0.05f).isEmpty())
        assertEquals(Posture.DESK, r.heldAs)
        val picked = r.hold(inHand, 5000, 2000, jitter = 0.5f)
        assertEquals(1, picked.size)
        assertEquals(Posture.HAND, r.heldAs)
    }

    @Test
    fun `a hand reaching past the phone is no pick-up`() {
        val r = Reactions()
        r.hold(onDesk, 0, 5000, jitter = 0.05f)
        // On the desk, then in the hand for less than GREET_AFTER_MS, then down again: the first lift counts, the
        // moment it is put back does not, and a lift right after that is too soon to count again.
        val first = r.hold(inHand, 5000, 500, jitter = 0.5f)
        assertEquals(1, first.size)
        val down = r.hold(onDesk, 5500, 2000, jitter = 0.05f)
        assertTrue(down.isEmpty())
        assertTrue("picked up again before the pose settled", r.hold(inHand, 7500, 400, jitter = 0.5f).isEmpty())
    }

    @Test
    fun `the screen turning off with the phone in hand counts as picking it up`() {
        val r = Reactions()
        // Reading in another app: the screen is on and unlocked, so the phone is not the pet's.
        assertTrue(r.hold(inHand, 0, 2000, jitter = 0.5f, screenOn = true, unlocked = true).isEmpty())
        assertEquals(Posture.USING_PHONE, r.heldAs)
        // The screen goes off and the phone stays in the hand: checked again a moment later (GREET_CHECK_MS).
        assertTrue(r.hold(inHand, 2000, Reactions.GREET_CHECK_MS, jitter = 0.5f).isEmpty())
        val noticed = r.hold(inHand, 2000 + Reactions.GREET_CHECK_MS, 400, jitter = 0.5f)
        assertEquals(1, noticed.size)
    }

    @Test
    fun `out of the pocket counts, and our own app in front does not get in the way`() {
        val r = Reactions()
        // In a pocket: the front covered and dark, the phone upright.
        val pocket = Pose(gravityZ = 1f, near = true, lux = 0f)
        assertTrue(r.hold(pocket, 0, 5000, jitter = 0.3f).isEmpty())
        assertEquals(Posture.AWAY, r.heldAs)
        // Out and into the hand, our own app open: still the pet's phone.
        val picked = r.hold(inHand, 5000, 1000, jitter = 0.5f, screenOn = true, unlocked = true, appInFront = true)
        assertEquals(1, picked.size)
        assertEquals(Posture.HAND, r.heldAs)
    }

    @Test
    fun `the sensors starting again is no pick-up`() {
        val r = Reactions()
        r.hold(onDesk, 0, 5000, jitter = 0.05f)
        r.reset()
        assertEquals("a reset reads as the hand, where it started", Posture.HAND, r.heldAs)
        assertTrue(r.hold(inHand, 5000, 1000, jitter = 0.5f).isEmpty())
    }

    @Test
    fun `before the accelerometer has said anything the phone counts as held`() {
        val r = Reactions()
        assertTrue(r.hold(null, 0, 1000, jitter = 0f).isEmpty())
        assertEquals(Posture.HAND, r.heldAs)
    }

    // --- The gesture list the tests above sweep ------------------------------------------------------

    private fun everyGesture(): List<Gestures> = listOf(
        Gestures(shake = true),
        Gestures(twirl = true),
        Gestures(rock = true),
        Gestures(knocks = 1),
        Gestures(knocks = 2),
        Gestures(pickedUp = true),
    )

    @Test
    fun `an empty frame asks nothing of the pet`() {
        for (mood in Mood.entries) for (posture in Posture.entries) {
            assertFalse(
                "$mood $posture",
                Reactions().frame(1000, Gestures(), awake.copy(mood = mood, posture = posture)).isNotEmpty(),
            )
        }
    }
}
