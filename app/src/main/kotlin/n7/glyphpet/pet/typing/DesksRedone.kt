package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Typing scenes redone with the owner, scene by scene (docs/design/typing-review): a calm process that grows with the
// typing heard, rather than the hero drawn big.

private val DeskInput.rushing: Boolean get() = heat > 0.5f

/** A falling streak, [len] cells up from ([x], [y]), fading upwards: drawn only when all of it is on the matrix, so a streak cut by the round edge never leaves a stray bit. */
private fun Cv.streak(x: Int, y: Int, len: Int, b: Float) {
    if ((0 until len).any { !n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x, y - it) }) return
    for (j in 0 until len) put(x, y - j, b * (1f - j * 0.2f))
}

/**
 * How much the scene has built up, in keys of steady typing: from its progress ([DeskInput.typed]), so a scene that
 * goes on after a short break (a new [Desk] is made) keeps what it built, as its act does.
 */
private val DeskInput.beats: Int get() = (typed / 500).toInt()

/**
 * Phoenix, raising the sun: a dark horizon, and each key the phoenix beats its wings and the sun climbs a little out of
 * the ground, so the sun rises as you type. A pause and the bird lands on the horizon and folds its wings. After 10 s
 * the sun is clear of the horizon and the sky round it brightens; after 20 s the phoenix circles the sun, a trail of
 * fire behind it.
 */
internal class PhoenixSunDesk : Desk() {
    private companion object {
        const val HORIZON = 18
        val UP = listOf("#.....#", ".#...#.", "..###..", "...#...")
        val DOWN = listOf("...#...", "..###..", ".#...#.", "#.....#")
        val SIT = listOf("..#..", ".###.", "#####")
    }

    private val trail = ArrayDeque<Pair<Int, Int>>()

    override fun draw(c: Cv, d: DeskInput) {
        // The sun, risen by how much has been typed.
        val rise = min(1f, d.beats / 40f)
        val sy = HORIZON + 4f - rise * 12f
        if (d.stage >= 1) for (y in 0 until HORIZON) for (x in 0..24) {
            val dd = hypot(x - 12f, y - sy)
            if (dd in 4f..9f) c.put(x, y, 0.07f * (1f - (dd - 4f) / 5f) * (if (d.stage == 1) d.stageIn(2500) else 1f))
        }
        c.disc(12f, sy, 3.6f, 0.75f)
        // The ground hides what has not risen yet.
        for (x in 0..24) { c.set(x, HORIZON, 0.25f); for (y in HORIZON + 1..24) c.set(x, y, 0.06f) }
        // The bird: flying over the sun (or round it, from the third act), landed in a pause.
        if (d.paused) {
            c.art(SIT, 16, HORIZON - 3, 0.8f)
            trail.clear()
            return
        }
        val (bx, by) = if (d.stage >= 2) {
            val a = d.t / 900f
            (12f + kotlin.math.cos(a) * 8f) to (sy + sin(a) * 6f)
        } else (12f + sin(d.t / 1700f) * 7f) to (4f + sin(d.t / 900f))
        val flap = d.hit(220) > 0f || (d.rushing && (d.t / 120) % 2 == 0L)
        val x0 = bx.roundToInt() - 3; val y0 = by.roundToInt() - 2
        if (d.stage >= 2) {
            trail.addLast(bx.roundToInt() to by.roundToInt())
            while (trail.size > 10) trail.removeFirst()
            for ((i, xy) in trail.withIndex()) c.put(xy.first, xy.second, 0.15f + 0.04f * i)
        }
        c.art(if (flap) DOWN else UP, x0, y0, 0.85f)
    }
}

/**
 * Kolobok on the road: the round bun, small, bowling along, and every key is a step on: the road, the trees beside it
 * and the hills behind go by, so the faster you type the faster it travels. A pause and it stops and smiles at you.
 * After 10 s the evening comes, the sun setting behind the hills; after 20 s it is night and the moon is up.
 */
internal class KolobokRoadDesk : Desk() {
    private var road = 0f
    private var seen = -1

    private fun ball(c: Cv, cx: Int, cy: Int, happy: Boolean, blink: Boolean) {
        val r = 4.5f
        for (y in cy - 6..cy + 6) for (x in cx - 6..cx + 6) if (hypot((x - cx).toFloat(), (y - cy).toFloat()) <= r + 1.2f) c.cut(x, y)
        for (y in cy - 5..cy + 5) for (x in cx - 5..cx + 5) {
            val ddx = (x - cx) / r; val ddy = (y - cy) / r; val q = ddx * ddx + ddy * ddy
            if (q > 1f) continue
            val z = kotlin.math.sqrt(1 - q)
            c.set(x, y, 0.22f + 0.78f * max(0f, -0.5f * ddx - 0.4f * ddy + 0.75f * z))
        }
        for (ex in listOf(cx - 2, cx + 1)) {
            if (happy || blink) { c.cut(ex, cy); c.cut(ex + 1, cy) }
            else { c.cut(ex, cy - 1); c.cut(ex + 1, cy - 1); c.cut(ex, cy); c.cut(ex + 1, cy) }
        }
        c.cut(cx, cy + 2); c.cut(cx - 1, cy + 2); c.cut(cx + 1, cy + 2)
        if (happy) { c.cut(cx - 2, cy + 1); c.cut(cx + 2, cy + 1) }
    }

    override fun draw(c: Cv, d: DeskInput) {
        if (d.n != seen && d.n >= 0) { seen = d.n; road += 1f }
        val evening = d.stage >= 1
        val night = d.stage >= 2
        // The sky: the sun going down behind the hills, then the moon.
        if (evening && !night) { val g = d.stageIn(3000); c.disc(18f, 7f + g * 2f, 2.6f, 0.55f) }
        if (night) { val g = d.stageIn(2500); c.disc(18f, 6f, 2.2f, 0.5f * g); c.hole(19f, 5.5f, 1.8f) }
        // Hills far off, going by slowly.
        for (x in 0..24) {
            val hh = (2f + 1.6f * sin((x + road * 0.3f) * 0.35f)).roundToInt()
            for (y in 15 - hh..15) c.set(x, y, 0.08f)
        }
        // Trees by the road, going by faster.
        for (k in 0..2) {
            val x = Math.floorMod(k * 10 - road.roundToInt(), 30) - 3
            c.art(listOf(".#.", "###", "###", "###", ".#.", ".#."), x, 12, if (night) 0.12f else 0.18f)
        }
        // The road and its dashes.
        for (x in 0..24) { c.set(x, 19, 0.25f); c.set(x, 22, (if (Math.floorMod(x + (road * 2).roundToInt(), 6) < 3) 0.3f else 0.12f)) }
        for (x in 0..24) for (y in 20..21) c.set(x, y, 0.06f)
        // The bun, bobbing a little as it rolls on with each key.
        val bob = if (d.hit(200) > 0f) -1 else 0
        ball(c, 8, 14 + bob, happy = d.paused || d.sending, blink = !d.paused && d.t % 3400 < 140)
    }
}

/**
 * Cactus, the rain garden: a small cloud drifts over the sand and each key it lets fall a drop. The drops add up:
 * every few of them a little cactus sprouts in the sand and grows, so the garden fills as you type. A pause and the
 * cloud stops raining. After 10 s the little ones flower;
 * after 20 s night comes, a moon rises and the flowers glow.
 */
internal class CactusGardenDesk : Desk() {
    private companion object {
        const val SAND = 21
        /** The sprouts in the order they come: column and full height. */
        val SPROUTS = listOf(12 to 5, 7 to 4, 17 to 4, 4 to 3, 20 to 3, 9 to 2, 15 to 2)
    }

    private var drops = 0

    private fun sprout(c: Cv, x: Int, h: Int, flower: Boolean, glow: Boolean) {
        for (j in 0 until h) c.set(x, SAND - 1 - j, 0.3f)
        if (h >= 3) { c.set(x - 1, SAND - 2, 0.3f); c.set(x - 1, SAND - 3, 0.3f) }
        if (h >= 4) { c.set(x + 1, SAND - 3, 0.3f); c.set(x + 1, SAND - 4, 0.3f) }
        if (flower) c.set(x, SAND - 1 - h, if (glow) 1f else 0.7f)
    }

    override fun draw(c: Cv, d: DeskInput) {
        drops = d.beats
        val night = d.stage >= 2
        if (night) { val g = d.stageIn(2500); c.disc(5f, 6f, 2f, 0.5f * g); c.hole(6f, 5.5f, 1.6f) }
        for (x in 0..24) { c.set(x, SAND, 0.2f); for (y in SAND + 1..24) c.set(x, y, 0.08f) }
        // The garden: one sprout every six drops, growing a row every few drops after it came.
        for ((i, sp) in SPROUTS.withIndex()) {
            val born = i * 5 + 1
            if (drops < born) continue
            val h = min(sp.second, 1 + (drops - born) / 3)
            sprout(c, sp.first, h, d.stage >= 1 && h == sp.second, night)
        }
        // The cloud, drifting slowly, and its drops.
        val cx = 12f + sin(d.t / 2600f) * 6f
        val cloud = if (d.paused) 0.18f else 0.3f
        c.art(listOf(".###..", "######", ".####."), (cx - 3).roundToInt(), 2, cloud)
        // Each key one drop, falling on its own as a short streak, from somewhere under the cloud.
        if (!d.paused) d.recent(1300) { k, a ->
            val x = (cx - 2 + h(k.n) * 4).roundToInt()
            val y = (8 + a / 85).toInt()
            if (y < SAND - 1) { for (j in 0..3) c.put(x, y - j, 0.65f - j * 0.13f) }
            else if (a < 1300) { c.put(x - 1, SAND - 1, 0.5f); c.put(x + 1, SAND - 1, 0.5f) }
        }

    }
}

/**
 * Jellyfish, the bloom: the jelly drifts low in the dark water, and each key it lets go of a tiny jelly that floats up
 * to join the others, so the sea fills with a drifting swarm of little jellies as you type. A pause and the little
 * ones dim and sink slowly. After 10 s every key sends a wave of light through the swarm, out from the jelly; after
 * 20 s the whole swarm pulses together with the typing.
 */
internal class JellyBloomDesk : Desk() {
    private companion object {
        /** Where the little ones settle, in the order they come. */
        val SPOTS = listOf(12 to 4, 6 to 7, 18 to 6, 9 to 11, 16 to 10, 3 to 12, 21 to 11, 12 to 8, 7 to 4, 17 to 3, 14 to 13, 4 to 8)
        const val MOTHER_Y = 18f
    }

