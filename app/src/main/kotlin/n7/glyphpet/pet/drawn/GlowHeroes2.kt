package n7.glyphpet.pet.drawn

import n7.glyph.ui.matrix.GlyphMatrixSpec
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

/** The rest of the heroes of light from the Supporter mockups ([GlowHeroes]). */
internal object GlowHeroes2 {
    val all: List<Anomaly> = listOf(RadarGhost, Candle, Cocoa, Wisp, Sonar, Scope)
}

private val PIF = PI.toFloat()
private const val TAU_F = 6.2831855f

/** The angle from (cx, cy) to (x, y), 0..2pi. */
private fun angleOf(x: Float, y: Float, cx: Float = 12f, cy: Float = 12f): Float {
    val a = atan2(y - cy, x - cx)
    return if (a < 0) a + TAU_F else a
}

// --- the ghost on the radar -----------------------------------------------------------------------------------------------

/**
 * A ghost only a radar can see: the beam sweeps round and the ghost glows where it has just passed, fading behind it
 * like phosphor. Its mood is the sweep: quick and bright when happy, slow when tired, stopped asleep.
 */
private object RadarGhost : NewHero(
    "radar", listOf("Blip", "Radar Ghost", "Phantom"), Rarity.RARE,
    "Призрак на радаре: его видно только там, где прошёл луч",
    mapOf("calm" to "проявляется под лучом", "happy" to "луч быстрее, призрак прыгает", "habit" to "прячется за кольцом", "signature" to "импульс на весь экран",
        "hungry" to "думает о еде", "sad" to "еле виден у края", "tired" to "луч замедляется", "asleep" to "луч замер",
        "eating" to "ловит точку-еду", "game" to "ловит точки наклоном", "loved" to "точки-сердечки", "dizzy" to "луч мечется",
        "tilt" to "сносит к краю", "charging" to "кольца наполняются", "grow" to "точка вырастает в призрака"),
) {
    private val SHAPES = listOf(
        listOf(".###.", "#####", "#-#-#", "#####", "#.#.#"),
        listOf(".####.", "######", "#-##-#", "######", "######", "#.##.#"),
        listOf("..###..", ".#####.", "##-#-##", "#######", "#######", "#######", "#.#.#.#"),
    )

    /** The scope: two faint rings and the beam at [beam] (radians), or no beam when null. */
    private fun scope(c: Cv, beam: Float?, b: Float = 1f) {
        for (y in 0..24) for (x in 0..24) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val r = hypot(x - 12f, y - 12f)
            if (abs(r - 6f) < 0.5f || abs(r - 11.4f) < 0.5f) c.put(x, y, 0.1f * b)
        }
        if (beam != null) c.line(12f, 12f, 12 + cos(beam) * 12f, 12 + sin(beam) * 12f, 0.55f * b)
        c.set(12, 12, 0.6f * b)
    }

    /** How bright the phosphor is at ([x], [y]) with the beam at [beam]: just swept is bright, it fades round the dial. */
    private fun glow(x: Float, y: Float, beam: Float, fade: Float = 0.9f): Float {
        val d = Math.floorMod(((beam - angleOf(x, y)) * 1000).toInt(), (TAU_F * 1000).toInt()) / 1000f
        return exp(-d * fade)
    }

    /** The ghost at ([gx], [gy]) lit by the beam; [all] lights it whatever the beam (a flash). */
    private fun ghost(c: Cv, st: Int, gx: Float, gy: Float, beam: Float?, all: Float = 0f, shut: Boolean = false, b: Float = 1f) {
        val rows = SHAPES[st]
        val left = (gx - rows[0].length / 2f).roundToInt(); val top = (gy - rows.size / 2f).roundToInt()
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            val x = left + i; val y = top + j
            val v = max(all, if (beam != null) glow(x.toFloat(), y.toFloat(), beam) else 0f) * b
            when (ch) {
                '#' -> if (v > 0.05f) c.set(x, y, max(0.12f, v))
                '-' -> if (shut && v > 0.05f) c.set(x, y, max(0.12f, v * 0.6f)) else c.cut(x, y)
            }
        } }
    }

    private fun drift(t: Long, k: Float = 1f) = (12f + sin(t / 1300f * k) * 4.5f) to (11f + cos(t / 1700f * k) * 3.5f)

    private var tKey = -1
    private var tPing = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        val beam = (t / 700f) % TAU_F
        val (gx, gy) = drift(t)
        when (col) {
            "calm" -> { scope(c, beam); ghost(c, st, gx, gy, beam, shut = blink(t)) }
            "happy" -> { val bf = (t / 350f) % TAU_F; scope(c, bf); ghost(c, st, gx, gy - hop(t, 400f, 2f), bf) }
            "habit" -> { scope(c, beam); val hide = (t % 3200) / 3200f; ghost(c, st, 12f + 7.5f * cos(hide * TAU_F), 12f + 7.5f * sin(hide * TAU_F), beam) }
            "signature" -> {
                // A ping from the middle: a ring runs out and lights everything it crosses, the ghost all at once.
                scope(c, beam)
                val r = (t % 2000) / 2000f * 14f
                c.ring(12f, 12f, r, 0.8f * (1 - r / 14f))
                val lit = if (abs(hypot(gx - 12f, gy - 12f) - r) < 3f) 1f else 0f
                ghost(c, st, gx, gy, beam, lit)
            }
            "hungry" -> { scope(c, beam); ghost(c, st, 10f, 14f, beam); thought(c, listOf(".o.", "o.o", ".o."), t) }
            "sad" -> { val bs = (t / 1400f) % TAU_F; scope(c, bs, 0.6f); ghost(c, 0, 18f, 18f, bs, b = 0.6f) }
            "tired" -> { val bs = (t / 1800f) % TAU_F; scope(c, bs); ghost(c, st, 12f, 15f, bs, shut = (t / 700) % 2 == 0L) }
            "asleep" -> { scope(c, null, 0.6f); ghost(c, st, 12f, 15f, null, all = 0.18f + 0.08f * sin(t / 600f), shut = true) }
            "eating" -> {
                val fx = 16f; val fy = 8f; val p = seg(t, 600, 2000)
                scope(c, beam)
                if (t < 2000) c.set(fx.roundToInt(), fy.roundToInt(), max(0.3f, glow(fx, fy, beam)))
                ghost(c, st, mix(10f, fx, p), mix(15f, fy + 1, p), beam, if (t in 1800L..2400L) 1f else 0f)
            }
            "game" -> { scope(c, beam); ghost(c, st, 12f + tilt * 6f, 12f, beam) }
            "loved" -> { scope(c, beam); ghost(c, st, 12f, 13f, beam, 0.6f); hearts(c, 12f, 9f, t) }
            "dizzy" -> { val bd = sin(t / 300f) * 3f; scope(c, bd); ghost(c, st, 12f + cos(t / 250f) * 3f, 12f, bd, shut = true) }
            "tilt" -> { scope(c, beam); ghost(c, st, 12f + tilt * 6f, gy, beam) }
            "charging" -> { val p = seg(t, 0, 3000); scope(c, beam); for (k in 1..(p * 4).toInt()) c.ring(12f, 12f, k * 2.6f, 0.3f); ghost(c, st, 12f, 12f, beam, p * 0.7f) }
            "grow" -> {
                scope(c, beam)
                if (t < 2600) { val b = glow(12f, 13f, beam); c.disc(12f, 13f, 0.5f + seg(t, 0, 2600) * 1.5f, max(0.25f, b)) }
                else ghost(c, st, 12f, 13f, beam, if (t < 3200) 1f - seg(t, 2600, 3200) else 0f)
            }
            // Typing: every key puts a new blip on the dial, where the beam shows it; at 20 s the dial is crowded with
            // them, and at 40 s great pings sweep out and show a giant ghost face filling the screen.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tPing = y.now; tKey = y.key }
                val bt = (t / (if (y.rush) 350f else 600f)) % TAU_F
                scope(c, bt)
                if (y.stage == 2) {
                    val q = (y.stageMs % 1600) / 1600f
                    c.ring(12f, 12f, q * 15f, 0.6f * (1 - q))
                    val rows = SHAPES[2]
                    rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
                        for (a in 0..1) for (b in 0..1) { val x = 5 + i * 2 + a; val yy = 4 + j * 2 + b
                            val v = max(0.25f, glow(x.toFloat(), yy.toFloat(), bt, 0.6f))
                            when (ch) { '#' -> c.set(x, yy, v); '-' -> c.cut(x, yy) } }
                    } }
                    return
                }
                for (n in 0..min(y.key, 24)) { val a = rnd(n, 41) * TAU_F; val r = 3f + rnd(n, 42) * 7.5f; val bx = 12 + cos(a) * r; val by = 12 + sin(a) * r
                    val fresh = n == y.key && y.now - tPing < 300
                    c.set(bx.roundToInt(), by.roundToInt(), if (fresh) 1f else max(0.18f, glow(bx, by, bt))) }
                ghost(c, st, gx, gy, bt, if (y.now - tPing < 250) 1f else 0f, shut = y.paused && blink(t))
            }
        }
    }
}

