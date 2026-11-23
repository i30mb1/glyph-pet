package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wind on the heroes that used to roll or tumble ([WindPoses]): the answer is drawn in whole cells, so no cell is
 * ever a blend of its neighbours. On a monochrome print a blended cell is a brightness the print never had, and the
 * hero turns into grey smear; that is what this guards.
 *
 * The pose is checked on its own, over a still hero, because in a real gust the wind also throws dust and dims the
 * body ([Blows]), which legitimately puts many brightness values on the matrix — measuring the engine's whole frame
 * would blame the pose for the weather.
 */
class WindPosesTest {

    private val posed = SpeciesForms.sprites.filter { WindPoses.has(it.kind) }

    private fun still(s: Species) = SpeciesRenderer.render(s, Face.CONTENT, timeMs = 0, shadow = true)

    /** The six tenths of a second of a gust at [strength], step by step. */
    private fun posed(s: Species, strength: Float): List<IntArray> = (0 until 40).map { i ->
        val f = still(s)
        WindPoses.draw(f, s, WindPoses.of(s.kind)!!, strength, i * 80L, s.ox, s.oy)
        f
    }

    private fun levels(frames: List<IntArray>): Set<Int> = frames.flatMap { f -> f.filter { it > 0 } }.toSet()

    @Test
    fun `every hero that rolled now has a wind pose of its own`() {
        assertEquals(setOf("hedgehog", "planet", "ufo", "bat"), posed.map { it.kind }.toSet())
        // Each its own answer: no two of them share a pose.
        val poses = posed.associate { it.kind to WindPoses.of(it.kind) }
        assertEquals(poses.size, poses.values.toSet().size)
    }

    @Test
    fun `the wind never blends cells into new brightness`() {
        val problems = mutableListOf<String>()
        for (s in posed) {
            val calm = levels(listOf(still(s)))
            for (strength in listOf(0.3f, 0.6f, 1f)) {
                val wind = levels(posed(s, strength))
                // A pose may bring its own few levels (the spines at a quarter brightness); a rotation brings a spread.
                if (wind.size > calm.size + 4) problems += "${s.id} at $strength: ${calm.size} levels still, ${wind.size} in the wind"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The pose may not add debris. Some art has separate parts to begin with — the saucer's three legs are three
     * groups on its own picture — so the wind is only blamed for loose bits it creates beyond what the hero already has.
     */
    @Test
    fun `the wind adds no loose dots to what the hero already has`() {
        val problems = mutableListOf<String>()
        for (s in posed) {
            val own = loose(still(s))
            for (strength in listOf(0.4f, 0.8f, 1f)) for ((i, f) in posed(s, strength).withIndex()) {
                val now = loose(f)
                if (now > own) { problems += "${s.id} at $strength frame $i: $own loose groups still, $now in the wind"; break }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** How many small groups stand apart from the biggest one. */
    private fun loose(f: IntArray): Int {
        val groups = groups(f)
        if (groups.isEmpty()) return 0
        val biggest = groups.maxOf { it.size }
        return groups.count { it.size <= 3 && it.size < biggest }
    }

    @Test
    fun `the wind never lights a cell off the matrix or past its brightness`() {
        for (s in posed) for (strength in listOf(0.05f, 0.5f, 1f)) for (f in posed(s, strength)) for (i in f.indices) {
            if (f[i] > 0) assertTrue("${s.id} lights ${i % 25},${i / 25}", GlyphMatrixSpec.isVisible(i % 25, i / 25))
            assertTrue("${s.id} value ${f[i]}", f[i] in 0..SpeciesRenderer.BRIGHT)
        }
    }

    @Test
    fun `the hero really answers the wind, and settles when it drops`() {
        for (s in posed) {
            val calmFrame = still(s)
            val during = posed(s, 1f)
            val changed = during.count { !it.contentEquals(calmFrame) }
            assertTrue("${s.id} does not answer the wind", changed > during.size / 3)
            // No wind at all: the picture is exactly the hero as it stands.
            val none = posed(s, 0f)
            assertTrue("${s.id} is posed with no wind", none.all { it.contentEquals(calmFrame) })
        }
    }

    /** Connected groups of lit LEDs (8-way): a group of 1-3 apart from the hero reads as a bug. */
    private fun groups(f: IntArray): List<List<Int>> {
        val seen = BooleanArray(625)
        val out = ArrayList<List<Int>>()
        for (i in 0 until 625) {
            if (f[i] <= 0 || seen[i] || !GlyphMatrixSpec.isVisible(i % 25, i / 25)) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i)
            seen[i] = true
            while (stack.isNotEmpty()) {
                val j = stack.removeLast()
                group += j
                val x = j % 25
                val y = j / 25
                for (dx in -1..1) for (dy in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0..24 || ny !in 0..24) continue
                    val k = ny * 25 + nx
                    if (!seen[k] && f[k] > 0 && GlyphMatrixSpec.isVisible(nx, ny)) { seen[k] = true; stack.add(k) }
                }
            }
            out += group
        }
        return out
    }
}
