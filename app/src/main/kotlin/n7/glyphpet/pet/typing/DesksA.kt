package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The typing scenes of the first pets: cat, fox, bunny, chick, owl, penguin, shroom, cactus, toast, snail.

/** How the pet looks for the tempo: wide-eyed at a salute, happy in a pause, busy in a rush. */
internal fun DeskInput.mood(calm: Look = Look.CALM): Look = when {
    sending -> Look.WOW
    paused -> Look.HAPPY
    rush -> Look.FOCUS
    else -> calm
}

/** Rows up for a salute that jumps: a hop [high] rows up and down over the salute. */
internal fun DeskInput.jump(high: Float = 4f): Float = if (sending) high * sin(sentP * PIF) else 0f

/**
 * Cat: two big paws come up from the bottom and slap down, one then the other, with each key, a wave and sparks
 * rolling out from the slap; in a rush they drum. It grows as the typing goes on, a step every few seconds: a keyboard
 * under the paws (8 s), its key lighting where a paw hits; the cat's ears coming down from the top (16 s); its eyes,
 * glancing at the paw that hits (24 s); the whole face (32 s); whiskers that twitch with the keys (42 s); and at last a
 * contented cat (52 s), eyes shut in happy arcs and smiling between the keys. In a pause the paws rise and the cat
 * blinks and smiles; a sent message is both paws down at once and a ring of light.
 */
internal class CatDesk : Desk() {
    private companion object {
        /** The steps, in seconds of typing. */
        const val KEYBOARD = 8f
        const val EARS = 16f
        const val EYES = 24f
        const val FACE = 32f
        const val WHISKERS = 42f
        const val CONTENT = 52f
        val HEAD = listOf("#.........#", "##.......##", "###.....###", "###########", "###########", "###########",
            "###########", "###########", "###########", ".#########.")
    }

