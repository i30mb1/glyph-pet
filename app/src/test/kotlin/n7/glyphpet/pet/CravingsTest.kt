package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test

/** A hungry pet thinks of its own food ([Cravings]), and the thought always finds room on the matrix. */
class CravingsTest {
    @Test
    fun `every kind has its own food to think of, and every form has room for the thought`() {
        for (s in SpeciesCatalog.all) assertTrue("${s.id} has no craving", Cravings.has(s.kind))
        val none = SpeciesForms.all.filter { SpeciesRenderer.thoughtSpot(it, 0) == null }.map { it.id }
        assertTrue("no room for the thought: $none", none.isEmpty())
    }

    @Test
    fun `every kind has its own way of showing it sleeps`() {
        for (s in SpeciesCatalog.all) assertTrue("${s.id} has no sleep sign", SleepSigns.has(s.kind))
    }

    @Test
    fun `every form's sleep sign shows on the matrix, beside the pet`() {
        val hidden = mutableListOf<String>()
        for (s in SpeciesForms.all) {
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
        for (s in SpeciesCatalog.all) assertTrue("${s.id} has no dizzy gag", s.kind in Gags.DIZZY_KINDS)
        for (s in SpeciesForms.all) {
            val looks = (0 until 2400L step 100).map { SpeciesRenderer.render(s, Face.DIZZY, timeMs = it).toList() }.toSet()
            assertTrue("${s.id}: the gag does not move", looks.size >= 2)
        }
    }

    @Test
    fun `every kind has its own way of being tired, sad and dizzy`() {
        for (s in SpeciesCatalog.all) assertTrue("${s.id} lacks a mood sign", MoodSigns.hasAll(s.kind))
    }
}
