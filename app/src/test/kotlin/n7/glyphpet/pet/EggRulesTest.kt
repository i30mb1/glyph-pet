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
    fun `rarity odds over the whole catalogue are close to 70, 25 and 5 percent`() {
        val random = Random(42)
        val counts = Rarity.entries.associateWith { 0 }.toMutableMap()
        repeat(20_000) {
            val rarity = SpeciesCatalog.roll(random).rarity
            counts[rarity] = counts.getValue(rarity) + 1
        }
        val total = counts.values.sum().toFloat()
        assertEquals(0.70f, counts.getValue(Rarity.COMMON) / total, 0.02f)
        assertEquals(0.25f, counts.getValue(Rarity.RARE) / total, 0.02f)
        assertEquals(0.05f, counts.getValue(Rarity.LEGENDARY) / total, 0.01f)
    }

    @Test
    fun `the catalog has 18 pets in three tiers`() {
        assertEquals(18, SpeciesCatalog.all.size)
        assertEquals(10, SpeciesCatalog.all.count { it.rarity == Rarity.COMMON })
        assertEquals(5, SpeciesCatalog.all.count { it.rarity == Rarity.RARE })
        assertEquals(3, SpeciesCatalog.all.count { it.rarity == Rarity.LEGENDARY })
    }

    private fun draw(f: (IntArray) -> Unit) = IntArray(625).also(f)

    private fun assertInside(what: String, frame: IntArray) {
        for (i in frame.indices) {
            assertTrue("$what: ${frame[i]}", frame[i] in 0..4095)
            if (frame[i] > 0) assertTrue("$what: LED ${i % 25},${i / 25} off the matrix", GlyphMatrixSpec.isVisible(i % 25, i / 25))
        }
    }

    @Test
    fun `every stage of the egg looks different, big and small, golden or not`() {
        for (golden in listOf(false, true)) {
            val big = EggStage.entries.map { st -> draw { EggRenderer.big(it, EggView(st, golden)) }.toList() }
            val small = EggStage.entries.map { st -> draw { EggRenderer.mini(it, EggView(st, golden), EggLayout.EDGE) }.toList() }
            assertEquals(4, big.toSet().size)
            assertEquals(4, small.toSet().size)
            for ((i, f) in (big + small).withIndex()) assertInside("golden=$golden $i", f.toIntArray())
            // Neighbouring stages differ by at least two LEDs, so a press shows from 30 cm.
            for (list in listOf(big, small)) list.zipWithNext().forEach { (a, b) -> assertTrue(a.indices.count { a[it] != b[it] } >= 2) }
        }
        // A golden egg glows: brighter than a plain one.
        val plain = draw { EggRenderer.mini(it, EggView(EggStage.WHOLE, false), EggLayout.EDGE) }
        val gold = draw { EggRenderer.mini(it, EggView(EggStage.WHOLE, true), EggLayout.EDGE) }
        assertTrue(gold.sum() > plain.sum() * 2)
    }

    @Test
    fun `the egg wobbles inside the round matrix, the small one rocks without losing an LED`() {
        for (st in EggStage.entries) {
            for (w in -1..1) assertInside("big $st $w", draw { EggRenderer.big(it, EggView(st, true), w) })
            for (spot in listOf(EggLayout.EDGE, EggLayout.CHARGER)) {
                val upright = draw { EggRenderer.mini(it, EggView(st, true), spot) }
                val tipped = draw { EggRenderer.mini(it, EggView(st, true), spot, tipped = true) }
                assertInside("small $st", upright)
                assertInside("small tipped $st", tipped)
                assertTrue("$st rocks", !upright.contentEquals(tipped))
                assertEquals("$st: the same LEDs count, nothing cut off", upright.count { it > 0 }, tipped.count { it > 0 })
            }
        }
        assertTrue(EggRenderer.fits(EggLayout.EDGE))
        assertTrue(EggRenderer.fits(EggLayout.CHARGER))
    }
}