    override fun draw(c: Cv, d: DeskInput) {
        val s = d.typed / 1000f
        fun into(at: Float, over: Float = 1.2f) = ((s - at) / over).coerceIn(0f, 1f)
        val k = d.last
        val left = k == null || k.n % 2 == 0
        val hit = d.hit(220)
        val rush = d.heat > 0.5f
        val rest = d.paused
        val strong = 0.5f + 0.5f * (s / 50f).coerceAtMost(1f) + 0.4f * d.heat

        // The cat's head, coming down from the top: ears, then eyes, then the face.
        if (s >= EARS) {
            // The head stands in its place and shows from the top down: the ears, down to the eyes, then all of it.
            val top = 2
            val rows = when {
                s < EYES -> 3
                s < FACE -> 7
                else -> HEAD.size
            }
            val fade = when {
                s < EYES -> into(EARS)
                s < FACE -> into(EYES)
                else -> into(FACE)
            }
            HEAD.forEachIndexed { j, row ->
                if (j >= rows) return@forEachIndexed
                // The newest rows fade in; the edge of what shows is a soft fringe.
                val newRow = j >= when { s < EYES -> 0; s < FACE -> 3; else -> 7 }
                val b = 0.34f * (if (newRow) fade else 1f)
                row.forEachIndexed { i, ch -> if (ch == '#') c.set(7 + i, top + j, b) }
            }
            val content = s >= CONTENT && d.sinceKey > 150
            if (s >= EYES) {
                val look = if (k != null && d.sinceKey < 600) (if (left) -1 else 1) else 0
                val blink = rest && (d.t % 2400) < 150
                for (ex in listOf(9, 14)) when {
                    content -> { c.cut(ex, top + 5); c.cut(ex + 1, top + 4); c.cut(ex + 2, top + 5) }
                    blink -> { c.cut(ex, top + 5); c.cut(ex + 1, top + 5) }
                    else -> {
                        for (dy in 0..1) for (dx in 0..1) c.cut(ex + dx + look, top + 4 + dy)
                        c.set(ex + look, top + 4, 0.9f)
                    }
                }
            }
            if (s >= WHISKERS) {
                val f = into(WHISKERS, 1.5f)
                val twitch = if (hit > 0f) -1 else 0
                for (side in listOf(-1, 1)) for (w in 0..2) for (i in 0..2) {
                    val y = top + 6 + (w - 1) + (if (i == 2) (w - 1) else 0) + (if (i > 0) twitch else 0)
                    c.put(12 + side * (7 + i), y, (0.45f + 0.4f * hit) * f)
                }
            }
            if (s >= FACE) {
                c.cut(12, top + 7); c.set(11, top + 7, 0.6f); c.set(13, top + 7, 0.6f)
                if (rest || content) { c.cut(10, top + 8); c.cut(14, top + 8); c.cut(11, top + 9); c.cut(13, top + 9); c.cut(12, top + 8) }
            }
        }

        // The keyboard under the paws: the key a paw hits lights up.
        val kb = into(KEYBOARD, 1.5f)
        if (kb > 0f) {
            for (x in 2..22) c.put(x, 23, 0.1f * kb)
            for (x in 3..21 step 2) {
                val under = k != null && d.sinceKey < 300 && abs(x - (if (left) 8 else 16)) <= 2
                c.put(x, 22, (0.2f + if (under) 0.8f * hit else 0f) * kb)
            }
        }

        // A wave rolls out from each slap, sparks too once the cat is all there.
        d.recent(600) { key, a ->
            val q = a / 600f
            val cx = if (key.n % 2 == 0) 8f else 16f
            val r = 1f + q * (3f + 4f * strong)
            for (i in 0 until 40) { val an = PIF + i / 40f * PIF; c.put(cx + cos(an) * r, 19f + sin(an) * r * 0.6f, 0.5f * (1f - q) * strong.coerceAtMost(1f)) }
            if (s >= WHISKERS) for (j in 0..3) {
                val an = 3.4f + j * 0.8f + h(key.n, j) * 0.4f
                c.put(cx + cos(an) * q * 8f, 17f + sin(an) * q * 7f + q * q * 4f, 0.9f * (1f - q))
            }
        }

        // The paws: the one that hits comes down; in a rush they drum; in a pause both rise.
        val drum = ((d.t / 90) % 2 == 0L)
        val dl = if (rush) (if (drum) 1f else 0f) else if (left) hit else 0f
        val dr = if (rush) (if (drum) 0f else 1f) else if (!left) hit else 0f
        if (d.sending) { paw(c, 7, 0f); paw(c, 17, 0f); c.ring(12f, 15f, 2f + d.sentP * 12f, 1f - d.sentP) }
        else { paw(c, 7, if (rest) 1.3f else 1f - dl); paw(c, 17, if (rest) 1.3f else 1f - dr) }
    }

    /** A big paw coming up from the bottom edge, [up] 0 slapped down .. 1 raised, its toe beans and pad lit. */
    private fun paw(c: Cv, cx: Int, up: Float) {
        val top = (14f + up * 4f).roundToInt()
        for (y in top until 25) for (x in cx - 5..cx + 5) {
            val dx = (x - cx) / 5.3f; val dy = (y - (top + 5)) / 5.8f
            if (dx * dx + dy * dy <= 1f || (y > top + 5 && abs(x - cx) <= 4)) c.set(x, y, 0.34f)
        }
        for ((ox, oy) in listOf(-3 to 2, 0 to 1, 3 to 2)) c.set(cx + ox, top + oy, 0.95f)
        for (ox in -1..1) c.set(cx + ox, top + 5, 0.95f)
        c.set(cx, top + 6, 0.95f); c.set(cx - 1, top + 6, 0.6f); c.set(cx + 1, top + 6, 0.6f)
    }
}

/**
 * Fox: a spark leaps from its tail on each key, and a rush sets the tail alight. Then it sits by a little campfire that
 * flares with the keys; later the night comes, the stars, and it sleeps curled up by the fire.
 */
