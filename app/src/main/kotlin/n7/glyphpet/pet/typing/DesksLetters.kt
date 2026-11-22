package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Typing scenes built on the letters it types: every key is a big letter (5x5) that something happens to, in the
// hero's own theme, and the scene builds up over 40 s of typing to a payoff. The hero itself may stay off stage.

/** Big 5x5 capitals: the letters it types. */
internal val FONT5 = listOf(
    listOf(".###.", "#...#", "#####", "#...#", "#...#"), // A
    listOf("####.", "#...#", "####.", "#...#", "####."), // B
    listOf(".####", "#....", "#....", "#....", ".####"), // C
    listOf("####.", "#...#", "#...#", "#...#", "####."), // D
    listOf("#####", "#....", "####.", "#....", "#####"), // E
    listOf(".####", "#....", "#..##", "#...#", ".###."), // G
    listOf("#...#", "#...#", "#####", "#...#", "#...#"), // H
    listOf("#...#", "#..#.", "###..", "#..#.", "#...#"), // K
    listOf("#...#", "##.##", "#.#.#", "#...#", "#...#"), // M
    listOf("#...#", "##..#", "#.#.#", "#..##", "#...#"), // N
    listOf(".###.", "#...#", "#...#", "#...#", ".###."), // O
    listOf("####.", "#...#", "####.", "#....", "#...."), // P
    listOf("####.", "#...#", "####.", "#..#.", "#...#"), // R
    listOf(".####", "#....", ".###.", "....#", "####."), // S
    listOf("#####", "..#..", "..#..", "..#..", "..#.."), // T
    listOf("#...#", "#...#", "#.#.#", "##.##", "#...#"), // W
    listOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#"), // X
    listOf("#...#", ".#.#.", "..#..", "..#..", "..#.."), // Y
)

/** The letter key [n] types (the same key always the same letter). */
internal fun glyph(n: Int) = FONT5[(h(n, 31) * FONT5.size).toInt().coerceIn(0, FONT5.size - 1)]

/** The lit cells of a letter, as (dx, dy) from its top left. */
internal fun cells(g: List<String>) = g.flatMapIndexed { y, r -> r.mapIndexedNotNull { x, ch -> if (ch == '#') x to y else null } }

/** Draws letter [g] with its top left at ([x], [y]), every cell [k] cells apart. */
internal fun Cv.big(g: List<String>, x: Int, y: Int, b: Float, k: Int = 1) {
    for ((dx, dy) in cells(g)) for (sy in 0 until k) for (sx in 0 until k) put(x + dx * k + sx, y + dy * k + sy, b)
}

private val PF = PI.toFloat()

/**
 * Moon rabbit: a great full moon. Each key pounds a letter into it (the mallet's blow): the letter is carved dark and a
 * ripple runs out over the moon. As you type the rabbit on the moon shows in its dark seas, and after 40 s it glows.
 */
internal class MoonLettersDesk : Desk() {
    private companion object {
        /** The rabbit on the moon (its dark seas), as people see it: ears up, pounding. */
        val RABBIT = listOf(
            "...##........",
            "..###.##.....",
            "..##.###.....",
            "...####......",
            "..######...##",
            ".########.###",
            ".#########.#.",
            "..#######..#.",
            "...#####..###",
            "..##...##.###",
        )
    }

    override fun draw(c: Cv, d: DeskInput) {
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        val glow = if (d.typed > 40_000) 0.08f + 0.06f * sin(d.t / 300f) else 0f
        for (y in 0..24) for (x in 0..24) {
            val r = hypot(x - 12f, y - 12f)
            if (r > 11.2f) continue
            val tex = 0.03f * sin(x * 1.7f + y * 0.9f) + 0.03f * cos(x * 0.6f - y * 1.3f)
            c.put(x, y, 0.22f + tex + glow + (if (r > 10.3f) 0.15f else 0f))
        }
        // The rabbit comes out of the seas: darker as you type.
        RABBIT.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '#') { val x = 6 + i; val y = 7 + j; c.set(x, y, mix(0.22f, 0.03f, g) + glow * 0.5f) } } }
        // Each key: a letter carved at a spot on the moon, a ripple running out from it.
        d.recent(1200) { k, a ->
            val gl = glyph(k.n)
            val lx = 5 + (h(k.n, 3) * 11).toInt(); val ly = 5 + (h(k.n, 4) * 11).toInt()
            val q = a / 1200f
            val r = 1f + q * 14f
            c.ring(lx + 2f, ly + 2f, r, 0.75f * (1 - q))
            if (a < 900) for ((dx, dy) in cells(gl)) c.set(lx + dx, ly + dy, if (a < 90) 1f else 0f)
        }
        if (d.sending) c.ring(12f, 12f, 11f, 1f - d.sentP, 0.8f)
    }
}

