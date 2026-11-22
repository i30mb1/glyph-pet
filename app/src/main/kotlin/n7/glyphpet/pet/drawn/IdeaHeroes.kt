package n7.glyphpet.pet.drawn

import n7.glyphpet.pet.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
    private fun water(c: Cv, t: Long, level: Float = 18f, amp: Float = 1f, slope: Float = 0f, b: Float = 0.35f) {
        for (x in 0..24) { val y = level + sin(x * 0.6f + t / 300f) * amp + (x - 12) * slope; c.put(x.toFloat(), y, b); c.put(x.toFloat(), y + 2, b * 0.4f) }
    }
    private fun boat(c: Cv, st: Int, x: Float, bottom: Float, eyes: Int = 0, b: Float = 1f, sag: Boolean = false) {
        val rows = if (sag) BOAT[st].drop(1) else BOAT[st]
        val eyeRows = rows.map { it.replace('-', if (eyes == 0) '-' else '#') }
        c.spr(eyeRows, x, bottom, 0.38f * b)
        if (eyes == 2) { val y = bottom - 1; c.set((x - 2).roundToInt(), y.roundToInt(), 1f); c.set((x + 2).roundToInt(), y.roundToInt(), 1f) }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
    private fun rock(c: Cv, st: Int, x: Float = 12f, bottom: Float = 20f, eyes: Int = 1, b: Float = 1f, squash: Float = 1f, moss: Int = 3) {
        val rx = RX[st] * (2 - squash); val ry = RY[st] * squash
        val cy = bottom - ry
        c.oval(x, cy, rx, ry, 0.28f * b, 0.42f * b)
        c.line(x - rx * 0.4f, cy - ry * 0.4f, x - rx * 0.2f, cy, 0f)
        c.eyes(x - rx * 0.3f, x + rx * 0.3f, cy + ry * 0.1f, eyes)
        for (k in 0 until moss) c.put(x + rx * 0.3f + k, cy - ry - (if (k % 2 == 1) 1 else 0), 0.55f)
    }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
            // Typing: a little hop on each key (a rush: bouncing), and moss creeps over it while you type.
            "typing" -> { val y = DrawnTyping; val h = if (y.rush) hop(t, 300f, 2f) else y.hit(180) * 1.5f
                rock(c, st, bottom = 20f - h, squash = 1f - y.hit(120) * 0.12f, eyes = if (y.paused) peek else 0, moss = 2 + (y.grown() * 6).toInt()) }
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
    private fun plant(c: Cv, st: Int, t: Long, lean: Float = 0f, droop: Float = 0f, eyes: Int = 0, leaf: Float = 0f, bloom: Float = if (st == 2) 0.5f else 0f, h: Float = H[st], b: Float = 1f) {
        c.rows(listOf("#########", ".#######.", ".#######.", "..#####.."), 8, 17, 0.38f * b)
        c.eyes(10f, 14f, 18f, eyes)
        val topX = 12f + lean; val topY = 16f - h + droop
        c.line(12f, 16f, topX, topY, 0.42f * b)
        val ly = 16f - h * 0.55f + droop * 0.6f; val lx = 12f + lean * 0.55f
        val open = 1f - droop / 4f + leaf
        c.line(lx, ly, lx - 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.45f * b); c.put(lx - 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.7f * b)
        c.line(lx, ly, lx + 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.45f * b); c.put(lx + 2.5f * open, ly - 1.2f * open + droop * 0.5f, 0.7f * b)
        if (bloom > 0f) { val r = 0.8f + bloom * 1.6f; for (k in 0 until 5) { val a = k * 2 * PI_F / 5 + t / 2000f; c.put(topX + cos(a) * r, topY + sin(a) * r, 0.6f * b) }; c.put(topX, topY, 1f) }
        else c.put(topX, topY, 0.6f * b)
    }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
            // Typing: it grows taller while you type and blooms at the end; its leaves flap on each key, it droops in a pause.
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                plant(c, st, t, lean = if (y.rush) sin(t / 160f) * 1.2f else sw * 0.5f, droop = if (y.paused) 1.5f else 0f,
                    leaf = if (y.hit(150) > 0f) 0.5f else 0f, eyes = if (y.rush) 2 else if (blinkAt(t)) 1 else 0,
                    h = H[st] * (0.6f + 0.6f * g), bloom = if (g >= 1f) 0.8f else 0f) }
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
    private fun spider(c: Cv, st: Int, t: Long, x: Float, y: Float, eyes: Int = 0, legs: Float = 1f, thread: Boolean = true, b: Float = 1f, curl: Boolean = false) {
        if (thread) c.line(x, 0f, x, y - R[st], 0.2f)
        val wig = if ((t / 200) % 2 == 0L) 0.5f else -0.5f
        for (sd in intArrayOf(-1, 1)) for (k in 0..3) {
            if (curl) c.put(x + sd * (R[st] + 0.5f), y - 1 + k * 0.6f, 0.3f * b)
            else c.line(x + sd * R[st], y - 1 + k * 0.8f, x + sd * (R[st] + 3f * legs), y - 2.5f + k * 1.6f + wig * sd * legs, 0.3f * b)
        }
        c.oval(x, y, R[st], R[st] * 0.9f, 0.38f * b)
        c.eyes(x - 0.8f, x + 0.8f, y - 0.3f, eyes)
    }
    private fun web(c: Cv, cx: Float, cy: Float, r: Float, b: Float, spokes: Int = 8, rings: Int = 3) {
        for (k in 0 until spokes) { val a = k * 2 * PI_F / spokes; c.line(cx, cy, cx + cos(a) * r, cy + sin(a) * r, b) }
        for (i in 1..rings) c.ring(cx, cy, r * i / rings, b * 0.8f, 0.45f)
    }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
            // Typing: the web grows ring by ring while you type; on each key it scuttles to another spot on it.
            "typing" -> { val y = DrawnTyping; val g = y.grown()
                web(c, 12f, 12f, 7f + g * 4f, 0.11f, rings = 2 + (g * 3).toInt())
                val a = y.key * 2.4f; val r = if (y.paused || y.key < 0) 0f else (2f + g * 4f) * (0.5f + 0.25f * (y.key % 3))
                spider(c, st, t, 12f + cos(a) * r, 12f + sin(a) * r, if (y.rush) 2 else if (y.paused && blinkAt(t)) 1 else 0, thread = false, legs = 1f + y.hit(150) * 0.4f) }
            "grow" -> { if (t < 2400) { c.line(12f, 0f, 12f, 8f, 0.2f); c.oval(12f, 10f, 2f, 2.5f, 0.4f, 0.5f); if (t > 1600) c.cut(12, 11) } else spider(c, if (st == 0 || t > 4400) st else st - 1, t, 12f, mix(10f, y0, seg(t, 2400, 3600)), if (t > 3600) 0 else 1) }
        }
    }
}