internal class FoxDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        heat.fill(0f)
        when (d.stage) {
            0 -> {
                val sc = p.fit(8f)
                val cx = 10f
                val cy = p.onFeet(20f, sc) - d.jump(3f)
                val tx = cx + p.w * sc / 2f
                val ty = cy + 1f
                c.floor(21, DIM)
                if (d.heat > 0.03f) { Fire.flame(heat, tx, ty, d.t, size = 0.55f, ux = 0.45f, uy = -0.9f, power = d.heat); Fire.render(c, heat) }
                p.draw(c, cx, cy, sc, d.mood())
                d.recent(1000) { k, a ->
                    val q = a / 1000f
                    val (x, y) = arc(tx, ty - 1f, 2f + h(k.n) * 7f, -10f - h(k.n, 1) * 5f, 16f, a)
                    val (px, py) = arc(tx, ty - 1f, 2f + h(k.n) * 7f, -10f - h(k.n, 1) * 5f, 16f, maxOf(0L, a - 70))
                    c.twinkle(x, y, 1f - q)
                    c.soft(px, py, 0.5f * (1f - q))
                }
                // The tail flicks up with each key.
                if (d.hit(150) > 0f) c.twinkle(tx + 1f, ty - 2f, d.hit(150))
                if (d.sending) for (i in 0..9) {
                    val ang = -PIF / 2 + (i - 4.5f) * 0.3f
                    val r = d.sentP * 10f
                    c.put(tx + cos(ang) * r, ty + sin(ang) * r, 1f - d.sentP)
                }
            }
            else -> {
                val night = d.stage == 2
                if (night) for (i in 0..9) {
                    val sx = 4f + h(i, 5) * 17f; val sy = 3f + h(i, 6) * 7f
                    c.put(sx, sy, 0.15f + 0.25f * (0.5f + 0.5f * sin(d.s * 2f + i)))
                }
                val fx = 17f
                val power = if (night) 0.35f + 0.4f * d.hit(300) + 0.5f * d.heat else 0.5f + 0.4f * d.hit(250) + 0.4f * d.heat
                for (j in -1..1) Fire.flame(heat, fx + j * 1.2f, 20.5f, d.t, size = 0.2f + 0.06f * d.heat + if (j == 0) 0.05f else 0f, power = power.coerceAtMost(1f), salt = j + 3)
                Fire.render(c, heat)
                c.art(listOf("#.#", ".#."), 16, 20, 0.35f)
                c.floor(22, 0.06f)
                d.recent(1400) { k, a -> val q = a / 1400f; c.soft(fx + sin(q * 5f + k.n) * 1.5f, 18f - q * 12f, 0.7f * (1f - q)) }
                val sc = p.fit(7f)
                val sy = if (night) sc * 0.7f else sc
                p.draw(c, 8f, p.onFeet(21f, sc, sy) - d.jump(2f), sc, if (night && !d.sending) Look.SLEEP else d.mood(), sy = sy)
            }
        }
    }
}

/**
 * Bunny: digs a burrow, a clod of earth flying from the hole with every key, a fountain of them in a rush. After a while
 * it is half down the hole; later only its ears stick out, twitching to the keys, and in a pause it peeks out.
 */
