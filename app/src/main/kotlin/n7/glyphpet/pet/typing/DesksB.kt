package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The typing scenes of: hedgehog, crab, whale, jelly, axolotl, slime, frog, ghost, ufo, bot.

/**
 * Hedgehog: a quill stands up with each key; in a rush it curls into a ball and rolls to and fro. Then apples and
 * leaves ride on its quills, bouncing with the keys; later it sleeps under the leaves that fall with them.
 */
internal class HedgehogDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        c.floor(21, DIM)
        if (d.stage < 2 && d.heat > 0.4f) {
            // Curled up and rolling.
            val x = 12f + 6f * sin(d.s * 3f)
            val r = 3.6f
            c.disc(x, 20f - r, r, 0.3f)
            for (i in 0..9) { val a = i * TAU / 10 + (x - 12f) / r; c.put(x + cos(a) * (r + 1f), 20f - r + sin(a) * (r + 1f), 0.55f) }
            return
        }
        val sleep = d.stage == 2 && !d.paused && !d.sending
        val cy = p.onFeet(20f, sc, if (sleep) sc * 0.8f else sc) - d.jump(2.5f)
        p.draw(c, 12f, cy, sc, if (sleep) Look.SLEEP else d.mood(), sy = if (sleep) sc * 0.8f else sc)
        val top = cy - (p.h - 1) * sc / 2f
        val hw = p.w * sc / 2f
        // Quills along its back, one up for each key (all of them at a salute).
        d.recent(420) { k, a ->
            val ang = PIF + 0.25f + h(k.n) * (PIF - 0.5f)
            val q = 1f - a / 420f
            val bx = 12f + cos(ang) * hw * 0.8f; val by = top + 2f + sin(ang) * 2f
            c.line(bx, by, bx + cos(ang) * 2.5f * q, by + sin(ang) * 2.5f * q, 0.9f * q)
        }
        if (d.sending) for (i in 0..8) { val ang = PIF + 0.2f + i * (PIF - 0.4f) / 8; c.put(12f + cos(ang) * (hw + 1.5f), top + 2f + sin(ang) * 3f, 1f - d.sentP) }
        when (d.stage) {
            1 -> for (i in 0..2) {
                val bounce = if (d.n % 3 == i) d.hit(240) else 0f
                val ax = 12f + (i - 1) * hw * 0.6f
                if (i == 1) c.disc(ax, top - 1.5f - bounce, 1.1f, 0.55f) else c.art(listOf(".#", "#."), ax.toInt(), (top - 2f - bounce).toInt(), 0.45f)
            }
            2 -> {
                for (i in 0..5) c.art(listOf("#+"), (12f + (i - 2.5f) * hw * 0.35f).toInt(), (top - 0.5f + (i % 2)).toInt(), 0.32f)
                d.recent(1400) { k, a ->
                    val q = a / 1400f
                    val lx = 12f + (h(k.n) - 0.5f) * 14f + sin(q * 9f) * 1.5f
                    c.put(lx, 2f + q * (top - 3f), 0.6f); c.put(lx + 1f, 2f + q * (top - 3f), 0.3f)
                }
            }
        }
    }
}

/**
 * Crab: on the sand, a snap of one claw then the other with each key; in a rush it scuttles from edge to edge. Then it
 * digs into the sand; later only its eyes stick out, a puff of sand with each key, and in a pause it pops up.
 */
