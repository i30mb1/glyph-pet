package n7.glyphpet.pet.drawn

import n7.glyphpet.pet.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Heroes made of light in the dark, from the Supporter mockups: each lights its own world rather than being drawn on
 * it, so what the matrix shows is what its glow reaches.
 */
object GlowHeroes {
    val all: List<Anomaly> = listOf(Firefly)
}

private val PIF = PI.toFloat()

// --- the firefly in a jar ------------------------------------------------------------------------------------------------

/**
 * A firefly in a jar, in the dark: the jar is only seen where the bug's own light reaches it, so the glass appears
 * and fades as it flies. Its light is its mood: bright and quick when happy, a weak flicker when hungry, a slow
 * breathing glow asleep; its signature lights the whole jar at once in three flashes.
 */
private object Firefly : NewHero(
    "firefly", listOf("Glowworm", "Firefly", "Lantern Bug"), Rarity.RARE,
    "Светлячок в банке: банку видно только там, куда достаёт его свет",
    mapOf("calm" to "летает и светит", "happy" to "кружит, свет ярче", "habit" to "чистит крылышки на стенке", "signature" to "три вспышки на всю банку",
        "hungry" to "свет мерцает слабо", "sad" to "еле тлеет в углу", "tired" to "свет гаснет и разгорается", "asleep" to "дышит светом на дне",
        "eating" to "пьёт каплю росы", "game" to "летит за наклоном", "loved" to "светится ровно и тепло", "dizzy" to "свет мигает по кругу",
        "tilt" to "сносит к стенке", "charging" to "разгорается на всю банку", "grow" to "личинка зажигается"),
) {
    /** The jar's glass: lid, neck, walls and bottom. */
    private fun glass(x: Int, y: Int): Boolean =
        (y == 3 && x in 9..15) || (y == 4 && (x == 9 || x == 15)) || (y == 5 && (x in 6..9 || x in 15..18)) ||
            (y in 6..20 && (x == 6 || x == 18)) || (y == 21 && x in 6..18)

    /** How far each stage's light reaches. */
    private val REACH = floatArrayOf(7f, 8.5f, 10f)

    /**
     * The jar lit by lights at [lights] (x, y, power 0..1): each glass cell as bright as the nearest light makes it, and
     * a faint haze right round each light. [all] lights the whole jar evenly (a flash).
     */
    private fun lit(c: Cv, st: Int, lights: List<Triple<Float, Float, Float>>, all: Float = 0f) {
        val reach = REACH[st]
        for (y in 0..24) for (x in 0..24) {
            var g = all * 0.8f
            var haze = 0f
            for ((lx, ly, p) in lights) {
                val d = hypot(x - lx, y - ly)
                val r = reach * (0.35f + 0.65f * p)
                if (d < r) g = max(g, p * (1f - d / r) * 0.9f + 0.1f)
                if (d < 3.6f) haze = max(haze, p * (0.2f - d * 0.04f))
            }
            if (glass(x, y)) { if (g > 0.08f) c.put(x, y, g) } else if (haze > 0f && x in 7..17 && y in 4..20) c.put(x, y, haze)
        }
    }

    /**
     * The firefly at ([x], [y]): a glowing body (2x2, 3x2 grown) with two dark eyes on top, wings beside it that flap
     * with [flap]. [glow] 0..1 is its light; [eyes] 0 open, 1 shut.
     */
    private fun bug(c: Cv, st: Int, x: Float, y: Float, glow: Float, eyes: Int = 0, flap: Boolean = true) {
        // A head with two dark eyes on top, a glowing tail below it (the bigger the stage, the bigger the lamp), and a
        // wing each side that flaps.
        val X = x.roundToInt(); val Y = y.roundToInt()
        val w = if (st == 0) 3 else 4
        val tail = if (st == 2) 3 else 2
        val lamp = 0.5f + 0.5f * glow
        for (i in 0 until w) c.set(X + i, Y, 0.55f)
        for (j in 1..tail) for (i in 0 until w) if (!(j == tail && (i == 0 || i == w - 1) && st > 0)) c.set(X + i, Y + j, lamp)
        if (eyes == 0) { c.cut(X, Y); c.cut(X + w - 1, Y) } else { c.set(X, Y, 0.3f); c.set(X + w - 1, Y, 0.3f) }
        val wy = if (flap) Y - 1 else Y + 1
        c.set(X - 1, wy, 0.5f); c.set(X + w, wy, 0.5f)
        if (st > 0) { c.set(X - 1, if (flap) Y - 2 else Y + 2, 0.35f); c.set(X + w, if (flap) Y - 2 else Y + 2, 0.35f) }
    }

    /** Where it flies in the jar: a slow loop round it. */
    private fun fly(t: Long, speed: Float = 1f): Pair<Float, Float> =
        (11.5f + sin(t / 760f * speed) * 4f) to (12f + sin(t / 520f * speed) * 5f)

    private fun flap(t: Long, fast: Boolean = false) = (t / (if (fast) 60 else 110)) % 2 == 0L

    private var tKey = -1
    private var tFlash = -99_999L

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        val (fx, fy) = fly(t)
        when (col) {
            "calm" -> { val g = 0.75f + 0.25f * sin(t / 300f); lit(c, st, listOf(Triple(fx + 1, fy + 1, g))); bug(c, st, fx, fy, g, if (blink(t)) 1 else 0, flap(t)) }
            "happy" -> { val a = t / 2400f * 2 * PIF; val x = 11.5f + cos(a) * 4.5f; val y = 12f + sin(a * 2) * 4f
                val g = if ((t / 300) % 2 == 0L) 1f else 0.8f; lit(c, st, listOf(Triple(x + 1, y + 1, g))); bug(c, st, x, y, g, 0, flap(t, true)) }
            "habit" -> { val g = 0.6f + 0.3f * sin(t / 500f); lit(c, st, listOf(Triple(8.5f, 12f, g))); bug(c, st, 7f, 11f, g, if (t % 1600 < 200) 1 else 0, flap = t % 800 < 400) }
            "signature" -> {
                // Three flashes that light the whole jar, then it flies on.
                val k = (t / 700).toInt(); val inFlash = k in 1..3 && t % 700 < 260
                val all = if (inFlash) 1f - (t % 700) / 260f else 0f
                lit(c, st, listOf(Triple(13f, 13f, 1f)), all)
                if (inFlash) for (y in 4..20) for (x in 7..17) c.put(x, y, 0.18f * all)
                bug(c, st, 12f, 12f, if (inFlash) 1f else 0.7f, 0, flap(t))
            }
            "hungry" -> { val g = if (sin(t / 90f) > 0.3f) 0.45f else 0.15f; lit(c, st, listOf(Triple(12f, 19f, g))); bug(c, st, 11f, 18f, g, 0, flap = false); thought(c, listOf(".o.", "ooo", ".o."), t) }
            "sad" -> { val g = 0.18f + 0.1f * sin(t / 400f); val x = 7f + (if (t % 3200 in 1600L..1900L) 1 else 0); lit(c, st, listOf(Triple(x + 1.5f, 19.5f, g))); bug(c, st, x, 19f, g, 1, flap = t % 3200 in 1600L..1900L) }
            "tired" -> { val g = 0.2f + 0.6f * (0.5f + 0.5f * sin(t / 700f)); val (x, y) = fly(t, 0.5f); lit(c, st, listOf(Triple(x + 1, y + 4, g))); bug(c, st, x, y + 3, g, if (g < 0.4f) 1 else 0, flap(t)) }
            "asleep" -> { val g = 0.12f + 0.25f * (0.5f + 0.5f * sin(t / 650f)); lit(c, st, listOf(Triple(12f, 19.5f, g))); bug(c, st, 11f, 19f, g, 1, flap = false) }
            "eating" -> {
                // A drop of dew falls from the lid; it flies down to it and drinks, its light brightening.
                val drop = 4f + seg(t, 0, 1200) * 12f
                val bx = mix(fx, 11f, seg(t, 600, 1400)); val by = mix(fy, 15f, seg(t, 600, 1400))
                val g = 0.6f + 0.4f * seg(t, 1500, 2600)
                lit(c, st, listOf(Triple(bx + 1, by + 1, g)))
                if (t < 1500) c.put(12f, drop, 0.8f) else if (t < 2400) c.put(12f, 17f, 0.8f * (1 - seg(t, 1500, 2400)))
                bug(c, st, bx, by, g, if (t > 1600) 1 else 0, flap(t))
            }
            "game" -> { val x = 11f + tilt * 5f; val y = 12f + sin(t / 400f) * 4f; lit(c, st, listOf(Triple(x + 1, y + 1, 0.9f))); bug(c, st, x, y, 0.9f, 0, flap(t, true)) }
            "loved" -> { val a = t / 2400f * 2 * PIF; val hx = 11.5f + 3f * sin(a).let { it * it * it } * 1.3f
                val hy = 12f - (13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)) / 5f
                lit(c, st, listOf(Triple(hx + 1, hy + 1, 1f))); bug(c, st, hx, hy, 1f, 1, flap(t)) }
            "dizzy" -> { val a = t / 300f; val x = 11.5f + cos(a) * 4f; val y = 12f + sin(a) * 4f; val g = if ((t / 120) % 2 == 0L) 1f else 0.3f
                lit(c, st, listOf(Triple(x + 1, y + 1, g))); bug(c, st, x, y, g, 1, flap(t, true)) }
            "tilt" -> { val x = 11f + tilt * 5.5f; lit(c, st, listOf(Triple(x + 1, fy + 1, 0.85f))); bug(c, st, x, fy, 0.85f, 0, flap(t)) }
            "charging" -> { val p = seg(t, 0, 3000); lit(c, st, listOf(Triple(12f, 12f, 0.5f + 0.5f * p)), p * 0.6f); bug(c, st, 11f, 11f, 0.5f + 0.5f * p, 0, flap(t)) }
            "grow" -> {
                // A larva on the floor lights up, glows brighter, and lifts off as the firefly.
                if (t < 3000) { val g = seg(t, 500, 3000); lit(c, st, listOf(Triple(12f, 20f, g * 0.6f))); for (i in 0..2) c.set(11 + i, 20, 0.3f + 0.6f * g) }
                else { val p = seg(t, 3000, 5000); val x = mix(11f, fx, p); val y = mix(19f, fy, p); lit(c, st, listOf(Triple(x + 1, y + 1, 0.8f))); bug(c, st, x, y, 0.8f, 0, flap(t)) }
            }
            // Typing: every key a flash that lights the whole jar for a moment; at 20 s a second firefly joins it, at
            // 40 s the jar is full of them, twinkling, and the glass glows all round.
            "typing" -> { val y = DrawnTyping
                if (y.key != tKey) { if (y.key >= 0) tFlash = y.now; tKey = y.key }
                val flash = (1f - (y.now - tFlash) / 220f).coerceIn(0f, 1f)
                val n = 1 + min(4, y.stage * 2)
                val lights = (0 until n).map { i -> val (x, yy) = fly(t + i * 1300L, 1f + i * 0.15f); Triple(x + 1, yy + 1, 0.7f + 0.3f * sin(t / 200f + i)) }
                lit(c, st, lights, max(flash, if (y.stage == 2) 0.35f else 0f))
                lights.forEachIndexed { i, (x, yy, g) -> bug(c, if (i == 0) st else 0, x - 1, yy - 1, max(g, flash), if (y.paused && blink(t)) 1 else 0, flap(t + i * 37, y.rush)) }
            }
        }
    }
}