// --- the candle -------------------------------------------------------------------------------------------------------------

/**
 * A candle with a face, burning with Glyph Flame's living fire. The flame is its mood: tall when happy, low and
 * smoking when sad, out asleep (a glowing wick and a curl of smoke); tilt the phone and the flame still burns upward.
 */
private object Candle : NewHero(
    "candle", listOf("Tealight", "Candle", "Pillar"), Rarity.RARE,
    "Свеча: живой огонь, как в Glyph Flame; пламя всегда тянется вверх",
    mapOf("calm" to "ровно горит", "happy" to "пламя выше", "habit" to "воск стекает", "signature" to "пламя вспыхивает столбом",
        "hungry" to "огонёк слабеет", "sad" to "чадит", "tired" to "мерцает", "asleep" to "погасла, тлеет фитиль",
        "eating" to "спичка разжигает", "game" to "зажигает фитили", "loved" to "тёплое ровное пламя", "dizzy" to "пламя кругом",
        "tilt" to "огонь тянется вверх", "charging" to "разгорается", "grow" to "спичка зажигает фитиль"),
) {
    private val heat = Fire.field()
    private val smoke = Fire.field()
    private val TOP = intArrayOf(17, 15, 13)

    /** The candle body with its face; [glow] is how much the flame lights the wax. */
    private fun body(c: Cv, st: Int, glow: Float, shut: Boolean, mouth: Int = 0) {
        val top = TOP[st]; val w = 3 + st
        for (y in top..22) for (x in 12 - w..12 + w) {
            val edge = x == 12 - w || x == 12 + w || y == top
            val near = (1f - (y - top) / 10f).coerceAtLeast(0f)
            c.set(x, y, (if (edge) 0.5f else 0.32f) + glow * 0.45f * near)
        }
        for (x in 12 - w - 1..12 + w + 1) c.set(x, 23, 0.45f)
        val ey = top + 3
        if (shut) { c.cut(10, ey); c.cut(11, ey); c.cut(13, ey); c.cut(14, ey) } else { c.cut(10, ey); c.cut(14, ey); c.cut(10, ey - 1); c.cut(14, ey - 1) }
        when (mouth) { 0 -> { c.cut(11, ey + 2); c.cut(12, ey + 3); c.cut(13, ey + 2) }; 1 -> { c.cut(11, ey + 3); c.cut(12, ey + 2); c.cut(13, ey + 3) }; else -> { c.cut(12, ey + 2); c.cut(12, ey + 3) } }
        c.set(12, top - 1, 0.25f)
    }

    private fun burn(c: Cv, st: Int, t: Long, size: Float = 1f, power: Float = 1f, ux: Float = 0f, uy: Float = -1f, smokeAmt: Float = 0f) {
        heat.fill(0f); smoke.fill(0f)
        val wy = TOP[st] - 1.3f
        if (power > 0f) Fire.flame(heat, 12f, wy, t, size * (0.8f + st * 0.1f), ux, uy, power)
        if (smokeAmt > 0f) Fire.smoke(smoke, 12f, wy, t, smokeAmt)
        Fire.renderSmoke(c, smoke); Fire.render(c, heat)
    }

    private var tKey = -1
    private var tFlare = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        when (col) {
            "calm" -> { body(c, st, 0.8f, blink(t)); burn(c, st, t) }
            "happy" -> { body(c, st, 1f, false); burn(c, st, t, size = 1.2f) }
            "habit" -> { body(c, st, 0.8f, false); burn(c, st, t); val p = (t % 3200) / 3200f; val y = TOP[st] + (p * (22 - TOP[st])).toInt(); c.set(12 + 3 + st, y, 0.8f); c.set(12 + 3 + st, y - 1, 0.6f) }
            "signature" -> { val p = seg(t, 600, 2400); val back = seg(t, 3600, 5200); val k = p - back; body(c, st, 1f, k > 0.3f, if (k > 0.3f) 2 else 0); burn(c, st, t, size = 1f + k * 0.8f) }
            "hungry" -> { body(c, st, 0.35f, false, 2); burn(c, st, t, size = 0.6f, power = 0.5f); thought(c, listOf("o..", ".o.", "..o"), t) }
            "sad" -> { body(c, st, 0.25f, false, 1); burn(c, st, t, size = 0.55f, power = 0.4f, smokeAmt = 0.5f) }
            "tired" -> { val f = 0.45f + 0.35f * (0.5f + 0.5f * sin(t / 500f)); body(c, st, f, f < 0.6f); burn(c, st, t, size = 0.7f, power = f) }
            "asleep" -> { body(c, st, 0f, true); c.set(12, TOP[st] - 1, 0.35f + 0.2f * sin(t / 600f)); burn(c, st, t, power = 0f, smokeAmt = 0.4f) }
            "eating" -> {
                // A match comes in from the side, touches the wick and the flame jumps up.
                val lit = t > 1400
                body(c, st, if (lit) 0.9f else 0.2f, false, if (lit) 0 else 2)
                if (t < 1600) { val mx = mix(22f, 13.5f, seg(t, 0, 1200)); c.line(mx, TOP[st] - 2f, mx + 6, TOP[st] - 4f, 0.5f); c.disc(mx, TOP[st] - 2f, 0.8f, 1f) }
                burn(c, st, t, size = if (lit) 1f + 0.3f * (1 - seg(t, 1400, 2400)) else 0f, power = if (lit) 1f else 0f)
            }
            "game" -> { body(c, st, 0.8f, false); burn(c, st, t, ux = -tilt * 0.7f, uy = -1f) }
            "loved" -> { body(c, st, 1f, true); burn(c, st, t, size = 1.05f + 0.1f * sin(t / 300f)) }
            "dizzy" -> { body(c, st, 0.7f, true, 2); burn(c, st, t, ux = sin(t / 150f) * 0.8f, uy = -0.7f) }
            "tilt" -> { body(c, st, 0.8f, false); burn(c, st, t, ux = -tilt * 0.8f) }
            "charging" -> { val p = seg(t, 0, 3000); body(c, st, p, false); burn(c, st, t, size = 0.4f + 0.7f * p, power = 0.4f + 0.6f * p) }
            "grow" -> {
                val lit = t > 3000
                body(c, st, if (lit) 0.8f else 0f, !lit)
                if (t in 1400L..3200L) { val mx = mix(22f, 13.5f, seg(t, 1400, 2800)); c.disc(mx, TOP[st] - 2f, 0.8f, 1f) }
                burn(c, st, t, size = if (lit) seg(t, 3000, 4400) else 0f, power = if (lit) 1f else 0f)
            }
            // Typing: a birthday cake. Every key lights the next little candle on it, left to right, round the candle
            // itself in the middle; at 20 s the whole cake is alight, and at 40 s the flames gather into one great fire.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tFlare = y.now; tKey = y.key }
                val flare = (1f - (y.now - tFlare) / 300f).coerceIn(0f, 1f)
                for (yy in 19..23) for (x in 3..21) c.set(x, yy, if (yy == 19 || x == 3 || x == 21) 0.55f else if ((x + yy) % 4 == 0) 0.45f else 0.3f)
                val spots = intArrayOf(5, 8, 16, 19)
                val lit = if (y.stage >= 1) 4 else min(4, y.key + 1)
                heat.fill(0f)
                for ((i, x) in spots.withIndex()) {
                    for (yy in 16..18) c.set(x, yy, if (i % 2 == 0) 0.6f else 0.4f)
                    if (i < lit && y.stage < 2) Fire.flame(heat, x.toFloat(), 15f, t, 0.36f, power = if (i == lit - 1) 0.8f + 0.2f * flare else 0.9f, salt = i + 1)
                    else if (y.stage < 2) c.set(x, 15, 0.2f)
                }
                // The hero: the big candle in the middle of the cake, with its face.
                for (yy in 13..18) for (x in 11..13) c.set(x, yy, if (x == 11 || x == 13) 0.55f else 0.45f)
                c.cut(11, 15); c.cut(13, 15); c.cut(12, 17)
                if (y.paused && blink(t)) { c.set(11, 15, 0.55f); c.set(13, 15, 0.55f) }
                val big = if (y.stage == 2) 1.7f else 0.75f + 0.25f * flare
                Fire.flame(heat, 12f, 11.8f, t, big, power = 1f)
                Fire.render(c, heat)
            }
        }
    }
}

