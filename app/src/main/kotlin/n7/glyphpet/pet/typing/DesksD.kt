package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The typing scenes of the drawn heroes: phoenix, black hole, swarm, dragon, mimic, moon rabbit, Schrodinger's cat,
// lava lamp, paper boat. A drawn hero is a whole picture, so its scene mostly plays round it and changes the picture.

/**
 * Phoenix: a feather of fire flares on its wings with each key; in a rush its wings burn. Then it burns down to ash in
 * a great flame; later it is reborn, a little phoenix in a nest of fire that puffs up with each key.
 */
internal class PhoenixDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        heat.fill(0f)
        when (d.stage) {
            0 -> {
                val sc = 0.85f
                val cy = p.cy + 1.5f - d.jump(2f)
                d.recent(500) { k, a ->
                    val side = if (k.n % 2 == 0) -1f else 1f
                    Fire.flame(heat, 12f + side * (3f + h(k.n) * 6f), cy - 2f + h(k.n, 1) * 3f, d.t, size = 0.3f, power = 1f - a / 500f, salt = k.n)
                }
                if (d.heat > 0.03f) for (side in listOf(-1f, 1f)) Fire.flame(heat, 12f + side * 7f, cy, d.t, size = 0.5f, ux = side * 0.4f, uy = -0.9f, power = d.heat, salt = side.toInt() + 7)
                if (d.sending) Fire.flame(heat, 12f, cy - 3f, d.t, size = 0.9f, power = 1f - d.sentP, salt = 99)
                Fire.render(c, heat)
                p.draw(c, 12f, cy, sc, d.mood())
            }
            1 -> {
                val k = d.stageIn(4000)
                Fire.flame(heat, 12f, 18f, d.t, size = 0.9f * (1f - 0.5f * k) + 0.2f * d.hit(300), power = 1f - 0.4f * k)
                Fire.render(c, heat)
                p.draw(c, 12f, p.cy + 2f, 0.8f, Look.CALM, gain = mix(1f, 0.12f, k))
                for (i in 0..9) { val q = ((d.t / 2400f + h(i)) % 1f); c.put(5f + h(i, 2) * 14f, 4f + q * 17f, 0.14f * k) }
            }
            else -> {
                val sc = 0.38f + 0.18f * d.grow
                Fire.flame(heat, 12f, 19.5f, d.t, size = 0.45f + 0.3f * d.hit(350) + 0.25f * d.heat, power = 0.8f)
                Fire.render(c, heat)
                c.art(listOf("#.#.#.#", ".#####."), 9, 20, 0.35f)
                p.draw(c, 12f, 16f - d.jump(3f), sc, d.mood())
            }
        }
    }
}

/**
 * Black hole: a bit of light falls in on a spiral with each key; in a rush its disc blazes. Then the stars round it
 * stream in; later it has swallowed the matrix, its arms turning round it, a flash at the heart with each key.
 */
internal class BlackholeDesk : Desk() {
    private var turn = 0f
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        turn += d.dtMs / 1000f * (0.5f + 2.5f * d.heat)
        if (d.stage >= 1) for (i in 0..17) {
            val q = ((d.t / (3000f + h(i) * 2000f) + h(i, 1)) % 1f)
            val a = h(i, 2) * TAU + q * 2f
            val r = 12f * (1f - q)
            c.put(12f + cos(a) * r, 12f + sin(a) * r, 0.25f * q)
            if (d.stage == 1) c.put(12f + cos(a - 0.15f) * (r + 1f), 12f + sin(a - 0.15f) * (r + 1f), 0.1f * q)
        }
        if (d.stage == 2) for (arm in 0..1) for (j in 0..40) {
            val r = 2.5f + j * 0.25f
            val a = turn + arm * PIF + r * 0.45f
            c.put(12f + cos(a) * r, 12f + sin(a) * r * 0.9f, 0.18f + 0.1f * d.heat)
        }
        p.draw(c, gain = 1f + 0.6f * d.heat)
        if (d.heat > 0.05f) c.ring(12f, 12f, 4.5f + sin(turn * 3f) * 0.3f, 0.6f * d.heat, 0.5f)
        d.recent(1000) { k, a ->
            val q = a / 1000f
            val r = 11.5f * (1f - q) + 2f * q
            val ang = h(k.n) * TAU + q * 4.5f
            c.soft(12f + cos(ang) * r, 12f + sin(ang) * r, 0.9f)
        }
        if (d.stage == 2 && d.hit(300) > 0f) c.ring(12f, 12f, 3f, d.hit(300), 0.6f)
        if (d.sending) { val l = d.sentP * 12f; for (y in 0..l.toInt()) { c.put(12, 9 - y, 0.9f * (1f - d.sentP)); c.put(12, 15 + y, 0.9f * (1f - d.sentP)) } }
    }
}

