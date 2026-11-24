package n7.glyphpet.pet.drawn

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The last of the Supporter mockups: heroes built on a way of drawing rather than a sprite: the cat against the sun, a
 * head lit in 3D, a shadow play, a diorama, an X-ray, a reflection, the sun, the negative cat, the squid's ink and
 * the rolling ball.
 */
internal object GlowHeroes3 {
    val all: List<Anomaly> = listOf(EclipseCat, Lumen, ShadowPlay, Meadow, XRay, Pond, Sunny, NegCat, Squid, Kolobok)
}

private val PIF = PI.toFloat()
private const val TAU_F = 6.2831855f

/** Rounds a lit value to five steps, so shading reads crisp on the LEDs. */
private fun q5(v: Float) = if (v <= 0f) 0f else min(1f, kotlin.math.ceil(v * 5f) / 5f)

/** The calm clip's own clock at pastime time [t]: the moment [Anomaly.calmNow] draws then. */
private fun Anomaly.calmAt(t: Long): Long = Math.floorMod(calmT(t), Drawn.length("calm"))

/**
 * A clock for the calm clip's own motion (a sway, turning rays, ripples) through a pastime [ms] long: it runs on from
 * where the calm clip is at the start and lands where the calm clip will be at the end (as far as a motion that repeats
 * every [period] ms can tell), the difference eased in on the way, so that motion carries on smoothly through it.
 */
private fun Anomaly.calmRun(t: Long, ms: Long, period: Float): Long {
    val e = Drawn.PASTIME_EDGE
    val a = calmAt(e).toFloat()
    var d = (calmAt(ms - e) - a - (ms - 2 * e)) % period
    if (d > period / 2) d -= period
    if (d < -period / 2) d += period
    return (a + (t - e) + d * ease(seg(t, e, ms - e))).roundToInt().toLong()
}

/**
 * Plays [col] if it is one of the hero's pastimes ([Anomaly.pastimes]): exactly its calm clip for the first and last
 * [Drawn.PASTIME_EDGE] ms (the engine dissolves there), [body] (given the pastime's length) in between. True if it was one.
 */
private inline fun Anomaly.pastime(c: Cv, col: String, t: Long, st: Int, body: (Long) -> Unit): Boolean {
    val p = pastimes.firstOrNull { it.id == col } ?: return false
    if (t < Drawn.PASTIME_EDGE || t >= p.ms - Drawn.PASTIME_EDGE) calmNow(c, t, st) else body(p.ms)
    return true
}

/** Up and down from 0 to 1 and back: 0 before [a], 1 from [b] to [c], 0 again after [d], eased between. */
private fun hold(t: Long, a: Long, b: Long, c: Long, d: Long) = ease(seg(t, a, b)) - ease(seg(t, c, d))

/** Whether ([x], [y]) is inside a sitting cat's head with its ears, centred at ([cx], [cy]), [k] its size. */
private fun inCatHead(x: Float, y: Float, cx: Float, cy: Float, k: Float = 1f): Boolean {
    val dx = (x - cx) / k; val dy = (y - cy) / k
    if ((dx / 5.5f) * (dx / 5.5f) + ((dy - 1f) / 4.6f) * ((dy - 1f) / 4.6f) <= 1f) return true
    for (d in intArrayOf(-1, 1)) { val ex = dx * d; val h = (-1f - dy) / 5f; if (h in 0f..1f && ex >= 1.2f + 2.3f * h && ex <= 5.8f - 2.3f * h) return true }
    return false
}

/** Whether ([x], [y]) is inside a sitting cat (head and round body), head centred at ([cx], [cy]). */
private fun inCat(x: Float, y: Float, cx: Float, cy: Float, k: Float = 1f): Boolean =
    inCatHead(x, y, cx, cy, 0.9f * k) || ((x - cx) / (4.5f * k)).let { it * it } + ((y - cy - 8f * k) / (5f * k)).let { it * it } <= 1f

/**
 * A face cut into a lit shape: two dark eyes ([eyes] 0 open, 1 shut, 2 wide) and a dark mouth ([mouth] 0 smile,
 * 1 frown, 2 an O, 3 none), eyes [gap] apart round ([cx], [ey]).
 */
private fun face(c: Cv, cx: Int, ey: Int, gap: Int, eyes: Int, mouth: Int, under: Float = 0.6f, lookX: Int = 0, lookY: Int = 0) {
    // [lookX]/[lookY] move only the eyes: where it looks.
    for (d in intArrayOf(-gap, gap)) { val x = cx + d + lookX; val y = ey + lookY
        when (eyes) {
            0 -> { c.cut(x, y); c.cut(x, y - 1) }
            1 -> { c.cut(x - 1, y); c.cut(x, y); c.cut(x + 1, y) }
            else -> { c.cut(x, y); c.cut(x, y - 1); c.cut(x + 1, y); c.cut(x + 1, y - 1) }
        }
    }
    val my = ey + 2 + (if (gap > 2) 1 else 0)
    when (mouth) {
        0 -> { c.cut(cx - 1, my); c.cut(cx, my + 1); c.cut(cx + 1, my) }
        1 -> { c.cut(cx - 1, my + 1); c.cut(cx, my); c.cut(cx + 1, my + 1) }
        2 -> { c.cut(cx, my); c.cut(cx, my + 1) }
    }
}

// --- the cat against the sun --------------------------------------------------------------------------------------------

/** A black cat sitting in front of the sun, only its eyes lit, the corona's rays blazing round it to the edge. */
private object EclipseCat : NewHero(
    "eclipse", listOf("Kitten", "Eclipse Cat", "Corona"), Rarity.RARE,
    "Кот на солнце: чёрный силуэт закрывает солнце, светятся только глаза",
    mapOf("calm" to "корона мерцает", "happy" to "лучи длиннее", "habit" to "поводит ушами", "signature" to "полное затмение",
        "hungry" to "лучи короче", "sad" to "солнце тускнеет", "tired" to "глаза прикрыты", "asleep" to "ночь, тонкий месяц",
        "eating" to "ловит солнечную рыбку", "game" to "закрывает солнце", "loved" to "глаза-дуги", "dizzy" to "лучи кругом",
        "tilt" to "кот съезжает", "charging" to "солнце разгорается", "grow" to "восход"),
) {
    private val K = floatArrayOf(0.7f, 0.85f, 1f)

    /** The sun: a bright disc, [rays] long rays turning, [b] how bright, centred at [cy]. */
    private fun sun(c: Cv, t: Long, rays: Float, b: Float = 1f, spin: Float = 1f, cy: Float = 12f, n: Int = 16) {
        if (rays > 0f) for (i in 0 until n) { val a = i * TAU_F / n + t / 5000f * spin; val len = (1 + 2 * rnd(i, (t / 200).toInt())) * rays
            var j = 0f; while (j <= len) { c.put(12 + cos(a) * (10 + j), cy + sin(a) * (10 + j), (if (j + 1 > len) 1f else 0.5f) * b); j += 1f } }
        for (y in 0..24) for (x in 0..24) { val d = hypot(x - 12f, y - cy); if (d <= 9.3f) c.put(x, y, (if (d > 8.4f) 1f else 0.62f) * b) }
    }

    /**
     * The cat's head cut out of the sun, its eyes lit: [eyes] 0 open slits, 1 shut, 2 happy arcs, 3 none (its back
     * turned). [dy] raises or lowers the head, [look] moves the eyes sideways, [mouth] 1..3 opens a yawn the sun shines
     * through.
     */
    private fun cat(c: Cv, st: Int, dx: Float = 0f, eyes: Int = 0, ears: Int = 0, eyeB: Float = 1f, dy: Int = 0, look: Int = 0, mouth: Int = 0) {
        val k = K[st]; val cx = 12f + dx; val cy = 13f + dy
        for (y in 0..24) for (x in 0..24) if (inCatHead(x.toFloat(), y + (if (ears != 0 && y < 9 + dy) 0.6f * ears else 0f), cx, cy, k)) c.cut(x, y)
        val ex = (2f * k).roundToInt().coerceAtLeast(2)
        val ey = 13 + dy
        for (d in intArrayOf(-ex, ex)) { val x = (cx + d).roundToInt() + look
            when (eyes) { 0 -> { c.set(x, ey, eyeB); c.set(x, ey + 1, eyeB) }; 1 -> c.set(x, ey + 1, 0.5f * eyeB); 2 -> { c.set(x - 1, ey + 1, eyeB); c.set(x, ey, eyeB); c.set(x + 1, ey + 1, eyeB) } } }
        if (mouth > 0) {
            val mx = cx.roundToInt(); val my = (cy + 3f * k).roundToInt()
            val open = when (mouth) {
                1 -> listOf(0 to 0)
                2 -> listOf(-1 to 0, 0 to 0, 1 to 0, 0 to 1)
                else -> listOf(0 to -1, -1 to 0, 0 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
            }
            for ((ox, oy) in open) c.set(mx + ox, my + oy, 0.62f)
        }
    }

    /**
     * Its tail, a black hook rising up the sun from below: [up] 0..1 how far it has risen, [sway] swings the tip.
     * Cut out of the sun, so it reads as the same silhouette as the head.
     */
    private fun tail(c: Cv, up: Float, sway: Float) {
        if (up <= 0f) return
        var x = 16.5f; var y = 23f + (1f - up) * 13f
        for (i in 0 until 10) {
            val a = -PIF / 2 - 0.02f * i + (if (i > 6) 0.6f * (i - 6) else 0f) + sway * 0.05f * i
            val nx = x + cos(a) * 1.4f; val ny = y + sin(a) * 1.4f
            var s = 0f; while (s <= 1f) { val px = mix(x, nx, s).toInt(); val py = mix(y, ny, s).toInt(); c.cut(px, py); c.cut(px + 1, py); c.cut(px, py + 1); c.cut(px + 1, py + 1); s += 0.25f }
            x = nx; y = ny
        }
    }

    override val pastimes = listOf(
        DrawnPastime("yawn", 4400, "зевает",
            "Жмурится, задирает голову и широко зевает: сквозь открытый рот светит солнце, корона на миг вытягивается. Потом закрывает рот и снова смотрит."),
        DrawnPastime("tail_swish", 4800, "машет хвостом",
            "Отворачивается (глаза гаснут, видно только затылок) и отходит в сторону, а снизу по солнцу поднимается чёрный хвост крючком и машет туда-сюда; потом хвост опускается, кот возвращается и оборачивается."),
    )

    private var tKey = -1
    private var tRay = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                // The corona flickers and turns exactly as in the calm clip all through.
                val tv = calmAt(t)
                when (col) {
                    "yawn" -> {
                        val m = when { t < 1100 -> 0; t < 1500 -> 2; t < 2900 -> 3; t < 3250 -> 2; else -> 0 }
                        val up = t in 1250L until 3150L
                        sun(c, tv, if (t in 1500L until 2900L) 1.5f else 1f)
                        cat(c, st, eyes = if (t in 800L until 3500L) 1 else 0, ears = if (up) 1 else 0, dy = if (up) -1 else 0, mouth = m)
                    }
                    "tail_swish" -> {
                        val up = ease(seg(t, 1000, 1600)) - ease(seg(t, 3300, 3800))
                        val sway = if (t in 1600L until 3300L) sin((t - 1600) / 280f) * 1.6f else 0f
                        // Its eyes go out (its back to us) and its head steps aside, a column at a time, to make room.
                        val dx = when { t < 900 -> 0; t < 1200 -> -((t - 900) / 100 + 1).toInt(); t < 3800 -> -3; t < 4100 -> -3 + ((t - 3800) / 100 + 1).toInt(); else -> 0 }
                        sun(c, tv, 1f)
                        tail(c, up, sway)
                        cat(c, st, dx = dx.toFloat(), eyes = if (t in 800L until 4200L) 3 else 0, ears = if (t in 1600L until 3300L && (t / 500) % 2 == 0L) 1 else 0)
                    }
                }
            }) return
        when (col) {
            "calm" -> { sun(c, t, 1f); cat(c, st, eyes = if (blink(t)) 1 else 0) }
            "happy" -> { sun(c, t, 1.6f, spin = 2f); cat(c, st, eyes = 2) }
            "habit" -> { sun(c, t, 1f); cat(c, st, ears = if (t % 1600 < 300) 1 else 0) }
            "signature" -> { val k = seg(t, 600, 2200) - seg(t, 4200, 5800); sun(c, t, 1f + k * 1.5f, 1f - k * 0.75f); cat(c, st, eyes = 0) }
            "hungry" -> { sun(c, t, 0.5f); cat(c, st); thought(c, listOf("o.o.o", ".ooo.", "o.o.o"), t) }
            "sad" -> { sun(c, t, 0.4f, 0.45f); cat(c, st, eyes = 1, eyeB = 0.6f) }
            "tired" -> { sun(c, t, 0.7f, 0.8f); cat(c, st, eyes = if ((t / 700) % 2 == 0L) 1 else 0) }
            "asleep" -> {
                // Night: a thin crescent instead of the sun, the cat's outline only where the crescent lights it.
                for (y in 0..24) for (x in 0..24) { val d = hypot(x - 12f, y - 12f); if (d <= 9.3f && hypot(x - 15f, y - 10f) > 8.6f) c.put(x, y, 0.45f) }
                cat(c, st, eyes = 1, eyeB = 0.5f + 0.2f * sin(t / 700f))
            }
            "eating" -> { sun(c, t, 1f); cat(c, st, eyes = 2); if (t < 1800) { val p = seg(t, 0, 1800); c.disc(mix(22f, 12f, p), mix(4f, 15f, p), 0.9f, 1f) } }
            "game" -> { sun(c, t, 1f); cat(c, st, dx = tilt * 4f) }
            "loved" -> { sun(c, t, 1.2f + 0.3f * sin(t / 250f)); cat(c, st, eyes = 2) }
            "dizzy" -> { sun(c, t, 1.2f, spin = 12f); cat(c, st, eyes = 1) }
            "tilt" -> { sun(c, t, 1f); cat(c, st, dx = tilt * 4f) }
            "charging" -> { val p = seg(t, 0, 3000); sun(c, t, p * 1.5f, 0.4f + 0.6f * p); cat(c, st) }
            "grow" -> { val p = seg(t, 0, 4500); sun(c, t, p, 1f, cy = mix(26f, 12f, p)); if (p > 0.7f) cat(c, st, eyes = if (t < 5200) 1 else 0) }
            // Typing: every key a ray shoots out of the corona; at 20 s the corona doubles, at 40 s loops of flame arc
            // off the sun's rim.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tRay = y.now; tKey = y.key }
                sun(c, t, 1f + 0.6f * y.stage, n = if (y.stage >= 1) 24 else 16)
                val a = rnd(y.key, 61) * TAU_F; val len = 12f * (1f - ((y.now - tRay) / 300f).coerceIn(0f, 1f))
                if (len > 0f) c.line(12 + cos(a) * 10f, 12 + sin(a) * 10f, 12 + cos(a) * (10f + len), 12 + sin(a) * (10f + len), 1f)
                if (y.stage == 2) for (k in 0..2) { val a0 = k * 2.1f + t / 1500f; c.ring(12 + cos(a0) * 10f, 12 + sin(a0) * 10f, 2.5f, 0.8f, 0.5f, a0 - 1.2f, a0 + 1.2f) }
                cat(c, st, eyes = if (y.paused && blink(t)) 1 else if (y.stage == 2) 2 else 0)
            }
        }
    }
}