// --- the cup of cocoa -------------------------------------------------------------------------------------------------------

/** A cup of cocoa with a face; its steam is living smoke, and how much it steams is how it feels. */
private object Cocoa : NewHero(
    "cocoa", listOf("Mug", "Cocoa", "Hot Chocolate"), Rarity.COMMON,
    "Чашка какао: над ней живой пар; чем ей лучше, тем больше пара",
    mapOf("calm" to "парит", "happy" to "пар клубится, подпрыгивает", "habit" to "зефирка качается", "signature" to "пар складывается в облако",
        "hungry" to "пустая, без пара", "sad" to "остыла", "tired" to "тонкая струйка", "asleep" to "спит, пар еле-еле",
        "eating" to "наливают какао", "game" to "ловит зефирки", "loved" to "пар сердечком", "dizzy" to "ложка мешает",
        "tilt" to "какао наклоняется", "charging" to "нагревается", "grow" to "наполняется"),
) {
    private val steam = Fire.field()
    private val TOP = intArrayOf(15, 14, 13)

    private fun cup(c: Cv, st: Int, dy: Int = 0, level: Float = 1f, shut: Boolean = false, mouth: Int = 0, tiltS: Float = 0f) {
        val top = TOP[st] + dy; val l = 7 - st; val r = 16 + st
        for (y in top..21 + dy) for (x in l..r) c.set(x, y, if (x == l || x == r || y == 21 + dy) 0.8f else 0.5f)
        for (y in top + 2..top + 5) c.set(r + 2, y, 0.8f); c.set(r + 1, top + 2, 0.8f); c.set(r + 1, top + 5, 0.8f)
        // The cocoa's surface: a bright line, as full as [level], leaning with the phone.
        if (level > 0f) for (x in l + 1 until r) c.set(x, top + (1 - level).times(4).roundToInt() + ((x - 12) * tiltS * 0.3f).roundToInt(), 0.95f)
        val ey = top + 3 + (if (st == 0) 0 else 1)
        if (shut) { c.cut(9, ey); c.cut(10, ey); c.cut(14, ey); c.cut(15, ey) } else { c.cut(10, ey); c.cut(10, ey - 1); c.cut(14, ey); c.cut(14, ey - 1) }
        when (mouth) { 0 -> { c.cut(11, ey + 2); c.cut(12, ey + 3); c.cut(13, ey + 2) }; 1 -> { c.cut(11, ey + 3); c.cut(12, ey + 2); c.cut(13, ey + 3) }; else -> { c.cut(12, ey + 2); c.cut(12, ey + 3) } }
        for (x in l - 2..r + 3) c.set(x, 22 + dy, 0.35f)
    }

    /** Steam from the cup: living smoke, drawn brighter than Fire's grey so it reads as steam. */
    private fun steamUp(c: Cv, st: Int, t: Long, amount: Float, lean: Float = 0f, dy: Int = 0) {
        steam.fill(0f)
        Fire.smoke(steam, 10.5f + lean, TOP[st] - 1f + dy, t, amount, 1)
        Fire.smoke(steam, 13.5f + lean, TOP[st] - 1f + dy, t + 700, amount, 5)
        for (i in steam.indices) { val v = steam[i]; if (v > 0.1f) c.put(i % 25, i / 25, min(0.55f, 0.08f + 0.6f * (v - 0.1f))) }
    }

    private var tKey = -1
    private var tDrop = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        when (col) {
            "calm" -> { steamUp(c, st, t, 1f); cup(c, st, shut = blink(t)) }
            "happy" -> { val h = hop(t, 400f, 2f).roundToInt(); steamUp(c, st, t, 1f, dy = -h); cup(c, st, -h) }
            "habit" -> { steamUp(c, st, t, 0.8f); cup(c, st); val bob = (sin(t / 300f) * 1.2f).roundToInt(); c.disc(12f + bob, TOP[st] - 0.5f, 1.2f, 1f) }
            "signature" -> {
                // The steam gathers into a little cloud over the cup, then drifts apart.
                steamUp(c, st, t, 1f)
                val k = seg(t, 800, 2600) - seg(t, 4200, 5800)
                if (k > 0f) for (i in 0..2) c.disc(9f + i * 3, 5f - (if (i == 1) 1 else 0), 2.2f * k, 0.5f)
                cup(c, st, mouth = if (k > 0.5f) 2 else 0)
            }
            "hungry" -> { cup(c, st, level = 0f, mouth = 2); thought(c, listOf("o.o", "ooo", ".o."), t) }
            "sad" -> { cup(c, st, dy = if (t % 3200 in 1600L..1900L) 1 else 0, level = 0.6f, mouth = 1); if (t % 3200 > 2000) c.set(10, TOP[st] + 6 + ((t % 3200 - 2000) / 300).toInt(), 0.5f) }
            "tired" -> { steamUp(c, st, t, 0.35f); cup(c, st, shut = (t / 700) % 2 == 0L) }
            "asleep" -> { steamUp(c, st, t / 2, 0.2f); cup(c, st, shut = true) }
            "eating" -> {
                // Cocoa pours in from above, the level rises and the steam comes back.
                val p = seg(t, 300, 2200)
                if (t < 2200) c.line(12f, 0f, 12f, TOP[st] + 4f - p * 4, 0.7f)
                steamUp(c, st, t, seg(t, 1800, 3000))
                cup(c, st, level = p, mouth = if (t < 2200) 2 else 0)
            }
            "game" -> { steamUp(c, st, t, 0.8f, lean = -tilt * 3f); cup(c, st, tiltS = tilt) }
            "loved" -> {
                cup(c, st, shut = true)
                val p = (t % 2400) / 2400f
                c.rows(listOf(".o.o.", "ooooo", ".ooo.", "..o.."), 10, (TOP[st] - 6 - p * 6).roundToInt(), 0.5f * (1 - p) + 0.15f)
            }
            "dizzy" -> { steamUp(c, st, t, 0.6f); cup(c, st, shut = true, mouth = 2); val a = t / 150f; c.line(12f, TOP[st].toFloat(), 12 + cos(a) * 3f, TOP[st] - 6f, 0.7f) }
            "tilt" -> { steamUp(c, st, t, 0.9f, lean = -tilt * 3f); cup(c, st, tiltS = tilt) }
            "charging" -> { val p = seg(t, 0, 3000); steamUp(c, st, t, p); cup(c, st) }
            "grow" -> { val p = seg(t, 500, 4500); steamUp(c, st, t, seg(t, 3500, 5000)); cup(c, st, level = p, shut = t < 3500) }
            // Typing: every key drops a marshmallow onto a tower over the cup; at 20 s the tower sways, and at 40 s the
            // cocoa overflows down the sides and the steam rises as a heart.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tDrop = y.now; tKey = y.key }
                val a = y.now - tDrop
                val n = min(4, y.key / 2 + 1)
                val sway = if (y.stage >= 1) sin(t / 300f) else 0f
                if (y.stage == 2) {
                    val p = (y.stageMs % 2400) / 2400f
                    c.rows(listOf(".o.o.", "ooooo", ".ooo.", "..o.."), 10, (TOP[st] - 6 - p * 6).roundToInt(), 0.6f * (1 - p) + 0.2f)
                } else { steam.fill(0f); Fire.smoke(steam, 12f, TOP[st] - 3f - n * 2f, t, 0.5f + 0.3f * y.stage, 1); for (i in steam.indices) { val v = steam[i]; if (v > 0.1f) c.put(i % 25, i / 25, min(0.4f, 0.06f + 0.5f * (v - 0.1f))) } }
                cup(c, st, shut = y.paused && blink(t), mouth = if (a < 300 || y.stage == 2) 2 else 0)
                for (k in 0 until n) {
                    val by = TOP[st] - 2 - k * 2
                    val bx = 12 + (sway * k * 0.6f).roundToInt()
                    val fall = if (k == n - 1 && a < 300) ((1 - a / 300f) * 6).roundToInt() else 0
                    for (dx in -1..1) { c.set(bx + dx, by - fall, 0.95f); c.set(bx + dx, by + 1 - fall, 0.7f) }
                }
                if (y.stage == 2) { val l = 7 - st; val r = 16 + st; val d = ((y.stageMs % 1200) / 150).toInt()
                    for (k in 0..min(d, 7)) { c.set(l - 1, TOP[st] + k, 0.8f); c.set(r + 1, TOP[st] + k, 0.8f) } }
            }
        }
    }
}

