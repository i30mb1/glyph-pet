package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Typing scenes as little toys: each key does one juicy thing, something is built as you type, and after 40 s of typing
// there is a payoff. Each hero its own toy, in its own theme.

/** A filled shape from rows ('#'), its edge lit brighter, centred on ([cx], [cy]). */
internal fun Cv.shape(rows: List<String>, cx: Int, cy: Int, fill: Float, edge: Float) {
    val h = rows.size; val w = rows.maxOf { it.length }
    val x0 = cx - w / 2; val y0 = cy - h / 2
    fun on(i: Int, j: Int) = j in rows.indices && i in rows[j].indices && rows[j][i] == '#'
    for (j in 0 until h) for (i in 0 until w) if (on(i, j)) {
        val e = !on(i - 1, j) || !on(i + 1, j) || !on(i, j - 1) || !on(i, j + 1)
        set(x0 + i, y0 + j, if (e) edge else fill)
    }
}

private fun typedK(d: DeskInput, s: Float = 40f) = (d.typed / (s * 1000f)).coerceIn(0f, 1f)
private fun after40(d: DeskInput) = d.typed > 40_000

/**
 * Paper boat, origami: a sheet of paper on the matrix. Each key a crease of light runs across it and it flutters; as
 * you type it is folded step by step (half, house, hat, diamond, triangle) into a boat, and after 40 s the boat sails
 * away over the waves and comes round again.
 */
internal class OrigamiDesk : Desk() {
    private companion object {
        val STEPS = listOf(
            List(12) { "############" },
            List(6) { "############" },
            listOf("......##......", ".....####.....", "....######....", "...########...", "..##########..", ".############.", "##############", "##############", "##############"),
            listOf("......##......", ".....####.....", "....######....", "...########...", "..##########..", ".############.", "##############", "################", "################"),
            listOf("......#......", ".....###.....", "....#####....", "...#######...", "..#########..", ".###########.", "..#########..", "...#######...", "....#####....", ".....###.....", "......#......"),
            listOf("#############", ".###########.", "..#########..", "...#######...", "....#####....", ".....###....."),
        )
        val BOAT = listOf("......#......", ".....##......", "....###......", "...####......", "..#####......", "......#......", "#############", ".###########.", "..#########..")
    }

    override fun draw(c: Cv, d: DeskInput) {
        if (after40(d)) {
            val t = d.t / 1000f
            for (x in 0..24) { val s = 19f + sin(x * 0.5f + t * 2f) * 0.8f; c.put(x, s.roundToInt(), 0.5f); for (y in s.roundToInt() + 1..24) c.put(x, y, 0.08f) }
            val bx = ((d.typed - 40_000) / 120f % 36f) - 6f
            val by = 15f + sin(bx * 0.5f + t * 2f) * 0.8f
            c.shape(BOAT, bx.roundToInt(), by.roundToInt(), 0.3f, 0.75f)
            return
        }
        val step = (typedK(d) * STEPS.size).toInt().coerceAtMost(STEPS.size - 1)
        val rows = STEPS[step]
        val since = ((typedK(d) * STEPS.size - step) * 40_000f / STEPS.size).toLong()
        val jig = if (d.hit(120) > 0f) 1 else 0
        c.shape(rows, 12, 12 - jig, 0.22f, if (since < 250) 1f else 0.55f)
        // The crease: a line of light running across on each key.
        d.recent(400) { k, a ->
            val q = a / 400f
            val diag = k.n % 2 == 0
            for (i in -7..7) { val x = 12 + i; val y = if (diag) 12 + i else 12; val lit = abs(i - (q * 14 - 7)) < 1.5f; if (lit && c.get(x, y - jig) > 0f) c.set(x, y - jig, 1f) }
        }
    }
}

/**
 * Moon rabbit, moon ping-pong: two rabbits volley a mochi across the night, one key one stroke, a flash at each hit. The
 * moon behind them waxes as you type; after 40 s the mochi bursts into stars on every stroke.
 */
internal class MochiPongDesk : Desk() {
    private companion object {
        val BUN = listOf(".#.#.", ".#.#.", "#####", "#-#-#", "#####", ".###.")
    }

