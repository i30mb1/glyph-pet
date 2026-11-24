package n7.glyphpet.pet.drawn

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The heroes picked from the sketches (atlas "new pets"): each a drawing of its own in every state, and each with
 * one rule that follows the phone ([DrawnWorld]): the hourglass pours when turned over, the water in the fish's jar
 * keeps level, the dandelion's seeds fly when blown, the bubble swells and pops, the capybara melts into bliss when
 * the phone is held still, the moon wears tonight's phase, the tardigrade curls into a barrel on a low battery, the
 * cassette spins to the music in the room, the ferrofluid spikes toward north and any magnet.
 */
object NewHeroes {
    val all: List<Anomaly> = listOf(Hourglass, FishJar, Dandelion, Bubble, Capybara, Moon, Tardigrade, Cassette, Ferro)
}

private val PIF = PI.toFloat()

internal abstract class NewHero(kind: String, names: List<String>, private val tier: Rarity, idea: String, tags: Map<String, String>) :
    Anomaly(kind, names, "", idea, tags) {
    override val rarity get() = tier

    /** How big each stage is drawn. */
    protected fun s(st: Int) = floatArrayOf(0.7f, 0.85f, 1f)[st]

    protected fun blink(t: Long, every: Long = 3200, at: Long = 2300) = t % every in at..at + 160

    /** Draws [draw] into a canvas of its own and lays it onto [c] turned by [angle] round ([px], [py]) and moved. */
    protected fun posed(c: Cv, angle: Float = 0f, dx: Float = 0f, dy: Float = 0f, px: Float = 12f, py: Float = 13f, draw: (Cv) -> Unit) {
        val tmp = Cv()
        draw(tmp)
        if (abs(angle) < 0.01f && abs(dx) < 0.01f && abs(dy) < 0.01f) { for (i in tmp.v.indices) if (tmp.v[i] > c.v[i]) c.v[i] = tmp.v[i]; return }
        val ca = cos(angle); val sa = sin(angle)
        for (y in 0..24) for (x in 0..24) {
            val rx = x - dx - px; val ry = y - dy - py
            val u = px + rx * ca + ry * sa
            val w = py - rx * sa + ry * ca
            val v = tmp.get(u.roundToInt(), w.roundToInt())
            if (v > 0f) c.put(x, y, v)
        }
    }

    /** Hearts rising round ([x], [y]). */
    protected fun hearts(c: Cv, x: Float, y: Float, t: Long) {
        for (k in 0..1) {
            val p = ((t + k * 700) % 1400) / 1400f
            c.rows(listOf("o.o", "ooo", ".o."), (x - 1 + (if (k == 0) -4 else 4) + sin(p * 6) * 0.6f).roundToInt(), (y - p * 6).roundToInt(), 0.6f * (1 - p) + 0.1f)
        }
    }

    protected fun rain(c: Cv, t: Long) {
        for (i in 0 until 9) { val ph = ((t + i * 377) % 900) / 900f; c.put(2f + rnd(i, 3) * 21, ph * 24, 0.32f) }
    }

    protected fun zs(c: Cv, x: Float, y: Float, t: Long) {
        val ph = (t % 2000) / 2000f
        c.rows(listOf("ooo", ".o.", "ooo"), (x + ph * 2).roundToInt(), (y - ph * 5).roundToInt(), 0.3f * (1 - ph) + 0.05f)
    }

    protected fun stars(c: Cv, x: Float, y: Float, t: Long) {
        for (k in 0..2) { val a = t / 200f + k * 2.1f; c.put(x + cos(a) * 4, y + sin(a) * 1.5f, 0.9f) }
    }

    protected fun sparkle(c: Cv, cx: Float, cy: Float, r: Float, t: Long) {
        for (k in 0 until 5) {
            val slot = t / 180 + k * 3
            val a = rnd(slot.toInt(), k) * 2 * PIF
            val d = r * (0.6f + 0.6f * rnd(k, slot.toInt()))
            c.put(cx + cos(a) * d, cy + sin(a) * d, 0.8f)
        }
    }

    /** What it longs for when hungry, as a little thought rising at the top right. */
    protected fun thought(c: Cv, rows: List<String>, t: Long) {
        val bob = ((t / 400) % 2).toInt()
        c.put(17f, 8f, 0.25f); c.put(18f, 6f, 0.3f)
        c.rows(rows, 18, 2 + bob, 0.5f)
    }

    protected fun hop(t: Long, period: Float, h: Float) = abs(sin(t / period * PIF)) * h

    /** Whether [col] is one of its pastimes ([pastimes]). */
    protected fun isPastime(col: String) = pastimes.any { it.id == col }

    /**
     * A pastime's two ends: for its first and last [Drawn.PASTIME_EDGE] ms it is exactly the calm clip ([calmNow]), so
     * the dissolve in and out never shows two heroes. True when it drew that (nothing else to draw).
     */
    protected fun calmEdge(c: Cv, col: String, t: Long, st: Int): Boolean {
        val ms = pastimes.first { it.id == col }.ms
        if (t < Drawn.PASTIME_EDGE || t >= ms - Drawn.PASTIME_EDGE) { calmNow(c, t, st); return true }
        return false
    }

    /** Where the calm clip's own clock is at pastime time [t] (the t its "calm" branch is drawn with). */
    protected fun calmClock(t: Long) = Math.floorMod(calmT(t), Drawn.length("calm"))

    /** Lays [tmp] onto [c] without its crumbs (groups of 1-3 lit LEDs: what is left of a part sliding off the matrix). */
    protected fun layWhole(c: Cv, tmp: Cv) {
        val seen = BooleanArray(625)
        for (i in 0 until 625) {
            if (tmp.v[i] <= 0f || seen[i]) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack += i; seen[i] = true
            while (stack.isNotEmpty()) {
                val p = stack.removeLast(); group += p
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = p % 25 + dx; val ny = p / 25 + dy
                    if (nx !in 0..24 || ny !in 0..24) continue
                    val n = ny * 25 + nx
                    if (!seen[n] && tmp.v[n] > 0f) { seen[n] = true; stack += n }
                }
            }
            if (group.size > 3) for (p in group) if (tmp.v[p] > c.v[p]) c.v[p] = tmp.v[p]
        }
    }

    /** A little sprite ('#' [b], '+' dim, 'o' bright, '-' off) drawn whole or not at all: never cut by the round rim. */
    protected fun whole(c: Cv, rows: List<String>, x: Int, y: Int, b: Float) {
        for ((dy, r) in rows.withIndex()) for ((dx, ch) in r.withIndex()) if (ch != '.' && !GlyphMatrixSpec.isVisible(x + dx, y + dy)) return
        c.rows(rows, x, y, b)
    }
}

// --- the hourglass: a pet of sand ------------------------------------------------------------------------------------------