/**
 * Swarm: a new mote flies in from the edge with each key; in a rush the swarm makes shapes, one after another. Then it
 * draws a running wave; later it is a great ring turning, a mote spinning off with each key.
 */
internal class SwarmDesk : Desk() {
    private val n = 18
    private val xs = FloatArray(n) { 12f + (h(it) - 0.5f) * 8f }
    private val ys = FloatArray(n) { 12f + (h(it, 1) - 0.5f) * 8f }
    override fun draw(c: Cv, d: DeskInput) {
        val k = (d.dtMs / 220f).coerceAtMost(1f)
        val shape = ((d.t / 1600) % 3).toInt()
        for (i in 0 until n) {
            val u = i.toFloat() / n
            val (tx, ty) = when {
                d.sending -> { val a = u * TAU; val r = 2f + d.sentP * 10f; (12f + cos(a) * r) to (12f + sin(a) * r) }
                d.stage == 2 -> { val a = u * TAU + d.s * 0.8f; (12f + cos(a) * 9f) to (12f + sin(a) * 9f) }
                d.stage == 1 -> { val x = 3f + u * 18f; x to (12f + 4f * sin(x * 0.5f - d.s * 3f)) }
                d.heat > 0.4f -> when (shape) {
                    0 -> { val a = u * TAU; val hx = 16f * sin(a).let { it * it * it }; val hy = -(13f * cos(a) - 5f * cos(2 * a) - 2f * cos(3 * a) - cos(4 * a)); (12f + hx * 0.5f) to (12f + hy * 0.5f) }
                    1 -> { val a = u * TAU; (12f + cos(a) * 7f) to (12f + sin(a) * 7f) }
                    else -> { val a = u * TAU; val r = if (i % 2 == 0) 8f else 3.5f; (12f + cos(a) * r) to (12f + sin(a) * r) }
                }
                else -> {
                    val drift = if (d.paused) 0.4f else 1f
                    (12f + (h(i) - 0.5f) * 9f + 1.5f * sin(d.s * drift * (1f + u) + i)) to (12f + (h(i, 1) - 0.5f) * 8f + 1.5f * cos(d.s * drift * (1.3f - u) + i))
                }
            }
            xs[i] += (tx - xs[i]) * k
            ys[i] += (ty - ys[i]) * k
            c.soft(xs[i], ys[i], 0.85f)
        }
        // The newest mote, flying in from the edge to its place in the swarm.
        d.recent(450) { key, a ->
            val q = a / 450f
            val i = key.n % n
            val ang = h(key.n) * TAU
            val fx = 12f + cos(ang) * 12f; val fy = 12f + sin(ang) * 12f
            if (d.stage == 2) c.soft(mix(xs[i], 12f + cos(ang) * 13f, q), mix(ys[i], 12f + sin(ang) * 13f, q), 1f - q)
            else c.soft(mix(fx, xs[i], q), mix(fy, ys[i], q), 1f)
        }
    }
}

/**
 * Dragon: a puff of smoke from its nose with each key; in a rush it breathes fire. Then it lies on a pile of treasure,
 * a coin glinting with each key; later it sleeps on its hoard, a smoke ring rising with each key.
 */
internal class DragonDesk : Desk() {
    private val heat = Fire.field()
    private val smoke = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        heat.fill(0f); smoke.fill(0f)
        val hoard = d.stage >= 1
        if (hoard) {
            for (y in 18..22) { val half = (y - 17) * 2.2f; for (x in (12 - half).toInt()..(12 + half).toInt()) c.put(x, y, if (h(x, y) > 0.6f) 0.42f else 0.2f) }
            d.recent(400) { k, a -> c.twinkle(6f + h(k.n) * 12f, 19f + h(k.n, 1) * 3f, 1f - a / 400f) }
        }
        val sc = if (hoard) 0.62f else 0.72f
        val cx = if (hoard) 12f else 10f
        val cy = if (hoard) 11f else 12f - d.jump(2f)
        val mx = cx + p.w * sc / 2f
        val my = cy - p.h * sc / 4f
        if (!hoard) {
            Fire.smoke(smoke, mx, my, d.t, amount = 0.25f + 0.75f * d.hit(500))
            Fire.renderSmoke(c, smoke)
            val fire = maxOf(d.heat, if (d.sending) 1f - d.sentP else 0f)
            if (fire > 0.03f) { Fire.flame(heat, mx + 1f, my, d.t, size = 0.6f + 0.3f * fire, ux = 0.95f, uy = -0.3f, power = fire); Fire.render(c, heat) }
        }
        val sleep = d.stage == 2 && !d.paused && !d.sending
        p.draw(c, cx, cy, sc, if (sleep) Look.SLEEP else d.mood())
        if (d.stage == 2) d.recent(1800) { _, a -> val q = a / 1800f; c.ring(mx - 1f + q * 2f, my - 1f - q * 8f, 0.7f + q * 1.3f, 0.4f * (1f - q), 0.45f) }
    }
}

