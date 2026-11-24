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
 * Cat at the keyboard: the whole cat sits facing us from the start, its paws on a keyboard. Each key one paw taps down
 * and the key under it lights; the eyes follow the paw. A rush drums both paws and the eyes narrow; a pause lifts
 * both paws off the keys and it waits, blinking; a
 * sent message lifts both paws with happy eyes. After 10 s its tail comes in and swings with the keys; after 20 s
 * whiskers, and between keys it shuts its eyes in content.
 */
internal class CatDesk : Desk() {
    private companion object {
        val HEAD = listOf(
            "#.........#",
            "##.......##",
            "###.....###",
            "###########",
            "###########",
            "###########",
            "###########",
            "###########",
            "###########",
            ".#########.",
        )
        const val HX = 7
        const val HY = 3
        const val B = 0.32f
    }

    override fun draw(c: Cv, d: DeskInput) {
        val k = d.last
        val left = k == null || k.n % 2 == 0
        val hit = d.hit(200) > 0f
        val rush = d.heat > 0.5f
        val content = d.stage >= 2 && d.sinceKey > 260 && !d.sending && !d.paused
        val up = if (d.sending) -1 else 0

        // Tail (10 s): from behind the body, swinging with the keys.
        if (d.stage >= 1) {
            val sw = if (hit) 1 else 0
            val tail = listOf(18 to 19, 19 to 19, 20 to 18, 21 to 17, 21 to 16, 21 + sw to 15, 20 + sw * 2 to 14)
            for ((x, y) in tail) { c.set(x, y, B); c.set(x, y + 1, B * 0.6f) }
        }
        // Body: shoulders under the head, widening down to the keyboard.
        for (y in 13..19) {
            val half = 4 + (y - 13) / 2
            for (x in 12 - half..12 + half) c.set(x, y + up, B)
        }
        // Head.
        HEAD.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '#') c.set(HX + i, HY + j + up, B) } }
        // Ears perk with each key in the last act.
        if (d.stage >= 2 && hit) { c.set(HX, HY - 1 + up, B); c.set(HX + 10, HY - 1 + up, B) }

        // Eyes: two dark 2x2 holes, glancing at the paw that taps.
        val ey = HY + 4 + up
        val look = when { d.sending || content -> 0; k != null && d.sinceKey < 500 -> if (left) -1 else 0; else -> 0 }
        val blink = d.paused && d.t % 2600 < 160
        for (ex in listOf(HX + 2, HX + 7)) {
            val x = ex + look
            when {
                d.sending -> { c.cut(x, ey + 1); c.cut(x + 1, ey); c.cut(x + 2, ey + 1) }
                blink || content -> { c.cut(x, ey + 1); c.cut(x + 1, ey + 1) }
                rush -> { c.cut(x, ey + 1); c.cut(x + 1, ey + 1); c.cut(x, ey); c.cut(x + 1, ey) ; c.set(x, ey, B) }
                else -> { c.cut(x, ey); c.cut(x + 1, ey); c.cut(x, ey + 1); c.cut(x + 1, ey + 1) }
            }
        }
        // Nose and mouth.
        c.cut(HX + 5, HY + 7 + up)
        if (d.paused || content || d.sending) { c.cut(HX + 4, HY + 8 + up); c.cut(HX + 6, HY + 8 + up) }
        // Whiskers (20 s), from the cheeks outwards.
        if (d.stage >= 2) for (side in listOf(-1, 1)) {
            val x0 = if (side < 0) HX - 1 else HX + 11
            for (w in 0..1) { c.set(x0, HY + 6 + w * 2 + up, 0.22f); c.set(x0 + side, HY + 6 + w * 2 + up, 0.22f) }
        }

        // Keyboard: a row of keys and its base.
        for (x in 3..21) { c.set(x, 23, 0.1f); c.set(x, 22, if (x % 3 != 0) 0.2f else 0.1f) }

        // Paws: a rush drums them, a key taps one, a sent message lifts both.
        val drum = (d.t / 100) % 2 == 0L
        // A pause: it lifts both paws off the keys and waits, looking at you.
        val downL = when { d.sending -> -2; d.paused -> -1; rush -> if (drum) 1 else 0; hit && left -> 1; else -> 0 }
        val downR = when { d.sending -> -2; d.paused -> -1; rush -> if (drum) 0 else 1; hit && !left -> 1; else -> 0 }
        paw(c, 8, downL)
        paw(c, 16, downR)
    }

    /** A paw on the keys at column [cx]: [down] 1 taps (and the key under it lights), 0 rests, below 0 raised. */
    private fun paw(c: Cv, cx: Int, down: Int) {
        val y = 19 + down
        for (x in cx - 1..cx + 1) { c.set(x, y, 0.7f); c.set(x, y + 1, 0.7f) }
        c.set(cx - 1, y + 1, 0.45f); c.set(cx + 1, y + 1, 0.45f)
        // The arm from the body to the paw.
        for (yy in 18 until y) c.set(cx, yy, B)
        if (down == 1) for (x in cx - 2..cx + 2) if (x % 3 != 0) c.set(x, 22, 1f)
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
 * Bunny: it digs by its burrow, a dark oval opening with a lit rim. With each key it dives its nose in and reaches in
 * with a paw, and a clod of earth flies out of the hole in an arc onto the pile beside it, which grows; in a rush two
 * clods fly. In a pause it sits and blinks. From 20 s of typing it is half down the hole, from 40 s only its ears stick
 * out over the rim, and in a pause it peeks out to its eyes. A sent message is a ring of light.
 */
internal class BunnyDesk : Desk() {
    private companion object {
        const val GROUND = 18
        const val HOLE = 13
        const val PILE = 4
        const val FLY_MS = 450L
        const val B = 0.34f
        const val HI = 0.95f
        /** The bunny it digs as: ears, eyes off, a bright nose. */
        val BUN = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "#######", ".#####.")
    }

    /** Clods in the air (when they left the hole, ms into the mode), and how big the pile has got. */
    private val clods = ArrayList<Long>()
    private var lastN = -1
    private var pile = 0f

    override fun draw(c: Cv, d: DeskInput) {
        val k = d.last
        if (k != null && k.n != lastN) {
            lastN = k.n
            clods += k.at
            if (d.rush) clods += k.at + 60
        }
        val s = d.typed / 1000f
        // The ground and the earth under it.
        for (x in 0..24) { c.set(x, GROUND, 0.22f); for (y in GROUND + 1..24) c.set(x, y, 0.07f) }
        // The burrow: a dark oval opening with a lit rim, seen a little from above.
        for (y in GROUND - 1..GROUND + 2) for (x in HOLE - 4..HOLE + 4) {
            val dx = (x - HOLE) / 4.2f; val dy = (y - (GROUND + 0.5f)) / 1.6f
            val dd = dx * dx + dy * dy
            if (dd <= 1f) c.set(x, y, if (dd > 0.55f) 0.42f else 0f)
        }
        // The pile of earth on the left, joined to the ground.
        val ph = minOf(5, pile.toInt())
        for (j in 0 until ph) for (x in PILE - j..PILE + j + 1) c.set(x, GROUND - ph + j, 0.3f)
        // Clods: 2x2, out of the hole in an arc onto the pile, where they join it.
        val it = clods.iterator()
        while (it.hasNext()) {
            val a = d.t - it.next()
            if (a < 0) continue
            if (a >= FLY_MS) { it.remove(); pile = minOf(5.9f, pile + 0.18f); continue }
            val q = a.toFloat() / FLY_MS
            val x = Math.round(HOLE + (PILE + 1 - HOLE) * q)
            val y = Math.round(GROUND - 2 - sin(q * PIF) * 8f)
            for (dy in 0..1) for (dx in 0..1) c.set(x + dx, y + dy, 0.85f)
        }
        // How deep it is: beside the hole first, half in from 20 s, only its ears from 40 s; a pause brings it up.
        val depth = when { s < 20f -> 0; s < 40f -> 4; else -> 6 }
        val peek = if (d.paused) minOf(depth, 4) else 0
        val dig = d.sinceKey < 200 && k != null
        val bx = if (depth == 0) HOLE + 5 else HOLE
        val feet = GROUND - 1 + depth - peek + if (dig && depth < 6) 1 else 0
        val blink = d.paused && (d.t % 2600) < 160
        BUN.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            val x = bx - 3 + i; val y = feet - 7 + j
            if (depth > 0 && y >= GROUND) return@forEachIndexed
            when (ch) {
                '#' -> c.set(x, y, B)
                'o' -> c.set(x, y, HI)
                'x' -> c.set(x, y, if (blink) B else 0f)
            }
        } }
        // Beside the hole it digs with a front paw: the paw reaches into the hole with each key.
        if (depth == 0) { val py = if (dig) GROUND else GROUND - 2; c.set(bx - 4, py, B); c.set(bx - 5, py, B) }
        if (d.sending) c.ring(12f, 12f, 3f + d.sentP * 10f, 1f - d.sentP)
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
            // A rush pops it higher; it is never turned upside down (its face, mouth and all, would flip over).
            p.draw(c, 12f, 15f - lift - d.heat * 2f, sc, d.mood(), pattern = burnt)
        }
        // The toaster.
        for (y in 15..21) for (x in 6..18) c.set(x, y, if (y == 15 || y == 21 || x == 6 || x == 18) 0.34f else 0.16f)
        for (x in 9..15) c.set(x, 15, 0.04f)
        val lever = if (d.stage < 2 && d.sinceKey > 300) 18 else 16
        c.set(19, lever, 0.7f)
        if (d.stage == 2) p.draw(c, 12f, p.onFeet(14f, sc), sc, if (d.paused || d.sending) Look.HAPPY else Look.SLEEP, pattern = burnt)
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