/**
 * Paper boat: a sea under the night. Each key a letter drops in and floats away on the waves like a paper boat, a
 * fleet of words drifting left. The sea grows rougher as you type, and the boat rides the swell.
 */
internal class SeaLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        val amp = 0.6f + 2.2f * g + (if (d.rush) 0.6f else 0f)
        val t = d.t / 1000f
        fun sea(x: Float) = 16f + sin(x * 0.45f + t * (1.6f + g)) * amp * 0.6f + sin(x * 0.9f - t * 2.3f) * amp * 0.3f
        for (x in 0..24) {
            val s = sea(x.toFloat())
            val top = s.roundToInt()
            c.put(x, top, 0.55f)
            for (y in top + 1..24) c.put(x, y, 0.06f + 0.03f * sin(x * 0.7f + y + t * 3))
            // Foam on the crests once it is rough.
            if (g > 0.4f && sea(x - 0.5f) > s && sea(x + 0.5f) > s) c.put(x, top - 1, 0.9f)
        }
        // Letters: drop in from the sky, then float on the water drifting left.
        d.recent(6000) { k, a ->
            val gl = glyph(k.n)
            val x0 = 16f + h(k.n, 2) * 4f
            val x = x0 - a / 1000f * (1.6f + g * 2f)
            if (x < -6f) return@recent
            val land = 500L
            val y = if (a < land) mix(-5f, sea(x + 2f) - 5f, ease(a / land.toFloat())) else sea(x + 2f) - 5f
            c.big(gl, x.roundToInt(), y.roundToInt(), if (a < land + 120) 1f else 0.6f)
        }
        // The boat itself, riding the swell.
        val bx = 6f; val by = sea(bx + 3f)
        val tilt = (sea(bx + 5f) - sea(bx + 1f)) / 4f
        c.artSolid(listOf("...o...", "...oo..", "...ooo.", "#######", ".#####."), bx.roundToInt(), (by - 5 + tilt).roundToInt(), 0.45f)
        if (d.sending) for (x in 0..24) c.put(x, sea(x.toFloat()).roundToInt(), 1f - d.sentP)
    }
}

/**
 * Swarm: fireflies drifting in the dark. Each key they fly together and spell the letter, blazing, then scatter. The
 * swarm grows as you type, and after 40 s each letter is spelled big, two cells to a dot.
 */
internal class SwarmLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = (16 + d.typed / 1000f * 0.8f).toInt().coerceAtMost(48)
        val big = d.typed > 40_000
        val last = d.last
        val a = if (last == null) 99_999L else d.sinceKey
        val form = when { a < 250 -> ease(a / 250f); a < 650 -> 1f; a < 1000 -> 1f - ease((a - 650) / 350f); else -> 0f }
        val target = if (last != null) cells(glyph(last.n)) else emptyList()
        val k = if (big) 2 else 1
        val ox = 12 - (5 * k) / 2; val oy = 12 - (5 * k) / 2
        for (i in 0 until n) {
            val wx = 12f + sin(d.t / (900f + h(i) * 900f) + h(i, 1) * 9f) * 10f
            val wy = 12f + cos(d.t / (1100f + h(i, 2) * 800f) + h(i, 3) * 9f) * 9f
            val tc = if (target.isNotEmpty()) target[i % target.size] else null
            val x = if (tc != null) mix(wx, (ox + tc.first * k).toFloat(), form) else wx
            val y = if (tc != null) mix(wy, (oy + tc.second * k).toFloat(), form) else wy
            val tw = 0.35f + 0.25f * sin(d.t / 200f + i)
            c.put(x, y, if (form > 0.9f) 1f else tw + form * 0.5f)
        }
        if (d.sending) c.ring(12f, 12f, 3f + d.sentP * 9f, 1f - d.sentP)
    }
}