internal class CrabDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        val sand = 19
        c.under(sand + 1, 0.09f)
        for (x in 0..24) c.put(x, sand, 0.16f + if (h(x, 3) > 0.7f) 0.08f else 0f)
        when (d.stage) {
            0 -> {
                val x = if (d.heat > 0.05f) mix(12f, 12f + 7f * sin(d.s * 5f), d.heat) else 12f
                val cy = p.onFeet(sand.toFloat(), sc) - d.jump(3f)
                p.draw(c, x, cy, sc, d.mood())
                val hw = p.w * sc / 2f
                if (d.hit(170) > 0f) {
                    val side = if (d.n % 2 == 0) -1 else 1
                    c.twinkle(x + side * (hw + 1.5f), cy - p.h * sc / 2f, d.hit(170))
                }
                if (d.sending) for (side in listOf(-1, 1)) c.twinkle(x + side * (hw + 1.5f), cy - p.h * sc / 2f - 3f * sin(d.sentP * PIF), 1f)
                d.recent(1600) { k, a -> if (k.n % 4 == 0) { val q = a / 1600f; c.ring(x + 2f, cy - 3f - q * 8f, 0.6f + q * 0.5f, 0.4f * (1f - q), 0.4f) } }
            }
            else -> {
                val deep = if (d.stage == 1) 0.5f else 0.82f
                val out = d.rest
                val feet = sand + p.h * sc * mix(deep, 0f, out) + 1f
                p.draw(c, 12f, p.onFeet(feet, sc) - d.jump(2f), sc, if (out > 0.5f) Look.HAPPY else d.mood(), below = sand - 0.5f)
                d.recent(500) { k, a ->
                    val q = a / 500f
                    val px = 4f + h(k.n) * 16f
                    for (j in -1..1) c.put(px + j * (1f + q * 2f), sand - 1f - q * 2f + abs(j) * q, 0.5f * (1f - q))
                }
            }
        }
    }
}

/**
 * Whale: under the sea, a bubble rising to the surface with each key; in a rush it blows its spout up through it. Then
 * it dives deeper and dims under bigger waves; later only its glow is left in the deep, a sonar ring with each key.
 */
internal class WhaleDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        val surf = 6f
        c.wave(surf, d.s, 0.35f, amp = if (d.stage >= 1) 1.1f else 0.6f)
        val depth = when (d.stage) { 0 -> 0f; 1 -> 3f * d.stageIn(); else -> 5f }
        val gain = when (d.stage) { 0 -> 1f; 1 -> mix(1f, 0.55f, d.stageIn()); else -> 0.3f }
        val cx = 12f
        val cy = 15f + depth - d.jump(3f)
        val top = cy - (p.h - 1) * sc / 2f
        if (d.stage == 2) {
            d.recent(1200) { _, a -> c.ring(cx, cy, 1f + a / 1200f * 11f, 0.45f * (1f - a / 1200f)) }
            for (i in 0..11) { val a = d.s * 0.4f + i * 0.9f; c.put(12f + cos(a + i) * (4f + i % 5 * 1.6f), 14f + sin(a * 1.3f + i) * 6f, 0.1f + 0.1f * d.hit(500)) }
        }
        p.draw(c, cx, cy, sc, d.mood(), gain = gain)
        d.recent(1500) { k, a ->
            val q = a / 1500f
            val bx = cx + (h(k.n) - 0.5f) * 4f + sin(q * 8f) * 0.8f
            val by = top - 1f - q * (top - surf - 1f)
            if (by > surf + 0.5f) c.put(bx, by, 0.6f) else c.put(bx, surf - 1f, 0.8f * (1f - q))
        }
        if (d.heat > 0.05f && d.stage < 2) for (i in 0..8) {
            val q = ((d.t / 500f + i / 9f) % 1f)
            val y = top - 1f - q * (top - 1f)
            c.put(cx + (if (y < surf) (i % 3 - 1) * q * 4f else 0f), y, 0.8f * d.heat * (1f - q * 0.5f))
        }
        if (d.sending) for (i in -4..4) c.put(cx + i, surf - 1f - 3f * sin(d.sentP * PIF) * (1f - abs(i) / 5f), 0.8f)
    }
}

/**
 * Jelly: a tentacle lights up with each key; in a rush it pulses and rises in beats. Then plankton glows around it in
 * waves as it fades; later it is all but gone into the plankton, which swirls and flashes with the keys.
 */