// --- the lit head ---------------------------------------------------------------------------------------------------------

/** A bear's head lit in 3D: a light circles it and its shading turns like a real thing's; the light is its mood. */
private object Lumen : NewHero(
    "lumen", listOf("Cub", "Lumen", "Bust"), Rarity.RARE,
    "Объёмная голова: вокруг ходит свет, тени поворачиваются, как у настоящей",
    mapOf("calm" to "свет медленно обходит", "happy" to "свет кружит быстро", "habit" to "подсвечен снизу, страшилка", "signature" to "свет со всех сторон",
        "hungry" to "свет тусклый", "sad" to "свет сверху, тяжёлые тени", "tired" to "свет гаснет", "asleep" to "лунный свет",
        "eating" to "жуёт под светом", "game" to "светит фонариком", "loved" to "тёплый ровный свет", "dizzy" to "свет мечется",
        "tilt" to "свет от наклона", "charging" to "разгорается", "grow" to "из полутьмы"),
) {
    private val SCALE = floatArrayOf(0.75f, 0.88f, 1f)

    /** The head lit from ([lx], [ly], [lz]) at [power]; [eyes]/[mouth] as in [face]. */
    private fun head(c: Cv, st: Int, lx: Float, ly: Float, lz: Float, power: Float = 1f, eyes: Int = 0, mouth: Int = 0, dy: Int = 0, ambient: Float = 0.12f) {
        val n = sqrt(lx * lx + ly * ly + lz * lz); val k = SCALE[st]
        val balls = arrayOf(floatArrayOf(12f, 14f + dy, 7.5f * k), floatArrayOf(12f - 5f * k, 14f + dy - 7f * k, 2.8f * k), floatArrayOf(12f + 5f * k, 14f + dy - 7f * k, 2.8f * k))
        for (y in 0..24) for (x in 0..24) {
            var best: FloatArray? = null
            for (b in balls) { val dx = (x - b[0]) / b[2]; val dyy = (y - b[1]) / b[2]; val q = dx * dx + dyy * dyy
                if (q <= 1f) { val z = sqrt(1 - q); if (best == null || z * b[2] > best[3]) best = floatArrayOf(dx, dyy, z, z * b[2]) } }
            val bb = best ?: continue
            val dif = max(0f, (bb[0] * lx + bb[1] * ly + bb[2] * lz) / n)
            c.set(x, y, q5(ambient + (1f - ambient) * dif * power))
        }
        face(c, 12, (13 + dy + (1 - st)).coerceAtMost(15), (3 * k).roundToInt(), eyes, mouth)
    }

    private var tKey = -1
    private var tFlash = -99_999L

    override val pastimes = listOf(
        DrawnPastime("sneeze", 4400, "чихает от света",
            "Свет подплывает прямо к носу и разгорается, мишка жмурится, набирает воздух и чихает так, что свет отлетает в сторону, а потом снова идёт по кругу."),
        DrawnPastime("blow_out", 5200, "задувает свет",
            "Свет опускается перед мордочкой, как свечка; мишка дует, свет мигает и гаснет, голова еле видна в темноте. Потом свет вдруг вспыхивает сбоку, и мишка вздрагивает."),
    )

    /** Where the calm clip's light is at its clock [tv]. */
    private fun calmLight(tv: Long): FloatArray { val a = tv / 1100f; return floatArrayOf(cos(a), -0.4f, sin(a) * 0.6f + 0.5f) }

    private fun mixL(p: FloatArray, q: FloatArray, k: Float) = FloatArray(3) { mix(p[it], q[it], k) }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val calm = calmLight(calmRun(t, ms, 1100f * TAU_F))
                when (col) {
                    "sneeze" -> {
                        // The light comes up to its nose; it squints, breathes in (head up), sneezes (head down) and
                        // the light is flung aside, then drifts back onto its round.
                        val front = floatArrayOf(0.35f, -0.25f, 1f); val side = floatArrayOf(1.7f, -0.7f, 0.15f)
                        val l = when {
                            t < 2500 -> mixL(calm, front, ease(seg(t, 480, 1300)))
                            t < 2700 -> mixL(front, side, ease(seg(t, 2500, 2700)))
                            else -> mixL(side, calm, ease(seg(t, 2700, ms - Drawn.PASTIME_EDGE)))
                        }
                        val near = if (t < 2500) ease(seg(t, 480, 1300)) else 1f - seg(t, 2500, 2700)
                        val dy = when { t in 1900L until 2500L -> -1; t in 2500L until 2900L -> 1; else -> 0 }
                        head(c, st, l[0], l[1], l[2], ambient = 0.12f + 0.12f * near, dy = dy,
                            eyes = if (t in 1100L until 3000L) 1 else 0, mouth = if (t in 1700L until 2900L) 2 else 0)
                    }
                    "blow_out" -> {
                        // It puffs at the light held before it like a candle; the light flickers out, a moment in the
                        // dark, then the light pops on at the side where its round has got to, and it starts.
                        val candle = floatArrayOf(0.25f, 0.3f, 1f)
                        val l = if (t < 3300) mixL(calm, candle, ease(seg(t, 480, 1200))) else calm
                        val flick = if (t in 1500L until 1900L && (t / 80) % 2 == 0L) 0.65f else 1f
                        val power = when { t < 1900 -> flick; t < 2200 -> 1f - seg(t, 1900, 2200); t < 3300 -> 0f; else -> seg(t, 3300, 3400) }
                        val eyes = when { t in 2600L until 2800L -> 1; t in 3300L until 3800L -> 2; else -> 0 }
                        val mouth = when { t in 1300L until 2200L -> 2; t in 2200L until 3300L -> 3; t in 3300L until 3700L -> 2; else -> 0 }
                        head(c, st, l[0], l[1], l[2], power = power, eyes = eyes, mouth = mouth)
                    }
                }
            }) return
        val a = t / 1100f
        when (col) {
            "calm" -> head(c, st, cos(a), -0.4f, sin(a) * 0.6f + 0.5f, eyes = if (blink(t)) 1 else 0)
            "happy" -> { val f = t / 400f; head(c, st, cos(f), -0.4f, sin(f) * 0.6f + 0.6f, mouth = 0, dy = -hop(t, 400f, 2f).roundToInt()) }
            "habit" -> head(c, st, sin(t / 400f) * 0.3f, 1f, 0.35f, eyes = 2, mouth = 2)
            "signature" -> { val k = seg(t, 600, 2000) - seg(t, 4400, 6000); head(c, st, cos(a * 3), -0.3f, 0.8f, power = 1f, ambient = 0.12f + k * 0.6f, eyes = if (k > 0.5f) 2 else 0) }
            "hungry" -> { head(c, st, cos(a), -0.4f, 0.6f, power = 0.5f, mouth = 2); thought(c, listOf(".o.", "ooo", ".o."), t) }
            "sad" -> head(c, st, sin(t / 900f) * 0.4f, -1f, 0.25f, power = 0.8f, mouth = 1, dy = if (t % 3200 in 1600L..2000L) 1 else 0)
            "tired" -> head(c, st, cos(a * 0.5f), -0.4f, 0.6f, power = 0.4f + 0.3f * (0.5f + 0.5f * sin(t / 600f)), eyes = 1)
            "asleep" -> head(c, st, -0.8f, -0.6f, 0.3f, power = 0.3f + 0.15f * sin(t / 650f), eyes = 1, mouth = 3, ambient = 0.06f)
            "eating" -> head(c, st, cos(a), -0.4f, 0.7f, mouth = if ((t / 250) % 2 == 0L) 2 else 0)
            "game" -> head(c, st, tilt, -0.3f, 0.7f)
            "loved" -> { head(c, st, 0.2f + sin(t / 500f) * 0.3f, -0.3f, 1f, eyes = 1, mouth = 0, ambient = 0.3f); hearts(c, 12f, 4f, t) }
            "dizzy" -> { val f = t / 150f; head(c, st, cos(f), sin(f), 0.4f, eyes = 1, mouth = 2) }
            "tilt" -> head(c, st, tilt * 1.2f, -0.3f, 0.6f)
            "charging" -> head(c, st, cos(a), -0.4f, 0.6f, power = seg(t, 0, 3000))
            "grow" -> head(c, st, cos(a), -0.4f, 0.6f, power = seg(t, 0, 4000), eyes = if (t < 4400) 1 else 0)
            // Typing: every key the light jumps to a new side; at 20 s a second light joins it, at 40 s it is under a
            // spinning disco light.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tFlash = y.now; tKey = y.key }
                val la = if (y.stage == 2) t / 160f else rnd(y.key, 71) * TAU_F
                val flash = (1f - (y.now - tFlash) / 250f).coerceIn(0f, 1f)
                head(c, st, cos(la), sin(la) * 0.6f, 0.5f, power = 0.75f + 0.25f * flash, ambient = if (y.stage >= 1) 0.3f else 0.12f, eyes = if (y.paused && blink(t)) 1 else if (y.stage == 2) 2 else 0)
                if (y.stage == 2) for (i in 0 until 6) { val b = t / 300f + i * 1.05f; c.set((12 + cos(b) * 11f).roundToInt(), (12 + sin(b) * 11f).roundToInt(), 0.8f) }
            }
        }
    }
}

