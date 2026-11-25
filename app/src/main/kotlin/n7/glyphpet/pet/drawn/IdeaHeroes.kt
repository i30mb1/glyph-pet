package n7.glyphpet.pet.drawn

import n7.glyphpet.pet.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The drawn heroes that come in the daily egg like any other: each draws every state its own way. */
object IdeaHeroes {
    val all: List<Anomaly> = listOf(PaperBoat, Pebble, Sprout, Spider, Astronaut, Kodama, Kraken, Glitch, Shadow)
}

private abstract class EggHero(kind: String, names: List<String>, private val tier: Rarity, idea: String, tags: Map<String, String>) :
    Anomaly(kind, names, "", idea, tags) {
    override val rarity get() = tier
}

private val PI_F = PI.toFloat()
private fun hop(t: Long, period: Float, h: Float) = abs(sin(t / period * PI_F)) * h
private fun blinkAt(t: Long, every: Long = 3200, at: Long = 2200) = t % every in at..at + 160
private fun Cv.spr(rows: List<String>, cx: Float, bottom: Float, b: Float = BODY) =
    rows(rows, (cx - rows[0].length / 2f).roundToInt(), (bottom - rows.size + 1).roundToInt(), b)
private fun Cv.oval(cx: Float, cy: Float, rx: Float, ry: Float, b: Float, rim: Float = b) {
    for (y in (cy - ry).toInt() - 1..(cy + ry).toInt() + 1) for (x in (cx - rx).toInt() - 1..(cx + rx).toInt() + 1) {
        val e = (x - cx) * (x - cx) / (rx * rx) + (y - cy) * (y - cy) / (ry * ry)
        if (e <= 1f) put(x, y, if (e > 0.7f) rim else b)
    }
}
/** eyes: 0 open (dark holes), 1 closed (dim), 2 lit, 3 none. */
private fun Cv.eyes(x1: Float, x2: Float, y: Float, mode: Int) {
    when (mode) {
        0 -> { cut(x1, y); cut(x2, y) }
        1 -> { set(x1.roundToInt(), y.roundToInt(), 0.1f); set(x2.roundToInt(), y.roundToInt(), 0.1f) }
        2 -> { set(x1.roundToInt(), y.roundToInt(), 1f); set(x2.roundToInt(), y.roundToInt(), 1f) }
    }
}
private fun rain(c: Cv, t: Long, n: Int = 8, b: Float = 0.35f) {
    for (i in 0 until n) { val ph = ((t + i * 377) % 900) / 900f; c.put(1f + rnd(i, 7) * 22, ph * 24, b) }
}
private fun zs(c: Cv, x: Float, y: Float, t: Long) {
    val ph = (t % 2000) / 2000f
    c.rows(listOf("ooo", ".o.", "ooo"), (x + ph * 2).roundToInt(), (y - ph * 5).roundToInt(), 0.3f * (1 - ph) + 0.05f)
}

// --- pastimes: what every hero's share ----------------------------------------------------------------------------------

private const val PEDGE = Drawn.PASTIME_EDGE
/** In a pastime: the calm clip's own clock, looped as the calm clip is, at pastime time [t]. */
private fun Anomaly.calmClk(t: Long): Long = Math.floorMod(calmT(t), Drawn.length("calm"))
private fun Anomaly.pastimeMs(col: String): Long = pastimes.first { it.id == col }.ms
/** A pastime's first and last [PEDGE] ms are the calm hero itself (the engine dissolves there); true when it drew that. */
private fun Anomaly.calmEdge(c: Cv, t: Long, st: Int, ms: Long): Boolean {
    if (t < PEDGE || t >= ms - PEDGE) { calmNow(c, t, st); return true }
    return false
}
/** How far out of its calm pose: 0 -> 1 over [ramp] ms after the first edge, back to 0 over the [ramp] ms before the last. */
private fun outOf(t: Long, ms: Long, ramp: Long = 600L) = min(ease(seg(t, PEDGE, PEDGE + ramp)), 1f - ease(seg(t, ms - PEDGE - ramp, ms - PEDGE)))
/** 0 -> 1 -> 0 over [from]..[to]. */
private fun bump(t: Long, from: Long, to: Long) = if (t <= from || t >= to) 0f else sin(seg(t, from, to) * PI_F)

// --- paper boat -----------------------------------------------------------------------------------------------------------

