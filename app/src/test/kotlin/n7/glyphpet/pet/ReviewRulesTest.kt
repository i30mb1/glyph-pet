package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Reviewer's attacks on the 1.2 rules: mood order, the button, habits, the peek, random runs, the previews. */
class ReviewRulesTest {

    /** The mood order exactly as the brief writes it. */
    private fun expectedMood(s: Float, j: Float, e: Float, asleep: Boolean): Mood = when {
        asleep -> Mood.SLEEPING
        s < 5f && j < 5f -> Mood.SICK
        s < 25f -> Mood.HUNGRY
        e < 20f -> Mood.TIRED
        j < 25f -> Mood.SAD
        j > 70f && s > 50f -> Mood.HAPPY
        else -> Mood.CONTENT
    }

    private val grid = listOf(0f, 1f, 4.99f, 5f, 5.01f, 9.99f, 10f, 19.99f, 20f, 24.99f, 25f, 49.99f, 50f, 50.01f, 70f, 70.01f, 89.99f, 90f, 100f)

    @Test
    fun `mood follows the brief's order on every boundary, SICK right after SLEEPING`() {
        for (s in grid) for (j in grid) for (e in grid) for (asleep in listOf(false, true)) {
            val stats = PetStats(s, j, e, 0, 0)
            assertEquals("s=$s j=$j e=$e asleep=$asleep", expectedMood(s, j, e, asleep), PetRules.mood(stats, asleep))
        }
        assertEquals(5f, PetRules.SICK_SATIETY)
        assertEquals(5f, PetRules.SICK_JOY)
    }

    @Test
    fun `one meal always cures a sick pet and is never refused`() {
        for (s in listOf(0f, 2.5f, 4.99f)) for (j in listOf(0f, 2.5f, 4.99f)) for (e in listOf(0f, 15f, 50f, 100f)) {
            val sick = PetStats(s, j, e, 0, 0)
            assertEquals(Mood.SICK, PetRules.mood(sick, asleep = false))
            val (fed, ate) = PetRules.feed(sick)
            assertTrue(ate)
            assertTrue("s=$s j=$j e=$e", PetRules.mood(fed, asleep = false) != Mood.SICK)
        }
    }

    @Test
    fun `the button for every mood, with and without an egg and a pet, at every Food level`() {
        // The owner's order: an egg is cracked first (also at night and when hungry), then a sleeper stirs, a hungry
        // (or sick) pet eats, Food below 70 eats, otherwise a stroke. Without pet or egg nothing happens.
        for (pet in listOf(false, true)) for (mood in Mood.entries) for (egg in listOf(false, true)) for (s in grid) {
            val expected = when {
                egg -> ButtonAction.CRACK_EGG
                !pet -> ButtonAction.NOTHING
                mood == Mood.SLEEPING -> ButtonAction.PET
                mood == Mood.HUNGRY || mood == Mood.SICK -> ButtonAction.FEED
                s < 70f -> ButtonAction.FEED
                else -> ButtonAction.PET
            }
            assertEquals("pet=$pet $mood egg=$egg s=$s", expected, ButtonRules.decide(hasPet = pet, mood = mood, eggWaiting = egg, satiety = s))
        }
    }

    @Test
    fun `a sleeper is never fed or played with by the button, so the peek leaves the needs alone`() {
        // The toy only calls PetRules.play when the mood is not SLEEPING and only feeds on FEED: the button never says FEED to a sleeper.
        for (egg in listOf(false, true)) for (s in grid) {
            val action = ButtonRules.decide(hasPet = true, mood = Mood.SLEEPING, eggWaiting = egg, satiety = s)
            assertEquals(if (egg) ButtonAction.CRACK_EGG else ButtonAction.PET, action)
        }
    }

