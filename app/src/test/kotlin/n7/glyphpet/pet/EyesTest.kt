package n7.glyphpet.pet

import org.junit.Assert.assertTrue
import org.junit.Test

/** The eyes are what makes a pet read as a face: they must stay whole, at every stage and whatever the pet does. */
class EyesTest {

    private val forms = SpeciesForms.sprites

    @Test
    fun `every eye is carved inside the body, with body on both sides of it`() {
        val problems = mutableListOf<String>()
        for (s in forms) {
            // The bot's eyes are lit on a dark screen; the cactus trunk is too narrow for eyes with body beside them.
            if (s.inverted || s.kind == "cactus") continue
            fun body(x: Int, y: Int) = s.rows.getOrNull(y)?.getOrNull(x)?.let { it != '.' } == true
            for ((ex, ey) in s.eyes) for (y in ey..ey + 1) {
                if (!body(ex - 1, y) || !body(ex + 2, y)) problems += "${s.id}: eye at $ex,$ey row $y has no body beside it"
            }
            // Closed above and below too, or it reads as a notch in the outline, not an eye.
            for ((ex, ey) in s.eyes) for (x in ex..ex + 1) {
                if (!body(x, ey - 1)) problems += "${s.id}: eye at $ex,$ey is open at the top"
                if (!body(x, ey + 2)) problems += "${s.id}: eye at $ex,$ey is open at the bottom"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Whether the eye at sprite ([ex], [ey]) of the plain picture [plain] (drawn at [oy]) shows whole somewhere in
     * [frame]: the same lit and dark cells, its two rows and a cell either side, moved aside together.
     */
    private fun whole(plain: IntArray, frame: IntArray, x0: Int, y0: Int): Boolean {
        fun lit(f: IntArray, x: Int, y: Int) = x in 0 until 25 && y in 0 until 25 && f[y * 25 + x] > SpeciesRenderer.BODY / 3
        // The rows the eye is drawn in: a happy or shut eye is one row, an open one two.
        val rows = (y0..y0 + 1).filter { y -> (x0..x0 + 1).any { !lit(plain, it, y) } }
        if (rows.isEmpty()) return true
        for (d in -6..6) {
            var same = true
            for (y in rows) for (x in x0 - 1..x0 + 2) if (lit(plain, x, y) != lit(frame, x + d, y)) same = false
            if (same) return true
        }
        return false
    }

    @Test
    fun `looking aside only moves the eyes over the body, never into a ring, a detail or off the edge`() {
        val problems = mutableListOf<String>()
        for (s in forms) {
            if (s.inverted) continue
            val home = s.rows
            for (face in listOf(Face.IDLE, Face.CONTENT, Face.SCARED, Face.HAPPY, Face.SAD)) for (look in -2..2) {
                val g = SpeciesRenderer.carve(s, face, eyeDx = SpeciesRenderer.safeEyeDx(s, home, look))
                for (y in home.indices) for (x in home[y].indices) {
                    val was = home[y][x]
                    // What looking may carve: body cells, and the eye's own home cells.
                    val ownHome = s.eyes.any { (ex, ey) -> x - ex in 0..1 && y - ey in 0..1 }
                    if (g[y][x] != was && was != '#' && !ownHome && s.mouth?.let { (mx, my) -> x - mx in 0..2 && y - my in 0..1 } != true) {
                        problems += "${s.id} $face look=$look changes '$was' at $x,$y"
                    }
                }
            }
        }
        assertTrue(problems.distinctBy { it.substringBefore(" look") }.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `a lean never tears an eye apart`() {
        val problems = mutableListOf<String>()
        for (s in forms) {
            if (s.inverted) continue
            val style = TiltStyle.of(s.kind)
            if (style == TiltStyle.GLITCH || style == TiltStyle.WRAP || style == TiltStyle.TURN) continue // the bot tears on purpose; a ghost leaves the matrix
            // Pets drawn in 3D turn with the lean in their own renderer (Living3D, its own tests); the lean leaves their rows alone.
            if (Living3D.handles(s)) continue
            for (tilt in listOf(1f, -1f)) {
                val engine = PetEngine(kotlin.random.Random(3), autoHabits = false, autoSignatures = false)
                var t = 0L
                while (t < 4000) {
                    t += 80
                    val f = engine.tick(80, Scene(s, Mood.CONTENT, tilt = tilt))
                    val sh = engine.shown
                    if (sh.face != style.face) continue
                    // The pet may be blinking in this frame: either picture will do.
                    val look = if (tilt > 0) 1 else -1
                    val plains = listOf(false, true).map { blink ->
                        SpeciesRenderer.render(s, sh.face, blink = blink, dx = sh.dx, lift = sh.lift, eyeDx = look, badges = false, shadow = true)
                    }
                    val oy = s.oy - SpeciesRenderer.jumpOf(sh.face) - sh.lift
                    for ((ex, ey) in s.eyes) {
                        val x0 = s.ox + sh.dx + ex + (if (tilt > 0) 1 else -1)
                        // Only eyes that are on the matrix to begin with.
                        if (!n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x0 - 1, oy + ey) || !n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x0 + 2, oy + ey + 1)) continue
                        if (plains.none { whole(it, f, x0, oy + ey) }) { problems += "${s.id} $style tilt=$tilt t=$t eye $ex,$ey"; break }
                    }
                }
            }
        }
        assertTrue(problems.distinctBy { it.substringBefore(" t=") }.joinToString("\n"), problems.isEmpty())
    }
}