// --- the will-o'-wisp ---------------------------------------------------------------------------------------------------------

/** A living flame with eyes that drifts about the dark; its fire trails behind it as it moves. */
private object Wisp : NewHero(
    "wisp", listOf("Spark", "Wisp", "Will-o'-Wisp"), Rarity.RARE,
    "Блуждающий огонёк: живое пламя с глазами, огонь тянется за движением",
    mapOf("calm" to "летает", "happy" to "кружит, разгорается", "habit" to "делится на два огонька", "signature" to "вспыхивает огромным",
        "hungry" to "огонёк сникает", "sad" to "голубой и маленький", "tired" to "тускнеет", "asleep" to "тлеет угольком",
        "eating" to "сжигает веточку", "game" to "сжигает листья", "loved" to "ровное тёплое пламя", "dizzy" to "крутится",
        "tilt" to "пламя гнётся", "charging" to "разгорается", "grow" to "искра загорается"),
) {
    private val heat = Fire.field()
    private val SIZE = floatArrayOf(0.5f, 0.62f, 0.75f)

    /** The wisp at ([x], [y]) moving at ([vx], [vy]): flame leaning away from its motion, a glowing core, two eyes. */
    private fun wisp(c: Cv, st: Int, t: Long, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, size: Float = 1f, power: Float = 1f, shut: Boolean = false, salt: Int = 0, eyes: Boolean = true) {
        var ux = -vx * 0.12f; var uy = -1f - vy * 0.12f
        val n = hypot(ux, uy); ux /= n; uy /= n
        Fire.flame(heat, x, y + 1.5f, t, SIZE[st] * size, ux, uy, power, salt)
        Fire.splat(heat, x, y + 1f, 2.2f * SIZE[st] / 0.62f * size, 0.55f * power)
        Fire.render(c, heat); heat.fill(0f)
        // Eyes only on a flame big enough to hold them; a little one stays a whole flame.
        if (SIZE[st] * size * power < 0.42f || !eyes) return
        val ex = x.roundToInt(); val ey = (y + 1).roundToInt()
        if (shut) { c.set(ex - 1, ey, 0.9f); c.set(ex + 1, ey, 0.9f); c.cut(ex - 1, ey + 1); c.cut(ex + 1, ey + 1) }
        else { c.cut(ex - 1, ey); c.cut(ex + 1, ey) }
    }

    private fun path(t: Long, k: Float = 1f): FloatArray {
        val a = t / 1000f * k
        return floatArrayOf(12 + sin(a * 0.9f) * 5f, 12 + sin(a * 1.4f) * 3f, cos(a * 0.9f) * 4.5f * k, cos(a * 1.4f) * 4.2f * k)
    }

    private var tKey = -1
    private var tFlare = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        heat.fill(0f)
        val p = path(t)
        when (col) {
            "calm" -> wisp(c, st, t, p[0], p[1], p[2], p[3], shut = blink(t))
            "happy" -> { val q = path(t, 2f); wisp(c, st, t, q[0], q[1], q[2], q[3], 1.15f) }
            "habit" -> {
                val k = sin(seg(t, 0, 3200) * PIF)
                wisp(c, st, t, 12f - k * 4f, 13f, 0f, 0f, 1f - 0.3f * k, shut = k > 0.2f, salt = 1)
                if (k > 0.2f) wisp(c, 0, t, 12f + k * 4f, 13f, 0f, 0f, 0.8f, salt = 2, eyes = false)
            }
            "signature" -> { val k = seg(t, 600, 2200) - seg(t, 3800, 5600); wisp(c, st, t, 12f, 14f - k * 2, 0f, 0f, 1f + k * 1.1f, shut = k > 0.4f) }
            "hungry" -> { wisp(c, st, t, 11f, 16f, 0f, 0f, 0.85f, 0.75f); thought(c, listOf("..o", ".o.", "o.."), t) }
            "sad" -> wisp(c, 1, t, 12f, 17f + sin(t / 500f), 0f, 0f, 0.75f, 0.65f, shut = true)
            "tired" -> { val q = path(t, 0.5f); val f = 0.55f + 0.35f * (0.5f + 0.5f * sin(t / 600f)); wisp(c, st, t, q[0], q[1] + 3, q[2], q[3], 0.85f, f, shut = f < 0.7f) }
            "asleep" -> { val g = 0.35f + 0.2f * sin(t / 600f); c.disc(12f, 19f, 1.2f, g); c.set(11, 19, g * 0.4f); c.set(13, 19, g * 0.4f); for (x in 8..16) c.set(x, 21, 0.2f) }
            "eating" -> {
                // A twig drops; it flies to it and it flares up as it burns.
                val tp = seg(t, 0, 900); val fly = seg(t, 700, 1500)
                if (t < 1700) c.line(10f, mix(4f, 18f, tp), 14f, mix(5f, 19f, tp), 0.6f)
                wisp(c, st, t, mix(p[0], 12f, fly), mix(p[1], 16f, fly), 0f, 0f, 1f + 0.4f * (seg(t, 1500, 2000) - seg(t, 2400, 3200)), shut = t in 1600L..2400L)
            }
            "game" -> wisp(c, st, t, 12f + tilt * 6f, 12f, tilt * 6f, 0f)
            "loved" -> { wisp(c, st, t, 12f, 13f, 0f, 0f, 1.1f + 0.08f * sin(t / 250f), shut = true); hearts(c, 12f, 8f, t) }
            "dizzy" -> { val a = t / 250f; wisp(c, st, t, 12 + cos(a) * 4f, 13 + sin(a) * 4f, -sin(a) * 16f, cos(a) * 16f, shut = true) }
            "tilt" -> wisp(c, st, t, 12f + tilt * 5f, 13f, tilt * 8f, 0f)
            "charging" -> { val k = seg(t, 0, 3000); wisp(c, st, t, 12f, 13f, 0f, 0f, 0.5f + 0.7f * k, 0.5f + 0.5f * k) }
            "grow" -> {
                if (t < 2400) { val g = seg(t, 400, 2400); c.disc(12f, 18f, 0.6f + g, 0.4f + 0.6f * g) }
                else wisp(c, st, t, 12f, mix(17f, 13f, seg(t, 2400, 4000)), 0f, -2f, seg(t, 2400, 3600))
            }
            // Typing: every key lights a little lantern in a ring round it in the dark; at 20 s the ring is whole, and
            // at 40 s the lanterns lift off and drift up into the sky.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tFlare = y.now; tKey = y.key }
                val flare = (1f - (y.now - tFlare) / 250f).coerceIn(0f, 1f)
                val n = if (y.stage >= 1) 12 else min(12, y.key + 1)
                for (i in 0 until n) {
                    val a = i * TAU_F / 12 - PIF / 2
                    var lx = 12 + cos(a) * 9.5f; var ly = 12 + sin(a) * 9.5f
                    if (y.stage == 2) { ly -= ((y.stageMs / 90f + i * 3) % 30); lx += sin(t / 500f + i) * 0.8f; if (ly < -2) continue }
                    val fresh = i == n - 1 && flare > 0f && y.stage == 0
                    // A little paper lantern: a dim cap, a glowing 2x2 body.
                    val X = lx.roundToInt(); val Y = ly.roundToInt()
                    c.set(X, Y - 1, 0.35f); c.set(X + 1, Y - 1, 0.35f)
                    for (a in 0..1) for (b in 0..1) c.set(X + a, Y + b, if (fresh) 1f else 0.8f)
                }
                wisp(c, st, t, 12f + sin(t / 900f) * 1.5f, 12f + cos(t / 700f), 0f, 0f, 1f + 0.35f * flare, shut = y.paused && blink(t))
            }
        }
    }
}

