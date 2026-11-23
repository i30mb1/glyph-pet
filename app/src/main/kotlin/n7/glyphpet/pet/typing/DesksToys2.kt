package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The sprite heroes' typing toys: each key one juicy thing, something built as you type, a payoff after 40 s.

private fun gk(d: DeskInput) = (d.typed / 40_000f).coerceIn(0f, 1f)
private fun won(d: DeskInput) = d.typed > 40_000
private fun since(d: DeskInput) = if (d.last == null) 99_999L else d.sinceKey
private fun keyN(d: DeskInput) = d.last?.n ?: -1

/** The hero, [hgt] cells tall, standing with its feet on row [bottom] at column [x]. */
private fun Cv.pet(d: DeskInput, x: Float, bottom: Float, hgt: Float, look: Look = d.mood(), flip: Boolean = false, gain: Float = 1f) {
    val s = d.pet.fit(hgt)
    d.pet.draw(this, x, bottom - hgt / 2f, s, look, gain = gain, flipX = flip)
}

private fun Cv.ground(y: Int, b: Float = 0.25f) { for (x in 0..24) put(x, y, b) }

/** Fox, mousing in the snow: each key a leap and a nose-first dive into the snow, a burst of it flying; its tracks fill the field, and after 40 s the northern lights come out. */
internal class FoxToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        if (g > 0.3f) for (x in 0..24) { val h = 4f + sin(x * 0.4f + d.t / 900f) * 2f * g; for (y in 1..h.roundToInt()) c.put(x, y, 0.12f * g + 0.1f * (if (won(d)) 1f else 0f) * (0.5f + 0.5f * sin(d.t / 300f + x * 0.3f))) }
        for (x in 0..24) for (y in 18..24) c.put(x, y, 0.3f)
        val n = keyN(d); val a = since(d)
        for (i in 0 until min(n + 1, 40)) { val x = 2 + (i * 7) % 21; c.cut(x, 18); c.cut(x + 1, 18) }
        val x0 = 4f + (n.coerceAtLeast(0) * 7 % 15); val q = (a / 380f).coerceAtMost(1f)
        val x = x0 + q * 4; val y = 17f - sin(q * PIF) * 8f
        if (q < 1f) c.pet(d, x, y, 7f, Look.FOCUS) else c.pet(d, x0 + 4, 21f, 7f, Look.HAPPY, gain = 1f)
        if (q >= 1f && a < 700) { val p = (a - 380) / 320f; for (i in -3..3) c.put(x0 + 4 + i * (1 + p * 2), 17f - (3 - abs(i)) * p * 2, 0.9f * (1 - p)) }
    }
}

/** Chick, popcorn: a pan of corn on the fire. Each key a kernel pops, bursting into a white puff that bounces out; the heap grows as you type, and after 40 s it overflows in a fountain. */
internal class PopcornToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        for (x in 5..19) { c.put(x, 21, 0.5f); c.put(x, 22, 0.35f) }; c.put(4, 20, 0.5f); c.put(20, 20, 0.5f); for (x in 20..23) c.put(x, 21, 0.4f)
        for (x in 7..17) if ((x + d.t / 120) % 3 == 0L) c.put(x, 23, 0.7f)
        val heap = (g * 6).roundToInt()
        for (j in 0 until heap) for (x in 6 + j..18 - j) c.put(x, 20 - j, if ((x + j) % 3 == 0) 0.85f else 0.6f)
        d.recent(if (won(d)) 1500 else 900) { k, a -> val (x, y) = arc(8f + h(k.n) * 8f, 20f - heap, (h(k.n, 1) - 0.5f) * 16f, -16f - h(k.n, 2) * 10f - (if (won(d)) 8f else 0f), 50f, a)
            val s = if (a < 80) 0 else 1; c.put(x, y, 1f); if (s > 0) { c.put(x + 1, y, 0.8f); c.put(x, y - 1, 0.8f); c.put(x - 1, y, 0.6f) } }
        c.pet(d, 21f, 19f, 6f, if (d.hit(200) > 0f) Look.WOW else d.mood())
    }
}