// --- the shadow play --------------------------------------------------------------------------------------------------------

/** A little bunny under a swinging lamp: its huge shadow sweeps the lit wall behind it. */
private object ShadowPlay : NewHero(
    "shadowplay", listOf("Puppet", "Shadow Bunny", "Shadow Theatre"), Rarity.COMMON,
    "Театр теней: качается лампа, огромная тень зайца гуляет по стене",
    mapOf("calm" to "лампа покачивается", "happy" to "тень скачет", "habit" to "уши шевелятся", "signature" to "тень превращается в птицу",
        "hungry" to "тень тянется к морковке", "sad" to "уши опущены", "tired" to "лампа тускнеет", "asleep" to "лампа погасла",
        "eating" to "тень хрумкает морковку", "game" to "ловит тенью бабочек", "loved" to "тень-сердце", "dizzy" to "лампа кругом",
        "tilt" to "лампа от наклона", "charging" to "лампа разгорается", "grow" to "лампа зажигается"),
) {
    private val BUN = listOf("#...#", "#...#", "##.##", ".###.", "#####", "#-#-#", "#####", ".###.")
    private val BUN_DOWN = listOf(".....", "##.##", "##.##", ".###.", "#####", "#-#-#", "#####", ".###.")
    private val BIRD = listOf("#.....#", "##...##", ".#####.", "..###..", "...#...", "..#.#..", ".......", ".......")

    private fun inShape(rows: List<String>, x: Float, y: Float, left: Int, top: Int): Boolean {
        val i = x.roundToInt() - left; val j = y.roundToInt() - top
        return j in rows.indices && i in rows[j].indices && rows[j][i] != '.'
    }

    /** The scene: the wall lit by a lamp at [lx], the shadow of [shadow] cast large, the bunny in front ([bunny]). */
    private fun scene(c: Cv, st: Int, lx: Float, light: Float = 1f, bunny: List<String> = BUN, shadow: List<String> = bunny, by: Int = 13, shut: Boolean = false, sdx: Float = 0f, sdy: Float = 0f) {
        // [sdx]/[sdy] move the shadow away from where the lamp casts it (a shadow with a mind of its own).
        val ly = 3f; val left = 10; val big = 2.2f - st * 0.15f
        for (y in 0..24) for (x in 0..24) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val d = hypot(x - lx, y - ly)
            var v = max(0.1f, 0.55f - d * 0.025f) * light
            val sx = 12 - (lx - 12) * 0.9f; val qx = (x - sx - sdx) / big + 12f; val qy = (y - 11f - sdy) / big + by + 2.5f
            if (inShape(shadow, qx, qy, left, by)) v = 0f
            c.set(x, y, q5(v))
        }
        bunny.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(left + i, by + j, 0.95f * max(0.4f, light)); '-' -> if (shut) c.set(left + i, by + j, 0.6f) else c.cut(left + i, by + j) } } }
        if (light > 0.05f) { c.put(lx, ly, light); c.put(lx - 1, ly, 0.8f * light); c.put(lx + 1, ly, 0.8f * light); for (y in 0 until ly.toInt()) c.put(lx, y.toFloat(), 0.4f) }
    }

    private var tKey = -1
    private var tKeyAt = -99_999L

    override val pastimes = listOf(
        DrawnPastime("shadow_walk", 5200, "тень уходит гулять",
            "Огромная тень зайца отрывается от него и вприпрыжку уходит со стены; зайчик опускает уши. Потом тень крадётся обратно с другой стороны и встаёт на место."),
        DrawnPastime("push_lamp", 4800, "толкает лампу",
            "Лампа останавливается над зайчиком, он подпрыгивает и стукает её ушами: лампа широко раскачивается, тень мечется по стене, потом всё успокаивается."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val calmLx = 12 + sin(calmRun(t, ms, 1250f * TAU_F) / 1250f) * 8f
                val big = 2.2f - st * 0.15f
                when (col) {
                    "shadow_walk" -> {
                        // Off it hops to the right, gone a moment, and creeps back in from the left.
                        val sdx = when {
                            t < 2000 -> 26f * ease(seg(t, 800, 2000))
                            t < 2600 -> 26f
                            else -> -26f * (1f - ease(seg(t, 2600, 3700)))
                        }
                        val sdy = if (t in 800L until 2000L) -abs(sin(seg(t, 800, 2000) * 3 * PIF)) * 3f else 0f
                        scene(c, st, calmLx, bunny = if (t in 1700L until 3700L) BUN_DOWN else BUN, sdx = sdx, sdy = sdy, shut = t in 2300L until 2460L)
                    }
                    "push_lamp" -> {
                        // The lamp comes to rest over it; it crouches, jumps and knocks it with its ears, and the lamp
                        // swings wide, the swing dying away into the lamp's own sway.
                        val swing = if (t < 1600) mix(calmLx, 12f, ease(seg(t, 480, 1100)))
                            else 12f + 10f * sin((t - 1600) / 240f) * kotlin.math.exp(-(t - 1600) / 1500f)
                        val lx = mix(swing, calmLx, ease(seg(t, 3200, ms - Drawn.PASTIME_EDGE)))
                        val by = when {
                            t in 1100L until 1250L -> 14
                            t in 1250L until 1600L -> mix(13f, 4f, 1f - (1f - seg(t, 1250, 1600)).let { it * it }).roundToInt()
                            t in 1600L until 1900L -> mix(4f, 13f, seg(t, 1600, 1900).let { it * it }).roundToInt()
                            else -> 13
                        }
                        scene(c, st, lx, by = by, sdy = (by - 13) * big * 0.8f, bunny = if (t in 1100L until 1250L) BUN_DOWN else BUN, shut = t in 1900L until 2300L)
                    }
                }
            }) return
        val sway = 12 + sin(t / 1250f) * 8f
        when (col) {
            "calm" -> scene(c, st, sway, shut = blink(t))
            "happy" -> scene(c, st, 12 + sin(t / 500f) * 8f, by = 13 - hop(t, 350f, 2f).roundToInt())
            "habit" -> scene(c, st, sway, bunny = if (t % 1200 < 600) BUN else BUN_DOWN)
            "signature" -> scene(c, st, sway, shadow = if (t in 1200L..5200L) BIRD else BUN)
            "hungry" -> { scene(c, st, sway); thought(c, listOf("..o", ".oo", "oo."), t) }
            "sad" -> scene(c, st, 12f + sin(t / 1600f) * 2f, 0.7f, bunny = BUN_DOWN)
            "tired" -> scene(c, st, 12 + sin(t / 2500f) * 4f, 0.5f + 0.3f * sin(t / 700f), shut = true)
            "asleep" -> { scene(c, st, 12f, 0.1f, shut = true); zs(c, 15f, 9f, t) }
            "eating" -> { scene(c, st, sway); if (t < 2400) { c.line(15f, 17f, 18f, 15f, 0.8f); c.set(18, 14, 0.6f) } }
            "game" -> scene(c, st, 12f + tilt * 9f)
            "loved" -> { scene(c, st, 12f, shut = true); hearts(c, 12f, 10f, t) }
            "dizzy" -> scene(c, st, 12 + sin(t / 150f) * 9f, shut = true)
            "tilt" -> scene(c, st, 12f + tilt * 9f)
            "charging" -> scene(c, st, 12f, seg(t, 0, 3000))
            "grow" -> scene(c, st, 12f, seg(t, 1000, 3000), shut = t < 3400)
            // Typing: every key the lamp jumps to a new place and the shadow swings after it; at 20 s a second lamp
            // throws a second shadow, at 40 s the shadow turns into a different animal on every key.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tKeyAt = y.now; tKey = y.key }
                val target = 4f + rnd(y.key, 81) * 16f; val prev = 4f + rnd(y.key - 1, 81) * 16f
                val lx = mix(prev, target, ease((y.now - tKeyAt) / 200f))
                val shadow = if (y.stage == 2 && y.key % 2 == 1) BIRD else BUN
                scene(c, st, lx, shadow = shadow, shut = y.paused && blink(t))
                if (y.stage >= 1) { val lx2 = 24f - lx; c.put(lx2, 3f, 0.9f); c.put(lx2 - 1, 3f, 0.7f); c.put(lx2 + 1, 3f, 0.7f) }
            }
        }
    }
}

// --- the diorama -------------------------------------------------------------------------------------------------------------

