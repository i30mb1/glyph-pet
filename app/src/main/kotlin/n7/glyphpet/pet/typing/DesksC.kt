package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The typing scenes of: dino, bee, bat, chameleon, eye, gem, planet, raccoon, cloud.

/**
 * Dino: stomps with each key, the ground shaking and dust flying; in a rush it roars, sound rolling from its mouth.
 * Then a volcano behind it spits a glowing blob with each key; later it is night, the volcano erupts in a rush and
 * meteors streak across with the keys.
 */
internal class DinoDesk : Desk() {
    private val heat = Fire.field()
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        heat.fill(0f)
        val shake = if (d.hit(140) > 0f) 1f else 0f
        if (d.stage >= 1) {
            // The volcano at the back.
            for (y in 11..20) { val half = (y - 11) * 0.8f; for (x in (19 - half).toInt()..(19 + half).toInt()) c.put(x, y + shake.toInt(), 0.1f) }
            Fire.flame(heat, 19f, 10.5f, d.t, size = 0.35f + 0.25f * d.heat, power = 0.4f + 0.4f * d.hit(400) + 0.6f * d.heat)
            Fire.render(c, heat, 0.8f)
            d.recent(1300) { k, a -> val (x, y) = arc(19f, 10f, (h(k.n) - 0.5f) * 10f, -12f, 22f, a); if (y < 20f) c.soft(x, y, 0.8f * (1f - a / 1300f)) }
        }
        if (d.stage == 2) {
            for (i in 0..7) c.put(3f + h(i, 31) * 15f, 3f + h(i, 32) * 6f, 0.15f + 0.15f * sin(d.s * 2f + i))
            d.recent(700) { k, a -> val q = a / 700f; val sx = 2f + h(k.n) * 8f + q * 12f; val sy = 2f + h(k.n, 1) * 4f + q * 6f; c.line(sx - 2.5f, sy - 1.2f, sx, sy, 0.8f * (1f - q)) }
        }
        c.floor(21 + shake.toInt(), 0.12f)
        val cx = 8f
        val cy = p.onFeet(20f, sc) + shake - d.jump(3f)
        p.draw(c, cx, cy, sc, d.mood())
        val hw = p.w * sc / 2f
        d.recent(500) { k, a ->
            val q = a / 500f
            for (s in listOf(-1, 1)) c.put(cx + s * (2f + q * 4f), 20f - q * 2f, 0.45f * (1f - q))
        }
        if (d.heat > 0.05f) for (i in 0..2) { val r = ((d.t / 300f + i / 3f) % 1f) * 9f; c.ring(cx + hw, cy - 2f, r + 1f, 0.5f * d.heat * (1f - r / 9f), 0.5f, -0.7f, 0.7f) }
        if (d.sending) c.ring(cx, 21f, d.sentP * 14f, 0.6f * (1f - d.sentP), 0.5f, PIF, TAU)
    }
}

/**
 * Bee: a honeycomb cell flashes with each key; in a rush it dances its figure of eight. Then the comb grows in from the
 * side; later it fills the matrix, the bee asleep in a cell, honey filling one with each key.
 */
internal class BeeDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(if (d.stage == 2) 6f else 7f)
        fun cellAt(i: Int, j: Int) = (3f + i * 4f + (j % 2) * 2f) to (3f + j * 3.4f)
        val reach = when (d.stage) { 0 -> 0f; 1 -> d.grow; else -> 1f }
        for (j in 0..6) for (i in 0..5) {
            val (x, y) = cellAt(i, j)
            if (fromMid(x, y) > 11.5f) continue
            val shown = d.stage == 2 || (24f - x) / 24f < reach + 0.05f
            val keyCell = d.n >= 0 && (i * 7 + j * 3) % 37 == d.n % 37
            val lit = if (keyCell) d.hit(500) else 0f
            if (!shown && lit <= 0f) continue
            val v = if (shown) 0.14f else 0f
            c.ring(x, y, 1.6f, maxOf(v, lit * 0.8f), 0.45f)
            if (d.stage == 2 && keyCell) c.disc(x, y, 1f, 0.7f * lit + 0.2f)
        }
        val t = d.s * 2.4f
        val cx = 12f + d.heat * 6f * sin(t)
        val cy = (if (d.stage == 2) 13f else 12f) + d.heat * 3f * sin(2 * t) + 0.6f * sin(d.s * 6f) - d.jump(3f)
        val sleep = d.stage == 2 && !d.paused && !d.sending && d.heat < 0.3f
        p.draw(c, cx, cy, sc, if (sleep) Look.SLEEP else d.mood(), flipX = d.heat > 0.3f && cos(t) < 0f)
        if (d.sending) { val q = d.sentP; c.put(cx, cy + 3f + q * 7f, 0.9f); c.put(cx, cy + 2f + q * 7f, 0.5f) }
    }
}