internal class JellyDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        val pulse = d.heat * sin(d.s * 9f)
        val cy = 11f - d.heat * 2f + 0.8f * sin(d.s * 1.3f) - d.jump(3f)
        val gain = when (d.stage) { 0 -> 1f; 1 -> 0.7f; else -> 0.2f + 0.2f * d.hit(300) + 0.4f * d.rest }
        if (d.stage >= 1) for (i in 0..(if (d.stage == 2) 26 else 14)) {
            val a = d.s * (0.2f + h(i) * 0.3f) + h(i, 1) * TAU
            val r = if (d.stage == 2) 3f + h(i, 2) * 8f else 6f + h(i, 2) * 6f
            val x = 12f + cos(a) * r; val y = 12f + sin(a) * r * 0.8f
            val wave = 0.5f + 0.5f * sin(d.s * 2f - r * 0.6f)
            c.put(x, y, 0.1f + 0.25f * wave + (if (d.n >= 0 && i % 5 == d.n % 5) 0.5f * d.hit(300) else 0f))
        }
        p.draw(c, 12f, cy, sc * (1f + 0.08f * pulse), d.mood(), sy = sc * (1f - 0.1f * pulse), gain = gain)
        val bottom = cy + (p.h - 1) * sc / 2f
        val hw = p.w * sc / 2f
        d.recent(380) { k, a ->
            val tx = 12f + (h(k.n) - 0.5f) * hw * 1.6f
            val q = 1f - a / 380f
            for (y in 0..4) c.put(tx + sin(y * 0.9f + d.s * 5f) * 0.6f, bottom + 1f + y, q * (1f - y / 6f))
        }
        if (d.sending) for (i in 0..624) if (c.v[i] > 0.05f) c.v[i] = minOf(1f, c.v[i] + 0.6f * (1f - d.sentP))
    }
}

/**
 * Axolotl: its gills light up branch by branch with the keys; in a rush it swims round in a circle. Then it shines as a
 * bare outline; later a light runs round the outline and each key fills it in for a moment.
 */
internal class AxolotlDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        val ang = d.s * 2.2f
        val cx = 12f + d.heat * 5f * cos(ang)
        val cy = 13f + d.heat * 4f * sin(ang) - d.jump(3f)
        val top = cy - (p.h - 1) * sc / 2f
        val hw = p.w * sc / 2f
        when (d.stage) {
            0 -> p.draw(c, cx, cy, sc, d.mood(), flipX = d.heat > 0.3f && sin(ang) < 0f)
            1 -> p.draw(c, cx, cy, sc, d.mood(), outline = true, gain = 1.6f)
            else -> {
                p.draw(c, cx, cy, sc, d.mood(), outline = true, gain = 1.2f)
                if (d.hit(260) > 0f) p.draw(c, cx, cy, sc, d.mood(), gain = d.hit(260))
                val run = (d.s * 1.5f) % 1f * TAU
                c.twinkle(cx + cos(run) * hw, cy + sin(run) * (p.h * sc / 2f), 0.9f)
            }
        }
        // Gills: three branches each side of the head, one lit per key.
        for (side in listOf(-1, 1)) for (b in 0..2) {
            val gx = cx + side * (hw + 0.5f); val gy = top + 1f + b * 1.2f
            val lit = d.n >= 0 && (d.n % 6) == (b + if (side > 0) 3 else 0)
            val v = if (d.sending) 1f else if (lit) 0.3f + 0.7f * d.hit(300) else 0.25f
            c.put(gx, gy, v); c.put(gx + side, gy - 0.6f, v * 0.6f)
        }
        d.recent(1600) { k, a -> if (k.n % 3 == 0) { val q = a / 1600f; c.soft(cx + (h(k.n) - 0.5f) * 3f, top - 1f - q * 9f, 0.5f * (1f - q)) } }
    }
}

/**
 * Slime: bounces with each key, a drop flying off each side; in a rush it melts into a bubbling puddle. Then it splits
 * in two that hop in turn; later four little slimes hop one after another, a wave along them with the keys.
 */
