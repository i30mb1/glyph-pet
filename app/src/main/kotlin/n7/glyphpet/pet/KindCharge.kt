package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** The charging ways of the second batch of heroes; see [KindCharge]. */
enum class KindWay { SPIRAL, BUBBLES, TIDE, ECHO, GILLS, PRISM, ORBIT, CAMO, WASH }

/**
 * How the heroes of the second batch charge, picked by kind for the teen and the adult (every baby fills up, as in
 * [ChargeRenderer]). The same rule as everywhere: the bright part is the battery level, the motion is the charging
 * speed. Every sprite-specific cell is looked up with a guard, so a redrawn sprite only loses its effect.
 */
internal object KindCharge {
    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private const val FULL = ChargeRenderer.FULL
    private const val LOW = ChargeRenderer.LOW

    /** A part of the indicator that is still empty but should stay readable, a little above [LOW]. */
    private const val EMPTY = 500

    private val BY_KIND = mapOf(
        "snail" to KindWay.SPIRAL,
        "crab" to KindWay.BUBBLES,
        "whale" to KindWay.TIDE,
        "bat" to KindWay.ECHO,
        "axolotl" to KindWay.GILLS,
        "gem" to KindWay.PRISM,
        "planet" to KindWay.ORBIT,
        "chameleon" to KindWay.CAMO,
        "raccoon" to KindWay.WASH,
    )

    /** The way [s] charges, or null when [ChargeRenderer] draws it by [Species.charge]. */
    fun way(s: Species): KindWay? = if (s.stage == Stage.BABY) null else BY_KIND[s.kind]

    fun draw(way: KindWay, f: IntArray, s: Species, st: ChargeState) {
        when (way) {
            KindWay.SPIRAL -> spiral(f, s, st)
            KindWay.BUBBLES -> bubbles(f, s, st)
            KindWay.TIDE -> tide(f, s, st)
            KindWay.ECHO -> echo(f, s, st)
            KindWay.GILLS -> gills(f, s, st)
            KindWay.PRISM -> prism(f, s, st)
            KindWay.ORBIT -> orbit(f, s, st)
            KindWay.CAMO -> camo(f, s, st)
            KindWay.WASH -> wash(f, s, st)
        }
    }

    // --- shapes worked out once per sprite --------------------------------------------------------------------------

    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> shape(s: Species, what: String, build: () -> T): T =
        cache.getOrPut("${s.id}/$what") { build() } as T

    private fun cell(s: Species, x: Int, y: Int): Char = s.rows.getOrNull(y)?.getOrNull(x) ?: '.'

    private fun calmFace(st: ChargeState) = ChargeRenderer.face(st) { if (st.done) Face.HAPPY else Face.CONTENT }

    private fun level(st: ChargeState): Float = if (st.done) 1f else st.level.coerceIn(0f, 1f)

    /** The blinking "next" part of a filling indicator, as on the Bot's bar. */
    private fun nextOn(st: ChargeState): Boolean = st.live && (st.pace * 0.7).toInt() % 2 == 1

    private fun put(f: IntArray, x: Int, y: Int, value: Int) = SpeciesRenderer.put(f, x, y, value.coerceIn(0, BRIGHT))

    private fun set(f: IntArray, x: Int, y: Int, value: Int) = ChargeRenderer.set(f, x, y, value.coerceIn(0, BRIGHT))

    /** The finish shared by every way: a twinkling star once full, Zz while asleep. */
    private fun finish(f: IntArray, s: Species, st: ChargeState, dy: Int = 0) {
        if (st.done && !st.still && (st.timeMs / 500) % 2 == 1L) {
            SpeciesRenderer.glyph(f, ChargeRenderer.STAR, s.ox + s.width, max(1, s.oy + dy - 2))
        }
        if (st.still && st.reaction == null) ChargeRenderer.zzz(f, s, dy)
    }

    // --- snail: SPIRAL ----------------------------------------------------------------------------------------------