private object PaperBoat : EggHero(
    "paperboat", listOf("Fold", "Paper Boat", "Galleon"), Rarity.COMMON,
    "Бумажный кораблик: плывёт по волнам, наклон гонит его по матрице",
    mapOf("calm" to "качается на волнах", "happy" to "прыгает по гребням", "habit" to "плывёт с кильватером", "signature" to "становится журавликом",
        "hungry" to "мель, лист не достать", "sad" to "размокает под дождём", "tired" to "волны стихли", "asleep" to "штиль и луна",
        "eating" to "черпает листик", "game" to "шторм и скалы", "loved" to "круги по воде", "dizzy" to "водоворот",
        "tilt" to "катится по склону воды", "charging" to "вода поднимается", "grow" to "складывается из листа"),
) {
    private val BOAT = listOf(
        listOf("...#...", "..###..", "#######", ".#-#-#.", "..###.."),
        listOf(".....#.....", "....###....", "...#####...", "###########", ".#-#####-#.", "..#######.."),
        listOf("......#......", "......##.....", ".....####....", "....######...", "#############", ".##-#####-##.", "..#########.."),
    )
    /**
     * The water line (and a dim one under it). [lift] raises it at a column (a swell): the swell is a dim mound of water
     * under its raised line, steep bits of the line filled so it stays one line.
     */
    private fun water(c: Cv, t: Long, level: Float = 18f, amp: Float = 1f, slope: Float = 0f, b: Float = 0.35f, lift: (Int) -> Float = { 0f }) {
        var prev = 0
        for (x in 0..24) {
            val l = lift(x)
            val y = level + sin(x * 0.6f + t / 300f) * amp + (x - 12) * slope - l
            val yi = y.roundToInt()
            c.put(x.toFloat(), y, b)
            if (l < 0.5f) c.put(x.toFloat(), y + 2, b * 0.4f) else for (k in yi + 1..(y + l + 2).roundToInt()) c.put(x, k, b * 0.4f)
            if (x > 0 && abs(yi - prev) > 1) for (k in min(yi, prev) + 1 until max(yi, prev)) c.put(x, k, b)
            prev = yi
        }
    }
    private fun boat(c: Cv, st: Int, x: Float, bottom: Float, eyes: Int = 0, b: Float = 1f, sag: Boolean = false) {
        val rows = if (sag) BOAT[st].drop(1) else BOAT[st]
        val eyeRows = rows.map { it.replace('-', if (eyes == 0) '-' else '#') }
        c.spr(eyeRows, x, bottom, 0.38f * b)
        if (eyes == 2) { val y = bottom - 1; c.set((x - 2).roundToInt(), y.roundToInt(), 1f); c.set((x + 2).roundToInt(), y.roundToInt(), 1f) }
    }

    override val pastimes = listOf(
        DrawnPastime("hoists_flag", 4400, "поднимает флажок",
            "Мачта вытягивается вверх, на макушке разворачивается флажок и треплется на ветру, ветер чуть сносит кораблик; потом флажок спускается."),
        DrawnPastime("rides_swell", 4800, "взлетает на большую волну",
            "Справа катится большая волна; кораблик взлетает на гребень, съезжает с него и возвращается на своё место."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val bottom = 17.5f + sin(ct / 500f)
        val eyes = if (blinkAt(ct)) 1 else 0
        when (col) {
            "hoists_flag" -> {
                // The wind gets up: the boat drifts a little, its mast grows a row at a time with a pennant on top that
                // flutters, then all of it comes down again.
                water(c, ct)
                val x = 12f - 2f * outOf(t, ms, 900)
                boat(c, st, x, bottom, eyes)
                val rows = BOAT[st]
                val mx = (x - rows[0].length / 2f).roundToInt() + rows[0].indexOf('#')
                val top = (bottom - rows.size + 1).roundToInt()
                val up = (4.99f * min(seg(t, PEDGE + 100, PEDGE + 900), 1f - seg(t, ms - PEDGE - 900, ms - PEDGE - 100))).toInt()
                for (k in 1..up) c.put(mx, top - k, 0.38f)
                if (up >= 2) {
                    val fy = top - up
                    val flick = (t / 140) % 2 == 0L
                    for (i in 1..(if (flick) 3 else 2)) c.put(mx - i, fy, 0.75f)
                    for (i in 1..(if (flick) 2 else 3)) c.put(mx - i, fy + 1, 0.75f)
                }
            }
            "rides_swell" -> {
                // One big swell rolls in from the right; the boat rides up its crest, is carried along a little, slides
                // off the back and sails home.
                val amp = 3.5f * min(ease(seg(t, PEDGE, PEDGE + 400)), 1f - ease(seg(t, ms - PEDGE - 400, ms - PEDGE)))
                val hx = mix(31f, -8f, seg(t, PEDGE + 100, ms - PEDGE - 700))
                val lift = { x: Float -> amp * exp(-((x - hx) / 5f).let { it * it }) }
                water(c, ct, lift = { lift(it.toFloat()) })
                val carried = 3f * ease(((15f - hx) / 6f).coerceIn(0f, 1f))
                val x = 12f - carried * (1f - ease(seg(t, ms - PEDGE - 1100, ms - PEDGE - 100)))
                // The swell's dim body keeps a dark ring round the boat, so its outline never melts into the water.
                val hull = Cv().also { boat(it, st, x, bottom - lift(x), eyes) }
                for (y in 0..24) for (cx in 0..24) {
                    val v = c.get(cx, y)
                    if (v <= 0f || v >= 0.2f || hull.get(cx, y) > 0f || lift(cx.toFloat()) < 0.5f) continue
                    if ((-1..1).any { dy -> (-1..1).any { dx -> hull.get(cx + dx, y + dy) > 0f } }) c.cut(cx, y)
                }
                boat(c, st, x, bottom - lift(x), eyes)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val bob = sin(t / 500f)
        when (col) {
            "calm" -> { water(c, t); boat(c, st, 12f, 17.5f + bob, if (blinkAt(t)) 1 else 0) }
            "happy" -> { water(c, t, amp = 1.6f); val h = hop(t, 600f, 4f); boat(c, st, 12f, 17f - h, 2); if (h < 0.6f) for (k in -2..2) c.put(12f + k * 2.2f, 16f - abs(k), 0.6f) }
            "habit" -> { water(c, t); val x = 12f + sin(t / 3200f * 2 * PI_F) * 6; boat(c, st, x, 17.5f + bob); for (k in 1..5) c.put(x - k * 1.5f * (if (cos(t / 3200f * 2 * PI_F) > 0) 1 else -1), 19f + (k % 2), 0.25f - k * 0.03f) }
            "signature" -> {
                water(c, t)
                when {
                    t < 1000 -> boat(c, st, 12f, 17.5f + bob)
                    t < 1500 -> c.line(6f, 15f, 18f, 15f, 0.5f)
                    t < 5400 -> {
                        val p = seg(t, 1500, 5400); val a = p * 2 * PI_F
                        val cx = 12 + cos(a - PI_F / 2) * 6; val cy = 10 + sin(a - PI_F / 2) * 4
                        val flap = if ((t / 160) % 2 == 0L) -2f else 1f
                        c.line(cx - 4, cy + flap, cx, cy, 0.5f); c.line(cx + 4, cy + flap, cx, cy, 0.5f)
                        c.line(cx, cy, cx + 2, cy - 2, 0.5f); c.put(cx + 2, cy - 2, 1f); c.line(cx, cy, cx - 2, cy + 1, 0.4f)
                    }
                    else -> boat(c, st, 12f, mix(10f, 17.5f, seg(t, 5400, 6200)))
                }
            }
            "hungry" -> { water(c, t, level = 20f, amp = 0.4f); boat(c, st, 10f, 20f, 0); c.rows(listOf(".o", "o."), 19, (18f + bob).roundToInt(), 0.6f) }
            "sad" -> { rain(c, t); water(c, t, b = 0.25f); boat(c, st, 12f, 18f, 1, 0.6f, sag = true) }
            "tired" -> { water(c, t / 3, amp = 0.4f); boat(c, st, 12f, 18f, if (t % 1600 < 800) 1 else 0, 0.8f) }
            "asleep" -> { water(c, 0, amp = 0f, b = 0.2f); boat(c, st, 11f, 18f, 1, 0.8f); c.oval(19f, 5f, 2f, 2f, 0.5f); c.put(19f, 20f, 0.3f); zs(c, 15f, 12f, t) }
            "eating" -> {
                water(c, t)
                val lx = mix(22f, 13f, seg(t, 0, 1400))
                val dip = if (t in 1400..2000) 1.5f else 0f
                if (t < 1600) c.rows(listOf(".o", "o."), lx.roundToInt(), (18f + bob).roundToInt(), 0.6f)
                boat(c, st, 12f, 17.5f + bob + dip, if (t > 1600) 2 else 0)
            }
            "game" -> {
                water(c, t, amp = 1.3f)
                val x = 12f + tilt * 7
                for (i in 0 until 4) { val rx = 26f - ((t / 25f + i * 160) % 30); if (rx in -2f..26f) c.rows(listOf(".#.", "###"), rx.roundToInt(), 16, 0.5f) }
                boat(c, st, x, 17f + bob)
            }
            "loved" -> { water(c, t); for (k in 0..1) { val r = ((t + k * 1200) % 2400) / 2400f * 9; c.ring(12f, 18f, r, 0.25f * (1 - r / 9)) }; boat(c, st, 12f, 17.5f + bob * 0.5f, 2) }
            "dizzy" -> { for (k in 0 until 3) c.ring(12f, 15f, 2f + k * 3f, 0.15f, 0.5f, t / 300f + k, t / 300f + k + 4f); val a = t / 250f; boat(c, 0, 12 + cos(a) * 6, 15 + sin(a) * 3, 1) }
            "tilt" -> { water(c, t, slope = tilt * 0.25f); val x = 12f + tilt * 6; boat(c, st, x, 17.5f + (x - 12) * tilt * 0.25f + bob) }
            "charging" -> { val p = seg(t, 0, 2900); val lvl = mix(22f, 15f, p); water(c, t, level = lvl); for (y in (lvl + 3).roundToInt()..24) for (x in 0..24) c.put(x, y, 0.08f); boat(c, st, 12f, lvl - 0.5f + bob, if (p > 0.9f) 2 else 0) }
            "grow" -> {
                water(c, t)
                val p = seg(t, 0, 3600)
                if (p < 1f) { val w = mix(9f, 4f, p); for (y in 0..((1 - p) * 6).toInt()) c.line(12 - w, 10f + y, 12 + w, 10f + y, 0.3f); c.line(12 - w, 10f, 12f, 10f - p * 6, 0.5f); c.line(12 + w, 10f, 12f, 10f - p * 6, 0.5f) }
                else boat(c, if (st == 0 || t > 5000) st else st - 1, 12f, 17.5f + bob)
            }
        }
    }
}

// --- pebble ----------------------------------------------------------------------------------------------------------------

private object Pebble : EggHero(
    "pebble", listOf("Grit", "Pebble", "Boulder"), Rarity.COMMON,
    "Камушек: почти всё время спит; если долго не трогать телефон, на нём растёт мох",
    mapOf("calm" to "сонно дышит", "happy" to "подпрыгивает в пыли", "habit" to "растёт мох", "signature" to "раскалывается — внутри кристаллы",
        "hungry" to "ждёт росинку", "sad" to "мокнет под дождём", "tired" to "уходит в землю", "asleep" to "спит камнем",
        "eating" to "впитывает росу", "game" to "не трогай — растёт мох", "loved" to "теплеет", "dizzy" to "катится колесом",
        "tilt" to "медленно скатывается", "charging" to "греется на солнце", "grow" to "откололся от скалы"),
) {
    private val RX = floatArrayOf(4.5f, 7f, 9f)
    private val RY = floatArrayOf(3f, 4f, 5f)
    private fun rock(c: Cv, st: Int, x: Float = 12f, bottom: Float = 20f, eyes: Int = 1, b: Float = 1f, squash: Float = 1f, moss: Int = 3, look: Int = 0, up: Int = 0) {
        val rx = RX[st] * (2 - squash); val ry = RY[st] * squash
        val cy = bottom - ry
        c.oval(x, cy, rx, ry, 0.28f * b, 0.42f * b)
        c.line(x - rx * 0.4f, cy - ry * 0.4f, x - rx * 0.2f, cy, 0f)
        c.eyes(x - rx * 0.3f + look, x + rx * 0.3f + look, cy + ry * 0.1f - up, eyes)
        for (k in 0 until moss) c.put(x + rx * 0.3f + k, cy - ry - (if (k % 2 == 1) 1 else 0), 0.55f)
    }
    override val pastimes = listOf(
        DrawnPastime("snail_visit", 5600, "улитка в гостях",
            "Из-за камушка на макушку выползает улитка и не спеша переползает через него; камушек открывает глаза и следит за ней, пока она не скроется с другой стороны."),
        DrawnPastime("moss_flower", 4800, "цветок на макушке",
            "Изо мха на макушке вырастает стебелёк и раскрывается цветок; камушек поднимает на него глаза и любуется, потом цветок закрывается и прячется обратно в мох."),
    )

    /** A snail going right: its shell a ring, its foot along the bottom, its feeler in front. */
    private val SNAIL = listOf(".###..", ".#.#.#", ".###.#", "######")

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val squash = 1f + 0.05f * sin(ct / 1600f * PI_F)
        val peek = if (ct % 3200 in 1800..2600) 0 else 1
        val rx = RX[st] * (2 - squash); val ry = RY[st] * squash; val cy = 20f - ry
        when (col) {
            "snail_visit" -> {
                // The snail rises from behind the rock on the left, crawls over its top (bumping over the moss) and sinks
                // behind it on the right; the pebble opens its eyes and follows it.
                val crawl = seg(t, PEDGE + 200, ms - PEDGE - 200)
                val sx = (12f - rx + 1f + crawl * (2 * rx - 7f)).toInt()
                val hide = max(1f - seg(t, PEDGE + 200, PEDGE + 1000), seg(t, ms - PEDGE - 1000, ms - PEDGE - 200))
                val mid = sx + 2.5f
                val watching = hide < 0.9f
                rock(c, st, squash = squash, eyes = if (watching) 0 else peek,
                    look = if (!watching) 0 else if (mid < 10f) -1 else if (mid > 14f) 1 else 0, up = if (watching && abs(mid - 12f) < 3f) 1 else 0)
                if (hide < 1f) {
                    fun topAt(x: Int): Int { for (y in 0..24) if (c.get(x, y) > 0f) return y; return 25 }
                    fun inRock(x: Int, y: Int) = (x - 12f) * (x - 12f) / (rx * rx) + (y - cy) * (y - cy) / (ry * ry) <= 1f
                    // It rests on the lowest point under it: on a slope the rest of it is behind the rock, never in the air.
                    val base = (1..4).maxOf { topAt(sx + it) } - 1 + (hide * 4f).roundToInt()
                    val stretch = (t / 280) % 2 == 0L
                    val cells = ArrayList<Triple<Int, Int, Float>>()
                    SNAIL.forEachIndexed { dy, row -> row.forEachIndexed { dx, ch ->
                        if (ch == '#') cells += Triple(sx + dx, base - 3 + dy, if (dy == 3) 0.7f else 1f)
                    } }
                    if (stretch) cells += Triple(sx + 6, base, 0.7f)
                    for ((x, y, b) in cells) if (!inRock(x, y)) c.put(x, y, b)
                }
            }
            "moss_flower" -> {
                // A stem grows a row at a time out of the moss, a bud opens into a flower that nods; the pebble looks up at
                // it with a contented blink, and the flower closes and goes back down.
                val grow = min(seg(t, PEDGE + 200, PEDGE + 1100), 1f - seg(t, ms - PEDGE - 1100, ms - PEDGE - 200))
                val len = (grow * 4.99f).toInt()
                val open = t in (PEDGE + 1250)..(ms - PEDGE - 1250)
                val admiring = t in (PEDGE + 900)..(ms - PEDGE - 900)
                val bliss = open && (t - PEDGE - 1400) % 1400 in 800..1000
                rock(c, st, squash = squash, eyes = if (!admiring) peek else if (bliss) 1 else 0, look = if (admiring) 1 else 0, up = if (admiring) 1 else 0)
                val mx = (12f + rx * 0.3f + 1f).roundToInt(); val my = (cy - ry - 1f).roundToInt()
                val nod = if (!open) 0 else { val w = sin((t - PEDGE - 1400) / 380f); if (w > 0.55f) 1 else if (w < -0.55f) -1 else 0 }
                for (k in 1..len) c.put(mx + (if (k == 4) nod else 0), my - k, 0.45f)
                if (len == 4) {
                    val fx = mx + nod; val fy = my - 5
                    c.put(fx, fy, if (open) 1f else 0.8f)
                    if (open) { c.put(fx - 1, fy, 0.6f); c.put(fx + 1, fy, 0.6f); c.put(fx, fy - 1, 0.6f) }
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val breath = 1f + 0.05f * sin(t / 1600f * PI_F)
        val peek = if (t % 3200 in 1800..2600) 0 else 1
        when (col) {
            "calm" -> rock(c, st, squash = breath, eyes = peek)
            "happy" -> { val h = hop(t, 500f, 2.5f); rock(c, st, bottom = 20f - h, eyes = 0); if (h < 0.5f) for (k in -3..3 step 2) c.put(12f + k * 1.5f, 20f, 0.4f) }
            "habit" -> rock(c, st, eyes = 1, moss = 3 + (seg(t, 0, 3000) * 6).toInt())
            "signature" -> {
                val open = when { t < 1200 -> 0f; t < 1800 -> seg(t, 1200, 1800); t < 4800 -> 1f; else -> 1f - seg(t, 4800, 5600) }
                val gap = open * 3f
                c.oval(12f - gap, 20f - RY[st], RX[st] / 2 + 0.5f, RY[st], 0.3f, 0.42f)
                c.oval(12f + gap, 20f - RY[st], RX[st] / 2 + 0.5f, RY[st], 0.3f, 0.42f)
                if (open > 0.3f) for (k in 0 until 8) { val a = k * PI_F / 4 + t / 400f; c.put(12 + cos(a) * gap * 0.6f, 20 - RY[st] + sin(a) * RY[st] * 0.6f, if ((k + t / 160) % 2 == 0L) 1f else 0.5f) }
                if (t < 1200) c.line(12f, 20f - RY[st] * 2, 12f, 20f, 0.8f * seg(t, 300, 1200))
            }
            "hungry" -> { rock(c, st, eyes = 0); val ph = (t % 1600) / 1600f; c.put(16f, 4f + ph * 4, 0.6f) }
            "sad" -> { rain(c, t); rock(c, st, eyes = 1, b = 0.7f, moss = 1) }
            "tired" -> rock(c, st, bottom = 20f + seg(t, 0, 3000) * 2, eyes = 1, b = 0.8f)
            "asleep" -> { rock(c, st, squash = breath, eyes = 1, b = 0.85f); zs(c, 16f, 12f, t) }
            "eating" -> { val y = mix(2f, 20f - RY[st] * 2, seg(t, 0, 1000)); if (t < 1100) c.put(12f, y, 0.9f); rock(c, st, eyes = if (t > 1100) 0 else 1, b = if (t in 1100..2000) 1.4f else 1f) }
            "game" -> { val moss = if (t < 4400) (t / 400).toInt() else 0; rock(c, st, eyes = 1, moss = 2 + moss); if (t in 4400..4800) c.line(4f, 22f, 20f, 22f, 0.8f) }
            "loved" -> { rock(c, st, eyes = 0, b = 1.15f + 0.2f * sin(t / 200f)); for (sd in intArrayOf(-1, 1)) c.put(12f + sd * RX[st] * 0.5f, 20f - RY[st] + 1, 0.7f) }
            "dizzy" -> { val q = (t / 200) % 2 == 0L; rock(c, st, x = 12f + sin(t / 150f) * 3, squash = if (q) 1.25f else 0.8f, eyes = 1) }
            "tilt" -> rock(c, st, x = 12f + tilt * 4f, eyes = peek)
            "charging" -> { val p = seg(t, 0, 2900); for (k in 0 until 5) { val a = -PI_F / 2 + (k - 2) * 0.4f; c.line(12 + cos(a) * 11, 10 + sin(a) * 11, 12 + cos(a) * (11 - p * 5), 10 + sin(a) * (11 - p * 5), 0.4f) }; rock(c, st, b = 0.8f + 0.6f * p, eyes = if (p > 0.9f) 0 else 1) }
            // Typing: each key a chisel blow (it jolts, the crack's tip flares); the crack runs deeper as you type, and
            // after 40 s it splits open on its crystals (its signature).
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { draw(c, "signature", min(1200L + y.stageMs, 4200L) + (if (y.stageMs > 3000) (t % 1600) else 0L), st, tilt); return }
                val g = y.grown(); val squash = 1f - y.hit(140) * 0.15f
                rock(c, st, squash = squash, eyes = if (y.paused) peek else 0, moss = 0)
                val ry = RY[st] * squash; val top = 20f - ry * 2; val len = (g * ry * 2 * 0.9f).roundToInt()
                var x = 12f; for (j in 0..len) { x += if (j % 2 == 0) 0.6f else -0.6f; c.cut(x, top + j) }
                if (len > 0 && y.hit(160) > 0f) { c.put(12f, top - 1, 1f); c.put(11f, top - 1, 0.6f); c.put(13f, top - 1, 0.6f) } }
            "grow" -> { if (t < 2400) { c.oval(12f, 14f, 10f, 7f, 0.25f, 0.4f); if (t > 1400) c.line(10f, 8f, 14f, 20f, 0f) } else { val x = mix(14f, 12f, seg(t, 2400, 3400)); rock(c, if (st == 0 || t > 4600) st else st - 1, x = x, eyes = if (t > 4200) 0 else 1) } }
        }
    }
}

// --- sprout ----------------------------------------------------------------------------------------------------------------

private object Sprout : EggHero(
    "sprout", listOf("Seed", "Sprout", "Bloom"), Rarity.COMMON,
    "Росток в горшке: тянется к свету, если повернуть телефон к окну",
    mapOf("calm" to "листики качаются", "happy" to "хлопает листьями", "habit" to "тянется к солнышку", "signature" to "распускается цветок",
        "hungry" to "ждёт полива", "sad" to "вянет", "tired" to "листья сложились", "asleep" to "бутон закрылся",
        "eating" to "лейка поливает", "game" to "к свету через камни", "loved" to "обнимает листьями", "dizzy" to "стебель крутится",
        "tilt" to "растёт вверх, куда ни наклони", "charging" to "фотосинтез", "grow" to "семечко проклюнулось"),
) {
    private val H = floatArrayOf(4f, 6f, 8f)
    private val STEM = Tubes.Look(lit = 0.62f, core = 0.42f, shadow = 0.2f, thin = 0.45f, thinBelow = 0.95f)
    /** The stem's wave for the clip being drawn ([Tubes.waveMs]); pastimes draw on the calm clock. */
    private var waveMs = 3200f
    private fun plant(
        c: Cv, st: Int, t: Long, lean: Float = 0f, droop: Float = 0f, eyes: Int = 0, leaf: Float = 0f, bloom: Float = if (st == 2) 0.5f else 0f, h: Float = H[st], b: Float = 1f,
        look: Int = 0, reach: List<Pair<Float, Float>>? = null,
    ) {
        c.rows(listOf("#########", ".#######.", ".#######.", "..#####.."), 8, 17, 0.38f * b)
        // A glance moves only the inner eye, so neither ever sits on the pot's rim.
        c.eyes(10f + max(look, 0), 14f + min(look, 0), 18f, eyes)
        val topX = 12f + lean; val topY = 16f - h + droop
        // The stem: a lit tube from the pot to the bud, a slow wave running up it (its ends stay put).
        val wv = TAU * t / waveMs
        Tubes.paint(c, listOf(Tube(Tubes.path(30) { q -> (mix(12f, topX, q) + 1.6f * sin(wv - q * 3f) * q * (1 - q)) to mix(16f, topY, q) }, 0.8f + 0.2f * st, 0.75f)),
            STEM, b, clip = { _, y -> y <= 16 }, crumbs = false)
        val ly = 16f - h * 0.55f + droop * 0.6f; val lx = 12f + lean * 0.55f
        val open = 1f - droop / 4f + leaf
        c.line(lx, ly, lx - 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.45f * b); c.put(lx - 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.7f * b)
        // The right leaf, or (reaching) a bent one along [reach]; its tip bright.
        var (rx, ry) = lx to ly
        for ((nx, ny) in reach ?: listOf((lx + 2.5f * open) to (ly - 1.2f * open + droop * 0.5f))) { c.line(rx, ry, nx, ny, 0.45f * b); rx = nx; ry = ny }
        c.put(rx, ry, 0.7f * b)
        if (bloom > 0f) { val r = 0.8f + bloom * 1.6f; for (k in 0 until 5) { val a = k * 2 * PI_F / 5 + t / 2000f; c.put(topX + cos(a) * r, topY + sin(a) * r, 0.6f * b) }; c.put(topX, topY, 1f) }
        else c.put(topX, topY, 0.6f * b)
    }
    override val pastimes = listOf(
        DrawnPastime("scratch_pot", 4400, "чешет горшок листиком",
            "Опускает листик к боку горшка и чешет его, зажмурившись от удовольствия, потом поднимает листик обратно."),
        DrawnPastime("hide_in_pot", 4800, "прячется в горшок",
            "Стебелёк уходит в горшок, листики складываются; горшок косится глазами туда-сюда — и росток выпрыгивает обратно."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val lean = sin(ct / 700f) * 0.8f
        val blink = if (blinkAt(ct)) 1 else 0
        when (col) {
            "scratch_pot" -> {
                // The right leaf bends out and down to the side of the pot and rubs it up and down, eyes shut in bliss.
                val r = outOf(t, ms, 700)
                val lx = 12f + lean * 0.55f; val ly = 16f - H[st] * 0.55f
                val rubbing = r >= 1f
                val rub = rubbing && (t / 130) % 2 == 0L
                val nx = lx + 2.5f; val ny = ly - 1.2f
                plant(c, st, ct, lean = lean, eyes = if (t in (PEDGE + 1400)..(PEDGE + 2400)) 1 else blink,
                    reach = listOf(mix(nx, if (rub) 17f else 16f, r) to mix(ny, ly + 1f, r), mix(nx, 16f, r) to mix(ny, if (rub) 18f else 19f, r)))
            }
            "hide_in_pot" -> {
                // Shrinks down into its pot (leaves folding), the pot's eyes look left and right, then it springs back up a
                // bit too far and settles, eyes shining.
                val h0 = H[st]
                val down = ease(seg(t, PEDGE + 100, PEDGE + 1000))
                val upP = seg(t, ms - PEDGE - 1000, ms - PEDGE - 300)
                val h = if (upP > 0f) h0 * upP + 2f * sin(upP * PI_F) else h0 * (1f - down)
                val hiding = t in (PEDGE + 1000) until (ms - PEDGE - 1000)
                val look = if (!hiding) 0 else when ((t - PEDGE - 1000) / 450) { 0L -> -1; 2L -> 1; else -> 0 }
                plant(c, st, ct, lean = lean, leaf = -(1f - (h / h0).coerceIn(0f, 1f)), h = max(0f, h), look = look,
                    bloom = if (st == 2 && abs(h - h0) < 0.01f) 0.5f else 0f, eyes = if (upP in 0.01f..0.99f) 2 else blink)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        waveMs = Tubes.waveMs(col)
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val sw = sin(t / 700f) * 0.8f
        when (col) {
            "calm" -> plant(c, st, t, lean = sw, eyes = if (blinkAt(t)) 1 else 0)
            "happy" -> plant(c, st, t, lean = sw, leaf = if ((t / 200) % 2 == 0L) 0.5f else -0.3f, eyes = 2, bloom = 0.8f)
            "habit" -> { val p = sin(seg(t, 200, 3000) * PI_F); c.disc(21f, 5f, 1.5f, p); plant(c, st, t, lean = 3f * p, eyes = 0) }
            "signature" -> { val p = ease(seg(t, 600, 2600)) * (1 - ease(seg(t, 5000, 6200))); plant(c, st, t, bloom = 0.2f + p * 1.5f, eyes = 2); if (p > 0.7f) sparks(c, 12f, 16f - H[st] - 2, t, 6, 4f, 5f, 33) }
            "hungry" -> { plant(c, st, t, droop = 1.5f, eyes = 0); val ph = (t % 1400) / 1400f; c.put(19f, 6f + ph * 3, 0.5f); c.rows(listOf(".o.", "ooo", ".o."), 18, 4, 0.3f) }
            "sad" -> { plant(c, st, t, droop = 3.5f, eyes = 1, b = 0.7f); val ph = (t % 1600) / 1600f; c.put(14f + ph, 10f + ph * 7, 0.4f * (1 - ph)) }
            "tired" -> plant(c, st, t, lean = sin(t / 1200f) * 0.6f, droop = 2f + sin(t / 800f) * 0.5f, leaf = -0.6f, eyes = 1)
            "asleep" -> { plant(c, st, t, leaf = -0.8f, bloom = 0f, eyes = 1, b = 0.8f); zs(c, 16f, 8f, t) }
            "eating" -> {
                c.rows(listOf("###..", "#####", "###.#", "###.."), 17, 3, 0.4f)
                for (i in 0 until 4) { val ph = ((t + i * 220) % 880) / 880f; if (t < 2400) c.put(16f - ph * 2, 6f + ph * 8, 0.6f) }
                plant(c, st, t, droop = 2f * (1 - seg(t, 600, 2400)), eyes = if (t > 2000) 2 else 0)
            }
            "game" -> { val sx = 12f + tilt * 9; c.disc(sx, 3f, 1.5f, 1f); for (k in 0..1) c.rows(listOf("###"), 5 + k * 12, 8 + k * 2, 0.4f); plant(c, st, t, lean = (sx - 12) * 0.35f, eyes = 0) }
            "loved" -> plant(c, st, t, leaf = 0.8f, eyes = 2, bloom = 0.6f)
            "dizzy" -> plant(c, st, t, lean = sin(t / 90f) * 3f, eyes = 1)
            "tilt" -> plant(c, st, t, lean = -tilt * 3f, eyes = 0)
            "charging" -> { val p = seg(t, 0, 2900); for (k in 0 until 4) { val ph = ((t + k * 300) % 1200) / 1200f; c.put(6f + k * 4, ph * 9, 0.5f) }; plant(c, st, t, h = H[st] * (0.6f + 0.4f * p), b = 0.7f + 0.5f * p, eyes = if (p > 0.9f) 2 else 0) }
            // Typing: each key a drop of water falls onto it and it grows a notch; leaves open, a bud forms, and after
            // 40 s it bursts into flower.
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                val h = 1.5f + (H[st] + 2f - 1.5f) * g; val lean = if (y.rush) sin(t / 160f) * 1.2f else sw * 0.4f
                val bloom = if (y.stage == 2) 0.3f + ease(y.stageMs / 1500f) * 1.4f else 0f
                plant(c, st, t, lean = lean, droop = if (y.paused) 1f else 0f, leaf = if (y.hit(180) > 0f) 0.6f else (g - 0.5f).coerceAtMost(0f),
                    eyes = if (y.stage == 2 || y.rush) 2 else if (blinkAt(t)) 1 else 0, h = h, bloom = bloom)
                val topY = 16f - h
                for (at in y.keys.takeLast(3)) { val q = (y.now - at) / 260f; if (q in 0f..1f) { val dy = mix(1f, topY - 2, q); c.put(12f + lean, dy, 0.95f); c.put(12f + lean, dy - 1, 0.45f) } } }
            "grow" -> { val p = seg(t, 600, 5200); plant(c, if (st == 0) 0 else st - (if (p < 0.6f) 1 else 0), t, h = max(0.5f, H[st] * p), eyes = if (p > 0.3f) 0 else 1, bloom = 0f) }
        }
    }
}

// --- spider ----------------------------------------------------------------------------------------------------------------

private object Spider : EggHero(
    "spider", listOf("Dangle", "Spider", "Weaver"), Rarity.RARE,
    "Паучок на паутинке: плетёт узор, пока телефон лежит спокойно",
    mapOf("calm" to "качается на нити", "happy" to "пружинит на нити", "habit" to "плетёт паутинку", "signature" to "паутина на всю матрицу",
        "hungry" to "муха не долетает", "sad" to "повис без сил", "tired" to "медленно ползёт вверх", "asleep" to "свернулся клубком",
        "eating" to "муха в паутине", "game" to "ловит мух", "loved" to "машет лапками", "dizzy" to "крутится на нити",
        "tilt" to "нить — маятник", "charging" to "нити загораются", "grow" to "из кокона на нити"),
) {
    private val R = floatArrayOf(1.6f, 2.2f, 2.8f)
    /** [lift] raises a leg (0..3 the left ones top down, 4..7 the right ones) by 0..1, bright as it goes up. */
    private fun spider(
        c: Cv, st: Int, t: Long, x: Float, y: Float, eyes: Int = 0, legs: Float = 1f, thread: Boolean = true, b: Float = 1f, curl: Boolean = false,
        lift: ((Int) -> Float)? = null,
    ) {
        if (thread) c.line(x, 0f, x, y - R[st], 0.2f)
        val wig = if ((t / 200) % 2 == 0L) 0.5f else -0.5f
        for (sd in intArrayOf(-1, 1)) for (k in 0..3) {
            val l = lift?.invoke(if (sd < 0) k else 4 + k) ?: 0f
            if (curl) c.put(x + sd * (R[st] + 0.5f), y - 1 + k * 0.6f, 0.3f * b)
            else if (l > 0f) c.line(x + sd * R[st], y - 1 + k * 0.8f, x + sd * (R[st] + 2.5f), y - 2.5f + k * 1.6f - l * (3f + k * 1.4f), (0.3f + 0.4f * l) * b)
            else c.line(x + sd * R[st], y - 1 + k * 0.8f, x + sd * (R[st] + 3f * legs), y - 2.5f + k * 1.6f + wig * sd * legs, 0.3f * b)
        }
        c.oval(x, y, R[st], R[st] * 0.9f, 0.38f * b)
        c.eyes(x - 0.8f, x + 0.8f, y - 0.3f, eyes)
    }
    private fun web(c: Cv, cx: Float, cy: Float, r: Float, b: Float, spokes: Int = 8, rings: Int = 3) {
        for (k in 0 until spokes) { val a = k * 2 * PI_F / spokes; c.line(cx, cy, cx + cos(a) * r, cy + sin(a) * r, b) }
        for (i in 1..rings) c.ring(cx, cy, r * i / rings, b * 0.8f, 0.45f)
    }
    override val pastimes = listOf(
        DrawnPastime("counts_legs", 5000, "пересчитывает лапки",
            "Замирает на нити и поднимает лапки по одной — четыре слева, четыре справа; все восемь на месте, и он радостно ими шевелит."),
        DrawnPastime("hammock", 5200, "качается в гамаке",
            "Натягивает поперёк матрицы провисшую нить, укладывается в неё, подобрав лапки, и сонно качается из стороны в сторону; потом сматывает гамак."),
    )

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val y0 = 12f + sin(ct / 800f) * 3f
        val blink = if (blinkAt(ct)) 1 else 0
        when (col) {
            "counts_legs" -> {
                // Stops swinging, lifts each leg in turn (left top to bottom, then right), then wiggles all eight, eyes shining.
                val r = outOf(t, ms, 600)
                val cheer = t in 2900L..3900L
                spider(c, st, if (cheer) ct * 3 else ct, 12f, mix(y0, 12f, r), eyes = if (cheer) 2 else blink, legs = if (cheer) 1.3f else 1f,
                    lift = { i -> val s0 = PEDGE + 600L + i * 200L; bump(t, s0, s0 + 380L) })
            }
            "hammock" -> {
                // Spins a sagging thread from rim to rim, settles into it legs drawn in, and swings drowsily; winds it up again.
                val r = outOf(t, ms, 700)
                val vis = seg(t, PEDGE + 300, PEDGE + 1100) * (1f - seg(t, ms - PEDGE - 1000, ms - PEDGE - 300))
                val lying = t in (PEDGE + 1100)..(ms - PEDGE - 1000)
                val swing = min(1f, 2.5f * bump(t, PEDGE + 1100, ms - PEDGE - 1000)) * sin((t - PEDGE - 1100) / 260f) * 2.5f
                val cx = 12f + swing * 2f
                if (vis > 0f) for (i in 0..60) {
                    val u = i / 60f
                    if (abs(u - 0.5f) > vis * 0.5f) continue
                    c.put((1 - u) * (1 - u) * 1f + 2 * u * (1 - u) * cx + u * u * 23f, (1 - u) * (1 - u) * 9f + 2 * u * (1 - u) * 16f + u * u * 9f, 0.22f)
                }
                val doze = lying && (t - PEDGE - 1100) % 1300 in 900..1200
                spider(c, st, ct, mix(12f, 6f + 0.5f * cx, r), mix(y0, 12.5f - R[st] * 0.9f, r), eyes = if (doze) 1 else if (lying) 0 else blink, legs = mix(1f, 0.5f, r))
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val y0 = 12f + sin(t / 800f) * 3f
        when (col) {
            "calm" -> spider(c, st, t, 12f, y0, if (blinkAt(t)) 1 else 0)
            "happy" -> spider(c, st, t, 12f, 10f + hop(t, 400f, 6f), 2, legs = 1.3f)
            "habit" -> { web(c, 12f, 10f, 3f + seg(t, 0, 3000) * 5f, 0.18f, rings = 1 + (seg(t, 0, 3000) * 3).toInt()); spider(c, st, t, 12f + cos(t / 300f) * 4, 10f + sin(t / 300f) * 4, 0, thread = false) }
            "signature" -> { val p = ease(seg(t, 0, 2500)); web(c, 12f, 12f, 11f * p, 0.25f, spokes = 12, rings = max(1, (p * 5).toInt())); if (p >= 1f) for (k in 0 until 6) { val a = k * 1.1f; if ((t / 160 + k) % 3 == 0L) c.put(12 + cos(a) * 7, 12 + sin(a) * 7, 1f) }; spider(c, st, t, 12f, 12f, 2, thread = false) }
            "hungry" -> { val fx = 18f + sin(t / 120f) * 2; val fy = 8f + cos(t / 90f) * 2; c.put(fx, fy, 1f); c.put(fx - 1, fy - 1, 0.4f); c.put(fx + 1, fy - 1, 0.4f); spider(c, st, t, 11f, y0, 0) }
            "sad" -> spider(c, st, t, 12f, 17f, 1, legs = 0.4f, b = 0.7f)
            "tired" -> spider(c, st, t, 12f, 18f - seg(t, 0, 3200) * 8, 1, legs = 0.6f)
            "asleep" -> { spider(c, st, t, 12f, 12f + sin(t / 2000f), 1, curl = true, b = 0.85f); zs(c, 16f, 8f, t) }
            "eating" -> { web(c, 12f, 12f, 7f, 0.15f); val wrap = seg(t, 600, 2400); c.disc(15f, 12f, 1f, 0.8f); if (wrap > 0) c.ring(15f, 12f, 1.6f, 0.5f * wrap, 0.4f, 0f, wrap * 2 * PI_F); spider(c, st, t, 12f + wrap * 1.5f, 12f, if (t > 2400) 2 else 0, thread = false) }
            "game" -> { for (x in 2..22) c.put(x, 3, 0.2f); val x = 12f + tilt * 8; for (i in 0 until 4) { val fx = (t / 30f + i * 60) % 26; c.put(fx, 8f + i * 3 + sin(t / 100f + i) * 0.6f, 0.9f) }; c.line(x, 3f, x, 11f, 0.25f); spider(c, st, t, x, 13f, 0, thread = false) }
            "loved" -> spider(c, st, t, 12f, 12f, 2, legs = 1f + 0.4f * sin(t / 100f))
            "dizzy" -> { val a = t / 120f; c.line(12f, 0f, 12f, 10f, 0.2f); for (k in 0 until 8) { val aa = a + k * PI_F / 4; c.line(12f, 12f, 12 + cos(aa) * 5, 12 + sin(aa) * 4, 0.3f) }; c.oval(12f, 12f, R[st], R[st], 0.38f); c.eyes(11f, 13f, 12f, 1) }
            "tilt" -> { val a = -tilt * 0.6f; val x = 12 + sin(a) * 12; val y = cos(a) * 12; c.line(12f, 0f, x, y - R[st], 0.2f); spider(c, st, t, x, y, 0, thread = false) }
            "charging" -> { val p = seg(t, 0, 2900); val n = (p * 8).toInt(); for (k in 0 until n) { val a = k * PI_F / 4; c.line(12f, 12f, 12 + cos(a) * 10, 12 + sin(a) * 10, 0.35f) }; spider(c, st, t, 12f, 12f, if (p > 0.9f) 2 else 0, thread = false) }
            // Typing: each key it spins one more thread of the spiral, the new one flashing, and runs to its end: the web
            // grows thread by thread from the middle; after 40 s it is whole and dew glints on it.
            "typing" -> { val y = DrawnTyping; val n = min((y.key + 6) / 6, 30)
                for (k in 0 until 8) { val a = k * PI_F / 4; c.line(12f, 12f, 12 + cos(a) * 10.5f, 12 + sin(a) * 10.5f, 0.1f) }
                var ex = 12f; var ey = 12f
                for (j in 0 until n) { val a0 = j * PI_F / 4; val a1 = (j + 1) * PI_F / 4; val r0 = 1.5f + j * 0.3f; val r1 = 1.5f + (j + 1) * 0.3f
                    ex = 12 + cos(a1) * r1; ey = 12 + sin(a1) * r1
                    c.line(12 + cos(a0) * r0, 12 + sin(a0) * r0, ex, ey, if (j == n - 1 && y.hit(220) > 0f) 1f else 0.28f) }
                if (y.stage == 2) for (k in 0 until 5) { val a = k * 1.3f + 0.4f; if ((t / 200 + k) % 4 == 0L) c.put(12 + cos(a) * 6.5f, 12 + sin(a) * 6.5f, 1f) }
                if (y.paused || n == 0) { ex = 12f; ey = 12f }
                spider(c, 0, t, ex, ey, if (y.rush || y.stage == 2) 2 else if (y.paused && blinkAt(t)) 1 else 0, thread = false, legs = 0.8f + y.hit(150) * 0.4f) }
            "grow" -> { if (t < 2400) { c.line(12f, 0f, 12f, 8f, 0.2f); c.oval(12f, 10f, 2f, 2.5f, 0.4f, 0.5f); if (t > 1600) c.cut(12, 11) } else spider(c, if (st == 0 || t > 4400) st else st - 1, t, 12f, mix(10f, y0, seg(t, 2400, 3600)), if (t > 3600) 0 else 1) }
        }
    }
}

// --- astronaut ------------------------------------------------------------------------------------------------------------

private object Astronaut : EggHero(
    "astronaut", listOf("Cadet", "Astronaut", "Commander"), Rarity.RARE,
    "Космонавт на тросе: в невесомости руки и ноги отстают от тела и догоняют",
    mapOf("calm" to "парит на тросе", "happy" to "сальто в невесомости", "habit" to "пшик ранцем", "signature" to "полёт на ранце",
        "hungry" to "тюбик уплывает", "sad" to "трос оборвался", "tired" to "обвис и оседает", "asleep" to "спит на боку",
        "eating" to "ловит тюбик", "game" to "плывёт по инерции", "loved" to "машет рукой", "dizzy" to "кувыркается",
        "tilt" to "дрейфует, трос следом", "charging" to "ток по тросу", "grow" to "выплывает из капсулы"),
) {
    /** One stage's suit: rows '#' the suit, 'v' the lit visor, '-' a dark eye; [cr] the row of the eyes (the helmet's middle). */
    private class Form(val rows: List<String>, val cr: Int, val ex: Int, val armX: Int, val armY: Int, val hipX: Int, val hipY: Int, val len: Int) {
        val half = (rows[0].length - 1) / 2
    }
    private val FORMS = listOf(
        Form(listOf(".###.", "#vvv#", "#-v-#", ".###.", ".###.", ".###."), 2, 1, 2, 4, 1, 6, 2),
        Form(listOf(".#####.", "#vvvvv#", "#v-v-v#", "#vvvvv#", ".#####.", ".#####.", "..###..", "..###.."), 2, 1, 3, 5, 1, 8, 3),
        Form(listOf("..#####..", ".#######.", "#vvvvvvv#", "#v-vvv-v#", "#vvvvvvv#", ".#######.", "..#####..", "...###...", "..#####..", "..#####..", "..#####.."), 3, 2, 3, 8, 2, 11, 4),
    )
    private val HOME = floatArrayOf(10f, 9f, 7f)
    /** How its limbs hang at rest (arms, then legs: the angle out from straight down), and flung out like a star. */
    private val REST = floatArrayOf(0.6f, 0.6f, 0.4f, 0.4f)
    private val JACK = floatArrayOf(2.3f, 2.3f, 0.9f, 0.9f)
    private val LIMP = floatArrayOf(0.08f, 0.08f, 0.06f, 0.06f)
    const val OPEN = 0
    const val HAPPY = 1
    const val CLOSED = 2
    const val WIDE = 3
    const val PEEK = 4
    const val TIED = 0
    const val SNAPPED = 1
    const val LOOSE = 2

    /**
     * The astronaut, its helmet's middle at [x], [y], turned [q] quarter turns (whole quarters only, so it stays crisp).
     * [th] are its four limbs' angles; [reach] is where its right glove reaches to instead. Its tether runs from its belt
     * to the bottom of the matrix ([TIED]), hangs torn off ([SNAPPED]) or is not there ([LOOSE]); [charge] sends pulses up it.
     */
    fun figure(
        c: Cv, st: Int, t: Long, x: Int, y: Int, th: FloatArray, q: Int = 0, eyes: Int = OPEN, visor: Float = 0.85f, look: Int = 0,
        tether: Int = TIED, charge: Boolean = false, reach: Pair<Float, Float>? = null, tuck: Boolean = false,
    ) {
        val f = FORMS[st]
        fun px(dx: Float, dy: Float) = x + when (q) { 0 -> dx; 1 -> -dy; 2 -> -dx; else -> dy }
        fun py(dx: Float, dy: Float) = y + when (q) { 0 -> dy; 1 -> dx; 2 -> -dy; else -> -dx }
        val grid = Array(f.rows.size) { f.rows[it].toCharArray() }
        for (sd in intArrayOf(-1, 1)) {
            val c0 = f.half + sd * f.ex
            grid[f.cr][c0] = 'v'
            when (if (eyes == PEEK) (if (sd > 0) OPEN else CLOSED) else eyes) {
                OPEN -> grid[f.cr][c0 + (if (st == 0) 0 else look)] = '-'
                CLOSED -> grid[f.cr][c0] = 'c'
                WIDE -> { grid[f.cr][c0] = '-'; grid[f.cr - 1][c0] = '-' }
                HAPPY -> if (st == 2) { grid[f.cr - 1][c0] = '-'; grid[f.cr][c0 - 1] = '-'; grid[f.cr][c0 + 1] = '-' } else grid[f.cr - 1][c0] = '-'
            }
        }
        val bx = px(0f, (f.rows.size - f.cr).toFloat()); val by = py(0f, (f.rows.size - f.cr).toFloat())
        if (tether == SNAPPED) for (i in 0..4) c.put((bx + if (i == 4) sin(t / 500f).roundToInt() else 0).roundToInt(), (by + i).roundToInt(), 0.24f)
        if (tether == TIED) {
            val dist = hypot(bx - 12f, by - 24f); val slack = max(0f, 15f - dist)
            val mx = (bx + 12f) / 2 + slack * 0.55f * sin(t / 1250f) + (if (bx > 12f) -1 else 1) * slack * 0.25f; val my = (by + 24f) / 2 + slack * 0.2f
            for (i in 0..40) {
                val u = i / 40f
                c.put((1 - u) * (1 - u) * bx + 2 * u * (1 - u) * mx + u * u * 12f, (1 - u) * (1 - u) * by + 2 * u * (1 - u) * my + u * u * 24f,
                    if (charge && Math.floorMod((u * 12 - t / 170f).toInt(), 4) == 0) 0.9f else 0.22f)
            }
        }
        // Its limbs: lit tubes from the shoulders and hips, a brighter glove or boot at the end.
        for (i in 0 until 4) {
            val side = if (i % 2 == 1) 1 else -1
            val jx = (side * (if (i < 2) f.armX else f.hipX)).toFloat(); val jy = ((if (i < 2) f.armY else f.hipY) - f.cr).toFloat()
            if (i == 1 && reach != null) { c.line(px(jx, jy), py(jx, jy), reach.first, reach.second, 0.3f); c.put(reach.first, reach.second, 0.75f); continue }
            val len = if (tuck && i >= 2) max(1, f.len - 1) else f.len
            for (k in 0..len * 2) { val r = k / 2f; val dx = jx + side * sin(th[i]) * r; val dy = jy + cos(th[i]) * r; c.put(px(dx, dy), py(dx, dy), if (k >= len * 2 - 1) 0.75f else 0.3f) }
        }
        grid.forEachIndexed { r, row -> row.forEachIndexed { col, ch ->
            if (ch != '.') { val dx = (col - f.half).toFloat(); val dy = (r - f.cr).toFloat()
                c.set(px(dx, dy).roundToInt(), py(dx, dy).roundToInt(), when (ch) { '#' -> 0.3f; 'v' -> visor; 'c' -> 0.12f; else -> 0f }) }
        } }
    }

    // It floats: its body follows where it is going on a soft spring, and its limbs trail behind the body's every push.
    // Live this runs on the engine's clock ([DrawnFeel]); in a clip on the clip's own time, settled when the clip starts over.
    private class Pose(val x: Int, val y: Int, val th: FloatArray)
    private var pKey = ""
    private var pAt = 0L
    private var bx = 12f
    private var by = 8f
    private var bvx = 0f
    private var bvy = 0f
    private val lth = REST.copyOf()
    private val lom = FloatArray(4)

    @Synchronized
    private fun float(st: Int, col: String, t: Long, tilt: Float, hx: Float = 12f, hy: Float = HOME[st], rest: FloatArray = REST, sway: Float = 0.22f): Pose {
        val live = DrawnFeel.live
        val clock = if (live) DrawnFeel.timeMs else t
        val tx = if (col == "tilt") tilt.coerceIn(-1f, 1f) else if (live) DrawnFeel.tiltX.coerceIn(-1f, 1f) else 0f
        val w = if (live) DrawnFeel.wind.coerceIn(0f, 1f) else 0f
        val jolt = live && DrawnFeel.startleMs in 0 until 500
        val want = FloatArray(4) { i -> (if (jolt) JACK[i] else rest[i]) + w * (if (i < 2) 1.6f else 0.5f) }
        val tgx = hx + sin(clock / 3200f * 2 * PI_F) * 1.1f + tx * 5f
        val tgy = hy + sin(clock / 3200f * 4 * PI_F + 1f) * 1.1f - w * 3f - (if (jolt) 2f else 0f)
        val key = if (live) "live$st" else "$col$st"
        val dt = clock - pAt
        if (key != pKey || dt < 0 || dt > 400) { bx = tgx; by = tgy; bvx = 0f; bvy = 0f; for (i in 0 until 4) { lth[i] = want[i]; lom[i] = 0f } } else {
            var left = dt.toInt()
            while (left > 0) {
                val h = min(left, 10) / 1000f
                val ax = (tgx - bx) * 14f - bvx * 1.7f; val ay = (tgy - by) * 14f - bvy * 1.7f
                bvx += ax * h; bvy += ay * h; bx += bvx * h; by += bvy * h
                for (i in 0 until 4) {
                    val side = if (i % 2 == 1) 1 else -1
                    val idle = (sway + 0.3f * w) * sin(clock / 1000f * (0.9f + i * 0.13f) * (if (w > 0.3f) 9f else 1f) + i * 1.7f)
                    val acc = (want[i] + idle - lth[i]) * 26f - lom[i] * 3.2f - side * ax * 0.09f + ay * 0.07f
                    lom[i] += acc * h; lth[i] = (lth[i] + lom[i] * h).coerceIn(-0.5f, 3f)
                }
                left -= 10
            }
        }
        pKey = key; pAt = clock
        return Pose(bx.roundToInt(), by.roundToInt(), lth.copyOf())
    }

    private fun feltEyes(): Int? = if (!DrawnFeel.live) null else if (DrawnFeel.startleMs in 0 until 500) WIDE else if (DrawnFeel.wind > 0.3f) CLOSED else null
    private fun feltLook(): Int = if (!DrawnFeel.live) 0 else when (DrawnFeel.startleMs) { in 500 until 950 -> -1; in 950 until 1400 -> 1; else -> if (abs(DrawnFeel.tiltX) > 0.3f) (if (DrawnFeel.tiltX > 0) 1 else -1) else 0 }
    private fun blinks(t: Long) = if (t % 3000 < 140) CLOSED else OPEN

    /** A food tube, its cap first. */
    private fun tube(c: Cv, x: Int, y: Int) { for (i in 0..3) c.put(x + i, y, if (i == 0) 1f else 0.55f); c.put(x + 3, y - 1, 0.55f); c.put(x + 3, y + 1, 0.55f) }

    override val pastimes = listOf(
        DrawnPastime("jumping_jacks", 4800, "делает зарядку",
            "Прямо в невесомости делает зарядку: руки вверх, ноги врозь — и обратно, раз за разом, потом снова плывёт."),
        DrawnPastime("catches_comet", 5200, "ловит комету",
            "Мимо летит комета; космонавт тянет руку и ловит её, держит мерцающую звёздочку, а потом отпускает лететь дальше."),
    )

    /** A comet: a bright head and a tail of three fading behind it ([dir] -1: the tail to the left), all at [f] of full. */
    private fun comet(c: Cv, x: Int, y: Int, dir: Int, f: Float) {
        c.put(x, y, f); c.put(x + dir, y, 0.7f * f); c.put(x + 2 * dir, y, 0.45f * f); c.put(x + 3 * dir, y, 0.3f * f)
    }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val f = FORMS[st]
        when (col) {
            "jumping_jacks" -> {
                // Jumping jacks: arms up and legs apart, a hop, back, again.
                val up = t in (PEDGE + 600)..(ms - PEDGE - 700) && ((t - PEDGE - 600) / 330) % 2 == 0L
                val p = float(st, "calm", ct, 0f, hy = HOME[st] - (if (up) 1f else 0f), rest = if (up) JACK else REST, sway = 0.05f)
                figure(c, st, ct, p.x, p.y, p.th, eyes = if (up) HAPPY else blinks(ct))
            }
            "catches_comet" -> {
                // A comet comes over the top; it reaches out and catches it: a twinkling star in its glove; then lets it go.
                val r = outOf(t, ms, 700)
                val p = float(st, "calm", ct, 0f, hx = mix(12f, 9f, r), hy = HOME[st] + 2f * r)
                val reach = min(ease(seg(t, 1300, 1900)), 1f - ease(seg(t, 3300, 3800)))
                val sx = p.x + f.armX.toFloat(); val sy = p.y + (f.armY - f.cr).toFloat()
                figure(c, st, ct, p.x, p.y, p.th, eyes = if (t in 1900L until 3300L) HAPPY else blinks(ct), look = if (reach > 0f) 1 else 0,
                    reach = if (reach > 0f) mix(sx + 1f, 17f, reach) to mix(sy + 1f, 9f, reach) else null)
                when {
                    t in 1000L until 1900L -> { val q = seg(t, 1000, 1900); comet(c, mix(8f, 17f, q).roundToInt(), (2f + 5f * q * q).roundToInt(), -1, seg(t, 1000, 1150)) }
                    t in 1900L until 3300L -> {
                        val tw = 0.5f + 0.3f * sin((t - 1900) / 120f)
                        c.put(18, 7, 1f); c.put(17, 7, tw); c.put(19, 7, tw); c.put(18, 6, tw); c.put(18, 8, tw)
                    }
                    t in 3300L until 4000L -> { val g = 1f - seg(t, 3750, 4000); if (g > 0.15f) { val q = seg(t, 3300, 4000); comet(c, mix(17f, 7f, q).roundToInt(), mix(7f, 3f, q).roundToInt(), 1, g) } }
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val f = FORMS[st]
        val home = HOME[st]
        when (col) {
            "calm" -> { val p = float(st, col, t, tilt); figure(c, st, t, p.x, p.y, p.th, eyes = feltEyes() ?: blinks(t), look = feltLook()) }
            "tilt" -> { val p = float(st, col, t, tilt); figure(c, st, t, p.x, p.y, p.th, eyes = feltEyes() ?: blinks(t), look = (tilt * 1.4f).roundToInt().coerceIn(-1, 1)) }
            "happy" -> { val p = float(st, col, t, tilt, rest = floatArrayOf(1.5f, 1.5f, 0.6f, 0.6f)); figure(c, st, t, p.x, p.y + 2, p.th, q = ((t / 300) % 4).toInt(), eyes = HAPPY) }
            "habit" -> {
                // Two puffs of its backpack: one sends it across, the other back.
                val dir = if (t in 500 until 900) 1 else if (t in 1300 until 1700) -1 else 0
                val p = float(st, col, t, tilt, hx = if (t in 500 until 1300) 7f else if (t in 1300 until 2300) 17f else 12f)
                figure(c, st, t, p.x, p.y, p.th, eyes = if (t in 500 until 2300) HAPPY else blinks(t))
                if (dir != 0) for (i in 1..4) { c.put(p.x + dir * (f.half + i), p.y + 3, 1f - i * 0.18f); if (i < 3) { c.put(p.x + dir * (f.half + i), p.y + 2, 0.5f); c.put(p.x + dir * (f.half + i), p.y + 4, 0.5f) } }
            }
            "signature" -> {
                // Off the tether: a loop round the sky on its jetpack, the flame at its feet.
                val a = seg(t, 600, 5600) * 2 * PI_F
                val p = float(st, col, t, tilt, hx = 12f + sin(a) * 5f, hy = home - 1f + (1f - cos(a)) * 2f, rest = floatArrayOf(2.6f, 2.6f, 0.2f, 0.2f), sway = 0.05f)
                figure(c, st, t, p.x, p.y, p.th, eyes = HAPPY, tether = LOOSE)
                if (t in 600 until 5600) for (k in 0..2) c.put(p.x, p.y + f.rows.size - f.cr + k, if ((t / 80 + k) % 2 == 0L) 1f else 0.5f)
            }
            "hungry" -> {
                val p = float(st, col, t, tilt, hx = 9f, rest = floatArrayOf(0.5f, 1.75f, 0.3f, 0.4f))
                figure(c, st, t, p.x, p.y, p.th, eyes = feltEyes() ?: blinks(t), look = 1)
                tube(c, min(19, p.x + f.armX + f.len + 2 + sin(t / 3200f * 2 * PI_F * 2).roundToInt()), p.y + 1 + cos(t / 3200f * 2 * PI_F).roundToInt())
            }
            "sad" -> { val p = float(st, col, t, tilt, hx = 12f + 2f * sin(t / 3200f * 2 * PI_F), hy = home + 3f, rest = LIMP, sway = 0.05f); figure(c, st, t, p.x, p.y, p.th, eyes = CLOSED, visor = 0.6f, tether = SNAPPED) }
            "tired" -> {
                val p = float(st, col, t, tilt, hy = home + 2.5f * seg(t, 0, 2600), rest = LIMP, sway = 0.06f)
                figure(c, st, t, p.x, p.y, p.th, eyes = if (t < 2600) CLOSED else WIDE, visor = if (t < 2600) 0.55f + 0.12f * sin(t / 200f) else 0.85f)
            }
            "asleep" -> {
                val p = float(st, col, t, tilt, hx = 13f, hy = home + 3f, rest = floatArrayOf(0.2f, 0.2f, 1f, 0.1f), sway = 0.05f)
                figure(c, st, t, p.x, p.y, p.th, q = 1, eyes = if (DrawnFeel.live && DrawnFeel.peek) PEEK else CLOSED, visor = 0.6f, tuck = true)
            }
            "eating" -> {
                // It catches the tube, brings it to its helmet, and cheers.
                val rest = when { t < 900 -> floatArrayOf(0.5f, 1.9f, 0.3f, 0.3f); t < 2400 -> floatArrayOf(0.5f, 2.9f, 0.3f, 0.3f); else -> floatArrayOf(1.6f, 1.6f, 0.4f, 0.4f) }
                val p = float(st, col, t, tilt, hx = 9f, rest = rest)
                figure(c, st, t, p.x, p.y, p.th, eyes = if (t >= 2400) HAPPY else OPEN, look = if (t < 2400) 1 else 0)
                if (t < 2400) { val g = ease(seg(t, 200, 900)); tube(c, mix(min(19f, p.x + f.armX + f.len + 2f), p.x + f.half + 2f, g).roundToInt(), mix(p.y + 1f, p.y.toFloat(), g).roundToInt()) }
            }
            "game" -> { val p = float(0, col, t, tilt, hx = 12f + tilt * 6f, hy = 11f); figure(c, 0, t, p.x, p.y, p.th, tether = LOOSE) }
            "loved" -> { val p = float(st, col, t, tilt, rest = floatArrayOf(0.6f, 2.4f + 0.5f * sin(t / 90f), 0.4f, 0.4f)); figure(c, st, t, p.x, p.y, p.th, eyes = HAPPY) }
            "dizzy" -> {
                val k = 1f - seg(t, 1500, 2300)
                val p = float(st, col, t, tilt, rest = floatArrayOf(1.9f, 1.9f, 0.8f, 0.8f))
                figure(c, st, t, p.x, p.y + 2, p.th, q = if (k <= 0f) 0 else ((t / 1000f * 9f * k).toInt() % 4), eyes = if (k <= 0f) OPEN else WIDE)
            }
            "charging" -> {
                val g = seg(t, 0, 2900)
                val p = float(st, col, t, tilt, rest = floatArrayOf(0.9f, 0.9f, 0.3f, 0.3f))
                figure(c, st, t, p.x, p.y, p.th, eyes = if (t > 2900) HAPPY else blinks(t), visor = 0.35f + 0.6f * g, charge = true)
            }
            // Typing: each key a burst of its jetpack; a planet rises from below as it flies to it, closer and closer, and
            // after 40 s it lands and plants its flag.
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                val pr = 2f + 9f * g; val py = 24f + pr * 0.35f
                c.disc(12f, py, pr, 0.2f); c.ring(12f, py, pr, 0.45f, 0.6f); if (pr > 5f) { c.put(9f, py - pr * 0.6f, 0.08f); c.put(15f, py - pr * 0.4f, 0.08f) }
                val land = if (y.stage == 2) ease(y.stageMs / 1500f) else 0f
                val feet = f.rows.size - f.cr + f.len
                val planted = y.stage == 2 && y.stageMs > 1500
                val p = float(st, col, t, tilt, hy = mix(home - 2f, py - pr - feet, land), rest = if (planted) floatArrayOf(0.6f, 2.5f, 0.3f, 0.3f) else REST)
                figure(c, st, t, p.x, p.y, p.th, eyes = if (planted) HAPPY else blinks(t), visor = 0.55f + 0.3f * y.hit(220), tether = LOOSE)
                if (land < 1f && (y.hit(180) > 0f || y.rush)) for (k in 0..2) c.put(p.x, p.y + f.rows.size - f.cr + k, if (k == 0) 1f else 0.5f)
                if (planted) { val q = seg(y.stageMs, 1500, 2300); c.line(19f, py - pr + 1, 19f, py - pr + 1 - q * 6, 0.45f); if (q >= 1f) for (k in 0..2) c.put(20f + k, py - pr - 5 + (if ((t / 200 + k) % 2 == 0L) 0f else 1f), 0.75f) } }
            "grow" -> {
                val o = seg(t, 1200, 2600)
                c.oval(12f, 12f, 5f, 6f, 0.3f, 0.5f)
                if (o > 0) for (y in 7..17) for (x in (12 - o * 4).roundToInt()..(12 + o * 4).roundToInt()) c.cut(x, y)
                if (t > 2600) { val now = if (st == 0 || t > 4400) st else st - 1; val p = float(now, col, t, tilt, hy = mix(12f, HOME[now], seg(t, 2600, 4200))); figure(c, now, t, p.x, p.y, p.th, tether = LOOSE) }
            }
        }
    }

    /** The little Cadet for its toy: off the tether, its helmet's middle at [x], [y], its limbs as the toy flings them. */
    fun cadet(c: Cv, t: Long, x: Int, y: Int, th: FloatArray, wide: Boolean) = figure(c, 0, t, x, y, th, eyes = if (wide) WIDE else blinks(t), tether = LOOSE)
}

/** The astronaut's little Cadet, for its toy ([Astronaut.cadet]). */
internal fun astroCadet(c: Cv, t: Long, x: Int, y: Int, th: FloatArray, wide: Boolean) = Astronaut.cadet(c, t, x, y, th, wide)

// --- kodama ----------------------------------------------------------------------------------------------------------------

private object Kodama : EggHero(
    "kodama", listOf("Sprout", "Kodama", "Grove"), Rarity.LEGENDARY,
    "Лесные духи: их становится больше, когда вокруг тихо",
    mapOf("calm" to "головы щёлкают набок", "happy" to "трещат, подпрыгивая", "habit" to "выглядывает ещё один", "signature" to "лес светится",
        "hungry" to "ловит каплю сока", "sad" to "листья падают", "tired" to "становятся прозрачными", "asleep" to "сидят в светлячках",
        "eating" to "пьют росу", "game" to "тсс — считай духов", "loved" to "тянутся друг к другу", "dizzy" to "головы трещат по кругу",
        "tilt" to "наклоняют головы", "charging" to "стволы наливаются светом", "grow" to "семечко стало духом"),
) {
    private val ONE = listOf("..###..", ".#####.", "##-##-#", "#######", "###-###", ".#####.", "..#.#..", "..###..", "..#.#..")
    private fun spots(st: Int) = when (st) { 0 -> listOf(12f to 19f); 1 -> listOf(8f to 19f, 16f to 17f); else -> listOf(12f to 20f, 6f to 16f, 18f to 16f) }
    private fun trunks(c: Cv, glow: Float = 0f) { for (x in intArrayOf(3, 21)) for (y in 2..22) c.put(x, y, if ((y + x) % 4 == 0) 0.12f else 0.08f + glow) }
    private fun one(c: Cv, x: Float, bottom: Float, tilt: Int = 0, b: Float = 0.18f, eyes: Boolean = true, dy: Float = 0f) {
        val rows = ONE.mapIndexed { y, r -> var row = r.replace('#', 'o'); if (!eyes) row = row.replace('-', 'o').let { if (y == 2) it.replaceRange(2, 3, "-").replaceRange(5, 6, "-") else it }; if (y < 3 && tilt != 0) (if (tilt > 0) ".${row.dropLast(1)}" else "${row.drop(1)}.") else row }
        c.spr(rows, x, bottom + dy, b)
    }
    private fun all(c: Cv, st: Int, t: Long, b: Float = 0.18f, tiltFor: (Int) -> Int = { 0 }, dy: (Int) -> Float = { 0f }, eyes: Boolean = true) =
        spots(st).forEachIndexed { i, (x, y) -> one(c, x, y, tiltFor(i), b, eyes, dy(i)) }
    override val pastimes = listOf(
        DrawnPastime("pass_leaf", 5600, "передают листик",
            "С дерева падает листик на голову левому духу, и духи перекидывают его с головы на голову, щёлкая головами; под конец листик тает."),
        DrawnPastime("climb_shoulders", 5200, "залез на плечи",
            "Левый дух запрыгивает на голову среднему, оглядывается по сторонам и подпрыгивает от радости, правый косится на него; потом спрыгивает на место."),
    )

    private fun calmTilt(i: Int, ct: Long) = if ((ct + i * 900) % 2400 in 900..1500) (if (i % 2 == 0) 1 else -1) else 0
    /** Where a spirit's head is: its middle column and its top row, for a spirit at [x] standing on [bottom], head clicked [tilt]. */
    private fun head(x: Float, bottom: Float, tilt: Int) = ((x - ONE[0].length / 2f).roundToInt() + 3 + tilt) to (bottom.roundToInt() - ONE.size + 1)
    /** A leaf: two by two, slanted, [x] its middle column, [y] its bottom row. */
    private fun leaf(c: Cv, x: Int, y: Int, b: Float) { c.put(x, y - 1, b); c.put(x + 1, y - 1, b); c.put(x - 1, y, b); c.put(x, y, b) }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (st < 2) { calmNow(c, t, st); return }
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val sp = spots(st)
        trunks(c)
        when (col) {
            "pass_leaf" -> {
                // A leaf drifts down onto the left spirit; it flicks it to the middle one, that one to the right, and back to
                // the middle, each throw a click of the head toward the catcher; then all click for joy and the leaf fades.
                val tosses = listOf(Triple(1, 0, 1800L), Triple(0, 2, 2600L), Triple(2, 0, 3400L))
                val tilts = IntArray(3)
                for ((a, b, s0) in tosses) if (t in (s0 - 150)..(s0 + 100)) tilts[a] = if (sp[b].first > sp[a].first) 1 else -1
                val joy = t in 3900L..4500L
                for (i in 0..2) {
                    if (joy) tilts[i] = if (((t / 160) + i) % 2 == 0L) 1 else -1
                    else if (t < 1000 || t > 4500) tilts[i] = calmTilt(i, ct)
                }
                for (i in 0..2) one(c, sp[i].first, sp[i].second, tilts[i])
                fun on(i: Int): Pair<Float, Float> { val (hx, top) = head(sp[i].first, sp[i].second, tilts[i]); return hx.toFloat() to (top - 1).toFloat() }
                val toss = tosses.firstOrNull { t in it.third until it.third + 500 }
                val (lx, ly) = when {
                    t < 1500 -> { val p = seg(t, 680, 1500); val (hx, hy) = on(1); (hx + (if ((t / 200) % 2 == 0L) 1 else 0)) to mix(4f, hy, p) }
                    toss != null -> { val p = seg(t, toss.third, toss.third + 500); val (ax, ay) = on(toss.first); val (bx, by) = on(toss.second)
                        mix(ax, bx, p) to mix(ay, by, p) - 4f * sin(p * PI_F) }
                    else -> on(tosses.lastOrNull { t >= it.third }?.second ?: 1)
                }
                val f = seg(t, 680, 830) * (1f - seg(t, 4100, 4500))
                if (f > 0.1f) leaf(c, lx.roundToInt(), ly.roundToInt(), 0.4f * f)
            }
            "climb_shoulders" -> {
                // The left spirit hops onto the middle one's head, looks left and right, bounces for joy, hops back down;
                // the right one turns its head to watch.
                val upP = seg(t, 780, 1380); val downP = seg(t, 3820, 4420)
                val (x, bottom) = when {
                    t < 1380 -> mix(sp[1].first, sp[0].first, upP) to mix(sp[1].second, sp[0].second - 9f, upP) - 3f * sin(upP * PI_F)
                    t < 3820 -> sp[0].first to sp[0].second - 9f - (if (t in 2800L..2950L || t in 3100L..3250L) 1f else 0f)
                    else -> mix(sp[0].first, sp[1].first, downP) to mix(sp[0].second - 9f, sp[1].second, downP) - 3f * sin(downP * PI_F)
                }
                val busy = t in 780L..4420L
                val look = when (t) { in 1600L..2000L -> -1; in 2200L..2600L -> 1; else -> 0 }
                one(c, sp[0].first, sp[0].second, if (busy) 0 else calmTilt(0, ct))
                one(c, sp[2].first, sp[2].second, if (t in 1500L..3600L) -1 else if (busy) 0 else calmTilt(2, ct))
                one(c, x, bottom, if (busy) look else calmTilt(1, ct))
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        when (col) {
            "calm" -> { trunks(c); all(c, st, t, tiltFor = { i -> if ((t + i * 900) % 2400 in 900..1500) (if (i % 2 == 0) 1 else -1) else 0 }) }
            "happy" -> { trunks(c); all(c, st, t, tiltFor = { i -> if (((t / 120) + i) % 2 == 0L) 1 else -1 }, dy = { i -> -hop(t + i * 150, 300f, 2f) }) }
            "habit" -> { trunks(c); all(c, st, t); val p = sin(seg(t, 300, 2900) * PI_F); if (p > 0.1f) one(c, 21f - p * 3, 12f, 1, 0.18f * p) }
            "signature" -> { val p = sin(seg(t, 0, 6000) * PI_F); trunks(c, glow = 0.3f * p); for (i in 0 until (p * 12).toInt()) c.put(2 + rnd(i, 5) * 20, 4 + rnd(i, 6) * 16, 0.6f + 0.4f * sin(t / 120f + i)); all(c, st, t, b = 0.18f + 0.1f * p) }
            "hungry" -> { trunks(c); val ph = (t % 1600) / 1600f; c.put(12f, 2f + ph * 5, 0.7f); all(c, st, t, dy = { -1f }) }
            "sad" -> { trunks(c); for (i in 0 until 4) { val ph = ((t + i * 700) % 2800) / 2800f; c.put(4 + i * 5 + sin(ph * 6) , ph * 22, 0.3f) }; all(c, st, t, b = 0.11f, tiltFor = { 1 }) }
            "tired" -> { trunks(c); all(c, st, t, b = 0.07f + 0.06f * (0.5f + 0.5f * sin(t / 600f))) }
            "asleep" -> { trunks(c); all(c, st, t, b = 0.12f, eyes = false, dy = { 1f }); for (i in 0 until 4) { val a = t / 1500f + i * 1.6f; c.put(12 + cos(a) * 8, 8 + sin(a * 1.3f) * 4, 0.6f) } }
            "eating" -> { trunks(c); val y = mix(1f, 9f, seg(t, 0, 1200)); if (t < 1300) c.put(12f, y, 0.8f); all(c, st, t, b = if (t in 1300..2200) 0.28f else 0.18f) }
            "game" -> { trunks(c); val n = if (t < 4600) 1 + (t / 700).toInt() else 0; for (i in 0 until n) one(c, 5f + (i * 7) % 15, 22f - (i / 3) * 6f, 0, 0.18f); if (t in 4600..5000) c.fill(0.05f) }
            "loved" -> { trunks(c); all(c, st, t, b = 0.22f + 0.06f * sin(t / 250f), tiltFor = { i -> if (spots(st)[i].first < 12) 1 else -1 }) }
            "dizzy" -> { trunks(c); all(c, st, t, tiltFor = { i -> if (((t / 80) + i) % 2 == 0L) 1 else -1 }, dy = { i -> sin(t / 100f + i) }) }
            "tilt" -> { trunks(c); val d = if (tilt > 0.3f) 1 else if (tilt < -0.3f) -1 else 0; spots(st).forEach { (x, y) -> one(c, x + tilt * 2, y, d) } }
            "charging" -> { val p = seg(t, 0, 2900); for (x in intArrayOf(3, 21)) for (y in 2..22) c.put(x, y, if (y > 22 - p * 20) 0.45f else 0.08f); all(c, st, t, b = 0.14f + 0.08f * p) }
            // Typing: each key one head clicks aside; a new spirit peeks out at 20 s and another at 40 s, and
            // after 40 s the forest glows.
            "typing" -> { val y = DrawnTyping
                val glow = if (y.stage == 2) 0.25f * ease(y.stageMs / 1500f) else 0f; trunks(c, glow)
                val spots = listOf(12f to 21f, 6f to 17f, 18f to 17f)
                val n = (1 + y.typedS / 20f).toInt().coerceIn(1, 3)
                val newest = ((y.typedS - (n - 1) * 20f) / 0.6f).coerceIn(0f, 1f)
                for (i in 0 until n) { val (x, b0) = spots[i]
                    val tl = when { y.rush -> if (((t / 120) + i) % 2 == 0L) 1 else -1; y.hit(170) > 0f && y.key % n == i -> if ((y.key / n) % 2 == 0) 1 else -1; else -> 0 }
                    one(c, x, b0, tilt = tl, b = 0.18f * (if (i == n - 1) newest else 1f), dy = if (i == n - 1) (1 - newest) * 3 else 0f) } }
            "grow" -> { trunks(c); if (t < 2400) { c.put(12f, 20f, 0.5f); if (t > 1200) c.put(12f, 19f, 0.6f) } else { val p = ease(seg(t, 2400, 4400)); one(c, 12f, 20f, 0, 0.18f * p) } }
        }
    }
}

// --- kraken ----------------------------------------------------------------------------------------------------------------

private object Kraken : EggHero(
    "kraken", listOf("Squidling", "Kraken", "Leviathan"), Rarity.LEGENDARY,
    "Кракен: живёт под краем матрицы, наружу только щупальца и глаз",
    mapOf("calm" to "щупальца из воды", "happy" to "плещется", "habit" to "щупальце закручивается", "signature" to "всплывает целиком",
        "hungry" to "тянется за рыбкой", "sad" to "щупальца поникли", "tired" to "уходит под воду", "asleep" to "глаз и пузырьки",
        "eating" to "утаскивает рыбку", "game" to "топит кораблики", "loved" to "щупальца аркой", "dizzy" to "водоворот",
        "tilt" to "вода и щупальца кренятся", "charging" to "кончики светятся", "grow" to "кальмарчик всплыл"),
) {
    /** Where the sea is at column [x]: a swell every [period] ms (whole swells per clip, so no clip jumps where it loops); flat when 0. */
    private fun seaY(x: Int, t: Long, level: Float, slope: Float, period: Float) =
        level + (if (period > 0f) sin(x * 0.7f + TAU * t / period) * 0.6f else 0f) + (x - 12) * slope
    private fun sea(c: Cv, t: Long, level: Float = 17f, slope: Float = 0f, period: Float = 1600f) {
        for (x in 0..24) c.put(x.toFloat(), seaY(x, t, level, slope, period), 0.25f)
    }

    // The arms are lit tubes: thick at the water, thin at the tip, a wave running up them and the tip coiling.

    private val LEN = floatArrayOf(9.5f, 10.5f, 11.5f)
    private val R0 = floatArrayOf(1.25f, 1.45f, 1.6f)

    /** How long an arm [h] rows tall (the old one-LED arms' height) is at stage [st]: raised arms grow cell for cell. */
    private fun len(h: Float, st: Int) = if (h <= 9f) h / 9f * LEN[st] else LEN[st] + (h - 9f)
    /** An arm raised above its usual 9 in a pastime straightens up as it goes: how much of its sway it keeps. */
    private fun keep(h: Float) = 1f - 0.75f * ((h - 9f) / 4f).coerceIn(0f, 1f)

    /** A spine from ([bx], [root]): [len] cells, leaning [lean] rad, a wave [amp] running root to tip at [phase], the tip curled [curl] rad. */
    private fun spine(bx: Float, root: Float, len: Float, lean: Float, phase: Float, amp: Float, curl: Float) =
        Tubes.spine(bx, root, len, -PI_F / 2 + lean, phase, amp, curl)

    /** Arm [k] of [n]: rising from [level], [hh] tall, its wave and coil once every [period] ms; [curled] rolls it up tight. */
    private fun arm(k: Int, n: Int, st: Int, t: Long, hh: Float, lean: Float, curled: Boolean, level: Float, period: Float, pastime: Boolean, droop: Float = 0f): Tube {
        val gap = (if (n >= 4) 15f else 17f) / n
        val bx = 12f + (k - (n - 1) / 2f) * gap
        val side = if (k < (n - 1) / 2f) -1f else 1f
        val calm = if (pastime) keep(hh) else 1f
        val outer = if (n == 4 && (k == 0 || k == 3)) 0.8f else 1f   // the grown-up's outer arms stay inside the rim
        val w = TAU * t / period
        val coil = if (curled) side * (2.4f + 0.55f * st + (1f + 0.25f * st) * sin(w)) else side * ((0.5f + 1.1f * (0.5f + 0.5f * sin(w + k * 2.1f))) * calm + droop)
        return Tube(spine(bx, level + 1, len(hh, st) * outer, side * 0.1f + lean * 0.45f, w + k * 1.7f, 0.4f * calm, coil), min(R0[st], gap * 0.43f), 0.75f)
    }

    /** Draws [arms] as lit tubes ([Tubes]), nothing at or under the water ([water] for a column); [b] dims them, [tip] lights their tips. */
    private fun paint(c: Cv, arms: List<Tube>, water: (Int) -> Float, b: Float = 1f, tip: Float? = null) {
        Tubes.paint(c, arms, b = b, clip = { x, y -> y < water(x) - 0.5f }, shade = tip?.let { tp -> { s: Float, v: Float -> if (s > 0.8f) tp else v } })
    }

    /**
     * The arms out of the sea: [n] of them, [h] tall (or [hk] each, a raised one straightening as it goes), swaying with a
     * wave that runs up them once every [period] ms; [curl] is one arm rolled up tight, [lean] bends them all, [droop] hangs
     * their tips down. Cut at the
     * sea drawn with the same [slope] and [swell] (0: flat at [level]). [extra] arms are drawn last, in front.
     */
    private fun arms(
        c: Cv, st: Int, t: Long, h: Float = 9f, lean: Float = 0f, curl: Int = -1, tip: Float? = null, b: Float = 1f, level: Float = 17f,
        n: Int = 2 + st, period: Float = 3200f, hk: ((Int) -> Float)? = null, slope: Float = 0f, swell: Float = 1600f, extra: List<Tube> = emptyList(), droop: Float = 0f,
    ) {
        val order = (0 until n).sortedByDescending { abs(it - (n - 1) / 2f) }   // the outer ones behind, the middle in front
        val list = order.map { k -> arm(k, n, st, t, hk?.invoke(k) ?: h, lean, k == curl, level, period, hk != null, droop) } + extra
        paint(c, list, { x -> seaY(x, t, level, slope, swell) }, b, tip)
    }

    /** The tip of arm [k] of [n] at height [h] in a pastime (calm sway): where a thrown shell lands. */
    private fun tipOf(k: Int, n: Int, st: Int, t: Long, h: Int): Pair<Float, Float> {
        val p = arm(k, n, st, t, h.toFloat(), 0f, false, 17f, 3200f, true).pts
        return p[p.size - 2] to p[p.size - 1]
    }
    private fun eye(c: Cv, x: Float, y: Float, open: Boolean = true, r: Float = 2.5f, look: Int = 0) { c.oval(x, y, r, r * 0.6f, 0.6f); if (open) c.cut(x + look, y) else c.line(x - 1, y, x + 1, y, 0.2f) }
    override val pastimes = listOf(
        DrawnPastime("juggles_shell", 5600, "жонглирует ракушкой",
            "Достаёт со дна ракушку и перекидывает её с щупальца на щупальце, следя за ней глазом; потом роняет обратно в воду."),
        DrawnPastime("arm_wave", 4800, "пускает волну щупальцами",
            "Щупальца поднимаются по очереди, как волна на стадионе: слева направо, обратно и ещё раз; глаз довольно жмурится."),
        DrawnPastime("unfurls", 6400, "разворачивает щупальце",
            "Из воды поднимается щупальце, свёрнутое, как лист папоротника, медленно разворачивается во весь рост и качается, по нему бегут волны; потом сворачивается обратно и уходит под воду."),
    )

    /** A shell: a fan on its base, [x] its middle, [y] its bottom row. */
    private fun shell(c: Cv, x: Int, y: Int) { c.put(x, y - 1, 0.8f); for (d in -1..1) c.put(x + d, y, 0.8f) }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val n = 2 + st
        val eyeOn = (ct % 3200 in 800..2400) || t in 600L..(ms - PEDGE - 300)
        sea(c, ct)
        when (col) {
            "juggles_shell" -> {
                // The first arm dips under and comes up with a shell; it flies from tip to tip along the arms, back to the
                // first, which takes it down again. Each throw a flick of the thrower, each catch a give of the catcher.
                val tosses = listOf(Triple(0, 1, 1600L), Triple(1, 2, 2200L), Triple(2, 3, 2800L), Triple(3, 0, 3400L)).filter { it.first < n && it.second < n }
                fun h(k: Int): Int {
                    var v = 9f
                    if (k == 0) v -= 6f * (bump(t, 680, 1480) + bump(t, 4000, 4800))
                    for ((a, b, s0) in tosses) { if (k == a) v += 2f * bump(t, s0 - 100, s0 + 150); if (k == b) v -= 1f * bump(t, s0 + 400, s0 + 650) }
                    return v.roundToInt()
                }
                arms(c, st, ct, hk = { h(it).toFloat() })
                val toss = tosses.firstOrNull { t in it.third until it.third + 450 }
                val at: Pair<Float, Float>? = when {
                    t < 1080 || t >= 4400 -> null
                    toss != null -> { val p = seg(t, toss.third, toss.third + 450); val (ax, ay) = tipOf(toss.first, n, st, ct, h(toss.first)); val (bx, by) = tipOf(toss.second, n, st, ct, h(toss.second))
                        mix(ax, bx, p) to mix(ay, by, p) - 4f * sin(p * PI_F) }
                    else -> { val k = tosses.lastOrNull { t >= it.third }?.second ?: 0; tipOf(k, n, st, ct, h(k)) }
                }
                if (at != null) shell(c, at.first.roundToInt(), at.second.roundToInt() - 1)
                val look = at?.let { if (it.first < 9f) -1 else if (it.first > 15f) 1 else 0 } ?: 0
                if (eyeOn) eye(c, 12f, 19f, look = look)
            }
            "arm_wave" -> {
                // Three waves along the arms: left to right, back, and left to right again; the eye follows the crest and
                // screws up happily at the end.
                fun up(k: Int): Float {
                    val s1 = PEDGE + 300L + k * 200L; val s2 = PEDGE + 1500L + (n - 1 - k) * 200L; val s3 = PEDGE + 2700L + k * 200L
                    return bump(t, s1, s1 + 500) + bump(t, s2, s2 + 500) + bump(t, s3, s3 + 500)
                }
                arms(c, st, ct, hk = { 9f + (4f * up(it)).roundToInt() })
                val top = (0 until n).maxByOrNull { up(it) } ?: 0
                val bx = 12f + (top - (n - 1) / 2f) * (16f / n)
                val look = if (up(top) < 0.2f) 0 else if (bx < 10f) -1 else if (bx > 14f) 1 else 0
                if (eyeOn) eye(c, 12f, 19f, open = t !in (PEDGE + 3500)..(PEDGE + 3800), look = look)
            }
            "unfurls" -> {
                // The middle arm (the teen's own sinks out of its way) rises rolled up like a fern frond, unrolls to its
                // full height, sways with two waves running up it, then rolls up again and sinks; the eye watches it.
                val mid = if (n % 2 == 1) (n - 1) / 2 else -1
                val sink = ease(seg(t, PEDGE, 1000)) * (1f - ease(seg(t, 5400, ms - PEDGE)))
                val grow = ease(seg(t, 900, 2600)) * (1f - ease(seg(t, 4700, 5500)))
                val roll = 5f * (1f - ease(seg(t, 1100, 2700))) + 5f * ease(seg(t, 4300, 5300))
                val amp = 0.5f * ease(seg(t, 2000, 2800)) * (1f - ease(seg(t, 4300, 4800)))
                val w = TAU * t / ms * 2
                val big = (len(9f, st) + 3f + st) * grow
                val fern = if (big > 0.8f) listOf(Tube(spine(12f, 18f, big, 0f, w, amp, roll + 1.2f + 0.8f * sin(w)), R0[st] + 0.5f, 0.8f)) else emptyList()
                arms(c, st, ct, hk = { if (it == mid) 9f * (1f - sink) else 9f }, extra = fern)
                if (eyeOn) eye(c, 12f, 19f, open = t in 2600L..4600L)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        when (col) {
            "calm" -> { sea(c, t); arms(c, st, t); if (t % 3200 in 800..2400) eye(c, 12f, 19f) }
            "happy" -> { sea(c, t, period = 2400f); arms(c, st, t, h = 9f + hop(t, 400f, 3f), period = 800f, swell = 2400f); for (i in 0 until 4) c.put(4f + i * 5, 13f - hop(t + i * 100, 400f, 3f), 0.7f) }
            "habit" -> { sea(c, t); arms(c, st, t, curl = 0); eye(c, 12f, 19f) }
            "signature" -> { val p = sin(seg(t, 0, 6000) * PI_F); val lvl = 22f - p * 8; c.oval(12f, lvl + 3, 6f, 5f, 0.3f, 0.45f)
                arms(c, 2, t, h = 6f + p * 8, level = lvl + 6, n = 6, period = 6400f / 7, swell = 0f); eye(c, 12f, lvl + 2, r = 2.5f + p); sea(c, t, 18f) }
            "hungry" -> { sea(c, t); val fx = (t / 20f) % 30 - 3; c.rows(listOf(".#o", "###", ".#."), fx.roundToInt(), 8, 0.5f); arms(c, st, t, lean = ((fx - 12) / 12f).coerceIn(-1f, 1f)); eye(c, 12f, 19f) }
            "sad" -> { rain(c, t, 6, 0.25f); sea(c, t); arms(c, st, t, h = 7f, b = 0.7f, droop = 1.8f); eye(c, 12f, 19f, open = false) }
            "tired" -> { sea(c, t); arms(c, st, t, h = 9f * (1 - seg(t, 0, 3000) * 0.6f)) }
            "asleep" -> { sea(c, t, period = 4000f); eye(c, 12f, 18.5f, open = false); for (i in 0..2) { val ph = ((t + i * 600) % 1800) / 1800f; c.ring(14f + i, 16f - ph * 10, 0.6f, 0.3f * (1 - ph)) } }
            "eating" -> { sea(c, t); val fx = 16f; val pull = seg(t, 1000, 2000); if (t < 2000) c.rows(listOf(".#o", "###"), fx.roundToInt(), (9 + pull * 8).roundToInt(), 0.5f); arms(c, st, t, lean = if (t < 2000) 0.6f else 0f); if (t in 2000..2400) for (k in -2..2) c.put(fx + k, 15f - abs(k), 0.7f); eye(c, 12f, 19f) }
            "game" -> { sea(c, t, 15f); for (i in 0 until 3) { val sx = (t / 40f + i * 9) % 28 - 2; c.rows(listOf("..#..", "#####", ".###."), sx.roundToInt() - 2, 12, 0.45f) }
                val k = ((tilt + 1) / 2 * 2.99f).toInt(); val strike = (t % 1000) < 300
                paint(c, listOf(Tube(spine(5f + k * 7, 17f, if (strike) 10f else 3f, 0f, TAU * t / 1000f, 0.3f, 0.6f), 1.2f, 0.75f)), { 16.5f }); eye(c, 12f, 19f) }
            "loved" -> { sea(c, t, period = 2400f)
                // Two arms meet over the eye in an arch.
                fun half(sign: Float) = FloatArray(42) { i -> val a = PI_F / 2 * (i / 2) / 20f; if (i % 2 == 0) 12 - sign * cos(a) * 7 else 17 - sin(a) * 9 }
                paint(c, listOf(Tube(half(1f), R0[st], 0.75f), Tube(half(-1f), R0[st], 0.75f)), { x -> seaY(x, t, 17f, 0f, 2400f) }); eye(c, 12f, 19f) }
            "dizzy" -> {
                // Four arms swirled into a whirlpool round the closed eye.
                val list = (0 until 4).map { k -> val a0 = TAU * t / 800f + k * PI_F / 2
                    Tube(FloatArray(202) { i -> val j = (i / 2) / 10f; val r = 2.2f + j * 0.9f; if (i % 2 == 0) 12 + cos(a0 + j * 0.3f) * r else 12 + sin(a0 + j * 0.3f) * r }, 1.1f, 0.75f) }
                paint(c, list, { 99f }); eye(c, 12f, 12f, open = false) }
            "tilt" -> { sea(c, t, slope = tilt * 0.2f); arms(c, st, t, lean = -tilt, slope = tilt * 0.2f) }
            "charging" -> { val p = seg(t, 0, 2900); sea(c, t); arms(c, st, t, tip = 0.3f + 0.7f * p); eye(c, 12f, 19f, open = p > 0.5f) }
            // Typing: each key an arm lashes up out of the sea with a splash; at 20 s its eye surfaces, and after 40 s
            // the whole kraken rises.
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { val p = ease(y.stageMs / 2000f); val lvl = 22f - p * 8; c.oval(12f, lvl + 3, 6f, 5f, 0.3f, 0.45f)
                    arms(c, 2, t, h = 6f + p * 6 + y.hit(200) * 3, level = lvl + 6, n = 6, period = if (y.rush) 600f else 900f, swell = 0f)
                    eye(c, 12f, lvl + 2, r = 2.5f + p); sea(c, t, 18f); return }
                sea(c, t); val n = 2 + st
                val hit = y.hit(240); val lash = y.key.coerceAtLeast(0) % n
                arms(c, st, t, period = if (y.rush) 750f else 1900f,
                    hk = { k -> (if (y.rush) 8f + hop(t, 300f, 2f) else 7f) + (if (hit > 0f && k == lash) 6f * hit else 0f) })
                if (hit > 0.6f) { val bx = 12f + (lash - (n - 1) / 2f) * ((if (n >= 4) 15f else 17f) / n); for (d in 2..3) { c.put(bx - d, 16f, 0.8f); c.put(bx + d, 16f, 0.8f) } }
                if (y.stage == 1) { val p = ease(y.stageMs / 1200f); eye(c, 12f, mix(21f, 19f, p), open = !(y.paused && blinkAt(t))) } }
            "grow" -> { sea(c, t); val p = seg(t, 1000, 5000); if (p > 0) arms(c, if (st == 0) 0 else st - 1, t, h = 2f + p * 7, n = 2 + (p * st).toInt()); c.oval(12f, 18f, 1.5f + p, 1f + p * 0.5f, 0.5f) }
        }
    }
}

// --- glitch ----------------------------------------------------------------------------------------------------------------

private object Glitch : EggHero(
    "glitch", listOf("Bit", "Glitch", "Kernel"), Rarity.MYTHIC,
    "Существо из строк развёртки: каждая строка на своей пружине и кренится вслед за телефоном",
    mapOf("calm" to "бежит развёртка", "happy" to "идёт волной", "habit" to "загрузка…", "signature" to "перезагрузка",
        "hungry" to "слабый сигнал", "sad" to "картинка провисла", "tired" to "низкое разрешение", "asleep" to "экран выключен",
        "eating" to "глотает байты", "game" to "чинит битые пиксели", "loved" to "глаза-сердечки", "dizzy" to "сбилась развёртка",
        "tilt" to "кренится как желе", "charging" to "заливается светом", "grow" to "собирается из строк"),
) {
    /** One stage's picture: rows '#' body, '-' a dark eye, 'o' bright; [b0]..[b1] the body's rows (the rest: antenna, legs). */
    private class Form(val x0: Int, val y0: Int, val b0: Int, val b1: Int, val eyes: IntArray, val er: Int, val eh: Int, val ew: Int, val mr: Int, val m0: Int, val m1: Int, val rows: List<String>) {
        val h = rows.size
        val w = rows[0].length
    }
    private val FORMS = listOf(
        Form(9, 11, 0, 5, intArrayOf(1, 5), 2, 1, 1, 4, 2, 4, listOf(
            ".#####.", "#######", "#-###-#", "#######", "##ooo##", ".#####.", "..#.#..")),
        Form(7, 8, 2, 9, intArrayOf(2, 8), 4, 2, 1, 7, 3, 7, listOf(
            ".....o.....", ".....#.....", ".#########.", "###########", "##-#####-##", "##-#####-##", "###########", "###ooooo###", "###########", ".#########.", "..##...##..")),
        Form(3, 7, 1, 11, intArrayOf(6, 11), 5, 2, 2, 8, 6, 12, listOf(
            ".........o.........", "..###############..", "..###############..", "###################", "..###############..", "..####--###--####..",
            "######--###--######", "..###############..", "..####ooooooo####..", "###################", "..###############..", "..###############..", ".....##.....##.....")),
    )
    const val OPEN = 0
    const val HAPPY = 1
    const val CLOSED = 2
    const val NONE = 3
    const val WIDE = 4
    const val HEART = 5
    const val CURSOR = 0
    const val SMILE = 1
    const val AGAPE = 2
    const val OFF = 3
    const val BAR = 4

    /**
     * The creature: a screen drawn row by row. [offs] slides each row sideways (its antenna and legs go with the body row
     * next to them), [src] says which of its rows a row shows (a rolling picture), [show] which rows are there at all.
     * [fill] lights it from the bottom up (0..1), [lowres] draws it in 2x2 blocks, [sag] droops its sides, [bar] is how
     * far the loading bar in its mouth has got.
     */
    fun body(
        c: Cv, st: Int, t: Long, eyes: Int = OPEN, mouth: Int = CURSOR, look: Int = 0, offs: IntArray? = null, src: IntArray? = null,
        show: BooleanArray? = null, dim: Float = 1f, hop: Int = 0, sag: Boolean = false, fill: Float = -1f, lowres: Boolean = false,
        bar: Float = 0f, dx: Int = 0, dy: Int = 0,
    ) {
        val f = FORMS[st]
        val grid = Array(f.h) { f.rows[it].toCharArray() }
        fun cut(col: Int, row: Int) { if (row in 0 until f.h && col in 0 until f.w && grid[row][col] == '#') grid[row][col] = '-' }
        for (ec in f.eyes) {
            for (j in 0 until f.eh) for (i in 0 until f.ew) grid[f.er + j][ec + i] = '#'
            val c0 = ec; val c1 = ec + f.ew - 1
            when (eyes) {
                OPEN -> for (j in 0 until f.eh) for (i in 0 until f.ew) cut(ec + i + look, f.er + j)
                WIDE -> for (j in -1 until f.eh) for (i in 0 until f.ew) cut(ec + i, f.er + j)
                CLOSED -> if (st == 0) grid[f.er][ec] = '=' else for (i in 0 until f.ew) cut(ec + i, f.er + f.eh - 1)
                HAPPY -> if (st == 0) cut(ec, f.er) else { for (k in c0..c1) cut(k, f.er); cut(c0 - 1, f.er + 1); cut(c1 + 1, f.er + 1) }
                HEART -> if (st == 0) cut(ec, f.er) else { cut(c0 - 1, f.er - 1); cut(c1 + 1, f.er - 1); for (k in c0 - 1..c1 + 1) cut(k, f.er); for (k in c0..c1) cut(k, f.er + 1) }
            }
        }
        for (col in f.m0..f.m1) grid[f.mr][col] = when (mouth) {
            CURSOR -> if ((t / 450) % 2 == 0L) 'o' else '#'
            SMILE -> 'o'
            AGAPE -> if (col > f.m0 && col < f.m1) '-' else '#'
            BAR -> if ((col - f.m0).toFloat() / (f.m1 - f.m0 + 1) < bar) 'o' else '='
            else -> '#'
        }
        if (st > 0 && (t / 620) % 2 == 0L) grid[0][f.w / 2] = '#'
        val scan = ((t / 110) % (f.h + 8)).toInt()
        for (i in 0 until f.h) {
            if (show != null && !show[i]) continue
            val si = src?.get(i) ?: i
            val o = offs?.get(i.coerceIn(f.b0, f.b1)) ?: 0
            val rb = (if (i % 2 == 1) 0.36f else 0.44f) + if (i == scan && !lowres) 0.3f else 0f
            val filled = fill > 0f && i >= f.h - 1 - (fill * (f.h - 1)).roundToInt()
            for (col in 0 until f.w) {
                var ch = grid[si][col]
                if (lowres) {
                    val r0 = si - Math.floorMod(si - f.b0, 2); val cc = col - col % 2
                    var lit = 0; var eye = false
                    for (r in r0..r0 + 1) for (k in cc..cc + 1) if (r in 0 until f.h && k < f.w && grid[r][k] != '.') { lit++; if (grid[r][k] == '-') eye = true }
                    ch = if (lit < 2) '.' else if (eye) '-' else '#'
                }
                if (ch == '.') continue
                val x = f.x0 + col + o + dx
                var y = f.y0 + i - hop + dy
                if (sag) { val q = (col - (f.w - 1) / 2f) / ((f.w - 1) / 2f); y += (q * q * (st + 1)).roundToInt() }
                c.set(x, y, when (ch) { '#' -> (if (filled) 0.8f else rb) * dim; 'o' -> if (dim < 1f) 0.8f * dim else 1f; '=' -> 0.16f * dim; else -> 0f })
            }
        }
    }

    // Every row leans after the phone on its own spring, the top the loosest: the picture rocks like jelly. Live it runs on
    // the engine's clock ([DrawnFeel]); in a clip on the clip's own time, starting settled each time the clip starts over.
    private var sKey = ""
    private var sAt = 0L
    private val sOff = FloatArray(16)
    private val sVel = FloatArray(16)

    @Synchronized
    private fun lean(st: Int, col: String, t: Long, tilt: Float): IntArray {
        val f = FORMS[st]
        val live = DrawnFeel.live
        val tx = if (col == "tilt") tilt.coerceIn(-1f, 1f) else if (live) DrawnFeel.tiltX.coerceIn(-1f, 1f) else 0f
        val key = if (live) "live$st" else "$col$st"
        val clock = if (live) DrawnFeel.timeMs else t
        val dt = clock - sAt
        val reset = key != sKey || dt < 0 || dt > 400
        for (i in 0 until f.h) {
            val up = 1f - i / (f.h - 1f); val target = tx * 3.2f * up; val k = 150f - 90f * up
            if (reset) { sOff[i] = target; sVel[i] = 0f } else {
                var left = dt.toInt()
                while (left > 0) { val h = min(left, 10) / 1000f; sVel[i] += ((target - sOff[i]) * k - sVel[i] * 5f) * h; sOff[i] += sVel[i] * h; left -= 10 }
            }
        }
        sKey = key; sAt = clock
        val o = IntArray(f.h) { sOff[it].roundToInt() }
        if (live) {
            // The wind makes its rows flutter; a clap knocks them apart in pairs, and they snap back.
            val w = DrawnFeel.wind.coerceIn(0f, 1f)
            if (w > 0.02f) for (i in 0 until f.h) o[i] += (w * 1.6f * sin(i * 1.3f + clock / 38f)).roundToInt()
            val s = DrawnFeel.startleMs
            if (s in 0 until 900) { val a = 3.4f * kotlin.math.exp(-s / 160f) * cos(s / 38f); for (i in 0 until f.h) o[i] += (a * (if ((i shr 1) % 2 == 1) 1 else -1)).roundToInt() }
        }
        return o
    }

    /** The eyes and mouth the engine's feelings ask for (blown on, clapped at), or null. */
    private fun feltEyes(): Int? = if (!DrawnFeel.live) null else if (DrawnFeel.startleMs in 0 until 500) WIDE else if (DrawnFeel.wind > 0.3f) CLOSED else null
    private fun feltLook(): Int = if (!DrawnFeel.live) 0 else when (DrawnFeel.startleMs) { in 500 until 950 -> -1; in 950 until 1400 -> 1; else -> 0 }

    /** A burst of interference: a few rows knocked sideways, [amount] cells at most. */
    private fun slips(st: Int, t: Long, amount: Int): IntArray {
        val f = FORMS[st]
        return IntArray(f.h) { i -> if (rnd(i / 2 + 50, (t / 80).toInt()) > 0.55f) ((rnd(i / 2, (t / 80).toInt()) * (2 * amount + 1)).toInt() - amount) else 0 }
    }
    override val pastimes = listOf(
        DrawnPastime("tetris", 5200, "играет в тетрис",
            "Сверху падают две палочки тетриса и ложатся ему на макушку, вторая на лету поворачивается; антенна закрывает ряд, он мигает и исчезает, а Глитч сияет."),
        DrawnPastime("emoji_faces", 4800, "примеряет смайлики",
            "Перебирает лица, как смайлики, с помехой между ними: ^_^, удивлённое О_О, подмигивание и хохочущее XD — и снова своё лицо."),
    )

    /** Interference on the head's rows only (its antenna and legs stay put). */
    private fun shear(c: Cv, t: Long) {
        val copy = c.v.copyOf()
        for (y in 8..18) {
            val sh = if (rnd(y + 50, (t / 80).toInt()) > 0.45f) (rnd(y, (t / 80).toInt()) * 5).toInt() - 2 else 0
            for (x in 0..24) c.v[y * 25 + x] = 0f
            for (x in 0..24) { val sx = x - sh; if (sx in 0..24) c.set(x, y, copy[y * 25 + sx]) }
        }
    }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        when (col) {
            "tetris" -> {
                // Two I-pieces fall a row at a time onto its head (the second turns over on the way); with the antenna they
                // fill a row, which flashes and is gone; it beams.
                val clear = t >= 3450
                val flash = t in 2850L until 3450L
                val happy = t in 3450L until 4100L
                val p1 = if (t >= 680 && !clear) 8 to min(7L, 2 + (t - 680) / 150).toInt() else null
                val k2 = (t - 1900) / 150
                val p2 = if (t >= 1900 && !clear) k2 else null
                val falling = t in 680L until 2850L
                val lx = if (!falling) 0 else if (t < 1900) -1 else 1
                body(c, st, ct, eyes = if (happy) HAPPY else if (falling) NONE else OPEN, mouth = if (happy) SMILE else if (falling || flash) OFF else CURSOR)
                if (falling) for (ex in intArrayOf(9, 14)) for (k in 0..1) { c.cut(ex + k + lx, 12); c.cut(ex + k + lx, 13) }
                if (flash) {
                    val on = ((t - 2850) / 100) % 2 == 0L
                    for (x in 8..16) if (on) c.set(x, 7, 1f) else c.cut(x, 7)
                } else {
                    if (p1 != null) for (x in 0..3) c.put(p1.first + x, p1.second, 0.7f)
                    if (p2 != null) {
                        if (p2 < 3) for (y in 0..3) c.put(14, (2 + p2).toInt() + y, 0.7f)
                        else for (x in 13..16) c.put(x, min(7L, 3 + p2).toInt(), 0.7f)
                    }
                }
            }
            "emoji_faces" -> {
                // Four faces, each brought in by a burst of interference: ^_^, O_O, a wink, XD.
                val k = ((t - PEDGE - 200) / 860).toInt()
                val inK = (t - PEDGE - 200) % 860
                val shown = t >= PEDGE + 200 && k in 0..3 && inK >= 160
                body(c, st, ct, eyes = if (shown) NONE else OPEN, mouth = if (shown) OFF else CURSOR)
                fun lit(vararg xy: Int) { for (i in xy.indices step 2) c.set(xy[i], xy[i + 1], 1f) }
                fun dark(vararg xy: Int) { for (i in xy.indices step 2) c.cut(xy[i], xy[i + 1]) }
                if (shown) when (k) {
                    0 -> { dark(8, 13, 9, 12, 10, 13, 14, 13, 15, 12, 16, 13); lit(9, 14, 10, 15, 11, 15, 12, 15, 13, 15, 14, 15, 15, 14) }
                    1 -> { for (ex in intArrayOf(9, 15)) for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) c.cut(ex + dx, 12 + dy)
                        dark(12, 13, 11, 14, 13, 14, 12, 15) }
                    2 -> { dark(8, 13, 9, 13, 10, 13, 15, 12, 15, 13); lit(11, 15, 12, 15, 13, 15, 14, 14) }
                    else -> { for (ex in intArrayOf(9, 15)) dark(ex - 1, 11, ex + 1, 11, ex, 12, ex - 1, 13, ex + 1, 13)
                        for (x in 10..14) lit(x, 14, x, 15); lit(11, 16, 12, 16, 13, 16) }
                }
                if (t >= PEDGE + 200 && k in 0..4 && inK < 160) shear(c, t)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val f = FORMS[st]
        val o = lean(st, col, t, tilt)
        val blink = t % 2800 < 140
        when (col) {
            "calm" -> {
                // Now and then two of its rows slip aside for a moment.
                val clock = if (DrawnFeel.live) DrawnFeel.timeMs else t
                if (clock % 4600 in 3900 until 4050) { val r = f.b0 + 1 + (rnd((clock / 4600).toInt(), 3) * (f.b1 - f.b0 - 2)).toInt(); o[r] += 2; o[r + 1] += 2 }
                val startled = DrawnFeel.live && DrawnFeel.startleMs in 0 until 500
                body(c, st, t, eyes = feltEyes() ?: if (blink) CLOSED else OPEN, mouth = if (startled) AGAPE else CURSOR, look = feltLook(), offs = o)
            }
            "tilt" -> body(c, st, t, eyes = feltEyes() ?: if (blink) CLOSED else OPEN, look = (tilt * 1.4f).roundToInt().coerceIn(-1, 1), offs = o)
            "happy" -> {
                for (i in 0 until f.h) o[i] += (1.2f * sin(i * 0.9f - t / 2400f * 2 * PI_F * 3)).roundToInt()
                body(c, st, t, eyes = HAPPY, mouth = SMILE, offs = o, hop = if (abs(sin(t / 600f * PI_F)) > 0.7f) 1 else 0)
            }
            "habit" -> if (t < 2400) body(c, st, t, eyes = NONE, mouth = BAR, bar = seg(t, 200, 2200), offs = o) else body(c, st, t, eyes = HAPPY, mouth = SMILE, offs = o)
            "signature" -> {
                // A reboot: the picture breaks up, collapses into a line, and boots again from the middle out.
                val mid = (f.b0 + f.b1) / 2
                fun rowsWithin(k: Int) = BooleanArray(f.h) { abs(it - mid) <= k }
                when {
                    t < 1000 -> body(c, st, t, offs = slips(st, t, 1 + (t / 350).toInt()), mouth = OFF)
                    t < 1600 -> body(c, st, t, eyes = NONE, mouth = OFF, show = rowsWithin(((1f - seg(t, 1000, 1600)) * f.h / 2).toInt()))
                    t < 3000 -> { val w = max(2, ((f.w - 3) / 2 * (1f - 0.6f * seg(t, 1600, 2400))).roundToInt()); for (x in 12 - w..12 + w) c.put(x, f.y0 + mid, if (abs(x - 12) <= 1) 1f else 0.5f) }
                    t < 4400 -> body(c, st, t, eyes = NONE, mouth = BAR, bar = seg(t, 3000, 4300), show = rowsWithin((seg(t, 3000, 3600) * f.h).toInt()))
                    else -> body(c, st, t, eyes = HAPPY, mouth = SMILE, hop = if (abs(sin(t / 600f * PI_F)) > 0.7f) 1 else 0)
                }
            }
            "hungry" -> {
                // A weak signal: its rows drop out every other one, and swap.
                val ph = ((t / 500) % 2).toInt()
                val show = BooleanArray(f.h) { i -> i in f.b0..f.b1 && ((i + ph) % 2 == 1 || i == f.mr || i in f.er - 1..f.er + f.eh) }
                body(c, st, t, eyes = feltEyes() ?: if (blink) CLOSED else OPEN, mouth = AGAPE, offs = o, show = show, dim = 0.85f)
            }
            "sad" -> body(c, st, t, eyes = CLOSED, mouth = OFF, offs = o, sag = true, dim = 0.62f)
            // Low resolution, and it nods off: the picture sinks a row, then starts back up.
            "tired" -> body(c, st, t, eyes = if (t < 2400) CLOSED else OPEN, mouth = OFF, offs = o, lowres = true, dim = if (t in 1400 until 2400) 0.6f else 0.72f, hop = if (t in 1400 until 2400) -1 else 0)
            "asleep" -> {
                // The screen is off: one line, breathing. Touched, it opens a slit with its eyes in it.
                if (DrawnFeel.live && DrawnFeel.peek) body(c, st, t, mouth = OFF, show = BooleanArray(f.h) { it in f.er - 1..f.er + f.eh })
                else { val w = max(2, ((f.w - 3) / 2 + sin(t / 4000f * 2 * PI_F) * 1.2f).roundToInt()); for (x in 12 - w..12 + w) c.put(x, f.y0 + f.h / 2, if (abs(x - 12) <= 1) 1f else 0.7f) }
            }
            "eating" -> {
                // Three bytes slide in along its mouth's row; each one swallowed lights another third of it.
                val eat = t < 2400
                val p = (t % 800) / 550f
                body(c, st, t, eyes = if (eat) OPEN else HAPPY, mouth = if (eat && p < 1f) AGAPE else SMILE, look = if (eat && p < 1f) 1 else 0, offs = o,
                    fill = if (eat) (t / 800) / 3f else if (t < 3000) 1f else -1f)
                if (eat && p < 1f) {
                    val xm = f.x0 + f.w; val y = f.y0 + f.mr
                    val head = (22 - (22 - xm) * p - 4 * p).roundToInt()
                    val xs = (0..4).map { head + it }.filter { it >= xm && n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(it, y) }
                    if (xs.size >= 4 || xs.firstOrNull() == xm) for (x in xs) c.put(x, y, 1f - (x - head) * 0.12f)
                }
            }
            "game" -> body(c, 0, t, dx = (tilt * 6f).roundToInt(), look = (tilt * 1.4f).roundToInt().coerceIn(-1, 1))
            "loved" -> {
                for (i in 0 until f.h) o[i] += (0.8f * sin(i * 0.7f - t / 2400f * 2 * PI_F * 2)).roundToInt()
                body(c, st, t, eyes = HEART, mouth = SMILE, offs = o)
            }
            "dizzy" -> {
                // Shaken, it loses its vertical hold: the picture rolls through its own frame, torn, and locks again.
                val lock = seg(t, 1700, 2300); val n = f.b1 - f.b0 + 1
                val roll = ((1f - lock) * (1f - lock) * 14f + t / 1000f * 9f * (1f - lock)).toInt()
                val src = IntArray(f.h) { i -> if (i in f.b0..f.b1) f.b0 + Math.floorMod(i - f.b0 + roll, n) else i }
                val tear = IntArray(f.h) { i -> ((rnd(i / 2, (t / 80).toInt()) - 0.5f) * 5f * (1f - lock)).roundToInt() }
                body(c, st, t, eyes = if (lock >= 1f) OPEN else NONE, mouth = if (lock >= 1f) CURSOR else OFF, offs = tear, src = src)
            }
            "charging" -> body(c, st, t, eyes = if (t > 2900) HAPPY else if (blink) CLOSED else OPEN, mouth = if (t > 2900) SMILE else CURSOR, offs = o, fill = seg(t, 0, 2900))
            // Typing: each key a burst of interference; a big loading bar fills as you type (100% at 40 s), and then it
            // reboots (its signature) and beams with heart eyes.
            "typing" -> { val y = DrawnTyping
                if (y.stage == 2) { if (y.stageMs < 3800) draw(c, "signature", 1200L + y.stageMs, st, tilt) else draw(c, "loved", t, st, tilt); return }
                val hit = y.hit(140) > 0f || (y.rush && (t / 80) % 3 == 0L)
                body(c, st, t, dy = if (st == 2) -3 else -2, eyes = if (y.rush) HAPPY else if (y.paused && t % 2600 < 160) CLOSED else OPEN, mouth = if (y.hit(160) > 0f || y.rush) SMILE else CURSOR,
                    offs = if (hit) slips(st, t, if (y.rush) 3 else 2) else null)
                val p = y.grown(); for (x in 6..18) { val on = x - 6 <= p * 12; c.put(x, 20, 0.45f); c.put(x, 22, 0.45f); c.put(x, 21, if (on) 0.95f else 0.08f) }; c.put(5, 21, 0.45f); c.put(19, 21, 0.45f) }
            "grow" -> {
                // Out of static (dashes of it, thinning) the picture scans in, row by row from the top.
                val p = seg(t, 0, 4400)
                for (k in 0 until (9 * (1f - p)).toInt()) { val y = 3 + (rnd(k, (t / 80).toInt()) * 18).toInt(); val x0 = 3 + (rnd(k, (t / 80).toInt() + 7) * 12).toInt(); for (x in x0..x0 + 4 + k % 3) c.put(x, y, 0.3f) }
                val now = if (st == 0 || t > 5200) st else st - 1
                val g = FORMS[now]
                if (p > 0.3f) body(c, now, t, show = BooleanArray(g.h) { it <= ((p - 0.3f) / 0.7f * g.h).toInt() }, mouth = if (p < 1f) OFF else CURSOR)
            }
        }
    }

    /** The little Bit for its game, [dx], [dy] from the middle: glad, or patching (its cursor racing), or just walking. */
    fun bit(c: Cv, t: Long, dx: Int, dy: Int, glad: Boolean, patching: Boolean, lean: Float) {
        val f = FORMS[0]
        val o = IntArray(f.h) { ((1f - it / (f.h - 1f)) * lean * 1.6f).roundToInt() }
        body(c, 0, if (patching) t * 3 else t, eyes = if (glad) HAPPY else OPEN, mouth = if (glad) SMILE else CURSOR, look = lean.roundToInt().coerceIn(-1, 1), offs = o, dx = dx, dy = dy - 3, hop = if (glad && (t / 120) % 2 == 0L) 1 else 0)
    }
}

/** The glitch's little Bit, for its game ([Glitch.bit]). */
internal fun glitchBit(c: Cv, t: Long, dx: Int, dy: Int, glad: Boolean, patching: Boolean, lean: Float) = Glitch.bit(c, t, dx, dy, glad, patching, lean)

// --- shadow ----------------------------------------------------------------------------------------------------------------

private object Shadow : EggHero(
    "shadow", listOf("Smudge", "Shadow", "Umbra"), Rarity.MYTHIC,
    "Живёт там, где тебя нет: прячется, когда смотришь",
    mapOf("calm" to "силуэт из темноты", "happy" to "скалится и прыгает", "habit" to "вытягивается, как вечером", "signature" to "гасит свет",
        "hungry" to "тянет к себе свет", "sad" to "сжимается", "tired" to "растворяется", "asleep" to "закрытые глаза в темноте",
        "eating" to "глотает огонёк", "game" to "не смотри — перебежала", "loved" to "довольный прищур", "dizzy" to "крутится воронкой",
        "tilt" to "тень ложится длинной", "charging" to "свет ярче — тень чётче", "grow" to "пятно стало фигурой"),
) {
    private fun glow(c: Cv, t: Long, b: Float = 1f) {
        for (y in 0..24) for (x in 0..24) { val r = hypot(x - 12f, y - 12f); c.put(x, y, (0.1f + 0.08f * (r / 12f) + 0.02f * sin(t / 300f + x * 0.5f + y * 0.3f)) * b) }
    }
    private fun figure(c: Cv, st: Int, t: Long, x: Float = 12f, top: Float = 6f, scaleH: Float = 1f, lean: Float = 0f, eyes: Int = 0, grin: Boolean = false, wide: Float = 1f) {
        val s = 0.75f + st * 0.15f
        val h = 17f * scaleH * s
        for (yi in 0..h.toInt()) {
            val y = 23f - yi
            val rel = yi / h
            val hw = (if (rel > 0.85f) 2.5f + (1 - rel) * 8f else 5f - rel * 1.5f) * s * wide
            val off = lean * rel * 4 + (if (rel > 0.85f) sin(t / 160f + yi) * 0.8f else 0f)
            for (xx in (x + off - hw).roundToInt()..(x + off + hw).roundToInt()) c.cut(xx, y.roundToInt())
        }
        val ey = (23f - h * 0.72f)
        val ex = x + lean * 3
        when (eyes) {
            0 -> { c.set((ex - 2).roundToInt(), ey.roundToInt(), 1f); c.set((ex + 2).roundToInt(), ey.roundToInt(), 1f) }
            1 -> { for (d in intArrayOf(-3, -2, 2, 3)) c.set((ex + d).roundToInt(), ey.roundToInt(), 0.25f) }
            2 -> for (d in intArrayOf(-2, 2)) { c.set((ex + d - 1).roundToInt(), ey.roundToInt() + 1, 1f); c.set((ex + d).roundToInt(), ey.roundToInt(), 1f); c.set((ex + d + 1).roundToInt(), ey.roundToInt() + 1, 1f) }
        }
        if (grin) for (d in -2..2) c.set((ex + d).roundToInt(), (ey + 3 - (if (abs(d) == 2) 1 else 0)).roundToInt(), 0.9f)
    }
    override val pastimes = listOf(
        DrawnPastime("shadow_bird", 5200, "показывает птичку",
            "Вытягивает руку, и кисть становится тенью птицы: она машет крыльями, слетает с руки, облетает тень над головой и садится обратно; тень довольно скалится."),
        DrawnPastime("peekaboo", 4800, "играет в ку-ку",
            "Закрывает лицо руками-тенями, глаза пропадают, один разок подглядывает между пальцами — и «ку-ку!»: руки в стороны, глаза и улыбка."),
    )

    /** A dark band two rows thick from ([x0], [y0]) to ([x1], [y1]): an arm of shadow. */
    private fun band(c: Cv, x0: Float, y0: Float, x1: Float, y1: Float) {
        val n = max(1, (max(abs(x1 - x0), abs(y1 - y0)) * 2).toInt())
        for (i in 0..n) { val x = x0 + (x1 - x0) * i / n; val y = y0 + (y1 - y0) * i / n; c.cut(x, y); c.cut(x, y + 1) }
    }
    /** A shadow bird at ([x], [y]), wings up or down. */
    private fun bird(c: Cv, x: Int, y: Int, up: Boolean) {
        val w = if (up) -1 else 1
        for (d in -1..1) c.cut(x + d, y)
        c.cut(x - 2, y + w); c.cut(x + 2, y + w); c.cut(x, y + 1)
    }

    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col)
        if (calmEdge(c, t, st, ms)) return
        val ct = calmClk(t)
        val sway = sin(ct / 700f) * 0.15f
        val blink = if (blinkAt(ct, 2800, 1600)) 1 else 0
        glow(c, ct)
        when (col) {
            "shadow_bird" -> {
                // An arm stretches out to the right, its hand a bird that flaps; the bird flies up over its head to the left
                // and back, lands on the hand, and the arm draws back in. Its eyes stay where they are; it grins at the end.
                val grin = t in 3700L until 4100L
                figure(c, st, ct, lean = sway, eyes = blink, grin = grin)
                val reach = 5f * min(ease(seg(t, 680, 1180)), 1f - ease(seg(t, 4100, 4600)))
                if (reach >= 0.5f) band(c, 15f, 13f, 15f + reach, 13f)
                val flap = (t / 140) % 2 == 0L
                if (reach >= 4.5f) {
                    val a = if (t in 1900L until 2800L) ease(seg(t, 1900, 2800)) * PI_F else if (t in 2800L until 3700L) (1f - ease(seg(t, 2800, 3700))) * PI_F else 0f
                    val bx = 12f + 9f * cos(a); val by = 12f - 10.4f * sin(a)
                    bird(c, bx.roundToInt(), by.roundToInt(), flap)
                }
            }
            "peekaboo" -> {
                // Arms bend up to its face and the eyes vanish behind them; one peek; then arms flung wide, eyes and a grin.
                val cover = ease(seg(t, 680, 1180)) * (1f - seg(t, 2300, 2450))
                val boo = ease(seg(t, 2300, 2450)) * (1f - ease(seg(t, 3300, 3900)))
                val peek = t in 1700L until 1950L
                figure(c, st, ct, lean = sway, eyes = if (cover > 0.6f) 3 else blink, grin = boo > 0.5f)
                for (sd in intArrayOf(-1, 1)) {
                    val pk = peek && sd < 0
                    val sx = 12f + sd * 4f; val sy = 14f
                    val ex = 12f + sd * mix(mix(4f, 8f, cover), 8f, boo); val ey = mix(mix(sy, 12f, cover), 9f, boo)
                    val hx = 12f + sd * mix(mix(4f, if (pk) 5f else 3f, cover), 9f, boo); val hy = mix(mix(sy, if (pk) 12f else 9f, cover), 5f, boo)
                    if (cover + boo > 0.05f) { band(c, sx, sy, ex, ey); band(c, ex, ey, hx, hy) }
                }
                if (cover > 0.6f && peek) c.set(10, 10, 1f)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val sway = sin(t / 700f) * 0.15f
        when (col) {
            "calm" -> { glow(c, t); figure(c, st, t, lean = sway, eyes = if (blinkAt(t, 2800, 1600)) 1 else 0) }
            "happy" -> { glow(c, t); val h = hop(t, 400f, 2f); figure(c, st, t, scaleH = 1f - h * 0.05f, eyes = 2, grin = true) }
            "habit" -> { glow(c, t); val p = sin(seg(t, 200, 3000) * PI_F); figure(c, st, t, scaleH = 1f + p * 0.3f, wide = 1f - p * 0.3f) }
            "signature" -> when {
                t < 1500 -> { glow(c, t, 1f - seg(t, 0, 1500)); figure(c, st, t) }
                t < 4500 -> { val a = t / 300f; c.set((12 + cos(a) * 7).roundToInt(), (12 + sin(a) * 5).roundToInt(), 1f); c.set((14 + cos(a) * 7).roundToInt(), (12 + sin(a) * 5).roundToInt(), 1f) }
                else -> { glow(c, t, seg(t, 4500, 5300)); figure(c, st, t, x = 16f, eyes = 2, grin = true) }
            }
            "hungry" -> { glow(c, t); for (i in 0 until 5) { val ph = ((t + i * 300) % 1500) / 1500f; val a = i * 1.3f; c.put(12 + cos(a) * (11 - ph * 7), 12 + sin(a) * (11 - ph * 7), 0.7f) }; figure(c, st, t, eyes = 0) }
            "sad" -> { glow(c, t, 0.8f); figure(c, st, t, scaleH = 0.7f, wide = 0.8f, eyes = 1) }
            "tired" -> { glow(c, t); figure(c, st, t, wide = 0.6f + 0.3f * (0.5f + 0.5f * sin(t / 500f)), eyes = 1) }
            "asleep" -> { glow(c, t, 0.6f); figure(c, st, t, scaleH = 0.8f, eyes = 1) }
            "eating" -> { glow(c, t); val p = seg(t, 0, 1400); if (t < 1500) c.disc(mix(21f, 12f, p), 9f, 1.2f, 1f); figure(c, st, t, eyes = if (t > 1500) 2 else 0, grin = t > 1500) }
            "game" -> { glow(c, t, if ((t / 1600) % 2 == 0L) 1f else 0.15f); val spot = intArrayOf(7, 17, 12, 5)[((t / 1600) % 4).toInt()]; if ((t / 1600) % 2 == 0L) figure(c, 0, t, x = spot.toFloat()) }
            "loved" -> { glow(c, t, 1.2f); figure(c, st, t, eyes = 2, wide = 1.1f) }
            "dizzy" -> { glow(c, t); for (k in 0 until 3) { val a0 = t / 150f + k * 2.1f; for (j in 0..12) { val r = j * 0.8f; c.cut(12 + cos(a0 + j * 0.35f) * r, 12 + sin(a0 + j * 0.35f) * r) } }; c.set(11, 12, 1f); c.set(13, 12, 1f) }
            "tilt" -> { glow(c, t); figure(c, st, t, lean = -tilt * 0.8f, scaleH = 1f - abs(tilt) * 0.2f) }
            "charging" -> { val p = seg(t, 0, 2900); glow(c, t, 0.6f + 0.8f * p); figure(c, st, t, eyes = if (p > 0.9f) 2 else 0) }
            // Typing: each key it stretches up and sinks back; the light goes down as you type, and after 40 s only its
            // eyes and grin are left in the dark.
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                glow(c, t, 1f - 0.92f * g); val p = y.hit(320); val up = if (p > 0f) sin((1 - p) * PI_F) else 0f
                figure(c, st, t, lean = sway, scaleH = 1f + up * (if (y.rush) 0.28f else 0.18f), wide = 1f - up * 0.15f,
                    eyes = if (y.rush || y.stage == 2) 2 else if (y.paused && blinkAt(t, 2800, 1600)) 1 else 0, grin = y.rush || y.stage == 2) }
            "grow" -> { glow(c, t); val p = ease(seg(t, 800, 4800)); if (p < 0.4f) c.hole(12f, 21f, 1f + p * 8f) else figure(c, if (st == 0 || t > 5200) st else st - 1, t, scaleH = p, eyes = if (p > 0.8f) 0 else 3) }
        }
    }
}

@Suppress("unused") private val keepImports = listOf(atan2(1f, 1f), min(1f, 2f))