/**
 * Black hole: a dark heart in a turning disc of light. Each key a letter appears out at the edge and is torn apart,
 * cell by cell, spiralling in. The disc swells and burns brighter as you type; after 40 s it fires its jets.
 */
internal class HoleLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        val spin = d.t / 1000f * (1.2f + 2f * g)
        val outer = 4.5f + 3f * g
        for (y in 0..24) for (x in 0..24) {
            val dx = x - 12f; val dy = (y - 12f) * 1.6f
            val r = hypot(dx, dy)
            if (r < 2.6f || r > outer + 1f) continue
            val a = atan2(dy, dx)
            val arm = 0.5f + 0.5f * sin(a * 2 - spin * 3 + r * 0.8f)
            c.put(x, y, (0.18f + 0.5f * arm * (1 - (r - 2.6f) / (outer - 1.6f)).coerceIn(0f, 1f)) * (0.7f + 0.5f * g))
        }
        for (y in 0..24) for (x in 0..24) if (hypot(x - 12f, (y - 12f) * 1.6f) < 2.6f) c.cut(x, y)
        // Letters torn apart into it: each cell spirals in on its own clock.
        d.recent(1800) { k, a ->
            val gl = glyph(k.n)
            val side = h(k.n, 5) * 2 * PF
            val lx = 12f + cos(side) * 8.5f - 2f; val ly = 12f + sin(side) * 8.5f - 2f
            cells(gl).forEachIndexed { i, (cx, cy) ->
                val sx = lx + cx; val sy = ly + cy
                val delay = 250L + (i * 37L) % 300L
                if (a < delay) { c.put(sx, sy, 1f); return@forEachIndexed }
                val q = ((a - delay) / 1100f).coerceIn(0f, 1f)
                if (q >= 1f) return@forEachIndexed
                val r0 = hypot(sx - 12f, sy - 12f); val a0 = atan2(sy - 12f, sx - 12f)
                val r = r0 * (1 - ease(q)); val ang = a0 + q * 4f
                if (r > 2.6f) c.put(12 + cos(ang) * r, 12 + sin(ang) * r / 1.2f, 1f - q * 0.4f)
            }
        }
        if (d.typed > 40_000) { val p = 0.5f + 0.5f * sin(d.t / 120f); for (j in 3..11) { c.put(12, 12 - j, (0.9f - j * 0.05f) * p + 0.2f); c.put(12, 12 + j, (0.9f - j * 0.05f) * p + 0.2f) } }
        if (d.sending) c.ring(12f, 12f, 3f + d.sentP * 9f, 1f - d.sentP)
    }
}

/**
 * Dragon: each key a letter hangs in the air and the dragon's breath roars in from the side and burns it; it glows,
 * crumbles into embers and they fall as gold onto its hoard, which grows as you type. After 40 s its eye opens over it.
 */