/** A little critter in a three-layer diorama: hills, grass and it slide apart as the phone leans. */
private object Meadow : NewHero(
    "meadow", listOf("Sprig", "Meadow", "Diorama"), Rarity.COMMON,
    "Диорама: холмы, трава и зверёк расходятся слоями при наклоне",
    mapOf("calm" to "слои дышат", "happy" to "скачет по траве", "habit" to "нюхает цветок", "signature" to "день сменяется ночью",
        "hungry" to "ищет ягоды", "sad" to "дождик", "tired" to "сумерки", "asleep" to "ночь, луна",
        "eating" to "ест ягоду", "game" to "собирает ягоды", "loved" to "солнце и сердечки", "dizzy" to "слои кругом",
        "tilt" to "слои расходятся", "charging" to "солнце встаёт", "grow" to "росток в зверька"),
) {
    /** The diorama leaned by [l]: a back hill, the sky light [sky] (sun or moon), a grass layer, the critter at [px]. */
    private fun scene(c: Cv, st: Int, t: Long, l: Float, sky: Float = 1f, night: Boolean = false, px: Float = 12f, dy: Int = 0, eyes: Int = 0, mouth: Int = 0) {
        for (x in 0..24) { val h = (15 + sin((x + l) * 0.35f) * 2f).roundToInt(); for (y in h..24) c.set(x, y, 0.15f * sky + 0.05f) }
        if (night) { c.disc(18f - l * 0.5f, 5f, 2.3f, 0.6f); c.hole(19f - l * 0.5f, 4.5f, 2f) } else c.disc(18f - l * 0.5f, 5f, 2.4f, 0.65f * sky)
        for (x in 0..24) { val h = (19 + sin((x + l * 3) * 0.6f)).roundToInt(); for (y in h..24) c.set(x, y, 0.35f * sky + 0.08f) }
        val x0 = (px + l * 5).roundToInt(); val r = 2.5f + st * 0.5f
        c.disc(x0.toFloat(), 18f + dy, r + 0.5f, 0.9f)
        c.set(x0 - 2, 14 + dy - st, 0.9f); c.set(x0 + 2, 14 + dy - st, 0.9f); c.set(x0 - 2, 15 + dy - st, 0.9f); c.set(x0 + 2, 15 + dy - st, 0.9f)
        face(c, x0, 17 + dy, 1, eyes, mouth)
        // The front strip of earth, in front of everything: it sinks behind it.
        for (x in 0..24) for (y in 22..24) c.set(x, y, 0.6f)
    }

    /** The kite on its string: the string from ([hx], [hy]) in its paw to the kite at ([kx], [ky]). */
    private fun kite(c: Cv, hx: Float, hy: Float, kx: Float, ky: Float) {
        val x = kx.roundToInt(); val y = ky.roundToInt()
        c.line(hx, hy, x.toFloat(), y + 2f, 0.45f)
        for ((ox, oy) in listOf(0 to -1, -1 to 0, 0 to 0, 1 to 0, 0 to 1, 0 to 2)) c.set(x + ox, y + oy, 0.95f)
    }

    private var tKey = -1

    override val pastimes = listOf(
        DrawnPastime("hide_seek", 5200, "играет в прятки",
            "Прячется: уходит вниз за край земли целиком, выглядывает слева одними глазами, моргает, снова прячется и выскакивает дома."),
        DrawnPastime("kite", 5600, "запускает змея",
            "Из-за спины поднимается воздушный змей на нитке: улетает в небо слева, пляшет на ветру, потом зверёк сматывает нитку обратно."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val l = sin(calmRun(t, ms, 1300f * TAU_F) / 1300f) * 0.6f
                when (col) {
                    "hide_seek" -> {
                        // Down behind the front strip of earth, up again at the left for a peek, down, and up at home.
                        fun down(a: Long, b: Long, from: Int, to: Int) = mix(from.toFloat(), to.toFloat(), ease(seg(t, a, b))).roundToInt()
                        val (px, dy) = when {
                            t < 1500 -> 12f to down(900, 1500, 0, 11)
                            t < 1900 -> 12f to 11
                            t < 3000 -> 7f to down(1900, 2300, 11, 4)
                            t < 3400 -> 7f to down(3000, 3400, 4, 11)
                            else -> 12f to down(3400, 3900, 11, 0)
                        }
                        val eyes = if (t in 2550L until 2710L || t in 4000L until 4160L) 1 else 0
                        scene(c, st, t, l, px = px, dy = dy, eyes = eyes)
                    }
                    "kite" -> {
                        // The kite rises from behind it into the sky at the left, dances on the wind, is reeled back in.
                        scene(c, st, t, l, eyes = if (t in 2600L until 2760L) 1 else 0)
                        val x0 = (12 + l * 5).roundToInt().toFloat()
                        val k = ease(seg(t, 900, 2000)) - ease(seg(t, 3900, 4800))
                        if (k > 0f) {
                            val fly = hold(t, 1700, 2200, 3700, 4000)
                            val kx = mix(x0 - 2f, 6f + sin(t / 380f) * 1.5f * fly, k)
                            val ky = mix(14f, 5f + sin(t / 270f) * 1f * fly, k)
                            kite(c, x0 - 3f, 17f, kx, ky)
                        }
                    }
                }
            }) return
        val l = sin(t / 1300f) * 0.6f
        when (col) {
            "calm" -> scene(c, st, t, l, eyes = if (blink(t)) 1 else 0)
            "happy" -> scene(c, st, t, l, dy = -hop(t, 350f, 2f).roundToInt(), eyes = 2)
            "habit" -> { scene(c, st, t, l, px = 10f); c.set((14 + l * 5).roundToInt(), 18, 1f); c.set((14 + l * 5).roundToInt(), 19, 0.6f) }
            "signature" -> { val k = seg(t, 600, 2400) - seg(t, 4000, 5800); scene(c, st, t, l, sky = 1f - k * 0.6f, night = k > 0.5f, eyes = if (k > 0.5f) 2 else 0) }
            "hungry" -> { scene(c, st, t, l, mouth = 2); thought(c, listOf(".o.", "ooo", ".o."), t) }
            "sad" -> { scene(c, st, t, l, 0.6f, mouth = 1); rain(c, t) }
            "tired" -> scene(c, st, t, l, 0.5f, eyes = 1)
            "asleep" -> scene(c, st, t, l * 0.3f, 0.4f, night = true, eyes = 1, mouth = 3)
            "eating" -> { scene(c, st, t, l, mouth = if ((t / 250) % 2 == 0L) 2 else 0); if (t < 1500) c.disc(mix(20f, 13f, seg(t, 0, 1500)), 17f, 0.8f, 1f) }
            "game" -> scene(c, st, t, tilt)
            "loved" -> { scene(c, st, t, l, eyes = 1); hearts(c, 12f, 10f, t) }
            "dizzy" -> scene(c, st, t, sin(t / 200f) * 1.2f, eyes = 1, mouth = 2)
            "tilt" -> scene(c, st, t, tilt)
            "charging" -> scene(c, st, t, l, seg(t, 0, 3000))
            "grow" -> { if (t < 2600) { scene(c, 0, t, l); c.line(12f, 21f, 12f, 21f - seg(t, 0, 2600) * 4, 0.6f) } else scene(c, st, t, l) }
            // Typing: every key a flower pops up in the grass; at 20 s butterflies flutter over them, at 40 s
            // fireworks burst in the sky behind.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) tKey = y.key
                scene(c, st, t, l, eyes = if (y.paused && blink(t)) 1 else 0, mouth = if (y.stage == 2) 2 else 0)
                for (n in 0..min(y.key, 14)) { val fx = (2 + rnd(n, 91) * 20 + l * 3).roundToInt(); if (abs(fx - 12) < 4) continue
                    c.set(fx, 20, 0.5f); c.set(fx, 19, if (n == y.key && y.sinceKey < 200) 1f else 0.85f) }
                if (y.stage >= 1) for (b in 0..1) { val bx = 12 + sin(t / 700f + b * 3) * 8; val by = 11 + cos(t / 500f + b) * 3; val flap = (t / 120 + b) % 2 == 0L
                    c.put(bx, by, 0.9f); c.put(bx - 1, by - (if (flap) 1 else 0), 0.6f); c.put(bx + 1, by - (if (flap) 1 else 0), 0.6f) }
                if (y.stage == 2) { val q = (y.stageMs % 1500) / 1500f; val fx = 6f + ((y.stageMs / 1500) % 3) * 6f; c.ring(fx, 6f, q * 4f, 0.8f * (1 - q)) }
            }
        }
    }
}

// --- the X-ray cat --------------------------------------------------------------------------------------------------------

/** A cat under an X-ray scanner: a bar runs up and down it, and under the bar its bones show. */
private object XRay : NewHero(
    "xray", listOf("Kitten", "X-Ray Cat", "Skeleton"), Rarity.RARE,
    "Рентген: по коту бежит полоса сканера, под ней видны кости",
    mapOf("calm" to "сканер ходит", "happy" to "сканер быстрее", "habit" to "умывается", "signature" to "весь скелет вспышкой",
        "hungry" to "в животе пусто", "sad" to "сканер медленный", "tired" to "сканер замирает", "asleep" to "сканер выключен",
        "eating" to "видно рыбку внутри", "game" to "ищет кость в багаже", "loved" to "на снимке сердечко", "dizzy" to "кости кругом",
        "tilt" to "сканер от наклона", "charging" to "сканер прогревается", "grow" to "снимок проявляется"),
) {
    private val K = floatArrayOf(0.75f, 0.88f, 1f)

    private fun bone(x: Int, y: Int, k: Float): Boolean {
        val cx = 12f; val cy = 9f
        val sk = hypot(x - cx, (y - cy) / 1f)
        if (abs(sk - 3f * k) < 0.6f && y < cy + 3) return true
        if ((x == 11 || x == 13) && y == 9) return true
        if (x == 12 && y >= (cy + 3 * k).toInt() && y <= (cy + 12 * k).toInt()) return true
        if (y >= (cy + 5 * k).toInt() && y <= (cy + 10 * k).toInt() && y % 2 == 0 && abs(x - 12) <= 3) return true
        return false
    }

    /** The cat, its skeleton showing within [w] rows of [bar] (or all over with [all]); [inside] draws a shape in its belly. */
    private fun scan(c: Cv, st: Int, bar: Float, w: Float = 2f, all: Boolean = false, eyes: Int = 0, inside: List<String>? = null, b: Float = 1f, dy: Int = 0) {
        // [dy] moves the cat down (or up, leaping) under the scanner.
        val k = K[st]
        for (y in 0..24) for (x in 0..24) {
            if (!inCat(x.toFloat(), (y - dy).toFloat(), 12f, 9f, k)) continue
            val under = all || abs(y - bar) <= w
            c.set(x, y, (if (under) (if (bone(x, y - dy, k)) 0.1f else 0.85f) else 0.45f) * b)
        }
        if (inside != null) inside.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> val yy = 16 + j + dy; if (ch == '#' && (all || abs(yy - bar) <= w + 2)) c.set(10 + i, yy, 0.1f) } }
        val ey = 9 + dy
        if (!all && abs(ey - bar) > w) { if (eyes == 1) { c.cut(9, ey); c.cut(10, ey); c.cut(14, ey); c.cut(15, ey) } else { c.cut(10, ey); c.cut(14, ey); c.cut(10, ey - 1); c.cut(14, ey - 1) } }
        if (!all) for (x in 5..19) { c.put(x.toFloat(), bar - w - 1, 0.25f * b); c.put(x.toFloat(), bar + w + 1, 0.25f * b) }
    }

    /**
     * A tail curling up from its right hip, [len] 0..1 of it out, its tip swung by [sway]: grey flesh like the body, and
     * under the scanner bar ([bar], [w]) bright with its little vertebrae dark in it.
     */
    private fun tail(c: Cv, st: Int, len: Float, sway: Float, bar: Float, w: Float = 2f) {
        val k = K[st]
        val n = (16 * len).roundToInt(); if (n <= 0) return
        var x = 15f; var y = 20f
        for (i in 0 until n) {
            val a = -0.35f - i * 0.085f + sway * 0.03f * i
            x += cos(a) * 0.65f; y += sin(a) * 0.65f
            val px = x.roundToInt(); val py = y.roundToInt()
            for (ox in 0..1) {
                if (inCat((px + ox).toFloat(), py.toFloat(), 12f, 9f, k)) continue
                val under = abs(py - bar) <= w
                c.set(px + ox, py, if (!under) 0.45f else if (ox == 0 && i % 4 == 3) 0.1f else 0.85f)
            }
        }
    }

    private val FISH = listOf(".#.#.", "#####", ".#.#.")

    private var tKey = -1

    override val pastimes = listOf(
        DrawnPastime("tail_bones", 5200, "хвост на просвет",
            "Из-за спины вылезает хвост и виляет; сканер съезжает к нему и ходит вдоль хвоста, а в нём видны косточки-позвонки. Потом хвост прячется."),
        DrawnPastime("pounce_bar", 4800, "охотится на полосу",
            "Полоса сканера замирает над котом и подрагивает, как лазерная указка; кот припадает, прыгает за ней — на снимке видно его череп, — а полоса удирает вниз."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val calmBar = 12 + sin(calmRun(t, ms, 900f * TAU_F) / 900f) * 10f
                val end = ms - Drawn.PASTIME_EDGE
                when (col) {
                    "tail_bones" -> {
                        // The bar leaves its round for the tail and runs up and down it, then goes back to its round.
                        val bar = mix(calmBar, 16f + sin((t - 1300) / 420f) * 4.5f, hold(t, 900, 1500, 3700, end))
                        val len = ease(seg(t, 650, 1250)) - ease(seg(t, 3900, 4400))
                        scan(c, st, bar, eyes = if (t in 2400L until 2560L) 1 else 0)
                        tail(c, st, len, sin((t - 650) / 260f) * 2f, bar)
                    }
                    "pounce_bar" -> {
                        // The bar comes to hover over its ears and twitches; it crouches, leaps into it (its skull shows
                        // on the scan) and the bar zips away down through it, back into its round.
                        val twitch = if (t in 1100L until 2050L) (if ((t / 120) % 3 == 0L) 1f else if ((t / 120) % 3 == 1L) -1f else 0f) else 0f
                        val hover = 3f + twitch
                        val bar = when {
                            t < 2050 -> mix(calmBar, hover, ease(seg(t, 480, 1100)))
                            t < 2400 -> mix(3f, 23f, seg(t, 2050, 2400))
                            else -> mix(23f, calmBar, ease(seg(t, 2400, end)))
                        }
                        val dy = when {
                            t in 1250L until 1800L -> 1
                            t in 1800L until 1950L -> -1
                            t in 1950L until 2200L -> -2
                            t in 2200L until 2350L -> -1
                            else -> 0
                        }
                        scan(c, st, bar, dy = dy, eyes = if (t in 2350L until 2700L) 1 else 0)
                    }
                }
            }) return
        val bar = 12 + sin(t / 900f) * 10f
        when (col) {
            "calm" -> scan(c, st, bar, eyes = if (blink(t)) 1 else 0)
            "happy" -> scan(c, st, 12 + sin(t / 350f) * 10f)
            "habit" -> scan(c, st, bar, eyes = 1)
            "signature" -> { val all = t % 1600 < 300; scan(c, st, bar, all = all) }
            "hungry" -> { scan(c, st, bar); thought(c, FISH, t) }
            "sad" -> scan(c, st, 12 + sin(t / 2000f) * 10f, b = 0.7f, eyes = 1)
            "tired" -> scan(c, st, 12 + sin(t / 2600f) * 8f, eyes = if ((t / 700) % 2 == 0L) 1 else 0)
            "asleep" -> scan(c, st, -10f, b = 0.45f + 0.1f * sin(t / 700f), eyes = 1)
            "eating" -> scan(c, st, mix(4f, 18f, seg(t, 0, 3200)), inside = if (t > 800) FISH else null)
            "game" -> scan(c, st, 12f - tilt * 12f)
            "loved" -> { scan(c, st, bar, inside = listOf("#.#", "###", ".#.")); hearts(c, 12f, 6f, t) }
            "dizzy" -> scan(c, st, 12 + sin(t / 150f) * 10f, eyes = 1)
            "tilt" -> scan(c, st, 12f - tilt * 12f)
            "charging" -> scan(c, st, mix(-4f, 26f, seg(t, 0, 3000)), w = 1f)
            "grow" -> scan(c, st, bar, b = seg(t, 0, 3500))
            // Typing: every key the scanner flashes; at 20 s it sweeps fast, at 40 s the whole skeleton dances on the
            // screen to the keys.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) tKey = y.key
                val fast = if (y.stage >= 1) 300f else 900f
                val flash = y.sinceKey < 150
                if (y.stage == 2) { val sh = if (y.key % 2 == 0) -1 else 1; val g = Cv(); scan(g, st, 0f, all = true); for (yy in 0..24) for (x in 0..24) { val v = g.get(x, yy); if (v > 0f) c.set(x + (if (yy > 12) sh else 0), yy, v) } }
                else scan(c, st, 12 + sin(t / fast) * 10f, all = flash, eyes = if (y.paused && blink(t)) 1 else 0)
            }
        }
    }
}