/** Owl, the night watch: its two great eyes in the dark. Each key a light blinks on somewhere in the night and its eyes snap to it; the sky fills with stars as you type, and after 40 s the moon rises. */
internal class OwlToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        for (i in 0 until (g * 24).toInt()) c.put(1 + h(i, 4) * 22, 1 + h(i, 5) * 9, 0.25f + 0.15f * sin(d.t / 400f + i))
        if (won(d)) { val p = ease((d.typed - 40_000) / 2000f); c.disc(19f, 14f - p * 9, 3f, 0.55f) }
        val n = keyN(d); val tx = if (n < 0) 12f else 3f + h(n, 1) * 18f; val ty = if (n < 0) 12f else 2f + h(n, 2) * 10f
        if (n >= 0 && since(d) < 900) c.put(tx, ty, if (since(d) < 150) 1f else 0.6f)
        val dx = ((tx - 12f) / 9f).coerceIn(-1f, 1f); val dy = ((ty - 14f) / 9f).coerceIn(-1f, 1f)
        for (ex in listOf(8f, 16f)) { c.disc(ex, 14f, 3.4f, 0.5f); c.disc(ex, 14f, 2.4f, 0.12f)
            if (!(d.paused && d.t % 2600 < 160)) { val px = ex + dx * 1.2f; val py = 14f + dy * 1.2f; c.disc(px, py, 1.1f, 0f); for (yy in -1..1) for (xx in -1..1) if (abs(xx) + abs(yy) <= 1) c.cut(px.roundToInt() + xx, py.roundToInt() + yy) } }
        c.shape(listOf("#.#", ".#."), 12, 18, 0.6f, 0.6f)
    }
}

/** Penguin, curling: each key it shoves a stone that glides over the ice into the rings; the stones gather in the house as you type, and after 40 s one hits dead centre and the rings flash. */
internal class CurlingToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) for (y in 13..20) c.put(x, y, 0.08f)
        c.ring(19f, 16.5f, 3.5f, 0.35f, 0.5f); c.ring(19f, 16.5f, 1.8f, 0.5f, 0.5f)
        if (won(d) && (d.t / 150) % 2 == 0L) c.ring(19f, 16.5f, 3.5f, 1f, 0.5f)
        val n = keyN(d)
        for (i in 0 until min(n, 8)) { val a = h(i, 3) * 6.28f; val r = 0.5f + h(i, 4) * 3f; c.shape(listOf("##", "##"), (19 + cos(a) * r).roundToInt(), (16.5f + sin(a) * r * 0.6f).roundToInt(), 0.7f, 0.7f) }
        val a = since(d); val q = ease((a / 700f).coerceAtMost(1f))
        if (n >= 0 && a < 700) c.shape(listOf("##", "##"), mix(8f, 19f, q).roundToInt(), 17, 1f, 1f)
        c.pet(d, mix(5f, if (a < 200) 7f else 5f, ((a % 700) / 200f).coerceAtMost(1f)), 20f, 8f, if (won(d)) Look.HAPPY else d.mood())
    }
}

/** Mushroom, the fairy ring: each key a tiny mushroom pops out of the ground somewhere; the glade fills as you type, and after 40 s the ring of them glows. */
internal class FairyRingToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(21, 0.2f)
        val n = keyN(d)
        val glow = if (won(d)) 0.3f + 0.3f * sin(d.t / 250f) else 0f
        for (i in 0..min(n, 13)) {
            val a = i * 6.28f / 14f; val x = 12 + cos(a) * 9.5f; val y = 20.5f + sin(a) * 1.4f
            val grow = if (i == n) ease(since(d) / 250f) else 1f
            val hgt = (1 + grow * 2).roundToInt()
            for (j in 0 until hgt) c.put(x, y - j, 0.35f)
            c.put(x - 1, y - hgt, 0.6f + glow); c.put(x, y - hgt, 0.75f + glow); c.put(x + 1, y - hgt, 0.6f + glow)
        }
        c.pet(d, 12f, 19f, 9f)
    }
}

/** Cactus, the balloon range: balloons drift up past it; each key a needle shoots out and pops one with a bang. After 40 s it bursts into flower. */
internal class BalloonToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(22, 0.25f)
        c.pet(d, 6f, 22f, 12f, if (won(d)) Look.HAPPY else d.mood())
        if (won(d)) for (k in 0 until 5) { val a = k * 1.26f + d.t / 2000f; c.put(6 + cos(a) * 2, 9 + sin(a) * 2, 1f) }
        val n = keyN(d)
        for (i in 0 until 3) { val id = n + 1 + i; val y = 24f - ((d.t / 40 + i * 9) % 30); val x = 15f + h(id, 1) * 6f
            if (y < -3f) continue; c.disc(x, y, 1.6f, 0.55f); c.put(x - 0.7f, y - 0.7f, 1f); c.line(x, y + 2, x, y + 4, 0.2f) }
        val a = since(d)
        if (n >= 0 && a < 150) { val p = a / 150f; c.line(8f, 12f, mix(8f, 18f, p), mix(12f, 10f, p), 1f) }
        if (n >= 0 && a in 150..450) { val p = (a - 150) / 300f; for (k in 0 until 8) { val an = k * 0.785f; c.put(18 + cos(an) * (1 + p * 4), 10 + sin(an) * (1 + p * 4), 1f - p) } }
    }
}