private object Hourglass : NewHero(
    "hourglass", listOf("Grain", "Hourglass", "Sandglass"), Rarity.RARE,
    "Песочные часы: тело из песка; переверни телефон, и он пересыпается в другую колбу",
    mapOf("calm" to "пересыпается", "happy" to "песок подпрыгивает", "habit" to "переворачивается сам", "signature" to "песок в сердце",
        "hungry" to "колба почти пуста", "sad" to "мокрый песок", "tired" to "сыплется медленно", "asleep" to "песок замер",
        "eating" to "ловит песчинку", "game" to "ловит песчинки", "loved" to "песок светится", "dizzy" to "кружится",
        "tilt" to "песок съезжает", "charging" to "песок раскаляется", "grow" to "колба выдувается"),
) {
    private val H = intArrayOf(6, 8, 9)
    private val W = intArrayOf(3, 4, 5)

    private fun half(st: Int, d: Int) = if (d == 0) 0 else min(W[st], 1 + (d * W[st] + H[st] / 2) / H[st])

    /**
     * The glass, [top] of the sand in the upper bulb, a stream while [flow], eyes ([eyes] 0 open, 1 shut, 2 happy, 3 sad,
     * 4 shut tight). [stream] 1 pours a solid stream up from the lower bulb, -1 down into it.
     */
    private fun glass(c: Cv, st: Int, t: Long, top: Float, flow: Boolean, eyes: Int, b: Float = 1f, slant: Float = 0f, stream: Int = 0) {
        val h = H[st]
        for (y in 12 - h..12 + h) {
            val d = abs(y - 12)
            val hw = half(st, d)
            c.put(12 - hw - 1, y, 0.2f * b); c.put(12 + hw + 1, y, 0.2f * b)
        }
        for (x in 12 - W[st] - 1..12 + W[st] + 1) { c.put(x, 12 - h - 1, 0.45f * b); c.put(x, 12 + h + 1, 0.45f * b) }
        val upRows = (top * h).roundToInt()
        val downRows = ((1 - top) * h).roundToInt()
        for (y in 12 - h..12 + h) {
            val d = abs(y - 12)
            val hw = half(st, d)
            for (x in 12 - hw..12 + hw) {
                val lean = (slant * (x - 12)).roundToInt()
                val inTop = y < 12 && y >= 12 - upRows + lean
                val inBottom = y > 12 && y > 12 + h - downRows + lean
                if (inTop || inBottom) c.put(x, y, (0.42f + 0.12f * rnd(x, y)) * b)
            }
        }
        if (flow && top > 0.02f && downRows < h) {
            val bottom = 12 + h - downRows
            for (y in 12..bottom) if (((y + t / 90) % 2).toInt() == 0) c.put(12, y, 0.75f * b)
        }
        if (stream == 1 && downRows > 0 && upRows < h || stream == -1 && upRows > 0 && downRows < h) {
            val bottom = 12 + h - downRows
            for (y in 12..bottom) c.put(12, y, (if (Math.floorMod(y + stream * (t / 90).toInt(), 3) == 0) 0.85f else 0.5f) * b)
        }
        val ey = if (top >= 0.5f) 12 - max(2, upRows / 2) else 12 + h - max(1, downRows / 2)
        when (eyes) {
            0 -> { c.cut(10, ey); c.cut(14, ey) }
            1 -> { c.set(10, ey, 0.08f); c.set(14, ey, 0.08f) }
            2 -> { c.cut(10, ey); c.cut(14, ey); c.cut(9, ey + 1); c.cut(11, ey + 1); c.cut(13, ey + 1); c.cut(15, ey + 1) }
            3 -> { c.cut(10, ey + 1); c.cut(14, ey + 1); c.cut(9, ey); c.cut(15, ey) }
            4 -> { c.cut(9, ey); c.cut(10, ey); c.cut(14, ey); c.cut(15, ey) }
        }
    }

    /**
     * The glass lying on its side (its upper bulb to the right), the sand settled flat in each bulb, the face in the
     * fuller one; [eyes] as in [glass].
     */
    /** [side] the bulb its face is in (1 the top one), by default the fuller one; a pastime keeps it while the sand moves. */
    private fun lying(c: Cv, st: Int, top: Float, eyes: Int, side: Int = if (top >= 0.5f) 1 else -1) {
        val h = H[st]; val w = W[st]
        for (x in 12 - h..12 + h) { val hw = half(st, abs(x - 12)); c.put(x, 12 - hw - 1, 0.2f); c.put(x, 12 + hw + 1, 0.2f) }
        for (y in 12 - w - 1..12 + w + 1) { c.put(12 - h - 1, y, 0.45f); c.put(12 + h + 1, y, 0.45f) }
        val cells = (1..h).sumOf { 2 * half(st, it) + 1 }
        fun level(f: Float): Int {
            val want = (f * cells).roundToInt()
            var lv = 12 + w + 1; var n = 0
            while (n < want && lv > 12 - w) { lv--; n += (1..h).count { abs(lv - 12) <= half(st, it) } }
            return lv
        }
        var face = 12
        for (s in intArrayOf(-1, 1)) {
            val lv = level(if (s == 1) top else 1 - top)
            if (s == side) face = lv
            for (d in 1..h) { val x = 12 + s * d; val hw = half(st, d); for (y in max(lv, 12 - hw)..12 + hw) c.put(x, y, 0.42f + 0.12f * rnd(x, y)) }
        }
        val bx = 12 + side * (h * 2 / 3)
        val ey = (face + 1).coerceIn(11, 12 + half(st, h * 2 / 3 - 2) - 1)
        when (eyes) {
            4 -> { c.cut(bx - 3, ey); c.cut(bx - 2, ey); c.cut(bx + 2, ey); c.cut(bx + 3, ey) }
            // Asleep, peeking with one eye: the left shut, the right open.
            5 -> { c.cut(bx - 3, ey); c.cut(bx - 2, ey); c.cut(bx + 2, ey) }
            else -> { c.cut(bx - 2, ey); c.cut(bx + 2, ey) }
        }
    }

    override val pastimes = listOf(
        DrawnPastime("pour_up", 4800, "сыплется вверх",
            "Песок вдруг течёт снизу вверх, и рожица перебирается в верхнюю колбу; довольный, он пересыпается обратно."),
        DrawnPastime("side_nap", 5600, "прилёг на бочок",
            "Ложится на бок, песок растекается ровным слоем, и он дремлет, зажмурившись; потом встаёт обратно."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val top0 = DrawnWorld.sandTop
        when (col) {
            "pour_up" -> {
                // The wrong way: up, if the sand lies below (as it does on a phone standing upright), and back.
                val amt = if (top0 <= 0.5f) min(0.6f, 1f - top0) else -min(0.6f, top0)
                val q = ease(seg(t, 800, 2200)) - ease(seg(t, 3000, 4100))
                val top = (top0 + amt * q).coerceIn(0f, 1f)
                val dir = if (amt > 0f) 1 else -1
                val stream = when (t) { in 800L until 2200L -> dir; in 3000L until 4100L -> -dir; else -> 0 }
                val eyes = when { t in 800L until 3000L && top < 0.5f -> 2; blink(ct) -> 1; else -> 0 }
                glass(c, st, ct, top, flow = false, eyes = eyes, stream = stream)
            }
            "side_nap" -> {
                // A quarter turn onto its side (whole, never a slant poking off the round matrix), the sand slumps flat, a
                // nap, and up again.
                val a = if (t in 900L until 4100L) PIF / 2 else 0f
                // On its side the sand slumps level, half and half, and goes back before it stands; mid-nap it peeks
                // with one eye. So the nap lives however full the glass is.
                val settle = ease(seg(t, 1300, 2300)) - ease(seg(t, 3000, 3700))
                val eyes = when { t in 2500L until 2800L -> 5; t in 1500L until 3600L -> 4; else -> 0 }
                if (t in 1300L until 3800L) lying(c, st, mix(top0, 0.5f, settle), eyes, side = if (top0 >= 0.5f) 1 else -1)
                else posed(c, angle = a, py = 12f) { glass(it, st, ct, top0, flow = a == 0f, eyes = if (a == 0f && blink(ct)) 1 else 0) }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val top = DrawnWorld.sandTop
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> glass(c, st, t, top, flow = true, eyes = e)
            "happy" -> { val h = hop(t, 450f, 2f); posed(c, dy = -h) { glass(it, st, t, top, true, 2) }; if (h < 0.4f) sparkle(c, 12f, 13f + H[st], 4f, t) }
            "habit" -> { val a = PIF * ease(seg(t, 400, 1800)); posed(c, angle = a, py = 12f) { glass(it, st, t, if (t < 1800) top else 1 - top, t > 1800, 0) } }
            "signature" -> {
                glass(c, st, t, 0.2f, false, 2)
                // Grains lift out of the glass, make a heart over it, and pour back in.
                val heart = listOf(-2 to 0, -1 to -1, 0 to 0, 1 to -1, 2 to 0, -2 to 1, 2 to 1, -1 to 2, 1 to 2, 0 to 3)
                val p = when { t < 1600 -> ease(seg(t, 200, 1600)); t < 4400 -> 1f; else -> 1 - ease(seg(t, 4400, 6000)) }
                heart.forEachIndexed { i, (hx, hy) ->
                    val sx = 12f + (i % 3 - 1); val sy = 14f
                    val tx = 12f + hx * 1.6f; val ty = 3f + hy * 1.4f
                    c.put(mix(sx, tx, p), mix(sy, ty, p), 0.6f + 0.4f * p)
                }
            }
            "hungry" -> { glass(c, st, t, 0.05f, (t / 300) % 3 == 0L, 0); thought(c, listOf("o", "o"), t) }
            "sad" -> { rain(c, t); glass(c, st, t, top, false, 3, b = 0.7f) }
            "tired" -> glass(c, st, t, top, (t / 500) % 2 == 0L, if ((t / 800) % 2 == 0L) 1 else 0, b = 0.85f)
            "asleep" -> { glass(c, st, t, top, false, 1, b = 0.8f); zs(c, 17f, 8f, t) }
            "eating" -> { val y = mix(1f, 11f - top * H[st], seg(t, 0, 1200)); if (t < 1300) c.put(12f, y, 0.9f); glass(c, st, t, top, true, if (t > 1300) 2 else 0) }
            "game" -> posed(c, angle = tilt * 0.5f, py = 12f) { glass(it, st, t, top, true, 0, slant = -tilt * 0.6f); val gx = 12f + sin(t / 500f) * 6; it.put(gx, ((t % 1600) / 1600f) * 6f, 0.9f) }
            "loved" -> { glass(c, st, t, top, true, 2, b = 1.15f + 0.2f * sin(t / 200f)); hearts(c, 12f, 4f, t) }
            "dizzy" -> { posed(c, angle = sin(t / 160f) * 0.45f, py = 12f) { glass(it, st, t, top, true, 1) }; stars(c, 12f, 2f, t) }
            "tilt" -> posed(c, angle = tilt * 0.6f, py = 12f) { glass(it, st, t, top, true, e, slant = -tilt * 0.8f) }
            "charging" -> { val p = seg(t, 0, 3000); glass(c, st, t, top, true, if (p > 0.9f) 2 else 0, b = 0.8f + 0.7f * p); sparkle(c, 12f, 12f, 8f, t) }
            // Typing: sand pours with the keys and runs down as you type; when the top is empty (every 40 s) it turns
            // itself over and starts again.
            "typing" -> { val y = DrawnTyping; val cyc = y.typedS % 40f
                if (y.typedS >= 40f && cyc < 1.6f) { draw(c, "habit", 400L + (cyc / 1.6f * 1400f).toLong(), st, tilt); return }
                glass(c, st, t, (1f - cyc / 40f).coerceAtLeast(0.03f), flow = y.sinceKey < 400 || y.rush, eyes = if (y.rush) 2 else if (y.paused && blink(t)) 1 else 0) }
            "grow" -> {
                // The glass rises row by row from its foot, then sand pours in from above.
                if (t < 2600) {
                    val rows = (seg(t, 0, 2600) * (H[st] * 2 + 3)).toInt()
                    val tmp = Cv(); glass(tmp, st, t, 0f, false, 1)
                    for (y in 0..24) for (x in 0..24) if (y >= 12 + H[st] + 1 - rows) c.put(x, y, tmp.get(x, y))
                } else {
                    val q = seg(t, 2600, 5600)
                    glass(c, st, t, q, false, if (t > 5600) 2 else 0)
                    for (k in 0..3) c.put(12f, ((t / 60 + k * 3) % 10) / 10f * (11f - H[st]), 0.7f)
                }
            }
        }
    }
}

// --- the fish in a jar: the water keeps level -------------------------------------------------------------------------------

private object FishJar : NewHero(
    "fishjar", listOf("Fry", "Goldfish", "Koi"), Rarity.COMMON,
    "Рыбка в банке: матрица и есть круглая банка; вода держит горизонт, как ни наклоняй",
    mapOf("calm" to "плавает", "happy" to "выпрыгивает", "habit" to "пускает кольца", "signature" to "мёртвая петля",
        "hungry" to "у поверхности", "sad" to "мутная вода", "tired" to "плавает медленно", "asleep" to "спит у дна",
        "eating" to "ловит хлопья", "game" to "ловит наклоном", "loved" to "пузырьки-сердечки", "dizzy" to "водоворот",
        "tilt" to "вода держит горизонт", "charging" to "вода светится", "grow" to "икринка"),
) {
    private fun water(c: Cv, t: Long, angle: Float, slosh: Float = 0f, level: Float = 2f, b: Float = 1f) {
        val nx = sin(angle); val ny = cos(angle)
        for (y in 0..24) for (x in 0..24) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val px = x - 12f; val py = y - 12f
            val along = px * ny - py * nx
            val depth = px * nx + py * ny - level + slosh * along / 10f + 0.5f * sin(along * 0.7f + t / 260f)
            if (depth > 0.6f) c.put(x, y, (0.12f + 0.03f * sin((x + y + t / 200f) * 0.8f)) * b) else if (depth > -0.4f) c.put(x, y, 0.42f * b)
        }
    }

    /** The fish at ([x], [y]) facing right when [right], [eyes] 0 open, 1 shut. */
    private fun fish(c: Cv, st: Int, x: Float, y: Float, right: Boolean, eyes: Int = 0, b: Float = 1f, mouth: Boolean = false) {
        val rows = when (st) {
            0 -> listOf(".#o", "###", ".#.")
            1 -> listOf(".##.#", "#o###", ".##.#")
            else -> listOf("..###..#", ".#o####.", "########", ".#####.#")
        }
        // The baby is drawn facing right, the bigger ones facing left: each is turned to swim head first.
        val r = if (right == (st == 0)) rows else rows.map { it.reversed() }
        val left = (x - r[0].length / 2f).roundToInt(); val top = (y - r.size / 2f).roundToInt()
        r.forEachIndexed { dy, row -> row.forEachIndexed { dx, ch ->
            when (ch) {
                '#' -> c.put(left + dx, top + dy, 0.6f * b)
                'o' -> if (eyes == 0) c.cut(left + dx, top + dy) else c.put(left + dx, top + dy, 0.2f)
            }
        } }
        if (mouth) c.cut(if (right) left + r[0].length else left - 1, top + r.size / 2)
    }

    private fun swim(t: Long, period: Long = 5200): Triple<Float, Float, Boolean> {
        val ph = (t % period) / period.toFloat()
        val right = ph < 0.5f
        val x = 12 + if (right) -5 + ph * 20 else 5 - (ph - 0.5f) * 20
        return Triple(x, 17f + sin(t / 400f) * 0.7f, right)
    }

    private fun bubbles(c: Cv, x: Float, y: Float, t: Long, n: Int = 3) {
        for (k in 0 until n) { val q = ((t + k * 700) % 2100) / 2100f; c.put(x + sin(q * 9f) * 0.6f, y - q * 9, 0.5f * (1 - q) + 0.15f) }
    }

    private fun worldAngle() = atan2(DrawnWorld.tilt, DrawnWorld.tiltY.coerceAtLeast(0.2f)) * 0.8f

    /** The fish face on, nearer and nearer the glass: small, middle, pressed to it (mouth shut), pressed (mouth open). */
    private val FRONT = listOf(
        listOf("..###..", ".#-#-#.", "+#####+", "..#-#.."),
        listOf("...###...", ".#######.", "+##-#-##+", "+#######+", "..##-##..", "...###..."),
        listOf("....###....", "..#######..", ".#########.", "+##--#--##+", "+##--#--##+", ".#########.", "..###-###..", "....###...."),
        listOf("....###....", "..#######..", ".#########.", "+##--#--##+", "+##--#--##+", ".####-####.", "..##---##..", "....#-#...."),
    )

    private fun front(c: Cv, n: Int, cy: Float) {
        val r = FRONT[n]
        val left = 12 - r[0].length / 2; val top = (cy - r.size / 2f).roundToInt()
        r.forEachIndexed { dy, row -> row.forEachIndexed { dx, ch ->
            when (ch) { '#' -> c.put(left + dx, top + dy, 0.6f); '+' -> c.put(left + dx, top + dy, 0.3f); '-' -> c.cut(left + dx, top + dy) }
        } }
    }

    override val pastimes = listOf(
        DrawnPastime("glass_kiss", 4800, "целует стекло",
            "Подплывает к стеклу и поворачивается к тебе мордочкой; всё ближе и ближе, прижимается и чмокает губами, потом уплывает дальше."),
        DrawnPastime("bubble_ball", 5200, "гоняет пузырь",
            "Выдувает большой пузырь и толкает его носом через банку; пузырь отскакивает от стенки и всплывает, а рыбка провожает его взглядом."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        val ct = calmClock(t)
        val (sx, sy, sr) = swim(calmClock(e))
        val (ex, ey, er) = swim(calmClock(ms - e))
        val shut = if (blink(ct)) 1 else 0
        water(c, ct, worldAngle())
        when (col) {
            "glass_kiss" -> when {
                t < 1100 -> { val p = ease(seg(t, e, 1100)); fish(c, st, mix(sx, 12f, p), mix(sy, 17f, p), if (abs(sx - 12f) < 1f) sr else sx < 12f, shut) }
                t < 1300 -> front(c, 0, 17.5f)
                t < 1500 -> front(c, 1, 17.5f)
                t < 3100 -> front(c, if (t > 1800 && (t - 1800) % 500 < 220) 3 else 2, 17.5f)
                t < 3300 -> front(c, 1, 17.5f)
                t < 3500 -> front(c, 0, 17.5f)
                else -> { val p = ease(seg(t, 3500, ms - e)); fish(c, st, mix(12f, ex, p), mix(17f, ey, p), if (p > 0.8f || abs(ex - 12f) < 1f) er else ex > 12f, shut) }
            }
            "bubble_ball" -> {
                // Blows a bubble, noses it across to the wall, it bounces back over the fish and floats up to the top.
                var fx = 10f; var fy = 19.5f; var right = true
                var bx = -1f; var by = 0f; var br = 1.5f
                when {
                    t < 1100 -> { val p = ease(seg(t, e, 1100)); fx = mix(sx, 10f, p); fy = mix(sy, 19.5f, p); right = if (p > 0.6f) true else sx < 10f }
                    t < 1500 -> { bx = 15.5f; by = 19f; br = mix(0.8f, 1.5f, seg(t, 1100, 1500)) }
                    t < 2400 -> { val p = seg(t, 1500, 2400); fx = mix(10f, 13.5f, p); bx = mix(15.5f, 19f, p); by = 19f }
                    t < 3000 -> { val p = ease(seg(t, 2400, 3000)); fx = 13.5f; right = t < 2600; bx = mix(19f, 13f, p); by = mix(19f, 16f, p) }
                    t < 3800 -> { val p = seg(t, 3000, 3800); fx = mix(13.5f, 15f, p); fy = mix(19.5f, 18f, p); right = false; bx = mix(13f, 12f, p); by = mix(16f, 13.5f, p * p) }
                    else -> { val p = ease(seg(t, 3800, ms - e)); fx = mix(15f, ex, p); fy = mix(18f, ey, p); right = if (p > 0.7f) er else ex > 15f }
                }
                fish(c, st, fx, fy, right, shut, mouth = t in 1100L until 1500L)
                if (bx >= 0f) {
                    // Air: a dark ring in the water round a glint (its own water cell, lit brighter).
                    val x = bx.roundToInt(); val y = by.roundToInt()
                    if (br < 1.2f) c.cut(x, y)
                    else { for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) c.cut(x + dx, y + dy); c.put(x, y, 0.8f) }
                }
            }
        }
    }

    private var hKey = -1
    private val hFood = ArrayList<Long>()
    private var hx = 12f
    private var hy = 17f
    private var hRight = true
    private var hEat = -9_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val a = worldAngle()
        val (fx, fy, right) = swim(t)
        when (col) {
            "calm" -> { water(c, t, a); fish(c, st, fx, fy, right, if (blink(t)) 1 else 0); bubbles(c, fx + if (right) 2 else -2, fy, t) }
            "happy" -> { water(c, t, 0f); val p = (t % 1200) / 1200f; val jx = 8 + p * 8; val jy = 16 - sin(p * PIF) * 11; fish(c, st, jx, jy, true); if (p > 0.85f) for (k in -2..2) c.put(16f + k, 13f - abs(k) * 0.5f, 0.6f) }
            "habit" -> { water(c, t, 0f); fish(c, st, 9f, 16f, true); for (k in 0..1) { val q = ((t + k * 800) % 1600) / 1600f; c.ring(13f + q * 4, 15f - q * 6, 0.8f + q * 1.5f, 0.5f * (1 - q)) } }
            "signature" -> {
                water(c, t, 0f)
                val p = seg(t, 0, 4000)
                val ang = p * 2 * PIF
                fish(c, st, 12 + cos(ang - PIF / 2) * 5, 15 + sin(ang - PIF / 2) * 4 + 4, cos(ang) >= 0)
                if (t > 4000) for (k in 0 until 10) { val q = seg(t, 4000, 6000); val aa = k * 0.63f; c.put(12 + cos(aa) * q * 9, 13 + sin(aa) * q * 4, 0.6f * (1 - q)) }
            }
            "hungry" -> { water(c, t, 0f); fish(c, st, 12f, 14.5f, true, mouth = (t / 300) % 2 == 0L); thought(c, listOf("o.o", ".o."), t) }
            "sad" -> { rain(c, t); water(c, t, 0f, b = 0.6f); fish(c, st, 12f, 21f, true, b = 0.7f) }
            "tired" -> { water(c, t, 0f); val (x, y, r) = swim(t, 10400); fish(c, st, x, y + 2, r, if ((t / 700) % 2 == 0L) 1 else 0, b = 0.8f) }
            "asleep" -> { water(c, t, 0f, b = 0.7f); fish(c, st, 12f, 21f, true, 1, b = 0.7f); bubbles(c, 14f, 20f, t, 2) }
            "eating" -> { water(c, t, 0f); val fl = (0..2).map { k -> 10f + k * 2f to min(13f, 1f + seg(t, 0, 1500) * 14 - k) }; if (t < 2000) fl.forEach { (x, y) -> if (y > 2) c.put(x, y, 0.8f) }; val up = if (t in 1200L..2600L) sin(seg(t, 1200, 2600) * PIF) else 0f; fish(c, st, 12f, 17f - up * 4, true, mouth = up > 0.5f) }
            "game" -> { water(c, t, tilt * 0.7f, slosh = tilt * 3); fish(c, st, 12 + tilt * 6, 17f, tilt >= 0) }
            "loved" -> { water(c, t, 0f); fish(c, st, 12f, 17f, true); for (k in 0..1) { val q = ((t + k * 600) % 1200) / 1200f; c.rows(listOf("o.o", ".o."), 13 + k * 2, (15 - q * 6).roundToInt(), 0.6f * (1 - q)) } }
            "dizzy" -> { water(c, t, 0f); for (k in 0 until 14) { val aa = t / 200f + k * 0.45f; val r = 2f + k * 0.5f; c.put(12 + cos(aa) * r, 18 + sin(aa) * r * 0.4f, 0.3f) }; fish(c, st, 12 + cos(t / 200f) * 3, 18f, (t / 600) % 2 == 0L, 1) }
            "tilt" -> { water(c, t, tilt * 0.8f, slosh = tilt * 2); fish(c, st, 12 + tilt * 4, 17f, tilt >= 0) }
            "charging" -> { val p = seg(t, 0, 3000); water(c, t, 0f, level = 6f - p * 8f, b = 0.8f + 0.6f * p); fish(c, st, fx, fy, right) }
            // Typing, the hunt: each key a flake drops into the jar and the fish darts after it and snaps it up; it grows
            // as you type (bigger at 20 s and 40 s), and after 40 s it loops the loop for joy now and then.
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2 && y.stageMs % 8000 < 4000) { draw(c, "signature", y.stageMs % 8000, st, tilt); return }
                water(c, t, 0f)
                if (y.key != hKey) { if (y.key >= 0) hFood += (6 + (y.key * 7) % 13) * 1_000_000L + y.now; hKey = y.key }
                val food = hFood.map { (it / 1_000_000L).toFloat() to min(21f, 3f + (y.now - it % 1_000_000L) / 1000f * 14f) }
                val tgt = food.firstOrNull { it.second > 14f }
                val sp = (if (y.rush) 40f else 24f) * y.dt / 1000f
                if (tgt != null) { val dx = tgt.first - hx; val dy = tgt.second - hy; val d = hypot(dx, dy)
                    if (d < 1.6f) { hFood.removeAt(food.indexOf(tgt)); hEat = y.now } else { hx += dx / d * min(sp, d); hy += dy / d * min(sp, d); if (abs(dx) > 0.5f) hRight = dx > 0 } }
                else { hy += (19f - hy) * 0.05f }
                for ((fx, fy) in food) { c.put(fx, fy, 0.95f); c.put(fx + 1, fy, 0.5f) }
                fish(c, min(2, 1 + (y.typedS / 40f).toInt()), hx, hy, hRight, if (y.paused && blink(t)) 1 else 0, mouth = y.now - hEat < 220 || tgt != null && hypot(tgt.first - hx, tgt.second - hy) < 4f) }
            "grow" -> { water(c, t, 0f); if (t < 2400) { c.disc(12f, 18f, 1.3f, 0.5f); c.cut(12f, 18f) } else if (t < 3000) c.disc(12f, 18f, 1.5f, 0.7f) else fish(c, st, mix(12f, fx, seg(t, 3000, 4400)), 18f, true) }
        }
    }
}

