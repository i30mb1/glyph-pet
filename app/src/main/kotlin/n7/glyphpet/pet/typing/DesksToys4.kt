package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun gg(d: DeskInput) = (d.typed / 40_000f).coerceIn(0f, 1f)
private fun done(d: DeskInput) = d.typed > 40_000
private fun sinceKey(d: DeskInput) = if (d.last == null) 99_999L else d.sinceKey

/** Sprout, the vine: a vine winds up the matrix as you type, and each key a flower bursts open at its tip; after 40 s butterflies come to the flowers. */
internal class VineToy : Desk() {
    private val blooms = ArrayList<Pair<Float, Long>>()
    private var lastN = -1
    private fun path(s: Float) = (12f + sin(s * 0.45f) * (2f + s * 0.25f)) to (22f - s * 0.85f)
    override fun draw(c: Cv, d: DeskInput) {
        val len = 2f + gg(d) * 22f
        val k = d.last
        if (k != null && k.n != lastN) { lastN = k.n; blooms += (1.5f + h(k.n, 3) * (len - 1.5f)) to d.t; if (blooms.size > 18) blooms.removeAt(0) }
        c.shape(listOf("#######", ".#####.", ".#####."), 12, 23, 0.3f, 0.45f)
        var s = 0f; while (s < len) { val (x, y) = path(s); c.put(x, y, 0.4f); s += 0.3f }
        for ((bs, at) in blooms) { val (x, y) = path(bs); val a = d.t - at; val open = ease(a / 250f)
            val r = 0.5f + open; val glow = if (done(d)) 0.2f * sin(d.t / 200f + bs) else 0f
            for (j in 0 until 4) { val an = j * PIF / 2 + bs; c.put(x + cos(an) * r * 1.2f, y + sin(an) * r, 0.65f + glow) }
            c.put(x, y, if (a < 200) 1f else 0.9f) }
        if (done(d)) for (b in 0..1) { val bx = 12f + sin(d.t / 900f + b * 3) * 7f; val by = 8f + cos(d.t / 700f + b * 2) * 4f; val flap = (d.t / 120 + b) % 2 == 0L
            c.put(bx, by, 0.9f); c.put(bx - 1, by - (if (flap) 1 else 0), 0.7f); c.put(bx + 1, by - (if (flap) 1 else 0), 0.7f) }
    }
}

/** Moon, the eclipse: each key a meteor streaks across the night past it; the earth's shadow creeps over the moon as you type, and after 40 s it is a total eclipse, a corona blazing round the dark disc. */
internal class EclipseToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gg(d)
        for (i in 0 until 6 + (g * 14).toInt()) c.put(1 + h(i, 1) * 22, 1 + h(i, 2) * 22, 0.18f + 0.12f * sin(d.t / 500f + i))
        val sx = mix(-11f, 0f, g)
        for (y in 0..24) for (x in 0..24) { val r = hypot(x - 12f, y - 12f); if (r > 6f) continue
            val shadow = hypot(x - 12f - sx, y - 12f) < 6.3f
            c.set(x, y, if (shadow) 0.06f else 0.42f + 0.06f * sin(x * 1.3f + y)) }
        if (done(d)) { val pulse = 0.5f + 0.5f * sin(d.t / 200f); c.ring(12f, 12f, 6.8f, 0.7f + 0.3f * pulse, 0.6f)
            for (j in 0 until 12) { val a = j * PIF / 6 + d.t / 3000f; val l = 2f + 2f * ((j % 3) / 2f) * pulse; c.line(12 + cos(a) * 7.5f, 12 + sin(a) * 7.5f, 12 + cos(a) * (7.5f + l), 12 + sin(a) * (7.5f + l), 0.5f) } }
        d.recent(500) { k, a -> val p = a / 500f; val x0 = 26f - h(k.n, 1) * 6; val y0 = h(k.n, 2) * 8 - 2
            val x = mix(x0, x0 - 24f, p); val y = mix(y0, y0 + 16f, p); for (t in 0..4) c.put(x + t * 1.5f, y - t, (1f - t * 0.2f) * (1 - p * 0.3f)) }
    }
}