internal class BunnyDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        val ground = 19
        c.under(ground + 1, 0.06f)
        c.floor(ground, 0.2f)
        val hx = 14
        for (x in hx - 1..hx + 1) c.cut(x, ground)
        val clods = 1100L
        val from = if (d.stage == 0) hx.toFloat() else hx.toFloat()
        d.recent(clods) { k, a ->
            for (j in 0..1) {
                val dir = if (h(k.n, 9) > 0.5f) 1f else -1f
                val (x, y) = arc(from + dir, ground - 1f, dir * (4f + h(k.n, j) * 6f), -11f - h(k.n, j + 2) * 4f, 26f, a)
                if (y < ground) c.put(x, y, 0.55f * (1f - a / clods.toFloat()))
            }
        }
        when (d.stage) {
            0 -> {
                val dig = d.hit(180)
                val shake = if (d.heat > 0.3f) (if ((d.t / 60) % 2 == 0L) -0.5f else 0.5f) else 0f
                p.draw(c, 9f + shake, p.onFeet(ground.toFloat(), sc, sc * (1f - 0.12f * dig)) - d.jump(4f), sc, d.mood(), sy = sc * (1f - 0.12f * dig))
            }
            1 -> {
                val out = d.rest
                val feet = ground + p.h * sc * mix(0.32f, 0.05f, out)
                p.draw(c, hx.toFloat(), p.onFeet(feet, sc) - d.jump(2f), sc, d.mood(Look.FOCUS), below = ground - 0.5f)
            }
            else -> {
                val out = d.rest
                val feet = ground + p.h * sc * mix(0.6f, 0.3f, out)
                val twitch = if (d.hit(200) > 0f) (if (d.n % 2 == 0) -1f else 1f) else 0f
                p.draw(c, hx + twitch, p.onFeet(feet, sc) - d.jump(2f), sc, if (out > 0.5f) Look.HAPPY else Look.CALM, below = ground - 0.5f)
            }
        }
    }
}

/**
 * Chick: grains fall with the keys and it pecks the newest, a shower of grain in a rush. After a while it sits in the
 * bottom half of its shell; later the egg is whole again and it taps from inside, a crack of light with each key, the
 * top lifting for a peek in a pause.
 */
internal class ChickDesk : Desk() {
    private var x = 12f
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        c.floor(21, DIM)
        when (d.stage) {
            0 -> {
                val target = d.last?.let { 6f + h(it.n, 3) * 12f } ?: 12f
                x += (target - x) * (d.dtMs * 0.007f).coerceAtMost(1f)
                d.recent(2200) { k, a ->
                    val gx = 6f + h(k.n, 3) * 12f
                    val gy = minOf(20f, 2f + 40f * (a / 1000f) * (a / 1000f))
                    if (!(k == d.last && a > 700)) c.put(gx, gy, if (gy >= 20f) 0.45f else 0.8f)
                }
                if (d.heat > 0.05f) for (i in 0..7) {
                    val q = ((d.t / 700f + h(i, 4)) % 1f)
                    c.put(3f + h(i, 7) * 18f, 2f + q * 18f, 0.6f * d.heat)
                }
                val peck = d.hit(160)
                val flap = if (d.sending) 1f + 0.15f * sin(d.sentP * 30f) else 1f
                p.draw(c, x, p.onFeet(20f, sc) + peck - d.jump(2f), sc * flap, d.mood(), sy = sc)
            }
            1 -> {
                val peck = d.hit(160)
                p.draw(c, 12f, p.onFeet(18f, sc) + peck - d.jump(2f), sc, d.mood())
                shell(c, 12f, 15.5f, bottom = true, lift = 0f)
                d.recent(500) { k, a -> c.soft(12f + (h(k.n) - 0.5f) * 10f, 15f - a / 120f, 0.6f * (1f - a / 500f)) }
            }
            else -> {
                val lift = d.rest * 4f + d.jump(3f)
                p.draw(c, 12f, 13.5f, sc * 0.9f, if (d.rest > 0.3f) Look.HAPPY else Look.SLEEP)
                shell(c, 12f, 13f, bottom = true, lift = 0f)
                shell(c, 12f, 13f, bottom = false, lift = lift)
                // The crack round its middle glows where the chick taps.
                val tap = d.hit(300)
                for (x in 8..16) c.put(x.toFloat(), 13f + (if (x % 2 == 0) 0f else 1f) - lift * 0f, 0.15f + 0.85f * tap * (1f - abs(x - 12f - (if (d.n % 2 == 0) -2f else 2f)) / 3f).coerceIn(0f, 1f))
                d.recent(350) { k, a ->
                    val ang = -PIF / 2 + (h(k.n) - 0.5f) * 1.6f
                    c.twinkle(12f + cos(ang) * 6.5f, 13f + sin(ang) * 7.5f, 1f - a / 350f)
                }
            }
        }
    }

    /** An egg's half: the bottom from the zigzag down, or the top (lifted [lift] rows). */
    private fun shell(c: Cv, cx: Float, cy: Float, bottom: Boolean, lift: Float) {
        for (y in 0..24) for (x in 0..24) {
            val dx = (x - cx) / 5.2f; val dy = (y - cy) / 6.6f
            if (dx * dx + dy * dy > 1f) continue
            val zig = cy + if (x % 2 == 0) 0f else 1f
            val inBottom = y >= zig
            if (bottom && inBottom) c.set(x, y, if (dx * dx + dy * dy > 0.7f) 0.42f else 0.26f)
            if (!bottom && !inBottom) c.set(x, (y - lift).toInt(), if (dx * dx + dy * dy > 0.7f) 0.42f else 0.26f)
        }
    }
}