// --- the dandelion ----------------------------------------------------------------------------------------------------------

private object Dandelion : NewHero(
    "dandelion", listOf("Bud", "Dandelion", "Puffball"), Rarity.COMMON,
    "Одуванчик: подуешь, и семена разлетаются по матрице; голова отрастает заново",
    mapOf("calm" to "покачивается", "happy" to "семена искрятся", "habit" to "отпускает семечко", "signature" to "хоровод семян",
        "hungry" to "поник без воды", "sad" to "мокнет", "tired" to "клонится", "asleep" to "закрылся",
        "eating" to "пьёт каплю", "game" to "семена на ветру", "loved" to "светится", "dizzy" to "голова кругом",
        "tilt" to "гнётся стебель", "charging" to "тянется к солнцу", "grow" to "прорастает"),
) {
    /** [open] (0 shut .. 1 open) overrides [closed]; [lx], [ly] turn the eyes; eyes -1 hides them. */
    private fun flower(c: Cv, st: Int, t: Long, seeds: Int, sway: Float, eyes: Int, b: Float = 1f, droop: Float = 0f, closed: Boolean = false,
                       open: Float = -1f, lx: Int = 0, ly: Int = 0) {
        val k = s(st)
        val stemTop = 21f - 11f * k
        val hx = 12f + sway + droop * 3; val hy = stemTop + droop * 3
        for (i in 0..20) { val q = i / 20f; c.put(mix(12f, hx, q * q), mix(21f, hy + 2, q), 0.33f * b) }
        c.line(12f, 18f, 9.5f, 16f, 0.28f * b); c.line(12f, 19.5f, 14.5f, 17.5f, 0.28f * b)
        c.disc(hx, hy, 2f * k + 0.3f, 0.4f * b)
        val r = mix(2.6f, 4.6f, if (open >= 0f) open else if (closed) 0f else 1f) * k
        for (i in 0 until seeds) {
            val a = i * 2 * PIF / 16
            c.put(hx + cos(a) * r * 0.65f, hy + sin(a) * r * 0.65f, 0.22f * b)
            c.put(hx + cos(a) * r, hy + sin(a) * r, (0.55f + 0.3f * sin(t / 300f + i)) * b)
        }
        val ex = hx + lx; val ey = hy + ly
        when (eyes) {
            0 -> { c.cut(ex - 1, ey); c.cut(ex + 1, ey) }
            1 -> { c.set((ex - 1).roundToInt(), ey.roundToInt(), 0.08f); c.set((ex + 1).roundToInt(), ey.roundToInt(), 0.08f) }
            2 -> { c.cut(ex - 1, ey); c.cut(ex + 1, ey); c.cut(ex - 2, ey + 1); c.cut(ex + 2, ey + 1) }
            3 -> { c.cut(ex - 1, ey + 1); c.cut(ex + 1, ey + 1) }
        }
        for (x in 6..18) c.put(x, 22, 0.15f * b)
    }

    override val pastimes = listOf(
        DrawnPastime("peekaboo", 4000, "играет в прятки",
            "Прячет мордочку в свой пух и хихикает, а потом вдруг распускается: «ку-ку!»; и ещё раз."),
        DrawnPastime("ladybug", 5200, "божья коровка",
            "Прилетает божья коровка, садится на листок и ползёт вверх по стеблю; одуванчик следит за ней, хихикает от щекотки, и она улетает."),
    )

    private val BUG = listOf("oo", "oo")
    private val BUG_FLY = listOf("#..#", ".oo.", ".oo.")
    private val BUG_FOLD = listOf("....", "#oo#", ".oo.")

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val seeds = DrawnWorld.seeds.toInt().coerceIn(0, 16)
        val sway = sin(ct / 900f) * 0.6f
        val e = if (seeds == 0) 3 else if (blink(ct)) 1 else 0
        when (col) {
            "peekaboo" -> {
                // Shuts its fluff over its face (giggling, the head shivers), then bursts open, twice.
                val o = 1f - seg(t, 480, 800) + seg(t, 1600, 1700) - seg(t, 2300, 2460) + seg(t, 3000, 3100)
                val hidden = o < 0.5f
                val giggle = if (hidden && t in 900L until 1550L || hidden && t in 2550L until 2950L) (if ((t / 120) % 2 == 0L) 0.6f else -0.6f) else 0f
                val eyes = when { hidden -> -1; t in 1600L until 2300L || t in 3000L until 3450L -> 2; else -> e }
                flower(c, st, ct, max(seeds, 1), sway + giggle, eyes, open = o)
            }
            "ladybug" -> {
                // A ladybird flies in onto the left leaf, crawls up the stem, tickles, and flies off up to the right.
                val k = s(st)
                val hy = 21f - 11f * k
                var tickle = 0f
                var bx: Float; var by: Float; var inAir = false
                when {
                    t < 1300 -> { val p = seg(t, 480, 1300); bx = mix(3f, 9.5f, p); by = mix(20f, 15.5f, p) - sin(p * PIF) * 1.5f; inAir = true }
                    t < 1700 -> { val p = seg(t, 1300, 1700); bx = mix(9.5f, 12f, p); by = mix(15f, 17f, p) }
                    t < 2900 -> { val p = seg(t, 1700, 2900); by = (mix(17f, hy + 5.5f, p) * 2).roundToInt() / 2f; bx = mix(12f, 12f + sway, ((21f - by) / (21f - hy - 2)).coerceIn(0f, 1f).let { it * it }) }
                    t < 3700 -> { by = hy + 5.5f; bx = 12f + sway; tickle = if ((t / 100) % 2 == 0L) 0.55f else -0.2f }
                    else -> { val p = seg(t, 3700, 4500); bx = mix(12f + sway, 22.5f, p); by = hy + 5.5f - 5.5f * p * p; inAir = true }
                }
                val look = bx - (12f + sway)
                val lx = if (look > 2.5f) 1 else if (look < -2.5f) -1 else 0
                val ly = if (by > hy + 3.5f) 1 else if (by < hy - 3.5f) -1 else 0
                flower(c, st, ct, seeds, if (tickle != 0f) tickle else sway, if (tickle != 0f) 2 else if (seeds == 0) 3 else 0, lx = lx, ly = ly)
                flying(c, st)
                if (t < 4500) {
                    if (inAir) whole(c, if ((t / 80) % 2 == 0L) BUG_FLY else BUG_FOLD, bx.roundToInt() - 2, by.roundToInt() - 2, 0.5f)
                    else whole(c, BUG, (bx - 0.5f).roundToInt(), (by - 0.5f).roundToInt(), 0.5f)
                }
            }
        }
    }

    /** Seeds blown off lately, flying away downwind. */
    private fun flying(c: Cv, st: Int) {
        val now = DrawnWorld.nowMs
        val k = s(st)
        DrawnWorld.blownAt.toList().forEachIndexed { i, at ->
            val q = (now - at) / 1600f
            if (q !in 0f..1f) return@forEachIndexed
            val a = i * 0.9f
            val x = 12 + cos(a) * 4.6f * k + q * 12f
            val y = 21f - 11f * k + sin(a) * 4.6f * k - q * 4 + sin(q * 6 + i) * 1.2f
            c.put(x, y, 0.7f * (1 - q) + 0.1f); c.put(x - 0.7f, y + 0.7f, 0.25f * (1 - q))
        }
    }

    private var tKey = -1
    private var tSeeds = 16f
    private val tFly = ArrayList<Long>()

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val seeds = DrawnWorld.seeds.toInt().coerceIn(0, 16)
        val e = if (blink(t)) 1 else 0
        val sway = sin(t / 900f) * 0.6f
        when (col) {
            "calm" -> { flower(c, st, t, seeds, sway, if (seeds == 0) 3 else e); flying(c, st) }
            "happy" -> { flower(c, st, t, seeds, sin(t / 250f) * 1.2f, 2, b = 1.1f); sparkle(c, 12f, 21f - 11f * s(st), 6f, t) }
            "habit" -> { flower(c, st, t, 15, sway, e); val q = seg(t, 300, 3000); c.put(12 + 4.6f * s(st) + q * 6, 21f - 11f * s(st) - q * 6 + sin(q * 8) * 1.5f, 0.8f * (1 - q) + 0.2f) }
            "signature" -> {
                flower(c, st, t, 0, 0f, 2)
                val p = when { t < 1200 -> ease(seg(t, 0, 1200)); t < 5200 -> 1f; else -> 1 - ease(seg(t, 5200, 6400)) }
                for (i in 0 until 16) { val a = i * 2 * PIF / 16 + t / 600f * p; val r = mix(4.6f * s(st), 9.5f, p); c.put(12 + cos(a) * r, 21f - 11f * s(st) + sin(a) * r * mix(1f, 0.8f, p), 0.7f) }
            }
            "hungry" -> { flower(c, st, t, seeds, 0f, 0, droop = 1f); thought(c, listOf(".o.", "ooo", ".o."), t) }
            "sad" -> { rain(c, t); flower(c, st, t, seeds, 0f, 3, b = 0.7f, droop = 0.8f) }
            "tired" -> flower(c, st, t, seeds, 0f, if ((t / 700) % 2 == 0L) 1 else 0, b = 0.85f, droop = seg(t, 0, 3000) * 0.8f)
            "asleep" -> { flower(c, st, t, 16, 0f, 1, b = 0.8f, closed = true); zs(c, 16f, 8f, t) }
            "eating" -> { val y = mix(0f, 21f - 11f * s(st) - 3, seg(t, 0, 1200)); if (t < 1300) c.put(12f, y, 0.9f); flower(c, st, t, seeds, 0f, if (t > 1300) 2 else 0, b = if (t in 1300..2200) 1.3f else 1f) }
            "game" -> { flower(c, st, t, 8, tilt * 2, 0); for (k in 0..5) { val q = ((t + k * 500) % 3000) / 3000f; c.put(12 + tilt * 10 * q + (k - 2) * 2, 6 + q * 14 + sin(q * 9 + k) * 1.2f, 0.7f) } }
            "loved" -> { flower(c, st, t, seeds.coerceAtLeast(10), sway, 2, b = 1.15f + 0.2f * sin(t / 200f)); hearts(c, 12f, 21f - 11f * s(st) - 4, t) }
            "dizzy" -> { posed(c, angle = t / 180f, px = 12f, py = 21f - 11f * s(st)) { flower(it, st, t, seeds, 0f, 1) }; stars(c, 12f, 4f, t) }
            "tilt" -> flower(c, st, t, seeds, tilt * 3, e)
            "charging" -> { val p = seg(t, 0, 3000); for (k in 0 until 5) { val a = -PIF / 2 + (k - 2) * 0.4f; c.line(12 + cos(a) * 11, 10 + sin(a) * 11, 12 + cos(a) * (11 - p * 3), 10 + sin(a) * (11 - p * 3), 0.35f) }; flower(c, st, t, 16, 0f, if (p > 0.8f) 2 else 0, b = 0.8f + 0.5f * p) }
            // Typing: each key a seed flies off on the wind, the head thinning as you type; after 40 s the seeds dance
            // round it (its signature).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "signature", 1200L + y.stageMs % 4000, st, tilt); return }
                if (y.key != tKey) { if (y.key >= 0) repeat(if (y.rush) 2 else 1) { tFly += y.now * 16 + ((y.key * 5) and 15) }; tKey = y.key }
                val seeds = (16 * (1 - y.grown())).roundToInt().coerceAtLeast(1)
                flower(c, st, t, seeds, sway, if (y.rush) 2 else if (y.paused) e else 0)
                tFly.removeAll { y.now - it / 16 > 1400 }
                for (f in tFly) { val q = (y.now - f / 16) / 1400f; val a = (f % 16) * 2 * PIF / 16; val k = s(st)
                    val x = 12 + cos(a) * 4.6f * k + q * 13f; val yy = 21f - 11f * k + sin(a) * 4.6f * k - q * 6 + sin(q * 6 + a) * 1.2f
                    c.put(x, yy, 0.9f * (1 - q) + 0.1f); c.put(x - 1, yy + 1, 0.4f * (1 - q)); c.put(x + 1, yy + 1, 0.4f * (1 - q)) } }
            "grow" -> { val p = seg(t, 0, 4000); if (p < 0.5f) { val h = p * 2 * 11 * s(st); c.line(12f, 21f, 12f, 21f - h, 0.33f); c.put(12f, 21f - h, 0.6f) } else flower(c, st, t, ((p - 0.5f) * 2 * 16).toInt(), 0f, if (p > 0.95f) 2 else 0) }
        }
    }
}

