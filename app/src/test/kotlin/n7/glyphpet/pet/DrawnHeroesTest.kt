package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Drawn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.math.abs

/** The drawn heroes ([Drawn]): every state of every form shows on the matrix and moves; the engine plays them all. */
class DrawnHeroesTest {

    @Test
    fun `every drawn hero draws every state at every stage, inside the matrix, and it moves`() {
        val bad = mutableListOf<String>()
        // The little worlds are shared: another test's wind may have just popped the bubble, which would hold its pop
        // for the whole clip here.
        n7.glyphpet.pet.drawn.DrawnWorld.poppedAt = Long.MIN_VALUE / 2
        n7.glyphpet.pet.drawn.DrawnWorld.bubble = 0.45f
        for (h in Drawn.all) for (st in Stage.entries) for ((col, ms) in Drawn.COLS) {
            val frames = (0 until ms step Drawn.STEP).map { Drawn.frame(h.kind, col, it, st, if (col == "tilt" || col == "game") 0.8f * kotlin.math.sin(it / 600f) else 0f).toList() }
            if (frames.all { f -> f.none { it > 0 } }) bad += "${h.kind}/${st.name}/$col: dark"
            if (frames.toSet().size < 2) bad += "${h.kind}/${st.name}/$col: still"
            frames.forEach { f -> f.forEachIndexed { i, v -> if (v > 0 && !GlyphMatrixSpec.isVisible(i % 25, i / 25)) bad += "${h.kind}/$col: off the matrix" } }
        }
        assertTrue(bad.distinct().take(20).joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `drawn heroes are in the catalogue, with a game, a signature and a meal, and anomalies never come in the daily egg`() {
        for (h in Drawn.all) {
            assertTrue(h.kind, SpeciesCatalog.all.any { it.kind == h.kind })
            assertTrue(h.kind, Tricks.has(h.kind) && Signatures.has(h.kind) && Feasts.has(h.kind))
        }
        val missing = NurseryRules.missing(NurseryState())
        assertTrue(missing.none { it.rarity == Rarity.ANOMALY })
        assertTrue(Drawn.anomalies.all { AnomalyUnlocks.HINTS_EN.containsKey(it.kind) && it.unlock.isNotBlank() })
    }

    @Test
    fun `the engine shows a drawn hero in every mood, on the charger and through its reactions`() {
        for (h in Drawn.all) {
            val s = SpeciesForms.of(h.kind, Stage.TEEN)
            for (mood in Mood.entries) {
                val e = PetEngine(Random(1))
                val f = e.tick(80, Scene(species = s, mood = mood))
                assertTrue("${h.kind} $mood", f.any { it > 0 })
            }
            val e = PetEngine(Random(2))
            e.tick(80, Scene(species = s, mood = Mood.CONTENT))
            e.pet(); assertTrue(e.tick(400, Scene(species = s, mood = Mood.CONTENT)).any { it > 0 })
            assertTrue(e.trick(h.kind)); assertTrue(e.tick(400, Scene(species = s, mood = Mood.CONTENT)).any { it > 0 })
            assertTrue(e.tick(80, Scene(species = s, mood = Mood.CONTENT, charging = true)).any { it > 0 })
        }
    }

    @Test
    fun `anomaly conditions`() {
        val base = AnomalyUnlocks.Facts(false, 0.5f, null, emptySet(), 0, 0L, false, 0L)
        assertTrue(AnomalyUnlocks.met("phoenix", base.copy(charging = true, battery = 1f)))
        assertTrue(!AnomalyUnlocks.met("phoenix", base.copy(charging = true, battery = 0.8f)))
        assertTrue(AnomalyUnlocks.met("swarm", base.copy(collected = setOf("bee", "bat", "owl", "ufo"))))
        assertTrue(AnomalyUnlocks.met("lavalamp", base.copy(desk = true, deskMs = AnomalyUnlocks.LAVA_STILL_MS)))
        assertTrue(AnomalyUnlocks.progress("dragon", base.copy(adults = 2)) == (2 to 5))
        // Some day within a month is a full moon.
        assertTrue(AnomalyUnlocks.daysToFullMoon(System.currentTimeMillis()) <= 30)
    }
}

/** A sad pet's rain does not stop while it eats: food does not cheer it up. */
class SadMealTest {
    @Test
    fun `the rain keeps falling through a sad pet's meal`() {
        val broken = mutableListOf<String>()
        for (s in SpeciesCatalog.sprites) {
            fun run(mood: Mood): List<IntArray> {
                val e = PetEngine(Random(7), autoHabits = false, autoSignatures = false)
                e.tick(80, Scene(species = s, mood = mood))
                e.feed(accepted = true)
                return (0 until 20).map { e.tick(80, Scene(species = s, mood = mood)).copyOf() }
            }
            val sad = run(Mood.SAD); val calm = run(Mood.CONTENT)
            val dry = sad.indices.count { i -> sad[i].indices.count { sad[i][it] > 0 && calm[i][it] == 0 } < 4 }
            if (dry > 2) broken += "${s.kind}: $dry dry frames"
        }
        assertTrue(broken.joinToString(), broken.isEmpty())
    }
}

/** A happy pet's bursts of joy are spaced out like a habit, not every few seconds. */
class JoySpacingTest {
    @Test
    fun `a happy pet bursts with joy two to six times a minute`() {
        val bad = mutableListOf<String>()
        for (s in SpeciesForms.all.filter { it.stage == Stage.TEEN }) {
            val e = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
            var bursts = 0; var was = false
            repeat((60_000 / 80).toInt()) {
                e.tick(80, Scene(species = s, mood = Mood.HAPPY))
                val now = e.shown.cheer
                if (now && !was) bursts++
                was = now
            }
            if (bursts !in 2..6) bad += "${s.kind}: $bursts"
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
    }
}

/** An adult's minutes together are not lost: every [XpRules.EGG_MINUTES] bring an egg of a hero not collected yet. */
class AdultEggTest {
    private val day = 86_400_000L
    private fun withAdult(): NurseryState {
        val kind = "cat"
        val g = Growth(Stage.ADULT, Stage.ADULT, xp = 0, ready = false, sinceDay = 1)
        return NurseryState(active = kind, seen = mapOf(kind to 1), growth = mapOf(kind to g), xpAtMs = 10 * day)
    }

    private fun minutes(s: NurseryState, n: Int): NurseryState {
        var state = s
        for (m in 1..n) state = GrowthRules.together(state, 10 * day + m * XpRules.MINUTE_MS, gains = true, perMinute = 1).first
        return state
    }

    @Test
    fun `thirty minutes with an adult bring exactly one egg`() {
        val half = minutes(withAdult(), 15)
        assertTrue("${half.eggs} ${half.eggMinutes}", half.eggs.isEmpty() && half.eggMinutes == 15)
        val full = minutes(withAdult(), 30)
        assertTrue("${full.eggs} ${full.eggMinutes}", full.eggs.size == 1 && full.eggMinutes == 0)
        assertTrue(SpeciesCatalog.get(full.eggs.single())!!.rarity != Rarity.ANOMALY)
    }

    @Test
    fun `a baby's minutes go to growing, not to an egg, and a full collection brings none`() {
        val b = minutes(withAdult().copy(growth = mapOf("cat" to Growth(Stage.BABY, Stage.BABY, 0, false, 1))), 30)
        assertTrue(b.eggs.isEmpty() && b.eggMinutes == 0)
        val a = minutes(withAdult().copy(seen = SpeciesCatalog.all.associate { it.id to 1 }), 30)
        assertTrue(a.eggs.isEmpty() && a.eggMinutes == 0)
    }
}

/** A change of state dissolves instead of cutting: the old frame fades out under the new one. */
class StateFadeTest {
    @Test
    fun `the frame after a change of mood still holds the old one, fading, and it is gone soon after`() {
        for (s in listOf(SpeciesForms.of("cat", Stage.TEEN), SpeciesForms.of("phoenix", Stage.TEEN))) {
            val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            var before = IntArray(625)
            repeat(10) { before = e.tick(80, Scene(species = s, mood = Mood.CONTENT)).copyOf() }
            val first = e.tick(80, Scene(species = s, mood = Mood.SAD))
            assertTrue(s.kind, before.indices.all { before[it] == 0 || first[it] >= before[it] * 0.6f })
            repeat(5) { e.tick(80, Scene(species = s, mood = Mood.SAD)) }
            val fresh = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            assertTrue(s.kind, e.tick(80, Scene(species = s, mood = Mood.SAD)).count { it > 0 } <= fresh.tick(80, Scene(species = s, mood = Mood.SAD)).count { it > 0 } + 60)
        }
    }
}

/** On the matrix a drawn hero moves at the full frame rate, not on the atlas's 160 ms beat. */
class DrawnFrameRateTest {
    @Test
    fun `frames between two atlas steps are frames of their own`() {
        val a = Drawn.frame("phoenix", "calm", 1600, Stage.TEEN)
        val b = Drawn.frame("phoenix", "calm", 1640, Stage.TEEN)
        assertTrue(!a.contentEquals(b))
    }
}

/** Feeding a hungry or a sad pet flows into its meal: no frame jumps away from the one before. */
class MealTransitionTest {
    @Test
    fun `a hungry or sad pet flows into its meal without a jump`() {
        val bad = mutableListOf<String>()
        for (s in SpeciesForms.all.filter { it.stage == Stage.TEEN }) for (mood in listOf(Mood.HUNGRY, Mood.SAD)) {
            val e = PetEngine(Random(2), autoHabits = false, autoSignatures = false)
            var prev = IntArray(625)
            repeat(12) { prev = e.tick(80, Scene(species = s, mood = mood)).copyOf() }
            e.feed(accepted = true)
            repeat(4) {
                val f = e.tick(80, Scene(species = s, mood = mood)).copyOf()
                // A jump is the light changing at once or the picture leaping away; things flying along are motion.
                val sf = f.sum().toDouble(); val sp = prev.sum().toDouble()
                fun centre(a: IntArray): Pair<Double, Double> { val tot = maxOf(1.0, a.sum().toDouble()); return a.indices.sumOf { (it % 25) * a[it].toDouble() } / tot to a.indices.sumOf { (it / 25) * a[it].toDouble() } / tot }
                val (fx, fy) = centre(f); val (px, py) = centre(prev)
                val leap = kotlin.math.hypot(fx - px, fy - py)
                if (abs(sf - sp) > 0.4 * maxOf(sf, sp) || leap > 3.0) bad += "${s.kind}/$mood: light ${(abs(sf - sp) * 100 / maxOf(1.0, maxOf(sf, sp))).toInt()}% leap ${"%.1f".format(leap)}"
                prev = f
            }
        }
        assertTrue(bad.distinct().joinToString(), bad.isEmpty())
    }
}

/** Hungry and sad at once: hunger shows, in the rain of the sadness, and the rain goes on through the meal. */
class HungryAndSadTest {
    @Test
    fun `hungry and sad shows the rain, hungry alone does not`() {
        val st = PetStats(satiety = 10f, joy = 10f, energy = 80f, bornAtMs = 0, updatedAtMs = 0)
        assertTrue(PetRules.alsoSad(st, false))
        assertTrue(!PetRules.alsoSad(st.copy(joy = 60f), false))
        assertTrue(!PetRules.alsoSad(st.copy(satiety = 80f), false))
        for (s in SpeciesForms.sprites.filter { it.stage == Stage.TEEN }) {
            fun lit(alsoSad: Boolean): Int {
                val e = PetEngine(Random(4), autoHabits = false, autoSignatures = false)
                var n = 0
                repeat(20) { n += e.tick(80, Scene(species = s, mood = Mood.HUNGRY, alsoSad = alsoSad)).count { it > 0 } }
                return n
            }
            assertTrue(s.kind, lit(true) > lit(false) + 20)
        }
    }
}

/** A drawn hero asleep reads at once: dimmer than awake, its sign of sleep rising; touched, it peeks with a "?". */
class DrawnSleepTest {
    @Test
    fun `asleep is dimmer than calm, and a touch while asleep shows a peek`() {
        val bad = mutableListOf<String>()
        for (h in Drawn.all) {
            val s = SpeciesForms.of(h.kind, Stage.TEEN)
            // The hero's own LEDs are dimmed: compared with its sleeping clip as drawn, on the cells the clip lights.
            run {
                val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
                var ratio = 0.0; var n = 0
                for (k in 1..40) {
                    val f = e.tick(80, Scene(species = s, mood = Mood.SLEEPING))
                    val raw = Drawn.frame(h.kind, "asleep", k * 80L, Stage.TEEN)
                    for (i in raw.indices) if (raw[i] > 400) { ratio += f[i].toDouble() / raw[i]; n++ }
                }
                if (n > 0 && ratio / n > 0.8) bad += "${h.kind}: asleep not dimmer (${"%.2f".format(ratio / n)})"
            }
            val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
            var before = IntArray(625)
            repeat(20) { before = e.tick(80, Scene(species = s, mood = Mood.SLEEPING)).copyOf() }
            e.feed(accepted = false)
            var peeked = false
            repeat(10) { if (e.shown.face == Face.PEEK || e.tick(80, Scene(species = s, mood = Mood.SLEEPING)).let { false }) peeked = true; e.tick(80, Scene(species = s, mood = Mood.SLEEPING)); if (e.shown.face == Face.PEEK) peeked = true }
            if (!peeked) bad += "${h.kind}: no peek"
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
    }
}