// --- astronaut ------------------------------------------------------------------------------------------------------------

private object Astronaut : EggHero(
    "astronaut", listOf("Cadet", "Astronaut", "Commander"), Rarity.RARE,
    "Космонавт: в невесомости медленно плавает по всей матрице",
    mapOf("calm" to "парит среди звёзд", "happy" to "кувырок в невесомости", "habit" to "ставит флаг", "signature" to "полёт на ранце",
        "hungry" to "еда в тюбике уплывает", "sad" to "трос оборвался", "tired" to "визор тускнеет", "asleep" to "спит, свернувшись",
        "eating" to "ест из тюбика", "game" to "к станции по инерции", "loved" to "машет рукой", "dizzy" to "кувыркается",
        "tilt" to "дрейфует в невесомости", "charging" to "заряжает скафандр", "grow" to "выплывает из капсулы"),
) {
    private val BODY_ROWS = listOf("..#####..", ".#######.", "##.###.##", "...###...", "...#.#...", "..##.##..")
    private fun stars(c: Cv, t: Long, speed: Float = 200f) { for (i in 0 until 7) c.put(((rnd(i, 1) * 24 - t / speed) % 24 + 24) % 24, rnd(i, 2) * 24, 0.35f) }
    private fun suit(c: Cv, st: Int, x: Float, y: Float, visor: Float = 1f, flip: Boolean = false, arm: Float = 0f, b: Float = 1f) {
        val s = 0.8f + st * 0.2f
        c.oval(x, y, 4f * s, 3.5f * s, 0.35f * b, 0.5f * b)
        for (yy in -1..1) for (xx in -2..2) c.cut(x + xx * s, y + 0.5f + yy)
        c.put(x + 1, y - 0.5f, visor)
        c.rows(if (flip) BODY_ROWS.reversed() else BODY_ROWS, (x - 4).roundToInt(), (y + 4 * s).roundToInt(), 0.32f * b)
        if (arm != 0f) c.line(x + 3, y + 5 * s, x + 6, y + 5 * s - arm * 3, 0.4f * b)
    }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        val fy = 7f + sin(t / 900f) * 1.5f
        when (col) {
            "calm" -> { stars(c, t); suit(c, st, 12f, fy) }
            "happy" -> { stars(c, t, 120f); val f = (t / 300) % 2 == 1L; suit(c, st, 12f, fy, flip = f, arm = 1f); sparks(c, 12f, 4f, t, 3, 6f, 3f, 41) }
            "habit" -> { stars(c, t); suit(c, st, 10f, fy); val p = seg(t, 400, 1600); c.line(17f, 20f, 17f, 20f - p * 8, 0.4f); if (p >= 1f) for (k in 0..2) c.put(18f + k, 13f + (if ((t / 200 + k) % 2 == 0L) 0f else 1f), 0.7f) }
            "signature" -> { val p = seg(t, 600, 5600); val a = p * 2 * PI_F; val x = 12 + sin(a) * 6; val y = 9 - cos(a) * 4 + 3; stars(c, t, 60f); suit(c, st, x, y - 3); for (k in 1..4) c.put(x - sin(a) * k, y + 6 + k * 0.5f, if ((t / 80 + k) % 2 == 0L) 1f else 0.5f) }
            "hungry" -> { stars(c, t); suit(c, st, 10f, fy, arm = 0.5f); c.rows(listOf("ooo", ".#."), (19 + sin(t / 500f)).roundToInt(), 11, 0.6f) }
            "sad" -> { stars(c, t, 400f); val d = seg(t, 0, 3200) * 3; suit(c, st, 12f + d, fy + d, visor = 0.3f, b = 0.7f); c.line(0f, 20f, 8f + d, fy + 8 + d, 0.15f) }
            "tired" -> { stars(c, t, 600f); suit(c, st, 12f, fy, visor = if (t % 1600 < 800) 0.15f else 0.5f, b = 0.8f) }
            "asleep" -> { stars(c, t, 1200f); suit(c, st, 11f, 9f + sin(t / 2000f), visor = 0.1f, b = 0.8f); zs(c, 17f, 6f, t) }
            "eating" -> { stars(c, t); suit(c, st, 12f, fy, arm = 1f); if (t < 2600) for (k in 0..2) { val ph = ((t + k * 300) % 900) / 900f; c.put(17f - ph * 4, fy + 2 - ph * 2, 0.7f) } }
            "game" -> {
                stars(c, t)
                c.rows(listOf("#.#", "###", "#.#"), 20, 3, 0.5f)
                val x = 12f + tilt * 6; val y = 12f + sin(t / 1300f) * 4
                suit(c, 0, x, y)
                if (abs(tilt) > 0.5f) c.put(x - tilt * 5, y + 5, 1f)
            }
            "loved" -> { stars(c, t); suit(c, st, 12f, fy, visor = 1f, arm = 1f + sin(t / 100f) * 0.5f); c.put(18f, 4f, 0.8f) }
            "dizzy" -> { stars(c, t, 50f); suit(c, st, 12f + sin(t / 150f) * 2, fy, flip = (t / 160) % 2 == 0L); for (k in 0..2) { val a = t / 150f + k * 2.1f; c.put(12 + cos(a) * 6, 5 + sin(a) * 2, 0.8f) } }
            "tilt" -> { stars(c, t); suit(c, st, 12f + tilt * 6, fy + abs(tilt)) }
            "charging" -> { val p = seg(t, 0, 2900); stars(c, t, 400f); suit(c, st, 12f, fy, visor = p); for (k in 0 until (p * 5).toInt()) c.put(10f + k, 21f, 0.7f) }
            // Typing: its visor flashes and its arm taps on each key; a rush and it flies, the stars streaming past, a jet under it.
            "typing" -> { val y = DrawnTyping; stars(c, t, if (y.rush) 45f else 200f - y.grown() * 120f)
                suit(c, st, 12f, fy, visor = 0.35f + 0.65f * y.hit(220), arm = if (y.hit(160) > 0f) 1f else 0f)
                if (y.rush) for (k in 1..3) c.put(12f, fy + 4 * (0.8f + st * 0.2f) + 2 + k, if ((t / 80 + k) % 2 == 0L) 0.9f else 0.4f) }
            "grow" -> { stars(c, t); val o = seg(t, 1200, 2600); c.oval(12f, 12f, 5f, 6f, 0.3f, 0.5f); if (o > 0) for (y in 7..17) for (x in (12 - o * 4).roundToInt()..(12 + o * 4).roundToInt()) c.cut(x, y); if (t > 2600) suit(c, if (st == 0 || t > 4400) st else st - 1, 12f, mix(12f, fy, seg(t, 2600, 4200))) }
        }
    }
}

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
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
            // Typing: one head clicks aside on each key; more spirits come out the longer you type (20 s, 40 s); a rush and all rattle.
            "typing" -> { val y = DrawnTyping; trunks(c); val pts = spots((y.typedS / 20f).toInt().coerceIn(0, 2))
                pts.forEachIndexed { i, (x, bottom) ->
                    val tl = when { y.rush -> if (((t / 120) + i) % 2 == 0L) 1 else -1; y.hit(160) > 0f && y.key % pts.size == i -> if ((y.key / pts.size) % 2 == 0) 1 else -1; else -> 0 }
                    one(c, x, bottom, tilt = tl, dy = if (y.rush) -hop(t + i * 150, 300f, 2f) else 0f)
                } }
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
    private fun sea(c: Cv, t: Long, level: Float = 17f, slope: Float = 0f) { for (x in 0..24) c.put(x.toFloat(), level + sin(x * 0.7f + t / 400f) * 0.6f + (x - 12) * slope, 0.25f) }
    private fun arms(c: Cv, st: Int, t: Long, h: Float = 9f, lean: Float = 0f, curl: Int = -1, tip: Float = 0.5f, b: Float = 1f, level: Float = 17f, n: Int = 2 + st, speed: Float = 300f) {
        for (k in 0 until n) {
            val bx = 12f + (k - (n - 1) / 2f) * (16f / n)
            for (j in 0..h.toInt()) {
                val q = j / h
                val x = if (k == curl) bx + cos(j * 0.8f + t / 200f) * j * 0.3f else bx + sin(t / speed + k * 1.7f + j * 0.5f) * j * 0.25f + lean * q * 4
                c.put(x, level - j, (if (j % 3 == 0) 0.5f else 0.3f) * b * (if (j == h.toInt()) tip / 0.5f else 1f))
            }
        }
    }
    private fun eye(c: Cv, x: Float, y: Float, open: Boolean = true, r: Float = 2.5f) { c.oval(x, y, r, r * 0.6f, 0.6f); if (open) c.cut(x, y) else c.line(x - 1, y, x + 1, y, 0.2f) }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        when (col) {
            "calm" -> { sea(c, t); arms(c, st, t); if (t % 3200 in 800..2400) eye(c, 12f, 19f) }
            "happy" -> { sea(c, t); arms(c, st, t, h = 9f + hop(t, 400f, 3f), speed = 120f); for (i in 0 until 4) c.put(4f + i * 5, 13f - hop(t + i * 100, 400f, 3f), 0.7f) }
            "habit" -> { sea(c, t); arms(c, st, t, curl = 0); eye(c, 12f, 19f) }
            "signature" -> { val p = sin(seg(t, 0, 6000) * PI_F); val lvl = 22f - p * 8; c.oval(12f, lvl + 3, 6f, 5f, 0.3f, 0.45f); eye(c, 12f, lvl + 2, r = 2.5f + p); arms(c, 2, t, h = 6f + p * 8, level = lvl + 6, n = 6, speed = 150f); sea(c, t, 18f) }
            "hungry" -> { sea(c, t); val fx = (t / 20f) % 30 - 3; c.rows(listOf(".#o", "###", ".#."), fx.roundToInt(), 8, 0.5f); arms(c, st, t, lean = ((fx - 12) / 12f).coerceIn(-1f, 1f)); eye(c, 12f, 19f) }
            "sad" -> { rain(c, t, 6, 0.25f); sea(c, t); arms(c, st, t, h = 4f, b = 0.6f, speed = 900f); eye(c, 12f, 19f, open = false) }
            "tired" -> { sea(c, t); arms(c, st, t, h = 9f * (1 - seg(t, 0, 3000) * 0.6f), speed = 700f) }
            "asleep" -> { sea(c, t / 3); eye(c, 12f, 18.5f, open = false); for (i in 0..2) { val ph = ((t + i * 600) % 1800) / 1800f; c.ring(14f + i, 16f - ph * 10, 0.6f, 0.3f * (1 - ph)) } }
            "eating" -> { sea(c, t); val fx = 16f; val pull = seg(t, 1000, 2000); if (t < 2000) c.rows(listOf(".#o", "###"), fx.roundToInt(), (9 + pull * 8).roundToInt(), 0.5f); arms(c, st, t, lean = if (t < 2000) 0.6f else 0f); if (t in 2000..2400) for (k in -2..2) c.put(fx + k, 15f - abs(k), 0.7f); eye(c, 12f, 19f) }
            "game" -> { sea(c, t, 15f); for (i in 0 until 3) { val sx = (t / 40f + i * 9) % 28 - 2; c.rows(listOf("..#..", "#####", ".###."), sx.roundToInt() - 2, 12, 0.45f) }; val k = ((tilt + 1) / 2 * 2.99f).toInt(); val strike = (t % 1000) < 300; for (j in 0..(if (strike) 10 else 3)) c.put(5f + k * 7, 16f - j, 0.5f); eye(c, 12f, 19f) }
            "loved" -> { sea(c, t); for (j in 0..10) { val a = PI_F * j / 10; c.put(12 - cos(a) * 7, 17 - sin(a) * 9, 0.45f) }; eye(c, 12f, 19f) }
            "dizzy" -> { for (k in 0 until 4) { val a0 = t / 200f + k * PI_F / 2; for (j in 0..10) { val r = j * 0.9f; c.put(12 + cos(a0 + j * 0.3f) * r, 12 + sin(a0 + j * 0.3f) * r, 0.4f) } }; eye(c, 12f, 12f, open = false) }
            "tilt" -> { sea(c, t, slope = tilt * 0.2f); arms(c, st, t, lean = -tilt) }
            "charging" -> { val p = seg(t, 0, 2900); sea(c, t); arms(c, st, t, tip = 0.3f + 0.7f * p); eye(c, 12f, 19f, open = p > 0.5f) }
            // Typing: on each key one tentacle shoots up and taps, its tip flashing; a rush and they all thrash; a pause, the eye peeks.
            "typing" -> { val y = DrawnTyping; sea(c, t); val n = 2 + st
                arms(c, st, t, h = if (y.rush) 9f + hop(t, 300f, 2f) else 9f, speed = if (y.rush) 120f else 300f)
                val hit = y.hit(220)
                if (hit > 0f) { val k = y.key % n; val bx = 12f + (k - (n - 1) / 2f) * (16f / n); val top = 9 + (4 * hit).roundToInt(); for (j in 9..top) c.put(bx, 17f - j, if (j == top) 1f else 0.5f) }
                if (y.paused) eye(c, 12f, 19f) }
            "grow" -> { sea(c, t); val p = seg(t, 1000, 5000); if (p > 0) arms(c, if (st == 0) 0 else st - 1, t, h = 2f + p * 7, n = 2 + (p * st).toInt()); c.oval(12f, 18f, 1.5f + p, 1f + p * 0.5f, 0.5f) }
        }
    }
}