// --- the soap bubble --------------------------------------------------------------------------------------------------------

private object Bubble : NewHero(
    "bubble", listOf("Droplet", "Bubble", "Foam"), Rarity.COMMON,
    "Мыльный пузырь: подуешь, и он раздувается и переливается; перестараешься, и он лопнет и соберётся снова",
    mapOf("calm" to "переливается", "happy" to "скачет", "habit" to "делится надвое", "signature" to "радужный вихрь",
        "hungry" to "сдувается", "sad" to "мутнеет", "tired" to "оседает", "asleep" to "дремлет крошкой",
        "eating" to "впитывает мыло", "game" to "плывёт по наклону", "loved" to "сердечком", "dizzy" to "кружится",
        "tilt" to "катится", "charging" to "наполняется светом", "grow" to "выдувается"),
) {
    /** [form] reshapes the film (1 round, more or less at each angle); [hr] the radius its glint sits by (default [r]). */
    private fun bubble(c: Cv, t: Long, cx: Float, cy: Float, r: Float, wob: Float, eyes: Int, b: Float = 1f, squash: Float = 1f, shine: Float = 1f,
                       form: ((Float) -> Float)? = null, hr: Float = r, look: Float = 0f) {
        // A reshaped film ([form], a pastime) needs more points round it; the round one keeps its 64, as it always drew.
        val n = if (form != null) max(64, (r * 10).roundToInt()) else 64
        for (k in 0 until n) {
            val a = k * 2 * PIF / n
            val rr = r * (form?.invoke(a) ?: 1f) + wob * sin(a * 3 + t / 220f) + wob * 0.5f * sin(a * 2 - t / 310f)
            val glow = max(0f, cos(a - t / 600f)).pow(6) * shine
            c.put(cx + cos(a) * rr / squash, cy + sin(a) * rr * squash, (0.3f + 0.7f * glow) * b)
        }
        if (hr > 0f) c.put(cx - hr * 0.45f, cy - hr * 0.5f, 0.9f * b)
        val ey = cy.roundToInt()
        val ex = cx + look
        when (eyes) {
            0 -> { c.put(ex - 2, cy, 0.5f * b); c.put(ex + 2, cy, 0.5f * b) }
            1 -> { c.put(ex - 2.5f, cy, 0.2f * b); c.put(ex - 1.5f, cy, 0.2f * b); c.put(ex + 1.5f, cy, 0.2f * b); c.put(ex + 2.5f, cy, 0.2f * b) }
            2 -> { c.put(ex - 2, cy - 0.6f, 0.6f * b); c.put(ex - 3, cy + 0.4f, 0.4f * b); c.put(ex + 2, cy - 0.6f, 0.6f * b); c.put(ex + 3, cy + 0.4f, 0.4f * b) }
            3 -> { c.put(ex - 2, cy + 1, 0.4f * b); c.put(ex + 2, cy + 1, 0.4f * b) }
        }
        if (ey < 0) Unit
    }

    private fun pop(c: Cv, q: Float, r: Float) {
        for (k in 0 until 18) { val a = k * 2 * PIF / 18; val d = r + q * 5; c.put(12 + cos(a) * d, 12 + sin(a) * d, 0.8f * (1 - q)) }
    }

    /** A regular [n]-gon round the bubble's centre, a corner at angle [rot]: its edge's distance at angle [a], 1 at the corners. */
    private fun gon(a: Float, n: Int, rot: Float): Float {
        val sec = 2 * PIF / n
        val d = Math.floorMod(((a - rot) * 10000).toInt(), (sec * 10000).toInt()) / 10000f - sec / 2
        return cos(sec / 2) / cos(d)
    }

    override val pastimes = listOf(
        DrawnPastime("shapes", 4800, "примеряет формы",
            "Плёнка вытягивается в треугольник, потом в квадрат, а потом пузырь снова круглеет и радостно дрожит."),
        DrawnPastime("sneeze", 4400, "чихает пузырьком",
            "Набирает воздуха, раздувается и дрожит, зажмурившись, чихает, и из него вылетает крошечный пузырёк, который улетает вверх."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val r = (3.5f + 6.5f * DrawnWorld.bubble) * s(st)
        // Its bob settles on the middle row (where its calm face blinks) and picks up again before the end.
        val ms = pastimes.first { it.id == col }.ms
        val cy = mix(12f + sin(ct / 700f), 12f, ease(seg(t, 480, 700)) - ease(seg(t, ms - 700, ms - 480)))
        val wob0 = 0.35f + DrawnWorld.wind
        val e = if (blink(ct)) 1 else 0
        when (col) {
            "shapes" -> {
                // Round, a triangle, a square, round again with a happy shiver; the face stays where it is.
                val tri = ease(seg(t, 900, 1300)); val sq = ease(seg(t, 2000, 2400)); val back = ease(seg(t, 3100, 3500))
                val m = tri * (1 - back)
                val wob = mix(wob0, 0.1f, ease(seg(t, 480, 900))) * (1 - back) + back * mix(0.7f, wob0, seg(t, 3500, 4300))
                val form = { a: Float -> mix(1f, mix(1.3f * gon(a, 3, -PIF / 2), 1.12f * gon(a, 4, PIF / 4), sq), m) }
                // The triangle's side would run through the glint: it goes while the film is three-cornered.
                bubble(c, ct, 12f, cy, r, wob, if (t in 3300L until 3600L) 1 else e, form = form, hr = if (m > 0.3f && sq < 0.5f) -1f else r)
            }
            "sneeze" -> {
                // A-a-a: it swells and shivers, eyes screwed up; choo: it snaps small and a tiny bubble shoots off and away.
                val inhale = ease(seg(t, 480, 1500))
                val rr = when { t < 1500 -> r + 1.2f * inhale; t < 1600 -> mix(r + 1.2f, r - 0.8f, seg(t, 1500, 1600)); else -> mix(r - 0.8f, r, ease(seg(t, 1600, 3200))) }
                val wob = if (t < 1500) wob0 + inhale * 0.5f else wob0
                val eyes = if (t in 900L until 1600L) 1 else e
                val look = t in 1900L until 3200L
                bubble(c, ct, 12f, cy, rr, wob, eyes, squash = if (t in 1500L until 1700L) 0.85f else 1f, hr = r, look = if (look) 1f else 0f)
                if (t in 1550L until 3100L) {
                    val p = seg(t, 1550, 3100)
                    val bx = mix(min(21f, 12f + r + 2f), 20f, p).roundToInt()
                    val by = (mix(12f, 5f, ease(p)) + sin(p * 12f) * 0.6f).roundToInt()
                    whole(c, listOf("o##", "#.#", "###"), bx - 1, by - 1, 0.45f)
                }
            }
        }
    }

    private var tKey = -1
    private var tSize = 0f
    private var tPop = -1L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val k = s(st)
        val size = DrawnWorld.bubble
        val r = (3.5f + 6.5f * size) * k
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> {
                val since = DrawnWorld.nowMs - DrawnWorld.poppedAt
                if (since in 0..500) pop(c, since / 500f, 10f * k) else bubble(c, t, 12f, 12f + sin(t / 700f), r, 0.35f + DrawnWorld.wind, e)
            }
            "happy" -> { val h = hop(t, 450f, 5f); bubble(c, t, 12f, 15f - h, r * 0.85f, if (h < 1f) 0.9f else 0.3f, 2, squash = if (h < 0.6f) 0.85f else 1f) }
            "habit" -> { val p = sin(seg(t, 0, 3200) * PIF); bubble(c, t, 12f - p * 4, 12f, r * (1 - p * 0.35f), 0.3f, e); if (p > 0.1f) bubble(c, t, 12f + p * 4, 12f, r * p * 0.65f, 0.3f, 0) }
            "signature" -> { val p = seg(t, 0, 6400); bubble(c, t * 3, 12f, 12f, r + sin(p * PIF) * 2, 0.5f, 2, shine = 2.5f); for (i in 0 until 6) { val a = t / 250f + i; c.put(12 + cos(a) * (r + 2.5f), 12 + sin(a) * (r + 2.5f), 0.6f) } }
            "hungry" -> { bubble(c, t, 12f, 13f, r * 0.7f, 0.2f, 0, b = 0.85f); thought(c, listOf("ooo", "o.o", "ooo"), t) }
            "sad" -> { rain(c, t); bubble(c, t, 12f, 14f, r * 0.85f, 0.15f, 3, b = 0.6f, shine = 0.2f) }
            "tired" -> bubble(c, t, 12f, 14f + seg(t, 0, 3000) * 2, r * 0.9f, 0.2f, if ((t / 700) % 2 == 0L) 1 else 0, b = 0.85f, squash = 0.8f)
            "asleep" -> { bubble(c, t, 12f, 15f, 3.5f * k, 0.15f, 1, b = 0.8f); zs(c, 16f, 9f, t) }
            "eating" -> { val y = mix(0f, 12f - r, seg(t, 0, 1200)); if (t < 1300) c.put(12f, y, 0.9f); bubble(c, t, 12f, 12f, r + (if (t > 1300) seg(t, 1300, 2300) * 1.5f else 0f), 0.4f, if (t > 1300) 2 else 0) }
            "game" -> bubble(c, t, 12 + tilt * 6, 12f + sin(t / 500f) * 2, r * 0.8f, 0.4f, 0)
            "loved" -> { bubble(c, t, 12f, 12f, r, 0.3f, 2, b = 1.15f + 0.2f * sin(t / 200f)); hearts(c, 12f, 12f - r - 1, t) }
            "dizzy" -> { bubble(c, t * 4, 12f + cos(t / 200f) * 2, 12f + sin(t / 200f) * 2, r * 0.85f, 0.8f, 1); stars(c, 12f, 3f, t) }
            "tilt" -> bubble(c, t, 12 + tilt * 5, 12f, r, 0.3f + abs(tilt) * 0.5f, e, squash = 1f - abs(tilt) * 0.15f)
            "charging" -> { val p = seg(t, 0, 3000); bubble(c, t, 12f, 12f, r, 0.3f, if (p > 0.9f) 2 else 0, b = 0.8f + 0.6f * p); c.disc(12f, 12f, (r - 1.5f) * p, 0.12f) }
            // Typing: it swells on each key and shivers, shining more as you type; too big and it pops and starts again;
            // after 40 s a rainbow whirl (its signature).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "signature", y.stageMs % 6400, st, tilt); return }
                if (y.key != tKey) { if (y.key >= 0 && tPop < 0) tSize += if (y.rush) 0.07f else 0.05f; tKey = y.key }
                if (y.paused) tSize = (tSize - y.dt / 8000f).coerceAtLeast(0f)
                if (tSize >= 1f && tPop < 0) tPop = y.now
                if (tPop >= 0) { val q = (y.now - tPop) / 500f; if (q >= 1f) { tPop = -1; tSize = 0f } else { pop(c, q, 10f * k); return } }
                bubble(c, t, 12f, 12f + sin(t / 700f), (3.5f + 6.5f * tSize) * k, 0.3f + y.hit(220) * 1.2f, if (y.rush) 2 else if (y.paused) e else 0, shine = 1f + 1.5f * y.grown()) }
            "grow" -> { c.line(12f, 24f, 12f, 18f, 0.35f); c.ring(12f, 17f, 1.2f, 0.4f); val p = seg(t, 600, 4400); if (t > 600) bubble(c, t, 12f, 17f - p * 5, 1.5f + p * (r - 1.5f), 0.3f, if (p > 0.95f) 2 else 0) }
        }
    }
}