internal class SlimeDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        c.floor(21, DIM)
        when (d.stage) {
            0 -> {
                val sc = p.fit(9f)
                val melt = d.heat
                val q = d.hit(300)
                val squash = if (q > 0.6f) (q - 0.6f) * 2.5f else 0f
                val stretch = if (q in 0.01f..0.6f) sin(q / 0.6f * PIF) else 0f
                val sx = sc * (1f + 0.22f * squash - 0.08f * stretch + 0.5f * melt)
                val sy = sc * (1f - 0.25f * squash + 0.12f * stretch - 0.5f * melt)
                val lift = stretch * 2f + d.jump(5f)
                p.draw(c, 12f, p.onFeet(20f, sc, sy) - lift, sx, d.mood(), sy = sy)
                if (melt > 0.3f) for (i in 0..3) { val bq = ((d.t / 600f + h(i)) % 1f); c.ring(8f + i * 2.7f, 18f - bq * 3f, 0.6f * bq, 0.6f * (1f - bq), 0.4f) }
                d.recent(600) { k, a -> for (s in listOf(-1, 1)) { val (x, y) = arc(12f + s * 3f, 17f, s * (5f + h(k.n, s) * 4f), -8f, 30f, a); if (y < 21f) c.put(x, y, 0.6f * (1f - a / 600f)) } }
            }
            else -> {
                val count = if (d.stage == 1) 2 else 4
                val sc = p.fit(if (count == 2) 6f else 4.5f)
                for (i in 0 until count) {
                    val x = 12f + (i - (count - 1) / 2f) * (if (count == 2) 8f else 5f)
                    val mine = d.n >= 0 && d.n % count == i
                    // In a wave along them: each hops a little after the one before.
                    val wave = if (count == 4) { var w = 0f; d.recent(500) { _, a -> w = maxOf(w, sin(((a - i * 90) / 300f).coerceIn(0f, 1f) * PIF)) }; w } else 0f
                    val lift = (if (mine) sin(d.hit(300) * PIF) * 2.5f else 0f) + wave * 2f + d.jump(3f)
                    p.draw(c, x, p.onFeet(20f, sc) - lift, sc, d.mood())
                }
            }
        }
    }
}

/**
 * Frog: on a lily pad, its tongue snaps out at a fly with each key; in a rush the flies swarm. Then night falls and the
 * flies are fireflies under a moon; later it sleeps on its pad and each key rings the water.
 */
internal class FrogDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        c.wave(21f, d.s * 0.5f, 0.14f, amp = 0.3f, len = 3f)
        for (x in 6..18) c.set(x, 20, 0.3f)
        if (d.stage >= 1) { c.disc(19f, 5f, 2f, 0.5f); c.hole(20f, 4.3f, 1.6f) }
        val sleep = d.stage == 2 && !d.paused && !d.sending
        val fx = 12f
        val cy = p.onFeet(19.5f, sc) - d.jump(4f)
        val mouthY = cy + 1f
        // The flies (fireflies at night), one more of them in a rush.
        val flies = 3 + (d.heat * 4).toInt()
        for (i in 0 until flies) {
            val x = 12f + 8f * sin(d.s * (0.7f + i * 0.23f) + i * 2f)
            val y = 8f + 3f * sin(d.s * (1.1f + i * 0.31f) + i)
            val caught = d.n >= 0 && d.n % flies == i && d.sinceKey < 400
            if (caught) continue
            val b = if (d.stage >= 1) (if (sin(d.s * 4f + i * 1.7f) > 0.2f) 0.8f else 0.1f) else 0.55f
            if (!sleep) c.put(x, y, b)
        }
        p.draw(c, fx, cy - if (d.rest > 0.3f) 0.5f * sin(d.s * 8f) else 0f, sc, if (sleep) Look.SLEEP else d.mood())
        if (!sleep && d.n >= 0 && d.sinceKey < 180) {
            val i = d.n % flies
            val tx = 12f + 8f * sin(d.s * (0.7f + i * 0.23f) + i * 2f)
            val ty = 8f + 3f * sin(d.s * (1.1f + i * 0.31f) + i)
            val q = sin(d.sinceKey / 180f * PIF)
            c.line(fx, mouthY, mix(fx, tx, q), mix(mouthY, ty, q), 0.7f)
        }
        if (d.stage == 2) d.recent(1500) { k, a -> val q = a / 1500f; val rx = 4f + h(k.n) * 16f; for (dx in -3..3) if (abs(dx) >= (q * 3f).toInt()) c.put(rx + dx * q * 1.4f, 22f, 0.4f * (1f - q)) }
    }
}

