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

/** The calm clip's own clock at pastime time [t]: it loops, as the engine plays it. */
private fun Anomaly.calmAt(t: Long): Long = Math.floorMod(calmT(t), Drawn.length("calm"))

/** How long the pastime [col] runs. */
private fun Anomaly.pastimeMs(col: String): Long = pastimes.first { it.id == col }.ms

/** [a] brought into -pi..pi. */
private fun wrapPi(a: Float): Float { var d = a % TAU_F; if (d > PIF) d -= TAU_F; if (d < -PIF) d += TAU_F; return d }

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
    private fun ghost(c: Cv, st: Int, gx: Float, gy: Float, beam: Float?, all: Float = 0f, shut: Boolean = false, b: Float = 1f, rows: List<String> = SHAPES[st]) {
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

    override val pastimes = listOf(
        DrawnPastime("grab_beam", 5600, "хватает луч",
            "Подлетает к лучу и хватается за него: луч замирает, призрак светится целиком и тащит луч назад. Отпускает, и луч, догоняя, проносится по кругу."),
        DrawnPastime("play_blip", 5200, "прикидывается точкой",
            "Сжимается в обычную точку на экране и замирает, будто его тут нет, а потом выскакивает обратно во весь рост и вспыхивает: «бу!»"),
    )

    /** A blip on the dial: what the ghost pretends to be. */
    private val BLIP = listOf("##", "##")

    /**
     * Its pastimes: they take the ghost (and the beam) from where the calm clip has them and give both back where the
     * calm clip will be by the end.
     */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val c0 = calmAt(e); val c1 = calmAt(ms - e)
        val b0 = c0 / 700f; val b1 = c1 / 700f
        val (sx, sy) = drift(c0); val (ex, ey) = drift(c1)
        // A little more of it shows than the beam alone would light while it plays, so the story reads.
        val lift = 0.3f * (ease(seg(t, e, e + 400)) - ease(seg(t, ms - e - 400, ms - e)))
        val shutNow = blink(calmAt(t))
        when (col) {
            "grab_beam" -> {
                // Onto the beam where it will be, a grip (the beam stops, the ghost lights whole), a pull back, let go:
                // the beam whizzes round to where the calm sweep has got to, the ghost drifts back.
                val r = 5.5f
                val tg = e + 800; val th = tg + 700; val tr = th + 1300; val tc = ms - e - 300
                val ag = b0 + 800 / 700f
                val gx = 12 + cos(ag) * r; val gy = 12 + sin(ag) * r
                val ar = ag - 1.6f
                val beam: Float; val x: Float; val y: Float; var all = lift; var shut = false
                when {
                    t < tg -> { val p = ease(seg(t, e, tg)); beam = b0 + (t - e) / 700f; x = mix(sx, gx, p); y = mix(sy, gy, p); shut = shutNow }
                    t < th -> { beam = ag; x = gx; y = gy; all = 1f }
                    t < tr -> { val a = mix(ag, ar, ease(seg(t, th, tr))); beam = a; x = 12 + cos(a) * r; y = 12 + sin(a) * r; all = 1f; shut = true }
                    else -> {
                        val bc = b1 - (ms - e - tc) / 700f
                        val d = wrapPi(bc - ar).let { if (it < 0) it + TAU_F else it } + TAU_F
                        beam = if (t < tc) ar + d * ease(seg(t, tr, tc)) else b1 - (ms - e - t) / 700f
                        val p = ease(seg(t, tr + 300, ms - e))
                        x = mix(12 + cos(ar) * r, ex, p); y = mix(12 + sin(ar) * r, ey, p)
                        all = max(lift, 1f - seg(t, tr, tr + 500))
                        shut = t < tr + 900 || shutNow
                    }
                }
                scope(c, beam)
                ghost(c, st, x, y, beam, all, shut)
            }
            "play_blip" -> {
                // Smaller form by form down to a plain blip, a still wait, then out at full size in a flash, and back to
                // its drift.
                val pop = ms - e - 1300
                val adj = wrapPi(b1 - (b0 + (ms - 2 * e) / 700f))
                val beam = b0 + (t - e) / 700f + adj * ease(seg(t, ms - e - 900, ms - e))
                scope(c, beam)
                val step = ((t - e) / 350).toInt()
                if (t < pop) {
                    // Each smaller form held long enough to see, lit up a little so the shrinking reads.
                    val rows = if (step <= st) SHAPES[st - step] else BLIP
                    val shown = if (rows === BLIP) 0.35f else if (step == 0) max(lift, 0.6f * seg(t, e, e + 250)) else 0.6f
                    ghost(c, st, sx, sy, beam, shown, shutNow && step == 0, rows = rows)
                } else {
                    val p = ease(seg(t, pop + 500, ms - e))
                    val jump = if (t - pop < 500) hop(t - pop, 500f, 1.5f) else 0f
                    ghost(c, st, mix(sx, ex, p), mix(sy, ey, p) - jump, beam, max(lift, 1f - seg(t, pop, pop + 800)), shutNow && t > pop + 800)
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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

    /** The candle body with its face; [glow] is how much the flame lights the wax, [look] and [up] move its eyes. */
    private fun body(c: Cv, st: Int, glow: Float, shut: Boolean, mouth: Int = 0, look: Int = 0, up: Int = 0) {
        val top = TOP[st]; val w = 3 + st
        for (y in top..22) for (x in 12 - w..12 + w) {
            val edge = x == 12 - w || x == 12 + w || y == top
            val near = (1f - (y - top) / 10f).coerceAtLeast(0f)
            c.set(x, y, (if (edge) 0.5f else 0.32f) + glow * 0.45f * near)
        }
        for (x in 12 - w - 1..12 + w + 1) c.set(x, 23, 0.45f)
        val ey = top + 3
        val l = 10 + look; val r = 14 + look; val y = ey - up
        if (shut) { c.cut(l, y); c.cut(l + 1, y); c.cut(r - 1, y); c.cut(r, y) } else { c.cut(l, y); c.cut(r, y); c.cut(l, y - 1); c.cut(r, y - 1) }
        when (mouth) { 0 -> { c.cut(11, ey + 2); c.cut(12, ey + 3); c.cut(13, ey + 2) }; 1 -> { c.cut(11, ey + 3); c.cut(12, ey + 2); c.cut(13, ey + 3) }; else -> { c.cut(12, ey + 2); c.cut(12, ey + 3) } }
        c.set(12, top - 1, 0.25f)
    }

    private fun burn(c: Cv, st: Int, t: Long, size: Float = 1f, power: Float = 1f, ux: Float = 0f, uy: Float = -1f, smokeAmt: Float = 0f, lift: Float = 0f) {
        heat.fill(0f); smoke.fill(0f)
        val wy = TOP[st] - 1.3f - lift
        if (power > 0f) Fire.flame(heat, 12f, wy, t, size * (0.8f + st * 0.1f), ux, uy, power)
        if (smokeAmt > 0f) Fire.smoke(smoke, 12f, wy, t, smokeAmt)
        Fire.renderSmoke(c, smoke); Fire.render(c, heat)
    }

    private var tKey = -1
    private var tFlare = -99_999L

    override val pastimes = listOf(
        DrawnPastime("toss_flame", 4800, "подбрасывает огонёк",
            "Подкидывает своё пламя, как мячик: огонёк трижды отрывается от фитиля, взлетает и садится обратно, а свеча следит за ним глазами."),
        DrawnPastime("moth", 5600, "мотылёк в гостях",
            "Из темноты на свет прилетает мотылёк и порхает вокруг пламени; свеча водит за ним глазами, пламя тянется к нему, а мотылёк улетает обратно в темноту."),
    )

    /** A moth, wings open and wings up. */
    private val MOTH = listOf(listOf("##.##", "#####", "..#.."), listOf(".#.#.", ".###.", "..#.."))

    /** Its pastimes: the candle never moves, so they start and end on its calm flame and face. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val top = TOP[st]
        val shutNow = blink(calmAt(t))
        when (col) {
            "toss_flame" -> {
                // Three tosses: the flame leaves the wick, flies up, comes down onto it again; the eyes go up after it.
                val w0 = e + 250; val per = 1080L
                val k = ((t - w0) / per).toInt(); val p = ((t - w0) % per) / per.toFloat()
                val h = if (t >= w0 && k in 0..2) 4f * p * (1 - p) * 4f else 0f
                val away = min(1f, h / 1.5f)
                body(c, st, 0.8f - 0.3f * away, h < 0.5f && shutNow, if (h > 2.5f) 2 else 0, up = if (h > 1f) 1 else 0)
                if (h > 0.8f) c.set(12, top - 1, 0.55f)
                // Up in the air it is a little ball of fire, so the toss shows under the top of the round.
                burn(c, st, t, size = 1f - 0.42f * away, lift = h)
            }
            "moth" -> {
                // Out of the dark (it shows only as near the flame as its light reaches), round the flame, off again.
                // In from the dark at the top right, fluttering by the flame on the right, across in front of it, by it on
                // the left, and off into the dark at the top left; it shows only as near as the candle's light reaches.
                val keys = listOf(0L to (23f to 1f), 900L to (18f to 6f), 1500L to (19f to 9f), 2100L to (12f to 10f),
                    2700L to (5f to 8f), 3300L to (6f to 5f), 4100L to (0f to 0f))
                val tt = t - e - 100
                val i = keys.indexOfLast { it.first <= tt }.coerceIn(0, keys.size - 2)
                val (k0, p0) = keys[i]; val (k1, p1) = keys[i + 1]
                val q = ease(((tt - k0).toFloat() / (k1 - k0)).coerceIn(0f, 1f))
                val mx = mix(p0.first, p1.first, q)
                val my = mix(p0.second, p1.second, q) + (if ((t / 180) % 2 == 0L) 0f else 1f)
                val near = (1f - (hypot(mx - 12f, my - (top - 4f)) - 5f) / 6f).coerceIn(0f, 1f)
                val seen = near > 0.05f
                val look = if (!seen) 0 else if (mx < 10.5f) -1 else if (mx > 13.5f) 1 else 0
                body(c, st, 0.8f, !seen && shutNow, look = look, up = if (seen) 1 else 0)
                val reach = ((mx - 12f) * 0.08f).coerceIn(-0.5f, 0.5f) * near
                burn(c, st, t, ux = reach)
                // Lit by the flame, it shows over it too as it crosses in front.
                if (seen) { val rows = MOTH[((t / 90) % 2).toInt()]; val x0 = mx.roundToInt() - 2; val y0 = my.roundToInt() - 1
                    rows.forEachIndexed { j, row -> row.forEachIndexed { ii, ch -> if (ch == '#') c.set(x0 + ii, y0 + j, 0.55f * near) } } }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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

    /**
     * Steam from the cup: living smoke, drawn brighter than Fire's grey so it reads as steam. [shear] blows it to the
     * left, the more the higher it has risen (a whole cell per row at 1).
     */
    private fun steamUp(c: Cv, st: Int, t: Long, amount: Float, lean: Float = 0f, dy: Int = 0, shear: Float = 0f) {
        steam.fill(0f)
        Fire.smoke(steam, 10.5f + lean, TOP[st] - 1f + dy, t, amount, 1)
        Fire.smoke(steam, 13.5f + lean, TOP[st] - 1f + dy, t + 700, amount, 5)
        for (i in steam.indices) { val v = steam[i]
            if (v > 0.1f) { val y = i / 25; val dx = max(0, (shear * (TOP[st] - 1 + dy - y)).roundToInt()); c.put(i % 25 - dx, y, min(0.55f, 0.08f + 0.6f * (v - 0.1f))) } }
    }

    private var tKey = -1
    private var tDrop = -99_999L

    override val pastimes = listOf(
        DrawnPastime("blow_steam", 4800, "дует на себя",
            "Остужает сама себя: набирает воздух и трижды дует, пар каждый раз сносит в сторону, и от раза к разу его всё меньше."),
        DrawnPastime("bubbles", 4800, "пускает пузыри",
            "На какао надуваются пузыри: два маленьких лопаются один за другим, а третий, большой, растёт и лопается так, что чашка жмурится."),
    )

    /** A bubble on the cocoa, by size: rows over the surface, bottom row last; '#' the film, '-' dark inside. */
    private val BUBBLE = listOf(
        listOf("#"),
        listOf(".##.", "#--#"),
        listOf(".###.", "#---#", "#---#"),
        listOf("..###..", ".#---#.", "#-----#", "#-----#"),
    )

    /** Its pastimes: the cup never moves, so they start and end on its calm steam and face. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val shutNow = blink(calmAt(t))
        when (col) {
            "blow_steam" -> {
                // Three blows: a breath in (mouth shut), then a puff (eyes squeezed, mouth round) that bends the steam
                // over; each leaves a little less steam, and it comes back as it settles.
                val w0 = e + 150; val per = 1100L
                val k = ((t - w0) / per).toInt(); val q = ((t - w0) % per) / per.toFloat()
                val puff = if (t >= w0 && k in 0..2 && q >= 0.3f) { val b = (q - 0.3f) / 0.7f; min(1f, b * 5f) * (1f - seg((b * 1000).toLong(), 600, 1000)) } else 0f
                val done = ((t - w0) / per.toFloat()).coerceIn(0f, 3f)
                val amount = 1f - 0.12f * done * (1f - ease(seg(t, ms - e - 900, ms - e)))
                steamUp(c, st, calmAt(t), amount, shear = 0.75f * puff)
                cup(c, st, shut = if (puff > 0.1f) true else shutNow, mouth = if (puff > 0.1f) 2 else 0)
            }
            "bubbles" -> {
                // The steam thins so the bubbles read; each grows form by form on the surface and pops.
                val amount = 1f - 0.7f * (ease(seg(t, e, e + 400)) - ease(seg(t, ms - e - 600, ms - e)))
                steamUp(c, st, calmAt(t), amount)
                data class B(val x: Int, val from: Long, val steps: LongArray)
                val bubbles = listOf(B(10, e + 150, longArrayOf(250, 350)), B(14, e + 850, longArrayOf(250, 350)), B(12, e + 1600, longArrayOf(250, 300, 350, 700)))
                var squint = false
                val surface = TOP[st]
                // Drawn after the cup, so the film sits on the cocoa.
                val draws = ArrayList<() -> Unit>()
                for (bb in bubbles) {
                    var at = bb.from
                    var size = -1
                    for ((i, d) in bb.steps.withIndex()) { if (t >= at) size = i; at += d }
                    if (t >= at) { size = -1; if (t < at + 350 && bb.steps.size == 4) squint = true }
                    if (size >= 0) draws += {
                        val rows = BUBBLE[size]
                        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
                            val x = bb.x - row.length / 2 + i; val y = surface - rows.size + j
                            when (ch) { '#' -> c.set(x, y, 1f); '-' -> c.cut(x, y) }
                        } }
                    }
                }
                cup(c, st, shut = squint || shutNow, mouth = 0)
                draws.forEach { it() }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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

    override val pastimes = listOf(
        DrawnPastime("raindrop", 5200, "на него капнуло",
            "Сверху падает капля прямо на огонёк: он шипит, съёживается и дымит, потом отряхивается и разгорается ярче прежнего."),
        DrawnPastime("bounce", 4800, "скачет мячиком",
            "Падает вниз и скачет, как мячик, всё ниже и ниже, на каждом ударе сплющивается и подсвечивает землю, а потом взлетает обратно."),
    )

    private val smokeF = Fire.field()

    /** A drop of water: a point on top, round below. */
    private val DROP = listOf(".#.", "###")

    /**
     * Its pastimes: they take the wisp from where its calm flight has it ([path]) to a place of its own and hand it back
     * to the flight where that will be by the end.
     */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val ps = path(calmAt(e)); val pe = path(calmAt(ms - e))
        val shutNow = blink(calmAt(t))
        val back = ease(seg(t, ms - e - 900, ms - e))
        when (col) {
            "raindrop" -> {
                // It stops to hover; a drop falls onto it: a hiss (it shrinks, eyes wide, smoke), a shake, a proud flare.
                val hx = 12f; val hy = 13f
                val go = ease(seg(t, e, e + 700))
                val hit = e + 1500
                var x = mix(ps[0], hx, go); var y = mix(ps[1], hy, go)
                var vx = ps[2] * (1 - go); var vy = ps[3] * (1 - go)
                var size = 1f; var shut = shutNow
                if (t >= hit && t < hit + 800) { size = 0.72f; shut = false }
                if (t >= hit + 800 && t < hit + 1500) { size = mix(0.72f, 0.9f, seg(t, hit + 800, hit + 1500)); val side = if ((t / 90) % 2 == 0L) -1f else 1f; x = hx + side; vx = side * 8f; shut = false }
                if (t >= hit + 1500) size = 0.9f + 0.3f * sin(seg(t, hit + 1500, hit + 2200) * PIF) + 0.1f * seg(t, hit + 1500, hit + 1800)
                if (back > 0f) { x = mix(hx, pe[0], back); y = mix(hy, pe[1], back); vx = pe[2] * back; vy = pe[3] * back }
                // Smoke while it hisses, rising from the moment of the hiss and thinning away after.
                val fume = if (t < hit) 0f else 1f - seg(t, hit + 500, hit + 1400)
                if (fume > 0f) {
                    smokeF.fill(0f); Fire.smoke(smokeF, x, y - 1f, t, 0.7f * fume)
                    val front = (y - 3f - 6.5f * (t - hit) / 1000f).roundToInt()
                    for (i in smokeF.indices) if (i / 25 < front) smokeF[i] = 0f
                    Fire.renderSmoke(c, smokeF)
                }
                wisp(c, st, t, x, y, vx, vy, size, shut = shut, salt = 0)
                if (t in (e + 700) until hit) {
                    val q = seg(t, e + 700, hit)
                    val dy = mix(3f, 9f, q * q)
                    c.rows(DROP, 11, dy.roundToInt() - 1, 0.15f + 0.5f * q)
                }
            }
            "bounce" -> {
                // Down to an unseen floor, three bounces lower each time (squashed on each landing, the ground lit under
                // it), a rest, and up into its flight again.
                val floor = 16f
                val tb = e + 450
                val durs = longArrayOf(800, 620, 440); val hs = floatArrayOf(5.5f, 3.2f, 1.4f)
                val tr = ms - e - 900
                fun height(tt: Long): Float {
                    if (tt < tb) { val q = seg(tt, e, tb); return (floor - mix(ps[1], floor, q * q)) }
                    var at = tb
                    for (i in durs.indices) { if (tt < at + durs[i]) { val q = (tt - at) / durs[i].toFloat(); return 4f * q * (1 - q) * hs[i] }; at += durs[i] }
                    return if (tt < tr) 0f else (floor - mix(floor, pe[1], ease(seg(tt, tr, ms - e))))
                }
                val h = height(t)
                val vy = ((height(t - 20) - height(t + 20)) / 40f * 1000f).coerceIn(-6f, 6f)
                val x = mix(ps[0], pe[0], ease(seg(t, e, ms - e)))
                var landed = false
                var at = tb
                for (d in durs) { at += d; if (t in at - 40 until at + 120) landed = true }
                if (t in tb - 40 until tb + 120) landed = true
                val vx = mix(ps[2], 0f, ease(seg(t, e, e + 400))) + pe[2] * back
                val vyy = if (t < e + 400) mix(ps[3], vy, seg(t, e, e + 400)) else if (back > 0f) mix(vy, pe[3], back) else vy
                // The ground lit by its glow, the brighter the nearer it is.
                val on = ease(seg(t, e, e + 300)) * (1f - ease(seg(t, tr, tr + 500)))
                if (on > 0f) { val gx = x.roundToInt(); val g = (0.1f + 0.25f * (1f - h / 6f).coerceIn(0f, 1f) + (if (landed) 0.15f else 0f)) * on
                    for (k in -3..3) c.put(gx + k, 20, g * (if (abs(k) == 3) 0.6f else 1f)) }
                wisp(c, st, t, x, floor - h, vx, vyy, if (landed) 0.82f else 1f, shut = landed || shutNow)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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

    private fun fish(c: Cv, st: Int, fx: Float, fy: Float, right: Boolean, lit: (Int, Int) -> Float, shut: Boolean = false, turned: List<String>? = null) {
        val rows = turned ?: SHAPES[st].let { if (right) it else it.map { r -> r.reversed() } }
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

    override val pastimes = listOf(
        DrawnPastime("somersault", 4400, "кувыркается",
            "Выплывает на середину и делает сальто: петлю целиком, переворачиваясь на каждой четверти круга, и плывёт дальше."),
        DrawnPastime("school", 5600, "плавает со стайкой",
            "Из темноты появляются две рыбки-мальки и плывут с ней стайкой, сверху и снизу, повторяя каждый её поворот, а потом уплывают вперёд и пропадают в темноте."),
    )

    /** [rows] turned [q] quarter turns anticlockwise on the screen (clockwise when negative). */
    private fun turn(rows: List<String>, q: Int): List<String> {
        var r = rows
        repeat(Math.floorMod(q, 4)) { val w = r[0].length; val src = r; r = List(w) { y -> String(CharArray(src.size) { x -> src[x][w - 1 - y] }) } }
        return r
    }

    /**
     * Its pastimes: they pick the fish up where its calm swim has it, ping for ping with the calm clip, and give it back
     * where the calm swim will be by the end.
     */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val c0 = calmAt(e); val c1 = calmAt(ms - e)
        val (sx, sy, sr) = swim(c0); val (ex, ey, er) = swim(c1)
        val ph = (calmAt(t) % 1600) / 1000f; val sp = 9f
        ping(c, ph, sp)
        // A little more of it shows than the ping alone would light while it plays, so the story reads.
        val lift = 0.3f * (ease(seg(t, e, e + 400)) - ease(seg(t, ms - e - 400, ms - e)))
        val lit = { x: Int, y: Int -> max(lift, echoAt(x.toFloat(), y.toFloat(), ph, sp)) }
        val shutNow = blink(calmAt(t))
        when (col) {
            "somersault" -> {
                // To the middle, one whole loop (up when it swims low, down when high) turning a quarter at a time, and on
                // to where the calm swim goes on.
                val mx = 12f; val my = sy.coerceIn(10f, 14f)
                val a = e + 900; val l1 = a + 1600; val r = 3f
                when {
                    t < a -> { val q = ease(seg(t, e, a)); val right = if (abs(mx - sx) < 1f) sr else mx > sx
                        fish(c, st, mix(sx, mx, q), mix(sy, my, q), right, lit, shutNow) }
                    t < l1 -> {
                        val right = if (abs(mx - sx) < 1f) sr else mx > sx
                        val dx = if (right) 1 else -1; val ys = if (my >= 12f) -1 else 1
                        val th = seg(t, a, l1) * TAU_F
                        val q = Math.floorMod((th / (PIF / 2)).roundToInt(), 4)
                        val rows = turn(SHAPES[st].let { if (right) it else it.map { rr -> rr.reversed() } }, q * dx * -ys)
                        fish(c, st, mx + dx * r * sin(th), my + ys * r * (1 - cos(th)), right, lit, turned = rows)
                    }
                    else -> { val q = ease(seg(t, l1, ms - e)); val right = if (t < ms - e - 250 && abs(ex - mx) >= 1f) ex > mx else er
                        fish(c, st, mix(mx, ex, q), mix(my, ey, q), right, lit, shutNow) }
                }
            }
            "school" -> {
                // Its own swim, carried over from where it was to where the calm swim will be; two little fish come in
                // from behind, follow it (each a moment later, so they copy its turns) and dart off ahead before the end.
                fun hero(tt: Long): Triple<Float, Float, Boolean> {
                    val a = swim(c0 + (tt - e)); val b = swim(c1 - (ms - e - tt))
                    val k = ease(seg(tt, e + 1200, ms - e - 1200))
                    if (k <= 0f) return a
                    if (k >= 1f) return b
                    val x = mix(a.first, b.first, k)
                    val x2 = mix(swim(c0 + (tt + 40 - e)).first, swim(c1 - (ms - e - tt - 40)).first, ease(seg(tt + 40, e + 1200, ms - e - 1200)))
                    return Triple(x, mix(a.second, b.second, k), if (abs(x2 - x) < 0.01f) a.third else x2 > x)
                }
                val (hx, hy, hr) = hero(t)
                for (k in 0..1) {
                    // Each keeps its row over or under it (never touching it) and swims where it was a moment ago.
                    val (fx, _, right) = hero(t - 450L * (k + 1))
                    val dir = if (right) 1f else -1f
                    val inn = ease(seg(t, e + 100 + k * 300, e + 900 + k * 300))
                    val out = ease(seg(t, ms - e - 1300 + k * 200, ms - e - 600 + k * 200))
                    // Out of the dark as the sonar finds it, and back into it: the whole little fish at once.
                    val here = inn * (1 - out)
                    if (here < 0.2f) continue
                    val x = fx - dir * 2f - dir * 4f * (1 - inn) + dir * 4f * out
                    // A whole dark row between it and the hero, counted in rows so rounding never lets them touch.
                    val heroTop = (hy - SHAPES[st].size / 2f).roundToInt()
                    val y = if (k == 0) heroTop - 4 + 1.5f else heroTop + SHAPES[st].size + 1 + 1.5f
                    fish(c, 0, x, y, right, { xx, yy -> max(0.3f * here, echoAt(xx.toFloat(), yy.toFloat(), ph, sp) * here) })
                }
                fish(c, st, hx, hy, hr, lit, shutNow)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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

    override val pastimes = listOf(
        DrawnPastime("top_hat", 5600, "примеряет цилиндр",
            "Луч штрих за штрихом дорисовывает себе цилиндр, лицо дважды приподнимает его, здороваясь, и жмурится от удовольствия; потом луч так же стирает шляпу."),
        DrawnPastime("funny_faces", 4800, "корчит рожицы",
            "Луч рисует рожицу за рожицей: подмигивает, показывает язык, жмурится в улыбке до ушей, и снова своё лицо."),
    )

    /** The point [w] (0..1) of the way along the line through [pts]. */
    private fun along(pts: List<Pair<Float, Float>>, w: Float): Pair<Float, Float> {
        val lens = pts.zipWithNext { a, b -> hypot(b.first - a.first, b.second - a.second) }
        var d = w.coerceIn(0f, 1f) * lens.sum()
        for ((i, l) in lens.withIndex()) {
            if (d <= l || i == lens.size - 1) { val q = (d / l).coerceIn(0f, 1f); return mix(pts[i].first, pts[i + 1].first, q) to mix(pts[i].second, pts[i + 1].second, q) }
            d -= l
        }
        return pts.last()
    }

    /**
     * The point [w] of a top hat on the head drawn as far as [k] (0..1, w < k): the brim out from the middle of the head
     * each way, then the crown. Each half of the brim grows outward but is traced from its tip in to the head, and the
     * crown runs from the brim round to the brim, so the freshest end of every stroke sits on the drawing and the part
     * of the trace that has faded never leaves a loose bit behind. [lift] raises the hat off the head.
     */
    private fun hat(w: Float, k: Float, r: Float, cx: Float, cy: Float, lift: Float = 0f): Pair<Float, Float>? {
        val top = (cy - r).roundToInt() - lift
        val left = (k / 0.2f).coerceIn(0f, 1f) * 5f
        val right = ((k - 0.22f) / 0.2f).coerceIn(0f, 1f) * 5f
        return when {
            w < 0.2f -> (cx - left + w / 0.2f * 5f) to (top - 1f)
            w < 0.22f -> null
            w < 0.42f -> (cx + right - (w - 0.22f) / 0.2f * 5f) to (top - 1f)
            w < 0.46f -> null
            else -> along(listOf((cx - 3f) to (top - 1f), (cx - 3f) to (top - 3f), (cx + 3f) to (top - 3f), (cx + 3f) to (top - 1f)), (w - 0.46f) / 0.54f)
        }
    }

    /**
     * A pulled face at [u]: the head round, the eyes ([eyeL], [eyeR]: 0 open, 1 shut, 2 squeezed into arches),
     * then the mouth ([mouth]: 0 a wide smile, 1 a tongue out, 2 a grin from ear to ear).
     */
    private fun pulled(u: Float, st: Int, eyeL: Int, eyeR: Int, mouth: Int, cx: Float, cy: Float): Pair<Float, Float>? {
        val r = R[st]
        fun eye(k: Float, side: Float, kind: Int): Pair<Float, Float> = when (kind) {
            1 -> (cx + side * r * 0.55f - (if (side < 0) 0f else 2.4f) + k * 2.4f) to (cy - r * 0.2f)
            2 -> (cx + side * r * 0.4f - 1.4f + k * 2.8f) to (cy - r * 0.2f + 0.6f - sin(k * PIF) * 1.6f)
            else -> { val a = k * TAU_F; (cx + side * r * 0.4f + cos(a) * 1.2f) to (cy - r * 0.2f + sin(a) * 1.2f) }
        }
        return when {
            u < 0.55f -> { val a = u / 0.55f * TAU_F; (cx + cos(a) * r) to (cy + sin(a) * r) }
            u < 0.58f -> null
            u < 0.68f -> eye((u - 0.58f) / 0.1f, -1f, eyeL)
            u < 0.71f -> null
            u < 0.81f -> eye((u - 0.71f) / 0.1f, 1f, eyeR)
            u < 0.84f -> null
            else -> { val k = (u - 0.84f) / 0.16f; val my = cy + r * 0.38f
                when (mouth) {
                    1 -> if (k < 0.5f) (cx - r * 0.45f + k / 0.5f * r * 0.9f) to (my + 0.3f) else { val a = (k - 0.5f) / 0.5f * PIF; (cx - 1.6f * cos(a)) to (my + 0.3f + 2.8f * sin(a)) }
                    2 -> if (k < 0.35f) (cx - r * 0.45f + k / 0.35f * r * 0.9f) to (my - 0.3f) else { val a = (k - 0.35f) / 0.65f * PIF; (cx + r * 0.45f * cos(a)) to (my - 0.3f + 2.8f * sin(a)) }
                    else -> (cx - r * 0.45f + k * r * 0.9f) to (my + sin(k * PIF) * 1.4f * r * 0.22f)
                }
            }
        }
    }

    /** Its pastimes: the face never moves, the beam keeps the calm clip's clock, so they start and end on its calm trace. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimeMs(col); val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = calmAt(t)
        val shut = blink(tc, 3200, 2200)
        val cx = 12f; val cy = 12.5f; val r = R[st]
        grid(c)
        when (col) {
            "top_hat" -> {
                // The beam's round grows a stroke at a time into a top hat; the hat is raised twice in greeting, the face
                // beams with its eyes shut; then the strokes unwind from their ends.
                val kh = ease(seg(t, e + 100, e + 1300)) - ease(seg(t, ms - e - 1100, ms - e - 150))
                val tip = t in (e + 1600) until (e + 1900) || t in (e + 2150) until (e + 2450)
                val proud = t in (e + 2700) until (e + 3500)
                val fh = 0.25f * kh; val end = 1f - fh
                trace(c, tc, 1100f) { u ->
                    if (u < end) face(u / end, st, 1f, proud || shut, cx, cy)
                    else hat((u - end) / 0.25f, kh, r, cx, cy, if (tip) 1f else 0f)
                }
            }
            "funny_faces" -> {
                // A wink, a tongue (eyes shut, teasing), a grin from ear to ear with the eyes squeezed, its own face again.
                val k = ((t - e - 150) / 1150).toInt()
                if (t < e + 150 || k > 2) trace(c, tc, 1100f) { u -> face(u, st, 1f, shut, cx, cy) }
                else trace(c, tc, 1100f) { u ->
                    when (k) { 0 -> pulled(u, st, 1, 0, 0, cx, cy); 1 -> pulled(u, st, 1, 1, 1, cx, cy); else -> pulled(u, st, 2, 2, 2, cx, cy) }
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
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
