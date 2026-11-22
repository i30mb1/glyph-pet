package n7.glyphpet.pet.typing

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// More typing toys, for the heroes whose first scenes were dull: real physics and big effects.

private fun g40(d: DeskInput) = (d.typed / 40_000f).coerceIn(0f, 1f)
private fun past40(d: DeskInput) = d.typed > 40_000
private fun ago(d: DeskInput) = if (d.last == null) 99_999L else d.sinceKey

/** Phoenix, fireworks of fire: each key a fireball shoots up and bursts into a flower of flame; a blaze climbs from below as you type, and after 40 s a bird of fire spreads its wings over it. */
internal class FireworkToy : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val g = g40(d)
        for (i in 0 until 5) Fire.flame(heat, 2f + i * 5f, 24f, d.t, size = 0.25f + 0.45f * g, power = 0.4f + 0.6f * g, salt = i)
        d.recent(1100) { k, a ->
            val x = 5f + h(k.n, 1) * 14f; val top = 5f + h(k.n, 2) * 5f
            if (a < 380) Fire.flame(heat, x, mix(23f, top, a / 380f), d.t, size = 0.22f, power = 1f, salt = k.n)
            else { val p = (a - 380) / 720f; for (j in 0 until 6) { val an = j * 1.047f + h(k.n, 3); Fire.flame(heat, x + cos(an) * p * 5f, top + sin(an) * p * 5f, d.t, size = 0.25f * (1 - p), ux = cos(an), uy = sin(an), power = 1f - p, salt = k.n * 7 + j) } }
        }
        if (past40(d)) {
            val flap = sin(d.t / 260f) * 0.5f
            Fire.flame(heat, 12f, 12f, d.t, size = 0.55f, power = 1f, salt = 90)
            for (s in listOf(-1f, 1f)) for (j in 1..3) Fire.flame(heat, 12f + s * j * 2.6f, 11f - j * (1.6f + flap), d.t, size = 0.35f, ux = s * 0.6f, uy = -0.8f, power = 1f, salt = 91 + j + (s * 5).toInt())
        }
        Fire.render(c, heat)
    }
}

/** Lava lamp, real lava: blobs of wax that melt into one another where they meet. Each key a blob breaks off the pool at the bottom and wobbles up; the lamp heats up as you type, and after 40 s the wax gathers into one great throbbing blob. */
internal class LavaToy : Desk() {
    private val bx = FloatArray(10); private val by = FloatArray(10); private val br = FloatArray(10); private val born = LongArray(10) { -99_999L }
    private var lastN = -1; private var slot = 0
    private fun inLamp(x: Int, y: Int) = y in 3..21 && abs(x - 12f) <= 3.5f + (y - 3) * 0.22f
    override fun draw(c: Cv, d: DeskInput) {
        val g = g40(d)
        val k = d.last
        if (k != null && k.n != lastN && k.n % 3 == 0) { lastN = k.n; bx[slot] = 8.5f + h(k.n, 1) * 7f; br[slot] = 1.1f + h(k.n, 2) * 0.5f; born[slot] = d.t; slot = (slot + 1) % 10 }
        for (i in 0 until 10) { val a = (d.t - born[i]) / 1000f; by[i] = if (a > 4f) 99f else 20f - min(16f, a * (4f + 4f * g)) + sin(a * 2 + i) * 0.5f }
        for (y in 2..23) for (x in 0..24) {
            if (!inLamp(x, y)) { if (y in 3..21 && abs(abs(x - 12f) - (3.5f + (y - 3) * 0.22f) - 0.8f) < 0.6f) c.put(x, y, 0.22f); continue }
            val wob = d.hit(200) * 1.2f
            var f = (2.5f + wob) / ((x - 12f) * (x - 12f) * 0.5f + (y - 22f + wob) * (y - 22f + wob) * 2f + 0.1f)
            if (past40(d)) { val r = 3.2f + sin(d.t / 400f) * 0.6f; f += r * r / ((x - 12f) * (x - 12f) + (y - 12f) * (y - 12f) + 0.1f) }
            for (i in 0 until 10) if (by[i] < 50f) { val dx = x - bx[i] - sin((d.t - born[i]) / 500f) * 0.8f; val dy = y - by[i]; f += br[i] * br[i] / (dx * dx + dy * dy * 1.3f + 0.1f) }
            c.put(x, y, when { f > 1.6f -> 0.55f + 0.25f * g; f > 1f -> 0.95f; else -> 0.05f + 0.06f * g })
        }
        for (x in 7..17) { c.put(x, 22, 0.45f); c.put(x, 23, 0.35f) }; for (x in 9..15) c.put(x, 2, 0.4f)
    }
}