// --- the capybara in a hot spring ------------------------------------------------------------------------------------------

private object Capybara : NewHero(
    "capybara", listOf("Pup", "Capybara", "Onsen Lord"), Rarity.RARE,
    "Капибара: невозмутимо сидит в горячем источнике с юдзу на голове; чем дольше телефон неподвижен, тем ей блаженнее",
    mapOf("calm" to "парится", "happy" to "юдзу подпрыгивает", "habit" to "птичка садится", "signature" to "дождь из юдзу",
        "hungry" to "мечтает об арбузе", "sad" to "уши поникли", "tired" to "погружается", "asleep" to "спит в воде",
        "eating" to "жуёт арбуз", "game" to "жонглирует юдзу", "loved" to "румянец в пару", "dizzy" to "кружится юдзу",
        "tilt" to "вода плещется", "charging" to "источник греется", "grow" to "заплывает"),
) {
    private val BODY_ROWS = listOf(
        "..##.....##..",
        ".#############",
        "###############",
        "###############",
        "###############",
        "###############",
        ".#############.",
    )

    private fun capy(c: Cv, st: Int, t: Long, eyes: Int, b: Float = 1f, sink: Int = 0, yuzu: Float = 0f, slosh: Float = 0f, steam: Int = 3) {
        body(c, st, eyes, b, sink, yuzu)
        pool(c, t, b, slosh, steam)
    }

    /** The capybara [dx] off centre, its yuzu on its head unless [yuzuOn] is false. */
    private fun body(c: Cv, st: Int, eyes: Int, b: Float = 1f, sink: Int = 0, yuzu: Float = 0f, dx: Int = 0, yuzuOn: Boolean = true) {
        val k = s(st)
        val rows = if (st == 0) BODY_ROWS.drop(1).map { it.substring(1, it.length - 1) } else BODY_ROWS
        val w = rows[0].length
        val left = 12 - w / 2 + dx
        val top = (16 - rows.size + 1 + sink + (1 - k) * 3).roundToInt()
        c.rows(rows, left, top, 0.3f * b)
        c.rows(listOf("+###+", "#####"), left + w - 4, top + 3, 0.36f * b)
        c.cut(left + w - 1, top + 3)
        val ey = top + 3
        when (eyes) {
            0 -> { c.cut(left + 4, ey); c.cut(left + 8, ey) }
            1 -> { c.set(left + 3, ey, 0.08f); c.set(left + 4, ey, 0.08f); c.set(left + 8, ey, 0.08f); c.set(left + 9, ey, 0.08f) }
            2 -> { c.cut(left + 4, ey); c.cut(left + 8, ey); c.cut(left + 3, ey + 1); c.cut(left + 9, ey + 1) }
            3 -> { c.cut(left + 4, ey + 1); c.cut(left + 8, ey + 1) }
        }
        if (yuzuOn) c.disc(left + 6f, top - 1.5f - yuzu, 1.3f * k, 0.6f * b + 0.3f)
    }

    /** The yuzu where its head would have it, [dx] off centre at [sink]. */
    private fun yuzuAt(st: Int, sink: Int, dx: Int): Pair<Float, Float> {
        val k = s(st)
        val rows = if (st == 0) BODY_ROWS.drop(1).map { it.substring(1, it.length - 1) } else BODY_ROWS
        val w = rows[0].length
        return (12 - w / 2 + dx + 6f) to ((16 - rows.size + 1 + sink + (1 - k) * 3).roundToInt() - 1.5f)
    }

    override val pastimes = listOf(
        DrawnPastime("dive", 5600, "ныряет",
            "Зажмуривается и уходит под воду с головой, юдзу остаётся плавать и отплывает в сторону; капибара выныривает прямо под ним, и юдзу снова на голове."),
        DrawnPastime("swim_lap", 5200, "проплывает круг",
            "Неспешно плывёт к одному краю источника, разворачивается, плывёт к другому и возвращается на место."),
    )

    /** The body under the water line hidden, turned to swim left when [left]. */
    private fun bodyIn(c: Cv, st: Int, eyes: Int, b: Float, sink: Int, dx: Int, yuzuOn: Boolean, left: Boolean = false) {
        val tmp = Cv()
        body(tmp, st, eyes, b, sink, 0f, 0, yuzuOn)
        for (y in 0..16) for (x in 0..24) { val v = tmp.get(x, y); if (v > 0f) c.put((if (left) 26 - x else x) + dx, y, v) }
    }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val bliss = (DrawnWorld.stillMs / 6000f).coerceIn(0f, 1f)
        val e = if (bliss > 0.5f || blink(ct, 4200)) 1 else 0
        val b = 1f + bliss * 0.15f
        val steam = 3 + (bliss * 4).toInt()
        when (col) {
            "dive" -> {
                // Under with its eyes shut, the yuzu left floating; it drifts off, and the capybara comes up right under it.
                val down = (9 * ease(seg(t, 900, 2000))).roundToInt()
                val up = 9 - (9 * ease(seg(t, 3200, 4200))).roundToInt()
                val dx = if (t < 3200) 0 else (3 * (1 - ease(seg(t, 4300, 5000)))).roundToInt()
                val sink = if (t < 3200) down else up
                val eyes = when { t in 900L until 4200L -> 1; t in 4300L until 5000L -> 2; else -> e }
                bodyIn(c, st, eyes, b, sink, dx, yuzuOn = false)
                val (hx, hy) = yuzuAt(st, sink, dx)
                val drift = if (t < 3200) (3 * ease(seg(t, 2000, 3200))).roundToInt() else 0
                c.disc(hx + drift, min(hy, 14.5f), 1.3f * s(st), 0.6f * b + 0.3f)
                pool(c, ct, b, steam = steam)
            }
            "swim_lap" -> {
                // Right to the edge, turn, across to the other edge, turn, and back to its place.
                val x = when {
                    t < 1600 -> 3f * ease(seg(t, 700, 1600))
                    t < 1800 -> 3f
                    t < 3400 -> mix(3f, -5f, ease(seg(t, 1800, 3400)))
                    t < 3600 -> -5f
                    else -> -5f * (1 - ease(seg(t, 3600, 4600)))
                }
                bodyIn(c, st, e, b, 0, x.roundToInt(), yuzuOn = true, left = t in 1700L until 3500L)
                pool(c, ct, b, steam = steam)
            }
        }
    }

    /** The hot spring's water and its steam. */
    private fun pool(c: Cv, t: Long, b: Float = 1f, slosh: Float = 0f, steam: Int = 3) {
        for (x in 1..23) { c.put(x.toFloat(), 16f + 0.6f * sin(x * 0.8f + t / 400f) + slosh * (x - 12) / 12f, 0.4f * b); for (y in 17..21) c.put(x, y, 0.12f * b) }
        for (i in 0 until steam) { val q = ((t + i * 520) % 2600) / 2600f; c.put(4f + i * 3.4f + sin(q * 5 + i) * 1.2f, 15f - q * 12, 0.3f * (1 - q)) }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val bliss = (DrawnWorld.stillMs / 6000f).coerceIn(0f, 1f)
        val e = if (bliss > 0.5f || blink(t, 4200)) 1 else 0
        when (col) {
            "calm" -> capy(c, st, t, e, steam = 3 + (bliss * 4).toInt(), yuzu = 0f, b = 1f + bliss * 0.15f)
            "happy" -> capy(c, st, t, 2, yuzu = hop(t, 400f, 3f))
            "habit" -> { capy(c, st, t, 1); val p = seg(t, 0, 1600); val y = mix(-2f, 4f, p); c.rows(listOf(".#.", "###", ".+."), 10, y.roundToInt() - 3, 0.45f); c.cut(11, y.roundToInt() - 2) }
            "signature" -> { capy(c, st, t, 1, steam = 6); for (i in 0 until 6) { val q = ((t + i * 530) % 3200) / 3200f; val x = 3f + i * 3.5f; val y = q * 18f; c.disc(x, min(y, 16f), 0.9f, 0.7f); if (y > 16f) c.put(x, 16f, 0.9f) } }
            "hungry" -> { capy(c, st, t, 0); thought(c, listOf("ooo", "#o#"), t) }
            "sad" -> { rain(c, t); capy(c, st, t, 3, b = 0.7f, steam = 1) }
            "tired" -> capy(c, st, t, if ((t / 800) % 2 == 0L) 1 else 0, sink = (seg(t, 0, 3000) * 2).toInt(), b = 0.85f)
            "asleep" -> { capy(c, st, t, 1, sink = 2, b = 0.8f, steam = 2); zs(c, 17f, 6f, t) }
            "eating" -> { capy(c, st, t, if ((t / 250) % 2 == 0L) 2 else 0); if (t < 2400) c.rows(listOf("#####", ".ooo."), 15, 11, 0.6f) }
            "game" -> { capy(c, st, t, 0, slosh = tilt * 2); for (k in 0..1) { val p = ((t + k * 800) % 1600) / 1600f; c.disc(12 + tilt * 5 + (k * 2 - 1) * 3, 6 - sin(p * PIF) * 5, 0.9f, 0.7f) } }
            "loved" -> { capy(c, st, t, 2, b = 1.1f, steam = 6); c.put(9, 13, 0.6f); c.put(15, 13, 0.6f); hearts(c, 12f, 6f, t) }
            "dizzy" -> { capy(c, st, t, 1); for (k in 0..2) { val a = t / 200f + k * 2.1f; c.disc(12 + cos(a) * 4, 6 + sin(a) * 1.5f, 0.8f, 0.7f) } }
            "tilt" -> capy(c, st, t, e, slosh = tilt * 4)
            "charging" -> { val p = seg(t, 0, 3000); capy(c, st, t, if (p > 0.6f) 1 else 0, steam = 2 + (p * 6).toInt(), b = 0.85f + 0.3f * p) }
            // Typing: the yuzu hops on each key; it sinks into bliss as you type (eyes shut, deeper, more steam), and
            // after 40 s it rains yuzu (its signature).
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                if (y.stage == 2) { draw(c, "signature", t, st, tilt); return }
                capy(c, st, t, if (y.rush) 2 else if (g > 0.5f || (y.paused && blink(t, 4200))) 1 else 0, sink = (g * 2).toInt(),
                    yuzu = if (y.rush) hop(t, 300f, 3f) else y.hit(240) * 2.5f, steam = 2 + (g * 5).toInt()) }
            "grow" -> { val p = seg(t, 0, 3200); posed(c, dx = mix(-14f, 0f, ease(p))) { capy(it, st, t, if (t > 3200) 2 else 0) } }
        }
    }
}

// --- the moon ---------------------------------------------------------------------------------------------------------------