// --- the reflection in the pond ---------------------------------------------------------------------------------------------

/** A round critter on the bank, its reflection trembling in the water below it. */
private object Pond : NewHero(
    "pond", listOf("Droplet", "Pondling", "Mirror"), Rarity.COMMON,
    "Отражение: сидит у воды, внизу дрожит его отражение",
    mapOf("calm" to "отражение дрожит", "happy" to "прыгает, круги", "habit" to "смотрит в воду", "signature" to "ныряет и выныривает",
        "hungry" to "ловит взглядом рыбку", "sad" to "дождь по воде", "tired" to "вода гладкая", "asleep" to "луна в воде",
        "eating" to "ловит мошку", "game" to "ловит стрекоз", "loved" to "сердечки в воде", "dizzy" to "водоворот",
        "tilt" to "вода плещется", "charging" to "вода светлеет", "grow" to "капля в зверька"),
) {
    private const val W = 13

    /** The critter at ([x], [y]) above the water; mirrored below with ripples of [amp]; [moon] puts a moon in the sky. */
    private fun scene(
        c: Cv, st: Int, t: Long, x: Float = 12f, y: Float = 9f, amp: Float = 0.12f, eyes: Int = 0, mouth: Int = 0, moon: Boolean = false, under: Float = 0f,
        rx: Float = x, reyes: Int = eyes, extra: ((Cv) -> Unit)? = null,
    ) {
        // The reflection is of the same bank unless told otherwise: [rx]/[reyes] give it a mind of its own, and
        // [extra] adds something to the bank (it shows in the water too).
        fun bank(g: Cv, x: Float, eyes: Int) {
            val r = 3.2f + st * 0.65f
            if (under < 1f) {
                g.disc(x, y + under * 6, r, 0.85f); g.ring(x, y + under * 6, r, 1f, 0.45f)
                for (d in intArrayOf(-1, 1)) { g.set((x + d * 3).roundToInt(), (y - r).roundToInt(), 0.9f); g.set((x + d * 3).roundToInt(), (y - r - 1).roundToInt(), 0.9f) }
                face(g, x.roundToInt(), y.roundToInt(), 2, eyes, mouth)
            }
            if (moon) { g.disc(20f, 3f, 1.6f, 0.6f) }
            extra?.invoke(g)
        }
        val g = Cv(); bank(g, x, eyes)
        val m = if (rx == x && reyes == eyes) g else Cv().also { bank(it, rx, reyes) }
        for (yy in 0 until W) for (xx in 0..24) { val v = g.get(xx, yy); if (v > 0f) c.set(xx, yy, v) }
        for (xx in 0..24) c.set(xx, W, 0.5f)
        for (yy in W + 1..24) { val sy = 2 * W - yy; val off = (sin(yy * 1.3f - t / 250f) * (yy - W) * amp).roundToInt()
            for (xx in 0..24) { val sx = xx - off; if (sx in 0..24 && sy >= 0) { val v = m.get(sx, sy); if (v > 0f) c.set(xx, yy, q5(v * 0.45f)) } } }
    }

    private var tKey = -1
    private var tDrop = -99_999L

    override val pastimes = listOf(
        DrawnPastime("lagging", 5200, "отражение отстаёт",
            "Шагает влево и вправо, а отражение в воде еле поспевает; потом отражение само уходит в сторону и подмигивает, зверёк таращит глаза. Отражение возвращается на место."),
        DrawnPastime("skip_stone", 4800, "пускает блинчики",
            "Отходит влево и бросает камешек: тот скачет по воде всё ниже, от каждого касания разбегается рябь, и тонет у края. Зверёк возвращается на место."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val tv = calmAt(t)
                when (col) {
                    "lagging" -> {
                        // Where it stands, step by step; the reflection follows 350 ms late, then goes its own way.
                        fun at(q: Long): Float = when {
                            q < 900 -> 12f
                            q < 1900 -> mix(12f, 9f, ease(seg(q, 900, 1200)))
                            q < 3700 -> mix(9f, 15f, ease(seg(q, 1900, 2300)))
                            else -> mix(15f, 12f, ease(seg(q, 3700, 4100)))
                        }.roundToInt().toFloat()
                        val x = at(t)
                        val rx = when {
                            t < 2700 -> at(t - 350)
                            t < 3400 -> mix(15f, 9f, ease(seg(t, 2700, 3000))).roundToInt().toFloat()
                            t < 3700 -> mix(9f, 15f, ease(seg(t, 3400, 3600))).roundToInt().toFloat()
                            else -> x
                        }
                        val eyes = when { t in 2850L until 3550L -> 2; t in 3700L until 3860L -> 1; else -> 0 }
                        val reyes = when { t in 3050L until 3300L -> 1; t in 2850L until 3550L -> 0; else -> eyes }
                        scene(c, st, tv, x = x, rx = rx, eyes = eyes, reyes = reyes, mouth = if (t in 2950L until 3450L) 2 else 0, amp = 0.12f * (1f - hold(t, 480, 880, 4150, ms - Drawn.PASTIME_EDGE)))
                    }
                    "skip_stone" -> {
                        // It steps left and throws; a 2x2 pebble skips along the water, lower each time, and sinks.
                        val x = when { t < 3300 -> mix(12f, 7f, ease(seg(t, 850, 1250))); else -> mix(7f, 12f, ease(seg(t, 3300, 3900))) }.roundToInt().toFloat()
                        val hops = listOf(
                            floatArrayOf(12f, 13f, 3f, 1250f, 1550f), floatArrayOf(13f, 16.5f, 3f, 1550f, 1850f),
                            floatArrayOf(16.5f, 19f, 2f, 1850f, 2100f), floatArrayOf(19f, 21f, 1f, 2100f, 2300f),
                        )
                        val h = hops.firstOrNull { t >= it[3] && t < it[4] }
                        val stone: Pair<Int, Int>? = when {
                            h != null -> { val p = seg(t, h[3].toLong(), h[4].toLong())
                                mix(h[0], h[1], p).roundToInt() to (11f - (if (h === hops[0]) mix(4f, 0f, p) + sin(p * PIF) * 2f else sin(p * PIF) * h[2])).roundToInt() }
                            t in 2300L until 2500L -> 21 to 11
                            else -> null
                        }
                        val throwHop = if (t in 1300L until 1450L) 1f else 0f
                        scene(c, st, tv, x = x, y = 9f - throwHop, amp = 0.12f * (1f - hold(t, 480, 840, 4000, ms - Drawn.PASTIME_EDGE)), eyes = if (t in 1500L until 2900L) 2 else 0, extra = stone?.let { (sx, sy) ->
                            { g: Cv -> for (oy in 0..1) for (ox in 0..1) g.set(sx + ox, sy + oy, 0.9f) } })
                        // Ripples run out from each touch along the water line, and from where it sinks.
                        for ((tx, at) in listOf(13f to 1550L, 16.5f to 1850L, 19f to 2100L, 21f to 2300L, 21.5f to 2500L)) {
                            val q = (t - at) / (if (at == 2500L) 700f else 450f); if (q !in 0f..1f) continue
                            val sp = 1f + q * (if (at == 2500L) 4f else 2.5f)
                            c.put(tx + 0.5f - sp, (W + 1).toFloat(), 0.6f * (1 - q) + 0.15f); c.put(tx + 0.5f + sp, (W + 1).toFloat(), 0.6f * (1 - q) + 0.15f)
                        }
                    }
                }
            }) return
        when (col) {
            "calm" -> scene(c, st, t, eyes = if (blink(t)) 1 else 0)
            "happy" -> { val h = hop(t, 400f, 3f); scene(c, st, t, y = 9f - h, amp = 0.25f, eyes = 2) }
            "habit" -> scene(c, st, t, y = 10f, eyes = 0, mouth = 2)
            "signature" -> { val k = seg(t, 800, 1800) - seg(t, 3600, 4600); scene(c, st, t, under = k, amp = 0.12f + 0.4f * k); if (k > 0.3f) c.ring(12f, W.toFloat(), 2f + k * 6, 0.6f) }
            "hungry" -> { scene(c, st, t, mouth = 2); thought(c, listOf(".o.o", "oooo", ".o.o"), t) }
            "sad" -> { scene(c, st, t, amp = 0.3f, mouth = 1); rain(c, t) }
            "tired" -> scene(c, st, t, amp = 0.06f, eyes = if ((t / 700) % 2 == 0L) 1 else 0)
            "asleep" -> { scene(c, st, t, amp = 0.08f, eyes = 1, mouth = 3, moon = true); zs(c, 15f, 4f, t) }
            "eating" -> { scene(c, st, t, mouth = if (t in 1200L..2000L) 2 else 0); if (t < 1400) c.set(mix(22f, 13f, seg(t, 0, 1400)).roundToInt(), 6, 1f) }
            "game" -> scene(c, st, t, x = 12f + tilt * 6f)
            "loved" -> { scene(c, st, t, eyes = 1); hearts(c, 12f, 4f, t) }
            "dizzy" -> scene(c, st, t, amp = 0.5f, eyes = 1, mouth = 2)
            "tilt" -> scene(c, st, t, x = 12f + tilt * 5f, amp = 0.3f)
            "charging" -> scene(c, st, t, amp = 0.12f * (1 - seg(t, 0, 3000)))
            "grow" -> { if (t < 2400) { scene(c, 0, t, under = 1f); c.disc(12f, mix(0f, 9f, seg(t, 0, 2400)), 1f, 0.9f) } else scene(c, st, t, eyes = if (t < 3000) 1 else 0) }
            // Typing: every key a drop falls and rings spread on the water; at 20 s lily pads float out, at 40 s the moon
            // and fireflies shine in the water.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tDrop = y.now; tKey = y.key }
                scene(c, st, t, amp = 0.1f + 0.1f * y.stage, eyes = if (y.paused && blink(t)) 1 else 0, moon = y.stage == 2)
                val a = y.now - tDrop; val dx = 4f + rnd(y.key, 101) * 16f
                if (a < 700) { val q = a / 700f; for (xx in -6..6) { val yy = W + 2 + (abs(xx) * 0.0f).roundToInt(); if (abs(abs(xx) - q * 6) < 0.6f) c.put(dx + xx, (W + 3).toFloat(), 0.7f * (1 - q)) } }
                if (y.stage >= 1) for (k in 0..2) { val lx = 4f + k * 8f + sin(t / 900f + k) * 1.5f; for (i in -1..1) c.set((lx + i).roundToInt(), W + 2 + k * 3, 0.6f) }
                if (y.stage == 2) for (k in 0..3) { val fx = 4f + rnd(k, 103) * 16f; c.put(fx, 5f + sin(t / 400f + k) * 2, 0.9f); c.put(fx, (2 * W - 5f - sin(t / 400f + k) * 2), 0.4f) }
            }
        }
    }
}

// --- the little sun ----------------------------------------------------------------------------------------------------------