/**
 * Owl: reads a book behind which it sits, a page turning with every key, pages fluttering in a rush. Then a candle is lit
 * beside it in the dark; later it falls asleep over the book and the pages go on turning by themselves.
 */
internal class OwlDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        heat.fill(0f)
        val sleep = d.stage == 2 && !d.sending
        if (d.stage >= 1) {
            for (i in 0..7) c.put(4f + h(i, 11) * 17f, 3f + h(i, 12) * 5f, 0.1f + 0.2f * (0.5f + 0.5f * sin(d.s * 1.5f + i * 2f)))
            Fire.flame(heat, 20.5f, 14.5f, d.t, size = if (d.stage == 2) 0.28f else 0.36f, power = 0.8f + 0.2f * d.hit(300))
            Fire.render(c, heat)
            for (y in 15..19) c.put(20.5f, y.toFloat(), 0.3f)
        }
        p.draw(c, 11.5f, p.onFeet(if (sleep) 19f else 17.5f, sc) - d.jump(2f), sc, if (sleep) Look.SLEEP else d.mood(Look.FOCUS))
        // The open book.
        val closing = if (d.sending) sin(d.sentP * PIF) else 0f
        for (y in 17..21) for (x in 5..18) c.set(x, y, 0.1f)
        for (x in 5..18) { c.set(x, 17, 0.35f); c.set(x, 21, 0.25f) }
        c.set(12, 17, 0.5f); for (y in 18..21) c.set(12, y, 0.35f)
        for (r in listOf(19, 20)) for (x in 6..17 step 2) if (x != 12) c.set(x, r, 0.18f * (1f - closing))
        // Pages turning, right to left.
        d.recent(if (d.rush) 420 else 320) { _, a ->
            val q = a / (if (d.rush) 420f else 320f)
            val x = 18f - 13f * q
            val top = 17f - 3f * sin(q * PIF)
            c.line(x, top, x, 21f, 1f - q * 0.3f)
        }
        if (closing > 0f) for (x in 5..18) c.put(x, 17, closing)
    }
}

/**
 * Penguin: waddles on the ice, a step and a crack with every key; in a rush it slides on its belly right across. Then it
 * builds an igloo block by block; later the igloo stands finished in falling snow, its doorway glowing to the keys.
 */