// --- the sonar fish ---------------------------------------------------------------------------------------------------------

/**
 * A fish in the dark deep, seen only by sonar: a ping spreads from the middle, and where it passes the fish flashes
 * as an echo and fades, sending a little ring back.
 */
private object Sonar : NewHero(
    "sonar", listOf("Fry", "Sonar Fish", "Deep Echo"), Rarity.COMMON,
    "Рыбка на сонаре: в темноте её видно только как эхо импульса",
    mapOf("calm" to "вспыхивает эхом", "happy" to "импульсы чаще", "habit" to "пускает своё колечко", "signature" to "большой импульс, видно всё дно",
        "hungry" to "у поверхности", "sad" to "у дна, эхо слабое", "tired" to "импульсы реже", "asleep" to "сонар молчит",
        "eating" to "ловит рачка", "game" to "ловит рачков", "loved" to "колечки-сердечки", "dizzy" to "кружится",
        "tilt" to "сносит течением", "charging" to "сонар заряжается", "grow" to "икринка"),
) {
    /** The fish's shape at each stage, facing right: '#' body, '-' the eye. */
    private val SHAPES = listOf(
        listOf(".##.#", "##-##", ".##.#"),
        listOf("..###..#", ".#####.#", "###-####", ".#####.#", "..###..#"),
        listOf("..####...#", ".######.##", "####-#####", "##########", ".######.##", "..####...#"),
    )

    /** A sunken ship lying on the bottom, for the typing scene. */
    private val SHIP = listOf(
        "........#..........",
        "........#..........",
        ".......###.........",
        "..##############...",
        "...############....",
        "....##########.....",
        "#################..",
    )

    private fun echoAt(x: Float, y: Float, phase: Float, speed: Float): Float {
        val reach = phase * speed
        val d = hypot(x - 12f, y - 12f)
        if (reach < d) return 0f
        return exp(-(reach - d) / speed * 2.4f)
    }

    private fun fish(c: Cv, st: Int, fx: Float, fy: Float, right: Boolean, lit: (Int, Int) -> Float, shut: Boolean = false) {
        val rows = SHAPES[st].let { if (right) it else it.map { r -> r.reversed() } }
        val left = (fx - rows[0].length / 2f).roundToInt(); val top = (fy - rows.size / 2f).roundToInt()
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            val x = left + i; val y = top + j; val v = lit(x, y)
            when (ch) {
                '#' -> if (v > 0.05f) c.set(x, y, max(0.12f, v))
                '-' -> if (shut && v > 0.05f) c.set(x, y, max(0.12f, v * 0.6f)) else c.cut(x, y)
            }
        } }
    }

    private fun ping(c: Cv, phase: Float, speed: Float, b: Float = 1f) {
        val r = phase * speed
        if (r < 13f) c.ring(12f, 12f, r, 0.45f * (1 - r / 13f) * b)
        c.set(12, 12, 0.5f * b)
    }

    private fun swim(t: Long, period: Long = 6000): Triple<Float, Float, Boolean> {
        val ph = (t % period) / period.toFloat()
        val right = ph < 0.5f
        val x = 12 + if (right) -6 + ph * 24 else 6 - (ph - 0.5f) * 24
        return Triple(x, 12 + sin(t / 700f) * 3f, right)
    }

    private var tKey = -1
    private var tPing = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        val (fx, fy, right) = swim(t)
        val per = 1600L
        val ph = (t % per) / 1000f
        val sp = 9f
        when (col) {
            "calm" -> { ping(c, ph, sp); fish(c, st, fx, fy, right, { x, y -> echoAt(x.toFloat(), y.toFloat(), ph, sp) }, blink(t)) }
            "happy" -> { val q = (t % 800) / 1000f; ping(c, q, 14f); fish(c, st, fx, fy - hop(t, 500f, 2f), right, { x, y -> echoAt(x.toFloat(), y.toFloat(), q, 14f) }) }
            "habit" -> { ping(c, ph, sp); fish(c, st, 10f, 13f, true, { x, y -> max(0.3f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }); val q = (t % 1600) / 1600f; c.ring(15f + q * 3, 12f - q * 4, 0.6f + q, 0.5f * (1 - q)) }
            "signature" -> {
                // One big ping lights the whole sea bed: rocks, weed and the fish, then all fades.
                val q = (t % 4000) / 1000f
                ping(c, q, 7f)
                for (x in 0..24) { val h = (21 + sin(x * 0.7f) * 1.5f).roundToInt(); for (y in h..24) { val v = echoAt(x.toFloat(), y.toFloat(), q, 7f); if (v > 0.05f) c.put(x, y, v * 0.6f) } }
                for (k in 0..2) for (y in 15..20) { val x = 4f + k * 8 + sin(y * 0.8f + t / 400f) * 0.6f; val v = echoAt(x, y.toFloat(), q, 7f); if (v > 0.05f) c.put(x, y.toFloat(), v * 0.7f) }
                fish(c, st, 12f, 11f, true, { x, y -> echoAt(x.toFloat(), y.toFloat(), q, 7f) })
            }
            "hungry" -> { ping(c, ph, sp); fish(c, st, 12f, 6f, true, { x, y -> max(0.25f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }); thought(c, listOf("o.o", ".o.", "o.o"), t) }
            "sad" -> { val q = (t % 3000) / 1000f; ping(c, q, 6f, 0.6f); fish(c, 0, 12f, 19f, true, { x, y -> echoAt(x.toFloat(), y.toFloat(), q, 6f) * 0.6f }, true) }
            "tired" -> { val q = (t % 3200) / 1000f; ping(c, q, 6f); val (x, y, r) = swim(t, 12000); fish(c, st, x, y + 3, r, { a, b -> echoAt(a.toFloat(), b.toFloat(), q, 6f) }, (t / 700) % 2 == 0L) }
            "asleep" -> { c.set(12, 12, 0.3f); fish(c, st, 12f, 19f, true, { _, _ -> 0.15f + 0.07f * sin(t / 600f) }, true) }
            "eating" -> {
                val sx = 16f; val sy = 9f; val p = seg(t, 600, 1900)
                ping(c, ph, sp)
                if (t < 1900) { val v = max(0.3f, echoAt(sx, sy, ph, sp)); c.set(sx.roundToInt(), sy.roundToInt(), v); c.set(sx.roundToInt() + 1, sy.roundToInt(), v * 0.6f) }
                fish(c, st, mix(9f, sx - 2, p), mix(14f, sy, p), true, { x, y -> max(if (t in 1800L..2400L) 1f else 0f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) })
            }
            "game" -> { ping(c, ph, sp); fish(c, st, 12f + tilt * 6f, 12f, tilt >= 0, { x, y -> max(0.3f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }) }
            "loved" -> { ping(c, ph, sp); fish(c, st, 12f, 13f, true, { _, _ -> 0.7f }, true); hearts(c, 12f, 9f, t) }
            "dizzy" -> { ping(c, ph, sp); val a = t / 250f; fish(c, st, 12 + cos(a) * 4f, 12 + sin(a) * 3f, sin(a) < 0, { x, y -> max(0.3f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }, true) }
            "tilt" -> { ping(c, ph, sp); fish(c, st, 12f + tilt * 6f, fy, tilt >= 0, { x, y -> max(0.3f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }) }
            "charging" -> { val p = seg(t, 0, 3000); for (k in 1..(p * 4).toInt()) c.ring(12f, 12f, k * 2.6f, 0.3f); fish(c, st, 12f, 12f, true, { _, _ -> 0.2f + 0.6f * p }) }
            "grow" -> {
                ping(c, ph, sp)
                if (t < 2600) { val g = seg(t, 0, 2600); val v = max(0.3f, echoAt(12f, 13f, ph, sp)); c.disc(12f, 13f, 0.6f + g, v); c.cut(12, 13) }
                else fish(c, st, 12f, 13f, true, { x, y -> max(0.25f, echoAt(x.toFloat(), y.toFloat(), ph, sp)) })
            }
            // Typing: every key a ping shows a little more of a sunken ship on the bottom, left to right; at 20 s the
            // whole wreck is there, and at 40 s the chest on its deck opens and glows.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tPing = y.now; tKey = y.key }
                val q = (y.now - tPing) / 1000f
                ping(c, q, 16f)
                val shown = if (y.stage >= 1) 25 else (4 + y.key * 1.3f).toInt()
                SHIP.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
                    val x = 2 + i; val yy = 15 + j
                    if (ch == '#' && x < shown) c.set(x, yy, max(0.3f, echoAt(x.toFloat(), yy.toFloat(), q, 16f)))
                } }
                if (y.stage == 2) {
                    val open = ease(y.stageMs / 1500f)
                    for (x in 11..13) { c.set(x, 14, 0.9f); c.set(x, 13 - (open * 2).roundToInt(), 0.9f) }
                    for (k in 0..2) { val a = -PIF / 2 + (k - 1) * 0.5f; c.line(12f, 12f, 12 + cos(a) * 6 * open, 12 + sin(a) * 6 * open, 0.45f) }
                }
                val (fx, fy, right) = swim(t)
                fish(c, st, fx, fy - 4, right, { xx, yy -> max(0.25f, echoAt(xx.toFloat(), yy.toFloat(), q, 16f)) }, y.paused && blink(t))
            }
        }
    }
}

