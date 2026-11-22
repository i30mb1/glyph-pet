package n7.glyphpet.pet

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The light on a flat pet ([Shading]), the catchlight in its eyes and the moods' wide eyes, swept over every pet at
 * every stage: whatever the light does, the outline, the eyes and the bright details stay whole.
 */
class LightAndFacesTest {

    private val flat = SpeciesForms.sprites.filter { !it.inverted }
    private val lights = listOf(-0.95f, -0.6f, 0f, 0.6f, 0.95f)
    private val moods = listOf(Face.CONTENT, Face.HAPPY, Face.LOVE, Face.DIZZY, Face.TIRED, Face.YAWN, Face.EAT)

    @After
    fun reset() {
        Shading.light = Shading.DEFAULT_LIGHT
    }

    private fun slots(s: Species): Set<Pair<Int, Int>> = buildSet {
        s.eyes.forEach { (ex, ey) -> for (a in 0..1) for (b in 0..1) add(ex + a to ey + b) }
        s.mouth?.let { (mx, my) -> for (a in 0..2) for (b in 0..1) add(mx + a to my + b) }
    }

    @Test
    fun `light and shade keep every body cell lit and below the bright details`() {
        assertTrue("the brightest body must stay below the bright details", SpeciesRenderer.BODY * Shading.MAX < SpeciesRenderer.BRIGHT * 0.6f)
        val problems = mutableListOf<String>()
        for (s in flat) for (face in listOf(Face.IDLE) + moods) for (light in lights) {
            val grid = SpeciesRenderer.carve(s, face)
            val body = SpeciesRenderer.BODY
            val lit = Shading.shade(grid, body, slots(s), light)
            for (y in grid.indices) for (x in grid[y].indices) {
                val v = lit[y][x]
                when (grid[y][x]) {
                    '#' -> if (v < body * Shading.CORNER_MIN - 1 || v > body * Shading.MAX + 1) problems += "${s.id} $face light=$light: body cell $x,$y at $v"
                    'o' -> if (v != 0) problems += "${s.id} $face: bright cell $x,$y shaded"
                    '.' -> if (v > Shading.STEP_MAX || v >= body) problems += "${s.id} $face: step $x,$y too bright ($v)"
                }
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the soft corners never light an eye, a mouth or a gap inside the body`() {
        val problems = mutableListOf<String>()
        for (s in flat) for (face in listOf(Face.IDLE, Face.SAD, Face.SCARED, Face.ANGRY) + moods) {
            val grid = SpeciesRenderer.carve(s, face)
            val lit = Shading.shade(grid, SpeciesRenderer.BODY, slots(s))
            for ((x, y) in slots(s)) {
                if (grid.getOrNull(y)?.getOrNull(x) == '.' && (lit.getOrNull(y)?.getOrNull(x) ?: 0) > 0) problems += "${s.id} $face: face hole $x,$y lit"
            }
            // A lit step always sits on the outside of the outline: next to body on two sides that meet.
            for (y in grid.indices) for (x in grid[y].indices) {
                if (grid[y][x] != '.' || lit[y][x] == 0) continue
                val n = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (a, b) -> grid.getOrNull(y + b)?.getOrNull(x + a) == '#' }
                if (n != 2) problems += "${s.id} $face: step $x,$y with $n body cells beside it"
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the light leans with the phone and is back above when it stands`() {
        assertEquals(Shading.DEFAULT_LIGHT, Shading.lightFor(0f, 1f), 1e-4f)
        assertEquals(Shading.DEFAULT_LIGHT, Shading.lightFor(0f, 0f), 1e-4f)
        assertTrue(Shading.lightFor(-0.7f, 0.7f) > Shading.DEFAULT_LIGHT)
        assertTrue(Shading.lightFor(0.7f, 0.7f) < Shading.DEFAULT_LIGHT)
        for (t in listOf(-1f, -0.5f, 0.5f, 1f)) assertTrue(Shading.lightFor(t, 0.1f) in -0.95f..0.95f)
    }

    @Test
    fun `an open eye has one catchlight on the light's side and stays an eye`() {
        val problems = mutableListOf<String>()
        var withGlint = 0
        for (s in flat) for (light in listOf(-0.6f, 0.6f)) {
            Shading.light = light
            val f = SpeciesRenderer.render(s, Face.IDLE)
            for ((ex, ey) in s.eyes) {
                val cells = (0..1).flatMap { a -> (0..1).map { b -> s.ox + ex + a to s.oy + ey + b } }
                    .filter { (x, y) -> x in 0 until 25 && y in 0 until 25 }
                if (cells.size < 4) continue
                val v = cells.map { (x, y) -> f[y * 25 + x] }
                val bright = cells.filterIndexed { i, _ -> v[i] >= SpeciesRenderer.BRIGHT }
                val dark = v.count { it == 0 }
                if (bright.size > 1) problems += "${s.id} light=$light: ${bright.size} catchlights in one eye"
                if (bright.size == 1) {
                    withGlint++
                    val col = bright[0].first - (s.ox + ex)
                    if (col != Shading.glintColumn(light) || bright[0].second != s.oy + ey) problems += "${s.id} light=$light: catchlight at $col"
                    if (dark < 3) problems += "${s.id}: only $dark dark cells left in the eye"
                }
            }
        }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
        assertTrue("catchlights in only $withGlint eyes", withGlint >= flat.size)
    }

    @Test
    fun `the catchlight glides across the eye instead of jumping`() {
        // Near the top both columns share it, and the share changes a little with every step of the light.
        var last = Shading.glintShares(-0.95f)
        for (i in -95..95) {
            val (l, r) = Shading.glintShares(i / 100f)
            assertEquals(1f, l + r, 1e-4f)
            assertTrue("jump at ${i / 100f}", kotlin.math.abs(r - last.second) <= 0.051f)
            last = l to r
        }
        val s = SpeciesForms.of("bunny", Stage.ADULT)
        Shading.light = 0f
        val f = SpeciesRenderer.render(s, Face.IDLE)
        val (ex, ey) = s.eyes[0]
        val left = f[(s.oy + ey) * 25 + s.ox + ex]
        val right = f[(s.oy + ey) * 25 + s.ox + ex + 1]
        assertTrue("straight above both halves glow ($left, $right)", left in 1 until SpeciesRenderer.BRIGHT && right in 1 until SpeciesRenderer.BRIGHT)
    }

    @Test
    fun `a sleeping, blinking or 3D pet has no catchlight`() {
        for (s in flat) {
            for (face in listOf(Face.SLEEP, Face.TIRED)) {
                val g = SpeciesRenderer.render(s, face)
                for ((ex, ey) in s.eyes) for (a in 0..1) for (b in 0..1) {
                    val x = s.ox + ex + a
                    val y = s.oy + ey + b
                    if (x !in 0 until 25 || y !in 0 until 25) continue
                    val was = s.rows.getOrNull(ey + b)?.getOrNull(ex + a)
                    if (was == '#') assertTrue("${s.id} $face lights its shut eye", g[y * 25 + x] < SpeciesRenderer.BRIGHT / 2 || s.rows[ey + b][ex + a] == 'o')
                }
            }
        }
    }

    @Test
    fun `wide eyes keep the outline whole and never touch each other`() {
        val problems = mutableListOf<String>()
        for (s in flat) for (face in moods) {
            val grid = SpeciesRenderer.carve(s, face)
            val rows = s.rows
            fun at(x: Int, y: Int) = rows.getOrNull(y)?.getOrNull(x) ?: '.'
            for (y in rows.indices) for (x in rows[y].indices) {
                val c = rows[y][x]
                val edge = c != '.' && listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (a, b) -> at(x + a, y + b) == '.' }
                if (c == '.' && grid[y][x] != '.') problems += "${s.id} $face: lights an empty cell $x,$y"
                if (edge && grid[y][x] == '.' && (x to y) !in slots(s)) problems += "${s.id} $face: cuts the outline at $x,$y"
            }
            if (s.eyes.size != 2) continue
            // Holes carved outside the slots belong to the nearer eye.
            val (l, r) = s.eyes.sortedBy { it.first }
            val mid = (l.first + r.first + 1) / 2f
            val carved = (l.second..l.second + 2).flatMap { y ->
                rows.getOrNull(y)?.indices?.filter { x -> at(x, y) == '#' && grid[y][x] == '.' && s.mouth?.let { (mx, my) -> x - mx in 0..2 && y - my in 0..1 } != true }
                    ?.map { it to y } ?: emptyList()
            }
            // Only wide eyes are checked: the 2x2 patterns are each pet's own drawing (the cactus' eyes sit close).
            if (carved.all { (x, y) -> s.eyes.any { (ex, ey) -> x - ex in 0..1 && y - ey in 0..1 } }) continue
            val left = carved.filter { it.first < mid }.maxOfOrNull { it.first }
            val right = carved.filter { it.first >= mid }.minOfOrNull { it.first }
            // Two body cells between them, or as many as the pet's own eyes have (the toast's sit one apart).
            val need = minOf(2, r.first - (l.first + 2))
            if (left != null && right != null && right - left - 1 < need) problems += "${s.id} $face: eyes run together ($left..$right)"
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `most grown pets show joy with wide eyes`() {
        val adults = flat.filter { it.stage == Stage.ADULT }
        val wide = adults.filter { s ->
            val g = SpeciesRenderer.carve(s, Face.CONTENT)
            // A wide eye carves a cell beside its 2x2 slot.
            s.eyes.any { (ex, ey) -> listOf(ex - 1, ex + 2).any { x -> g.getOrNull(ey + 1)?.getOrNull(x) == '.' && s.rows[ey + 1].getOrNull(x) == '#' } }
        }
        assertTrue("wide joy in ${wide.size} of ${adults.size} adults: missing ${(adults - wide.toSet()).map { it.id }}", wide.size * 2 >= adults.size)
    }

    @Test
    fun `love blushes only inside the face, never on the outline`() {
        val problems = mutableListOf<String>()
        for (s in flat) {
            val g = SpeciesRenderer.carve(s, Face.LOVE)
            val rows = s.rows
            for (y in rows.indices) for (x in rows[y].indices) {
                if (rows[y][x] != '#' || g[y][x] != 'o' || (x to y) in slots(s)) continue
                val edge = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (a, b) -> (rows.getOrNull(y + b)?.getOrNull(x + a) ?: '.') == '.' }
                if (edge) problems += "${s.id}: blush on the outline at $x,$y"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