/** Toast, the toaster: each key a slice shoots up out of it, flips in the air and lands on the stack; the stack rises as you type, and after 40 s the butter melts over it. */
internal class ToasterToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.shape(listOf("#########", "#########", "#########", "#########", "#########"), 7, 19, 0.3f, 0.5f); c.put(3, 16, 0.2f); c.put(4, 16, 0.2f); c.put(10, 16, 0.2f); c.put(11, 16, 0.2f)
        val lever = if (since(d) < 120) 18 else 15; c.put(12, lever, 0.8f)
        val n = keyN(d); val stack = min(n.coerceAtLeast(0), 10)
        for (i in 0 until stack) for (x in 15..21) c.put(x, 21 - i, if (x == 15 || x == 21) 0.5f else 0.35f)
        if (won(d)) { for (x in 16..20) c.put(x, 20 - stack + 1, 1f); for (k in 0..2) c.put(16 + k * 2, 21 - stack + 2 + ((d.t / 300 + k) % 3).toInt(), 0.9f) }
        val a = since(d)
        if (n >= 0 && a < 500) { val p = a / 500f; val x = mix(7f, 18f, p); val y = 14f - sin(p * PIF) * 10f; val flat = (a / 80) % 2 == 0L
            if (flat) for (dx in -3..3) c.put(x + dx, y, 0.6f) else for (dy in -2..2) c.put(x, y + dy, 0.6f) }
        c.ground(22, 0.2f)
    }
}

/** Snail, the race: a track to a chequered flag. Each key it creeps one step on, a trail of slime shining behind; it reaches the flag at 40 s and the flag waves. */
internal class SnailRaceToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(19, 0.22f)
        for (y in 10..18) c.put(21, y, 0.4f)
        for (j in 0..3) for (i in 0..2) c.put(22 + i, 10 + j + (if (won(d) && (d.t / 200 + i) % 2 == 0L) 1 else 0), if ((i + j) % 2 == 0) 0.9f else 0.15f)
        val x = mix(2f, 17f, gk(d)) + d.hit(200) * 0.6f
        for (xx in 0..x.roundToInt()) c.put(xx, 18, 0.45f + 0.2f * sin(xx * 0.8f + d.t / 200f))
        c.pet(d, x, 18f, 6f)
    }
}

/** Hedgehog, apples on its spines: apples drop from the tree; each key it rolls under one and catches it on its back. They pile up as you type; after 40 s it wears a crown of apples. */
internal class AppleToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) for (y in 0..3) c.put(x, y, 0.2f + 0.1f * sin(x + y * 2f))
        c.ground(22, 0.25f)
        val n = keyN(d); val a = since(d)
        val tx = if (n < 0) 12f else 5f + h(n, 1) * 14f
        val q = (a / 300f).coerceAtMost(1f)
        val hx = mix(12f, tx, ease(q))
        if (n >= 0 && a < 300) c.disc(tx, mix(4f, 15f, q), 1f, 0.9f)
        c.pet(d, hx, 22f, 7f)
        val on = min(n + (if (a >= 300) 1 else 0), if (won(d)) 9 else 6).coerceAtLeast(0)
        for (i in 0 until on) { val ang = PIF + (i + 0.5f) * PIF / on; c.disc(hx + cos(ang) * 4f, 18f + sin(ang) * 3f, 0.8f, 0.85f) }
    }
}