// --- the oscilloscope -------------------------------------------------------------------------------------------------------

/**
 * A creature that lives inside an oscilloscope: one bright beam keeps tracing its face, the glow fading behind it.
 * Its mood is the trace: a smile or a frown, quick or slow, a flat line with a heartbeat asleep.
 */
private object Scope : NewHero(
    "scope", listOf("Blip", "Oscillo", "Waveform"), Rarity.RARE,
    "Осциллограф: один луч рисует его лицо, свечение тает следом",
    mapOf("calm" to "луч рисует лицо", "happy" to "лицо подпрыгивает", "habit" to "рисует синусоиду", "signature" to "фигуры Лиссажу",
        "hungry" to "рот буквой О", "sad" to "грустная дуга", "tired" to "луч медленный", "asleep" to "ровная линия с пульсом",
        "eating" to "рисует бублик и ест", "game" to "ловит отметки лучом", "loved" to "рисует сердце", "dizzy" to "спираль",
        "tilt" to "картинка съезжает", "charging" to "рисует батарейку", "grow" to "точка раскрывается в лицо"),
) {
    private val R = floatArrayOf(6f, 7.5f, 8.5f)

    private fun grid(c: Cv) { for (i in 0..24) { c.put(12, i, 0.07f); c.put(i, 12, 0.07f) } }

    /**
     * A point of the face at [u] (0..1): the head round, then each eye, then the mouth ([mouth] -1 frown .. 1 smile,
     * 2 an O); null where the beam is blanked between strokes. [shut] draws the eyes as lines.
     */
    private fun face(u: Float, st: Int, mouth: Float, shut: Boolean, cx: Float, cy: Float): Pair<Float, Float>? {
        val r = R[st]
        return when {
            u < 0.55f -> { val a = u / 0.55f * TAU_F; (cx + cos(a) * r) to (cy + sin(a) * r) }
            u < 0.58f -> null
            u < 0.68f -> { val k = (u - 0.58f) / 0.1f; if (shut) (cx - r * 0.55f + k * 2.4f) to (cy - r * 0.2f) else { val a = k * TAU_F; (cx - r * 0.4f + cos(a) * 1.2f) to (cy - r * 0.2f + sin(a) * 1.2f) } }
            u < 0.71f -> null
            u < 0.81f -> { val k = (u - 0.71f) / 0.1f; if (shut) (cx + r * 0.55f - 2.4f + k * 2.4f) to (cy - r * 0.2f) else { val a = k * TAU_F; (cx + r * 0.4f + cos(a) * 1.2f) to (cy - r * 0.2f + sin(a) * 1.2f) } }
            u < 0.84f -> null
            else -> { val k = (u - 0.84f) / 0.16f
                if (mouth >= 2f) { val a = k * TAU_F; (cx + cos(a) * 1.5f) to (cy + r * 0.45f + sin(a) * 1.5f) }
                else (cx - r * 0.45f + k * r * 0.9f) to (cy + r * 0.38f + sin(k * PIF) * mouth * r * 0.22f) }
        }
    }

    /** Traces [path] with the beam: [periodMs] per round, the glow fading over [trail] of it. */
    private fun trace(c: Cv, t: Long, periodMs: Float, trail: Float = 0.95f, path: (Float) -> Pair<Float, Float>?) {
        val head = (t / periodMs) % 1f
        for (s in 0 until 600) {
            val age = s / 600f
            val u = ((head - age * trail) % 1f + 1f) % 1f
            val p = path(u) ?: continue
            val v = exp(-age * 2.6f)
            if (v > 0.06f) c.put(p.first, p.second, (v * 5).let { kotlin.math.ceil(it) / 5f })
        }
        path(head)?.let { c.set(it.first.roundToInt(), it.second.roundToInt(), 1f) }
    }

    private var tKey = -1
    private var tSpike = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        grid(c)
        when (col) {
            "calm" -> trace(c, t, 1100f) { u -> face(u, st, 1f, blink(t, 3200, 2200), 12f, 12.5f) }
            "happy" -> { val h = hop(t, 400f, 2f); trace(c, t, 800f) { u -> face(u, st, 1.4f, false, 12f, 12.5f - h) } }
            "habit" -> { if (t % 3200 < 1600) trace(c, t, 800f) { u -> (1f + u * 23f) to (12f + sin(u * 4 * TAU_F) * 4f) } else trace(c, t, 1100f) { u -> face(u, st, 1f, false, 12f, 12.5f) } }
            "signature" -> {
                // Lissajous figures, one after another, then the face comes back.
                val k = (t / 1600).toInt() % 4
                if (k < 3) trace(c, t, 900f) { u -> val a = u * TAU_F; (12 + sin(a * (k + 1)) * 8f) to (12 + sin(a * (k + 2) + PIF / 4) * 8f) }
                else trace(c, t, 1100f) { u -> face(u, st, 1.5f, false, 12f, 12.5f) }
            }
            "hungry" -> { trace(c, t, 1100f) { u -> face(u, st, 2f, false, 12f, 12.5f) }; thought(c, listOf("ooo", "o.o", "ooo"), t) }
            "sad" -> trace(c, t, 1400f) { u -> face(u, st, -1f, false, 12f, 13f) }
            "tired" -> trace(c, t, 2200f, 0.6f) { u -> face(u, st, 0f, (t / 700) % 2 == 0L, 12f, 13f) }
            "asleep" -> trace(c, t, 2000f, 0.9f) { u -> val x = 1f + u * 23f; val beat = if (abs(x - 12f) < 2f) -sin((x - 10f) / 4f * TAU_F) * 4f else 0f; x to (13f + beat) }
            "eating" -> {
                if (t < 1600) trace(c, t, 700f) { u -> val a = u * TAU_F; (18 + cos(a) * 2.5f) to (7 + sin(a) * 2.5f) }
                val chomp = t in 1600L..2800L && (t / 200) % 2 == 0L
                trace(c, t, 1000f) { u -> face(u, st, if (chomp) 2f else 1f, false, 12f, 13f) }
            }
            "game" -> trace(c, t, 1000f) { u -> face(u, 0, 1f, false, 12f + tilt * 5f, 12.5f) }
            "loved" -> trace(c, t, 1300f) { u -> val a = u * TAU_F; val s = sin(a); (12 + 16 * s * s * s * 0.5f) to (12f - (13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)) * 0.5f) }
            "dizzy" -> trace(c, t, 900f) { u -> val a = u * 5 * TAU_F; val r = u * 10f; (12 + cos(a) * r) to (12 + sin(a) * r) }
            "tilt" -> trace(c, t, 1100f) { u -> face(u, st, 1f, false, 12f + tilt * 4f, 12.5f) }
            "charging" -> {
                val p = seg(t, 0, 3000)
                trace(c, t, 1000f) { u -> when { u < 0.25f -> (6f + u * 4 * 12f) to 8f; u < 0.5f -> 18f to (8f + (u - 0.25f) * 4 * 8f); u < 0.75f -> (18f - (u - 0.5f) * 4 * 12f) to 16f; else -> 6f to (16f - (u - 0.75f) * 4 * 8f) } }
                for (x in 7..(7 + (p * 10).toInt())) for (y in 9..15) c.put(x, y, 0.5f)
            }
            "grow" -> { if (t < 2400) { val r = seg(t, 0, 2400) * 3f; trace(c, t, 600f) { u -> val a = u * TAU_F; (12 + cos(a) * r) to (12.5f + sin(a) * r) } } else trace(c, t, 1100f) { u -> face(u, st, 1f, false, 12f, 12.5f) } }
            // Typing: every key adds a note to a melody running along the bottom; at 20 s the face sings it, and at 40 s
            // the whole screen behind it is a dancing equalizer.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tSpike = y.now; tKey = y.key }
                if (y.stage == 2) for (x in 0..24 step 2) { val h = (3 + 5 * abs(sin(x * 0.7f + t / 160f)) * (0.6f + 0.4f * sin(t / 400f + x))).roundToInt(); for (k in 0 until h) c.put(x, 23 - k, 0.22f) }
                // The melody: one bar per key, scrolling left.
                for (n in max(0, y.key - 11)..y.key) {
                    val x = 23 - (y.key - n) * 2; val h = 1 + (rnd(n, 51) * 4).toInt()
                    val fresh = n == y.key && y.now - tSpike < 250
                    for (k in 0 until h) c.put(x, 22 - k, if (fresh) 1f else 0.5f)
                }
                val sing = if (y.stage >= 1 && y.now - tSpike < 300) 2f else 1f
                trace(c, t, if (y.rush) 700f else 1000f) { u -> face(u, if (y.stage >= 1) 1 else 0, sing, y.paused && blink(t), 12f, 10f) }
            }
        }
    }
}