    private fun little(c: Cv, x: Int, y: Int, b: Float) {
        c.set(x, y, b); c.set(x - 1, y + 1, b); c.set(x, y + 1, b); c.set(x + 1, y + 1, b)
        c.set(x - 1, y + 2, b * 0.6f); c.set(x + 1, y + 2, b * 0.6f)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val count = min(SPOTS.size, d.beats / 3)
        val p = d.pet
        val s = p.fit(7f)
        val mx = 12f + sin(d.t / 2600f) * 4f
        val sink = if (d.paused) min(3f, d.sinceKey / 1200f) else 0f
        for (i in 0 until count) {
            val (sx, sy) = SPOTS[i]
            val x = sx + (sin(d.t / 1700f + i) * 1.2f).roundToInt()
            val y = sy + (sin(d.t / 1300f + i * 2) * 0.8f + sink).roundToInt()
            var b = if (d.paused) 0.25f else 0.4f
            // The wave of light out from the jelly (from the second act): it reaches each little one in turn.
            if (d.stage >= 1 && d.last != null) {
                val reach = d.sinceKey / 1000f * 18f
                val dist = hypot(x - mx, y - MOTHER_Y)
                if (abs(reach - dist) < 2f) b = 0.95f
            }
            if (d.stage >= 2 && d.hit(160) > 0f) b = max(b, 0.8f)
            little(c, x, y, b)
        }
        // The newest little one on its way up from the jelly to its spot.
        val next = d.beats % 3
        if (count < SPOTS.size && d.last != null && d.sinceKey < 900) {
            val (sx, sy) = SPOTS[count]
            val q = d.sinceKey / 900f * (next + 1) / 3f
            little(c, mix(mx, sx.toFloat(), q).roundToInt(), mix(MOTHER_Y - 3, sy.toFloat(), q).roundToInt(), 0.5f)
        }
        val pulse = if (d.hit(180) > 0f) -1f else 0f
        p.draw(c, mx, p.onFeet(MOTHER_Y + 4f + pulse, s), scale = s, gain = if (d.paused) 0.7f else 1f, look = if (d.paused) Look.HAPPY else Look.CALM)
    }
}

/**
 * Ghost, candles in the graveyard: while you type the candles on the stones light one by one, flickering with the
 * keys, so the graveyard fills with little flames; the ghost keeps out of sight. A pause and it floats up over the
 * stones and hangs there, smiling at you. After 10 s mist creeps along the ground; after 20 s every stone has two
 * candles. The ghost is drawn by hand: two dark eyes and a small mouth, a wavy hem.
 */
internal class GhostCandlesDesk : Desk() {
    private companion object {
        const val GROUND = 21
        /** The stones: their column and shape (rows, '#'), apart from one another and in from the matrix's edge. */
        val STONES = listOf(
            5 to listOf(".#.", "###", "###", "###"),
            10 to listOf(".#.", "###", ".#.", ".#.", ".#."),
            15 to listOf("###", "###", "###"),
            20 to listOf(".#.", "###", "###", "###", "###"),
        )
        val ORDER = listOf(1, 3, 0, 2)
        val BODY = listOf("..###..", ".#####.", "#######", "#######", "#######", "#######", "#######", "#.#.#.#")
    }

    /** The ghost with its middle column at [x] and its top row at [top]: eyes either side of the middle, mouth under it. */
    private fun ghost(c: Cv, x: Int, top: Int, b: Float) {
        BODY.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.set(x - 3 + i, top + j, b) } }
        c.cut(x - 2, top + 3); c.cut(x + 2, top + 3)
        c.cut(x, top + 5)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val lit = min(STONES.size * 2, d.beats / 3)
        for (x in 0..24) { c.set(x, GROUND, 0.2f); for (y in GROUND + 1..24) c.set(x, y, 0.07f) }
        if (d.stage >= 1) {
            val g = if (d.stage == 1) d.stageIn(3000) else 1f
            for (x in 0..24) if (sin(x * 0.5f + d.t / 900f) > -0.3f) c.put(x, GROUND - 1, 0.1f * g)
        }
        if (d.paused) {
            val rise = min(1f, (d.sinceKey - Typing.PAUSE_MS) / 600f)
            ghost(c, 12, (13 - rise * 7 + sin(d.t / 700f)).roundToInt(), 0.36f)
        }
        for ((i, st) in STONES.withIndex()) {
            val (x, rows) = st
            val top = GROUND - rows.size
            rows.forEachIndexed { j, r -> r.forEachIndexed { k, ch -> if (ch == '#') c.set(x - 1 + k, top + j, 0.3f) } }
            val flick = d.hit(150) > 0f && (d.n + i) % 2 == 0
            if (ORDER.indexOf(i) < lit) {
                c.set(x, top - 1, 0.45f)
                c.set(x, top - 2, if (flick || (d.t / 140 + i * 3) % 7 == 0L) 0.65f else 1f)
            }
            // The second candle (third act), at the foot of the stone.
            if (ORDER.indexOf(i) + STONES.size < lit && d.stage >= 2) {
                c.set(x + 2, GROUND - 1, 0.45f)
                c.set(x + 2, GROUND - 2, if (flick) 0.65f else 0.9f)
            }
        }
    }
}

/**
 * The fish in its jar, fed by the keys: each key a flake of food drops onto the water and sinks, and the fish swims up
 * and snaps it; a rush is a shower of flakes. A pause and it waits at the surface, mouth up. After 10 s weed grows in
 * the jar; after 20 s a little fish joins it at the food.
 */
internal class FishjarFeedDesk : Desk() {
    private companion object {
        const val SURFACE = 8
        val FISH = listOf("#...#####..", ".#.#######.", "..######-##", ".#.#######.", "#...#####..")
        val BABY = listOf("#.###.", ".##-##", "#.###.")
    }

    private var fx = 12f
    private var fy = 18f
    private var right = true
    private var baby = 8f

    private fun fish(c: Cv, rows: List<String>, x: Float, y: Float, toRight: Boolean, b: Float) {
        val r = if (toRight) rows else rows.map { it.reversed() }
        val left = (x - r[0].length / 2f).roundToInt(); val top = (y - r.size / 2).roundToInt()
        r.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(left + i, top + j, b); '-' -> c.cut(left + i, top + j) } } }
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (y in SURFACE + 1..24) for (x in 0..24) c.set(x, y, 0.1f)
        for (x in 0..24) c.set(x, SURFACE + if ((x + d.t / 300) % 6 == 0L) 1 else 0, 0.3f)
        if (d.stage >= 1) {
            val g = if (d.stage == 1) d.stageIn(3000) else 1f
            for ((x0, h) in listOf(4 to 6, 19 to 5)) for (j in 0 until (h * g).roundToInt()) c.set(x0 + if ((j + d.t / 600) % 4 < 2) 0 else 1, 24 - j, 0.25f)
        }
        // Flakes: the young ones sinking; the fish heads for the newest.
        var target: Pair<Float, Float>? = null
        d.recent(2200) { k, a ->
            val x = 5f + h(k.n) * 15f
            // It lands on the water and sinks slowly.
            val y = SURFACE + 1f + a / 260f
            val eaten = abs(fx + (if (right) 4 else -4) - x) < 2f && abs(fy - y) < 2f
            if (!eaten && y < 23f) { c.set(x.roundToInt(), y.roundToInt(), 0.9f); c.set(x.roundToInt() + 1, y.roundToInt(), 0.6f); target = x to y }
            if (a < 200) { c.set(x.roundToInt() - 1, SURFACE, 0.7f); c.set(x.roundToInt() + 2, SURFACE, 0.7f) }
        }
        val (tx, ty) = when {
            d.paused -> 12f to SURFACE + 3f
            target != null -> target!!
            else -> (12f + sin(d.t / 1500f) * 6f) to 19f
        }
        val sp = d.dtMs / 1000f * (if (d.rushing) 9f else 6f)
        if (abs(tx - fx) > 0.5f) { right = tx > fx; fx += (tx - fx).coerceIn(-sp, sp) }
        fy += (ty - fy).coerceIn(-sp, sp)
        fy = fy.coerceIn(SURFACE + 3f, 21f)
        fish(c, FISH, fx, fy, right, 0.6f)
        // Waiting at the top, mouth up: a bubble now and then, rising out of its mouth to the surface.
        if (d.paused && d.t % 1200 < 600) { val bx = (fx + if (right) 5 else -5).roundToInt(); c.set(bx, (fy - 2).roundToInt(), 0.45f); c.set(bx, (fy - 3).roundToInt(), 0.45f) }
        if (d.stage >= 2) {
            baby += ((fx - 6f * (if (right) 1 else -1)) - baby) * 0.05f
            fish(c, BABY, baby, min(22f, fy + 4f), right, 0.5f * d.stageIn(1200))
        }
    }
}

/**
 * Cloud, filling the pond: the cloud floats over a pond and each key it lets fall a drop; every drop raises the water a
 * little, so the pond fills as you type. A pause and the rain stops, the cloud smiles and the water lies still. After
 * 10 s a rush brings lightning; after 20 s the sun breaks through and a rainbow arches over the full pond.
 */
internal class CloudPondDesk : Desk() {
    private var drops = 0

    override fun draw(c: Cv, d: DeskInput) {
        drops = d.beats
        val level = 23 - min(5, drops / 8)
        if (d.stage >= 2) {
            val g = d.stageIn(3000)
            // A rainbow of two bands, standing on the water well inside the matrix's edge.
            for (k in 0..1) { val r = 5f + k; for (i in 0..50) { val an = PIF * i / 50f; c.put(12f + kotlin.math.cos(an) * r, level - 1f - sin(an) * r * 0.9f, (0.25f + 0.2f * k) * g) } }
            c.disc(20f, 4f, 2f, 0.7f * g)
        }
        // The pond: its surface, rippling where a drop just fell, and the water under it.
        for (x in 0..24) { c.set(x, level, 0.3f); for (y in level + 1..24) c.set(x, y, 0.09f) }
        val p = d.pet
        val s = p.fit(8f)
        val cx = 12f + sin(d.t / 3000f) * 3f
        p.draw(c, cx, 6f, scale = s, look = when { d.paused -> Look.HAPPY; d.rushing -> Look.FOCUS; else -> Look.CALM })
        if (!d.paused) d.recent(900) { k, a ->
            val x = (cx - 4 + h(k.n) * 8).roundToInt()
            val y = (13 + a / 60).toInt()
            if (y < level) { for (j in 0..3) c.put(x, y - j, 0.7f - j * 0.15f) }
            else if (y < level + 4) { c.put(x - 1, level - 1, 0.5f); c.put(x + 1, level - 1, 0.5f) }
        }
        // Lightning in a rush, from the second act on.
        if (d.stage >= 1 && d.rushing && d.n % 6 == 0 && d.sinceKey < 110) {
            c.line(cx, 10f, cx - 2f, 14f, 1f); c.line(cx - 2f, 14f, cx + 1f, 14f, 1f); c.line(cx + 1f, 14f, cx - 1f, level - 1f, 1f)
        }
    }
}

/**
 * Robot, the workshop: the robot stands on the left at its control box, and on the right a crane builds a little robot
 * on the bench, a part with each key: two feet, legs, the body, the arms, the head, then its eyes light up and its
 * antenna blinks. Finished, it waves and walks off the bench to the right, and the crane starts the next one. Each key
 * the crane swings a part down on its hook and the robot presses its button (it bobs). A pause: the crane stops, the
 * little robot looks round at you. From 10 s a lamp lights on the crane rail for every robot finished; after 20 s the
 * lamps blink with the keys.
 */