private object Moon : NewHero(
    "moon", listOf("Sliver", "Moon", "Harvest Moon"), Rarity.MYTHIC,
    "Месяц: каждый день той же формы, что настоящая луна в небе; в полнолуние круглый и светится",
    mapOf("calm" to "сегодняшняя фаза", "happy" to "сияет", "habit" to "фазы за миг", "signature" to "звездопад",
        "hungry" to "мечтает о сыре", "sad" to "за тучами", "tired" to "тускнеет", "asleep" to "спит месяцем",
        "eating" to "ловит звезду", "game" to "ловит звёзды", "loved" to "ореол", "dizzy" to "кувыркается",
        "tilt" to "катится по небу", "charging" to "наполняется", "grow" to "выходит из тени"),
) {
    /** Tonight's phase: 0 new, 0.5 full (a synodic month from a known new moon). */
    fun phase(nowMs: Long = System.currentTimeMillis()): Float {
        val days = (nowMs - 947_182_440_000L) / 86_400_000.0
        return (((days % 29.530588) + 29.530588) % 29.530588 / 29.530588).toFloat()
    }

    override val pastimes = listOf(
        DrawnPastime("satellite", 5600, "играет со спутником",
            "Из-за спины выплывает маленький спутник и дважды облетает месяц: проходит перед ним тенью и прячется за ним, а месяц следит за ним глазами."),
        DrawnPastime("moonset", 5200, "прячется за горизонт",
            "Уходит вниз за край неба, выглядывает одними глазами, смотрит по сторонам и снова восходит, довольный."),
    )

    /** Its little satellite: two solar panels and a body with an aerial. */
    private val SAT = listOf("..#..", "##o##")

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val ph = phase()
        val e = if (blink(ct)) 1 else 0
        val b = if (abs(ph - 0.5f) < 0.04f) 1.3f else 1f
        when (col) {
            "satellite" -> {
                // Out from behind it, twice round (a shadow across its face in front, hidden behind), and back behind it.
                val th = -PIF / 2 + seg(t, 700, 4900) * 4 * PIF
                val sx = (12f + 10f * cos(th)).roundToInt(); val sy = (12f + 5f * sin(th)).roundToInt()
                val front = sin(th) > 0f
                sky(c, ct)
                val disc = Cv()
                moon(disc, st, ct, ph, if (t in 2600L until 3000L) 2 else e, b = b,
                    lx = if (t in 700L until 4900L) (sx - 12).coerceIn(-1, 1) else 0, ly = if (t in 700L until 4900L) (if (front) 1 else -1) else 0)
                for (i in 0 until 625) if (disc.v[i] > c.v[i]) c.v[i] = disc.v[i]
                // Lit where the sky (or the moon's dark side) is behind it, a shadow on the lit moon; its lit part laid only
                // whole, so a sliver at the moon's edge never shows as a crumb.
                val lit = Cv()
                if (t in 700L until 4900L) SAT.forEachIndexed { dy, row -> row.forEachIndexed { dx, ch ->
                    if (ch == '.') return@forEachIndexed
                    val x = sx - 2 + dx; val y = sy - 1 + dy
                    val on = disc.get(x, y)
                    when {
                        on <= 0f -> lit.put(x, y, if (ch == 'o') 1f else 0.5f)
                        !front -> Unit
                        on > 0.2f -> c.cut(x, y)
                        else -> lit.put(x, y, if (ch == 'o') 1f else 0.5f)
                    }
                } }
                layWhole(c, lit)
            }
            "moonset" -> {
                // Down behind the rim, a peek with just its eyes (a look left, a look right), and up again, pleased.
                val cy = when {
                    t < 1300 -> mix(12f, 33f, seg(t, 600, 1300).let { it * it })
                    t < 1800 -> 33f
                    t < 2400 -> mix(33f, 20f, ease(seg(t, 1800, 2400)))
                    t < 3400 -> 20f
                    else -> mix(20f, 12f, ease(seg(t, 3400, 4400)))
                }
                sky(c, ct)
                val tmp = Cv()
                val lx = when (t) { in 2500L until 2950L -> -1; in 2950L until 3400L -> 1; else -> 0 }
                moon(tmp, st, ct, ph, if (t in 3600L until 4600L) 2 else e, b = b, cy = cy.roundToInt().toFloat(), lx = lx)
                layWhole(c, tmp)
            }
        }
    }

    private fun moon(c: Cv, st: Int, t: Long, phase: Float, eyes: Int, b: Float = 1f, cx: Float = 12f, cy: Float = 12f, lx: Int = 0, ly: Int = 0) {
        val r = 7f * s(st)
        val k = cos(phase * 2 * PIF)
        for (y in (cy - r - 1).toInt()..(cy + r + 1).toInt()) for (x in (cx - r - 1).toInt()..(cx + r + 1).toInt()) {
            val dx = (x - cx) / r; val dy = (y - cy) / r
            if (dx * dx + dy * dy > 1f) continue
            val half = sqrt(1 - dy * dy)
            val lit = if (phase < 0.5f) dx > k * half else dx < -k * half
            c.put(x, y, if (lit) (0.4f + 0.06f * rnd(x, y)) * b else 0.06f * b)
        }
        val fx = (if (phase < 0.45f) cx + r * 0.4f else if (phase > 0.55f) cx - r * 0.4f else cx).roundToInt() + lx
        val ey = cy.roundToInt() + ly
        when (eyes) {
            0 -> { c.cut(fx - 2, ey); c.cut(fx + 1, ey) }
            1 -> { c.set(fx - 2, ey, 0.08f); c.set(fx + 1, ey, 0.08f) }
            2 -> { c.cut(fx - 2, ey); c.cut(fx + 1, ey); c.cut(fx - 3, ey + 1); c.cut(fx + 2, ey + 1) }
            3 -> { c.cut(fx - 2, ey + 1); c.cut(fx + 1, ey + 1) }
        }
    }

    private fun sky(c: Cv, t: Long, n: Int = 6) {
        for (i in 0 until n) { val x = 2 + (rnd(i, 1) * 21).toInt(); val y = 2 + (rnd(i, 2) * 8).toInt(); if (((t / 400) + i) % 3 != 0L) c.put(x, y, 0.35f) }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val ph = phase()
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> { sky(c, t); moon(c, st, t, ph, e, b = if (abs(ph - 0.5f) < 0.04f) 1.3f else 1f) }
            "happy" -> { moon(c, st, t, 0.5f, 2, b = 1.1f + 0.2f * sin(t / 150f)); sparkle(c, 12f, 12f, 9f, t) }
            "habit" -> moon(c, st, t, (t / 3200f) % 1f, 0)
            "signature" -> { moon(c, st, t, 0.5f, 2, b = 1.2f); for (i in 0 until 4) { val q = ((t + i * 900) % 1800) / 1800f; val x = 20f - q * 16 + i; val y = 2f + q * 8 + i; c.line(x, y, x + 2, y - 1, 0.7f * (1 - q)) } }
            "hungry" -> { moon(c, st, t, ph, 0); thought(c, listOf("ooo", "o.o"), t) }
            "sad" -> { moon(c, st, t, ph, 3, b = 0.7f); val cx = 6f + ((t % 3200) / 3200f) * 12; for (k in 0..4) c.put(cx + k, 10f + (k % 2), 0.25f) ; rain(c, t) }
            "tired" -> moon(c, st, t, ph, if ((t / 800) % 2 == 0L) 1 else 0, b = 0.7f + 0.2f * (1 - seg(t, 0, 3000)))
            "asleep" -> { moon(c, st, t, 0.18f, 1, b = 0.8f); zs(c, 17f, 6f, t) }
            "eating" -> { val p = seg(t, 0, 1400); if (t < 1500) c.put(mix(22f, 13f, p), mix(2f, 12f, p), 0.9f); moon(c, st, t, ph, if (t > 1500) 2 else 0, b = if (t in 1500..2400) 1.3f else 1f) }
            "game" -> { moon(c, st, t, 0.5f, 0, cx = 12 + tilt * 6, cy = 15f); for (k in 0..2) { val q = ((t + k * 700) % 2100) / 2100f; c.put(4f + k * 8, q * 12, 0.8f) } }
            "loved" -> { moon(c, st, t, 0.5f, 2, b = 1.15f); c.ring(12f, 12f, 7f * s(st) + 2f, 0.3f + 0.15f * sin(t / 200f)); hearts(c, 12f, 4f, t) }
            "dizzy" -> { posed(c, angle = t / 200f, py = 12f) { moon(it, st, t, 0.3f, 1) }; stars(c, 12f, 2f, t) }
            "tilt" -> posed(c, angle = tilt * 0.8f, dx = tilt * 4, py = 12f) { moon(it, st, t, ph, e) }
            "charging" -> moon(c, st, t, 0.5f * seg(t, 0, 3000), if (t > 2800) 2 else 0, b = 0.8f + 0.5f * seg(t, 0, 3000))
            // Typing: it waxes as you type, a thin sliver to full by 40 s, each key brightening it; then a halo and
            // falling stars (its signature).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "signature", t, st, tilt); c.ring(12f, 12f, 7f * s(st) + 2f, 0.25f + 0.15f * sin(t / 200f)); return }
                sky(c, t, 4); moon(c, st, t, 0.04f + 0.46f * y.grown(), if (y.rush) 2 else if (y.paused && blink(t)) 1 else 0, b = 1f + 0.5f * y.hit(220)) }
            "grow" -> { val p = seg(t, 0, 4400); moon(c, st, t, 0.5f, if (p > 0.95f) 2 else 1, b = p); c.disc(12f + p * 16, 12f, 7.5f * s(st), 0f); for (y in 0..24) for (x in 0..24) if (hypot(x - (12f + p * 16), y - 12f) < 7f * s(st)) c.set(x, y, 0f) }
        }
    }
}

// --- the tardigrade ---------------------------------------------------------------------------------------------------------