internal class DragonLettersDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        // The hoard: a heap of gold at the bottom, glinting.
        val hh = 1f + 5f * g
        for (x in 0..24) { val top = (24 - hh * (1 - ((x - 12f) / 12f).let { it * it })).roundToInt(); for (y in top..24) c.put(x, y, if (h(x, y) > 0.85f && (d.t / 300 + x) % 5 == 0L) 1f else 0.38f) }
        d.recent(1500) { k, a ->
            val gl = glyph(k.n)
            val lx = 10; val ly = 6
            if (a < 260) Fire.flame(heat, 1f + a / 30f, 9f, d.t, size = 0.55f, ux = 1f, uy = 0f, power = 1f, salt = k.n)
            when {
                a < 220 -> c.big(gl, lx, ly, 0.9f)
                a < 600 -> { c.big(gl, lx, ly, 1f); Fire.flame(heat, lx + 2f, ly + 4f, d.t, size = 0.3f, power = 1f - (a - 220) / 380f, salt = k.n + 3) }
                else -> { val q = (a - 600) / 900f; for ((dx, dy) in cells(gl)) { val fy = ly + dy + q * q * 14f * (0.6f + h(k.n, dx * 7 + dy)); if (fy < 24 - hh) c.put(lx + dx + (h(dx, dy) - 0.5f) * q * 3f, fy, 0.85f) } }
            }
        }
        if (d.rush) Fire.flame(heat, 1f, 9f, d.t, size = 0.6f, ux = 1f, uy = 0f, power = 0.9f, salt = 77)
        Fire.render(c, heat)
        if (d.typed > 40_000) {
            val open = ease((d.typed - 40_000) / 1500f)
            for (x in 5..19) { val e = 1 - ((x - 12f) / 7f).let { it * it }; val hgt = e * 3f * open; for (y in (4 - hgt).roundToInt()..(4 + hgt).roundToInt()) c.put(x, y, 0.55f) }
            if (open > 0.5f) for (y in 2..6) c.cut(12, y)
        }
        if (d.sending) c.ring(12f, 12f, 3f + d.sentP * 9f, 1f - d.sentP)
    }
}

/**
 * Mimic: a mouth as big as the matrix, rows of teeth. Each key a letter flies in and the jaws slam shut on it. At 20 s
 * its eyes open; after 40 s it spits gold with every bite.
 */
internal class MimicLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val a = if (d.last == null) 99_999L else d.sinceKey
        val bite = when { a < 180 -> 0f; a < 260 -> ease((a - 180) / 80f); a < 420 -> 1f - ease((a - 260) / 160f); else -> 0f }
        val gap = 5f * (1 - bite) + 0.5f
        val upper = 12f - gap; val lower = 12f + gap
        // Inside the mouth: dark, a tongue.
        for (x in 3..21) for (y in upper.roundToInt()..lower.roundToInt()) c.set(x, y, 0.04f)
        for (x in 8..16) c.put(x, lower.roundToInt() - 1, 0.28f)
        // Jaws and teeth.
        for (x in 0..24) { for (y in 0..upper.roundToInt()) c.put(x, y, 0.3f); for (y in lower.roundToInt()..24) c.put(x, y, 0.3f) }
        for (i in 0 until 6) { val x = 4f + i * 3; c.put(x, upper + 1, 1f); c.put(x + 1, upper + 1, 1f); c.put(x, upper + 2, 0.85f); c.put(x + 1, lower - 1, 1f); c.put(x + 2, lower - 1, 1f); c.put(x + 1, lower - 2, 0.85f) }
        // The letter flying in.
        d.last?.let { k -> if (a < 260) { val gl = glyph(k.n); val x = mix(26f, 10f, ease(a / 200f)); c.big(gl, x.roundToInt(), 10, 1f) } }
        if (d.typed > 20_000) { val blink = d.paused && (d.t % 2600) < 160; for (ex in listOf(7, 17)) { c.disc(ex.toFloat(), 3f, 1.6f, 0.9f); if (!blink) c.cut(ex, 3) } }
        if (d.typed > 40_000) d.recent(900) { k, q -> if (q > 260) for (i in 0..4) { val (x, y) = arc(12f, 12f, (h(k.n, i) - 0.5f) * 30f, -14f - h(k.n, i + 9) * 8f, 40f, q - 260); c.put(x, y, 1f) } }
        if (d.sending) c.ring(12f, 12f, 3f + d.sentP * 9f, 1f - d.sentP)
    }
}

/**
 * Schrodinger's cat: a box. Each key the letter over it flickers between two letters at once and then collapses to one
 * and drops into the box. Waves of maybe ripple out of the box as you type; after 40 s it opens and the cat looks out,
 * alive and a ghost by turns.
 */