/** The sun with a face, as round as the matrix: its rays reach to the very edge and turn. */
private object Sunny : NewHero(
    "sunny", listOf("Sunbeam", "Sunny", "Solar"), Rarity.COMMON,
    "Солнышко: круглое, как матрица, лучи до самого края",
    mapOf("calm" to "лучи крутятся", "happy" to "лучи длиннее", "habit" to "подмигивает", "signature" to "затмение, корона",
        "hungry" to "лучи короче", "sad" to "за тучкой", "tired" to "лучи гаснут", "asleep" to "закат",
        "eating" to "ловит облачко", "game" to "растит цветы", "loved" to "лучи-сердечки", "dizzy" to "лучи кругом",
        "tilt" to "лучи наклоняются", "charging" to "разгорается", "grow" to "рассвет"),
) {
    private val R = floatArrayOf(4f, 4.6f, 5.2f)

    /** [ray] (when given) sets each ray's own length instead of [rays] for all; [lookX]/[lookY] move its eyes. */
    private fun sun(c: Cv, st: Int, t: Long, rays: Float = 1f, cy: Float = 12f, eyes: Int = 0, mouth: Int = 0, spin: Float = 1f, n: Int = 12, b: Float = 1f,
                    ray: ((Int) -> Float)? = null, lookX: Int = 0, lookY: Int = 0) {
        val r = R[st]
        for (i in 0 until n) { val k = ray?.invoke(i) ?: rays; if (k <= 0f) continue
            val a = i * TAU_F / n + t / 4000f * spin; val len = ((1.5f + 1.5f * rnd(i, (t / 200).toInt())) * k).roundToInt()
            for (j in 0..len) c.put(12 + cos(a) * (r + 2 + j), cy + sin(a) * (r + 2 + j), (if (j == len) 1f else 0.55f) * b) }
        for (y in 0..24) for (x in 0..24) { val d = hypot(x - 12f, y - cy); if (d <= r + 0.3f) c.set(x, y, (if (d > r - 0.8f) 0.6f else 0.95f) * b) }
        face(c, 12, cy.roundToInt() - 1, 2, eyes, mouth, lookX = lookX, lookY = lookY)
    }

    /** A grey cloud at ([x], [y]): three puffs, laid over whatever is behind it. */
    private fun cloud(c: Cv, x: Float, y: Float) {
        for (yy in 0..24) for (xx in 0..24) {
            val inC = hypot(xx - (x - 3.2f), yy - (y + 0.6f)) <= 2.3f || hypot(xx - x, yy - (y - 0.6f)) <= 2.9f || hypot(xx - (x + 3.2f), yy - (y + 0.6f)) <= 2.3f
            if (inC) c.set(xx, yy, 0.32f)
        }
    }

    private var tKey = -1

    override val pastimes = listOf(
        DrawnPastime("blow_cloud", 5200, "сдувает тучку",
            "Слева приплывает тучка и закрывает солнышку глаза, оно хмурится, надувает щёки и дует: тучку уносит вправо, а солнышко довольно жмурится."),
        DrawnPastime("ray_count", 4800, "выпускает лучи по одному",
            "Прячет все лучи, потом выпускает их по одному по кругу и следит глазами за каждым новым; когда вышли все, разом вытягивает их — та-да."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val tv = calmAt(t)
                when (col) {
                    "blow_cloud" -> {
                        // The cloud drifts in over its eyes; it frowns, puffs, and blows it away to the right.
                        val cx = when { t < 700 -> -12f; t < 2900 -> mix(-4f, 11f, ease(seg(t, 700, 2100))); else -> mix(11f, 34f, seg(t, 2900, 3300).let { it * it }) }
                        val blow = t in 2900L until 3300L
                        val mouth = when { t in 2200L until 2600L -> 1; t in 2600L until 3300L -> 2; else -> 0 }
                        sun(c, st, tv, rays = if (t in 2600L until 2900L) 0.5f else if (blow) 1.6f else 1f,
                            eyes = if (t in 3300L until 3900L) 1 else 0, mouth = mouth)
                        if (cx < 30f) cloud(c, cx.roundToInt().toFloat(), 9f)
                    }
                    "ray_count" -> {
                        // All rays drawn in, then let out one at a time round the clock, its eyes on the newest one;
                        // when all are out, all stretch long at once.
                        val first = (0 until 12).minByOrNull { i ->
                            // How far ray i is from straight up when the count begins.
                            val a = i * TAU_F / 12 + calmAt(900) / 4000f + PIF / 2
                            val m = ((a % TAU_F) + TAU_F) % TAU_F
                            min(m, TAU_F - m)
                        } ?: 0
                        val out = ((t - 900) / 200).toInt()
                        val ray: (Int) -> Float = { i ->
                            val order = Math.floorMod(i - first, 12)
                            when {
                                t < 900 -> 1f - ease(seg(t, 480, 900))
                                t < 3300 -> if (order < out) 1f else if (order == out) 2f else 0f
                                t < 3700 -> 1.8f
                                else -> 1f
                            }
                        }
                        var lx = 0; var ly = 0
                        if (t in 900L until 3300L) {
                            val a = Math.floorMod(first + out, 12) * TAU_F / 12 + tv / 4000f
                            lx = cos(a).roundToInt(); ly = sin(a).roundToInt()
                        }
                        sun(c, st, tv, ray = ray, eyes = if (t in 3300L until 3700L) 2 else 0, lookX = lx, lookY = ly)
                    }
                }
            }) return
        when (col) {
            "calm" -> sun(c, st, t, eyes = if (blink(t)) 1 else 0)
            "happy" -> sun(c, st, t, 1.6f, spin = 2f, eyes = 2)
            "habit" -> { sun(c, st, t); if (t % 1600 < 400) { c.set(13, 11, 0.95f); c.cut(13, 11); c.cut(14, 11); c.cut(15, 11) } }
            "signature" -> { val k = seg(t, 600, 2000) - seg(t, 4200, 5800); sun(c, st, t, 1f + k, eyes = if (k > 0.5f) 2 else 0, mouth = if (k > 0.5f) 2 else 0); val mx = mix(-6f, 12f, k); for (y in -5..5) for (x in -5..5) if (hypot(x.toFloat(), y.toFloat()) <= 5.3f && k < 0.98f) c.cut((mx + x).roundToInt(), 12 + y) }
            "hungry" -> { sun(c, st, t, 0.5f, mouth = 2); thought(c, listOf("ooo", "o.o", "ooo"), t) }
            "sad" -> { sun(c, st, t, 0.4f, mouth = 1, b = 0.7f); for (i in 0..2) c.disc(8f + i * 4 + sin(t / 900f) * 2, 15f, 2.6f, 0.4f) }
            "tired" -> sun(c, st, t, 0.4f + 0.3f * sin(t / 600f), eyes = 1)
            "asleep" -> { sun(c, st, t, 0f, cy = 17f, eyes = 1, mouth = 3, b = 0.7f); for (x in 0..24) for (y in 19..24) c.set(x, y, if (y == 19) 0.3f else 0f); zs(c, 16f, 8f, t) }
            "eating" -> { sun(c, st, t, mouth = if (t in 1200L..2200L) 2 else 0); if (t < 1500) c.disc(mix(22f, 15f, seg(t, 0, 1500)), 5f, 1.5f, 0.5f) }
            "game" -> sun(c, st, t, 1f, spin = tilt * 6f)
            "loved" -> { sun(c, st, t, 1.2f + 0.3f * sin(t / 250f), eyes = 1); hearts(c, 12f, 4f, t) }
            "dizzy" -> sun(c, st, t, 1.2f, spin = 14f, eyes = 1, mouth = 2)
            "tilt" -> sun(c, st, t, 1f, spin = tilt * 4f)
            "charging" -> sun(c, st, t, seg(t, 0, 3000) * 1.5f, b = 0.4f + 0.6f * seg(t, 0, 3000))
            "grow" -> sun(c, st, t, seg(t, 1500, 4500), cy = mix(24f, 12f, seg(t, 0, 3000)), eyes = if (t < 3500) 1 else 0)
            // Typing: every key the rays shoot out; at 20 s they double, at 40 s rings of a supernova roll out to the rim.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) tKey = y.key
                val shot = y.sinceKey < 120
                sun(c, st, t, if (shot) 1.9f else 1f, n = if (y.stage >= 1) 20 else 12, eyes = if (y.paused && blink(t)) 1 else if (shot) 2 else 0, mouth = if (shot) 2 else 0)
                if (y.stage == 2) { val q = (y.stageMs % 1200) / 1200f; c.ring(12f, 12f, 6f + q * 7f, 0.6f * (1 - q)) }
            }
        }
    }
}

// --- the negative cat ---------------------------------------------------------------------------------------------------------