/** Tardigrade, unkillable: each key the world throws something at it (fire, ice, lightning, radiation) and it shrugs it off; the backdrop climbs from earth to sky to space as you type, and after 40 s it floats among the stars, waving. */
internal class SurvivorToy : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val g = gg(d)
        if (g < 0.5f) for (x in 0..24) c.put(x, 21, 0.25f * (1 - g * 2))
        for (i in 0 until (g * 20).toInt()) c.put(h(i, 1) * 24, h(i, 2) * 24, 0.25f)
        val n = d.last?.n ?: -1; val a = sinceKey(d)
        val float = if (done(d)) sin(d.t / 600f) * 2f else 0f
        val hit = n >= 0 && a < 500
        if (hit) when (n % 4) {
            0 -> { Fire.flame(heat, 8f, 20f, d.t, size = 0.45f, power = 1f - a / 500f, salt = n); Fire.flame(heat, 16f, 20f, d.t, size = 0.45f, power = 1f - a / 500f, salt = n + 1) }
            1 -> for (j in 0 until 6) { val an = j * PIF / 3; c.line(12 + cos(an) * 6, 15 + sin(an) * 5, 12 + cos(an) * 8, 15 + sin(an) * 7, 0.8f) }
            2 -> { var x = 12f; for (y in 0..12) { x += if (y % 2 == 0) 1f else -1f; c.put(x, y.toFloat(), 1f) } }
            else -> c.ring(12f, 15f, 3f + a / 60f, 0.7f * (1 - a / 500f), 0.6f)
        }
        Fire.render(c, heat)
        c.pet(d, 12f, 20f + float, 10f, if (hit) Look.WOW else if (done(d)) Look.HAPPY else d.mood())
        if (hit && n % 4 == 1) for (x in 7..17) { c.put(x, 12, 0.6f); c.put(x, 20, 0.6f) }
    }

    private fun Cv.pet(d: DeskInput, x: Float, bottom: Float, hgt: Float, look: Look) {
        val s = d.pet.fit(hgt); d.pet.draw(this, x, bottom - hgt / 2f, s, look)
    }
}

/** Planet, terraforming: a turning globe. Each key a new continent rises out of its ocean; clouds come as you type, and after 40 s city lights twinkle on its night side. */
internal class TerraToy : Desk() {
    private val lands = ArrayList<Pair<Float, Float>>(); private var lastN = -1
    override fun draw(c: Cv, d: DeskInput) {
        val g = gg(d)
        val k = d.last
        if (k != null && k.n != lastN) { lastN = k.n; if (lands.size < 14) lands += (h(k.n, 1) * 6.283f) to ((h(k.n, 2) - 0.5f) * 2.2f) }
        val rot = d.t / 3000f; val r = 9.5f
        for (y in 0..24) for (x in 0..24) {
            val px = (x - 12f) / r; val py = (y - 12f) / r; val q = px * px + py * py; if (q > 1f) continue
            val z = sqrt(1 - q); val lon = atan2(px, z) + rot; val lat = asin(py.coerceIn(-1f, 1f))
            val light = (0.2f + 0.8f * (px * -0.6f + z * 0.8f)).coerceIn(0f, 1f)
            var land = false; for ((lo, la) in lands) { var dl = (lon - lo) % 6.283f; if (dl > 3.14f) dl -= 6.283f; if (dl < -3.14f) dl += 6.283f; if (hypot(dl * cos(lat), lat - la) < 0.36f) { land = true; break } }
            val fresh = k != null && sinceKey(d) < 250 && land
            val cloud = g > 0.3f && sin(lon * 3 + lat * 5 + d.t / 2000f) > 1.4f - g * 0.5f
            var v = if (land) 0.2f + 0.25f * light else 0.05f + 0.1f * light
            if (cloud) v = 0.35f + 0.3f * light
            if (fresh) v = 1f
            if (done(d) && land && light < 0.25f && h(x, y) > 0.6f && (d.t / 300 + x + y) % 4 != 0L) v = 0.85f
            c.put(x, y, v)
        }
        if (k != null && sinceKey(d) < 300) c.ring(12f, 12f, r + sinceKey(d) / 100f, 0.4f, 0.5f)
    }
}

/** Fox, the kitsune: each key a foxfire flares up and joins the ring circling it; it grows a tail for every few seconds typed, up to nine, and after 40 s the nine-tailed fox blazes. */
internal class KitsuneToy : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val tails = (1 + gg(d) * 8).toInt().coerceAtMost(9)
        for (i in 0 until tails) { val a = -PIF / 2 + (i - (tails - 1) / 2f) * 0.32f + sin(d.t / 500f + i) * 0.06f
            for (j in 0..14) { val q = j / 14f; val x = 12 + cos(a) * q * 10 + sin(q * 3 + i) * q * 1.5f; val y = 17 + sin(a) * q * 11
                if (j > 3) c.put(x, y, if (j > 11) (if (done(d)) 1f else 0.8f) else 0.22f + 0.1f * (if (done(d)) 1f else 0f)) } }
        val n = d.last?.n ?: -1
        for (i in 0 until min(n + 1, 6)) { val a = d.t / 900f + i * 1.047f; val pow = if (i == n % 6 && sinceKey(d) < 300) 1f else 0.6f
            Fire.flame(heat, 12 + cos(a) * 9.5f, 17 + sin(a) * 5f, d.t, size = 0.18f + 0.1f * pow, power = pow, salt = i) }
        Fire.render(c, heat)
        val s = d.pet.fit(8f); d.pet.draw(c, 12f, 22f - 4f, s, if (done(d)) Look.HAPPY else d.mood())
        for (x in 3..21) c.put(x, 23, 0.2f)
    }
}
