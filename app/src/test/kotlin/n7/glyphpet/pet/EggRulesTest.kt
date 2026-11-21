package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EggRulesTest {

    @Test
    fun `each press cracks the egg one stage further`() {
        assertEquals(5, EggRules.PRESSES_TO_HATCH)
        assertEquals(
            listOf(EggStage.WHOLE, EggStage.CRACKED, EggStage.CRACKED_MORE, EggStage.HATCHING, EggStage.HATCHING),
            (0 until EggRules.PRESSES_TO_HATCH).map(EggRules::stage),
        )
    }

    @Test
    fun `rarity odds over the whole catalogue are the tiers' own weights`() {
        val random = Random(42)
        val counts = Rarity.entries.associateWith { 0 }.toMutableMap()
        repeat(20_000) {
            val rarity = SpeciesCatalog.roll(random).rarity
            counts[rarity] = counts.getValue(rarity) + 1
        }
        val total = counts.values.sum().toFloat()
        // Each tier comes as often as it weighs, however many heroes it holds: 70 / 24.5 / 4 / 1.5 percent.
        val weights = Rarity.entries.sumOf { it.weight }.toFloat()
        for (tier in Rarity.entries) {
            assertEquals(tier.name, tier.weight / weights, counts.getValue(tier) / total, 0.01f)
        }
    }

    @Test
    fun `the catalog has 29 pets in four tiers`() {
        assertEquals(29, SpeciesCatalog.sprites.size)
        assertEquals(10, SpeciesCatalog.sprites.count { it.rarity == Rarity.COMMON })
        assertEquals(13, SpeciesCatalog.sprites.count { it.rarity == Rarity.RARE })
        assertEquals(4, SpeciesCatalog.sprites.count { it.rarity == Rarity.LEGENDARY })
        assertEquals(2, SpeciesCatalog.sprites.count { it.rarity == Rarity.MYTHIC })
    }

    private fun draw(f: (IntArray) -> Unit) = IntArray(625).also(f)

    private fun assertInside(what: String, frame: IntArray) {
        for (i in frame.indices) {
            assertTrue("$what: ${frame[i]}", frame[i] in 0..4095)
            if (frame[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    @Test
    fun `every stage of the egg looks different, golden or not`() {
        for (golden in listOf(false, true)) {
            val big = EggStage.entries.map { st -> draw { EggRenderer.big(it, EggView(st, golden)) }.toList() }
            assertEquals(4, big.toSet().size)
            for ((i, f) in big.withIndex()) assertInside("golden=$golden $i", f.toIntArray())
            // Neighbouring stages differ by at least two LEDs, so a press shows from 30 cm.
            big.zipWithNext().forEach { (a, b) -> assertTrue(a.indices.count { a[it] != b[it] } >= 2) }
        }
        // A golden egg glows: brighter than a plain one.
        val plain = draw { EggRenderer.big(it, EggView(EggStage.WHOLE, false)) }
        val gold = draw { EggRenderer.big(it, EggView(EggStage.WHOLE, true)) }
        assertTrue(gold.sum() > plain.sum() * 2)
    }

    @Test
    fun `the egg wobbles inside the round matrix`() {
        for (st in EggStage.entries) {
            for (w in -1..1) assertInside("big $st $w", draw { EggRenderer.big(it, EggView(st, true), w) })
        }
    }
}