/** Turned inside out: the whole matrix glows and the cat is a dark silhouette in it, its slit eyes the only lights. */
private object NegCat : NewHero(
    "negcat", listOf("Inkling", "Negative Cat", "Void Cat"), Rarity.RARE,
    "Кот-негатив: светится вся матрица, а кот — тёмный силуэт со светящимися глазами",
    mapOf("calm" to "хвост качается", "happy" to "хвост трубой", "habit" to "умывается", "signature" to "ночь, одни глаза",
        "hungry" to "смотрит на рыбку", "sad" to "свет тусклый", "tired" to "глаза щёлочки", "asleep" to "темно, глаза закрыты",
        "eating" to "ест светлую рыбку", "game" to "ловит тени мотыльков", "loved" to "мурчит волнами", "dizzy" to "свет кругом",
        "tilt" to "съезжает, свет течёт", "charging" to "небо светлеет", "grow" to "проявляется"),
) {
    private val CAT = listOf("..#.....#..", "..##...##..", "..#######..", ".#########.", ".#########.", ".#########.", "..#######..", "...#####...", "..#######..", ".#########.", ".#########.", ".#########.", "###########")
    private val SMALL = listOf("..#...#..", "..##.##..", "..#####..", ".#######.", ".#######.", "..#####..", "..#####..", ".#######.", ".#######.", "#########")

    /** Stretching, seen side on and facing left: front paws far forward, rump up, tail straight up; 'o' its eye. */
    private val STRETCH = listOf(
        "...............#.",
        "..............#..",
        "..............#..",
        "..........#####..",
        ".........#######.",
        "...#..#.########.",
        "...#############.",
        "..#o############.",
        "..######....#..#.",
        ".#####......#..#.",
        "######......#..#.",
    )
    /** The same with the front paws pushed out one more step (drawn a column further left). */
    private val STRETCH2 = STRETCH.mapIndexed { j, r -> (if (j == 10) "#" else ".") + r }

    private fun scene(c: Cv, st: Int, t: Long, sky: Float = 0.38f, ox: Int = 0, eyes: Int = 0, tailUp: Boolean = false, wave: Float = -1f,
                      oy: Int = 0, sw: Float = sin(t / 600f), look: Int = 0) {
        // [oy] lifts (-) or crouches (+) it, [sw] swings the tail, [look] turns its eyes sideways.
        for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) { var v = sky; if (wave >= 0f && abs(hypot(x - 12f, y - 12f) - wave) < 1.2f) v = 0.9f; c.set(x, y, v) }
        val rows = if (st == 0) SMALL else CAT
        val left = 12 - rows[0].length / 2 + ox; val top = 22 - rows.size + oy
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.cut(left + i, top + j) } }
        for (k in 0 until 7) { val x = left + rows[0].length + (sin(k * 0.5f) * 2 + sw * k * 0.25f).roundToInt(); val y = 21 - k - (if (tailUp) 2 else 0) + oy; c.cut(x, y); c.cut(x + 1, y) }
        val ey = top + (if (st == 0) 4 else 4); val ex = rows[0].length / 2 - (if (st == 0) 2 else 2)
        for (d in intArrayOf(-ex, ex)) { val x = 12 + ox + d + look
            when (eyes) { 0 -> { c.set(x, ey, 1f); c.set(x, ey + 1, 1f) }; 1 -> c.set(x, ey + 1, 0.6f); 2 -> { c.set(x, ey + 1, 1f) }; else -> {} } }
    }

    /** The glowing sky with the cat stretching in it ([STRETCH]), its eye shut with [shut]. */
    private fun stretch(c: Cv, rows: List<String>, shut: Boolean) {
        for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) c.set(x, y, 0.38f)
        val left = 5 + STRETCH[0].length - rows[0].length; val top = 22 - rows.size
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.cut(left + i, top + j); 'o' -> c.set(left + i, top + j, if (shut) 0.6f else 1f) } } }
    }

    override val pastimes = listOf(
        DrawnPastime("tail_chase", 4800, "ловит хвост",
            "Хвост поднимается и дёргается сам по себе, кот щурится на него, припадает и, зажмурившись, подпрыгивает за ним, а хвост ускользает за спину. Кот приземляется, хвост опять качается как ни в чём не бывало."),
        DrawnPastime("stretch", 4800, "потягивается",
            "Жмурится, встаёт и сладко потягивается: передние лапы далеко вперёд, спина дугой, хвост трубой; потом снова садится и открывает глаза."),
    )

    private var tKey = -1
    private var tWave = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val calmSw = sin(calmRun(t, ms, 600f * TAU_F) / 600f)
                when (col) {
                    "tail_chase" -> {
                        // The tail lifts and twitches on its own; it squints at it, crouches and leaps straight up
                        // after it, eyes screwed shut, while the tail whips away behind it; it lands and looks again.
                        val sw = when {
                            t < 1000 -> calmSw
                            t < 2150 -> mix(calmSw, 1.7f * sin((t - 1000) / 110f), seg(t, 1000, 1200))
                            t < 2600 -> -3f
                            else -> mix(-3f, calmSw, ease(seg(t, 2600, 3900)))
                        }
                        val oy = when {
                            t in 1850L until 2150L -> 1
                            t in 2150L until 2250L -> -1
                            t in 2250L until 2350L -> -2
                            t in 2350L until 2450L -> -3
                            t in 2450L until 2550L -> -2
                            t in 2550L until 2650L -> -1
                            else -> 0
                        }
                        val eyes = when { t in 1200L until 2150L -> 2; t in 2150L until 2650L -> 3; t in 3000L until 3160L -> 1; else -> 0 }
                        scene(c, st, t, oy = oy, sw = sw, tailUp = t in 1000L until 2150L, eyes = eyes)
                    }
                    "stretch" -> {
                        // Eyes shut, up into a long stretch (the paws reach once more), and down to sit again.
                        if (t in 1100L until 3100L) stretch(c, if (t in 1600L until 2600L) STRETCH2 else STRETCH, shut = true)
                        else scene(c, st, t, sw = calmSw, eyes = if (t in 850L until 3500L) 1 else 0)
                    }
                }
            }) return
        when (col) {
            "calm" -> scene(c, st, t, eyes = if (blink(t)) 1 else 0)
            "happy" -> scene(c, st, t, 0.5f, tailUp = true)
            "habit" -> scene(c, st, t, eyes = 1, ox = if (t % 800 < 400) 0 else 1)
            "signature" -> { val k = seg(t, 600, 1800) - seg(t, 4200, 5400); scene(c, st, t, 0.38f * (1 - k)) }
            "hungry" -> { scene(c, st, t); thought(c, listOf(".#.#.", "#####", ".#.#."), t) }
            "sad" -> scene(c, st, t, 0.2f, eyes = 1)
            "tired" -> scene(c, st, t, 0.3f, eyes = 2)
            "asleep" -> scene(c, st, t, 0.06f, eyes = 1)
            "eating" -> { scene(c, st, t); if (t < 1800) { val p = seg(t, 0, 1800); c.cut(mix(22f, 13f, p).roundToInt(), 10); c.cut(mix(23f, 14f, p).roundToInt(), 10) } }
            "game" -> scene(c, st, t, ox = (tilt * 5f).roundToInt())
            "loved" -> scene(c, st, t, eyes = 1, wave = (t % 1500) / 1500f * 14f)
            "dizzy" -> scene(c, st, t, 0.38f + 0.2f * sin(t / 120f), eyes = 1)
            "tilt" -> scene(c, st, t, ox = (tilt * 5f).roundToInt(), wave = 12f + tilt * 8f)
            "charging" -> scene(c, st, t, 0.38f * seg(t, 0, 3000))
            "grow" -> scene(c, st, t, 0.38f * seg(t, 0, 3000), eyes = if (t < 3400) 1 else 0)
            // Typing: every key a wave of light rolls over the sky; at 20 s the sky pulses to the keys, at 40 s it strobes
            // like a party and the cat bobs its head.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tWave = y.now; tKey = y.key }
                val sky = if (y.stage == 2) (if ((t / 120) % 2 == 0L) 0.55f else 0.25f) else 0.38f + (if (y.stage >= 1 && y.sinceKey < 150) 0.15f else 0f)
                scene(c, st, t, sky, ox = if (y.stage == 2) (if (y.key % 2 == 0) -1 else 1) else 0, eyes = if (y.paused && blink(t)) 1 else 0, wave = (y.now - tWave) / 30f)
            }
        }
    }
}

// --- the squid ------------------------------------------------------------------------------------------------------------------

/** A squid in the lit sea; it squirts ink that blacks everything out, and in the ink only its outline glows. */
private object Squid : NewHero(
    "squid", listOf("Squiddy", "Squid", "Inkfish"), Rarity.COMMON,
    "Кальмар: пускает чернила, всё чернеет, и он светится контуром",
    mapOf("calm" to "покачивает щупальцами", "happy" to "кувыркается", "habit" to "пускает колечко чернил", "signature" to "чернила и свечение",
        "hungry" to "думает о креветке", "sad" to "мутная вода", "tired" to "щупальца медленно", "asleep" to "светится во тьме еле-еле",
        "eating" to "ловит креветку", "game" to "собирает планктон", "loved" to "щупальца сердцем", "dizzy" to "вертится",
        "tilt" to "сносит течением", "charging" to "море светлеет", "grow" to "икринка"),
) {
    private val ARM = Tubes.Look(lit = 1f, core = 0.8f, shadow = 0.6f, thin = 0.85f, thinBelow = 0.8f, seam = false)

    private fun squid(c: Cv, st: Int, t: Long, sx: Float = 12f, sy: Float = 8f, glow: Boolean = false, eyes: Int = 0, wig: Float = 1f, sea: Float = 0.32f, ink: Float = 0f,
                      spread: Float = 1f, wave: Float = 0.8f, lift: Float = 0f, liftSw: Float = 0f) {
        // [spread] fans the tentacles out (>1) or bunches them (<1), [wave] how much they wave, [lift] 0..1 raises the
        // last one up its side to wave hello, its tip swung by [liftSw].
        for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) c.set(x, y, if (hypot(x - sx, y - sy - 6f) < ink) 0f else sea)
        val w = 3.5f + st * 0.5f
        fun body(x: Int, y: Int): Boolean { val dy = y - sy; if (dy < -5 || dy > 3) return false; val ww = if (dy < 0) (dy + 6) * w / 5f else w; return abs(x - sx) <= ww }
        for (y in (sy - 6).toInt()..(sy + 3).toInt()) for (x in 0..24) if (body(x, y)) { val edge = !body(x - 1, y) || !body(x + 1, y) || !body(x, y - 1) || !body(x, y + 1); c.set(x, y, if (glow) (if (edge) 1f else 0f) else 0.9f) }
        val n = 4 + st
        // Lit, the tentacles are tubes thick at the body and thin at the tip, a wave running down them; in the ink they
        // glow as dotted outlines.
        val gap = 2 * w * spread / (n - 1)
        val arms = ArrayList<Tube>()
        val r0 = min(1.05f, gap * 0.5f)
        for (i in 0 until n) { val x0 = sx - w * spread + i * (2 * w * spread / (n - 1))
            if (i == n - 1 && lift > 0f) {
                // Up its side: out from the body, up past its eye, the tip waving over its head.
                val up = listOf(1f to -1f, 2f to -2f, 2.5f to -3.5f, 2.5f to -5f, 2.2f to -6.5f, 1.8f to -7.8f)
                var px = x0; var py = sy + 3
                val pts = arrayListOf(px to py)
                for (k in 1..6) {
                    val hx = x0 + sin(t / 250f * wig + i + k * 0.7f) * wave; val hy = sy + 3 + k
                    val (ux, uy) = up[k - 1]; val rx = sx + w + ux + liftSw * (k / 6f) * (k / 6f); val ry = sy + 3 + uy
                    val x = mix(hx, rx, lift); val y = mix(hy, ry, lift)
                    if (glow) { var s = 0f; while (s <= 1f) { c.put(mix(px, x, s), mix(py, y, s), 0.85f); s += 0.25f } }
                    px = x; py = y; pts += x to y
                }
                if (!glow) arms += Tube(Tubes.path(36) { q -> val f = q * 6f; val j = min(5, f.toInt()); val u = f - j; mix(pts[j].first, pts[j + 1].first, u) to mix(pts[j].second, pts[j + 1].second, u) }, r0, 0.75f)
                continue
            }
            if (glow) for (k in 1..6) { val x = x0 + sin(t / 250f * wig + i + k * 0.7f) * wave; c.set(x.roundToInt(), (sy + 3 + k).roundToInt(), if (k % 2 == 1) 0.9f else 0f) }
            else arms += Tube(Tubes.path(30) { q -> val k = q * 6f; (x0 + sin(t / 250f * wig + i + k * 0.7f) * wave * min(1f, k)) to (sy + 3 + k) }, r0, 0.75f) }
        if (arms.isNotEmpty()) Tubes.paint(c, arms, ARM, clip = { x, y -> !body(x, y) })
        for (d in intArrayOf(-2, 2)) { val x = (sx + d).roundToInt(); val y = (sy + 1).roundToInt()
            if (glow) { c.set(x, y, 1f) } else if (eyes == 1) c.cut(x, y) else { c.cut(x, y); c.cut(x, y - 1) } }
    }

    private var tKey = -1
    private var tInk = -99_999L

    override val pastimes = listOf(
        DrawnPastime("jet", 4400, "делает рывок",
            "Опускается и раскидывает щупальца веером, резко собирает их в пучок и реактивным рывком взлетает вверх, потом плавно опускается на место."),
        DrawnPastime("wave_hi", 4800, "машет щупальцем",
            "Поднимает одно щупальце вдоль бока над головой и машет им, как рукой: привет! Потом опускает его обратно к остальным."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val tv = calmRun(t, ms, 600f * TAU_F)
                val calmSy = 8f + sin(tv / 600f) * 0.8f
                when (col) {
                    "jet" -> {
                        // Down with its tentacles fanned, snapped together, a jet up, and a slow glide back to its place.
                        val sy = when {
                            t < 1300 -> mix(calmSy, 12f, ease(seg(t, 480, 1300)))
                            t < 1500 -> 12f
                            t < 1750 -> mix(12f, 5.5f, 1f - (1f - seg(t, 1500, 1750)).let { it * it })
                            t < 2600 -> mix(5.5f, 7f, ease(seg(t, 1750, 2600)))
                            else -> mix(7f, calmSy, ease(seg(t, 2600, ms - Drawn.PASTIME_EDGE)))
                        }
                        val spread = when {
                            t < 1300 -> mix(1f, 1.45f, ease(seg(t, 480, 1300)))
                            t < 1750 -> mix(1.45f, 0.75f, seg(t, 1300, 1450))
                            else -> mix(0.75f, 1f, ease(seg(t, 1900, 3000)))
                        }
                        val wave = if (t in 1300L until 1900L) 0.15f else if (t < 1300) 0.8f else mix(0.15f, 0.8f, seg(t, 1900, 3000))
                        squid(c, st, tv, sy = sy, spread = spread, wave = wave, eyes = if (t in 1450L until 1800L) 1 else 0)
                    }
                    "wave_hi" -> {
                        val lift = ease(seg(t, 480, 1100)) - ease(seg(t, 3300, 3900))
                        val sw = if (t in 1100L until 3300L) sin((t - 1100) / 170f) * 1.6f else 0f
                        squid(c, st, tv, sy = calmSy, lift = lift, liftSw = sw, eyes = if (t in 2200L until 2360L) 1 else 0)
                    }
                }
            }) return
        val bob = sin(t / 600f) * 0.8f
        when (col) {
            "calm" -> squid(c, st, t, sy = 8f + bob, eyes = if (blink(t)) 1 else 0)
            "happy" -> squid(c, st, t, sy = 8f - hop(t, 400f, 2f), wig = 2f)
            "habit" -> { squid(c, st, t, sy = 8f + bob); val q = (t % 1600) / 1600f; c.ring(12f, 18f + q * 4, 1f + q * 2, 0f); for (a in 0 until 12) { val an = a * 0.52f; c.set((12 + cos(an) * (1 + q * 2)).roundToInt(), (18 + q * 4 + sin(an) * (1 + q * 2)).roundToInt(), 0f) } }
            "signature" -> { val ink = when { t < 1500 -> 0f; t < 2200 -> ease((t - 1500) / 700f) * 14f; t < 4000 -> 14f; else -> max(0f, 14f - (t - 4000) / 1000f * 7f) }; squid(c, st, t, sy = 8f + bob, glow = ink > 6f, ink = ink) }
            "hungry" -> { squid(c, st, t, sy = 8f + bob); thought(c, listOf("o.o", ".o.", "o.o"), t) }
            "sad" -> squid(c, st, t, sy = 10f, sea = 0.18f, eyes = 1, wig = 0.4f)
            "tired" -> squid(c, st, t, sy = 9f + bob, wig = 0.4f, eyes = if ((t / 700) % 2 == 0L) 1 else 0)
            "asleep" -> squid(c, st, t, sy = 10f, glow = true, sea = 0f, wig = 0.3f)
            "eating" -> { squid(c, st, t, sy = 8f + bob); if (t < 1800) { val p = seg(t, 0, 1800); c.cut(mix(22f, 13f, p).roundToInt(), 17); c.cut(mix(23f, 14f, p).roundToInt(), 17) } }
            "game" -> squid(c, st, t, sx = 12f + tilt * 6f, sy = 8f + bob)
            "loved" -> { squid(c, st, t, sy = 8f + bob, eyes = 1); hearts(c, 12f, 4f, t) }
            "dizzy" -> squid(c, st, t, sx = 12f + cos(t / 200f) * 3f, sy = 8f + sin(t / 200f) * 2f, eyes = 1, wig = 3f)
            "tilt" -> squid(c, st, t, sx = 12f + tilt * 6f, sy = 8f + bob)
            "charging" -> squid(c, st, t, sy = 8f, sea = 0.32f * seg(t, 0, 3000))
            "grow" -> { if (t < 2600) { for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) c.set(x, y, 0.32f); c.disc(12f, 12f, 1f + seg(t, 0, 2600), 0.9f); c.cut(12, 12) } else squid(c, st, t, sy = 8f) }
            // Typing: every key a puff of ink; at 20 s the sea darkens and it glows in it, at 40 s its glowing outline
            // throbs to the keys in the black.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tInk = y.now; tKey = y.key }
                val a = y.now - tInk
                val sea = when (y.stage) { 0 -> 0.32f; 1 -> 0.16f; else -> 0f }
                squid(c, st, t, sy = 8f + bob, glow = y.stage >= 1, sea = sea, eyes = if (y.paused && blink(t)) 1 else 0, wig = if (y.rush) 2f else 1f)
                if (a < 600 && y.stage == 0) { val q = a / 600f; val px = 12f + (rnd(y.key, 111) - 0.5f) * 6; c.hole(px, 20f + q * 2, 1f + q * 2.5f) }
                if (y.stage == 2 && y.sinceKey < 150) c.ring(12f, 12f, 11f, 0.6f)
            }
        }
    }
}