/**
 * Mimic: the chest clacks with each key, a coin jumping out; in a rush its tongue lolls out and flaps. Then it starts
 * turning into a keyboard; later it is one, a key lighting with each key, and in a pause an eye opens on it.
 */
internal class MimicDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val board = when (d.stage) { 0 -> 0f; 1 -> d.stageIn(5000) * 0.7f; else -> 1f }
        if (board < 0.99f) {
            val clack = d.hit(160)
            val sy = 1f - 0.15f * clack
            val cy = p.cy + (p.h * (1f - sy)) / 2f - d.jump(3f)
            if (d.heat > 0.05f) for (j in 0..5) c.put(12f + 1.5f * sin(d.s * 9f + j * 0.7f) * (j / 5f), p.top - 1f - j, 0.6f * d.heat)
            p.draw(c, p.cx, cy, 1f, d.mood(), sy = sy, gain = 1f - board)
            d.recent(800) { k, a -> val (x, y) = arc(12f, p.top.toFloat(), (h(k.n) - 0.5f) * 8f, -10f, 26f, a); c.twinkle(x, y, 0.8f * (1f - a / 800f)) }
        }
        if (board > 0.01f) {
            for (r in 0..2) for (i in 0..(7 - r)) {
                val x = 5 + r + i * 2; val y = 13 + r * 2
                val lit = d.n >= 0 && (d.n * 5) % 21 == r * 8 + i
                c.put(x, y, board * (0.25f + if (lit) 0.75f * d.hit(300) else 0f) + (if (d.heat > 0.3f) 0.3f * (0.5f + 0.5f * sin(d.s * 12f - i)) else 0f) * board)
            }
            c.box(4, 12, 20, 18, 0.12f * board)
            if (d.stage == 2 && d.rest > 0.3f && (d.t % 2000) > 150) { c.put(10, 10, d.rest); c.put(14, 10, d.rest) }
            if (d.sending) for (i in 0..7) c.put(5 + i * 2, 13, (1f - abs(i - d.sentP * 7f) / 2f).coerceIn(0f, 1f))
        }
    }
}

/**
 * Moon rabbit: pounds the mortar with its pestle, a thump with each key; in a rush rice cakes fly out. Then its moon
 * waxes brighter; later a great full moon rises behind it, ringing with light at each key.
 */
internal class MoonrabbitDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        if (d.stage == 2) { c.disc(15f, 10f, 7.5f * d.stageIn(3000), 0.12f); d.recent(600) { _, a -> c.ring(15f, 10f, 7.5f + a / 600f * 4f, 0.4f * (1f - a / 600f)) } }
        if (d.stage >= 1) c.ring(19f, 5f, 3.2f + d.grow, 0.25f + 0.25f * d.grow, 0.5f)
        p.draw(c, p.cx - 2f, p.cy - d.jump(2f), 1f, d.mood())
        // The mortar and the pestle that strikes into it.
        c.art(listOf("#######", ".#####.", "..###.."), 14, 19, 0.35f)
        val strike = d.hit(200)
        val py = 17f - 4f * (1f - strike)
        c.line(17f, py - 4f, 17f, py, 0.6f)
        if (strike > 0.6f) for (s in listOf(-1, 1)) c.put(17f + s * 2f, 18f, strike)
        if (d.heat > 0.05f || d.sending) d.recent(900) { k, a ->
            val (x, y) = arc(17f, 18f, (h(k.n) - 0.5f) * 9f, if (d.sending) -16f else -11f, 24f, a)
            if (y < 21f) { c.put(x, y, 0.7f); c.put(x + 1f, y, 0.5f) }
        }
    }
}

/**
 * Schrodinger's cat: the box jumps with each key; in a rush the cat flickers, there and not. Then the box is shut and
 * shuffles with the keys; later a question mark hangs over it that turns to an exclamation mark with each key.
 */