/**
 * Ghost: fades as you type, and where it was a letter shows with each key; in a rush it drifts out through one edge and
 * in at the other. Then it is gone but for a flicker of its outline, the letters floating up out of it; later only its
 * eyes are left in the dark, glancing with the keys.
 */
internal class GhostDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        val drift = if (d.heat > 0.05f) ((d.t * 0.01f * d.heat) % 30f) else 0f
        val cx = (12f + drift + 3f).let { if (it > 27f) it - 30f else it }
        val cy = 12f + sin(d.s * 1.4f) - d.jump(3f)
        val boo = if (d.sending) 1f + 0.35f * sin(d.sentP * PIF) else 1f
        val gain = when (d.stage) {
            0 -> mix(0.55f, 1f, d.rest)
            1 -> mix(0.18f + 0.1f * sin(d.s * 11f), 1f, d.rest)
            else -> d.rest
        }
        if (d.stage == 1) p.draw(c, cx, cy, sc * boo, d.mood(), outline = true, gain = 0.5f + 0.3f * sin(d.s * 9f))
        p.draw(c, cx, cy, sc * boo, d.mood(), gain = gain)
        d.recent(if (d.stage == 0) 700 else 1600) { k, a ->
            val q = a / (if (d.stage == 0) 700f else 1600f)
            val ly = if (d.stage == 0) cy - 1f else cy - 1f - q * 9f
            c.letter((cx - 1f + (h(k.n) - 0.5f) * 4f).toInt(), ly.toInt(), k.n, 0.8f * (1f - q))
        }
        if (d.stage == 2 && d.rest < 0.6f) {
            val look = if (d.hit(400) > 0f) (if (d.n % 2 == 0) -1 else 1) else 0
            val blink = (d.t % 3200) < 140
            for (s in listOf(-1, 1)) if (!blink) { c.put(cx + s * 2f + look, cy - 1f, 0.8f); c.put(cx + s * 2f + look, cy, 0.5f) }
        }
    }
}

/**
 * Ufo: its beam pulls up a letter with each key; in a rush it circles and the beam sweeps. Then it climbs higher and
 * leaves crop marks on the ground with the keys; later it is gone and morse comes down from the sky, a dot with each
 * key and dashes in a rush.
 */