/**
 * Bat: an echo ring spreads from it with each key; in a rush it zigzags about. Then it hangs upside down from a branch;
 * later it sleeps there, its ears twitching to the keys.
 */
internal class BatDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(7f)
        if (d.stage == 0) {
            val cx = 12f + d.heat * 6f * sin(d.s * 5f) + (if (d.heat > 0.3f) (if ((d.t / 250) % 2 == 0L) 1f else -1f) else 0f)
            val cy = 11f + 1.2f * sin(d.s * 3f) - d.jump(4f)
            d.recent(900) { _, a -> c.ring(cx, cy, 1f + a / 900f * 10f, 0.55f * (1f - a / 900f)) }
            p.draw(c, cx, cy, sc, d.mood())
            if (d.sending) for (r in 0..2) c.ring(cx, cy, d.sentP * 12f - r * 2f, 0.5f * (1f - d.sentP))
            return
        }
        // The branch, and the bat hanging from it.
        for (x in 3..21) c.put(x.toFloat(), 4f + sin(x * 0.4f) * 0.4f, 0.3f)
        val sleep = d.stage == 2 && !d.paused && !d.sending
        val sway = 0.08f * sin(d.s * 1.5f) + 0.1f * d.heat * sin(d.s * 7f)
        val cy = 5f + (p.h - 1) * sc / 2f
        p.draw(c, 12f, cy, sc, if (sleep) Look.SLEEP else d.mood(), flipY = true, angle = sway)
        val bottom = cy + (p.h - 1) * sc / 2f
        if (d.stage == 2 && d.hit(220) > 0f) for (s in listOf(-1, 1)) c.put(12f + s * 2f + (if (d.n % 2 == 0) s else 0), bottom + 1f, d.hit(220))
        d.recent(900) { _, a -> c.ring(12f, cy, 2f + a / 900f * 8f, 0.3f * (1f - a / 900f)) }
    }
}

/**
 * Chameleon: its skin changes pattern with each key; in a rush the stripes run along it. Then leaves come round it and
 * its skin takes their pattern; later it has melted into them, only its eyes left, swivelling with the keys.
 */