internal class BoxLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        for (i in 0 until 3) { val q = ((d.t / 1600f + i / 3f) % 1f); if (g > i / 3f) c.ring(12f, 18f, 4f + q * 10f, 0.3f * (1 - q) * g, 0.5f) }
        // The box.
        for (x in 6..18) for (y in 15..22) c.put(x, y, if (x == 6 || x == 18 || y == 15 || y == 22) 0.5f else 0.2f)
        c.line(6f, 15f, 18f, 22f, 0.08f)
        d.last?.let { k ->
            val a = d.sinceKey
            if (a < 900) {
                val one = glyph(k.n); val other = glyph(k.n + 1000)
                when {
                    a < 450 -> c.big(if ((d.t / 70) % 2 == 0L) one else other, 10, 6, 0.85f)
                    else -> { val y = mix(6f, 16f, ease((a - 450) / 450f)); if (y < 13f) c.big(one, 10, y.roundToInt(), 1f) }
                }
            }
        }
        if (d.typed > 40_000) {
            val open = ease((d.typed - 40_000) / 1200f)
            val ghost = (d.t / 1400) % 2 == 1L
            val top = (15 - open * 5).roundToInt()
            c.art(listOf("#...#", "##.##", "#####", "#-#-#", "#####"), 10, top, if (ghost) 0.15f else 0.4f)
            for (x in 6..18) c.put(x, 15, 0.5f)
        }
        if (d.sending) c.ring(12f, 18f, 3f + d.sentP * 9f, 1f - d.sentP)
    }
}

/** The hero's own calm picture under a letters scene (its stage from the painter). */
private fun hero(c: Cv, d: DeskInput, kind: String, col: String = "calm") {
    n7.glyphpet.pet.drawn.Drawn.of(kind)?.draw(c, col, d.t % 3200, d.pet.species.stage.ordinal, 0f)
}

/**
 * Fish in a jar: each key it blows a bubble in the shape of the letter, which wobbles up through the water and bursts
 * into a ring at the surface. A rush and the letters come up in a stream; after 40 s it loops the loop with joy.
 */
internal class FishLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        if (d.typed > 40_000 && (d.typed - 40_000) % 9000 < 4000) { n7.glyphpet.pet.drawn.Drawn.of("fishjar")?.draw(c, "signature", (d.typed - 40_000) % 9000, d.pet.species.stage.ordinal, 0f); return }
        hero(c, d, "fishjar")
        d.recent(1500) { k, a ->
            val gl = glyph(k.n)
            val q = a / 1300f
            val x = 10 + (h(k.n, 6) * 4).toInt() + (sin(a / 140f) * 0.7f).roundToInt()
            val y = mix(17f, 9f, ease(q.coerceAtMost(1f)))
            if (q < 1f) for ((dx, dy) in cells(gl)) c.put(x + dx, y.roundToInt() + dy, if (dx == 0 || dy == 0) 0.95f else 0.7f)
            else c.ring(x + 2f, 11f, 1f + (q - 1f) * 20f, 0.8f * (1 - (q - 1f) * 5f))
        }
    }
}

/**
 * Capybara in its hot spring: each key a letter rises out of the water on the steam, swaying, and melts away; the yuzu
 * on its head hops. The steam thickens as you type; after 40 s it rains yuzu.
 */
internal class CapyLettersDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val st = d.pet.species.stage.ordinal
        if (d.typed > 40_000) { n7.glyphpet.pet.drawn.Drawn.of("capybara")?.draw(c, "signature", d.t % 6400, st, 0f) }
        else n7.glyphpet.pet.drawn.Drawn.of("capybara")?.draw(c, if (d.hit(220) > 0f || d.rush) "happy" else "calm", d.t % 2400, st, 0f)
        val g = (d.typed / 40_000f).coerceIn(0f, 1f)
        d.recent(2000) { k, a ->
            val gl = glyph(k.n)
            val q = a / 2000f
            val x = (2 + h(k.n, 8) * 4).toInt() + (sin(q * 7f + k.n) * 1.2f).roundToInt() + (if (k.n % 2 == 0) 0 else 14)
            val y = mix(13f, 0f, q)
            val b = (if (q < 0.15f) q / 0.15f else 1f - (q - 0.15f) / 0.85f) * (0.55f + 0.45f * g)
            c.big(gl, x, y.roundToInt(), b)
        }
    }
}