internal class BotWorkshopDesk : Desk() {
    private companion object {
        const val FLOOR = 21
        const val BX = 17
        /** The little robot's parts, bottom up: each a few cells relative to its middle column and the floor. */
        val PARTS: List<List<Pair<Int, Int>>> = listOf(
            listOf(-2 to 0, -1 to 0, 1 to 0, 2 to 0),
            listOf(-1 to -1, 1 to -1, -1 to -2, 1 to -2),
            listOf(-2 to -3, -1 to -3, 0 to -3, 1 to -3, 2 to -3, -2 to -4, -1 to -4, 0 to -4, 1 to -4, 2 to -4, -2 to -5, -1 to -5, 0 to -5, 1 to -5, 2 to -5),
            listOf(-3 to -4, -4 to -4, -4 to -5, 3 to -4, 4 to -4, 4 to -5),
            listOf(-2 to -6, -1 to -6, 0 to -6, 1 to -6, 2 to -6, -2 to -7, -1 to -7, 0 to -7, 1 to -7, 2 to -7, -2 to -8, -1 to -8, 0 to -8, 1 to -8, 2 to -8),
            listOf(0 to -9, 0 to -10),
        )
        /** Keys per finished robot: a part every three keys, then a few for it to light up and walk off. */
        val PER = PARTS.size * 3 + 6
    }

    override fun draw(c: Cv, d: DeskInput) {
        val beat = d.beats
        val built = beat / PER
        val inThis = beat % PER
        val parts = min(PARTS.size, inThis / 3)
        val done = parts == PARTS.size
        val walk = if (done) ((inThis - PARTS.size * 3) - 2).coerceAtLeast(0) else 0
        // The floor and the bench.
        for (x in 0..24) c.set(x, FLOOR + 1, 0.2f)
        for (x in BX - 4..BX + 4) c.set(x, FLOOR + 1, 0.32f)
        // A lamp hangs from the crane rail for every robot finished (from the second act); in the third they blink with the keys.
        if (d.stage >= 1) for (k in 0 until min(5, built)) {
            val x = BX - 6 + k * 2
            val on = if (d.stage >= 2) d.hit(200) > 0f || (d.t / 400 + k) % 3 != 0L else true
            c.set(x, 4, 0.3f); c.set(x, 5, if (on) 0.9f else 0.35f)
        }
        // The little robot on the bench, walking off to the right when done.
        val ox = BX + walk * 2
        for (i in 0 until parts) for ((dx, dy) in PARTS[i]) c.set(ox + dx, FLOOR + dy, 0.36f)
        if (done) {
            // Its eyes light, its antenna blinks; in a pause it looks at you (eyes in the middle).
            val look = if (d.paused) 0 else if ((d.t / 700) % 2 == 0L) -1 else 0
            c.cut(ox - 1 + look, FLOOR - 7); c.cut(ox + 1 + look, FLOOR - 7)
            c.set(ox, FLOOR - 11, if ((d.t / 300) % 2 == 0L) 1f else 0.4f)
            if (d.hit(240) > 0f) { c.set(ox + 4, FLOOR - 6, 0.36f); c.set(ox + 5, FLOOR - 7, 0.36f) }
        }
        // The crane: a rail along the top, a trolley over the bench, the hook coming down with the next part.
        for (x in BX - 6..BX + 4) c.set(x, 3, 0.25f)
        if (!done) {
            val hit = d.hit(400)
            val low = FLOOR - (if (parts < PARTS.size) PARTS[parts].minOf { it.second } else -11) - 3
            val hy = if (d.paused) 5 else mix(5f, low.toFloat(), hit).roundToInt()
            c.set(BX, 4, 0.4f)
            for (y in 5..hy) c.set(BX, y, 0.22f)
            c.set(BX - 1, hy, 0.3f); c.set(BX + 1, hy, 0.3f)
            if (hit > 0f && parts < PARTS.size) for ((dx, dy) in PARTS[parts]) {
                val y = hy + 1 + (dy - PARTS[parts].minOf { it.second })
                c.set(BX + dx, y, 0.6f)
            }
        }
        // The robot at its control box on the left, pressing the button with each key.
        val p = d.pet
        val s = p.fit(8f)
        val bob = if (d.hit(160) > 0f) 1f else 0f
        c.block(9, FLOOR - 3, 11, FLOOR, 0.25f)
        c.set(10, FLOOR - 4, if (d.hit(160) > 0f) 1f else 0.5f)
        p.draw(c, 6f, p.onFeet(FLOOR.toFloat() + bob, s), scale = s, look = if (d.paused) Look.HAPPY else Look.FOCUS)
    }
}

/**
 * Dino, the dig: a bank of sand hides a dinosaur's skeleton, and each key a brush sweeps across the sand and clears a
 * little more of it, the bones coming out one by one. After 10 s the skeleton is free: it shakes off the last sand and
 * stands up, and from then on it walks on the spot with the typing (legs stepping, tail swinging, head nodding), faster
 * for faster keys. A pause and it stops and turns its head to you. After 20 s it roars with each sent message, and its
 * bones glow in a wave with the keys.
 */
internal class DinoDigDesk : Desk() {
    private companion object {
        const val X0 = 3
        const val Y0 = 8
        /** The skeleton without legs, side on, facing right: '#' bone, '-' the eye socket. */
        val BODY = listOf(
            "...............####.",
            "..............######",
            "..............##-###",
            ".......#.#.#.###....",
            "...############.....",
            "..#.#.#.#.#.#.......",
            ".#..................",
            "#...................",
        )
        /** Two legs, front and back, in two steps: the cells relative to the hip, for step 0 and step 1. */
        val LEG_A = listOf(listOf(0 to 0, 0 to 1, 0 to 2, -1 to 3, 0 to 3), listOf(0 to 0, 1 to 1, 1 to 2, 1 to 3, 2 to 3))
        val LEG_B = listOf(listOf(0 to 0, 1 to 1, 1 to 2, 1 to 3, 2 to 3), listOf(0 to 0, 0 to 1, 0 to 2, -1 to 3, 0 to 3))
        val HIPS = listOf(6 to 13, 11 to 13)
        val PATCHES: List<Pair<Int, Int>> = buildList { for (row in 0..2) for (k in 0..6) add((if (row % 2 == 0) k else 6 - k) * 3 + 1 to Y0 + row * 3) }
    }

    private var stepT = 0f

    override fun draw(c: Cv, d: DeskInput) {
        val free = d.stage >= 1
        val cleared = if (free) PATCHES.size else min(PATCHES.size, d.beats / 1)
        val open = HashSet<Int>()
        for (i in 0 until cleared) { val (px, py) = PATCHES[i]; for (yy in py - 1..py + 2) for (xx in px - 1..px + 2) open += yy * 25 + xx }
        // Walking: steps with the keys once it is free (faster typing, faster steps).
        if (free && !d.paused) stepT += d.dtMs / 1000f * (if (d.rushing) 6f else if (d.rate >= Typing.FLOW_RATE) 4f else 2f) * (if (d.sinceKey < 600) 1f else 0f)
        val step = stepT.toInt() % 2
        val bob = if (free && step == 1) -1 else 0
        val stand = if (free) (d.stageIn(800) * 0f).roundToInt() else 0
        for (x in 0..24) c.set(x, 22, 0.22f)
        // The sand where it is not brushed away yet (all gone once it is free).
        if (!free) for (y in Y0 - 1..21) for (x in 0..24) if (y * 25 + x !in open) c.set(x, y, 0.13f)
        val wave = if (d.stage >= 2 && d.last != null) (d.sinceKey / 30).toInt() else -99
        fun bone(x: Int, y: Int, i: Int): Float = if (abs((BODY[0].length - i) - wave) < 2) 1f else if (free) 0.6f else 0.45f
        // The body, its head nodding (looking at you in a pause) and its tail swinging with the steps.
        BODY.forEachIndexed { j, r -> r.forEachIndexed { i, ch ->
            val head = i >= 13
            val x = X0 + i + (if (!head && i < 3 && free) step else 0)
            val y = Y0 + j + bob + stand + (if (head && free && !d.paused && step == 0) 1 else 0)
            if (!free && y * 25 + x !in open) return@forEachIndexed
            when (ch) { '#' -> c.set(x, y, bone(x, y, i)); '-' -> c.cut(x, y) }
        } }
        if (d.paused && free) c.set(X0 + 16, Y0 + 2 + bob, 0.6f)
        // The legs, stepping.
        for ((k, hip) in HIPS.withIndex()) {
            val leg = (if (k == 0) LEG_A else LEG_B)[if (free) step else 0]
            for ((dx, dy) in leg) {
                val x = X0 + hip.first + dx; val y = hip.second + dy + bob
                if (!free && y * 25 + x !in open) continue
                c.set(x, y, if (free) 0.6f else 0.45f)
            }
        }
        // The brush, sweeping across the patch being cleared, with each key.
        if (!free && !d.paused && cleared < PATCHES.size) {
            val (px, py) = PATCHES[cleared]
            val sweep = ((d.sinceKey / 60) % 4).toInt()
            val bx = px - 1 + sweep
            c.set(bx, py - 1, 0.8f); c.set(bx, py - 2, 0.5f); c.set(bx + 1, py - 2, 0.5f); c.set(bx - 1, py - 2, 0.5f)
        }
        // A roar for a sent message (third act): the jaw drops, a few lines out of the mouth.
        if (d.stage >= 2 && d.sending) { c.cut(X0 + 18, Y0 + 2 + bob); c.line(X0 + 20f, Y0 + 1f, X0 + 22f, Y0 - 1f, 0.6f); c.line(X0 + 20f, Y0 + 3f, X0 + 22f, Y0 + 4f, 0.6f) }
    }
}

/**
 * Mushroom, the choir: a row of little mushrooms with faces stands in the moss, and each key one of them sings: it
 * bobs down, opens its mouth and its cap lights, left to right and round again, so the typing is their song. The choir
 * grows as you type, one more singer every few keys, up to three. A pause and they all close their eyes and sway. After
 * 10 s a sung note floats up from each singer; after 20 s the caps glow softly between the notes too.
 */
internal class ShroomChoirDesk : Desk() {
    private companion object {
        const val MOSS = 21
        /** The singers: column and cap width (odd); the middle one the biggest. */
        val SINGERS = listOf(12 to 7, 4 to 7, 20 to 7)
    }

