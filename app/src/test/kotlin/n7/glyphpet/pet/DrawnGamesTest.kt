package n7.glyphpet.pet

import n7.glyphpet.pet.drawn.Drawn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Every drawn hero's game or toy plays: it draws, it moves with the lean, and a game can be won by leaning about. */
class DrawnGamesTest {
    private val toys = setOf("phoenix", "dragon", "pebble", "sprout", "astronaut")

    private fun play(kind: String, lean: (Int) -> Pair<Float, Float>): Pair<Int, Int> {
        val s = SpeciesForms.of(kind, Stage.TEEN)
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        e.haptics = mutableListOf()
        e.tick(80, Scene(species = s, mood = Mood.CONTENT))
        assertTrue(kind, e.trick(kind, seed = 3))
        var points = 0; var lit = 0
        for (i in 0 until 700) {
            val (tx, ty) = lean(i)
            val f = e.tick(80, Scene(species = s, mood = Mood.CONTENT, tilt = tx, tiltY = ty))
            lit += f.count { it > 0 }
            points += e.haptics!!.count { it.kind == HapticKind.BALL_HIT }; e.haptics!!.clear()
        }
        return points to lit
    }

    @Test
    fun `every drawn hero's game can be won by leaning about, and every toy draws`() {
        val bad = mutableListOf<String>()
        for (h in Drawn.all) {
            // Sweeps the lean round in circles of changing size, and now and then holds it still on one side.
            val (points, lit) = play(h.kind) { i ->
                if ((i / 60) % 3 == 2) (if ((i / 180) % 2 == 0) -0.7f else 0.7f) to 0.2f
                else { val a = i / 9f; val k = 0.3f + 0.7f * ((i / 40) % 3) / 2f; cos(a) * k to sin(a) * k }
            }
            // A second player: short nudges with stops in between, wandering the field (games that need a pause on a spot).
            val rnd = Random(9)
            var dir = 0f to 0f
            val (points2, _) = play(h.kind) { i -> if (i % 12 == 0) dir = if (rnd.nextFloat() < 0.45f) 0f to 0f else (rnd.nextFloat() * 1.6f - 0.8f) to (rnd.nextFloat() * 1.6f - 0.8f); dir }
            if (lit < 700) bad += "${h.kind}: dark"
            if (h.kind !in toys && maxOf(points, points2) < 2) bad += "${h.kind}: $points / $points2 points"
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
    }
}
