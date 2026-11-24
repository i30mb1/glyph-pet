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
private fun face(c: Cv, cx: Int, ey: Int, gap: Int, eyes: Int, mouth: Int, under: Float = 0.6f) {
    for (d in intArrayOf(-gap, gap)) when (eyes) {
        0 -> { c.cut(cx + d, ey); c.cut(cx + d, ey - 1) }
        1 -> { c.cut(cx + d - 1, ey); c.cut(cx + d, ey); c.cut(cx + d + 1, ey) }
        else -> { c.cut(cx + d, ey); c.cut(cx + d, ey - 1); c.cut(cx + d + 1, ey); c.cut(cx + d + 1, ey - 1) }
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

    /** The cat's head cut out of the sun, its eyes lit: [eyes] 0 open slits, 1 shut, 2 happy arcs. */
    private fun cat(c: Cv, st: Int, dx: Float = 0f, eyes: Int = 0, ears: Int = 0, eyeB: Float = 1f) {
        val k = K[st]; val cx = 12f + dx
        for (y in 0..24) for (x in 0..24) if (inCatHead(x.toFloat(), y + (if (ears != 0 && y < 9) 0.6f * ears else 0f), cx, 13f, k)) c.cut(x, y)
        val ex = (2f * k).roundToInt().coerceAtLeast(2)
        for (d in intArrayOf(-ex, ex)) { val x = (cx + d).roundToInt()
            when (eyes) { 0 -> { c.set(x, 13, eyeB); c.set(x, 14, eyeB) }; 1 -> c.set(x, 14, 0.5f * eyeB); else -> { c.set(x - 1, 14, eyeB); c.set(x, 13, eyeB); c.set(x + 1, 14, eyeB) } } }
    }

    private var tKey = -1
    private var tRay = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
    private fun scene(c: Cv, st: Int, lx: Float, light: Float = 1f, bunny: List<String> = BUN, shadow: List<String> = bunny, by: Int = 13, shut: Boolean = false) {
        val ly = 3f; val left = 10; val big = 2.2f - st * 0.15f
        for (y in 0..24) for (x in 0..24) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val d = hypot(x - lx, y - ly)
            var v = max(0.1f, 0.55f - d * 0.025f) * light
            val sx = 12 - (lx - 12) * 0.9f; val qx = (x - sx) / big + 12f; val qy = (y - 11f) / big + by + 2.5f
            if (inShape(shadow, qx, qy, left, by)) v = 0f
            c.set(x, y, q5(v))
        }
        bunny.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(left + i, by + j, 0.95f * max(0.4f, light)); '-' -> if (shut) c.set(left + i, by + j, 0.6f) else c.cut(left + i, by + j) } } }
        if (light > 0.05f) { c.put(lx, ly, light); c.put(lx - 1, ly, 0.8f * light); c.put(lx + 1, ly, 0.8f * light); for (y in 0 until ly.toInt()) c.put(lx, y.toFloat(), 0.4f) }
    }

    private var tKey = -1
    private var tKeyAt = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
        for (x in 0..24) for (y in 22..24) c.set(x, y, 0.6f)
    }

    private var tKey = -1

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
    private fun scan(c: Cv, st: Int, bar: Float, w: Float = 2f, all: Boolean = false, eyes: Int = 0, inside: List<String>? = null, b: Float = 1f) {
        val k = K[st]
        for (y in 0..24) for (x in 0..24) {
            if (!inCat(x.toFloat(), y.toFloat(), 12f, 9f, k)) continue
            val under = all || abs(y - bar) <= w
            c.set(x, y, (if (under) (if (bone(x, y, k)) 0.1f else 0.85f) else 0.45f) * b)
        }
        if (inside != null) inside.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> val yy = 16 + j; if (ch == '#' && (all || abs(yy - bar) <= w + 2)) c.set(10 + i, yy, 0.1f) } }
        if (!all && abs(9 - bar) > w) { if (eyes == 1) { c.cut(9, 9); c.cut(10, 9); c.cut(14, 9); c.cut(15, 9) } else { c.cut(10, 9); c.cut(14, 9); c.cut(10, 8); c.cut(14, 8) } }
        if (!all) for (x in 5..19) { c.put(x.toFloat(), bar - w - 1, 0.25f * b); c.put(x.toFloat(), bar + w + 1, 0.25f * b) }
    }

    private val FISH = listOf(".#.#.", "#####", ".#.#.")

    private var tKey = -1

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
    private fun scene(c: Cv, st: Int, t: Long, x: Float = 12f, y: Float = 9f, amp: Float = 0.12f, eyes: Int = 0, mouth: Int = 0, moon: Boolean = false, under: Float = 0f) {
        val g = Cv(); val r = 3.2f + st * 0.65f
        if (under < 1f) {
            g.disc(x, y + under * 6, r, 0.85f); g.ring(x, y + under * 6, r, 1f, 0.45f)
            for (d in intArrayOf(-1, 1)) { g.set((x + d * 3).roundToInt(), (y - r).roundToInt(), 0.9f); g.set((x + d * 3).roundToInt(), (y - r - 1).roundToInt(), 0.9f) }
            face(g, x.roundToInt(), y.roundToInt(), 2, eyes, mouth)
        }
        if (moon) { g.disc(20f, 3f, 1.6f, 0.6f) }
        for (yy in 0 until W) for (xx in 0..24) { val v = g.get(xx, yy); if (v > 0f) c.set(xx, yy, v) }
        for (xx in 0..24) c.set(xx, W, 0.5f)
        for (yy in W + 1..24) { val sy = 2 * W - yy; val off = (sin(yy * 1.3f - t / 250f) * (yy - W) * amp).roundToInt()
            for (xx in 0..24) { val sx = xx - off; if (sx in 0..24 && sy >= 0) { val v = g.get(sx, sy); if (v > 0f) c.set(xx, yy, q5(v * 0.45f)) } } }
    }

    private var tKey = -1
    private var tDrop = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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

    private fun sun(c: Cv, st: Int, t: Long, rays: Float = 1f, cy: Float = 12f, eyes: Int = 0, mouth: Int = 0, spin: Float = 1f, n: Int = 12, b: Float = 1f) {
        val r = R[st]
        if (rays > 0f) for (i in 0 until n) { val a = i * TAU_F / n + t / 4000f * spin; val len = ((1.5f + 1.5f * rnd(i, (t / 200).toInt())) * rays).roundToInt()
            for (j in 0..len) c.put(12 + cos(a) * (r + 2 + j), cy + sin(a) * (r + 2 + j), (if (j == len) 1f else 0.55f) * b) }
        for (y in 0..24) for (x in 0..24) { val d = hypot(x - 12f, y - cy); if (d <= r + 0.3f) c.set(x, y, (if (d > r - 0.8f) 0.6f else 0.95f) * b) }
        face(c, 12, cy.roundToInt() - 1, 2, eyes, mouth)
    }

    private var tKey = -1

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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

    private fun scene(c: Cv, st: Int, t: Long, sky: Float = 0.38f, ox: Int = 0, eyes: Int = 0, tailUp: Boolean = false, wave: Float = -1f) {
        for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) { var v = sky; if (wave >= 0f && abs(hypot(x - 12f, y - 12f) - wave) < 1.2f) v = 0.9f; c.set(x, y, v) }
        val rows = if (st == 0) SMALL else CAT
        val left = 12 - rows[0].length / 2 + ox; val top = 22 - rows.size
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.cut(left + i, top + j) } }
        val sw = sin(t / 600f)
        for (k in 0 until 7) { val x = left + rows[0].length + (sin(k * 0.5f) * 2 + sw * k * 0.25f).roundToInt(); val y = 21 - k - (if (tailUp) 2 else 0); c.cut(x, y); c.cut(x + 1, y) }
        val ey = top + (if (st == 0) 4 else 4); val ex = rows[0].length / 2 - (if (st == 0) 2 else 2)
        for (d in intArrayOf(-ex, ex)) { val x = 12 + ox + d
            when (eyes) { 0 -> { c.set(x, ey, 1f); c.set(x, ey + 1, 1f) }; 1 -> c.set(x, ey + 1, 0.6f); 2 -> { c.set(x, ey + 1, 1f) }; else -> {} } }
    }

    private var tKey = -1
    private var tWave = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
    private fun squid(c: Cv, st: Int, t: Long, sx: Float = 12f, sy: Float = 8f, glow: Boolean = false, eyes: Int = 0, wig: Float = 1f, sea: Float = 0.32f, ink: Float = 0f) {
        for (y in 0..24) for (x in 0..24) if (GlyphMatrixSpec.isVisible(x, y)) c.set(x, y, if (hypot(x - sx, y - sy - 6f) < ink) 0f else sea)
        val w = 3.5f + st * 0.5f
        fun body(x: Int, y: Int): Boolean { val dy = y - sy; if (dy < -5 || dy > 3) return false; val ww = if (dy < 0) (dy + 6) * w / 5f else w; return abs(x - sx) <= ww }
        for (y in (sy - 6).toInt()..(sy + 3).toInt()) for (x in 0..24) if (body(x, y)) { val edge = !body(x - 1, y) || !body(x + 1, y) || !body(x, y - 1) || !body(x, y + 1); c.set(x, y, if (glow) (if (edge) 1f else 0f) else 0.9f) }
        val n = 4 + st
        for (i in 0 until n) { val x0 = sx - w + i * (2 * w / (n - 1)); for (k in 1..6) { val x = x0 + sin(t / 250f * wig + i + k * 0.7f) * 0.8f; c.set(x.roundToInt(), (sy + 3 + k).roundToInt(), if (glow) (if (k % 2 == 1) 0.9f else 0f) else 0.85f) } }
        for (d in intArrayOf(-2, 2)) { val x = (sx + d).roundToInt(); val y = (sy + 1).roundToInt()
            if (glow) { c.set(x, y, 1f) } else if (eyes == 1) c.cut(x, y) else { c.cut(x, y); c.cut(x, y - 1) } }
    }

    private var tKey = -1
    private var tInk = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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

    /** The ball at [cx] on the floor, rolled by [phi], squashed by [sq]; [eyes]/[mouth] as in [face]. */
    private fun ball(c: Cv, st: Int, cx: Float, phi: Float, sq: Float = 0f, dy: Float = 0f, eyes: Int = 0, mouth: Int = 0, light: Float = 1f) {
        val r = R[st]; val rx = r * (1 + sq); val ry = r * (1 - sq); val cy = 20f - ry + dy
        for (y in 0..24) for (x in 0..24) {
            val dx = (x - cx) / rx; val dyy = (y - cy) / ry; val q = dx * dx + dyy * dyy; if (q > 1f) continue
            val z = sqrt(1 - q)
            val lon = kotlin.math.atan2(dx, z) - phi; val lat = kotlin.math.asin(dyy.coerceIn(-1f, 1f))
            var v = q5((0.22f + 0.78f * max(0f, -0.5f * dx - 0.4f * dyy + 0.75f * z)) * light)
            val L = ((lon + PIF) % TAU_F + TAU_F) % TAU_F - PIF
            val eyeHit = abs(abs(L) - 0.38f) < 0.17f && abs(lat + 0.2f) < (if (eyes == 1) 0.07f else 0.2f)
            val mouthHit = when (mouth) { 0 -> abs(L) < 0.32f && abs(lat - 0.35f - 0.25f * (L / 0.32f) * (L / 0.32f) * -1f) < 0.09f; 1 -> abs(L) < 0.32f && abs(lat - 0.42f + 0.15f * (1 - (L / 0.32f) * (L / 0.32f))) < 0.09f; 2 -> abs(L) < 0.12f && abs(lat - 0.4f) < 0.15f; else -> false }
            if (eyeHit || mouthHit) v = 0f
            c.set(x, y, v)
        }
        for (x in 0..24) c.put(x, 21, 0.3f)
    }

    private var tKey = -1
    private var tHop = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
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