/** Crab, the sandcastle: each key it flings a claw of sand onto the castle; walls, then towers rise as you type, and after 40 s a flag flies on top. */
internal class CastleToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) for (y in 21..24) c.put(x, y, 0.22f)
        val g = gk(d); val hgt = (g * 10).roundToInt()
        for (j in 0 until hgt) for (x in 12..20) { val tower = x == 12 || x == 13 || x == 19 || x == 20; if (j < 6 || tower) c.put(x, 20 - j, if ((x + j) % 4 == 0) 0.45f else 0.32f) }
        if (hgt >= 7) for (x in listOf(12, 20)) c.put(x, 20 - hgt, 0.5f)
        if (hgt in 3..6) for (x in 12..20 step 2) c.put(x, 20 - hgt, 0.45f)
        if (won(d)) { c.line(16f, 14f, 16f, 8f, 0.4f); for (i in 0..2) c.put(17 + i, 8 + ((d.t / 200 + i) % 2).toInt(), 0.9f) }
        val a = since(d); val n = keyN(d)
        if (n >= 0 && a < 350) { val (x, y) = arc(7f, 17f, 26f, -22f, 70f, a); c.put(x, y, 0.9f); c.put(x + 1, y, 0.6f) }
        c.pet(d, 5f, 21f, 7f, if (a < 150) Look.FOCUS else d.mood())
    }
}

/** Whale, the fountain: each key it spouts, higher the longer you type, the spray falling back; after 40 s a rainbow arches through the spray. */
internal class FountainToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val t = d.t / 1000f
        for (x in 0..24) { val s = 18f + sin(x * 0.6f + t * 2) * 0.5f; c.put(x, s.roundToInt(), 0.45f); for (y in s.roundToInt() + 1..24) c.put(x, y, 0.08f) }
        if (won(d)) for (k in 0..2) c.ring(12f, 18f, 8f + k, 0.2f + 0.1f * k, 0.5f, PIF, 2 * PIF)
        val g = gk(d); val top = 5f + 8f * (1 - g)
        d.recent(900) { k, a -> val p = a / 900f; val up = sin(min(1f, p * 1.6f) * PIF / 2); val hgt = (16 - top) * up
            for (j in 0..hgt.roundToInt()) c.put(12f, 15f - j, if (j == hgt.roundToInt()) 1f else 0.6f)
            if (p > 0.5f) for (s in listOf(-1, 1)) for (i in 1..3) c.put(12f + s * i * (p * 3f), 15f - hgt + (p - 0.5f) * 14f * i / 3f, 0.7f * (1 - p)) }
        c.pet(d, 12f, 19f, 8f)
    }
}

/** Jellyfish, the light show: each key a pulse of light runs down its tentacles and a ring of light spreads out; the deep fills with drifting glow as you type, and after 40 s it throbs to every key in full colour. */
internal class GlowToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        for (i in 0 until (g * 20).toInt()) { val y = ((h(i, 1) * 25 - d.t / 300f * (0.5f + h(i, 2))) % 25 + 25) % 25; c.put(h(i, 3) * 24, y, 0.2f) }
        d.recent(800) { k, a -> val p = a / 800f; c.ring(12f, 8f, 2f + p * 12f, (if (won(d)) 1f else 0.7f) * (1 - p), 0.5f) }
        c.pet(d, 12f, 12f, 8f, if (d.hit(200) > 0f) Look.WOW else d.mood())
        val a = since(d)
        for (i in 0 until 5) { val x = 8 + i * 2; for (j in 0..8) { val lit = a < 500 && abs(j - a / 50) < 1.5f; c.put(x + sin(d.t / 300f + j * 0.6f + i) * 0.6f, 13f + j, if (lit) 1f else 0.25f) } }
    }
}

/** Axolotl, the bubble ceiling: each key it puffs a bubble that floats up and sticks under the surface; the bubbles line up as you type, and after 40 s they burst all along in a cascade. */
internal class BubbleCeilingToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) c.put(x, 4, 0.4f)
        val n = keyN(d); val stuck = min(n, 10).coerceAtLeast(0)
        for (i in 0 until stuck) { val x = 3f + i * 2f; val burst = won(d) && ((d.typed - 40_000) / 100 % 12).toInt() == i
            if (burst) c.ring(x, 6f, 1.5f, 0.9f) else c.ring(x, 6f, 0.8f, 0.6f, 0.6f) }
        val a = since(d)
        if (n >= 0 && a < 600) { val p = a / 600f; val x = 3f + min(n, 10) * 2f; c.ring(mix(12f, x, p), mix(15f, 6f, p), 0.9f, 0.9f, 0.6f) }
        c.pet(d, 12f, 21f, 8f, if (a < 150) Look.WOW else d.mood())
        c.ground(22, 0.2f)
    }
}