internal class ChameleonDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        for (x in 2..22) c.put(x.toFloat(), 19f + 0.3f * sin(x * 0.5f), 0.28f)
        val leaves: (Int, Int) -> Float = { x, y -> if ((x * 3 + y * 5 + (x * y) % 4) % 7 < 3) 0.22f else 0.08f }
        if (d.stage >= 1) for (y in 0..24) for (x in 0..24) if (fromMid(x.toFloat(), y.toFloat()) < 12f && y < 19) c.put(x, y, leaves(x, y) * d.stageIn())
        val n = maxOf(0, d.n)
        val skin: (Int, Int) -> Float = when {
            d.stage >= 1 -> { x, y -> 0.3f + 2.5f * leaves(x, y) }
            d.heat > 0.3f -> { x, _ -> if (((x + (d.t / 70).toInt()) / 2) % 2 == 0) 1.3f else 0.5f }
            else -> when (n % 4) {
                0 -> { x, y -> if ((x + y) % 3 == 0) 1.4f else 0.7f }
                1 -> { x, _ -> if (x % 2 == 0) 1.3f else 0.6f }
                2 -> { x, y -> if (h(x, y + n) > 0.6f) 1.5f else 0.75f }
                else -> { _, y -> if (y % 2 == 0) 1.3f else 0.6f }
            }
        }
        val cy = p.onFeet(18.5f, sc) - d.jump(3f)
        val gain = if (d.stage == 2) d.rest else 1f
        p.draw(c, 12f, cy, sc, d.mood(), gain = gain, pattern = skin)
        if (d.sending) for (i in 0..624) if (c.v[i] > 0.05f) c.v[i] = minOf(1f, c.v[i] + 0.5f * sin(d.sentP * PIF + (i % 25) * 0.3f).coerceAtLeast(0f))
        if (d.stage == 2 && d.rest < 0.7f) {
            val swivel = if (d.n >= 0) (if (h(d.n) > 0.5f) 1f else -1f) * d.hit(500).coerceAtMost(0.99f).let { if (it > 0f) 1f else 0f } else 0f
            c.ring(10f, cy - 1f, 1f, 0.35f, 0.5f); c.ring(14f, cy - 1f, 1f, 0.35f, 0.5f)
            c.put(10f + swivel, cy - 1f, 0.9f); c.put(14f - swivel, cy - 1f, 0.9f)
        }
    }
}

/**
 * Eye: its pupil jumps to wherever a key sparks on the rim; in a rush it darts all over. Then red veins creep in from
 * its edge; later it is tired, half shut, a tear rolling down now and then.
 */
internal class EyeDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(11f)
        val spot = d.last?.let { h(it.n) * TAU } ?: 0f
        val look = when {
            d.heat > 0.3f -> (2f * sin(d.s * 13f)).toInt()
            d.last != null && d.sinceKey < 900 -> (cos(spot) * 2.2f).toInt()
            else -> 0
        }
        d.recent(500) { k, a -> val ang = h(k.n) * TAU; c.twinkle(12f + cos(ang) * 11f, 12f + sin(ang) * 11f, 1f - a / 500f) }
        if (d.stage >= 1) for (i in 0..7) {
            val ang = i * TAU / 8 + 0.3f
            val len = (if (d.stage == 1) d.grow else 1f) * 3.5f
            val r0 = p.w * sc / 2f + 0.5f
            for (j in 0..len.toInt()) c.put(12f + cos(ang + j * 0.12f) * (r0 - j * 0.8f + 2f), 12f + sin(ang + j * 0.12f) * (r0 - j * 0.8f + 2f), 0.22f)
        }
        val tired = d.stage == 2 && !d.paused && !d.sending
        val wow = if (d.sending) 1f + 0.12f * sin(d.sentP * PIF) else 1f
        p.draw(c, 12f, 12f, sc * wow, if (tired) Look.SLEEP else d.mood(), eyeDx = look,
            sy = sc * wow * (if (tired) 0.75f else 1f))
        if (d.stage == 2) {
            val q = ((d.t % 2600) / 2600f)
            c.soft(14.5f, 12f + p.h * sc * 0.3f + q * 8f, 0.7f * (1f - q))
        }
    }
}

/**
 * Gem: a glint runs across its facets with each key; in a rush rays shine out of it. Then little crystals grow up round
 * it; later it sits in a crystal cave, the crystals lighting one after another with the keys.
 */