    private fun singer(c: Cv, x: Int, w: Int, sing: Boolean, asleep: Boolean, glow: Float) {
        val stemH = if (x == 12) 7 else 5
        val down = if (sing) 1 else 0
        val top = MOSS - stemH - 3 + down
        // Cap: a dome of three rows; lit when it sings.
        val cap = if (sing) 0.8f else 0.35f + glow
        for (i in -(w / 2 - 2)..(w / 2 - 2)) c.set(x + i, top, cap)
        for (i in -(w / 2 - 1)..(w / 2 - 1)) c.set(x + i, top + 1, cap)
        for (i in -(w / 2)..(w / 2)) c.set(x + i, top + 2, cap)
        // Stem with a face: eyes, and a mouth that opens to sing.
        val sw = 1
        for (y in top + 3..MOSS - 1) for (i in -sw..sw) c.set(x + i, y, 0.3f)
        if (sw == 1) {
            val ey = top + 4
            if (asleep) { c.cut(x - 1, ey + 1); c.cut(x + 1, ey + 1) } else { c.cut(x - 1, ey); c.cut(x + 1, ey) }
            if (sing) { c.cut(x, ey + 2); c.cut(x, ey + 3) } else c.cut(x, ey + 2)
        }
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) { c.set(x, MOSS, 0.22f); for (y in MOSS + 1..24) c.set(x, y, 0.07f) }
        val n = min(SINGERS.size, 1 + d.beats / 8)
        val who = if (d.last != null) d.n % n else -1
        val sway = if (d.paused) (sin(d.t / 600f) * 1f).roundToInt() else 0
        for (i in 0 until n) {
            val (x, w) = SINGERS[i]
            val sing = i == who && d.hit(260) > 0f && !d.paused
            singer(c, x + sway, w, sing, d.paused, if (d.stage >= 2) 0.12f else 0f)
        }
        // A note floating up from the one that just sang (from the second act).
        if (d.stage >= 1 && who >= 0 && !d.paused && d.sinceKey < 900) {
            val (x, w) = SINGERS[who]
            val y = (MOSS - (if (x == 12) 13 else 11) - d.sinceKey / 150).toInt()
            c.set(x + 1, y, 0.7f); c.set(x + 1, y + 1, 0.7f); c.set(x, y + 2, 0.7f); c.set(x + 1, y + 2, 0.7f)
        }
    }
}

/**
 * Fox, the lanterns: a night, a hill, and the fox sitting on it, small, drawn by hand, its tail curled up. Each key a
 * paper lantern lifts off from its tail and floats slowly up into the sky, swaying, and stays there, so the sky fills
 * with lanterns as you type. A pause and the fox looks up at them. After 10 s the lanterns glow warmer, brighter at
 * every key; after 20 s the moon rises behind the hill.
 */
internal class FoxLanternsDesk : Desk() {
    private companion object {
        /** The fox side on, facing right: '#' body, '-' the eye. Its tail tip is at (0, 2). */
        /** 'o' the white tip of its brush. */
        val FOX = listOf(
            "o.......#..#.",
            "##......####.",
            "##......#-###",
            "###....####..",
            ".###.#####...",
            "..########...",
            "...#.#..#.#..",
        )
        val FOX_UP = listOf(
            "o.......#..#.",
            "##......#-##.",
            "##......#####",
            "###....####..",
            ".###.#####...",
            "..########...",
            "...#.#..#.#..",
        )
        /** Where the lanterns end up, spread over the sky, in the order they rise. */
        val SKY = listOf(13 to 4, 18 to 7, 9 to 7, 15 to 10, 21 to 11, 11 to 11, 6 to 11, 9 to 3)
    }

    private fun lantern(c: Cv, x: Int, y: Int, b: Float) {
        c.set(x, y, b); c.set(x + 1, y, b); c.set(x, y + 1, b * 0.7f); c.set(x + 1, y + 1, b * 0.7f); c.set(x, y + 2, b); c.set(x + 1, y + 2, b)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val fx = 2; val fy = 15
        if (d.stage >= 2) { val g = d.stageIn(3000); c.disc(5f, 7f - g * 2f, 2.2f, 0.45f * g) }
        // The hill.
        for (x in 0..24) { val top = (19 + (x - 7) * (x - 7) / 40f).roundToInt(); for (y in top..24) c.set(x, y, if (y == top) 0.25f else 0.07f) }
        val count = min(SKY.size, d.beats / 4)
        val warm = d.stage >= 1
        for (i in 0 until count) {
            val (sx, sy) = SKY[i]
            val x = sx + (sin(d.t / 1500f + i) * 0.8f).roundToInt()
            val y = sy + (sin(d.t / 1100f + i * 2) * 0.6f).roundToInt()
            val b = if (warm && d.hit(220) > 0f) 0.9f else if (warm) 0.6f else 0.45f
            lantern(c, x, y, b)
        }
        // The newest lantern on its way up from the tail to its place.
        if (count < SKY.size && d.last != null && !d.paused) {
            val q = min(1f, d.sinceKey / 1400f)
            val (sx, sy) = SKY[count]
            lantern(c, mix(fx + 1f, sx.toFloat(), q).roundToInt(), mix(fy - 2f, sy.toFloat(), q).roundToInt(), 0.7f)
        }
        val art = if (d.paused) FOX_UP else FOX
        art.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(fx + i, fy - 3 + j, 0.4f); 'o' -> c.set(fx + i, fy - 3 + j, 0.9f); '-' -> c.cut(fx + i, fy - 3 + j) } } }
        // Its tail flicks up with each key, sending the lantern off.
        if (d.hit(200) > 0f) c.set(fx, fy - 4, 0.9f)
    }
}

/**
 * Dandelion, the meadow: a meadow at dusk, and each key a seed floats in on its little parachute from the left and
 * settles in the grass; every few seeds a new dandelion grows where they came down, a stem and then a round puff, so
 * the meadow fills with them as you type. A pause and the breeze drops and the puffs stand still. After 10 s the
 * puffs open wider; after 20 s they sway together with the keys.
 */
internal class DandelionMeadowDesk : Desk() {
    private companion object {
        const val GRASS = 20
        /** Where the dandelions grow, in order: column and height of the stem. */
        val SPOTS = listOf(12 to 8, 6 to 5, 18 to 6, 9 to 2, 15 to 3)
    }

    private fun puff(c: Cv, x: Int, top: Int, wide: Boolean, b: Float) {
        // A round puff: lit ring, dimmer heart; wider from the second act.
        val r = if (wide) listOf(".#.", "#+#", ".#.") else listOf("#")
        val w = r[0].length
        r.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            when (ch) { '#' -> c.set(x - w / 2 + i, top - r.size + 1 + j, b); '+' -> c.set(x - w / 2 + i, top - r.size + 1 + j, b * 0.4f) }
        } }
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) { c.set(x, GRASS, 0.22f); for (y in GRASS + 1..24) c.set(x, y, 0.07f) }
        val grown = d.beats
        val sway = if (d.stage >= 2 && d.hit(250) > 0f) 1 else 0
        for ((i, sp) in SPOTS.withIndex()) {
            val born = i * 8 + 3
            if (grown < born) continue
            val (x, h) = sp
            val tall = min(h, 1 + (grown - born) / 2)
            for (j in 1..tall) c.set(x + (if (j > tall / 2) sway else 0), GRASS - j, 0.3f)
            if (tall == h) puff(c, x + sway, GRASS - h - 1, d.stage >= 1, 0.55f)
        }
        // Seeds in flight: in from the left, drifting down to the grass.
        if (!d.paused) d.recent(1800) { k, a ->
            val q = a / 1800f
            val x = (3 + q * (6 + h(k.n) * 12)).roundToInt()
            val y = (4 + h(k.n, 1) * 4 + q * 12 + sin(q * 9f) * 1.2f).roundToInt()
            if (y < GRASS - 1) { c.put(x - 1, y, 0.5f); c.put(x + 1, y, 0.5f); c.put(x, y + 1, 0.5f); c.put(x, y + 2, 0.3f) }
        }
    }
}

/**
 * Slime, the drip: a drip from the rock above lets fall a drop with each key, and the slime under it catches it and
 * grows a size, wobbling as it lands; grown big it splits off a little one, which hops to the side, so a family of
 * slimes gathers as you type. A pause and they all shut their eyes. After 10 s the family hops in turn with the keys;
 * after 20 s they all bounce together on every key.
 */
internal class SlimeDripDesk : Desk() {
    private companion object {
        const val FLOOR = 20
        /** Where the little ones sit once split off. */
        val FAMILY = listOf(4, 20)
    }

    /** A slime of [w] columns (odd), its middle at [x]: a dome (full rows, the top one two cells narrower), squashed flatter and wider for a moment when a drop lands. */
    private fun slime(c: Cv, x: Int, w: Int, squash: Boolean, asleep: Boolean, b: Float, up: Int = 0) {
        val ww = if (squash) w + 2 else w
        val h = if (squash) w / 2 else w / 2 + 1
        val top = FLOOR - h - up
        for (j in 0 until h) {
            val y = top + j
            val half = if (j == 0) ww / 2 - 1 else ww / 2
            for (i in -half..half) c.set(x + i, y, b)
        }
        val ey = top + 1
        val ex = if (ww >= 7) 2 else 1
        if (asleep) { c.cut(x - ex, ey + 1); c.cut(x + ex, ey + 1) } else { c.cut(x - ex, ey); c.cut(x + ex, ey) }
        if (w >= 7 && h >= 4) c.cut(x, ey + 2)
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) { c.set(x, FLOOR, 0.22f); for (y in FLOOR + 1..24) c.set(x, y, 0.07f) }
        // The rock and its drip.
        c.art(listOf("#######", ".#####.", "..###..", "...#..."), 9, 0, 0.25f)
        val beat = d.beats
        val family = min(FAMILY.size, beat / 8)
        val size = min(2, (beat % 8) / 3)
        val w = 5 + size * 2
        // The drop in flight (a key just now), landing on the slime.
        val fall = 260L
        if (d.last != null && d.sinceKey < fall && !d.paused) {
            val y = (4 + (FLOOR - w / 2 - 6) * d.sinceKey / fall.toFloat()).roundToInt()
            c.set(12, y, 0.7f); c.set(12, y + 1, 0.7f); c.set(11, y + 1, 0.5f); c.set(13, y + 1, 0.5f)
        }
        val landed = d.last != null && d.sinceKey in fall until fall + 140
        slime(c, 12, w, landed, d.paused, 0.38f)
        for (k in 0 until family) {
            val hopTurn = d.stage >= 1 && d.last != null && d.n % max(1, family) == k && d.hit(220) > 0f
            val hopAll = d.stage >= 2 && d.hit(220) > 0f
            slime(c, FAMILY[k], 5, false, d.paused, if (hopAll) 0.6f else 0.34f, up = if (hopTurn || hopAll) 2 else 0)
        }
    }
}

/**
 * UFO, the crop circle: a wheat field seen from above, and the saucer hovering over it flattens the crop as it goes,
 * a few more cells with each key, round and round in rings, so a crop circle is drawn in the field as you type. A
 * pause and the saucer hangs still in the middle, its lights turning slowly. After 10 s the pattern is finished and
 * its lines glow with the keys; after 20 s the saucer's lights chase round and the rings light up one after another.
 */