/** Slime, mitosis: each key a drop of slime falls in and it swells; big enough, it splits in two with a wobble, and its family grows as you type; after 40 s they all bounce together. */
internal class SlimeSplitToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(21, 0.2f)
        val n = keyN(d).coerceAtLeast(0)
        val kids = min(1 + n / 12, 4)
        val swell = (n % 12) / 12f
        for (i in 0 until kids) { val x = 12f + (i - (kids - 1) / 2f) * (16f / kids.coerceAtLeast(1)) * (if (kids > 1) 1f else 0f)
            val bounce = if (won(d)) abs(sin(d.t / 250f + i)) * 3f else 0f
            val main = i == kids - 1
            val hgt = if (main) 5f + swell * 4f else 5f
            c.pet(d, x, 21f - bounce, hgt + d.hit(150) * (if (main) 1f else 0f)) }
        val a = since(d)
        if (keyN(d) >= 0 && a < 250) { val x = 12f + ((kids - 1) - (kids - 1) / 2f) * (16f / kids) * (if (kids > 1) 1f else 0f); c.disc(x, mix(0f, 12f, a / 250f), 0.9f, 0.9f) }
    }
}

/** Frog, the fly catcher: flies buzz round; each key its tongue lashes out and snaps one. After 40 s the lily pads light up and it sings. */
internal class FlyToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) for (y in 20..24) c.put(x, y, 0.1f)
        for (p in listOf(4, 12, 20)) { c.shape(listOf(".#####.", "#######"), p, 20, if (won(d)) 0.6f + 0.3f * sin(d.t / 200f + p) else 0.3f, 0.45f) }
        val n = keyN(d)
        for (i in 0 until 3) { val id = n + 1 + i; val fx = 12f + sin(d.t / (300f + i * 70) + id) * 8f; val fy = 6f + cos(d.t / (230f + i * 50) + id) * 3f; c.put(fx, fy, 0.9f); c.put(fx - 1, fy - 1, 0.35f); c.put(fx + 1, fy - 1, 0.35f) }
        val a = since(d)
        c.pet(d, 12f, 19f, 7f, if (won(d)) Look.HAPPY else d.mood())
        if (n >= 0 && a < 260) { val fx = 12f + sin(d.last!!.at / 300f + n) * 8f; val fy = 6f + cos(d.last!!.at / 230f + n) * 3f
            val p = if (a < 130) a / 130f else 1 - (a - 130) / 130f; c.line(12f, 14f, mix(12f, fx, p), mix(14f, fy, p), 0.9f) }
    }
}

/** Ghost, peekaboo: a graveyard of stones. Each key it pops up from behind a different one (boo!), eyes wide; more stones lean as you type, and after 40 s it glows over the whole yard. */
internal class BooToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(21, 0.2f)
        val stones = listOf(4, 12, 20)
        val n = keyN(d); val a = since(d)
        val at = if (n < 0) -1 else stones[n % 3]
        val up = if (a < 120) ease(a / 120f) else if (a < 600) 1f else 1f - ease((a - 600) / 200f)
        if (at >= 0 && up > 0f) c.pet(d, at.toFloat(), 21f - up * 7f, 8f, Look.WOW, gain = if (won(d)) 1.4f else 1f)
        for ((i, x) in stones.withIndex()) c.shape(listOf(".###.", "#####", "#####", "#####", "#####"), x, 19, 0.3f, 0.5f + (if (won(d)) 0.2f * sin(d.t / 200f + i) else 0f))
    }
}

/** UFO, the abduction: cows graze below. Each key the beam snaps on and lifts one up into the saucer; the herd thins as you type, and after 40 s the mothership looms over everything. */
internal class AbductToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(22, 0.2f)
        if (won(d)) { val p = ease((d.typed - 40_000) / 2000f); for (x in 0..24) for (y in 0..(3 * p).roundToInt()) c.put(x, y, 0.3f); for (x in 2..22 step 4) c.put(x, (3 * p).roundToInt(), 1f) }
        val n = keyN(d); val a = since(d)
        val cx = if (n < 0) 12f else 5f + (n * 5 % 15)
        c.pet(d, cx, 9f, 5f)
        val beam = n >= 0 && a < 600
        if (beam) for (y in 10..21) { val w = (y - 10) * 0.35f; for (x in (cx - w).roundToInt()..(cx + w).roundToInt()) c.put(x, y, 0.15f) }
        for (i in 0 until 4) { val x = 3f + i * 6f; val lifted = beam && abs(x - cx) < 3f
            val y = if (lifted) mix(20f, 11f, a / 600f) else 20f
            if (lifted || i >= min(n.coerceAtLeast(0) / 10, 3)) c.shape(listOf("#.##", "####", "#..#"), x.roundToInt(), y.roundToInt(), 0.55f, 0.55f) }
    }
}

