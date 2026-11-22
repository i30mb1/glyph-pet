package n7.glyphpet.pet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The charging ways of the heroes that used to share the wheel or the fill; see [KindCharge2]. */
enum class KindWay2 {
    HEART, FIREFLIES, TRACKS, CARROTS, SEEDS, MOON, FLAME, IGLOO, APPLES, GLOW, TENTACLES, SPOTS, RAYS, CORE, COILS, HONEY,
}

/**
 * Every hero its own charging (for the teen and the adult; every baby fills up, as in [ChargeRenderer]). The same rule
 * as everywhere: the bright part is the battery level, the motion is the charging speed. The pieces are placed from
 * the sprite's box, never from fixed cells, so they fit the teen and the adult alike and a redrawn sprite keeps them.
 */
internal object KindCharge2 {
    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private const val BODY = SpeciesRenderer.BODY
    private const val FULL = ChargeRenderer.FULL
    private const val LOW = ChargeRenderer.LOW

    /** A part of the indicator still empty, a little above [LOW] so it reads as a place to fill. */
    private const val EMPTY = 420

    private val BY_KIND = mapOf(
        "cat" to KindWay2.HEART,
        "frog" to KindWay2.FIREFLIES,
        "dino" to KindWay2.TRACKS,
        "bunny" to KindWay2.CARROTS,
        "chick" to KindWay2.SEEDS,
        "owl" to KindWay2.MOON,
        "fox" to KindWay2.FLAME,
        "penguin" to KindWay2.IGLOO,
        "hedgehog" to KindWay2.APPLES,
        "ghost" to KindWay2.GLOW,
        "jelly" to KindWay2.TENTACLES,
        "shroom" to KindWay2.SPOTS,
        "cactus" to KindWay2.RAYS,
        "slime" to KindWay2.CORE,
        "toast" to KindWay2.COILS,
        "bee" to KindWay2.HONEY,
    )

    /** The way [s] charges, or null when another renderer draws it. */
    fun way(s: Species): KindWay2? = if (s.stage == Stage.BABY) null else BY_KIND[s.kind]

    fun draw(way: KindWay2, f: IntArray, s: Species, st: ChargeState) {
        when (way) {
            KindWay2.HEART -> heart(f, s, st)
            KindWay2.FIREFLIES -> fireflies(f, s, st)
            KindWay2.TRACKS -> row(f, s, st, n = 5, gap = 3, piece = listOf("o.", ".o"), bob = { (it * 0.8).toInt() % 2 })
            KindWay2.CARROTS -> row(f, s, st, n = 5, gap = 3, piece = listOf("-", "o", "o"), bob = { if ((it * 0.6).toInt() % 3 == 0) -1 else 0 })
            KindWay2.SEEDS -> row(f, s, st, n = 8, gap = 2, piece = listOf("o"), bob = { if ((it * 0.9).toInt() % 2 == 0) 1 else 0 })
            KindWay2.MOON -> moon(f, s, st)
            KindWay2.FLAME -> flame(f, s, st)
            KindWay2.IGLOO -> igloo(f, s, st)
            KindWay2.APPLES -> apples(f, s, st)
            KindWay2.GLOW -> glow(f, s, st)
            KindWay2.TENTACLES -> tentacles(f, s, st)
            KindWay2.SPOTS -> spots(f, s, st)
            KindWay2.RAYS -> rays(f, s, st)
            KindWay2.CORE -> core(f, s, st)
            KindWay2.COILS -> coils(f, s, st)
            KindWay2.HONEY -> honey(f, s, st)
        }
    }

    // --- shared pieces ---------------------------------------------------------------------------------------------

    private fun level(st: ChargeState): Float = if (st.done) 1f else st.level.coerceIn(0f, 1f)

    /** How many of [n] parts are lit. */
    private fun lit(st: ChargeState, n: Int): Int = if (st.done) n else (level(st) * n + 0.001f).toInt().coerceIn(0, n)

    /** The blinking "next" part of a filling indicator. */
    private fun nextOn(st: ChargeState): Boolean = st.live && (st.pace * 0.7).toInt() % 2 == 1

    private fun face(st: ChargeState) = ChargeRenderer.face(st) { if (st.done) Face.HAPPY else Face.CONTENT }

    private fun set(f: IntArray, x: Int, y: Int, value: Int) = ChargeRenderer.set(f, x, y, value.coerceIn(0, BRIGHT))