internal class GemDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        val cy = 12f - d.jump(2f)
        if (d.heat > 0.05f) for (i in 0..7) {
            val a = i * TAU / 8 + d.s * 0.8f
            for (r in 6..11) c.put(12f + cos(a) * r, cy + sin(a) * r, d.heat * 0.4f * (1f - (r - 6) / 6f))
        }
        val crystals = if (d.stage == 0) 0 else if (d.stage == 1) (1 + d.grow * 5).toInt() else 9
        for (i in 0 until crystals) {
            val a = PIF * (0.15f + 0.7f * (i % 9) / 8f) + if (d.stage == 2) PIF * (i % 2) else 0f
            val x = 12f + cos(a) * 9.5f; val y = 12f + sin(a) * 9.5f
            val lit = d.n >= 0 && d.n % crystals == i
            val b = 0.25f + if (lit) 0.7f * d.hit(400) else 0f
            c.put(x, y - 1f, b); c.put(x, y, b); c.put(x - 1f, y + 0.5f, b * 0.6f); c.put(x + 1f, y + 0.5f, b * 0.6f)
        }
        val glint: ((Int, Int) -> Float)? = if (d.sinceKey < 260 && d.last != null) {
            val q = d.sinceKey / 260f
            val line = 4f + q * 18f
            { x, y -> if (abs(x + y * 0.6f - line - 6f) < 1.2f) 2.6f else 1f }
        } else null
        p.draw(c, 12f, cy, sc, d.mood(), pattern = glint)
        if (d.sending) for (i in 0..3) { val a = i * PIF / 2 + PIF / 4; val r = d.sentP * 10f; c.twinkle(12f + cos(a) * r, cy + sin(a) * r, 1f - d.sentP) }
    }
}

/**
 * Planet: a meteor falls in and flares with each key; in a rush its ring spins fast. Then a moon goes round it and
 * city lights come on over its night side; later the lights twinkle with the keys and satellites cross.
 */
internal class PlanetDesk : Desk() {
    private var ring = 0f
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(9f)
        ring += d.dtMs / 1000f * (0.6f + 5f * d.heat)
        for (i in 0..9) c.put(2f + h(i, 41) * 20f, 2f + h(i, 42) * 20f, 0.1f)
        val cy = 12f - d.jump(2f)
        // The ring behind, then the planet, then the ring in front.
        fun ringPart(front: Boolean) {
            for (i in 0 until 40) {
                val a = i * TAU / 40
                if ((sin(a) > 0f) != front) continue
                val b = 0.2f + 0.6f * (0.5f + 0.5f * cos(a - ring * 2f)).let { it * it * it }
                c.put(12f + cos(a) * 10f, cy + sin(a) * 2.5f, b)
            }
        }
        ringPart(false)
        val lights: ((Int, Int) -> Float)? = if (d.stage >= 1) { x, y ->
            if (x > 12 && h(x, y) > 0.7f) 1.6f + (if (d.stage == 2 && h(x, y + maxOf(0, d.n)) > 0.8f) 1.5f * d.hit(400) else 0f) else 1f
        } else null
        p.draw(c, 12f, cy, sc, d.mood(), pattern = lights)
        ringPart(true)
        d.recent(500) { k, a ->
            val q = a / 500f
            val ang = h(k.n) * TAU
            val r = 12f - q * (12f - p.w * sc / 2f)
            if (q < 0.95f) c.line(12f + cos(ang) * (r + 1.5f), cy + sin(ang) * (r + 1.5f), 12f + cos(ang) * r, cy + sin(ang) * r, 0.8f)
            else c.twinkle(12f + cos(ang) * r, cy + sin(ang) * r, 1f)
        }
        if (d.stage >= 1) { val a = d.s * 0.9f; c.disc(12f + cos(a) * 11f, cy + sin(a) * 4f, 0.8f, 0.6f) }
        if (d.stage == 2) for (i in 0..1) { val a = d.s * (1.6f + i) + i * 2f; c.put(12f + cos(a) * 8f, cy - 6f + sin(a) * 1.5f, 0.7f) }
        if (d.sending) c.ring(12f, cy, p.w * sc / 2f + d.sentP * 6f, 0.7f * (1f - d.sentP))
    }
}

/**
 * Raccoon: pulls a thing out of the trash can with each key; in a rush it rummages and the junk fountains. Then it is in
 * the can, only its ears and eyes over the rim; later the lid is on and hops with each key, lifting for a peek in a
 * pause.
 */