/** Pebble, skimming stones: a lake. Each key the pebble skips once across it, a ring spreading where it touched; the skips get longer as you type, and after 40 s it lands with a splash that rings the whole lake. */
internal class SkimToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = g40(d)
        for (x in 0..24) for (y in 15..24) c.put(x, y, 0.08f + 0.02f * sin(x * 0.8f + y + d.t / 300f))
        val n = d.last?.n ?: -1
        val per = 6
        val i = if (n < 0) 0 else n % per
        val step = 3f + g * 0.6f
        d.recent(1500) { k, a -> val j = k.n % per; val x = 3f + (j + 1) * step; val p = a / 1500f; c.ring(x, 15.5f, 1f + p * 5f, 0.6f * (1 - p), 0.5f, 0f, 3.14f); c.ring(x, 15.5f, 1f + p * 5f, 0.3f * (1 - p), 0.5f, 3.14f, 6.28f) }
        val a = ago(d); val q = (a / 260f).coerceAtMost(1f)
        val x0 = 3f + i * step; val x1 = 3f + (i + 1) * step
        val px = if (n < 0) 3f else mix(x0, x1, q); val py = 14f - sin(q * PIF) * (2f + 2f * g)
        c.shape(listOf(".##.", "####"), px.roundToInt(), py.roundToInt(), 0.5f, 0.7f)
        if (past40(d)) { val p = ((d.typed - 40_000) % 2500) / 2500f; c.ring(12f, 16f, p * 14f, 0.8f * (1 - p), 0.5f); if (p < 0.3f) for (s in -2..2) c.put(12f + s, 15f - (2 - abs(s)) * p * 10, 0.9f) }
    }
}

/** Astronaut, the launch: a rocket on its pad. Each key a burst of fuel goes in (the gauge rises, steam puffs from the pad); at 40 s the engines light and it lifts off on a column of fire, the stars rushing past, and launches again. */
internal class LaunchToy : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val g = g40(d)
        val since = if (past40(d)) (d.typed - 40_000) % 6000 else -1L
        val lift = if (since < 0) 0f else (since / 1000f).let { it * it * 4f }
        if (since >= 0) for (i in 0 until 10) { val y = ((h(i, 1) * 25 + since / 30f * (1 + h(i, 2))) % 25); c.put(h(i, 3) * 24, y, 0.4f) }
        val ry = 19f - lift
        c.shape(listOf("..#..", ".###.", ".#o#.", ".###.", ".###.", "#####", "#.#.#"), 12, ry.roundToInt() - 3, 0.4f, 0.6f)
        c.set(12, ry.roundToInt() - 4, 0.05f)
        if (lift < 1f) { for (x in 5..19) c.put(x, 21, 0.4f); for (y in 10..20) c.put(5, y, 0.3f); for (y in 20 - (g * 10).roundToInt()..20) c.put(4, y, 0.9f) }
        if (since < 0) d.recent(600) { k, a -> val p = a / 600f; for (s in listOf(-1f, 1f)) c.disc(12f + s * (3f + p * 5f), 20f - p * 2f, 1.2f * (1 - p) + 0.3f, 0.5f * (1 - p)) }
        if (since >= 0) Fire.flame(heat, 12f, ry + 1f, d.t, size = 0.7f, uy = 1f, power = 1f, salt = 3)
        else if (g > 0.85f && (d.t / 120) % 2 == 0L) Fire.flame(heat, 12f, ry + 1f, d.t, size = 0.25f, uy = 1f, power = 0.6f, salt = 4)
        Fire.render(c, heat)
    }
}