// --- the rolling ball ------------------------------------------------------------------------------------------------------------

/** A ball lit in 3D that rolls with the lean: its face rolls round to the back and comes over the top. */
private object Kolobok : NewHero(
    "kolobok", listOf("Bun", "Kolobok", "Roly"), Rarity.COMMON,
    "Колобок: объёмный шар катится за наклоном, лицо уезжает назад и возвращается",
    mapOf("calm" to "покачивается", "happy" to "прыгает мячиком", "habit" to "катается туда-сюда", "signature" to "кувырок",
        "hungry" to "думает о крошках", "sad" to "сплющился", "tired" to "катится медленно", "asleep" to "лицом вниз",
        "eating" to "ловит крошку", "game" to "катается за крошками", "loved" to "румяный", "dizzy" to "крутится",
        "tilt" to "катится", "charging" to "наливается светом", "grow" to "тесто подходит"),
) {
    private val R = floatArrayOf(4.5f, 5.5f, 6.5f)

    /** The ball at [cx] on the floor, rolled by [phi], squashed by [sq]; [eyes]/[mouth] as in [face]; [up] turns the face up. */
    private fun ball(c: Cv, st: Int, cx: Float, phi: Float, sq: Float = 0f, dy: Float = 0f, eyes: Int = 0, mouth: Int = 0, light: Float = 1f, up: Float = 0f) {
        val r = R[st]; val rx = r * (1 + sq); val ry = r * (1 - sq); val cy = 20f - ry + dy
        for (y in 0..24) for (x in 0..24) {
            val dx = (x - cx) / rx; val dyy = (y - cy) / ry; val q = dx * dx + dyy * dyy; if (q > 1f) continue
            val z = sqrt(1 - q)
            val lon = kotlin.math.atan2(dx, z) - phi; val lat = kotlin.math.asin(dyy.coerceIn(-1f, 1f)) + up
            var v = q5((0.22f + 0.78f * max(0f, -0.5f * dx - 0.4f * dyy + 0.75f * z)) * light)
            val L = ((lon + PIF) % TAU_F + TAU_F) % TAU_F - PIF
            // Sized by the ball's radius, so the face is two dark 2x2 eyes and a flat smile at every stage (wide
            // fixed angles came out as tall slanted slits and a V of a mouth: an angry face on the LEDs).
            val ew = 0.95f / r; val eL = kotlin.math.asin((2.25f / r).coerceAtMost(0.95f)); val eLat = 1.3f / r
            val eyeHit = abs(abs(L) - eL) < ew && abs(lat + eLat) < (if (eyes == 1) ew * 0.45f else ew)
            val mw = 1.95f / r; val mLat = 2.75f / r; val mTh = 0.52f / r
            val mouthHit = when (mouth) { 0 -> abs(L) < mw && abs(lat - mLat + 0.12f * (L / mw) * (L / mw)) < mTh; 1 -> abs(L) < 0.32f && abs(lat - 0.42f + 0.15f * (1 - (L / 0.32f) * (L / 0.32f))) < 0.09f; 2 -> abs(L) < 0.12f && abs(lat - 0.4f) < 0.15f; else -> false }
            if (eyeHit || mouthHit) v = 0f
            c.set(x, y, v)
        }
        for (x in 0..24) c.put(x, 21, 0.3f)
    }

    private var tKey = -1
    private var tHop = -99_999L

    override val pastimes = listOf(
        DrawnPastime("run_away", 5600, "убегает",
            "Чуть отступает и укатывается за правый край, как в сказке: «я от дедушки ушёл». Миг пусто, и он выкатывается слева, проскакивает своё место и, качнувшись, встаёт."),
        DrawnPastime("look_around", 4800, "оглядывается",
            "Стоит на месте и поворачивает лицо: смотрит влево, вправо, потом вверх, моргает и снова смотрит прямо."),
    )

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastime(c, col, t, st) { ms ->
                val r = R[st]
                val e = Drawn.PASTIME_EDGE
                val calmX = 12f + sin(calmRun(t, ms, 1300f * TAU_F) / 1300f)
                when (col) {
                    "run_away" -> {
                        // A step back, off to the right, a moment gone, in from the left past its place and back.
                        val x0 = 12f + sin(calmAt(e) / 1300f)
                        val xT = 12f + sin(calmRun(4300, ms, 1300f * TAU_F) / 1300f)
                        val x = when {
                            t < 800 -> mix(x0, x0 - 1.5f, ease(seg(t, e, 800)))
                            t < 1900 -> mix(x0 - 1.5f, 32f, seg(t, 800, 1900).let { it * it })
                            t < 2700 -> 32f
                            t < 3800 -> { val p = seg(t, 2700, 3800); mix(-8f, xT + 2f, 1f - (1f - p) * (1f - p)) }
                            t < 4300 -> mix(xT + 2f, xT, ease(seg(t, 3800, 4300)))
                            else -> calmX
                        }
                        val sq = if (t in 3800L until 4300L) 0.08f * sin(seg(t, 3800, 4300) * 2 * PIF) else 0f
                        if (x in -9f..33f && t !in 1900L until 2700L) ball(c, st, x, -(x - 12f) / r, sq = sq, eyes = if (t in 3700L until 4300L) 2 else 0)
                        else for (xx in 0..24) c.put(xx, 21, 0.3f)
                    }
                    "look_around" -> {
                        // It stays put and turns its face: left, right, up, and back to the front with a blink.
                        val x = mix(mix(calmX, 12f, ease(seg(t, e, 900))), calmX, ease(seg(t, 3900, ms - e)))
                        val turn = when {
                            t < 1800 -> -0.75f * ease(seg(t, 900, 1300))
                            t < 2800 -> mix(-0.75f, 0.75f, ease(seg(t, 1800, 2300)))
                            else -> 0.75f * (1f - ease(seg(t, 2800, 3200)))
                        }
                        val up = 0.4f * hold(t, 2800, 3200, 3500, 3800)
                        ball(c, st, x, -(x - 12f) / r + turn, up = up, eyes = if (t in 3850L until 4010L) 1 else 0)
                    }
                }
            }) return
        val l = sin(t / 1300f)
        when (col) {
            "calm" -> ball(c, st, 12f + l, -l / R[st], eyes = if (blink(t)) 1 else 0)
            "happy" -> { val h = hop(t, 400f, 4f); ball(c, st, 12f, 0f, sq = if (h < 0.5f) 0.15f else 0f, dy = -h, eyes = 2) }
            "habit" -> { val x = 12f + sin(t / 800f) * 6f; ball(c, st, x, -(x - 12f) / R[st]) }
            "signature" -> { val p = seg(t, 600, 2600); ball(c, st, 12f, p * TAU_F, dy = -sin(p * PIF) * 6f) }
            "hungry" -> { ball(c, st, 12f, 0f, mouth = 2); thought(c, listOf("o.o", ".o.", "o.o"), t) }
            "sad" -> ball(c, st, 12f, 0f, sq = 0.15f + 0.08f * sin(t / 500f), mouth = 1, light = 0.7f)
            "tired" -> { val x = 12f + sin(t / 2600f) * 4f; ball(c, st, x, -(x - 12f) / R[st], eyes = 1) }
            "asleep" -> { ball(c, st, 12f, PIF * 0.8f, sq = 0.05f * sin(t / 650f), light = 0.5f); zs(c, 16f, 8f, t) }
            "eating" -> { ball(c, st, 12f, 0f, mouth = if (t in 1200L..2200L) 2 else 0); if (t < 1500) c.set(mix(22f, 15f, seg(t, 0, 1500)).roundToInt(), 19, 1f) }
            "game" -> ball(c, st, 12f + tilt * 6f, -tilt * 6f / R[st])
            "loved" -> { ball(c, st, 12f, 0f, eyes = 1); hearts(c, 12f, 6f, t) }
            "dizzy" -> ball(c, st, 12f, t / 200f, eyes = 1, mouth = 2)
            "tilt" -> ball(c, st, 12f + tilt * 6f, -tilt * 6f / R[st])
            "charging" -> ball(c, st, 12f, 0f, light = seg(t, 0, 3000))
            "grow" -> ball(c, 0.coerceAtLeast(if (t > 3000) st else 0), 12f, 0f, sq = 0.3f * (1 - seg(t, 0, 3000)), eyes = if (t < 3400) 1 else 0)
            // Typing: every key it hops; at 20 s it rolls over hills that scroll past, at 40 s it bounces high and spins.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tHop = y.now; tKey = y.key }
                val a = (y.now - tHop) / 300f
                val h = if (a < 1f) sin(a * PIF) * (if (y.stage == 2) 7f else 3f) else 0f
                if (y.stage >= 1) for (x in 0..24) { val hh = (19 + sin((x + t / 150f) * 0.4f) * 1.5f).roundToInt(); c.put(x, hh + 2, 0.25f) }
                ball(c, st, 12f, if (y.stage >= 1) t / 300f else 0f, sq = if (a in 0f..0.15f) 0.15f else 0f, dy = -h, eyes = if (y.paused && blink(t)) 1 else if (h > 2f) 2 else 0)
            }
        }
    }
}