// --- glitch ----------------------------------------------------------------------------------------------------------------

private object Glitch : EggHero(
    "glitch", listOf("Bit", "Glitch", "Kernel"), Rarity.MYTHIC,
    "Цифровое существо: рассыпается на пиксели и снова собирается",
    mapOf("calm" to "курсор мигает", "happy" to "рассыпается и собирается", "habit" to "загрузка…", "signature" to "перезагрузка",
        "hungry" to "ловит биты", "sad" to "пиксели осыпаются", "tired" to "низкое разрешение", "asleep" to "режим ожидания",
        "eating" to "биты в рот", "game" to "чинит битые пиксели", "loved" to "глаза-сердечки", "dizzy" to "сбилась развёртка",
        "tilt" to "картинку рвёт", "charging" to "скачивание", "grow" to "собирается из шума"),
) {
    private val FACE = listOf(".#########.", "###########", "##-#####-##", "##-#####-##", "###########", "###########", ".#########.", "..#.#.#.#..", "...#...#...")
    private fun face(c: Cv, st: Int, t: Long, dx: Int = 0, dy: Int = 0, eyes: Int = 0, mouth: Boolean = (t / 400) % 2 == 0L, b: Float = 1f) {
        val rows = FACE.map { if (eyes == 1) it.replace('-', '#') else it }
        val w = if (st == 0) 0.85f else 1f
        c.spr(rows, 12f + dx, 18f + dy, 0.32f * b * w)
        if (eyes == 1) { c.set(9 + dx, 12 + dy, 0f); c.set(15 + dx, 12 + dy, 0f) }
        if (eyes == 2) for (x in intArrayOf(9, 15)) { c.set(x + dx, 11 + dy, 1f); c.set(x + dx, 12 + dy, 1f) }
        if (mouth) for (x in 10..14) c.set(x + dx, 15 + dy, 1f)
        if (st == 2) c.put(12f + dx, 9f + dy, if ((t / 300) % 2 == 0L) 1f else 0.3f)
    }
    private fun glitch(c: Cv, t: Long, amount: Int = 2) {
        val copy = c.v.copyOf()
        for (y in 0..24) {
            val sh = if (rnd(y + 50, (t / 80).toInt()) > 0.6f) (rnd(y, (t / 80).toInt()) * (2 * amount + 1)).toInt() - amount else 0
            for (x in 0..24) c.v[y * 25 + x] = 0f
            for (x in 0..24) { val sx = x - sh; if (sx in 0..24) c.set(x, y, copy[y * 25 + sx]) }
        }
    }
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        when (col) {
            "calm" -> { face(c, st, t); if (t % 1800 in 1100..1400) glitch(c, t) }
            "happy" -> { val p = abs(sin(t / 1200f * PI_F)); if (p < 0.6f) face(c, st, t, eyes = 2, mouth = true) else for (i in 0 until 40) c.put(12 + (rnd(i, 1) - 0.5f) * 20 * p, 13 + (rnd(i, 2) - 0.5f) * 16 * p, 0.6f) }
            "habit" -> { face(c, st, t, mouth = false); val p = seg(t, 0, 3000); for (x in 7..17) c.put(x, 21, if (x - 7 <= p * 10) 0.8f else 0.15f) }
            "signature" -> when {
                t < 1200 -> { face(c, st, t); glitch(c, t, (t / 300).toInt() + 1) }
                t < 2600 -> for (i in 0 until 160) if (rnd(i, (t / 80).toInt()) > 0.4f) c.put(rnd(i, (t / 80).toInt() + 1) * 24, rnd(i, (t / 80).toInt() + 2) * 24, 0.5f)
                t < 4200 -> { c.ring(12f, 12f, 3f, 0.6f, 0.5f, t / 100f, t / 100f + 4f); for (x in 7..17) c.put(x, 18, if (x - 7 <= seg(t, 2600, 4200) * 10) 0.8f else 0.15f) }
                else -> face(c, st, t, eyes = 2, mouth = true, b = ease(seg(t, 4200, 5000)))
            }
            "hungry" -> { face(c, st, t, mouth = true); for (i in 0 until 4) { val ph = ((t + i * 300) % 1200) / 1200f; c.put(20f - ph * 3, 6f + i * 3f, 0.7f) } }
            "sad" -> { face(c, st, t, eyes = 1, mouth = false, b = 0.7f); for (i in 0 until 5) { val ph = ((t + i * 400) % 2000) / 2000f; c.put(8f + i * 2, 18f + ph * 6, 0.4f * (1 - ph)) } }
            "tired" -> { face(c, st, t, eyes = 1, mouth = false, b = 0.8f + 0.2f * sin(t / 300f)); for (y in 0..24) for (x in 0..24) if ((x + y) % 2 == 1) c.v[y * 25 + x] *= 0.3f }
            "asleep" -> { val br = 0.2f + 0.6f * (0.5f + 0.5f * sin(t / 4000f * 2 * PI_F)); c.set(12, 21, br); c.set(11, 21, br * 0.3f); c.set(13, 21, br * 0.3f) }
            "eating" -> { face(c, st, t, mouth = (t / 200) % 2 == 0L); if (t < 2600) for (i in 0 until 6) { val ph = ((t + i * 200) % 1000) / 1000f; c.put(mix(22f, 12f, ph), mix(4f + i, 15f, ph), 0.8f) } }
            "game" -> { face(c, st, t, mouth = false, b = 0.6f); val cx = 12f + tilt * 8; c.put(cx, 4f, 1f); c.put(cx - 1, 4f, 0.4f); c.put(cx + 1, 4f, 0.4f); for (i in 0 until 3) { val bx = (5 + i * 7).toFloat(); if (abs(bx - cx) > 1.5f && (t / 200 + i) % 2 == 0L) c.put(bx, 13f, 1f) } }
            "loved" -> { face(c, st, t, mouth = true); for (x in intArrayOf(8, 14)) c.rows(listOf("o.o", "ooo", ".o."), x, 10 - (if ((t / 300) % 2 == 0L) 1 else 0), 0.3f) }
            "dizzy" -> { face(c, st, t, eyes = 1); val sh = ((t / 60) % 25).toInt(); val copy = c.v.copyOf(); for (y in 0..24) for (x in 0..24) c.v[y * 25 + x] = copy[((y + sh) % 25) * 25 + x]; for (y in 0..24) for (x in 0..24) if (!n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x, y)) c.v[y * 25 + x] = 0f }
            "tilt" -> { face(c, st, t); val copy = c.v.copyOf(); for (y in 0..24) { val sh = ((y - 12) * tilt * 0.5f).roundToInt(); for (x in 0..24) { val sx = x - sh; c.v[y * 25 + x] = if (sx in 0..24 && n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x, y)) copy[y * 25 + sx] else 0f } } }
            "charging" -> { val p = seg(t, 0, 2900); face(c, st, t, mouth = false, b = 0.4f + 0.6f * p); for (x in 7..17) c.put(x, 21, if (x - 7 <= p * 10) 0.9f else 0.15f) }
            // Typing: a burst of interference on each key (a rush and the screen crawls), and a loading bar fills while you type.
            "typing" -> { val y = DrawnTyping
                face(c, st, t, eyes = if (y.rush) 2 else if (y.paused && t % 2600 < 160) 1 else 0, mouth = y.hit(160) > 0f || y.rush)
                val p = y.grown(); for (x in 7..17) c.put(x, 21, if (x - 7 <= p * 10) 0.8f else 0.15f)
                if (y.hit(130) > 0f || (y.rush && (t / 80) % 3 == 0L)) glitch(c, t, if (y.rush) 3 else 2) }
            "grow" -> { val p = seg(t, 0, 4400); for (i in 0 until 200) if (rnd(i, (t / 80).toInt()) > p) c.put(rnd(i, 3) * 24, rnd(i, 4) * 24, 0.3f); if (p > 0.5f) face(c, if (st == 0 || t > 5200) st else st - 1, t, b = (p - 0.5f) * 2) }
        }
    }
}

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
    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
            // Typing: on each key it stretches up and sinks back; a rush and it grins, reaching higher.
            "typing" -> { val y = DrawnTyping; glow(c, t); val p = y.hit(320); val up = if (p > 0f) sin((1 - p) * PI_F) else 0f
                figure(c, st, t, lean = sway, scaleH = 1f + up * (if (y.rush) 0.28f else 0.16f), wide = 1f - up * 0.15f,
                    eyes = if (y.rush) 2 else if (y.paused && blinkAt(t, 2800, 1600)) 1 else 0, grin = y.rush) }
            "grow" -> { glow(c, t); val p = ease(seg(t, 800, 4800)); if (p < 0.4f) c.hole(12f, 21f, 1f + p * 8f) else figure(c, if (st == 0 || t > 5200) st else st - 1, t, scaleH = p, eyes = if (p > 0.8f) 0 else 3) }
        }
    }
}

@Suppress("unused") private val keepImports = listOf(atan2(1f, 1f), min(1f, 2f))