/** Robot, the conveyor: boxes ride a belt under its press. Each key the press slams down and stamps a box; the stamped boxes stack up as you type, and after 40 s a rocket rises off the stack. */
internal class ConveyorToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) { c.put(x, 18, 0.35f); c.put(x, 19, if ((x + d.t / 100) % 3 == 0L) 0.5f else 0.2f) }
        val a = since(d); val n = keyN(d)
        val press = if (a < 80) mix(6f, 12f, a / 80f) else if (a < 300) 12f - (a - 80) / 220f * 6f else 6f
        for (y in 0..press.roundToInt()) c.put(12, y, 0.3f)
        c.shape(listOf("#####", "#####"), 12, press.roundToInt() + 1, 0.5f, 0.7f)
        for (i in 0 until 4) { val x = ((d.t / 70 + i * 7) % 28) - 2; c.shape(listOf("###", "###", "###"), x.toInt(), 16, 0.3f, 0.45f); if (abs(x - 12) < 2 && a < 300) c.set(x.toInt(), 16, 1f) }
        val stack = min(n.coerceAtLeast(0) / 4, 5)
        for (j in 0 until stack) c.shape(listOf("###", "###"), 21, 21 - j * 2, 0.35f, 0.5f)
        if (won(d)) { val p = ((d.typed - 40_000) % 3000) / 3000f; val y = 12f - p * 14f; c.shape(listOf(".#.", "###", "###", "#.#"), 21, y.roundToInt(), 0.6f, 0.8f); c.put(21f, y + 3, 1f) }
        c.pet(d, 4f, 18f, 7f, if (a < 150) Look.FOCUS else d.mood())
    }
}

/** Dino, the volcano: each key the ground shakes and it stomps; the volcano behind rumbles hotter as you type, and after 40 s it erupts. */
internal class VolcanoToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d); val shake = if (since(d) < 120) (if (d.t % 80 < 40) 1 else -1) else 0
        for (j in 0..8) for (x in 12 - (j + 2)..12 + (j + 2)) c.put(x + 4 + shake, 12 + j, 0.2f)
        for (x in 14..18) c.put(x + shake, 12, 0.3f + 0.6f * g)
        if (won(d)) d.recent(1500) { k, a -> for (i in 0..3) { val (x, y) = arc(16f, 11f, (h(k.n, i) - 0.5f) * 18f, -22f - h(k.n, i + 4) * 8f, 40f, a); c.put(x, y, 1f) } }
        else if (g > 0.5f) for (i in 0..2) { val q = ((d.t / 600f + i / 3f) % 1f); c.put(16f + sin(q * 6 + i) , 11f - q * 7, 0.3f * (1 - q)) }
        c.ground(21, 0.3f)
        c.pet(d, 6f + shake, 21f - (if (since(d) < 120) 1 else 0), 8f, if (since(d) < 150) Look.WOW else d.mood())
    }
}

/** Bee, the honeycomb: each key a cell is sealed with honey, the comb filling cell by cell; after 40 s honey drips from it, glowing. */
internal class CombToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = keyN(d).coerceAtLeast(-1)
        var i = 0
        for (row in 0 until 4) for (col in 0 until 5) {
            val cx = 4f + col * 4f + (if (row % 2 == 1) 2f else 0f); val cy = 4f + row * 3.5f
            val full = i <= n / 2; val fresh = i == n / 2 && since(d) < 200
            c.ring(cx, cy, 1.8f, 0.3f, 0.5f); if (full) c.disc(cx, cy, 1.1f, if (fresh) 1f else 0.6f)
            i++
        }
        if (won(d)) for (k in 0..3) { val q = ((d.t / 900f + k / 4f) % 1f); c.put(6f + k * 4, 16f + q * 7, 0.9f * (1 - q)) }
        val a = since(d); val x = 12f + sin(d.t / 400f) * 6f
        c.pet(d, x, 22f, 6f)
    }
}