internal class UfoCropDesk : Desk() {
    private companion object {
        /** The pattern, cell by cell in the order it is flattened: the middle, then rings outwards, then the spokes. */
        val PATTERN: List<Pair<Int, Int>> = buildList {
            val seen = HashSet<Int>()
            fun ring(r: Float) {
                for (i in 0 until 90) {
                    val a = i * 2 * PIF / 90
                    val x = (12 + kotlin.math.cos(a) * r).roundToInt(); val y = (12 + sin(a) * r).roundToInt()
                    if (seen.add(y * 25 + x)) add(x to y)
                }
            }
            for (yy in 10..14) for (xx in 10..14) if (hypot(xx - 12f, yy - 12f) <= 2.2f && seen.add(yy * 25 + xx)) add(xx to yy)
            ring(5f); ring(8.5f)
            for (k in 0 until 4) for (r in 6..8) {
                val a = PIF / 4 + k * PIF / 2
                val x = (12 + kotlin.math.cos(a) * r).roundToInt(); val y = (12 + sin(a) * r).roundToInt()
                if (seen.add(y * 25 + x)) add(x to y)
            }
        }
    }

    override fun draw(c: Cv, d: DeskInput) {
        val done = min(PATTERN.size, d.beats * 2)
        val flat = HashSet<Int>()
        for (i in 0 until done) flat += PATTERN[i].second * 25 + PATTERN[i].first
        // The field: rows of wheat, faintly striped, dark where it lies flat.
        val glow = d.stage >= 1 && d.hit(240) > 0f
        for (y in 0..24) for (x in 0..24) {
            if (y * 25 + x in flat) { if (glow) c.set(x, y, 0.3f); continue }
            c.set(x, y, if ((x + y) % 3 == 0) 0.16f else 0.1f)
        }
        // The saucer: over the cell being flattened, or still in the middle in a pause.
        val (sx, sy) = if (d.paused || done >= PATTERN.size) 12 to 12 else PATTERN[done]
        val turn = (d.t / (if (d.paused) 500 else 150)).toInt()
        c.disc(sx.toFloat(), sy.toFloat(), 1.6f, 0.0f)
        for (yy in sy - 2..sy + 2) for (xx in sx - 2..sx + 2) {
            val dd = hypot(xx - sx.toFloat(), yy - sy.toFloat())
            if (dd <= 1.5f) c.set(xx, yy, if (dd < 0.5f) 0.8f else 0.5f) else if (dd <= 2.6f) c.set(xx, yy, 0f)
        }
        val lights = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)
        for ((k, l) in lights.withIndex()) c.set(sx + l.first, sy + l.second, if ((turn + k) % 4 == 0 || d.stage >= 2 && (turn + k) % 2 == 0) 1f else 0.35f)
    }
}

/**
 * Penguin, the igloo (the penguin itself stays out of it): a dome of snow blocks rises on the ice, a block with each
 * key: it slides in from the side and settles in its place, the dome closing round from the left. It is the old
 * igloo's look: blocks with brighter seams and a bright rim. A pause and the building stops. After 10 s the igloo is
 * finished: its doorway glows, brighter with each key; after 20 s the northern lights
 * wave over it.
 */
internal class PenguinIglooDesk : Desk() {
    private companion object {
        const val BASE = 20
        const val CX = 12f
        const val R = 7f
    }

    private val smoke = n7.glyphpet.pet.drawn.Fire.field()

    /** The angle (0 at the left, PI at the right) up to which the dome is built. */
    private fun upTo(built: Float) = PIF * (0.06f + 0.94f * built)

    override fun draw(c: Cv, d: DeskInput) {
        if (d.stage >= 2) {
            val g = d.stageIn(3000)
            for (x in 0..24) for (k in 0..1) c.put(x, (3 + k * 2 + sin(x * 0.4f + d.t / 700f + k) * 1.5f).roundToInt(), (0.12f + 0.05f * k) * g)
        }
        for (x in 0..24) { c.set(x, BASE, 0.25f); for (y in BASE + 1..24) c.set(x, y, 0.06f) }
        val built = if (d.stage >= 1) 1f else min(1f, d.beats / 36f)
        val reach = upTo(built)
        // The dome: blocks, their seams and the rim brighter (the old igloo's look).
        for (y in 0..BASE - 1) for (x in 0..24) {
            val dx = x - CX; val dy = BASE - y.toFloat()
            val dist = hypot(dx, dy)
            if (dist > R) continue
            val ang = PIF - kotlin.math.atan2(dy, dx)
            if (ang > reach) continue
            val seam = (y % 2 == 0 && (x + y / 2) % 3 == 0) || abs(dist - R) < 0.6f
            c.set(x, y, if (seam) 0.4f else 0.2f)
        }
        // The block of the last key, sliding in from the side to the edge of what is built.
        if (built < 1f && d.last != null && d.sinceKey < 400 && !d.paused) {
            val q = d.sinceKey / 400f
            val tx = CX + kotlin.math.cos(PIF - reach) * (R - 1); val ty = BASE - sin(PIF - reach) * (R - 1)
            val fromRight = reach > PIF / 2
            val sx = if (fromRight) 20f else 3f
            val x = mix(sx, tx, q).roundToInt(); val y = (mix(BASE - 1f, ty, q) - sin(q * PIF) * 3f).roundToInt()
            c.block(x, y, x + 1, y + 1, 0.7f)
        }
        // Finished: the doorway lit from inside, smoke from the top.
        if (built >= 1f) {
            val glow = 0.3f + 0.5f * d.hit(300) + 0.2f * d.heat
            for (y in BASE - 2..BASE - 1) for (x in 11..13) c.set(x, y, glow)
            c.set(12, BASE - 3, glow)
        }
    }
}

/**
 * Crab, the sandcastle: on the beach the crab flings sand with each key, claws up, and the castle beside it grows:
 * first its wall, then two towers with battlements, then the keep between them. A pause and the crab sits back and
 * waves a claw. After 10 s a flag goes up on the keep; after 20 s the waves come in and out at the foot of the castle,
 * with the keys.
 */
internal class CrabCastleDesk : Desk() {
    private companion object {
        const val SAND = 20
        /** The castle, cell by cell bottom up: wall, towers, battlements, keep. */
        val CASTLE: List<Pair<Int, Int>> = buildList {
            for (y in SAND - 1 downTo SAND - 3) for (x in 11..21) add(x to y)
            for (y in SAND - 4 downTo SAND - 7) for (x in listOf(11, 12, 13, 19, 20, 21)) add(x to y)
            for (x in listOf(11, 13, 19, 21)) add(x to SAND - 8)
            for (y in SAND - 4 downTo SAND - 9) for (x in 15..17) add(x to y)
            for (x in listOf(15, 17)) add(x to SAND - 10)
        }
        val CRAB = listOf("#.....#", "##...##", ".#####.", "#######", "#.#.#.#")
        val CRAB_UP = listOf("#.....#", "#.....#", "##...##", "#######", "#.#.#.#")
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) { c.set(x, SAND, 0.25f); for (y in SAND + 1..24) c.set(x, y, 0.1f) }
        if (d.stage >= 2) {
            val tide = if (d.hit(400) > 0f) 2 else 0
            for (x in 0..24) for (y in SAND + 2 - tide..24) c.set(x, y, 0.2f)
            for (x in 0..24) if ((x + d.t / 200) % 4 == 0L) c.set(x, SAND + 2 - tide, 0.5f)
        }
        val built = if (d.stage >= 1) CASTLE.size else min(CASTLE.size, d.beats * 2)
        for ((x, y) in CASTLE.take(built)) c.set(x, y, 0.38f)
        if (built >= CASTLE.size) { c.cut(16, SAND - 1); c.cut(16, SAND - 2) }
        if (d.stage >= 1) {
            for (y in SAND - 14..SAND - 11) c.set(16, y, 0.4f)
            val wave = (d.t / 300) % 2 == 0L
            c.set(17, SAND - 14, 0.9f); c.set(18, SAND - 14 + (if (wave) 0 else 1), 0.9f); c.set(17, SAND - 13, 0.9f)
        }
        // Sand in the air, from the crab to the top of what is built.
        if (d.last != null && d.sinceKey < 350 && built < CASTLE.size && !d.paused) {
            val (tx, ty) = CASTLE[built]
            val q = d.sinceKey / 350f
            val x = mix(6f, tx.toFloat(), q).roundToInt(); val y = (mix(SAND - 5f, ty.toFloat(), q) - sin(q * PIF) * 5f).roundToInt()
            c.block(x, y, x + 1, y + 1, 0.6f)
        }
        val up = d.hit(220) > 0f || (d.paused && (d.t / 400) % 2 == 0L)
        val art = if (up) CRAB_UP else CRAB
        art.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.set(2 + i, SAND - 5 + j, 0.42f) } }
        c.cut(4, SAND - 3); c.cut(6, SAND - 3)
    }
}

/**
 * Bee, the honeycomb: flowers on the left, the comb on the right, and with each key the bee flies a load of nectar
 * from a flower to the comb, where one more cell fills with honey, so the comb fills as you type. A pause and the bee
 * rests on a flower. After 10 s honey drips from the comb; after 20 s the full comb glows with the keys.
 */
internal class BeeCombDesk : Desk() {
    private companion object {
        /** The comb's cells (their top left), in the order they fill: bottom row first. */
        val CELLS: List<Pair<Int, Int>> = buildList {
            for ((row, y) in listOf(16, 12, 8).withIndex()) for (k in 0 until 3) add(13 + k * 3 + (if (row % 2 == 1) 1 else 0) to y)
        }
        val BEE = listOf(".##.", "#-##", ".##.")
    }

    override fun draw(c: Cv, d: DeskInput) {
        for (x in 0..24) c.set(x, 21, 0.2f)
        // The flowers.
        for ((fx, h) in listOf(3 to 6, 7 to 4)) {
            for (y in 21 - h..20) c.set(fx, y, 0.25f)
            val y = 21 - h - 1
            c.set(fx - 1, y, 0.7f); c.set(fx + 1, y, 0.7f); c.set(fx, y - 1, 0.7f); c.set(fx, y + 1, 0.7f); c.cut(fx, y)
        }
        // The comb: each cell an outline, filled once honey is in it.
        val filled = if (d.stage >= 2) CELLS.size else min(CELLS.size, d.beats / 3)
        val glow = d.stage >= 2 && d.hit(250) > 0f
        for ((i, xy) in CELLS.withIndex()) {
            val (x, y) = xy
            for (yy in y..y + 3) for (xx in x..x + 3) {
                val edge = xx == x || xx == x + 3 || yy == y || yy == y + 3
                if (edge) c.set(xx, yy, 0.3f) else if (i < filled) c.set(xx, yy, if (glow) 1f else 0.7f)
            }
        }
        if (d.stage >= 1) { val drip = ((d.t / 300) % 4).toInt(); c.set(15, 20 + min(drip, 0), 0.7f); c.set(15, 20, 0.7f) }
        // The bee: flying flower to comb with each key, resting on a flower in a pause.
        val (bx, by) = if (d.paused || d.last == null) (3 to 13) else {
            val q = min(1f, d.sinceKey / 600f)
            val (tx, ty) = CELLS[min(CELLS.size - 1, filled)]
            mix(4f, tx + 1f, q).roundToInt() to (mix(14f, ty + 1f, q) - sin(q * PIF) * 3f).roundToInt()
        }
        BEE.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(bx - 1 + i, by - 1 + j, 0.55f); '-' -> c.cut(bx - 1 + i, by - 1 + j) } } }
        // Wings over its back, flickering as it flies.
        val wing = if (!d.paused && (d.t / 60) % 2 == 0L) 0.5f else 0.25f
        c.set(bx, by - 2, wing); c.set(bx + 1, by - 2, wing)
    }
}