private object Tardigrade : NewHero(
    "tardigrade", listOf("Speck", "Tardigrade", "Water Bear"), Rarity.RARE,
    "Тихоходка: неубиваемая; на низком заряде сворачивается в бочонок, а на зарядке оживает",
    mapOf("calm" to "топает", "happy" to "машет лапками", "habit" to "валяется на спине", "signature" to "пережила космос",
        "hungry" to "мечтает о мхе", "sad" to "сжалась", "tired" to "лапки медленнее", "asleep" to "бочонок",
        "eating" to "жуёт мох", "game" to "топает по наклону", "loved" to "румянец", "dizzy" to "кувыркается",
        "tilt" to "скатывается", "charging" to "оживает", "grow" to "из яйца"),
) {
    /** [stretch] 0..1 draws it out long and low; [ground] false leaves the floor line out (when it is turned). */
    private fun bear(c: Cv, st: Int, t: Long, tun: Float, eyes: Int, b: Float = 1f, cx: Float = 12f, legs: Long = 250, up: Boolean = false,
                     stretch: Float = 0f, ground: Boolean = true, joined: Boolean = false) {
        val k = s(st)
        val w = (6.5f - 2.5f * tun) * k * (1 + 0.3f * stretch); val h = (3.6f - 0.6f * tun) * k * (1 - 0.2f * stretch)
        val cy = 14f
        for (y in (cy - h - 1).toInt()..(cy + h + 1).toInt()) for (x in (cx - w - 1).toInt()..(cx + w + 1).toInt()) {
            val e = (x - cx) * (x - cx) / (w * w) + (y - cy) * (y - cy) / (h * h)
            if (e <= 1f) c.put(x, y, (if (e > 0.6f) 0.42f else 0.3f) * (1 - 0.45f * tun) * b)
        }
        val gap = 2.3f * (1 + 0.3f * stretch)
        for (i in -2..2) if (abs(i * gap) < w - 1) for (y in (cy - h + 1).toInt()..(cy + h - 1).toInt()) c.put(cx + i * gap, y.toFloat(), 0.2f * (1 - tun) * b)
        if (tun < 0.6f) for (i in 0..3) {
            val lx = cx - w * 0.7f + i * w * 0.47f
            val lift = if (legs > 0 && ((t / legs) + i) % 2 == 0L) 1 else 0
            val ly = if (up) cy - h - 1 + lift else cy + h + 0.3f - lift
            c.put(lx, ly, 0.45f * b); c.put(lx + 0.8f, ly + if (up) -1 else 1, 0.6f * b)
            if (joined) c.put(lx, ly + if (up) 1 else -1, 0.45f * b)
        }
        c.ring(cx + w - 0.5f, cy, 0.9f, 0.5f * (1 - tun) * b, 0.5f)
        val ex = (cx + w - 3).roundToInt()
        when {
            tun > 0.5f || eyes == 1 -> c.set(ex, 13, 0.06f)
            eyes == 2 -> { c.cut(ex, 13); c.cut(ex - 1, 14); c.cut(ex + 1, 14) }
            eyes == 3 -> c.cut(ex, 14)
            else -> c.cut(ex, 13)
        }
        if (ground) for (x in 4..20) c.put(x, 20, 0.12f * b)
    }

    override val pastimes = listOf(
        DrawnPastime("stretch", 4400, "потягивается",
            "Останавливается, вытягивается во всю длину, расставив лапки, и жмурится от удовольствия; потом сжимается обратно, встряхивается и топает дальше."),
        DrawnPastime("stand_up", 4400, "встаёт столбиком",
            "Встаёт на задние лапки, как маленький медведь, подпрыгивает и болтает передними в воздухе; потом опускается и топает дальше."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        // Curled into its barrel on a low battery, it uncurls for it first and curls up again after.
        val ms = pastimes.first { it.id == col }.ms
        val tun = if (DrawnWorld.battery < 0.2f && !DrawnWorld.charging) 1f - ease(seg(t, 480, 900)) + ease(seg(t, ms - 900, ms - 480)) else 0f
        val ct = calmClock(t)
        val e = if (blink(ct)) 1 else 0
        when (col) {
            "stretch" -> {
                // Stops, draws itself out long with its legs spread, eyes shut; back, a shake, and on it walks.
                val s = ease(seg(t, 800, 1800)) - ease(seg(t, 2600, 3300))
                val shake = if (t in 3300L until 3700L) (if ((t / 100) % 2 == 0L) 1f else -1f) else 0f
                val eyes = when (t) { in 800L until 2600L -> 1; in 2600L until 3700L -> 2; else -> e }
                bear(c, st, ct, tun, eyes, cx = 12f + shake, legs = if (t < 800 || t >= 3700) 250 else 0, stretch = s)
            }
            "stand_up" -> {
                // Up on its hind legs (a quarter turn, its head to the top), bouncing, the front legs pedalling; down again.
                val q = when (t) { in 800L until 960L, in 3200L until 3360L -> 0.5f; in 960L until 3200L -> 1f; else -> 0f }
                val eyes = if (q == 1f) 2 else e
                val bounce = if (t in 1200L until 3000L && (t - 1200) % 600 < 300) 1f else 0f
                posed(c, angle = -PIF / 2 * q, dy = -2f * q - bounce, px = 12f, py = 14f) {
                    bear(it, st, ct, tun, eyes, legs = if (q == 1f) 110 else if (q == 0f) 250 else 0, ground = false, joined = q > 0f)
                }
                for (x in 4..20) c.put(x, 20, 0.12f)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val low = DrawnWorld.battery < 0.2f && !DrawnWorld.charging
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> { bear(c, st, t, if (low) 1f else 0f, e); if (low) zs(c, 17f, 8f, t) }
            "happy" -> posed(c, dy = -hop(t, 400f, 2f)) { bear(it, st, t, 0f, 2, legs = 120) }
            "habit" -> posed(c, angle = PIF * ease(seg(t, 0, 800)) * (1 - ease(seg(t, 2400, 3200))), py = 14f) { bear(it, st, t, 0f, 2, legs = 100) }
            "signature" -> {
                val p = seg(t, 0, 6400)
                for (i in 0 until 8) { val x = (i * 3 + t / 80) % 25; c.put(x.toFloat(), (rnd(i, 4) * 24), 0.4f) }
                val tun = if (p < 0.2f) p * 5 else if (p < 0.75f) 1f else 1 - (p - 0.75f) * 4
                posed(c, angle = if (tun > 0.9f) t / 300f else 0f, py = 14f) { bear(it, st, t, tun, if (p > 0.9f) 2 else 0) }
            }
            "hungry" -> { bear(c, st, t, 0f, 0, legs = 500); thought(c, listOf("#o#", "ooo"), t) }
            "sad" -> { rain(c, t); bear(c, st, t, 0.4f, 3, b = 0.7f, legs = 0) }
            "tired" -> bear(c, st, t, seg(t, 0, 3000) * 0.4f, if ((t / 700) % 2 == 0L) 1 else 0, b = 0.85f, legs = 600)
            "asleep" -> { bear(c, st, t, 1f, 1, b = 0.85f); zs(c, 17f, 8f, t) }
            "eating" -> { bear(c, st, t, 0f, if ((t / 250) % 2 == 0L) 2 else 0, legs = 0); if (t < 2400) for (i in 0..2) c.put(12f + 6f * s(st) + i * 0.6f, 15f - i, 0.55f) }
            "game" -> bear(c, st, t, 0f, 0, cx = 12 + tilt * 5, legs = 150)
            "loved" -> { bear(c, st, t, 0f, 2, b = 1.1f + 0.15f * sin(t / 200f)); c.put(12f + 4f * s(st), 15f, 0.6f); hearts(c, 12f, 9f, t) }
            "dizzy" -> { posed(c, angle = t / 180f, py = 14f) { bear(it, st, t, 0.8f, 1) }; stars(c, 12f, 7f, t) }
            "tilt" -> bear(c, st, t, 0f, e, cx = 12 + tilt * 5, legs = 120)
            "charging" -> { val q = max(0f, 1 - seg(t, 0, 2500)); bear(c, st, t, q, if (q < 0.1f) 2 else 1, legs = if (q < 0.5f) 200 else 0); for (k in 0..5) { val a = k * 2 * PIF / 6 + t / 300f; c.put(12 + cos(a) * 8, 14 + sin(a) * 5, 0.4f * q + 0.1f) } }
            // Typing: a step on each key, the key under it lighting; it walks the keyboard end to end as you type, and
            // after 40 s rolls on its back, waving (its habit).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "habit", 800L + y.stageMs % 1600, st, tilt); return }
                val cx = 7f + 10f * y.inAct
                for (i in 0 until 6) { val kx = 2 + i * 4; val lit = y.hit(180) > 0f && abs(kx + 1.5f - cx) < 2.5f; for (x in kx..kx + 2) { c.put(x, 21, if (lit) 1f else 0.3f); c.put(x, 22, if (lit) 0.5f else 0.12f) } }
                if (y.rush) bear(c, st, t, 0f, 2, cx = cx, legs = 90) else bear(c, st, y.key.coerceAtLeast(0) * 250L, 0f, if (y.paused && blink(t)) 1 else 0, cx = cx, legs = if (y.paused) 0 else 250) }
            "grow" -> { if (t < 2400) { for (y in 11..19) for (x in 8..16) if ((x - 12f) * (x - 12f) / 9f + (y - 15f) * (y - 15f) / 12.25f <= 1f) c.put(x, y, 0.3f); if (t > 1600) c.line(10f, 13f, 14f, 17f, 0f) } else bear(c, st, t, 0f, if (t > 4000) 2 else 0) }
        }
    }
}

// --- the cassette -----------------------------------------------------------------------------------------------------------

private object Cassette : NewHero(
    "cassette", listOf("Single", "Cassette", "Mixtape"), Rarity.RARE,
    "Кассетка: катушки вместо глаз; крутит их в такт музыке, что играет рядом",
    mapOf("calm" to "тихо крутит", "happy" to "пританцовывает", "habit" to "перематывает", "signature" to "плёнка рисует сердце",
        "hungry" to "плёнка кончилась", "sad" to "плёнку зажевало", "tired" to "крутит медленно", "asleep" to "пауза",
        "eating" to "карандашом подматывает", "game" to "крутит по наклону", "loved" to "ноты-сердца", "dizzy" to "закрутилась",
        "tilt" to "клонится", "charging" to "эквалайзер", "grow" to "плёнка наматывается"),
) {
    private val SHELL = listOf(
        "#################",
        "#...............#",
        "#..###.....###..#",
        "#.#...#...#...#.#",
        "#.#...#####...#.#",
        "#.#...#...#...#.#",
        "#..###.....###..#",
        "#...............#",
        "##..#########..##",
        ".###############.",
    )

    override val pastimes = listOf(
        DrawnPastime("jump_rope", 5200, "прыгает через плёнку",
            "Вытягивает снизу петлю плёнки и крутит её как скакалку, подпрыгивая каждый раз, когда она проходит внизу; потом сматывает плёнку обратно."),
        DrawnPastime("tease", 4000, "дразнится",
            "Высовывает язык из плёнки, подмигивает катушкой и болтает языком туда-сюда; потом с треском втягивает его обратно."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val e = if (blink(ct)) 1 else 0
        val calmSpin = ct / (900f - 700f * DrawnWorld.loud)
        val top = 7 + (if (st == 0) 1 else 0)
        val bottom = top + (if (st == 0) 7 else 9)
        when (col) {
            "jump_rope" -> {
                // A loop of tape pulled out of the slot, swung round like a skipping rope (a hop as it passes below), wound back.
                val spinning = t in 1000L until 4000L
                val phi = if (spinning) (t - 1000) / 750f * 2 * PIF else 0f
                val hop = if (spinning && cos(phi) > 0.6f) -2 else 0
                val half = when { t < 1000 -> mix(1f, 7f, seg(t, 480, 1000)); t < 4000 -> 7f; else -> mix(7f, 0f, seg(t, 4000, 4600)) }
                val spin = when { spinning -> t / 160f; t >= 4000 -> -t / 40f; else -> calmSpin }
                tape(c, st, ct, spin, if (spinning) 2 else e, dy = hop)
                if (half >= 1f) {
                    val by = bottom + hop
                    val reach = if (cos(phi) >= 0f) min(3f, half) else 13f
                    var px = 0f; var py = 0f
                    for (i in 0..56) {
                        val x = 12f - half + 2 * half * i / 56f
                        val s = 1 - ((x - 12f) / half).let { it * it }
                        val y = by + reach * cos(phi) * s
                        if (i > 0) {
                            // Behind the shell it is hidden; only what shows below and above it is drawn.
                            val n = 4
                            for (j in 0..n) {
                                val qx = mix(px, x, j / n.toFloat()); val qy = mix(py, y, j / n.toFloat())
                                val rx = qx.roundToInt(); val ry = qy.roundToInt()
                                if (ry > by || ry < top + hop) c.put(rx, ry, 0.45f)
                            }
                        }
                        px = x; py = y
                    }
                }
            }
            "tease" -> {
                // A tongue of tape out of the slot, a wink, the tongue waggling; slurped back in with the reels whirring.
                val len = (4 * (seg(t, 480, 800) - seg(t, 2800, 3120))).roundToInt()
                val wag = if (t in 800L until 2800L) (if ((t / 160) % 2 == 0L) -1 else 1) else 0
                val spin = if (t in 2800L until 3200L) -t / 40f else calmSpin
                tape(c, st, ct, spin, e, wink = if (t in 900L until 2700L) 1 else -1)
                for (j in 1..len) {
                    val x0 = 12 + (wag * j / 4f).roundToInt()
                    if (j < len) for (x in x0 - 1..x0 + 1) c.put(x, bottom + j, 0.4f) else c.put(x0, bottom + j, 0.4f)
                }
            }
        }
    }

    /** [wink] shuts one reel (0 left, 1 right). */
    private fun tape(c: Cv, st: Int, t: Long, spin: Float, eyes: Int, b: Float = 1f, dy: Int = 0, blur: Boolean = false, wink: Int = -1) {
        val rows = if (st == 0) SHELL.drop(1).dropLast(1) else SHELL
        val top = 7 + dy + (if (st == 0) 1 else 0)
        c.rows(rows, 4, top, 0.3f * b)
        val cy = top + if (st == 0) 3f else 4f
        for ((cx, side) in listOf(8f to 0, 16f to 1)) {
            when {
                eyes == 1 || side == wink -> { c.put(cx - 1, cy, 0.2f * b); c.put(cx + 1, cy, 0.2f * b) }
                blur -> { c.ring(cx, cy, 1.5f, 0.35f * b, 0.7f); val a = spin * 3 + side; c.put(cx + cos(a) * 1.5f, cy + sin(a) * 1.5f, 1f * b) }
                else -> for (k in 0..2) { val a = spin + k * 2 * PIF / 3 + side; c.put(cx + cos(a) * 1.4f, cy + sin(a) * 1.4f, 0.9f * b) }
            }
            if (eyes != 1) c.cut(cx, cy)
        }
        if (eyes == 2) { c.put(11f, cy + 3, 0.6f * b); c.put(12f, cy + 4, 0.6f * b); c.put(13f, cy + 3, 0.6f * b) }
        if (eyes == 3) { c.put(11f, cy + 4, 0.5f * b); c.put(12f, cy + 3, 0.5f * b); c.put(13f, cy + 4, 0.5f * b) }
    }

    private fun notes(c: Cv, t: Long, b: Float = 0.5f) {
        for (k in 0..1) { val q = ((t + k * 900) % 1800) / 1800f; c.rows(listOf(".o", ".o", "oo"), (if (k == 0) 3f else 20f).roundToInt(), (5 - q * 5).roundToInt(), b * (1 - q) + 0.1f) }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val music = DrawnWorld.loud
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> { tape(c, st, t, t / (900f - 700f * music), e, dy = if (music > 0.3f && (t % 500) < 150) 1 else 0); if (music > 0.3f) notes(c, t) }
            "happy" -> { tape(c, st, t, t / 160f, 2, dy = if ((t % 500) < 150) 1 else 0); notes(c, t) }
            "habit" -> tape(c, st, t, -t / 40f, 0, blur = true)
            "signature" -> { tape(c, st, t, t / 80f, 2); val p = seg(t, 600, 5000); val n = (p * 24).toInt(); for (i in 0 until n) { val a = i / 24f * 2 * PIF; val hx = 16 * sin(a).pow(3); val hy = -(13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)); c.put(12 + hx * 0.4f, 4f + hy * 0.3f, 0.6f) } }
            "hungry" -> { tape(c, st, t, 0f, 0); thought(c, listOf("o", "o", "o"), t) }
            "sad" -> { rain(c, t); tape(c, st, t, 0f, 3, b = 0.7f); for (i in 0..6) c.put(10f + i, 18f + sin(i + t / 200f), 0.4f) }
            "tired" -> tape(c, st, t, t / 2400f, if ((t / 800) % 2 == 0L) 1 else 0, b = 0.85f)
            "asleep" -> { tape(c, st, t, 0f, 1, b = 0.8f); zs(c, 18f, 4f, t) }
            "eating" -> { tape(c, st, t, t / 200f, if (t > 1600) 2 else 0); val p = seg(t, 0, 1600); c.line(mix(22f, 17f, p), 4f, mix(22f, 17f, p), 10f, 0.45f) }
            "game" -> tape(c, st, t, t * tilt / 100f, 0, dy = (tilt * 2).roundToInt())
            "loved" -> { tape(c, st, t, t / 300f, 2, b = 1.1f + 0.15f * sin(t / 200f)); hearts(c, 12f, 5f, t) }
            "dizzy" -> { posed(c, angle = sin(t / 160f) * 0.5f) { tape(it, st, t, t / 50f, 1) }; stars(c, 12f, 3f, t) }
            "tilt" -> posed(c, angle = tilt * 0.4f) { tape(it, st, t, t / 600f, e) }
            "charging" -> { tape(c, st, t, t / 400f, 0, b = 0.9f); for (i in 0..6) { val h = ((sin(t / 150f + i * 1.3f) + 1) * 2).roundToInt() + 1; for (y in 0 until h) c.put(6 + i * 2, 21 - y, 0.5f) } }
            // Typing: the reels click a step on each key; the tape winds from the left reel onto the right as you type,
            // and after 40 s the tape draws a heart (its signature).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "signature", 600L + y.stageMs % 4400, st, tilt); return }
                val g = y.grown(); val top = 7 + (if (st == 0) 1 else 0); val cy = top + if (st == 0) 3f else 4f
                c.disc(8f, cy, mix(3.2f, 1.6f, g), 0.16f); c.disc(16f, cy, mix(1.6f, 3.2f, g), 0.16f)
                tape(c, st, t, y.key.coerceAtLeast(0) * 0.8f + (if (y.rush) t / 120f else 0f), if (y.rush) 2 else if (y.paused && blink(t)) 1 else 0, dy = if (y.hit(120) > 0f) 1 else 0)
                if (y.rush) notes(c, t) }
            "grow" -> { val p = seg(t, 0, 4000); val rows = (p * 10).toInt(); val tmp = Cv(); tape(tmp, st, t, t / 100f, if (p > 0.95f) 2 else 0); for (y in 0..24) for (x in 0..24) if (y < 7 + rows) c.put(x, y, tmp.get(x, y)) }
        }
    }
}