/** Bat, echolocation: a dark cave. Each key it squeaks and a ring of sound spreads out, lighting the walls and the crystals it touches for a moment; more of the cave stays lit as you type, and after 40 s the crystals all glow. */
internal class EchoToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        fun wall(x: Int, y: Int) = y < 3 + 2 * sin(x * 0.7f) + h(x, 1) * 2 || y > 20 - 2 * sin(x * 0.5f) - h(x, 2) * 2
        var r = -1f; d.last?.let { if (d.sinceKey < 800) r = d.sinceKey / 800f * 16f }
        for (y in 0..24) for (x in 0..24) if (wall(x, y)) { val dist = hypot(x - 12f, y - 10f); val lit = r > 0 && abs(dist - r) < 1.5f
            c.put(x, y, if (lit) 0.8f else if (h(x, y + 50) < g) 0.22f else 0.04f) }
        for (k in 0 until 6) { val x = 3 + k * 4; val y = if (k % 2 == 0) 19 - (h(k, 3) * 2).toInt() else 4 + (h(k, 4) * 2).toInt(); val lit = won(d) || (r > 0 && abs(hypot(x - 12f, y - 10f) - r) < 2f)
            c.put(x, y, if (lit) 1f else 0.1f); c.put(x, y + (if (k % 2 == 0) -1 else 1), if (lit) 0.7f else 0.06f) }
        if (r > 0) c.ring(12f, 10f, r, 0.3f * (1 - r / 16f), 0.5f)
        c.pet(d, 12f, 13f, 6f)
    }
}

/** Chameleon, the disguise: the background changes its pattern on each key (stripes, dots, checks) and the chameleon's skin flips to match; it blends in more as you type, and after 40 s only its eyes are left. */
internal class DisguiseToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = keyN(d).coerceAtLeast(0); val g = gk(d)
        fun pat(x: Int, y: Int) = when (n % 3) { 0 -> if ((x + y) % 4 < 2) 0.35f else 0.12f; 1 -> if (x % 3 == 0 && y % 3 == 0) 0.45f else 0.1f; else -> if ((x / 2 + y / 2) % 2 == 0) 0.35f else 0.12f }
        val flash = since(d) < 120
        val dimBg = 0.12f + 0.88f * g
        for (y in 0..24) for (x in 0..24) c.put(x, y, (if (flash) pat(x, y) * 1.6f else pat(x, y)) * dimBg)
        val tmp = Cv(); tmp.pet(d, 12f, 18f, 9f)
        for (y in 0..24) for (x in 0..24) { val v = tmp.get(x, y); if (v > 0f) c.set(x, y, mix(maxOf(v, 0.5f), pat(x, y) * dimBg, if (won(d)) 1f else g * g * 0.85f)) else if (v == 0f && tmp.get(x, y) == 0f) Unit }
        if (won(d)) { c.set(14, 13, 1f); c.set(16, 13, 1f) }
    }
}

/** Eye, hypnosis: a spiral turning round its pupil. Each key the pupil throbs and the spiral jolts a turn; it spins faster and wider as you type, and after 40 s the whole matrix is a hypnotic tunnel. */
internal class HypnoToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d); val turn = d.t / 1000f * (1f + 3f * g) + d.hit(300) * 1.5f
        val reach = if (won(d)) 13f else 5f + 7f * g
        for (y in 0..24) for (x in 0..24) { val r = hypot(x - 12f, y - 12f); if (r < 3f || r > reach) continue
            val a = atan2(y - 12f, x - 12f); val s = sin(a * 2 + r * 0.9f - turn * 3); if (s > 0.3f) c.put(x, y, 0.25f + 0.4f * s * (1 - r / 14f)) }
        val pr = 2.2f + d.hit(200) * 1.2f
        c.disc(12f, 12f, pr + 0.8f, 0.6f); c.disc(12f, 12f, pr, 0f); for (y in 0..24) for (x in 0..24) if (hypot(x - 12f, y - 12f) <= pr) c.cut(x, y)
        c.put(11f, 11f, 1f)
    }
}

/** Gem, the cutting: each key a facet is cut on the stone with a flash; it gains facets and fire as you type, and after 40 s it throws rainbow rays round the matrix. */
internal class FacetToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = keyN(d).coerceAtLeast(0); val facets = min(3 + n / 6, 12)
        if (won(d)) for (k in 0 until 8) { val a = k * 0.785f + d.t / 1500f; c.line(12 + cos(a) * 7, 12 + sin(a) * 7, 12 + cos(a) * 12, 12 + sin(a) * 12, if ((d.t / 150 + k) % 2 == 0L) 0.8f else 0.35f) }
        val r = 6f
        for (i in 0 until facets) { val a0 = i * 6.283f / facets - PIF / 2; val a1 = (i + 1) * 6.283f / facets - PIF / 2
            c.line(12 + cos(a0) * r, 12 + sin(a0) * r, 12 + cos(a1) * r, 12 + sin(a1) * r, 0.6f); c.line(12f, 12f, 12 + cos(a0) * r, 12 + sin(a0) * r, 0.25f) }
        val a = since(d); if (a < 200) { val i = n % facets; val am = (i + 0.5f) * 6.283f / facets - PIF / 2; c.line(12f, 12f, 12 + cos(am) * r, 12 + sin(am) * r, 1f); c.put(12 + cos(am) * (r + 2), 12 + sin(am) * (r + 2), 1f) }
        c.put(10f, 9f, 1f)
    }
}