    /** Brighter only: a piece never darkens the pet it touches. */
    private fun max(f: IntArray, x: Int, y: Int, value: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE && f[y * SIZE + x] < value) set(f, x, y, value)
    }

    /** The pet itself, its own face, a [dy] bob; asleep, dimmed with its Z. */
    private fun pet(f: IntArray, s: Species, st: ChargeState, dy: Int = 0, body: ((Int, Int) -> Int)? = null) {
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, face(st), st.blink), dy = if (st.live) dy else 0, still = st.still, body = body)
        if (st.still && st.reaction == null) ChargeRenderer.zzz(f, s, 0)
    }

    /** A filled part's brightness: full, the newest one blinking while it charges, an empty one faint. */
    private fun part(st: ChargeState, i: Int, lit: Int): Int = when {
        st.still -> if (i < lit) FULL / 2 else EMPTY / 2
        i < lit -> FULL
        i == lit && nextOn(st) -> BRIGHT
        else -> EMPTY
    }

    private fun top(s: Species) = s.oy
    private fun bottom(s: Species) = s.oy + s.height - 1
    private fun midX(s: Species) = s.ox + s.width / 2

    /** A free row for a strip of pieces [h] tall: under the pet if it fits on the matrix, else over it. */
    private fun stripY(s: Species, h: Int): Int = if (bottom(s) + 2 + h - 1 <= 22) bottom(s) + 2 else max(1, top(s) - 1 - h)

    private fun glyph(f: IntArray, rows: List<String>, x: Int, y: Int, value: Int) {
        rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                when (c) {
                    'o', '#' -> max(f, x + dx, y + dy, value)
                    '-' -> max(f, x + dx, y + dy, value / 3)
                }
            }
        }
    }

    // --- the ways -----------------------------------------------------------------------------------------------------

    /** Cat: a heart over its head fills from the bottom and beats, faster with more watts. */
    private fun heart(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val rows = listOf(".#.#.", "#####", "#####", ".###.", "..#..")
        val x0 = midX(s) - 2
        val y0 = max(1, top(s) - rows.size - 1)
        val h = rows.size
        // A heartbeat, not a bulb: a quick rise and a slower fall, once a beat of the pet's work.
        val beat = if (!st.live) 0f else (st.pace * 0.4).let { it - floor(it) }.toFloat().let {
            if (it < 0.18f) it / 0.18f else ((1f - it) / 0.82f).coerceAtLeast(0f)
        }
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c != '#') return@forEachIndexed
                val full = st.done || y + 0.5f > h - level(st) * h
                val v = when {
                    st.still -> if (full) FULL / 2 else EMPTY / 2
                    full -> FULL + ((BRIGHT - FULL) * beat).toInt()
                    else -> EMPTY
                }
                set(f, x0 + x, y0 + y, v)
            }
        }
    }

    /** Frog: fireflies over it, lit one by one, each blinking on its own, quicker with more watts. */
    private fun fireflies(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val n = 6
        val lit = lit(st, n)
        val cy = max(2, top(s) - 3)
        for (i in 0 until n) {
            val a = PI * (0.12 + 0.76 * i / (n - 1))
            val x = midX(s) - ((s.width / 2 + 2) * cos(a)).roundToInt()
            val y = cy - ((2.5) * sin(a)).roundToInt() + (if (st.live) (sin(st.pace * 0.5 + i) * 0.6).roundToInt() else 0)
            val v = part(st, i, lit)
            val flicker = if (st.live && i < lit && sin(st.pace * 1.3 + i * 1.7) < -0.6) FULL / 2 else v
            max(f, x, y, flicker)
        }
    }

    /** A row of [n] pieces under (or over) the pet, lit one by one; the pet bobs to the beat of the watts. */
    private fun row(f: IntArray, s: Species, st: ChargeState, n: Int, gap: Int, piece: List<String>, bob: (Double) -> Int) {
        pet(f, s, st, dy = bob(st.pace))
        val w = piece.maxOf { it.length }
        val span = n * w + (n - 1) * (gap - w).coerceAtLeast(1)
        val step = w + (gap - w).coerceAtLeast(1)
        val x0 = midX(s) - span / 2
        val y0 = stripY(s, piece.size)
        val lit = lit(st, n)
        for (i in 0 until n) glyph(f, piece, x0 + i * step, y0, part(st, i, lit))
    }

    /** Owl: a moon grows from a sliver to full beside it; stars twinkle around, quicker with more watts. */
    private fun moon(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val right = s.ox + s.width + 3 <= 22
        val cx = if (right) s.ox + s.width + 2 else s.ox - 3
        val cy = max(3, top(s) + 1)
        val r = 2.2f
        // The lit part grows from the side away from the owl.
        val edge = 1f - 2f * level(st)
        for (y in -2..2) for (x in -2..2) {
            if (hypot(x.toFloat(), y.toFloat()) > r) continue
            val u = (if (right) x else -x) / r
            val on = st.done || u >= edge
            set(f, cx + x, cy + y, when {
                st.still -> if (on) FULL / 2 else EMPTY / 2
                on -> FULL
                else -> EMPTY
            })
        }
        if (st.live) for (k in 0 until 3) {
            if (sin(st.pace * 0.9 + k * 2.1) > 0.3) max(f, cx + listOf(-3, 2, 3)[k], cy + listOf(-3, -3, 2)[k], BRIGHT)
        }
    }

    /** Fox: a little flame beside it grows with the charge and flickers, livelier with more watts. */
    private fun flame(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val right = s.ox + s.width + 2 <= 22
        val x = if (right) s.ox + s.width + 1 else s.ox - 2
        val base = bottom(s)
        val h = 1 + (level(st) * 6).roundToInt()
        for (i in 0 until 7) {
            val y = base - i
            val inFlame = i < h
            val sway = if (st.live && i >= 2) (sin(st.pace * 1.1 + i) * 0.7).roundToInt() else 0
            val v = when {
                st.still -> if (inFlame) FULL / 2 else 0
                !inFlame -> if (i == h && nextOn(st)) FULL / 2 else 0
                i == h - 1 -> BRIGHT
                else -> FULL
            }
            if (v > 0) set(f, x + sway, y, v)
            // A wider base under the flame.
            if (inFlame && i < 2) set(f, x + (if (right) 1 else -1), y, if (st.still) FULL / 3 else FULL * 2 / 3)
        }
    }

    /** Penguin: an igloo built brick by brick beside it; snow falls, faster with more watts. */
    private fun igloo(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val dome = listOf("..###..", ".#####.", "##...##")
        // Bricks in building order: the bottom row first, then up.
        val bricks = ArrayList<Pair<Int, Int>>()
        for (y in dome.indices.reversed()) for (x in dome[y].indices) if (dome[y][x] == '#') bricks += x to y
        val x0 = midX(s) - 3
        val y0 = stripY(s, dome.size)
        val lit = lit(st, bricks.size)
        bricks.forEachIndexed { i, (x, y) -> set(f, x0 + x, y0 + y, part(st, i, lit)) }
        if (st.live) for (k in 0 until 4) {
            val fx = s.ox - 1 + (k * 5 + 2) % (s.width + 2)
            val fy = ((st.pace * 1.2 + k * 2.7) % 8).toInt() + max(0, top(s) - 4)
            max(f, fx, fy, FULL / 2)
        }
    }

    /** Hedgehog: apples on its spines, picked up one by one; they bob as it trots, quicker with more watts. */
    private fun apples(f: IntArray, s: Species, st: ChargeState) {
        val step = if (st.live) (st.pace * 0.8).toInt() % 2 else 0
        pet(f, s, st, dy = -step)
        val n = 5
        val lit = lit(st, n)
        for (i in 0 until n) {
            val col = (s.width - 1) * (i + 1) / (n + 1)
            val topCell = s.rows.indexOfFirst { (it.getOrNull(col) ?: '.') != '.' }.takeIf { it >= 0 } ?: continue
            set(f, s.ox + col, s.oy + topCell - 1 - (if (st.live) step else 0), part(st, i, lit))
        }
    }

    /** Ghost: it shows up brighter as it charges; two wisps circle it, faster with more watts. */
    private fun glow(f: IntArray, s: Species, st: ChargeState) {
        val body = (LOW + (FULL - LOW) * level(st)).toInt()
        pet(f, s, st, body = { _, _ -> if (st.still) body / 2 else body })
        if (!st.live) return
        val cx = s.ox + s.width / 2f
        val cy = s.oy + s.height / 2f
        val rx = s.width / 2f + 2f
        val ry = s.height / 2f + 1.5f
        for (k in 0 until 2) {
            val a = st.pace * 0.6 + k * PI
            max(f, (cx + rx * cos(a)).roundToInt(), (cy + ry * sin(a)).roundToInt(), BRIGHT)
        }
    }

    /** Jelly: its tentacles light up one by one; a pulse runs down the lit ones, faster with more watts. */
    private fun tentacles(f: IntArray, s: Species, st: ChargeState) {
        val from = s.height * 2 / 3
        val cols = (0 until s.width).filter { x -> (from until s.height).any { y -> (s.rows[y].getOrNull(x) ?: '.') != '.' } }
        val lit = lit(st, cols.size)
        val litCols = cols.take(lit).toSet()
        val pulse = if (st.live) from + ((st.pace * 1.4) % (s.height - from)).toInt() else -1
        pet(f, s, st, body = { x, y ->
            when {
                y < from -> if (st.still) BODY / 2 else BODY
                x in litCols -> if (st.still) FULL / 2 else if (y == pulse) BRIGHT else FULL
                x == cols.getOrNull(lit) && nextOn(st) -> FULL
                else -> if (st.still) EMPTY / 2 else EMPTY
            }
        })
    }

    /** Shroom: the spots on its cap light up one by one; spores float up, faster with more watts. */
    private fun spots(f: IntArray, s: Species, st: ChargeState) {
        val capRows = s.height / 2
        val spotCells = shapeSpots(s, capRows)
        val lit = lit(st, spotCells.size)
        val order = spotCells.withIndex().associate { (i, c) -> c to i }
        pet(f, s, st, body = { x, y ->
            val i = order[x to y]
            if (i == null) (if (st.still) BODY / 2 else BODY) else part(st, i, lit)
        })
        if (st.live) for (k in 0 until 3) {
            val sx = s.ox + (k * 4 + 3) % s.width
            val sy = top(s) - 1 - ((st.pace * 0.9 + k * 1.9) % 5).toInt()
            max(f, sx, sy, FULL / 2)
        }
    }

    private fun shapeSpots(s: Species, capRows: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until capRows) for (x in s.rows[y].indices) {
            if (s.rows[y][x] == '#' && (x * 3 + y * 5) % 7 == 0) out += x to y
        }
        return out.ifEmpty { listOf(s.width / 2 to 0) }
    }

    /** Cactus: rays of the sun around it, one more for every bit of charge; the sun walks round, faster with more watts. */
    private fun rays(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val n = 8
        val lit = lit(st, n)
        val cx = s.ox + s.width / 2f
        val cy = s.oy + s.height / 2f
        val r = max(s.width, s.height) / 2f + 1.5f
        val turn = if (st.live) st.pace * 0.15 else 0.0
        for (i in 0 until n) {
            val a = turn + i * 2 * PI / n - PI / 2
            val v = part(st, i, lit)
            for (d in 0..1) max(f, (cx + (r + d) * cos(a)).roundToInt(), (cy + (r + d) * sin(a)).roundToInt(), if (d == 0) v else v * 2 / 3)
        }
    }

    /** Slime: it glows from its middle outward as it charges; a ripple runs out from the core, faster with more watts. */
    private fun core(f: IntArray, s: Species, st: ChargeState) {
        val cx = (s.width - 1) / 2f
        val cy = (s.height - 1) / 2f + s.height * 0.15f
        val far = hypot(cx, s.height - cy).coerceAtLeast(1f)
        val ripple = if (st.live) ((st.pace * 0.25) % 1.0).toFloat() else -1f
        pet(f, s, st, body = { x, y ->
            val d = hypot(x - cx, y - cy) / far
            when {
                st.still -> if (d <= level(st)) FULL / 2 else LOW / 2
                st.done || d <= level(st) -> if (ripple >= 0 && kotlin.math.abs(d - ripple) < 0.12f) BRIGHT else FULL
                else -> LOW
            }
        })
    }

    /** Toast: the toaster's coil under it glows as far as the charge; steam rises, faster with more watts. */
    private fun coils(f: IntArray, s: Species, st: ChargeState) {
        pet(f, s, st)
        val w = (s.width - 2).coerceAtLeast(3)
        val x0 = midX(s) - w / 2
        val y = stripY(s, 1)
        val lit = lit(st, w)
        val glowOn = st.live && (st.pace * 0.5).toInt() % 2 == 0
        for (i in 0 until w) set(f, x0 + i, y, if (i < lit && glowOn && !st.still) BRIGHT else part(st, i, lit))
        if (st.live) for (k in 0 until 3) {
            val sx = s.ox + 1 + k * (s.width - 2) / 2
            val sy = top(s) - 1 - ((st.pace * 1.1 + k * 1.6) % 4).toInt()
            max(f, sx + (sin(st.pace + k) * 0.6).roundToInt(), sy, FULL / 2)
        }
    }

    /** Bee: honeycomb cells under it fill with honey one by one; it hovers, quicker with more watts. */
    private fun honey(f: IntArray, s: Species, st: ChargeState) {
        val hover = if (st.live) (sin(st.pace * 0.9) * 1.2).roundToInt() else 0
        pet(f, s, st, dy = hover)
        val n = 5
        val lit = lit(st, n)
        val x0 = midX(s) - (n * 3 - 1) / 2
        val y0 = stripY(s, 2)
        for (i in 0 until n) {
            val v = part(st, i, lit)
            val x = x0 + i * 3
            // A little hexagon: two cells wide, offset rows.
            set(f, x, y0, v); set(f, x + 1, y0, v)
            set(f, x, y0 + 1, v * 2 / 3); set(f, x + 1, y0 + 1, v * 2 / 3)
        }
    }
}