    override fun draw(c: Cv, d: DeskInput) {
        val g = typedK(d)
        // The moon: waxing behind.
        for (y in 1..10) for (x in 7..17) { val dx = (x - 12f) / 5f; val dy = (y - 6f) / 5f; if (dx * dx + dy * dy > 1f) continue
            val half = kotlin.math.sqrt(1 - dy * dy); val lit = dx > half * (1 - 2 * g); c.put(x, y, if (lit) 0.3f else 0.05f) }
        for (x in 0..24) c.put(x, 21, 0.25f)
        val n = d.last?.n ?: -1
        val a = if (n < 0) 99_999L else d.sinceKey
        val fly = if (d.rush) 220f else 380f
        val q = (a / fly).coerceAtMost(1f)
        val fromLeft = n % 2 == 0
        val x0 = if (fromLeft) 6f else 18f; val x1 = if (fromLeft) 18f else 6f
        val bx = if (n < 0) 6f else mix(x0, x1, q); val by = 16f - sin(q * PIF) * 7f
        // The rabbits, each swinging when it strikes.
        for (side in listOf(-1, 1)) { val x = if (side < 0) 3 else 21; val swing = (side < 0) == fromLeft && a < 120
            c.shape(BUN, x, 17 - (if (swing) 1 else 0), 0.32f, 0.32f); c.cut(x - 1, 17 - (if (swing) 1 else 0)); c.cut(x + 1, 17 - (if (swing) 1 else 0)) }
        if (n >= 0) {
            if (a < 120) c.ring(x0, 16f, 1.5f + a / 40f, 1f - a / 120f)
            if (after40(d) && q >= 1f && a < fly + 500) { val p = (a - fly) / 500f; for (i in 0 until 8) { val an = i * PIF / 4; c.put(x1 + cos(an) * p * 6, 16f + sin(an) * p * 6, 1f - p) } }
            else {
                for (tr in 1..2) { val tq = (q - tr * 0.08f).coerceAtLeast(0f); c.put(mix(x0, x1, tq), 16f - sin(tq * PIF) * 7f, 0.35f / tr) }
                c.disc(bx, by, 1.1f, 1f)
            }
        } else c.disc(bx, 16f, 1.1f, 1f)
    }
}

/**
 * Capybara, yuzu tetris: each key a yuzu drops from the sky into the hot spring and settles in a row; a full row
 * flashes and bursts into steam, and the capybara sinks deeper into bliss. After 40 s it rains yuzu.
 */
internal class YuzuDesk : Desk() {
    private val cols = IntArray(9)
    private var lastN = -1
    private var cleared = 0
    private var flashAt = -9_999L
    override fun draw(c: Cv, d: DeskInput) {
        val st = d.pet.species.stage.ordinal
        val hero = Drawn.of("capybara")
        if (after40(d)) { hero?.draw(c, "signature", d.t % 6400, st, 0f); return }
        hero?.draw(c, if (cleared >= 2 || d.paused) "asleep" else "calm", d.t % 3200, st, 0f)
        val k = d.last
        if (k != null && k.n != lastN) {
            lastN = k.n
            val col = (k.n * 4) % 9
            if (cols[col] < 2) cols[col]++
            if (cols.all { it >= 1 }) { for (i in cols.indices) cols[i]--; cleared++; flashAt = d.t }
        }
        // The stacked yuzu, in the water.
        for (i in 0 until 9) for (r in 0 until cols[i]) { val x = 3 + i * 2; val y = 20 - r * 2; c.set(x, y, 0.8f); c.set(x + 1, y, 0.8f); c.set(x, y + 1, 0.6f); c.set(x + 1, y + 1, 0.6f) }
        // The one falling now.
        if (k != null && d.sinceKey < 220) { val col = (k.n * 4) % 9; val x = 3 + col * 2; val y = mix(-2f, 18f - (cols[col] - 1) * 2f, d.sinceKey / 220f).roundToInt()
            c.set(x, y, 1f); c.set(x + 1, y, 1f); c.set(x, y + 1, 0.8f); c.set(x + 1, y + 1, 0.8f) }
        // A row cleared: a flash along the water and steam bursting up.
        val f = d.t - flashAt
        if (f in 0..700) { val p = f / 700f; for (x in 3..20) c.put(x, 21, 1f - p); for (i in 0 until 6) c.line(4f + i * 3, 16f - p * 10, 4f + i * 3 + sin(p * 6 + i), 13f - p * 10, 0.6f * (1 - p)) }
    }
}