internal class UfoDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        c.floor(21, DIM)
        when (d.stage) {
            0, 1 -> {
                val high = d.stage == 1
                val sc = p.fit(if (high) 5f else 7f)
                val cx = 12f + d.heat * 6f * sin(d.s * 2.5f)
                val cy = (if (high) 6f else 8f) + 0.6f * sin(d.s * 2f) - d.jump(2f)
                val bottom = cy + (p.h - 1) * sc / 2f
                val sweep = d.heat * 4f * sin(d.s * 6f)
                for (y in (bottom + 1).toInt()..20) {
                    val k = (y - bottom) / (21f - bottom)
                    val half = 1f + k * 3f
                    for (dx in -half.toInt()..half.toInt()) c.put(cx + sweep * k + dx, y.toFloat(), 0.09f + if (abs(dx) == half.toInt()) 0.08f else 0f)
                }
                d.recent(1100) { k, a ->
                    val q = a / 1100f
                    c.letter((cx + sweep * (1f - q) - 1f).toInt(), (19f - q * (19f - bottom)).toInt(), k.n, 0.85f * (1f - q * 0.6f))
                }
                if (high) for (i in 0..2) c.ring(12f + (i - 1) * 6f, 21f, 1.5f, 0.12f + 0.4f * (if (d.n % 3 == i) d.hit(400) else 0f), 0.5f)
                p.draw(c, cx, cy, sc, d.mood())
                if (d.sending) c.ring(cx, cy, d.sentP * 9f, 0.7f * (1f - d.sentP))
                for (i in 0..2) if (((d.t / 200) + i) % 3 == 0L) c.put(cx - 2f + i * 2f, cy + 1f, 0.9f)
            }
            else -> {
                d.recent(2200) { k, a ->
                    val q = a / 2200f
                    val col = 5 + (k.n * 3) % 15
                    val dash = d.rush || h(k.n) > 0.75f
                    val y = 1f + q * 20f
                    c.put(col.toFloat(), y, 0.8f * (1f - q * 0.4f))
                    if (dash) { c.put(col.toFloat(), y - 1f, 0.8f); c.put(col.toFloat(), y - 2f, 0.6f) }
                }
                if (d.rest > 0.3f) p.draw(c, 12f, 3f + (1f - d.rest) * -4f, p.fit(4f), Look.HAPPY)
            }
        }
    }
}

/**
 * Bot: types code into a panel above it, a character with each key, the lines scrolling up; a rush pours code down the
 * whole matrix. Then it overheats, steam rising from its head; later a fan whirs over it at the speed of the typing.
 */
internal class BotDesk : Desk() {
    private val smoke = Fire.field()
    private var spin = 0f
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        smoke.fill(0f)
        if (d.heat > 0.05f) for (col in 0..24 step 2) {
            val sp = 6f + h(col) * 8f
            val y = ((d.s * sp + h(col, 1) * 25f) % 30f) - 3f
            for (j in 0..3) c.put(col.toFloat(), y - j, d.heat * (0.6f - j * 0.15f))
        }
        val cx = 12f
        val shake = if (d.stage == 1 && d.hit(120) > 0f) (if (d.n % 2 == 0) -1f else 1f) else 0f
        val cy = p.onFeet(21f, sc) - d.jump(2f)
        val top = cy - (p.h - 1) * sc / 2f
        if (d.stage == 0) {
            // The code panel: three lines, the newest at the bottom, the cursor after it.
            val n = maxOf(0, d.n + 1)
            for (line in 0..2) {
                val ln = n / 9 - line
                if (ln < 0) continue
                val len = if (line == 0) n % 9 else 9
                for (i in 0 until len) if (h(ln, i) > 0.25f) c.put(8f + i, top - 2f - line * 2f, if (line == 0) 0.75f else 0.3f)
            }
            if ((d.t / 400) % 2 == 0L || !d.paused) c.put(8f + n % 9, top - 2f, 0.9f)
            if (d.sending) c.art(listOf("....o", "...o.", "o.o..", ".o..."), 10, (top - 6f).toInt(), 0.9f * (1f - d.sentP * 0.5f))
        }
        if (d.stage == 1) {
            Fire.smoke(smoke, cx, top - 1f, d.t, amount = 0.5f + 0.5f * d.heat)
            Fire.renderSmoke(c, smoke)
        }
        if (d.stage == 2) {
            spin += d.dtMs / 1000f * (1f + d.rate * 1.6f)
            val fy = top - 3.5f
            for (b in 0..2) { val a = spin * 3f + b * TAU / 3; c.line(cx, fy, cx + cos(a) * 3f, fy + sin(a) * 2f, 0.55f) }
            c.put(cx, fy, 0.9f)
        }
        p.draw(c, cx + shake, cy, sc, if (d.stage == 1 && !d.paused) Look.WOW else d.mood(Look.FOCUS))
    }
}