internal class RaccoonDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        c.floor(22, 0.08f)
        val canL = 13; val canR = 20; val canT = 14
        val shake = if (d.heat > 0.3f) (if ((d.t / 70) % 2 == 0L) 1 else 0) else 0
        // Junk flying out of the can.
        d.recent(1000) { k, a ->
            val (x, y) = arc(16.5f, canT - 1f, -3f - h(k.n) * 7f, -10f - h(k.n, 1) * 4f, 24f, a)
            if (y < 21f) when (k.n % 3) {
                0 -> c.put(x, y, 0.7f)
                1 -> { c.put(x, y, 0.6f); c.put(x + 1f, y, 0.6f) }
                else -> { c.put(x, y, 0.6f); c.put(x, y + 1f, 0.6f); c.put(x + 1f, y + 1f, 0.6f) }
            }
        }
        when (d.stage) {
            0 -> p.draw(c, 8f, p.onFeet(21f, sc) - d.jump(3f), sc, if (d.paused) Look.WOW else d.mood())
            1 -> p.draw(c, 16.5f, p.onFeet(canT + p.h * sc * 0.55f, sc), sc, if (d.paused) Look.WOW else d.mood(), below = canT - 0.5f)
            else -> if (d.rest > 0.2f) p.draw(c, 16.5f, p.onFeet(canT + p.h * sc * 0.7f, sc), sc, Look.WOW, below = canT - 0.5f)
        }
        for (y in canT..21) for (x in canL..canR) c.set(x + shake, y, if (x == canL || x == canR || y == canT || (y - canT) % 3 == 0) 0.34f else 0.14f)
        if (d.stage == 2) {
            val lift = d.hit(220) * 1.5f + d.rest * 2.5f + d.jump(4f)
            for (x in canL - 1..canR + 1) c.set(x, (canT - 1 - lift).toInt(), 0.45f)
            c.set((canL + canR) / 2, (canT - 2 - lift).toInt(), 0.6f)
        }
    }
}

/**
 * Cloud: a raindrop falls with each key and splashes; in a rush it pours, with lightning. Then the sun comes out behind
 * it; later a rainbow arcs over the matrix, a band of it flaring with each key.
 */
internal class CloudDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        val p = d.pet
        val sc = p.fit(8f)
        c.floor(21, DIM)
        val cy = 7f + 0.6f * sin(d.s * 1.2f) - d.jump(2f)
        if (d.stage >= 1) {
            val sx = 18f; val sy = 6f
            c.disc(sx, sy, 2.2f * d.stageIn(), 0.55f)
            for (i in 0..7) { val a = i * TAU / 8 + d.s * 0.4f; c.put(sx + cos(a) * 3.8f, sy + sin(a) * 3.8f, 0.3f * d.stageIn()) }
        }
        if (d.stage == 2) for (band in 0..2) {
            val r = 10f - band * 1.6f
            val lit = d.n >= 0 && d.n % 3 == band
            val b = 0.18f + 0.1f * band + if (lit) 0.6f * d.hit(400) else 0f
            c.ring(12f, 21f, r, b, 0.5f, PIF, TAU)
        }
        val bottom = cy + (p.h - 1) * sc / 2f
        d.recent(700) { k, a ->
            val q = a / 700f
            val x = 12f + (h(k.n) - 0.5f) * p.w * sc * 0.8f
            val y = bottom + 1f + q * (20f - bottom)
            if (q < 0.9f) { c.put(x, y, 0.75f); c.put(x, y - 1f, 0.35f) } else { c.put(x - 1f, 20f, 0.6f); c.put(x + 1f, 20f, 0.6f) }
        }
        if (d.heat > 0.05f) for (i in 0..9) {
            val q = ((d.t / 450f + h(i)) % 1f)
            c.put(5f + h(i, 3) * 14f, bottom + 1f + q * (20f - bottom), 0.5f * d.heat)
        }
        val bolt = (d.heat > 0.5f && (d.t / 900) % 3 == 0L && (d.t % 900) < 120) || (d.sending && d.sentP < 0.2f)
        if (bolt) { var x = 13f; var y = bottom + 1f; while (y < 20f) { val nx = x + (if (((y * 7).toInt()) % 2 == 0) 1f else -1f); c.line(x, y, nx, y + 2f, 1f); x = nx; y += 2f } }
        p.draw(c, 12f, cy, sc, if (d.stage >= 1 && !d.rush) Look.HAPPY else d.mood())
    }
}