/** Kodama, the tree: a tree grows branch by branch as you type; each key a leaf lights on it; after 40 s the spirits sit along its branches, their heads clicking. */
internal class TreeToy : Desk() {
    private val tips = ArrayList<Pair<Float, Float>>()
    private fun branch(c: Cv, x: Float, y: Float, a: Float, len: Float, depth: Int, max: Int) {
        if (depth > max) { tips += x to y; return }
        val x2 = x + cos(a) * len; val y2 = y + sin(a) * len
        c.line(x, y, x2, y2, 0.3f + 0.05f * (4 - depth).coerceAtLeast(0))
        branch(c, x2, y2, a - 0.5f, len * 0.72f, depth + 1, max); branch(c, x2, y2, a + 0.45f, len * 0.72f, depth + 1, max)
    }
    override fun draw(c: Cv, d: DeskInput) {
        val g = g40(d); tips.clear()
        val max = (g * 4.99f).toInt()
        for (x in 0..24) c.put(x, 23, 0.2f)
        branch(c, 12f, 23f, -PIF / 2, 5f + g * 1.5f, 0, max)
        val n = d.last?.n ?: -1
        val lit = min(n + 1, tips.size * 2)
        for (i in 0 until lit) { val (x, y) = tips[(i * 7) % tips.size]; val fresh = i == lit - 1 && ago(d) < 200
            c.put(x + (if (i >= tips.size) 1 else 0), y - (if (i >= tips.size) 1 else 0), if (fresh) 1f else 0.6f) }
        if (past40(d)) for ((j, t) in tips.withIndex()) if (j % 3 == 0) { val (x, y) = t; val tilt = if (ago(d) < 150 && (n + j) % 2 == 0) 1 else 0
            c.disc(x + tilt, y - 1.5f, 1.2f, 0.7f); c.cut((x - 0.5f + tilt).roundToInt(), (y - 1.5f).roundToInt()); c.cut((x + 0.5f + tilt).roundToInt(), (y - 1.5f).roundToInt()) }
    }
}

/** Glitch, decryption: digital rain pours down the matrix; each key a column flares and runs. As you type a hidden heart locks in out of the rain, cell by cell, and after 40 s it beats, glitching. */
internal class DecryptToy : Desk() {
    private companion object { val HEART = listOf(".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#....") }
    override fun draw(c: Cv, d: DeskInput) {
        val g = g40(d)
        val flare = d.last?.let { (it.n * 7) % 25 } ?: -1
        for (x in 0..24) { val speed = 6f + h(x, 1) * 8f + (if (x == flare && ago(d) < 500) 14f else 0f); val head = ((d.t / 1000f * speed + h(x, 2) * 25) % 32) - 4
            for (j in 0..5) { val y = (head - j).roundToInt(); c.put(x, y, if (j == 0) (if (x == flare && ago(d) < 500) 1f else 0.6f) else 0.28f - j * 0.04f) } }
        val beat = if (past40(d)) 1f + 0.25f * max(0f, sin(d.t / 180f)) else 1f
        val shift = if (past40(d) && ago(d) < 100) 1 else 0
        HEART.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '#' && h(i, j + 40) < g * 1.05f) c.set(8 + i + shift, 8 + j, min(1f, 0.75f * beat)) } }
    }
}

/** Hourglass, real sand: each key a handful of grains pours in at the top and runs down through the neck, heaping up and sliding; after 40 s it turns over and pours back. */
internal class SandToy : Desk() {
    private val sand = BooleanArray(625); private var lastN = -1; private var flipped = false
    private fun half(y: Int) = 1 + (abs(y - 12) * 0.62f).roundToInt()
    private fun inside(x: Int, y: Int) = y in 2..22 && abs(x - 12) < half(y)
    override fun draw(c: Cv, d: DeskInput) {
        val k = d.last
        if (k != null && k.n != lastN) { lastN = k.n; for (dx in -2..2) if (inside(12 + dx, 3)) sand[3 * 25 + 12 + dx] = true }
        if (past40(d) && !flipped) { flipped = true; val copy = sand.copyOf(); for (y in 0..24) for (x in 0..24) sand[y * 25 + x] = copy[(24 - y) * 25 + x] && inside(x, 24 - y) }
        for (step in 0 until 2) for (y in 21 downTo 2) for (x in 0..24) {
            val i = y * 25 + x; if (!sand[i]) continue
            val dir = if ((x + y + d.t / 40) % 2 == 0L) 1 else -1
            when {
                inside(x, y + 1) && !sand[i + 25] -> { sand[i] = false; sand[i + 25] = true }
                inside(x + dir, y + 1) && !sand[i + 25 + dir] -> { sand[i] = false; sand[i + 25 + dir] = true }
                inside(x - dir, y + 1) && !sand[i + 25 - dir] -> { sand[i] = false; sand[i + 25 - dir] = true }
            }
        }
        for (y in 2..22) { val w = half(y); c.put(12 - w, y, 0.25f); c.put(12 + w, y, 0.25f) }
        for (x in 6..18) { c.put(x, 1, 0.5f); c.put(x, 23, 0.5f) }
        for (i in 0 until 625) if (sand[i]) c.put(i % 25, i / 25, 0.55f + 0.2f * h(i, 3))
    }
}