/**
 * Dragon, the forge: an ingot on an anvil. Each key a hammer blow rings out (sparks fly) while the dragon's fire roars
 * in from the side; the ingot glows hotter and is beaten longer into a blade as you type. After 40 s the sword stands
 * finished, a glint running up it.
 */
internal class ForgeDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val g = typedK(d)
        if (after40(d)) {
            val glint = ((d.t % 1400) / 1400f) * 20f
            for (y in 2..17) c.put(12, y, if (abs(y - (18 - glint)) < 1.5f) 1f else 0.6f)
            for (y in 3..16) { c.put(11, y, 0.35f); c.put(13, y, 0.35f) }
            for (x in 8..16) c.put(x, 18, 0.7f); for (y in 19..22) c.put(12, y, 0.45f); c.disc(12f, 23f, 1f, 0.6f)
            return
        }
        // The anvil.
        c.shape(listOf("###############", ".#############.", "....#######....", "....#######....", "..###########.."), 12, 20, 0.25f, 0.4f)
        // The ingot, longer and hotter as you type.
        val len = (3 + g * 7).roundToInt(); val hot = 0.35f + 0.55f * g + 0.1f * d.hit(300)
        for (x in 12 - len..12 + len) { c.set(x, 17, hot); c.set(x, 16, hot * 0.8f) }
        // The hammer: up, then down on each key.
        val a = if (d.last == null) 99_999L else d.sinceKey
        val hy = if (a < 90) mix(6f, 14f, a / 90f) else if (a < 300) 14f - (a - 90) / 210f * 8f else 6f
        for (y in 1..hy.roundToInt() - 2) c.put(17, y, 0.3f)
        c.shape(listOf("#####", "#####"), 16, hy.roundToInt(), 0.5f, 0.6f)
        if (a in 90..600) { val s = (a - 90) / 1000f; val n = d.last!!.n; for (i in 0..4) { val (x, y) = arc(14f, 15f, (h(n, i) - 0.5f) * 26f, -12f - h(n, i + 5) * 10f, 50f, (s * 1000).toLong()); c.put(x, y, 1f - s * 1.5f) } }
        if (d.rush || d.hit(400) > 0f) Fire.flame(heat, 0f, 15f, d.t, size = 0.5f + 0.2f * g, ux = 1f, uy = -0.2f, power = if (d.rush) 1f else d.hit(400), salt = 5)
        Fire.render(c, heat)
    }
}

/**
 * Mimic, the glutton: a treasure chest with teeth. Each key something flies into it (a coin, a key, a gem) and the lid
 * snaps shut on it. It gets fatter as you type; after 40 s it burps a fountain of gold.
 */
internal class GluttonDesk : Desk() {
    private companion object {
        val ITEMS = listOf(listOf(".#.", "#.#", ".#."), listOf("##...", "#####", "##.#."), listOf(".#.", "###", ".#."))
    }

