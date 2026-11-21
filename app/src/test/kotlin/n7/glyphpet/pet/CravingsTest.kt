package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test

/** A hungry pet thinks of its own food ([Cravings]), and the thought always finds room on the matrix. */
class CravingsTest {
    @Test
    fun `every kind has its own food to think of, and every form has room for the thought`() {
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} has no craving", Cravings.has(s.kind))
        val none = SpeciesForms.sprites.filter { SpeciesRenderer.thoughtSpot(it, 0) == null }.map { it.id }
        assertTrue("no room for the thought: $none", none.isEmpty())
    }

    @Test
    fun `every kind has its own way of showing it sleeps`() {
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} has no sleep sign", SleepSigns.has(s.kind))
    }

    @Test
    fun `every form's sleep sign shows on the matrix, beside the pet`() {
        val hidden = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            // The breathing glow is on the body and the drip runs from the mouth: checked by the still tests.
            if (SleepSigns.of(s.kind) == SleepSigns.Sign.BREATH || SleepSigns.of(s.kind) == SleepSigns.Sign.DRIP) continue
            val body = s.rows.flatMapIndexed { y, row -> row.indices.filter { row[it] != '.' }.map { (s.ox + it) + (s.oy + y) * 25 } }.toSet()
            val shows = (0 until 2400L step 100).any { t ->
                val f = SpeciesRenderer.render(s, Face.SLEEP, timeMs = t)
                f.indices.any { it !in body && f[it] > 0 }
            }
            if (!shows) hidden += s.id
        }
        assertTrue("sleep sign not seen: $hidden", hidden.isEmpty())
    }

    @Test
    fun `every kind has a dizzy gag of its own, and it moves`() {
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} has no dizzy gag", s.kind in Gags.DIZZY_KINDS)
        for (s in SpeciesForms.sprites) {
            val looks = (0 until 2400L step 100).map { SpeciesRenderer.render(s, Face.DIZZY, timeMs = it).toList() }.toSet()
            assertTrue("${s.id}: the gag does not move", looks.size >= 2)
        }
    }

    @Test
    fun `dizzy gags stay crisp - they never smear the pet into in-between brightness`() {
        // The ghost's double and the chameleon's colours change brightness on purpose.
        val own = setOf("ghost", "chameleon")
        val smeared = mutableListOf<String>()
        for (s in SpeciesForms.sprites) {
            if (s.kind in own) continue
            val base = IntArray(625)
            val g = SpeciesRenderer.carve(s, Face.DIZZY)
            g.forEachIndexed { y, row -> row.forEachIndexed { x, c ->
                val v = when (c) { '#' -> SpeciesRenderer.BODY; 'o' -> SpeciesRenderer.BRIGHT; else -> 0 }
                if (v > 0 && n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(s.ox + x, s.oy + y)) base[(s.oy + y) * 25 + s.ox + x] = v
            } }
            val allowed = base.toSet() + setOf(0, SpeciesRenderer.BODY, SpeciesRenderer.BRIGHT)
            for (t in 0 until 2400L step 40) {
                val f = base.copyOf()
                Gags.dizzy(f, s, s.ox, s.oy, t)
                val odd = f.filter { it !in allowed }.toSet()
                if (odd.isNotEmpty()) { smeared += "${s.id}@$t: $odd"; break }
            }
        }
        assertTrue("smeared: $smeared", smeared.isEmpty())
    }

    @Test
    fun `every kind has its own way of being tired, sad and dizzy`() {
        for (s in SpeciesCatalog.sprites) assertTrue("${s.id} lacks a mood sign", MoodSigns.hasAll(s.kind))
    }
}