/** Bubble, bubble wrap: a sheet of bubbles. Each key pops one (a flash, and it lies flat); pop them all and a fresh sheet slides in; after 40 s a wave of pops runs across every sheet by itself. */
internal class WrapToy : Desk() {
    private val popped = LongArray(25) { -1L }; private var lastN = -1
    override fun draw(c: Cv, d: DeskInput) {
        val k = d.last
        if (k != null && k.n != lastN) { lastN = k.n
            if (popped.all { it >= 0 }) popped.fill(-1L)
            var i = (h(k.n, 4) * 25).toInt().coerceIn(0, 24); while (popped[i] >= 0) i = (i + 7) % 25; popped[i] = d.t }
        if (past40(d)) { val wave = ((d.typed - 40_000) / 60) % 40; for (i in 0 until 25) if ((i % 5 + i / 5).toLong() == wave % 10 && popped[i] < 0) popped[i] = d.t; if (wave % 40 == 39L) popped.fill(-1L) }
        for (i in 0 until 25) { val x = 4 + (i % 5) * 4; val y = 4 + (i / 5) * 4; val a = if (popped[i] < 0) -1L else d.t - popped[i]
            when {
                a < 0 -> { c.ring(x.toFloat(), y.toFloat(), 1.3f, 0.55f, 0.55f); c.put(x - 1, y - 1, 1f) }
                a < 160 -> { c.disc(x.toFloat(), y.toFloat(), 1.8f, 1f); c.ring(x.toFloat(), y.toFloat(), 2f + a / 50f, 0.6f, 0.5f) }
                else -> { c.put(x, y, 0.2f); c.put(x - 1, y, 0.12f); c.put(x + 1, y, 0.12f) }
            } }
    }
}

/** Capybara, the bath party: it sits in its hot spring and friends join it as you type (a duck, a bird, a frog, a little capybara), splashing in; each key they all bob in turn; after 40 s every head wears a yuzu. */
internal class BathToy : Desk() {
    private companion object { val FRIENDS = listOf(listOf(".##.", "####", "#..."), listOf(".#.", "###", "#.#"), listOf("#.#", "###", ".#."), listOf("#..#", "####", "####")) ; val SLOTS = listOf(5, 19, 9, 15, 3, 21) }
    override fun draw(c: Cv, d: DeskInput) {
        val st = d.pet.species.stage.ordinal
        Drawn.of("capybara")?.draw(c, if (d.typed > 40_000) "loved" else "calm", d.t % 2400, st, 0f)
        val have = min(6, 1 + (d.typed / 6500).toInt())
        val n = d.last?.n ?: -1
        for (i in 0 until have) {
            val x = SLOTS[i]; val bornAt = i * 6500L
            val fall = ((d.typed - bornAt) / 400f).coerceAtMost(1f)
            val bob = if (n >= 0 && n % have == i && ago(d) < 180) 1 else 0
            val y = mix(-3f, 19f, fall).roundToInt() - bob
            c.shape(FRIENDS[i % 4], x, y, 0.75f, 0.95f)
            if (fall < 1f || d.typed - bornAt < 700) { val p = ((d.typed - bornAt - 400) / 300f).coerceIn(0f, 1f); if (p > 0f) c.ring(x.toFloat(), 20f, 1f + p * 3, 0.7f * (1 - p), 0.5f, 3.14f, 6.28f) }
            if (past40(d)) c.disc(x.toFloat(), y - 2.5f, 0.9f, 0.9f)
        }
    }
}