    /**
     * The shell's bright cells in the order of its spiral, from the centre outward. The top row and lone bright
     * cells stay out: the feeler tips and the adult's flower.
     */
    private fun spiralOrder(s: Species): List<Pair<Int, Int>> {
        val all = s.rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, c -> if (c == 'o') x to y else null } }
        fun joined(x: Int, y: Int) = (-1..1).any { a -> (-1..1).any { b -> (a != 0 || b != 0) && cell(s, x + a, y + b) == 'o' } }
        val shell = all.filter { (x, y) -> y >= 1 && joined(x, y) }.ifEmpty { all }
        if (shell.isEmpty()) return emptyList()
        val cx = shell.sumOf { it.first.toDouble() } / shell.size
        val cy = shell.sumOf { it.second.toDouble() } / shell.size
        val arm = 1.6
        return shell.sortedBy { (x, y) ->
            val r = hypot(x - cx, y - cy)
            var a = atan2(y - cy, x - cx) / (2 * PI)
            if (a < 0) a += 1.0
            // Which turn of the spiral the cell is on, then where along that turn.
            (r / arm - a).roundToInt() + a
        }
    }

    /** The shell lights from the centre outward with the level; a spark runs along the spiral with the watts. */
    private fun spiral(f: IntArray, s: Species, st: ChargeState) {
        val grid = SpeciesRenderer.carve(s, calmFace(st), st.blink)
        ChargeRenderer.paint(f, s, grid, still = st.still)
        val shell = shape(s, "spiral") { spiralOrder(s) }
        val n = shell.size
        if (n > 0) {
            val lit = if (st.done) n else (level(st) * n).roundToInt()
            val on = if (st.still) BRIGHT / 2 else BRIGHT
            val off = if (st.still) LOW / 2 else EMPTY
            shell.forEachIndexed { i, (x, y) ->
                if (grid.getOrNull(y)?.getOrNull(x) == 'o') set(f, s.ox + x, s.oy + y, if (i < lit) on else off)
            }
            if (st.live) {
                // A spark: a gap on the lit part, a light on the dark part, then a short rest before the next run.
                val head = floor((st.pace * 3.0) % (n + 5)).toInt()
                for (k in 0..2) {
                    val i = head - k
                    if (i !in 0 until n) continue
                    val (x, y) = shell[i]
                    val v = if (i < lit) (if (k == 0) 0 else FULL) else BRIGHT - k * 1200
                    set(f, s.ox + x, s.oy + y, v)
                }
            } else if (st.done && !st.still) {
                // Full: a slow shimmer winds out along the shell.
                val i = ((st.timeMs / 90) % (n + 8)).toInt()
                shell.getOrNull(i)?.let { (x, y) -> set(f, s.ox + x, s.oy + y, 0) }
            }
        }
        finish(f, s, st)
    }

    // --- crab: BUBBLES ----------------------------------------------------------------------------------------------

    private const val BUBBLE_SLOTS = 6

    /** Where the six bubbles of the battery hang over the crab, in matrix coordinates, left to right. */
    private fun bubbleSlot(s: Species, i: Int): Pair<Int, Int> {
        val left = s.ox + 1
        val span = max(1, s.width - 3)
        return left + span * i / (BUBBLE_SLOTS - 1) to s.oy - 2 - (i % 2)
    }

    /** Bubbles rise from under the crab, more often with more watts; as many hang over it as the battery holds. */
    private fun bubbles(f: IntArray, s: Species, st: ChargeState) {
        val grid = SpeciesRenderer.carve(s, calmFace(st), st.blink)
        ChargeRenderer.paint(f, s, grid, still = st.still)
        val lit = if (st.done) BUBBLE_SLOTS else (level(st) * BUBBLE_SLOTS).roundToInt()
        for (i in 0 until BUBBLE_SLOTS) {
            val (x, y) = bubbleSlot(s, i)
            val v = when {
                i < lit && st.still -> BRIGHT / 2
                i < lit && st.done -> if (((st.timeMs / 200) + i) % 3 == 0L) FULL else BRIGHT
                i < lit -> if ((floor(st.pace * 1.3).toInt() + i) % 4 == 0) FULL else BRIGHT
                i == lit && nextOn(st) -> FULL / 2
                else -> 0
            }
            if (v > 0) put(f, x, y, v)
        }
        if (st.live) {
            val bottom = s.oy + s.height
            val n = 3
            for (j in 0 until n) {
                val phase = st.pace * 0.25 + j.toDouble() / n
                val cycle = floor(phase).toInt()
                val a = phase - cycle
                val (sx, sy) = bubbleSlot(s, Math.floorMod(cycle * 2 + j * 3, BUBBLE_SLOTS))
                val y = (bottom - a * (bottom - sy - 1)).roundToInt()
                val x = sx + (sin(a * 9 + j) * 0.7).roundToInt()
                put(f, x, y, if (a < 0.85) BRIGHT else FULL)
            }
        }
        finish(f, s, st)
    }

    // --- whale: TIDE ------------------------------------------------------------------------------------------------

    /** Water rises from the bottom of the matrix up to the level with a moving wave; the whale bobs on it. */
    private fun tide(f: IntArray, s: Species, st: ChargeState) {
        val surface = 23.5f - level(st) * 14f
        val wavy = !st.still && !st.done
        for (x in 0 until SIZE) {
            val top = surface + if (wavy) 0.7 * sin(x * 0.6 - st.pace * 1.6) else 0.0
            for (y in 0 until SIZE) {
                if (y + 0.5 < top) continue
                val crest = y - 0.5 < top
                val v = when {
                    st.still -> if (crest) FULL / 2 else LOW
                    crest && wavy && sin(x * 0.9 - st.pace * 2.4) > 0.8 -> BRIGHT
                    crest -> FULL
                    else -> EMPTY
                }
                set(f, x, y, v)
            }
        }
        // Floating: the belly just under the water line, but never off the top or far down the round bottom.
        val want = (surface - (s.oy + s.height - 2)).roundToInt()
        val bob = if (st.live) sin(st.pace * 1.3).roundToInt() else 0
        val dy = (want + bob).coerceIn(1 - s.oy, 3)
        // The whale hides the water behind it, so its eyes and mouth stay dark.
        s.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c != '.') set(f, s.ox + x, s.oy + dy + y, 0) }
        }
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, calmFace(st), st.blink), dy, still = st.still)
        finish(f, s, st, dy)
    }

    // --- bat: ECHO --------------------------------------------------------------------------------------------------

    /** The tips of the two ears, in sprite coordinates: the topmost cell of the left half and of the right half. */
    private fun ears(s: Species): List<Pair<Int, Int>> {
        val w = s.width
        fun tip(xs: IntProgression): Pair<Int, Int>? {
            for (y in s.rows.indices) for (x in xs) if (cell(s, x, y) != '.') return x to y
            return null
        }
        return listOfNotNull(tip(0 until w / 2), tip(w - 1 downTo (w + 1) / 2))
    }

    /** Rings spread up and out from the ears, more often with more watts, as far as the battery holds. */
    private fun echo(f: IntArray, s: Species, st: ChargeState) {
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, calmFace(st), st.blink), still = st.still)
        val reach = 2.5f + level(st) * 8.5f
        val tips = shape(s, "ears") { ears(s) }
        tips.forEachIndexed { side, (ex, ey) ->
            val mx = s.ox + ex
            val my = s.oy + ey
            val outward = if (side == 0) -1 else 1
            fun arc(r: Float, value: Int, dotted: Boolean) {
                if (value <= 0 || r < 0.6f) return
                val span = r.toInt() + 1
                for (y in my - span..my + 2) for (x in mx - span..mx + span) {
                    if ((x - mx) * outward < -1) continue
                    if (cell(s, x - s.ox, y - s.oy) != '.') continue
                    if (abs(hypot((x - mx).toFloat(), (y - my).toFloat()) - r) >= 0.55f) continue
                    if (dotted && (x + y) % 2 == 0) continue
                    put(f, x, y, value)
                }
            }
            // The reach stays marked, dotted and dim, so the level reads between the rings.
            arc(reach, if (st.still) BRIGHT / 3 else LOW, dotted = !st.still)
            val phase = when {
                st.live -> st.pace * 0.45
                st.done && !st.still -> st.timeMs / 1000.0 * 0.3
                else -> return@forEachIndexed
            }
            for (j in 0 until 2) {
                val a = ((phase + j * 0.5) % 1.0).toFloat()
                arc(a * reach, (BRIGHT * (1f - 0.65f * a)).toInt(), dotted = false)
            }
        }
        finish(f, s, st)
    }

    // --- axolotl: GILLS ---------------------------------------------------------------------------------------------

    /**
     * Six battery segments made of the gill cells: the bright cells outside the body's span in their row, split into
     * a top, middle and bottom band on each side, in the order left top, right top, left middle and so on.
     */
    private fun gillSegments(s: Species): List<List<Pair<Int, Int>>> {
        val w = s.width
        val gills = s.rows.flatMapIndexed { y, row ->
            val first = row.indexOf('#')
            val last = row.lastIndexOf('#')
            row.mapIndexedNotNull { x, c -> if (c == 'o' && (first < 0 || x < first || x > last)) x to y else null }
        }
        fun bands(side: List<Pair<Int, Int>>): List<List<Pair<Int, Int>>> {
            if (side.isEmpty()) return List(3) { emptyList() }
            val y0 = side.minOf { it.second }
            val span = side.maxOf { it.second } - y0 + 1
            return List(3) { b -> side.filter { (it.second - y0) * 3 / span == b } }
        }
        val left = bands(gills.filter { it.first < w / 2 })
        val right = bands(gills.filter { it.first >= w / 2 })
        return List(6) { i -> if (i % 2 == 0) left[i / 2] else right[i / 2] }
    }

    /** The gills are six battery segments lit in turn; the lit ones flutter faster with more watts. */
    private fun gills(f: IntArray, s: Species, st: ChargeState) {
        val grid = SpeciesRenderer.carve(s, calmFace(st), st.blink)
        ChargeRenderer.paint(f, s, grid, still = st.still)
        val segments = shape(s, "gills") { gillSegments(s) }
        val lit = if (st.done) 6 else (level(st) * 6 + 0.001f).toInt()
        segments.forEachIndexed { i, cells ->
            cells.forEachIndexed { k, (x, y) ->
                if (grid.getOrNull(y)?.getOrNull(x) != 'o') return@forEachIndexed
                val v = when {
                    st.still -> if (i < lit) BRIGHT / 2 else LOW / 2
                    i < lit && st.live -> (2600 + 1400 * (0.5 + 0.5 * sin(st.pace * 2.2 + i * 1.1 + k * 0.9))).toInt()
                    i < lit && st.done -> if (((st.timeMs / 150) % 8).toInt() == i) FULL else BRIGHT
                    i < lit -> BRIGHT
                    i == lit && nextOn(st) -> BRIGHT
                    else -> LOW
                }
                set(f, s.ox + x, s.oy + y, v)
            }
        }
        finish(f, s, st)
    }

    // --- gem: PRISM -------------------------------------------------------------------------------------------------

    /** The rays of the fan, from straight down outward to both sides in turn. */
    private val RAYS = intArrayOf(0, -1, 1, -2, 2, -3, 3)
    private const val RAY_STEP = 0.38 // radians, about 22 degrees

    /** A beam enters the top of the gem; a fan of rays leaves its bottom tip, one ray per seventh of the battery. */
    private fun prism(f: IntArray, s: Species, st: ChargeState) {
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, calmFace(st), st.blink), still = st.still)
        val col = (s.width - 1) / 2
        val cx = s.ox + col
        val top = s.rows.indices.firstOrNull { cell(s, col, it) != '.' } ?: 0
        val bottom = s.rows.indices.lastOrNull { cell(s, col, it) != '.' } ?: s.height - 1
        // The beam from the top edge down to the gem, light pulses running down it with the watts.
        for (y in 0 until s.oy + top) {
            val v = when {
                st.still -> LOW
                st.live && ((y * 0.25 - st.pace * 0.6) % 1.0 + 1.0) % 1.0 < 0.25 -> BRIGHT
                else -> FULL / 2
            }
            put(f, cx, y, v)
        }
        val n = RAYS.size
        val lit = if (st.done) n else (level(st) * n).roundToInt()
        val tx = cx.toDouble()
        val ty = (s.oy + bottom + 1).toDouble()
        for (i in 0 until n) {
            val next = i == lit && nextOn(st)
            if (i >= lit && !next) continue
            val a = RAYS[i] * RAY_STEP
            for (d in 0..12) {
                val x = (tx + sin(a) * d).roundToInt()
                val y = (ty + cos(a) * d).roundToInt()
                if (y >= SIZE || x !in 0 until SIZE) break
                if (cell(s, x - s.ox, y - s.oy) != '.') continue
                val v = when {
                    next -> LOW
                    st.still -> FULL / 2
                    st.live -> (FULL * 0.6 + (BRIGHT - FULL * 0.6) * (0.5 + 0.5 * sin(d * 1.1 - st.pace * 2.5))).toInt()
                    else -> if (((st.timeMs / 140) + i + d) % 5 == 0L) BRIGHT else FULL
                }
                put(f, x, y, v)
            }
        }
        finish(f, s, st)
    }

    // --- planet: ORBIT ----------------------------------------------------------------------------------------------

    /** A satellite circles the planet in the plane of its ring, faster with more watts; its glowing tail is the level. */
    private fun orbit(f: IntArray, s: Species, st: ChargeState) {
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, calmFace(st), st.blink), still = st.still)
        val ring = shape(s, "ring") {
            s.rows.indices.maxByOrNull { y -> s.rows[y].count { it == 'o' } } ?: (s.height / 2)
        }
        val cx = s.ox + (s.width - 1) / 2.0
        val cy = (s.oy + ring).toDouble()
        val rx = 11.0
        val ry = 4.0
        // Behind the planet (the upper half of the ellipse) the planet hides it.
        fun point(phi: Double, value: Int) {
            val x = (cx + rx * cos(phi)).roundToInt()
            val y = (cy + ry * sin(phi)).roundToInt()
            if (sin(phi) < 0 && cell(s, x - s.ox, y - s.oy) != '.') return
            put(f, x, y, value)
        }
        val theta = when {
            st.live -> st.pace * 1.1
            st.done && !st.still -> st.timeMs * 0.0008
            else -> st.pace * 1.1
        }
        val step = 0.12
        val tail = (level(st) * 2 * PI * 0.94 / step).toInt()
        for (k in tail downTo 1) {
            val fade = 1f - k.toFloat() / (tail + 1)
            val v = (BRIGHT * 0.12f + (FULL - BRIGHT * 0.12f) * fade).toInt()
            point(theta - k * step, if (st.still) v / 2 else v)
        }
        point(theta, if (st.still) BRIGHT / 2 else BRIGHT)
        finish(f, s, st)
    }

    // --- chameleon: CAMO --------------------------------------------------------------------------------------------

    /** The body repaints from the tail (right) to the head; a stripe runs along it with the watts. */
    private fun camo(f: IntArray, s: Species, st: ChargeState) {
        val w = s.width
        val h = s.height
        val front = w * (1f - level(st))
        val stripe = if (st.live) w + 2 - ((st.pace * 0.18) % 1.0) * (w + 4) else Double.NaN
        val grid = SpeciesRenderer.carve(s, calmFace(st), st.blink)
        ChargeRenderer.paint(f, s, grid, still = st.still) { x, y ->
            val edge = front + if (st.live) 0.6 * sin(y * 1.7 + st.pace * 2) else 0.0
            val painted = st.done || x + 0.5 > edge
            val dapple = (x * 7 + y * 3) % 5 == 0
            val base = if (painted) (if (dapple) 3400 else FULL) else LOW
            val onStripe = !stripe.isNaN() && abs(x + (y - h / 2.0) * 0.4 - stripe) < 0.7
            when {
                st.still -> base / 2
                onStripe -> if (painted) BRIGHT else FULL / 2 + LOW
                st.done && (((st.timeMs / 120) + x * 2 + y) % 17 == 0L) -> BRIGHT
                else -> base
            }
        }
        finish(f, s, st)
    }

    // --- raccoon: WASH ----------------------------------------------------------------------------------------------

    /** Rows the tub takes under the paws, and how far the pet stands lifted to leave room for it. */
    private const val TUB_H = 5
    private const val TUB_LIFT = 3

    /**
     * The raccoon washes in a tub: it stands a little higher with a tub under its paws, the water in it rises with the
     * battery and rings spread over the surface in the charging's own time. A full tub is bright and still.
     */
    private fun wash(f: IntArray, s: Species, st: ChargeState) {
        val dy = -TUB_LIFT
        // The rim is right under the paws; the tub hangs below it, as far as the round matrix allows.
        val rim = s.oy + dy + s.height
        val floor = minOf(rim + TUB_H - 1, SIZE - 2)
        val left = s.ox + 1
        val right = s.ox + s.width - 2
        val mid = (left + right) / 2
        // Empty: the water is down at the floor; full: up at the rim.
        val surface = floor - level(st) * (floor - rim)
        val rings = if (st.live) (st.pace * 1.8) % 1.0 else -1.0
        for (y in rim..floor) {
            val span = GlyphMatrixSpec.rowRange(y)
            val a = max(left, span.first)
            val b = minOf(right, span.last)
            if (a > b) continue
            for (x in a..b) {
                val wall = x == a || x == b || y == floor
                val filled = y + 0.5f >= surface
                val crest = filled && y - 0.5f < surface
                val v = when {
                    wall && st.still -> FULL / 2
                    wall -> FULL
                    !filled -> if (st.still) 0 else LOW
                    st.still -> if (crest) FULL / 2 else LOW
                    st.done -> if (crest || ((st.timeMs / 150) + x) % 7 == 0L) BRIGHT else FULL
                    // A ring runs out from the middle of the surface, left and right, and fades as it goes.
                    crest && rings >= 0 && abs(abs(x - mid) - rings * (b - a) / 2.0) < 0.7 -> BRIGHT
                    crest -> FULL
                    else -> EMPTY
                }
                set(f, x, y, v)
            }
        }
        // The raccoon hides the tub behind it, so its face and the rings never run into each other.
        s.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c != '.') set(f, s.ox + x, s.oy + dy + y, 0) }
        }
        ChargeRenderer.paint(f, s, SpeciesRenderer.carve(s, calmFace(st), st.blink), dy, still = st.still)
        finish(f, s, st, dy)
    }
}