/** Planet, the moons: each key a new moon is flung into orbit round it; the moons pile up on their tracks as you type, and after 40 s they spread into a great ring. */
internal class MoonsToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val n = keyN(d).coerceAtLeast(-1)
        if (won(d)) for (x in 1..23) { val y = 12 + (x - 12) * 0.25f; c.put(x.toFloat(), y, if (abs(x - 12) > 7) 0.5f else 0f) }
        c.pet(d, 12f, 17f, 10f)
        for (i in 0..min(n, 7)) { val orbit = 7f + (i % 4) * 1.4f; val speed = 1.5f - (i % 4) * 0.25f; val a = d.t / 1000f * speed + h(i, 9) * 6.28f
            val launch = if (i == n) ease(since(d) / 300f) else 1f
            val x = 12 + cos(a) * orbit * launch; val y = 12 + sin(a) * orbit * 0.45f * launch
            if (sin(a) < 0 || hypot(x - 12, y - 12) > 4.5f) c.disc(x, y, 0.8f, if (i == n && since(d) < 300) 1f else 0.7f) }
    }
}

/** Raccoon, the bin: each key it pulls something out of the bin and tosses it over its shoulder (a can, a bone, a peel); the heap grows as you type, and after 40 s it pulls out treasure, gleaming. */
internal class BinToy : Desk() {
    private companion object { val JUNK = listOf(listOf("##", "##", "##"), listOf("#..#", "####", "#..#"), listOf(".#", "##", "#.")) }
    override fun draw(c: Cv, d: DeskInput) {
        c.ground(22, 0.2f)
        c.shape(listOf("#########", ".#######.", ".#######.", ".#######.", ".#######.", "..#####.."), 15, 18, 0.25f, 0.5f)
        val lid = if (since(d) < 200) 1 else 0; for (x in 10..20) c.put(x, 14 - lid * 2, 0.5f)
        val n = keyN(d); val heap = min(n.coerceAtLeast(0), 12)
        for (i in 0 until heap) c.put(2f + (i % 6), 21f - i / 6, 0.4f + 0.2f * (i % 2))
        val a = since(d)
        if (n >= 0 && a < 450) { val (x, y) = arc(15f, 12f, -22f, -18f, 60f, a); if (won(d)) { c.disc(x, y, 1.5f, 1f); c.ring(x, y, 3f, 0.5f) } else c.shape(JUNK[n % 3], x.roundToInt(), y.roundToInt(), 0.7f, 0.7f) }
        c.pet(d, 15f, 12f, 7f, if (won(d)) Look.HAPPY else d.mood())
    }
}

/** Cloud, the weather: each key a raindrop falls from it; drops turn to a downpour as you type, with lightning at 20 s, and after 40 s the sun breaks through and a rainbow arches under it. */
internal class WeatherToy : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val g = gk(d)
        if (won(d)) { for (k in 0..2) c.ring(12f, 24f, 9f + k, 0.25f + 0.15f * k, 0.5f, PIF, 2 * PIF); c.disc(20f, 4f, 2f, 0.8f) }
        d.recent(700) { k, a -> val x = 7f + h(k.n, 1) * 10f; val y = 10f + a / 700f * 14f; c.put(x, y, 0.9f); c.put(x, y - 1, 0.4f) }
        if (!won(d)) for (i in 0 until (g * 12).toInt()) { val x = 6f + h(i, 3) * 12f; val y = 10f + ((d.t / 400f + h(i, 4)) % 1f) * 14f; c.put(x, y, 0.3f) }
        if (d.typed in 20_000..40_000 && since(d) < 100 && keyN(d) % 5 == 0) { c.line(13f, 10f, 11f, 15f, 1f); c.line(11f, 15f, 14f, 15f, 1f); c.line(14f, 15f, 12f, 21f, 1f) }
        c.pet(d, 12f, 11f, 8f, if (won(d)) Look.HAPPY else d.mood())
    }
}