/** Cassette, the boombox: a tape spinning over an equalizer. Each key is a beat: the bars jump and a bass ring thumps out; more bars join as you type, and after 40 s the whole thing strobes like a rave. */
internal class BoomboxToy : Desk() {
    private val bars = FloatArray(7)
    override fun draw(c: Cv, d: DeskInput) {
        val g = g40(d)
        val k = d.last
        val beat = ago(d) < 60
        for (i in 0 until 7) { if (beat && k != null) bars[i] = max(bars[i], 3f + h(k.n, i) * (4f + 5f * g)); bars[i] = max(0f, bars[i] - d.dtMs / 90f) }
        val active = 3 + (g * 4).roundToInt().coerceAtMost(4)
        for (i in 0 until 7) { if (abs(i - 3) > active / 2) continue; val x = 3 + i * 3; val hh = bars[i].roundToInt()
            for (j in 0 until hh) { c.put(x, 21 - j, if (j == hh - 1) 1f else 0.5f); c.put(x + 1, 21 - j, if (j == hh - 1) 1f else 0.5f) } }
        val spin = d.t / (if (d.rush) 60f else 200f)
        c.shape(listOf("#############", "#...........#", "#...........#", "#...........#", "#############"), 12, 5, 0.12f, 0.45f)
        for (cx in listOf(8f, 16f)) { c.ring(cx, 5f, 1.4f, 0.35f, 0.6f); c.put(cx + cos(spin) * 1.4f, 5f + sin(spin) * 1.4f, 1f); c.put(cx - cos(spin) * 1.4f, 5f - sin(spin) * 1.4f, 1f) }
        if (k != null && ago(d) < 400) c.ring(12f, 14f, 2f + ago(d) / 40f, 0.5f * (1 - ago(d) / 400f), 0.6f)
        if (past40(d) && (d.t / 90) % 4 == 0L) for (y in 0..24) for (x in 0..24) if ((x + y) % 2 == 0) c.put(x, y, 0.2f)
    }
}

@Suppress("unused") private val keep = GlyphMatrixSpec::class

/** Shadow, hide and seek: the dark, and a torch. Each key the beam swings to a new spot and the shadow's eyes flit away to where it is dark; more pairs of eyes watch as you type, and after 40 s the torch dies and eyes blink all round. */
internal class TorchToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = d.last?.n ?: -1
        val tx = if (n < 0) 12f else 4f + h(n, 1) * 16f; val ty = if (n < 0) 8f else 3f + h(n, 2) * 10f
        val swing = (ago(d) / 150f).coerceAtMost(1f)
        val px = if (n <= 0) tx else mix(4f + h(n - 1, 1) * 16f, tx, ease(swing)); val py = if (n <= 0) ty else mix(3f + h(n - 1, 2) * 10f, ty, ease(swing))
        val torch = !past40(d)
        if (torch) for (y in 0..24) for (x in 0..24) { val r = hypot(x - px, y - py); if (r < 4.5f) c.put(x, y, if (r > 3.6f) 0.5f else 0.28f) else {
            val ax = x - 12f; val ay = y - 24f; val bx = px - 12f; val by = py - 24f; val t = ((ax * bx + ay * by) / (bx * bx + by * by)).coerceIn(0f, 1f)
            if (hypot(ax - bx * t, ay - by * t) < 0.6f + 2.5f * t) c.put(x, y, 0.08f) } }
        val pairs = if (torch) min(6, 1 + (d.typed / 7000).toInt()) else 10
        for (i in 0 until pairs) { var ex = 3f + h(i + n.coerceAtLeast(0) * (if (i == 0) 1 else 0), 5 + i) * 18f; var ey = 3f + h(i, 9 + n.coerceAtLeast(0) * (if (i == 0) 1 else 0)) * 16f
            if (torch && hypot(ex - px, ey - py) < 6f) { ex = 24f - ex; ey = 24f - ey }
            if (((d.t / 100) + i * 13) % 37 == 0L) continue
            c.set(ex.roundToInt() - 1, ey.roundToInt(), 1f); c.set(ex.roundToInt() + 1, ey.roundToInt(), 1f) }
    }
}