internal class SchrodingerDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val lid = p.top + p.h * 0.45f
        val jump = d.hit(180) * 1.5f + d.jump(3f)
        when (d.stage) {
            0 -> {
                val gone = d.heat > 0.3f && d.n % 2 == 1
                p.draw(c, p.cx, p.cy - jump, 1f, d.mood(), above = if (gone) lid - jump else -99f)
                if (d.paused) c.artSolid(listOf("ooo", "..o", ".o.", "...", ".o."), 11, (p.top - 6f).toInt().coerceAtLeast(1), 0.3f)
            }
            1 -> {
                val shuffle = if (d.hit(150) > 0f) (if (d.n % 2 == 0) -1f else 1f) else 0f
                p.draw(c, p.cx + shuffle, p.cy - jump, 1f, Look.CALM, above = lid - jump + 0.5f)
                for (x in (p.left - 1)..(p.right + 1)) c.put(x + shuffle, lid - jump, 0.45f)
                if (d.paused && (d.t % 1600) < 500) c.put(p.right + 1f, lid + 2f, 0.6f)
            }
            else -> {
                p.draw(c, p.cx, p.cy - jump, 1f, Look.CALM, above = lid - jump + 0.5f)
                for (x in (p.left - 1)..(p.right + 1)) c.put(x.toFloat(), lid - jump, 0.45f)
                val bang = d.hit(400) > 0f || d.sending
                val y = (lid - 7f).toInt().coerceAtLeast(2)
                c.art(if (bang) listOf(".o.", ".o.", ".o.", "...", ".o.") else listOf("ooo", "..o", ".o.", "...", ".o."), 11, y, 0.3f)
            }
        }
    }
}

/**
 * Lava lamp: a blob of wax rises with each key; in a rush it bubbles all over. Then the warm wax pools and rises from
 * the bottom; later the lamp is nearly full, and each key splits a blob in two that drift apart and join again.
 */
internal class LavalampDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        if (d.stage >= 1) {
            val level = 21f - (if (d.stage == 1) d.grow * 7f else 8f) - 0.6f * sin(d.s * 1.2f)
            for (y in 0..24) for (x in 0..24) if (y > level + 0.6f * sin(x * 0.7f + d.s)) c.put(x, y, 0.2f)
        }
        p.draw(c, gain = 1f + 0.4f * d.heat)
        d.recent(1800) { k, a ->
            val q = a / 1800f
            val x = 12f + (h(k.n) - 0.5f) * 8f + sin(q * 4f + k.n) * 1.2f
            val y = 21f - q * 17f
            c.disc(x, y, 1f + 0.4f * sin(q * PIF), 0.55f * (1f - q * 0.5f))
        }
        if (d.heat > 0.05f) for (i in 0..5) { val q = ((d.t / 900f + h(i)) % 1f); c.disc(6f + h(i, 1) * 12f, 20f - q * 15f, 0.6f, 0.45f * d.heat) }
        if (d.stage == 2 && d.hit(700) > 0f) {
            val q = 1f - d.hit(700)
            val sep = sin(q * PIF) * 4f
            c.disc(12f - sep, 9f, 1.6f, 0.6f); c.disc(12f + sep, 9f, 1.6f, 0.6f)
        }
        if (d.sending) for (i in 0..624) if (c.v[i] > 0.05f) c.v[i] = minOf(1f, c.v[i] * (1.8f - d.sentP))
    }
}

/**
 * Paper boat: a wave runs under it with each key; in a rush a storm rocks it, rain driving. Then it unfolds and folds
 * into a paper plane that loops round; later the plane flies with a trail, looping up with each key.
 */
internal class PaperboatDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        if (d.stage == 0) {
            val rock = d.heat * 0.22f * sin(d.s * 5f)
            p.draw(c, p.cx, p.cy - d.jump(3f) + d.heat * sin(d.s * 4f), 1f, d.mood(), angle = rock)
            d.recent(1000) { _, a -> val x = a / 1000f * 25f; c.put(x, 18f - sin(x * 0.6f), 0.9f); c.put(x - 1f, 18f - sin((x - 1f) * 0.6f), 0.5f) }
            if (d.heat > 0.05f) for (i in 0..11) { val q = ((d.t / 400f + h(i)) % 1f); val x = 2f + h(i, 1) * 20f + q * 3f; c.line(x, q * 18f, x + 1f, q * 18f + 2f, 0.4f * d.heat) }
            return
        }
        val k = if (d.stage == 1) d.stageIn(2500) else 1f
        if (k < 1f) p.draw(c, gain = 1f - k)
        // The plane: round a loop, nose first.
        val a = d.s * 1.4f
        var x = 12f + 7f * cos(a)
        var y = 10f + 4f * sin(a * 2f)
        if (d.stage == 2) { y -= 3f * sin(d.hit(500) * PIF); for (j in 1..8) { val b = a - j * 0.12f; c.put(12f + 7f * cos(b), 10f + 4f * sin(b * 2f), 0.3f * (1f - j / 9f)) } }
        y -= d.jump(3f)
        val dir = if (-sin(a) >= 0f) 1 else -1
        val plane = listOf("o.....", ".###..", "..####", ".###..")
        val rows = if (dir > 0) plane else plane.map { it.reversed() }
        c.art(rows, (x - 3f).toInt(), (y - 2f).toInt(), 0.35f * k)
        if (d.stage == 1) c.wave(19f, d.s, 0.2f * (1f - k) + 0.12f)
    }
}
