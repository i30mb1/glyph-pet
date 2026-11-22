package n7.glyphpet.pet

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Light on a flat pet, worked out from its carved silhouette: the side toward the light is brighter, the edge on that
 * side gets a lit rim and the far edge a dim one, sharp outer corners are dimmed and the steps of a diagonal edge get a
 * little light, so a round body reads round. The bright details ('o') are left as they are and stay the brightest.
 *
 * The light comes from above, a little from the left; [light] moves it sideways (the engine follows the phone's lean,
 * so the shading and the eyes' catchlight move as the phone tilts).
 */
object Shading {
    /** Where the light comes from, as a sideways lean: -1 far left, 0 straight above, 1 far right. */
    const val DEFAULT_LIGHT = -0.6f

    /** The light now; the engine sets it for its frame and puts it back after. */
    @Volatile
    var light: Float = DEFAULT_LIGHT

    /** The light's way across the body: this much brighter at the side facing it, as much dimmer at the far side. */
    const val SLOPE = 0.35f
    const val LIT_RIM = 1.3f
    const val DIM_RIM = 0.75f
    const val SIDE_RIM = 0.9f
    /** No body cell goes dimmer or brighter than these times the body. */
    const val MIN = 0.55f
    const val MAX = 1.65f
    /** A sharp outer corner is dimmed to this. */
    const val CORNER = 0.6f
    /** The floor for a dimmed corner, so it never drops out of the body. */
    const val CORNER_MIN = 0.4f
    /** A step of a diagonal edge gets this much of the light of the two body cells beside it, at most [STEP_MAX]. */
    const val STEP = 0.45f
    const val STEP_MAX = 800

    private val D4 = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** The light's direction on the matrix (toward the light; y grows down). */
    fun direction(light: Float = this.light): Pair<Float, Float> {
        val lx = light.coerceIn(-0.95f, 0.95f)
        return lx to -sqrt(1f - lx * lx)
    }

    /** Where the light comes from for a phone leaning [tilt] sideways and [tiltY] forward, as in [TiltStyle.headUp]. */
    fun lightFor(tilt: Float, tiltY: Float): Float {
        val len = sqrt(tilt * tilt + tiltY * tiltY)
        val up = if (len < 0.2f) 0f else -tilt / len
        return (DEFAULT_LIGHT + up).coerceIn(-0.95f, 0.95f)
    }

    /**
     * The lit cells of a carved [grid]: each body cell ('#') at [body] times its light, each step of a diagonal edge
     * (a '.' cell) dimly lit; every other cell 0. [slots] are cells that are never outside (the eye and mouth slots: a
     * carved eye at the edge is still an eye, not a notch to fill).
     */
    fun shade(grid: List<CharArray>, body: Int, slots: Set<Pair<Int, Int>>, light: Float = this.light): Array<IntArray> {
        val h = grid.size
        val w = grid.maxOfOrNull { it.size } ?: 0
        val out = Array(h) { IntArray(w) }
        if (w == 0) return out
        fun at(x: Int, y: Int): Char = grid.getOrNull(y)?.getOrNull(x) ?: '.'
        val outside = outside(grid, w, h, slots)
        fun out(x: Int, y: Int) = x !in 0 until w || y !in 0 until h || outside[y][x]

        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (at(x, y) != '.') {
            x0 = minOf(x0, x); x1 = max(x1, x); y0 = minOf(y0, y); y1 = max(y1, y)
        }
        if (x1 < 0) return out
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val r = max(x1 - x0 + 1, y1 - y0 + 1) / 2f + 0.5f
        val (lx, ly) = direction(light)

        for (y in 0 until h) for (x in 0 until w) {
            if (at(x, y) != '#') continue
            var f = 1f + SLOPE * ((x - cx) * lx + (y - cy) * ly) / r
            var nx = 0; var ny = 0; var edge = false
            for ((dx, dy) in D4) if (out(x + dx, y + dy)) { nx += dx; ny += dy; edge = true }
            if (edge) {
                val facing = nx * lx + ny * ly
                f *= if (facing > 0.2f) LIT_RIM else if (facing < -0.2f) DIM_RIM else SIDE_RIM
            }
            f = f.coerceIn(MIN, MAX)
            // A sharp outer corner: outside on two sides that meet.
            val o = D4.map { (dx, dy) -> out(x + dx, y + dy) }
            if (o.count { it } == 2 && (o[0] || o[1]) && (o[2] || o[3])) f = max(CORNER_MIN, f * CORNER)
            out[y][x] = (body * f).toInt()
        }
        // The steps of a diagonal edge: an outside cell with body on two sides that meet and on the diagonal between.
        for (y in 0 until h) for (x in 0 until w) {
            if (!outside[y][x]) continue
            val b = D4.map { (dx, dy) -> at(x + dx, y + dy) == '#' }
            if (b.count { it } != 2 || !(b[0] || b[1]) || !(b[2] || b[3])) continue
            val dx = if (b[0]) 1 else -1
            val dy = if (b[2]) 1 else -1
            if (at(x + dx, y + dy) != '#') continue
            if (D4.any { (ax, ay) -> at(x + ax, y + ay) == 'o' }) continue
            val m = (out[y][x + dx] + out[y + dy][x]) / 2
            out[y][x] = minOf(STEP_MAX, (m * STEP).toInt())
        }
        return out
    }

    /** The '.' cells of [grid] reachable from beyond its edge through '.' cells, never through [slots]. */
    internal fun outside(grid: List<CharArray>, w: Int, h: Int, slots: Set<Pair<Int, Int>>): Array<BooleanArray> {
        val seen = Array(h) { BooleanArray(w) }
        fun empty(x: Int, y: Int) = (grid.getOrNull(y)?.getOrNull(x) ?: '.') == '.' && (x to y) !in slots
        val queue = ArrayDeque<Pair<Int, Int>>()
        for (y in 0 until h) for (x in 0 until w) {
            if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && empty(x, y)) { seen[y][x] = true; queue += x to y }
        }
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in D4) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h || seen[ny][nx] || !empty(nx, ny)) continue
                seen[ny][nx] = true
                queue += nx to ny
            }
        }
        return seen
    }

    /** Which column of a 2x2 eye takes the catchlight: the one on the light's side. */
    fun glintColumn(light: Float = this.light): Int = if (light <= 0f) 0 else 1

    /** Within this much of straight above, the catchlight is shared by both columns, so it glides across. */
    const val GLINT_BAND = 0.2f

    /** How much of the catchlight each column of an eye takes (left, right): all of it on the light's side, shared near the top. */
    fun glintShares(light: Float = this.light): Pair<Float, Float> {
        val t = ((light + GLINT_BAND) / (2 * GLINT_BAND)).coerceIn(0f, 1f)
        return (1f - t) to t
    }
}