/**
 * Bat, mapping the cave: a dark cave, the bat hanging from the roof, and each key it squeaks: a ring of sound spreads
 * from it, and the stretch of cave wall it reaches stays faintly lit, so the cave comes out of the dark as you type. A
 * pause and it wraps itself in its wings. After 10 s crystals in the walls glint as the ring passes; after 20 s the
 * whole cave is mapped and the bat flies round it.
 */
internal class BatCaveDesk : Desk() {
    private companion object {
        /** The cave wall: roof, floor with stalactites and stalagmites, as cells round the matrix. */
        val WALL: List<Pair<Int, Int>> = buildList {
            for (x in 2..22) { add(x to 5); add(x to 19) }
            for (x in listOf(6, 12, 18)) for (y in 6..8) add(x to y)
            for (x in listOf(9, 15)) for (y in 16..18) add(x to y)
        }
        val CRYSTALS = listOf(4 to 18, 20 to 18, 9 to 6)
        val BAT = listOf("#.....#", "##.#.##", ".#####.", "..#-#..")
        val WRAPPED = listOf("..###..", ".#####.", ".#####.", "..#.#..")
    }

    override fun draw(c: Cv, d: DeskInput) {
        val mapped = if (d.stage >= 2) 1f else min(1f, d.beats / 40f)
        val a = if (d.last == null) 9f else d.sinceKey / 1000f
        val ring = a * 14f
        // The wall comes out of the dark nearest first, in whole stretches.
        // A stalactite or stalagmite comes out together with the stretch of roof or floor it hangs from.
        val order = WALL.sortedBy { (x, y) -> val by = when (y) { in 6..8 -> 5; in 16..18 -> 19; else -> y }; hypot(x - 12f, by - 7f) + (y - by) * 0.01f }
        val known = order.take((order.size * mapped).toInt()).toHashSet()
        // The roof it hangs from is always there, faint, till the sound finds it.
        for (x in 2..22) c.set(x, 5, 0.1f)
        for ((x, y) in WALL) {
            val dd = hypot(x - 12f, y - 7f)
            val seen = (x to y) in known
            val pass = abs(dd - ring) < 1.5f
            if (seen || pass) c.set(x, y, if (pass) 0.6f else 0.25f)
        }
        if (d.stage >= 1) for ((x, y) in CRYSTALS) {
            val pass = abs(hypot(x - 12f, y - 7f) - ring) < 2f
            c.set(x, y, if (pass) 1f else 0.4f); c.set(x, y - 1, if (pass) 0.7f else 0.3f)
        }
        if (!d.paused && a < 0.8f) c.ring(12f, 7f, ring, 0.3f)
        // The bat: hanging from the roof, wrapped in a pause, flying round the cave in the third act.
        val (bx, by) = if (d.stage >= 2 && !d.paused) {
            val t = d.t / 1200f
            (12 + kotlin.math.cos(t) * 7f).roundToInt() to (12 + sin(t) * 4f).roundToInt()
        } else 12 to 6
        val art = if (d.paused) WRAPPED else BAT
        art.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(bx - 3 + i, by + j, 0.45f); '-' -> c.cut(bx - 3 + i, by + j) } } }
    }
}

/**
 * Hedgehog, the burrow: the ground cut open, and the hedgehog digs its burrow under it, the tunnel going a
 * cell further with each key, down, along and into rooms. A pause and it stops and looks back up the tunnel. After
 * 10 s the pantry room fills with the apples it brings; after 20 s it curls up asleep in its bedroom at the end.
 */
internal class HedgehogBurrowDesk : Desk() {
    private companion object {
        const val GRASS = 7
        val WAY = listOf(6 to 7, 6 to 11, 13 to 11, 13 to 15, 8 to 15, 8 to 19, 17 to 19)
        val PATH: List<Pair<Int, Int>> = buildList {
            for (k in 0 until WAY.size - 1) {
                val (x0, y0) = WAY[k]; val (x1, y1) = WAY[k + 1]
                val n = max(abs(x1 - x0), abs(y1 - y0))
                for (i in 0 until n) add((x0 + (x1 - x0) * i / n.toFloat()).roundToInt() to (y0 + (y1 - y0) * i / n.toFloat()).roundToInt())
            }
            add(WAY.last())
        }
        /** The rooms: the pantry off the second level, the bedroom at the end. */
        val PANTRY = 15 to 10
        val BED = 18 to 18
    }

    override fun draw(c: Cv, d: DeskInput) {
        // Sky, grass, and the soil under it.
        for (x in 0..24) { c.set(x, GRASS, 0.3f); for (y in GRASS + 1..24) c.set(x, y, 0.14f) }
        c.art(listOf(".#.", "###", ".#."), 18, GRASS - 3, 0.3f)
        val dug = if (d.stage >= 2) PATH.size else min(PATH.size, d.beats / 2 + 1)
        for (i in 0 until dug) { val (x, y) = PATH[i]; c.cut(x, y); c.cut(x, y + 1) }
        if (d.stage >= 1) {
            val (px, py) = PANTRY
            for (y in py..py + 2) for (x in px..px + 3) c.cut(x, y)
            val apples = min(4, (d.beats - 40).coerceAtLeast(0) / 3 + 1)
            for (k in 0 until apples) c.set(px + k, py + 2, 0.85f)
        }
        val (bx, by) = BED
        if (dug >= PATH.size) for (y in by - 1..by + 2) for (x in bx..bx + 4) c.cut(x, y)
        // The hedgehog at the tip of the tunnel (asleep in its bedroom in the third act).
        if (d.stage >= 2) {
            c.art(listOf(".###.", "#####", ".###."), bx, by - 1, 0.45f)
            c.cut(bx + 1, by)
        } else {
            val (hx, hy) = PATH[(dug - 1).coerceAtLeast(0)]
            val face = if (d.paused) -1 else 1
            c.block(hx - 1, hy - 1, hx + 1, hy + 1, 0.45f)
            c.set(hx + face * 2, hy, 0.85f)
            c.cut(hx + face, hy - 1)
            if (d.hit(150) > 0f && !d.paused) c.set(hx + face * 2, hy + 1, 0.3f)
        }
    }
}

/**
 * Kodama, the tree (the old scene, the spirits sooner): a tree grows branch by branch as you type, each key a leaf
 * lighting on it; from 10 s the kodama sit along its branches, their heads clicking round with the keys, more of them
 * as the tree grows.
 */
internal class KodamaTreeDesk : Desk() {
    private val tips = ArrayList<Pair<Float, Float>>()
    private fun branch(c: Cv, x: Float, y: Float, a: Float, len: Float, depth: Int, max: Int) {
        if (depth > max) { tips += x to y; return }
        val x2 = x + kotlin.math.cos(a) * len; val y2 = y + sin(a) * len
        c.line(x, y, x2, y2, 0.3f + 0.05f * (4 - depth).coerceAtLeast(0))
        branch(c, x2, y2, a - 0.5f, len * 0.72f, depth + 1, max); branch(c, x2, y2, a + 0.45f, len * 0.72f, depth + 1, max)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val g = (d.typed / 40_000f).coerceIn(0f, 1f); tips.clear()
        val max = (g * 4.99f).toInt()
        for (x in 0..24) c.put(x, 23, 0.2f)
        branch(c, 12f, 23f, -PIF / 2, 5f + g * 1.5f, 0, max)
        val n = d.last?.n ?: -1
        val ago = if (d.last == null) 99_999L else d.sinceKey
        val lit = min(n + 1, tips.size * 2)
        for (i in 0 until lit) { val (x, y) = tips[(i * 7) % tips.size]; val fresh = i == lit - 1 && ago < 200
            c.put(x + (if (i >= tips.size) 1 else 0), y - (if (i >= tips.size) 1 else 0), if (fresh) 1f else 0.6f) }
        // The spirits: from the second act, one on every third tip, their heads clicking round with the keys.
        if (d.stage >= 1) for ((j, t) in tips.withIndex()) if (j % 3 == 0) {
            val (x, y) = t; val tilt = if (ago < 150 && (n + j) % 2 == 0) 1 else 0
            c.disc(x + tilt, y - 2f, 1.6f, 0.7f); c.cut((x - 0.7f + tilt).roundToInt(), (y - 2f).roundToInt()); c.cut((x + 0.7f + tilt).roundToInt(), (y - 2f).roundToInt())
        }
    }
}

/**
 * Astronaut, the launch: the rocket on its pad, the astronaut's face in its window. Each key a burst of fuel goes in:
 * the gauge beside it rises and steam puffs from the pad. After 10 s the countdown lamps light, one after another with
 * the keys. After 20 s it lifts off on a column of fire and stays up in space: stars stream past, and it flies higher
 * the faster you type and sinks lower when you slow down.
 */