internal class PenguinDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        c.under(21, 0.06f)
        c.floor(20, 0.25f)
        when (d.stage) {
            0 -> {
                val x = 12f + sin(d.n * 0.8f) * 4f
                d.recent(1500) { k, a ->
                    val kx = 12f + sin(k.n * 0.8f) * 4f
                    val b = 0.6f * (1f - a / 1500f)
                    c.put(kx, 21f, b); c.put(kx + 1, 22f, b * 0.7f); c.put(kx - 1, 22f, b * 0.5f)
                }
                if (d.heat > 0.2f) {
                    val sx = ((d.t * 0.012f) % 30f) - 3f
                    p.draw(c, sx, p.onFeet(20f, sc, sc * 0.7f), sc * 1.1f, Look.FOCUS, sy = sc * 0.7f, angle = 0.1f)
                    for (i in 1..4) c.put(sx - 4f - i, 20f - h(i, (d.t / 90).toInt()) * 2f, 0.4f)
                } else {
                    val tilt = if (d.n % 2 == 0) 0.13f else -0.13f
                    p.draw(c, x, p.onFeet(20f, sc) - d.jump(4f), sc, d.mood(), angle = tilt * d.hit(260))
                }
            }
            1 -> {
                igloo(c, 17f, 20f, 5f, d.grow, d)
                p.draw(c, 7f, p.onFeet(20f, sc * 0.85f) - d.hit(200) - d.jump(3f), sc * 0.85f, d.mood(Look.FOCUS))
            }
            else -> {
                igloo(c, 12f, 20f, 6.5f, 1f, d)
                // The doorway, lit from inside.
                val glow = 0.25f + 0.6f * d.hit(300) + 0.3f * d.heat
                for (y in 18..19) for (x in 11..13) c.set(x, y, glow)
                d.recent(2500) { k, a -> val q = a / 2500f; c.soft(3f + h(k.n) * 18f + sin(q * 6f + k.n) * 1.2f, 2f + q * 17f, 0.55f * (1f - q * 0.6f)) }
                if (d.rest > 0.5f) p.draw(c, 12f, 18f, sc * 0.45f, Look.HAPPY)
            }
        }
    }

    /** The igloo's dome of blocks, built up to [built] (0..1) from the left. */
    private fun igloo(c: Cv, cx: Float, base: Float, r: Float, built: Float, d: DeskInput) {
        val upTo = PIF * (0.08f + 0.92f * built)
        for (y in 0..24) for (x in 0..24) {
            val dx = x - cx; val dy = base - y
            if (dy < 0f) continue
            val dist = kotlin.math.hypot(dx, dy)
            if (dist > r) continue
            val ang = PIF - kotlin.math.atan2(dy, dx)
            if (ang > upTo) continue
            val seam = (y % 2 == 0 && (x + (y / 2)) % 3 == 0) || abs(dist - r) < 0.6f
            c.set(x, y, if (seam) 0.38f else 0.2f)
        }
        // The newest block glints with each key.
        if (built < 1f && d.hit(250) > 0f) c.twinkle(cx + cos(PIF - upTo) * (r - 1), base - sin(PIF - upTo) * (r - 1), d.hit(250))
    }
}

/**
 * Shroom: a spore drifts up from its cap with each key, the mycelium lights up under the ground in a rush. Then a fairy
 * ring of little mushrooms comes up round it, one bouncing with each key; later the ring glows in the night.
 */