    private fun assertValid(what: String, f: IntArray) {
        assertEquals(625, f.size)
        for (i in f.indices) {
            assertTrue("$what: value ${f[i]}", f[i] in 0..4095)
            if (f[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} off the round matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    @Test
    fun `no habit during eating, refusing, celebrating, a peek, falling food, tired or sad`() {
        for (s in SpeciesCatalog.all) {
            val cases = mapOf<String, Pair<Mood, (Long, PetEngine) -> Unit>>(
                "eating" to (Mood.CONTENT to { t, e -> if (t % 1600 == 80L) e.feed(accepted = true) }),
                "refusing" to (Mood.HAPPY to { t, e -> if (t % 1120 == 80L) e.feed(accepted = false) }),
                "celebrating" to (Mood.HAPPY to { t, e -> if (t % 2800 == 80L) e.celebrate() }),
                "tired" to (Mood.TIRED to { _, _ -> }),
                "sad" to (Mood.SAD to { _, _ -> }),
                "peek" to (Mood.SLEEPING to { t, e -> if (t % 800 == 80L) e.pet() }),
            )
            for ((what, case) in cases) {
                val (mood, poke) = case
                val engine = PetEngine(Random(41))
                var t = 0L
                while (t < 20_000) {
                    t += 80
                    poke(t, engine)
                    if (t % 400 == 0L) engine.trick()
                    assertValid("${s.id} $what", engine.tick(80, Scene(s, mood)))
                    assertFalse("${s.id} $what t=$t habit", engine.shown.habit)
                }
            }
        }
    }

    @Test
    fun `a habit that is playing stops at once when charging, sleep, a tilt or another mood comes`() {
        for (s in SpeciesCatalog.all) {
            val interrupts = listOf<Pair<String, Scene>>(
                "charging" to Scene(s, Mood.CONTENT, charging = true, watts = 15f, battery = 0.4f),
                "asleep" to Scene(s, Mood.SLEEPING),
                "tilt" to Scene(s, Mood.CONTENT, tilt = 1f),
                "hungry" to Scene(s, Mood.HUNGRY),
                "sick" to Scene(s, Mood.SICK),
            )
            for ((what, scene) in interrupts) {
                val engine = PetEngine(Random(42), autoHabits = false)
                engine.trick()
                engine.tick(80, Scene(s, Mood.CONTENT))
                assertTrue("${s.id} habit started", engine.shown.habit)
                engine.tick(80, scene)
                assertFalse("${s.id} $what", engine.shown.habit)
                // and it does not come back by itself once the interruption ends (no stale trick)
                repeat(10) { engine.tick(80, scene) }
                engine.tick(80, Scene(s, Mood.CONTENT))
                assertFalse("${s.id} after $what", engine.shown.habit)
            }
        }
    }

    @Test
    fun `the peek lasts about a second however it is started, and repeated pokes keep one eye open`() {
        for (s in SpeciesCatalog.all) for (charging in listOf(false, true)) {
            val scene = Scene(s, Mood.SLEEPING, charging = charging, watts = 10f, battery = 0.5f)
            for (poke in listOf<(PetEngine) -> Unit>({ it.pet() }, { it.shake() }, { it.feed(true) }, { it.feed(false) }, { it.celebrate() })) {
                val engine = PetEngine(Random(43))
                repeat(3) { engine.tick(80, scene) }
                val asleepFrame = engine.tick(80, scene)
                poke(engine)
                var ms = 0L
                var first: IntArray? = null
                while (ms < 3000) {
                    val f = engine.tick(80, scene)
                    if (first == null) first = f
                    if (!charging) { if (engine.shown.face != Face.PEEK) break } else if (f.contentEquals(asleepFrame)) break
                    ms += 80
                }
                assertFalse("${s.id} charging=$charging peek changed nothing", first!!.contentEquals(asleepFrame))
                assertTrue("${s.id} charging=$charging peek $ms ms", ms in 880L..1120L)
                if (!charging) assertEquals(Face.SLEEP, engine.shown.face)
            }
        }
    }

    @Test
    fun `long random runs only make valid frames and consistent states`() {
        val moods = Mood.entries
        for (seed in 0 until 6) for (s in SpeciesCatalog.all) {
            val r = Random(1000 + seed * 31 + s.id.hashCode())
            val engine = PetEngine(Random(seed), autoHabits = seed % 2 == 0)
            var mood = Mood.CONTENT
            var charging = false
            var egg: EggView? = null
            var tilt = 0f
            var peekMs = 0L
            repeat(3000) { step ->
                val roll = r.nextInt(60)
                if (roll <= 4) peekMs = 0 // a new poke may start the peek again
                when (roll) {
                    0 -> engine.feed(r.nextBoolean())
                    1 -> engine.pet()
                    2 -> engine.shake()
                    3 -> engine.trick()
                    4 -> engine.celebrate()
                    5 -> mood = moods[r.nextInt(moods.size)]
                    6 -> charging = !charging
                    7 -> egg = if (egg == null) EggView(EggStage.entries[r.nextInt(EggStage.entries.size)], r.nextBoolean()) else null
                    8 -> tilt = listOf(0f, 0f, 0.2f, -0.5f, 1f, -1f)[r.nextInt(6)]
                }
                val dt = listOf(0L, 16L, 80L, 80L, 80L, 120L, 250L)[r.nextInt(7)]
                val scene = Scene(s, mood, tilt = tilt, charging = charging, egg = egg, watts = r.nextFloat() * 60f, battery = r.nextFloat())
                val f = engine.tick(dt, scene)
                val what = "seed=$seed ${s.id} step=$step $mood charging=$charging"
                assertValid(what, f)
                assertTrue("$what nothing lit", f.count { it > 0 } > 15)
                val sh = engine.shown
                if (charging) {
                    assertTrue(what, sh.charging && !sh.habit && sh.lift == 0)
                } else {
                    if (sh.habit) assertTrue("$what habit in $mood", mood == Mood.CONTENT || mood == Mood.HAPPY)
                    if (sh.lift > 0) assertEquals("$what hop", Mood.HAPPY, mood)
                    if (mood == Mood.SLEEPING) assertTrue("$what ${sh.face}", sh.face == Face.SLEEP || sh.face == Face.PEEK)
                    if (sh.face == Face.PEEK) { peekMs += dt; assertTrue("$what peek $peekMs", peekMs <= 1000 + 250) } else peekMs = 0
                    if (mood != Mood.SLEEPING) assertTrue("$what peek awake", sh.face != Face.PEEK)
                }
            }
        }
    }

    @Test
    fun `Always-on stills are valid for every species, mood, charger and egg`() {
        for (s in SpeciesCatalog.all) for (mood in Mood.entries) for (charging in listOf(false, true)) for (egg in listOf(null, EggView(EggStage.HATCHING, true))) {
            val f = PetEngine().still(Scene(s, mood, charging = charging, egg = egg, watts = 20f, battery = 0.3f))
            assertValid("${s.id} $mood charging=$charging egg=${egg != null}", f)
        }
    }

    @Test
    fun `every preview loops, the signature shows within each loop, for 10 loops`() {
        for (s in SpeciesCatalog.all) for (st in PetState.entries) {
            val p = StatePreview(s, st)
            val span = if (p.loopMs > 0) 10 * p.loopMs else 30_000L
            val shown = mutableListOf<Shown>()
            var t = 0L
            while (t < span) {
                assertValid("${s.id} ${st.id}", p.tick(80))
                t += 80
                shown += p.shown
            }
            assertTrue("${s.id} ${st.id} never shows its state", shown.any { st.isShownBy(it) })
            if (p.loopMs > 0) {
                val per = (p.loopMs / 80).toInt()
                for (k in 0 until 10) {
                    val w = shown.subList(k * per, minOf((k + 1) * per, shown.size))
                    if (w.size < per) continue
                    assertTrue("${s.id} ${st.id} loop $k", w.any { st.isShownBy(it) })
                }
            } else {
                // states without a cue hold their state nearly all the time, forever
                val share = shown.count { st.isShownBy(it) }.toFloat() / shown.size
                assertTrue("${s.id} ${st.id} share $share", share >= if (st == PetState.TIRED || st == PetState.HAPPY) 0.75f else 0.95f)
            }
        }
    }

    @Test
    fun `a preview never shows another state's claim where it should not`() {
        for (s in SpeciesCatalog.all) {
            for (st in listOf(PetState.CALM, PetState.HUNGRY, PetState.SAD, PetState.SICK, PetState.TIRED)) {
                val p = StatePreview(s, st)
                repeat(200) {
                    p.tick(80)
                    assertFalse("${s.id} ${st.id} habit", p.shown.habit)
                    assertTrue("${s.id} ${st.id} lift", p.shown.lift == 0)
                    assertFalse("${s.id} ${st.id} charging", p.shown.charging)
                }
            }
        }
    }
}