internal class AstronautLaunchDesk : Desk() {
    private val heat = n7.glyphpet.pet.drawn.Fire.field()
    private var alt = 0f
    private companion object {
        const val PAD = 21
        val ROCKET = listOf("...#...", "..###..", "..###..", ".##-##.", ".#####.", ".#####.", ".#####.", "#######", "#.###.#", "#.....#")
    }

    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val fuel = min(1f, d.typed / 20_000f)
        val space = d.stage >= 2
        val lift = if (space) min(1f, d.stageMs / 2500f) else 0f
        // Height in space follows the typing: high for a rush, low for slow keys, sinking in a pause.
        val want = when { d.paused -> 0.1f; d.rushing -> 1f; d.rate >= Typing.FLOW_RATE -> 0.6f; else -> 0.3f }
        alt += (want - alt) * min(1f, d.dtMs / 900f)
        val top = if (!space) PAD - ROCKET.size else {
            val ground = PAD - ROCKET.size
            val inSpace = (14 - alt * 10).roundToInt()
            mix(ground.toFloat(), inSpace.toFloat(), ease(lift)).roundToInt()
        }
        if (!space || lift < 1f) {
            for (x in 4..20) c.set(x, PAD + (if (space) (lift * 6).roundToInt() else 0), 0.3f)
            val off = if (space) (lift * 6).roundToInt() else 0
            for (y in 9 + off..PAD - 1 + off) c.set(5, y, 0.25f)
            for (y in 11 + off..PAD - 1 + off step 3) { c.set(6, y, 0.25f); c.set(7, y, 0.25f) }
            if (!space) for (y in 0 until (fuel * 11).roundToInt()) c.set(3, PAD - 1 - y, 0.8f)
            if (d.stage >= 1 && !space) {
                for (y in 9..PAD - 1) c.set(20, y, 0.25f)
                for (k in 0 until 3) c.set(19, 10 + k * 3, if (d.beats % 6 >= k * 2) 0.9f else 0.25f)
            }
        }
        // Stars streaming past in space, faster the higher it flies.
        // Columns either side of the rocket, well inside the round edge, each streak offset in time.
        if (space) for ((k, x) in listOf(4, 7, 17, 20, 5, 19).withIndex()) {
            val y = ((d.t / (90 - alt * 50) + k * 7) % 22).toInt() + 4
            c.streak(x, y, 4, 0.5f)
        }
        ROCKET.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(9 + i, top + j, 0.42f); '-' -> c.cut(9 + i, top + j) } } }
        if (!d.paused) { c.cut(11, top + 3); c.cut(13, top + 3) }
        if (space) n7.glyphpet.pet.drawn.Fire.flame(heat, 12f, top + ROCKET.size + 0f, d.t, size = 0.35f + alt * 0.35f, uy = 1f, power = 1f, salt = 3)
        else if (d.last != null && d.sinceKey < 500) {
            val q = d.sinceKey / 500f
            for (s in listOf(-1, 1)) { val x = (12 + s * (5 + q * 4)).roundToInt(); c.block(x, PAD - 2, x + 1, PAD - 1, 0.4f * (1 - q) + 0.15f) }
        }
        n7.glyphpet.pet.drawn.Fire.render(c, heat, 0.8f)
    }
}

/**
 * Kraken, waking up: its head rises out of the sea in the middle, two big eyes over the water, and its tentacles come
 * up round it one after another as you type, up to four, curling and swaying; each key one of them lashes up high. A
 * pause and the tentacles sink and it shuts its eyes. After 10 s its head rises higher and its eyes glow; after 20 s a
 * storm whips the waves up with the keys.
 */
internal class KrakenWakeDesk : Desk() {
    private companion object {
        const val SEA = 15
        /** Where the tentacles come up, in the order they appear, and which way each curls. */
        val ARMS = listOf(6 to -1, 18 to 1, 3 to 1, 21 to -1)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val storm = d.stage >= 2 && d.hit(300) > 0f
        // The tentacles first, so the head and the sea lie over their roots.
        val arms = min(ARMS.size, 1 + d.beats / 5)
        val lash = if (d.last != null && d.hit(320) > 0f && !d.paused) d.n % arms else -1
        for (k in 0 until arms) {
            val (x0, curl) = ARMS[k]
            val h = when { d.paused -> 2; k == lash -> 11; else -> 8 + (k % 2) }
            for (j in 0 until h) {
                val y = SEA - j
                val sway = sin(j * 0.5f + d.t / 500f + k) * 1.2f + curl * j * j / 40f
                val x = (x0 + sway).roundToInt()
                c.set(x, y, 0.42f); c.set(x + curl, y, 0.42f)
            }
            // The tip curls over and is a little brighter.
            val tipX = (x0 + sin((h - 1) * 0.5f + d.t / 500f + k) * 1.2f + curl * (h - 1) * (h - 1) / 40f).roundToInt()
            c.set(tipX + curl * 2, SEA - h + 1, 0.6f)
        }
        // The head: a dome out of the water, its eyes over it.
        val rise = if (d.stage >= 1) 2 else 0
        val top = SEA - 4 - rise
        for (y in top..SEA + 3) {
            val half = when (y - top) { 0 -> 2; 1 -> 3; else -> 4 }
            for (x in 12 - half..12 + half) c.set(x, y, 0.36f)
        }
        val glow = d.stage >= 1
        for (ex in listOf(9, 14)) {
            val ey = top + 2
            if (glow) { c.set(ex - 1, ey + 1, 0.7f); c.set(ex + 2, ey + 1, 0.7f) }
            if (d.paused) { c.cut(ex, ey + 1); c.cut(ex + 1, ey + 1) }
            else { c.cut(ex, ey); c.cut(ex + 1, ey); c.cut(ex, ey + 1); c.cut(ex + 1, ey + 1) }
        }
        // The sea over everything below its surface: waves, solid from crest to water.
        for (x in 0..24) {
            val up = if ((x + d.t / 250) % 7 < (if (storm) 3 else 2)) (if (storm) 2 else 1) else 0
            for (y in SEA - up..SEA) c.put(x, y, 0.3f)
            for (y in SEA + 1..24) c.set(x, y, if (y <= SEA + 3 && abs(x - 12) <= 4) 0.2f else 0.07f)
        }
    }
}

/**
 * Tardigrade, unkillable: the little water bear in the middle, and each key the world throws something at it in turn,
 * fire, ice, lightning, and it shrugs each one off with a bounce. The world behind it changes as you type: earth at
 * first, then sky with clouds (10 s), then space with stars (20 s), where it floats, still unbothered. A pause and it
 * curls up into a little barrel and naps.
 */
internal class TardigradeDesk : Desk() {
    private val heat = n7.glyphpet.pet.drawn.Fire.field()
    private companion object {
        val BEAR = listOf("..######..", ".########.", "##########", "#-#####-##", "##########", "#.#.##.#.#")
        val BARREL = listOf("..####..", ".######.", "########", ".######.")
    }

    override fun draw(c: Cv, d: DeskInput) {
        heat.fill(0f)
        val space = d.stage >= 2
        // The world behind it.
        when (d.stage) {
            0 -> for (x in 0..24) { c.set(x, 20, 0.25f); for (y in 21..24) c.set(x, y, 0.08f) }
            1 -> for ((cx, cy) in listOf(5 to 6, 18 to 9, 10 to 19)) { val dx = ((d.t / 300 + cx) % 30) - 5; c.art(listOf(".##.", "####"), (dx).toInt(), cy, 0.2f) }
            else -> for ((sx, sy) in listOf(4 to 5, 19 to 4, 21 to 15, 3 to 17, 12 to 3, 16 to 21)) { c.set(sx, sy, 0.5f); c.set(sx - 1, sy, 0.25f); c.set(sx + 1, sy, 0.25f); c.set(sx, sy - 1, 0.25f); c.set(sx, sy + 1, 0.25f) }
        }
        val float = if (space) (sin(d.t / 900f) * 1.2f).roundToInt() else 0
        val bx = 7; val by = (if (space) 9 else 14) + float
        // What the world throws at it with the last key: fire, ice or lightning, in turn.
        val hit = d.last != null && d.sinceKey < 500 && !d.paused
        val kind = if (d.last == null) 0 else d.n % 3
        val bounce = if (hit && d.sinceKey in 250..450) -1 else 0
        if (hit) when (kind) {
            0 -> n7.glyphpet.pet.drawn.Fire.flame(heat, 12f, by + 6f, d.t, size = 0.45f, power = 1f - d.sinceKey / 500f, salt = d.n)
            1 -> for (k in 0 until 3) { val x = 9 + k * 3; val y = (by - 6 + d.sinceKey / 60).toInt().coerceAtMost(by - 1); c.set(x, y, 0.7f); c.set(x, y - 1, 0.7f); c.set(x, y - 2, 0.5f); c.set(x, y - 3, 0.3f) }
            else -> { val x = 13; c.line(x + 2f, 0f, x - 1f, by - 6f, 1f); c.line(x - 1f, by - 6f, x + 1f, by - 6f, 1f); c.line(x + 1f, by - 6f, x - 1f, by - 1f, 1f) }
        }
        n7.glyphpet.pet.drawn.Fire.render(c, heat, 0.7f)
        val art = if (d.paused) BARREL else BEAR
        val ox = if (d.paused) bx + 1 else bx
        art.forEachIndexed { j, r -> r.forEachIndexed { i, ch ->
            val x = ox + i; val y = by + j + bounce + (if (d.paused) 2 else 0)
            when (ch) { '#' -> c.set(x, y, if (hit && kind == 1) 0.6f else 0.4f); '-' -> c.cut(x, y) }
        } }
    }
}

/**
 * Cassette, the mixtape: the cassette big in the middle, its reels its eyes, and with each key the tape winds on from
 * the left reel to the right, the right one fattening and the left one thinning, both turning as fast as you type; an
 * equaliser under it jumps with the keys. A pause and the reels stop. After 10 s the label lights; when the tape has
 * all wound over (20 s) the cassette flips and plays the other side, the reels swapping.
 */
internal class CassetteMixtapeDesk : Desk() {
    private var spin = 0f

    private fun reel(c: Cv, cx: Int, cy: Int, r: Float, turn: Float) {
        for (y in cy - 4..cy + 4) for (x in cx - 4..cx + 4) {
            val dd = hypot((x - cx).toFloat(), (y - cy).toFloat())
            if (dd <= r && dd > 1.3f) c.set(x, y, 0.55f)
        }
        // The hub: dark, with a spoke that turns.
        c.cut(cx, cy)
        val a = turn
        c.set(cx + kotlin.math.cos(a).roundToInt(), cy + sin(a).roundToInt(), 0.9f)
    }

    override fun draw(c: Cv, d: DeskInput) {
        val speed = when { d.paused -> 0f; d.rushing -> 9f; d.rate >= Typing.FLOW_RATE -> 5f; else -> 2.5f }
        spin += speed * d.dtMs / 1000f
        // How far the tape has wound (0..1); flipped from the third act.
        val wound = min(1f, d.typed / 40_000f)
        val side = if (d.stage >= 2) 1f - min(1f, (d.typed - 40_000) / 40_000f) else wound
        // The shell.
        for (y in 6..17) for (x in 2..22) {
            val edge = y == 6 || y == 17 || x == 2 || x == 22
            c.set(x, y, if (edge) 0.4f else 0.16f)
        }
        // The label strip (lit from the second act).
        for (x in 5..19) c.set(x, 8, if (d.stage >= 1) 0.6f else 0.25f)
        // The reels: the tape moves from left to right as it winds.
        reel(c, 8, 12, 2.2f + (1f - side) * 1.8f, spin)
        reel(c, 16, 12, 2.2f + side * 1.8f, spin + 1f)
        // The tape window between them.
        for (x in 11..13) c.cut(x, 12)
        // The equaliser under it, jumping with the keys.
        if (!d.paused) for (k in 0 until 5) {
            val h = 2 + ((h(d.n + k, k) * 3 * (1 + d.hit(250))).toInt()).coerceAtMost(3)
            for (j in 0 until h) c.set(6 + k * 3, 21 - j, 0.5f); for (j in 0 until h) c.set(7 + k * 3, 21 - j, 0.5f)
        }
    }
}

/**
 * Ferro, the sculpture: a magnet hangs over the drop of ferrofluid, and with each key the magnet pulses and the drop
 * reaches up a little higher, spike over spike, building a spiky tower towards it as you type. A pause and the drop
 * relaxes into a smooth puddle with its face. After 10 s the tower reaches the magnet; after 20 s the whole tower
 * shivers with every key.
 */