    override fun draw(c: Cv, d: DeskInput) {
        val g = typedK(d)
        val w = (6 + g * 3).roundToInt()
        val a = if (d.last == null) 99_999L else d.sinceKey
        val open = when { a < 220 -> 1f; a < 300 -> 1f - (a - 220) / 80f; a < 700 -> 0f; else -> ((a - 700) / 300f).coerceAtMost(1f) }
        val lift = (open * 4).roundToInt()
        // The body.
        for (x in 12 - w..12 + w) for (y in 15..22) c.set(x, y, if (x == 12 - w || x == 12 + w || y == 22) 0.5f else 0.28f)
        c.set(12, 17, 0.9f)
        // The lid, raised by [lift], teeth under it and on the rim.
        val ly = 14 - lift
        for (x in 12 - w..12 + w) { c.set(x, ly, 0.5f); c.set(x, ly - 1, 0.35f); c.set(x, ly - 2, 0.25f) }
        for (x in 12 - w + 1 until 12 + w step 2) { c.set(x, ly + 1, 1f); c.set(x, 14, 0.9f) }
        if (lift > 0) for (x in 12 - w + 1 until 12 + w) for (y in ly + 2..13) if (c.get(x, y) == 0f) c.set(x, y, 0.03f)
        if (lift > 1) for (x in 12 - 3..12 + 3) c.put(x, 13, 0.4f)
        // Eyes on the lid.
        for (ex in listOf(12 - w / 2, 12 + w / 2)) { c.set(ex, ly - 1, 1f) }
        // The thing flying in.
        d.last?.let { k -> if (a < 220) { val it = ITEMS[k.n % ITEMS.size]; val side = if (k.n % 2 == 0) -1 else 1
            val (x, y) = arc(12f + side * 12f, 4f, -side * 55f, -10f, 90f, a); c.shape(it, x.roundToInt(), y.roundToInt(), 1f, 1f) } }
        if (after40(d)) d.recent(1200) { k, q -> if (q > 300) for (i in 0..5) { val (x, y) = arc(12f, 12f, (h(k.n, i) - 0.5f) * 28f, -18f - h(k.n, i + 9) * 8f, 40f, q - 300); c.set(x.roundToInt(), y.roundToInt(), 1f); c.put(x + 1, y, 0.6f) } }
    }
}

/**
 * Schrodinger's cat, the quantum slot machine: three reels spin; each key stops the next one. Three alike is a jackpot
 * (the lights round it chase and flash); the odds rise as you type, and after 40 s every spin lands on the living cat.
 */
internal class SlotDesk : Desk() {
    private companion object {
        val SYM = listOf(
            listOf("#...#", "#####", "#.#.#", "#####", ".###."), // the cat, alive
            listOf(".###.", "#####", "#.#.#", ".###.", "#.#.#"), // the ghost
            listOf("..#..", ".###.", "#####", ".###.", "..#.."), // a diamond
            listOf(".#.#.", "#####", "#####", ".###.", "..#.."), // a heart
        )
    }
    private val stopAt = IntArray(3) { -1 }
    private var lastN = -1
    private var win = -9_999L

    override fun draw(c: Cv, d: DeskInput) {
        val k = d.last
        if (k != null && k.n != lastN) {
            lastN = k.n
            val reel = stopAt.indexOfFirst { it < 0 }
            if (reel < 0) { for (i in 0..2) stopAt[i] = -1 }
            else {
                val luck = typedK(d)
                stopAt[reel] = when {
                    after40(d) -> 0
                    reel > 0 && h(k.n, 77) < 0.25f + 0.5f * luck -> stopAt[0]
                    else -> (h(k.n, 13) * SYM.size).toInt().coerceAtMost(SYM.size - 1)
                }
                if (reel == 2 && stopAt[0] == stopAt[1] && stopAt[1] == stopAt[2]) win = d.t
            }
        }
        // The machine.
        c.shape(List(11) { "#####################" }, 12, 12, 0.12f, 0.45f)
        for (i in 0..2) {
            val x0 = 3 + i * 7
            val s = stopAt[i]
            if (s >= 0) c.big(SYM[s], x0, 10, 0.95f)
            else { val roll = ((d.t / 60 + i * 3) % (SYM.size * 6)).toInt(); val sym = SYM[roll / 6]; val off = roll % 6 - 3
                for ((dx, dy) in cells(sym)) { val y = 10 + dy + off; if (y in 8..16) c.set(x0 + dx, y, 0.5f) } }
        }
        val w = d.t - win
        if (w in 0..1500) {
            val chase = (d.t / 60).toInt()
            for (x in 2..22) { c.set(x, 6, if ((x + chase) % 3 == 0) 1f else 0.2f); c.set(x, 18, if ((x - chase) % 3 == 0) 1f else 0.2f) }
            if ((d.t / 150) % 2 == 0L) for (i in 0..2) c.big(SYM[stopAt[i].coerceAtLeast(0)], 3 + i * 7, 10, 1f)
        } else for (x in 2..22 step 4) { c.set(x, 6, 0.3f); c.set(x, 18, 0.3f) }
    }
}

