package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Drawn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Shiny pets ([Shiny]): a rare hatch of a kind it suits, and a shimmer that keeps every pet whole at every stage. */
class ShinyTest {

    @After
    fun reset() {
        Shading.light = Shading.DEFAULT_LIGHT
    }

    @Test
    fun `about one hatch in twenty is shiny, and never a kind it does not suit`() {
        val kinds = SpeciesCatalog.eggable.map { it.id }
        val rolls = 20_000
        var shiny = 0
        var suited = 0
        for (n in 0 until rolls) {
            val id = kinds[n % kinds.size]
            if (Shiny.suits(id)) suited++
            if (NurseryRules.hatchesShiny(id, 1_700_000_000_000L + n * 7919L)) {
                shiny++
                assertTrue("$id hatched shiny", Shiny.suits(id))
            }
        }
        val share = shiny.toFloat() / suited
        assertTrue("shiny share $share", share in 0.035f..0.065f)
        for (k in listOf("shadow", "blackhole", "ghost", "chameleon", "glitch")) assertFalse(Shiny.suits(k))
    }

    @Test
    fun `a shiny hatch is kept in the save`() {
        val id = SpeciesCatalog.eggable.first { Shiny.suits(it.id) }.id
        val at = (0L..100_000L).first { NurseryRules.hatchesShiny(id, it) }
        var s = NurseryState(active = null, eggs = listOf(id), onMatrix = id)
        repeat(EggRules.PRESSES_TO_HATCH) { s = NurseryRules.press(s, at)!! }
        assertEquals(id, s.active)
        assertTrue(id in s.shiny)
    }

    private fun lit(frame: IntArray) = frame.indices.filter { frame[it] >= Shiny.MIN_LIT }.toSet()

    @Test
    fun `the shimmer keeps every pet whole and lights only right beside it`() {
        val problems = mutableListOf<String>()
        for (s in SpeciesForms.all) for (light in listOf(-0.9f, 0f, 0.9f)) for (t in listOf(0L, 700L, 1900L, 3300L)) {
            val plain = if (Drawn.handles(s.kind)) Drawn.frame(s.kind, "calm", t, s.stage) else SpeciesRenderer.render(s, Face.IDLE)
            val f = plain.copyOf()
            Shiny.draw(f, t, light)
            val body = lit(plain)
            for (i in f.indices) {
                val x = i % 25
                val y = i / 25
                if (f[i] > 0 && !GlyphMatrixSpec.isVisible(x, y)) problems += "${s.id}: lit off the matrix at $x,$y"
                if (i in body && f[i] == 0) problems += "${s.id}: put out $x,$y"
                if (plain[i] >= Shiny.CAP && f[i] < plain[i]) problems += "${s.id}: dimmed a bright detail at $x,$y"
                if (plain[i] == 0 && f[i] > 0) {
                    val near = body.any { j -> kotlin.math.abs(j % 25 - x) <= 3 && kotlin.math.abs(j / 25 - y) <= 3 }
                    if (!near) problems += "${s.id}: lit $x,$y away from the pet"
                }
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the shimmer moves with the lean and by itself`() {
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        val plain = SpeciesRenderer.render(s, Face.IDLE)
        fun at(t: Long, light: Float) = plain.copyOf().also { Shiny.draw(it, t, light) }
        assertFalse("still in time", at(0, 0f).contentEquals(at(1300, 0f)))
        // The same moment with the phone leaning the other way: the bands are somewhere else on the body.
        val a = at(100, -0.8f)
        val b = at(100, 0.8f)
        val body = lit(plain)
        assertTrue("the lean does not move it", body.count { a[it] != b[it] } > body.size / 3)
    }

    @Test
    fun `a shiny pet shimmers only as itself, never in a game or as an egg`() {
        val s = SpeciesForms.of("cat", Stage.ADULT)
        val plain = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
        val shiny = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
        var differs = 0
        repeat(30) {
            val a = plain.tick(80, Scene(s, Mood.CONTENT))
            val b = shiny.tick(80, Scene(s, Mood.CONTENT, shiny = true))
            if (!a.contentEquals(b)) differs++
        }
        assertTrue("shiny looked plain", differs > 20)
        val egg = EggView(EggStage.CRACKED, golden = false)
        val ea = PetEngine(Random(3)).tick(80, Scene(s, Mood.CONTENT, egg = egg))
        val eb = PetEngine(Random(3)).tick(80, Scene(s, Mood.CONTENT, egg = egg, shiny = true))
        assertTrue("the egg shimmered", ea.contentEquals(eb))
        if (Tricks.has("cat")) {
            val ga = PetEngine(Random(3), autoHabits = false, autoSignatures = false).apply { trick("cat", seed = 1) }
            val gb = PetEngine(Random(3), autoHabits = false, autoSignatures = false).apply { trick("cat", seed = 1) }
            repeat(10) {
                val a = ga.tick(80, Scene(s, Mood.CONTENT))
                val b = gb.tick(80, Scene(s, Mood.CONTENT, shiny = true))
                assertTrue("the game shimmered", a.contentEquals(b))
            }
        }
    }
}