internal class ShroomDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        c.floor(21, 0.12f)
        // The mycelium, lit by a rush.
        if (d.heat > 0.02f) for (i in 0..5) {
            val ex = 2f + h(i, 21) * 20f; val ey = 22f + h(i, 22) * 2f
            c.line(12f, 21.5f, ex, ey, 0.4f * d.heat * (0.6f + 0.4f * sin(d.s * 6f + i)))
        }
        val ring = d.stage >= 1
        val count = if (d.stage == 1) (1 + (d.grow * 6).toInt()).coerceAtMost(6) else 6
        val spots = listOf(4f, 19f, 7f, 16f, 2f, 21f)
        val cy = p.onFeet(20f, sc) - d.jump(2f)
        if (ring) for (i in 0 until count) {
            val bounce = if (d.n >= 0 && d.n % count == i) d.hit(260) else 0f
            val glow = if (d.stage == 2) 0.3f + 0.6f * bounce else 0.3f
            c.art(listOf("###", ".#."), spots[i].toInt() - 1, (19 - bounce).toInt(), glow)
        }
        p.draw(c, 12f, cy, sc, if (d.stage == 2 && !d.paused && !d.sending) Look.SLEEP else d.mood())
        val capTop = cy - (p.h - 1) * sc / 2f
        d.recent(2200) { k, a ->
            val q = a / 2200f
            val fromX = if (ring && h(k.n, 4) > 0.5f) spots[k.n % count] else 12f + (h(k.n) - 0.5f) * p.w * sc * 0.7f
            val fromY = if (ring && h(k.n, 4) > 0.5f) 18f else capTop
            c.soft(fromX + sin(q * 7f + k.n) * 1.5f, fromY - q * 14f, 0.65f * (1f - q))
        }
        if (d.sending) c.ring(12f, capTop, d.sentP * 9f, 0.6f * (1f - d.sentP))
    }
}

/**
 * Cactus: a needle shoots out with each key, all ways at once in a rush, the cactus swaying. Then a bud comes up on top,
 * swelling with the keys; later it opens into a flower whose petals beat with them, under a turning sun.
 */
internal class CactusDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(10f)
        c.floor(21, 0.1f)
        if (d.stage == 2) {
            c.disc(4.5f, 4.5f, 1.6f, 0.6f)
            for (i in 0..5) { val a = d.s * 0.6f + i * TAU / 6; c.put(4.5f + cos(a) * 3f, 4.5f + sin(a) * 3f, 0.3f) }
        }
        val sway = d.heat * 0.12f * sin(d.s * 8f)
        val cy = p.onFeet(20f, sc) - d.jump(1.5f)
        p.draw(c, 12f, cy, sc, d.mood(), angle = sway)
        val top = cy - (p.h - 1) * sc / 2f
        d.recent(550) { k, a ->
            val q = a / 550f
            val ang = if (d.rush) h(k.n) * TAU else (if (h(k.n) > 0.5f) 0f else PIF) + (h(k.n, 1) - 0.5f) * 0.8f
            val y0 = top + 2f + h(k.n, 2) * (p.h * sc - 4f)
            val r = 3f + q * 9f
            val x = 12f + cos(ang) * r; val y = y0 + sin(ang) * r * 0.6f
            c.put(x, y, 1f - q); c.put(x - cos(ang), y - sin(ang) * 0.6f, 0.5f * (1f - q))
        }
        when {
            d.stage == 1 -> { val r = 0.6f + 0.6f * d.grow + 0.5f * d.hit(250); c.disc(12f, top - 1f, r, 0.7f) }
            d.stage == 2 || d.sending -> {
                val r = 1.6f + 1.1f * d.hit(260) + 0.4f * d.heat
                c.put(12f, top - 2f, 1f)
                for (i in 0..4) { val a = i * TAU / 5 - PIF / 2 + d.s * 0.3f; c.put(12f + cos(a) * r, top - 2f + sin(a) * r, 0.65f) }
            }
        }
    }
}

/**
 * Toast: in its toaster, popping up with each key, flipping high in a rush. Then it browns and smoke curls from the slot;
 * later it naps on top of the smoking toaster, a puff of smoke with each key.
 */
