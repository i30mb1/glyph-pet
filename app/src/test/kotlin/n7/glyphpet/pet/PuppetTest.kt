package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The puppet heroes ([Puppet]): every pose is a sound silhouette, and the pet is alive on the matrix. */
class PuppetTest {
    private val stages = Stage.entries.map { SpeciesForms.of("bunny", it) }

    /** The lit cells of [s] on the matrix. */
    private fun cells(s: Species): Set<Pair<Int, Int>> = buildSet {
        s.rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch != '.') add(s.ox + i to s.oy + j) } }
    }

    private fun poses(base: Species) = BunnyArt.bodies.flatMap { b -> BunnyArt.ears.flatMap { l -> BunnyArt.ears.map { r -> Triple(b, l, r) } } }

    @Test
    fun `at rest the puppet is the sprite itself, cell for cell`() {
        for (base in stages) {
            val rest = BunnyArt.build(base, "sit", "0", "0")
            assertEquals(base.id, cells(base), cells(rest))
            assertEquals(base.id, base.eyes.map { (x, y) -> base.ox + x to base.oy + y }, rest.eyes.map { (x, y) -> rest.ox + x to rest.oy + y })
            assertEquals(base.id, base.mouth!!.let { (x, y) -> base.ox + x to base.oy + y }, rest.mouth!!.let { (x, y) -> rest.ox + x to rest.oy + y })
            assertEquals(base.id, base.rows.map { r -> r.map { it == 'o' } }.flatten().count { it }, rest.rows.sumOf { r -> r.count { it == 'o' } })
        }
    }

    @Test
    fun `no pose puts an LED off the matrix`() {
        for (base in stages) for ((b, l, r) in poses(base)) {
            val off = cells(BunnyArt.posed(base, b, l, r)).filter { (x, y) -> !GlyphMatrixSpec.isVisible(x, y) }
            assertTrue("${base.id} $b $l $r: off the matrix at $off", off.isEmpty())
        }
    }

    @Test
    fun `every pose is one piece, no loose bits`() {
        for (base in stages) for ((b, l, r) in poses(base)) {
            val all = cells(BunnyArt.posed(base, b, l, r))
            val seen = HashSet<Pair<Int, Int>>()
            val queue = ArrayDeque(listOf(all.first()))
            seen += all.first()
            while (queue.isNotEmpty()) {
                val (x, y) = queue.removeFirst()
                for (dx in -1..1) for (dy in -1..1) { val n = x + dx to y + dy; if (n in all && seen.add(n)) queue += n }
            }
            assertEquals("${base.id} $b $l $r: a piece hangs apart", all.size, seen.size)
        }
    }

    @Test
    fun `every pose keeps its face inside its head`() {
        for (base in stages) for ((b, l, r) in poses(base)) {
            val p = BunnyArt.posed(base, b, l, r)
            fun body(x: Int, y: Int) = p.rows.getOrNull(y)?.getOrNull(x) == '#'
            for ((ex, ey) in p.eyes) for (a in -1..2) for (c in -1..2) assertTrue("${base.id} $b $l $r: eye at the edge", body(ex + a, ey + c))
            val (mx, my) = p.mouth!!
            for (a in 0..2) for (c in 0..1) assertTrue("${base.id} $b $l $r: mouth off the head", body(mx + a, my + c))
        }
    }

    private fun run(base: Species, ms: Long, scene: (Long) -> Scene): List<IntArray> {
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        val out = ArrayList<IntArray>()
        var t = 0L
        while (t < ms) { out += e.tick(40, scene(t)).copyOf(); t += 40 }
        return out
    }

    @Test
    fun `calm, the pet is alive, not two frames`() {
        for (base in stages.drop(1)) {
            val frames = run(base, 20_000) { Scene(base, Mood.CONTENT) }
            assertTrue("${base.id}: only ${frames.map { it.toList() }.toSet().size} different frames", frames.map { it.toList() }.toSet().size >= 8)
        }
    }

    @Test
    fun `the ears hang to the low side of a leaned phone`() {
        for (base in stages) {
            fun lowSide(tilt: Float): Int {
                val f = run(base, 3000) { Scene(base, Mood.CONTENT, tilt = tilt) }.last()
                // Lit cells left and right of the matrix's middle, below the top of the head: a hanging ear adds to its side.
                var left = 0; var right = 0
                for (y in 0 until 25) for (x in 0 until 25) if (f[y * 25 + x] > 0) { if (x < 12) left++ else if (x > 12) right++ }
                return right - left
            }
            assertTrue("${base.id}: leaned right, nothing hangs right", lowSide(0.9f) > lowSide(-0.9f))
        }
    }

    @Test
    fun `asleep it lies down as a loaf, lower than awake`() {
        for (base in stages) {
            fun top(mood: Mood): Int {
                val f = run(base, 12_000) { Scene(base, mood) }.last()
                return (0 until 25).first { y -> (0 until 25).count { x -> f[y * 25 + x] > 600 } >= 4 }
            }
            assertTrue("${base.id}: the sleeper is no lower", top(Mood.SLEEPING) > top(Mood.CONTENT))
        }
    }
}