internal class FerroTowerDesk : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        // The magnet: a horseshoe at the top, its poles lit for a moment with each key.
        val pulse = d.hit(220) > 0f
        c.art(listOf("#######", "#.....#", "#.....#"), 9, 2, 0.35f)
        c.set(9, 4, if (pulse) 1f else 0.5f); c.set(15, 4, if (pulse) 1f else 0.5f)
        for (x in 0..24) c.set(x, 22, 0.2f)
        if (d.paused) {
            // A smooth puddle with its face.
            for (y in 19..21) { val half = when (y) { 19 -> 3; 20 -> 5; else -> 6 }; for (x in 12 - half..12 + half) c.set(x, y, 0.4f) }
            c.cut(10, 20); c.cut(14, 20)
            return
        }
        // The tower: layers of spikes, one more every few keys, up to the magnet.
        val layers = min(6, 1 + d.beats / 6)
        val shiver = if (d.stage >= 2 && pulse) (if (d.n % 2 == 0) 1 else -1) else 0
        for (k in 0 until layers) {
            val base = 21 - k * 3
            val half = 6 - k
            // A solid layer two rows thick, narrower each time, so the tower reads as one body of liquid metal.
            for (x in 12 - half..12 + half) { c.set(x + shiver * (k % 2), base, 0.45f); c.set(x + shiver * (k % 2), base - 1, 0.45f) }
            // Spikes off the layer's ends and up its middle, a cell longer on a key.
            val tall = if (pulse && k == layers - 1) 2 else 1
            val sh = shiver * (k % 2)
            for (j in 1..tall) { c.set(12 - half - j + sh, base - j, 0.6f); c.set(12 + half + j + sh, base - j, 0.6f) }
            c.set(12 + shiver * (k % 2), base - 1, 0.45f); c.set(12 + shiver * (k % 2), base - 2, if (k == layers - 1) 0.8f else 0.45f)
        }
        // Its face at the bottom of the tower.
        c.cut(10, 20); c.cut(14, 20)
    }
}

/**
 * Shadow play: a lamp throws a lit circle on the wall, and with each key the hands in front of it make the next shadow
 * animal, a clear dark silhouette in the light: a rabbit, a bird, a dog, a snail, round again. Each animal shown joins
 * a row of little shadows along the bottom, so the cast grows as you type. A pause and the lamp dims. After 10 s the
 * shadow moves (ears twitch, wings flap); after 20 s the lamp swings and the light sweeps.
 */
internal class ShadowPlayDesk : Desk() {
    private companion object {
        /** The shadows, solid (a real shadow has no eyes). */
        val FIGURES = listOf(
            listOf("...#...#...", "...##..##..", "....#..#...", "....####...", "...######..", "..##-#####.", "..########.", "...######..", "....####...", "....#..#..."),
            listOf("#.........#", "##.......##", ".###...###.", "..#######..", "...#####...", "....####...", "....####...", "....##.....", "...........", "..........."),
            listOf("..##.......", ".####......", ".#######...", "#########..", "###-######.", ".##########", "..####..##.", "..###......", "...........", "..........."),
            listOf("........#.#", "........#.#", "...###..###", ".#######.##", "###########", "###########", "###########", "...........", "...........", "..........."),
        )
        val SMALL = listOf(listOf("#.#", "###", ".#."), listOf("#.#", ".#.", ".#."), listOf("##.", "###", "#.#"), listOf("..#", "###", "###"))
    }

    override fun draw(c: Cv, d: DeskInput) {
        val swing = if (d.stage >= 2) (sin(d.t / 900f) * 3f) else 0f
        val lx = 12f + swing
        val dim = if (d.paused) 0.35f else 0.55f
        // The lit circle on the wall.
        for (y in 1..18) for (x in 0..24) { val dd = hypot(x - lx, (y - 9f) * 1.1f); if (dd <= 8.5f) c.set(x, y, if (dd > 7.8f) dim * 0.6f else dim) }
        // The shadow of the moment, dark in the light.
        val n = max(0, d.n)
        val fig = FIGURES[n % FIGURES.size]
        val twitch = d.stage >= 1 && d.hit(200) > 0f
        fig.forEachIndexed { j, r -> r.forEachIndexed { i, ch ->
            val x = (lx - 5 + i).roundToInt(); val y = 4 + j - (if (twitch && j < 2) 1 else 0)
            if (ch == '#' || ch == '-') c.cut(x, y)
        } }
        // The cast so far along the bottom: a little shadow for each animal shown (up to four, one of each).
        val cast = min(FIGURES.size, d.beats / 5 + 1)
        for (k in 0 until cast) SMALL[k].forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.set(5 + k * 4 + i, 20 + j, 0.4f) } }
    }
}

/**
 * Meadow, the flowers: the little critter in its meadow, and with each key a flower opens in the grass round it, so
 * the meadow fills with flowers as you type. A pause and it lies down in the grass. After 10 s dusk falls and the moon
 * comes up; after 20 s the flowers glow in the night, brighter with the keys.
 */
internal class MeadowFlowersDesk : Desk() {
    private companion object {
        const val GRASS = 17
        val SPOTS = listOf(4 to 18, 20 to 19, 7 to 21, 17 to 21, 2 to 15, 22 to 16, 10 to 22, 14 to 22, 5 to 13, 19 to 13)
    }

    override fun draw(c: Cv, d: DeskInput) {
        if (d.stage >= 1) { val g = if (d.stage == 1) d.stageIn(3000) else 1f; c.disc(19f, 5f, 2.2f, 0.5f * g) }
        // The grass: a soft hill.
        for (x in 0..24) { val top = (GRASS - 2 * sin((x - 2) / 20f * PIF)).roundToInt(); for (y in top..24) c.set(x, y, if (y == top) 0.25f else 0.08f) }
        val flowers = min(SPOTS.size, d.beats / 3)
        val glow = d.stage >= 2
        for (i in 0 until flowers) {
            val (x, y) = SPOTS[i]
            val b = if (glow && d.hit(250) > 0f) 1f else if (glow) 0.7f else 0.6f
            c.set(x, y + 1, 0.3f)
            c.set(x - 1, y, b); c.set(x + 1, y, b); c.set(x, y - 1, b); c.cut(x, y)
        }
        // The critter, drawn by hand (the hero itself is a whole diorama, hills and all), in the middle of its meadow.
        val critter = if (d.paused) listOf(".#...#.", "#######", "#######", ".#####.") else listOf(".#...#.", "##...##", "#######", "#-###-#", "#######", ".#####.")
        val top = GRASS - critter.size + (if (d.hit(200) > 0f && !d.paused) -1 else 0) + 1
        critter.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> when (ch) { '#' -> c.set(9 + i, top + j, 0.45f); '-' -> c.cut(9 + i, top + j) } } }
        if (d.paused) { c.cut(10, top + 2); c.cut(11, top + 2); c.cut(13, top + 2); c.cut(14, top + 2) }
    }
}

/**
 * Pond, gone fishing: the cat sits on the bank with its rod, the line down to a float on the pond, and with each key
 * the float bobs; every few keys it dips right under and the cat pulls out a fish, which flies over into the bucket
 * beside it, so the bucket fills as you type. A pause and the cat dozes, the float lying still. After 10 s evening
 * comes and the moon shines on the water; after 20 s a big fish leaps out of the pond with every sent message and
 * with the keys now and then.
 */
internal class PondFishingDesk : Desk() {
    private companion object {
        const val WATER = 16
        val CAT = listOf("#...#", "#####", "#-#-#", "#####", ".###.", "#####", "#####")
        val FISH = listOf("#.##.", ".####", "#.##.")
    }

    /** When the last fish was pulled out (ms into the mode), and how many have been. */
    private var catchAt = -10_000L
    private var catches = -1

    override fun draw(c: Cv, d: DeskInput) {
        // A fish every few keys' worth of typing, its flight timed on its own (fast typing must not cut it short).
        val due = d.beats / 4
        if (catches < 0) catches = due
        if (due > catches && !d.paused) { catches = due; catchAt = d.t }
        if (d.stage >= 1) {
            val g = if (d.stage == 1) d.stageIn(3000) else 1f
            c.disc(19f, 5f, 2f, 0.5f * g)
            for (y in WATER + 1..22) c.set(19, y, (if (y % 2 == 0) 0.35f else 0.2f) * g)
        }
        // The bank on the left, the pond on the right.
        for (x in 0..9) for (y in WATER - 1..24) c.set(x, y, 0.18f)
        for (x in 10..24) { c.set(x, WATER, 0.3f); for (y in WATER + 1..24) c.put(x, y, 0.06f) }
        // The cat on the bank, its eyes shut in a pause.
        CAT.forEachIndexed { j, r -> r.forEachIndexed { i, ch ->
            val x = 1 + i; val y = WATER - 8 + j
            when (ch) { '#' -> c.set(x, y, 0.45f); '-' -> if (d.paused) c.set(x, y, 0.45f) else c.cut(x, y) }
        } }
        if (d.paused) { c.cut(2, WATER - 5); c.cut(4, WATER - 5) }
        // The rod and the line down to the float.
        c.line(6f, WATER - 6f, 15f, 5f, 0.3f)
        val since = d.t - catchAt
        val caught = since < 700
        val bob = when { d.paused -> 0; caught && since < 200 -> 2; d.hit(200) > 0f -> 1; else -> 0 }
        val fx = 15
        c.line(15f, 5f, fx.toFloat(), (WATER - 1 + bob).toFloat(), 0.15f)
        c.set(fx, WATER - 1 + bob, 0.9f); c.set(fx, WATER + bob, 0.5f)
        // The bucket beside the cat, filling with fish.
        c.art(listOf("#...#", "#...#", ".###."), 5, WATER - 4, 0.35f)
        val fish = min(3, d.beats / 4)
        for (k in 0 until fish) c.set(6 + k, WATER - 3 + (if (k % 2 == 0) 0 else -1), 0.8f)
        // The fish of a catch, flying from the float over into the bucket.
        if (caught && since >= 200) {
            val q = (since - 200) / 500f
            val x = mix(fx.toFloat(), 7f, q).roundToInt(); val y = (mix(WATER - 2f, WATER - 4f, q) - sin(q * PIF) * 8f).roundToInt()
            FISH.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.set(x - 2 + i, y + j, 0.7f) } }
        }
        // The big fish leaping (third act).
        if (d.stage >= 2 && (d.sending || (d.last != null && d.n % 7 == 3 && d.sinceKey < 600))) {
            val q = if (d.sending) d.sentP else d.sinceKey / 600f
            val x = mix(13f, 22f, q).roundToInt(); val y = (WATER - sin(q * PIF) * 6f).roundToInt()
            listOf("..##..", ".####.", "######", ".####.", "#....#").forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '#') c.set(x - 3 + i, y - 4 + j, 0.75f) } }
        }
    }
}