// --- the ferrofluid ---------------------------------------------------------------------------------------------------------

private object Ferro : NewHero(
    "ferro", listOf("Drop", "Ferrofluid", "Magnetar"), Rarity.LEGENDARY,
    "Феррожидкость: капля жидкого металла; шипы смотрят на север, а к магниту встают дыбом",
    mapOf("calm" to "шипы на север", "happy" to "шипы пульсируют", "habit" to "становится кубиком", "signature" to "корона из шипов",
        "hungry" to "мечтает о магните", "sad" to "растеклась", "tired" to "оседает", "asleep" to "гладкая лужица",
        "eating" to "втягивает опилки", "game" to "тянется к магниту", "loved" to "капля-сердце", "dizzy" to "закрутилась",
        "tilt" to "перетекает", "charging" to "шипы светятся", "grow" to "капля падает"),
) {
    override val pastimes = listOf(
        DrawnPastime("tower", 5200, "строит ёлочку",
            "Шипы прячутся, и капля вытягивается вверх в конус с ярусами шипов, как ферро-скульптура на магните; постояв, оплывает обратно."),
        DrawnPastime("droplet", 4800, "отпускает капельку",
            "От бока отделяется маленькая капелька, катится к краю, подпрыгивает и катится назад, сливаясь с каплей обратно."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ct = calmClock(t)
        val north = DrawnWorld.magnetAngle
        val pull = 0.35f + DrawnWorld.magnet * 0.65f
        val e = if (blink(ct)) 1 else 0
        val k = s(st)
        when (col) {
            "tower" -> {
                // Spikes down, the drop rises into a cone ringed with tiers of spikes, holds, and slumps back.
                val p = ease(seg(t, 900, 2200)) - ease(seg(t, 3400, 4400))
                val sp = 1f - 0.75f * (ease(seg(t, 480, 900)) - ease(seg(t, 4400, 4720)))
                if (p > 0f) {
                    val apex = 15f - 10f * k * p
                    val base = 4.6f * k
                    for (y in apex.roundToInt()..19) {
                        val hw = base * (y - apex) / (19f - apex)
                        for (x in (12 - hw - 1).toInt()..(12 + hw + 1).toInt()) {
                            val d = abs(x - 12f)
                            if (d <= hw + 0.3f) c.put(x, y, if (d > hw - 0.8f) 0.5f else 0.32f)
                        }
                    }
                    var j = 0
                    var ty = apex + 2.5f
                    while (ty < 14f) {
                        val hw = base * (ty - apex) / (19f - apex)
                        val len = 2f * p + (if (t in 2200L until 3400L) 0.7f * sin(t / 150f + j) else 0f)
                        for (sgn in intArrayOf(-1, 1)) c.line(12f + sgn * hw, ty, 12f + sgn * (hw + len * 0.75f), ty - len * 0.75f, 0.55f)
                        ty += 3f; j++
                    }
                    c.line(12f, apex, 12f, apex - 1.6f * p, 0.6f)
                }
                drop(c, st, ct, north, pull, if (t in 2200L until 3400L) 2 else e, spikes = sp)
            }
            "droplet" -> {
                // A bud on its side pinches off, rolls to the edge, hops, rolls back and merges again.
                var bx: Float; var by = 18f; var br = 1.6f
                when {
                    t < 900 -> { bx = 16f; br = 1.6f * seg(t, 480, 900) }
                    t < 1300 -> bx = mix(16f, 18.5f, seg(t, 900, 1300))
                    t < 2000 -> bx = mix(18.5f, 20f, ease(seg(t, 1300, 2000)))
                    t < 2400 -> { bx = 20f; by = 18f - sin(seg(t, 2000, 2400) * PIF) * 2.5f }
                    t < 3300 -> bx = mix(20f, 16f, ease(seg(t, 2400, 3300)))
                    else -> { bx = mix(16f, 15f, seg(t, 3300, 3700)); br = 1.6f * (1 - seg(t, 3300, 3700)) }
                }
                val eyes = if (t in 3300L until 4100L) 2 else e
                drop(c, st, ct, north, pull, eyes, eyeDx = if (t in 900L until 3300L) 1 else 0)
                if (br > 0.3f) {
                    val x = bx.roundToInt().toFloat(); val y = by.roundToInt().toFloat()
                    if (br < 1f) c.put(x, y, 0.5f)
                    else { c.disc(x, y, br, 0.45f); c.put(x - 1, y - 1, 0.9f); c.put(x, y - 2, 0.5f) }
                }
            }
        }
    }

    /** [spikes] scales the spikes; [eyeDx] turns the eyes. */
    private fun drop(c: Cv, st: Int, t: Long, toward: Float, pull: Float, eyes: Int, b: Float = 1f, burst: Float = 0f, flat: Float = 0f, cx: Float = 12f,
                     spikes: Float = 1f, eyeDx: Int = 0) {
        val k = s(st)
        val cy = 15f + flat * 2
        val r = (4.2f + 0.3f * sin(t / 500f)) * k
        for (y in 4..20) for (x in 2..22) {
            val a = atan2(y - cy, x - cx)
            val d = hypot((x - cx) * (1 - flat * 0.4f), (y - cy) * (1 + flat))
            val stretch = pull * max(0f, cos(a - toward)).pow(3) * 2.5f
            val edge = r + 0.35f * sin(a * 7 + t / 180f) + stretch + burst * 0.8f
            if (y > 19) continue
            if (d <= edge) c.put(x, y, (if (d > edge - 1.1f) 0.5f else 0.32f) * b)
        }
        if (flat < 0.7f) for (i in 0 until 9) {
            val a = -PIF + (i + 0.5f) * PIF / 9
            val near = max(0f, cos(a - toward)).pow(4)
            val len = (1.2f + 4.5f * near * pull + 3f * burst + 0.4f * sin(t / 300f + i)) * k * (1 - flat) * spikes
            val bx = cx + cos(a) * (r - 0.5f); val by = cy + sin(a) * (r - 0.5f)
            val n = (len * 2).toInt()
            for (j in 0..n) { val q = j / (len * 2).coerceAtLeast(0.1f); c.put(bx + cos(a) * len * q, by + sin(a) * len * q, (0.55f * (1 - q) + 0.25f) * b) }
        }
        c.put(cx - 2, cy - 2, 1f * b); c.put(cx - 1, cy - 3, 0.7f * b)
        val ey = (cy + 1).roundToInt()
        val ex = cx + eyeDx
        when (eyes) {
            0 -> { c.cut(ex - 1, ey.toFloat()); c.cut(ex + 2, ey.toFloat()) }
            1 -> { c.set((ex - 1).roundToInt(), ey, 0.08f); c.set((ex + 2).roundToInt(), ey, 0.08f) }
            2 -> { c.cut(ex - 1, ey.toFloat()); c.cut(ex + 2, ey.toFloat()); c.cut(ex - 2, ey + 1f); c.cut(ex + 3, ey + 1f) }
            3 -> { c.cut(ex - 1, ey + 1f); c.cut(ex + 2, ey + 1f) }
        }
        for (x in 5..19) c.put(x, 20, 0.12f * b)
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (isPastime(col)) { if (!calmEdge(c, col, t, st)) pastime(c, col, t, st); return }
        val north = DrawnWorld.magnetAngle
        val pull = 0.35f + DrawnWorld.magnet * 0.65f
        val e = if (blink(t)) 1 else 0
        when (col) {
            "calm" -> drop(c, st, t, north, pull, e)
            "happy" -> { val beat = (t % 800) / 800f; drop(c, st, t, north, 0.3f, 2, burst = if (beat < 0.25f) beat * 4 else max(0f, 1 - (beat - 0.25f) * 2)) }
            "habit" -> { val p = sin(seg(t, 0, 3200) * PIF); drop(c, st, t, north, 0.3f * (1 - p), 0, flat = 0f); if (p > 0.3f) for (y in 11..18) for (x in 8..16) if (x == 8 || x == 16 || y == 11 || y == 18) c.put(x, y, 0.5f * p) }
            "signature" -> { drop(c, st, t, north, 0.3f, 2, burst = 0.6f); for (i in 0 until 8) { val a = i * PIF / 4 + t / 400f; c.line(12 + cos(a) * 6, 15 + sin(a) * 6, 12 + cos(a) * 9, 15 + sin(a) * 9, 0.6f) } }
            "hungry" -> { drop(c, st, t, north, 0.2f, 0); thought(c, listOf("o.o", "###"), t) }
            "sad" -> { rain(c, t); drop(c, st, t, north, 0.1f, 3, b = 0.7f, flat = 0.6f) }
            "tired" -> drop(c, st, t, north, 0.2f, if ((t / 800) % 2 == 0L) 1 else 0, b = 0.85f, flat = seg(t, 0, 3000) * 0.5f)
            "asleep" -> { drop(c, st, t, north, 0f, 1, b = 0.8f, flat = 0.8f); zs(c, 17f, 9f, t) }
            "eating" -> { for (i in 0 until 6) { val p = seg(t, i * 150L, 1500L + i * 150L); val a = i * 1.05f; c.put(12 + cos(a) * mix(10f, 3f, p), 15 + sin(a) * mix(8f, 2f, p), if (p < 1f) 0.7f else 0f) }; drop(c, st, t, north, 0.4f, if (t > 1600) 2 else 0) }
            "game" -> { val a = -PIF / 2 + tilt * 1.2f; drop(c, st, t, a, 1f, 0); c.rows(listOf("o.o", "###"), (12 + cos(a) * 10).roundToInt() - 1, (15 + sin(a) * 10).roundToInt() - 1, 0.5f) }
            "loved" -> { drop(c, st, t, north, 0.3f, 2, b = 1.15f + 0.2f * sin(t / 200f)); hearts(c, 12f, 6f, t) }
            "dizzy" -> { drop(c, st, t, t / 150f, 0.9f, 1); stars(c, 12f, 5f, t) }
            "tilt" -> drop(c, st, t, north, 0.3f, e, flat = abs(tilt) * 0.4f, cx = 12 + tilt * 5)
            "charging" -> { val p = seg(t, 0, 3000); drop(c, st, t, north, 0.3f, if (p > 0.9f) 2 else 0, burst = p * 0.7f, b = 0.8f + 0.6f * p) }
            // Typing: each key a magnet flashes at a new spot round the rim and the drop lunges at it, its spikes
            // stretching out in a spear; the more you type the wilder it gets, and after 40 s a spinning crown of spikes.
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { drop(c, st, t, t / 200f, 1f, 2, burst = 0.6f + 0.3f * sin(t / 120f)); for (i in 0 until 8) { val a = i * PIF / 4 + t / 300f; c.line(12 + cos(a) * 7, 15 + sin(a) * 7, 12 + cos(a) * 10, 15 + sin(a) * 10, 0.7f) }; return }
                val ang = if (y.key < 0) north else (y.key * 2.39996f) % (2 * PIF) - PIF
                val hit = y.hit(450)
                drop(c, st, t, ang, 0.3f + hit * (1.2f + y.grown()), if (y.rush) 2 else if (y.paused && blink(t)) 1 else 0, burst = 0.25f * y.grown())
                if (hit > 0f) { val mx = 12 + cos(ang) * 10.5f; val my = 15 + sin(ang) * 9f; c.rows(listOf("o.o", "###"), mx.roundToInt() - 1, my.roundToInt() - 1, 0.5f + 0.5f * hit) } }
            "grow" -> { val p = seg(t, 0, 2400); if (p < 1f) c.disc(12f, mix(-2f, 13f, p * p), 1.5f, 0.6f) else drop(c, st, t, north, seg(t, 2400, 4800) * 0.35f, if (t > 4800) 2 else 0, flat = 1 - seg(t, 2400, 3600)) }
        }
    }
}

