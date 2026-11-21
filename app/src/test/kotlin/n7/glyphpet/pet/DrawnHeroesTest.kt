package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Drawn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The drawn heroes ([Drawn]): every state of every form shows on the matrix and moves; the engine plays them all. */
class DrawnHeroesTest {

    @Test
    fun `every drawn hero draws every state at every stage, inside the matrix, and it moves`() {
        val bad = mutableListOf<String>()
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