internal class ToastDesk : Desk() {
    private val smoke = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        smoke.fill(0f)
        c.floor(22, 0.08f)
        val burnt: ((Int, Int) -> Float)? = if (d.stage >= 1) { x, y -> if (h(x, y) > 0.72f) 0.45f else 0.8f } else null
        if (d.stage >= 1) {
            Fire.smoke(smoke, 12f, if (d.stage == 2) 11f else 13f, d.t, amount = (if (d.stage == 2) 0.6f else 0.35f) + 0.5f * d.hit(400))
            Fire.renderSmoke(c, smoke)
        }
        if (d.stage < 2) {
            val q = 1f - d.hit(420)
            val pop = if (d.last == null) 0f else 4f * 4f * q * (1f - q) * (if (d.sinceKey < 420) 1f else 0f)
            val lift = pop + d.heat * 3f + d.rest * 1.5f + d.jump(9f)
            val flip = d.heat > 0.5f && d.n % 2 == 1
            p.draw(c, 12f, 15f - lift, sc, d.mood(), flipY = flip, pattern = burnt)
            if (d.heat > 0.3f) d.recent(600) { k, a -> c.put(10f + h(k.n) * 4f, 13f - (pop + 3f) + a / 60f, 0.4f) }
        }
        // The toaster.
        for (y in 15..21) for (x in 6..18) c.set(x, y, if (y == 15 || y == 21 || x == 6 || x == 18) 0.34f else 0.16f)
        for (x in 9..15) c.set(x, 15, 0.04f)
        val lever = if (d.stage < 2 && d.sinceKey > 300) 18 else 16
        c.set(19, lever, 0.7f)
        if (d.stage == 2) p.draw(c, 12f, p.onFeet(14f, sc, sc * 0.6f), sc, if (d.paused || d.sending) Look.HAPPY else Look.SLEEP, sy = sc * 0.6f, pattern = burnt)
    }
}

/**
 * Snail: crawls along writing a line of slime, a letter of it with each key, fast in a rush. Then it hides in its shell
 * and the shell writes on by itself; later the writing is one long glowing wave with a light running along it.
 */
internal class SnailDesk : Desk() {
    private var x = 4f
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        c.floor(22, 0.06f)
        fun at(n: Int) = 3f + (n * 0.35f) % 17f
        val target = at(maxOf(0, d.n))
        x = if (target < x - 3f) target else x + (target - x) * (d.dtMs * 0.01f).coerceAtMost(1f)
        if (d.stage < 2) {
            d.recent(7000) { k, a ->
                if (at(k.n) <= target + 0.1f) c.put(at(k.n) - 3f, (20f - if (h(k.n) > 0.6f) 1f else 0f), (0.5f + 0.4f * d.heat) * (1f - a / 7000f))
            }
            if (d.sending) for (i in 0..18) c.put(i + 2f, 21f, 0.8f * (1f - abs(i - 18 * d.sentP) / 3f).coerceIn(0f, 1f))
        }
        when (d.stage) {
            0 -> p.draw(c, x, p.onFeet(20f, sc) - d.jump(2f), sc, d.mood())
            1 -> shellAt(c, x, 18f - d.hit(160) - d.jump(2f), 0.36f + 0.3f * d.hit(160))
            else -> {
                for (xx in 0..24) {
                    val y = 18f + 2f * sin(xx * 0.45f + d.s * 0.8f)
                    val run = (d.t / 30f) % 30f
                    val pulse = d.recent2(xx)
                    c.put(xx.toFloat(), y, 0.22f + 0.6f * pulse + 0.5f * (1f - abs(xx - run) / 2f).coerceIn(0f, 1f) * d.heat)
                }
                shellAt(c, 19f, 13f, 0.32f)
                if (d.rest > 0.5f) p.draw(c, 19f, 13f, sc * 0.6f, Look.HAPPY)
            }
        }
    }

    /** How lit column [x] is by the keys running along the wave from the left. */
    private fun DeskInput.recent2(x: Int): Float {
        var v = 0f
        recent(900) { _, a -> val head = a / 900f * 25f; v = maxOf(v, (1f - abs(x - head) / 2f).coerceIn(0f, 1f) * (1f - a / 900f)) }
        return v
    }

    private fun shellAt(c: Cv, x: Float, y: Float, b: Float) {
        c.artSolid(listOf(".###.", "#...#", "#.#.#", "#..##", ".###."), (x - 2).toInt(), (y - 2).toInt(), b)
    }
}
