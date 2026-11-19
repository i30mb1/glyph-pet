package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mythic pets (Gem, Planet), permanently ray-cast per LED, calm: the view sways slowly and the tilt turns it a little.
 * - Gem: a faceted stone (a brilliant cut; the baby a simple shard) turning slowly under a fixed light, each facet its
 *   own brightness, glints flashing on the ones that turn to it; the adult's sparkles twinkle around it. Its habit
 *   becomes a quick extra turn.
 * - Planet: a shaded sphere with turning bands and a storm spot (the baby a lumpy, cratered asteroid), a tipped ring
 *   whose front arc crosses it and back arc hides behind it, and for the adult a moon orbiting round and behind. Its
 *   habit rocks the ring (and sends the adult's moon once round faster).
 * The face is stamped on the front afterwards ([FaceStamp]), moved with the view, so every face reads.
 */
internal class Mythic3D(private val view: Living3D) : BodyPainter {
    companion object {
        private const val N = GlyphMatrixSpec.SIZE
        private const val MAX = SpeciesRenderer.BRIGHT

        /** View turn at full tilt, radians (a little: ±12° yaw, ±7° pitch), and a slow sway of its own. */
        const val YAW = 0.21f
        const val PITCH = 0.12f
        const val SWAY_YAW = 0.06f
        const val SWAY_PITCH = 0.03f
        /** Always-on: a touch from the side. */
        const val STILL_YAW = 0.1f
        const val STILL_PITCH = -0.05f

        /** Gem: radians a second it turns by itself, and the extra turn its habit gives. */
        const val GEM_SPIN = 0.35f
        const val GEM_HABIT_TURN = (2 * PI).toFloat()
        /** Gem: tipped toward the viewer so the table shows (the view's pitch adds to it). */
        const val GEM_TIP = -0.2f
        const val GEM_AMBIENT = 560f
        const val GEM_DIFFUSE = 1500f
        const val GEM_GLINT_POW = 18f

        /** Planet: surface turn, radians a second (about one turn in 16 s). */
        const val PLANET_SPIN = 0.4f
        const val PLANET_AMBIENT = 480f
        const val PLANET_DIFFUSE = 1300f
        /** Ring tip (0 is edge-on), radii in planet radii, and how far the habit rocks it, radians. */
        const val RING_TIP = 0.36f
        const val RING_IN = 1.25f
        const val RING_SPLIT = 1.40f
        const val RING_OUT = 1.58f
        const val RING_ROCK = 0.35f
        /** The adult's moon: orbit radius and size in planet radii, radians a second. */
        const val MOON_ORBIT = 1.45f
        const val MOON_R = 0.21f
        const val MOON_SPEED = 0.5f
        const val MOON_START = -1.2f
        /** The asteroid's lumps (share of its radius). */
        const val LUMP = 0.07f
        /** Rays per LED along each axis. */
        const val SS = 2

        val L = unit(-0.7f, -0.45f, -0.6f)
        val GEM_L = unit(-0.55f, -0.7f, -0.6f)
        val STORM = unit(0.3f, 0.25f, -0.9f)
        val CRATERS = listOf(unit(0.4f, -0.3f, -0.85f), unit(-0.5f, 0.35f, -0.8f), unit(0.85f, 0.4f, 0.3f), unit(-0.6f, -0.5f, 0.6f))

        private fun unit(x: Float, y: Float, z: Float): FloatArray {
            val l = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / l, y / l, z / l)
        }

        /** Half-spaces n . p <= d ([nx, ny, nz, d]) bounding the triangles' hull (the origin inside). */
        private fun planes(tris: List<Array<FloatArray>>, extra: List<FloatArray>): Array<FloatArray> {
            val out = ArrayList<FloatArray>(extra)
            for ((a, b, c) in tris) {
                val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
                val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
                var nx = uy * vz - uz * vy
                var ny = uz * vx - ux * vz
                var nz = ux * vy - uy * vx
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= l; ny /= l; nz /= l
                val mx = (a[0] + b[0] + c[0]) / 3f
                val my = (a[1] + b[1] + c[1]) / 3f
                val mz = (a[2] + b[2] + c[2]) / 3f
                if (nx * mx + ny * my + nz * mz < 0f) { nx = -nx; ny = -ny; nz = -nz }
                out += floatArrayOf(nx, ny, nz, nx * a[0] + ny * a[1] + nz * a[2])
            }
            return out.toTypedArray()
        }

        private fun ring(n: Int, r: Float, y: Float, off: Float) = Array(n) { i ->
            val a = (i + off) / n * 2f * PI.toFloat()
            floatArrayOf(cos(a) * r, y, sin(a) * r)
        }

        /**
         * The brilliant cut: table, 16 crown facets, 8 pavilion facets; y from -0.62 (table) to 0.95 (culet), the girdle
         * a little lower than a real stone's, where the sprites draw it.
         */
        val BRILLIANT: Array<FloatArray> = run {
            val t = ring(8, 0.5f, -0.62f, 0f)
            val g = ring(8, 1f, -0.05f, 0.5f)
            val apex = floatArrayOf(0f, 0.95f, 0f)
            val tris = (0 until 8).flatMap { i ->
                val j = (i + 1) % 8
                listOf(arrayOf(t[i], t[j], g[i]), arrayOf(g[i], g[(i + 7) % 8], t[i]), arrayOf(g[i], g[j], apex))
            }
            planes(tris, listOf(floatArrayOf(0f, -1f, 0f, 0.62f)))
        }
        /** The middle of the tipped stone's height (table about -0.7, culet 0.9) and that height. */
        const val BRILLIANT_MID = 0.1f
        const val BRILLIANT_H = 1.6f

        /** The baby's shard: a long double pyramid over six sides. */
        val SHARD: Array<FloatArray> = run {
            val g = ring(6, 1f, -0.1f, 0f)
            val top = floatArrayOf(0f, -1f, 0f)
            val bottom = floatArrayOf(0f, 1f, 0f)
            val tris = (0 until 6).flatMap { i ->
                val j = (i + 1) % 6
                listOf(arrayOf(top, g[i], g[j]), arrayOf(bottom, g[j], g[i]))
            }
            planes(tris, emptyList())
        }
    }

    /** What the ray-casting needs of a sprite, worked out once per species. */
    private class Shape(
        /** The middle and size of the main body (gem: its whole stone; planet: its '#' ball). */
        val midX: Float, val midY: Float, val w: Int, val h: Int,
        /** The sprite's ring row is there (a row of bright cells across the ball). */
        val ring: Boolean,
        /** Cells of the pieces apart from the main body (the adult gem's sparkles), one list per piece. */
        val apart: List<List<Pair<Int, Int>>>,
    )

    private val shapes = HashMap<String, Shape>()
    private val buf = IntArray(N * N)
    private val worldN = FloatArray(BRILLIANT.size * 3)

    private fun shape(s: Species): Shape = shapes.getOrPut(s.id) { measure(s) }

    private fun measure(s: Species): Shape {
        val rows = s.rows
        val h = rows.size
        val w = rows.maxOf { it.length }
        // The pieces: 4-connected lit cells; the biggest is the body.
        val seen = Array(h) { BooleanArray(w) }
        val pieces = ArrayList<List<Pair<Int, Int>>>()
        for (y in 0 until h) for (x in 0 until w) {
            if (seen[y][x] || (rows[y].getOrNull(x) ?: '.') == '.') continue
            val piece = ArrayList<Pair<Int, Int>>()
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(x to y)
            seen[y][x] = true
            while (stack.isNotEmpty()) {
                val (cx, cy) = stack.removeLast()
                piece += cx to cy
                for ((nx, ny) in listOf(cx + 1 to cy, cx - 1 to cy, cx to cy + 1, cx to cy - 1)) {
                    if (ny !in 0 until h || nx !in 0 until w || seen[ny][nx]) continue
                    if ((rows[ny].getOrNull(nx) ?: '.') == '.') continue
                    seen[ny][nx] = true
                    stack.addLast(nx to ny)
                }
            }
            pieces += piece
        }
        val main = pieces.maxByOrNull { it.size } ?: emptyList()
        val core = if (s.kind == "planet") main.filter { (x, y) -> rows[y][x] == '#' }.ifEmpty { main } else main
        val x0 = core.minOfOrNull { it.first } ?: 0
        val x1 = core.maxOfOrNull { it.first } ?: (w - 1)
        val y0 = core.minOfOrNull { it.second } ?: 0
        val y1 = core.maxOfOrNull { it.second } ?: (h - 1)
        val ring = rows.any { row -> row.count { it == 'o' } > 4 }
        return Shape((x0 + x1) / 2f, (y0 + y1) / 2f, x1 - x0 + 1, y1 - y0 + 1, ring, pieces.filter { it !== main })
    }

    override fun paint(
        frame: IntArray, species: Species, face: Face, rows: List<String>, grid: List<CharArray>,
        ox: Int, oy: Int, body: Int, bright: Int,
    ): Boolean {
        val shape = shape(species)
        if (shape.w <= 1 || shape.h <= 1) return false
        val t = view.timeMs / 1000f
        val a: Float
        val b: Float
        if (view.still) {
            a = STILL_YAW; b = STILL_PITCH
        } else {
            a = -view.tiltX * YAW + view.yaw + SWAY_YAW * sin(t * 0.37f)
            b = view.tiltY * PITCH + SWAY_PITCH * sin(t * 0.29f + 2f)
        }
        val gain = body / SpeciesRenderer.BODY.toFloat()
        buf.fill(0)
        val cx = ox + shape.midX
        val cy = oy + shape.midY
        // Where the face's features go: their spot on the front of the body (an ellipsoid fit), turned with the view.
        val rx: Float
        val ry: Float
        when (species.kind) {
            "gem" -> {
                rx = shape.w / 2f * 0.98f
                ry = shape.h / 2f
                gem(species, shape, cx, cy, a, b, t, gain)
                twinkle(shape, ox, oy, t, bright)
            }
            "planet" -> {
                val r = minOf(shape.w, shape.h) / 2f
                rx = r
                ry = r
                planet(species, shape, cx, cy, r, a, b, t, gain)
            }
            else -> return false
        }
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)
        val anchors = FaceStamp.anchors(species)
        val sx = IntArray(anchors.size)
        val sy = IntArray(anchors.size)
        anchors.forEachIndexed { j, (fx, fy) ->
            val u = (fx - shape.midX) / rx
            val v = (fy - shape.midY) / ry
            val z = -sqrt((1f - u * u - v * v).coerceAtLeast(0.05f))
            val x1 = ca * u + sa * z
            val z1 = -sa * u + ca * z
            val y2 = cb * v - sb * z1
            sx[j] = FaceStamp.round((x1 - u) * rx)
            sy[j] = FaceStamp.round((y2 - v) * ry)
        }
        val markBright = if (face == Face.PEEK) MAX else bright
        FaceStamp.stamp(buf, species, rows, grid, ox, oy, sx, sy, markBright)
        FaceStamp.commit(frame, buf)
        return true
    }

    // ----------------------------------------------------------------------------------------------------------
    // Gem
    // ----------------------------------------------------------------------------------------------------------

    private fun gem(species: Species, shape: Shape, cx: Float, cy: Float, yaw: Float, pitch: Float, t: Float, gain: Float) {
        val baby = species.stage == Stage.BABY
        val cut = if (baby) SHARD else BRILLIANT
        val mid = if (baby) 0f else BRILLIANT_MID
        val scaleX = shape.w / 2f * 0.98f
        val scaleY = if (baby) shape.h / 2f else shape.h / BRILLIANT_H
        val habit = view.habit
        val turn = (if (view.still) 0.6f else t * GEM_SPIN) + (if (habit >= 0f) GEM_HABIT_TURN * ease(habit) else 0f) + view.spin
        val ay = turn + yaw
        val ax = GEM_TIP + pitch
        val cay = cos(ay); val say = sin(ay)
        val cax = cos(ax); val sax = sin(ax)
        for (p in cut.indices) {
            val q = cut[p]
            val rx = cay * q[0] + say * q[2]
            val rz = -say * q[0] + cay * q[2]
            worldN[p * 3] = rx
            worldN[p * 3 + 1] = cax * q[1] - sax * rz
            worldN[p * 3 + 2] = sax * q[1] + cax * rz
        }
        val ex = inverse(1f, 0f, 0f, cax, sax, cay, say)
        val ey = inverse(0f, 1f, 0f, cax, sax, cay, say)
        val ez = inverse(0f, 0f, 1f, cax, sax, cay, say)
        val x0 = maxOf(0, (cx - scaleX - 1).toInt())
        val x1 = minOf(N - 1, (cx + scaleX + 1).toInt() + 1)
        val y0 = maxOf(0, (cy - scaleY - 1).toInt())
        val y1 = minOf(N - 1, (cy + scaleY + 1).toInt() + 1)
        val inv = 1f / (SS * SS)
        for (y in y0..y1) for (x in x0..x1) {
            val idx = y * N + x
            if (!Living3D.VISIBLE[idx]) continue
            var sum = 0f
            var hits = 0
            for (sj in 0 until SS) for (si in 0 until SS) {
                val u = (x + (si + 0.5f) / SS - 0.5f - cx) / scaleX
                val v = (y + (sj + 0.5f) / SS - 0.5f - cy) / scaleY + mid
                val v0 = facet(cut, u, v, ex, ey, ez)
                if (v0 >= 0f) { sum += v0; hits++ }
            }
            if (hits > 0) buf[idx] = (sum * inv * gain).toInt().coerceIn(0, MAX)
        }
    }

    /** The light of the facet the ray through (u, v) enters, or -1 for a miss. */
    private fun facet(cut: Array<FloatArray>, u: Float, v: Float, ex: FloatArray, ey: FloatArray, ez: FloatArray): Float {
        val ox = u * ex[0] + v * ey[0] - 4f * ez[0]
        val oy = u * ex[1] + v * ey[1] - 4f * ez[1]
        val oz = u * ex[2] + v * ey[2] - 4f * ez[2]
        var tin = -1e9f
        var tout = 1e9f
        var hit = -1
        for (p in cut.indices) {
            val q = cut[p]
            val den = q[0] * ez[0] + q[1] * ez[1] + q[2] * ez[2]
            val num = q[3] - (q[0] * ox + q[1] * oy + q[2] * oz)
            if (abs(den) < 1e-7f) {
                if (num < 0f) return -1f
                continue
            }
            val tt = num / den
            if (den < 0f) { if (tt > tin) { tin = tt; hit = p } } else if (tt < tout) tout = tt
        }
        if (hit < 0 || tin > tout) return -1f
        val nx = worldN[hit * 3]
        val ny = worldN[hit * 3 + 1]
        val nz = worldN[hit * 3 + 2]
        val diff = max(0f, nx * GEM_L[0] + ny * GEM_L[1] + nz * GEM_L[2])
        // The view ray (+z) mirrored on the facet: a glint where it points at the light.
        val rx = -2f * nz * nx
        val ry = -2f * nz * ny
        val rz = 1f - 2f * nz * nz
        val spec = max(0f, rx * GEM_L[0] + ry * GEM_L[1] + rz * GEM_L[2]).pow(GEM_GLINT_POW)
        return GEM_AMBIENT + GEM_DIFFUSE * diff + MAX * spec
    }

    /** Inverse rotation (x by -ax, then y by -ay) of a vector. */
    private fun inverse(x: Float, y: Float, z: Float, cax: Float, sax: Float, cay: Float, say: Float): FloatArray {
        val y1 = cax * y + sax * z
        val z1 = -sax * y + cax * z
        return floatArrayOf(cay * x - say * z1, y1, say * x + cay * z1)
    }

    /** The adult gem's sparkles around the stone, flaring in turn. */
    private fun twinkle(shape: Shape, ox: Int, oy: Int, t: Float, bright: Int) {
        shape.apart.forEachIndexed { k, cells ->
            val wave = max(0f, sin(t * 2.6f + k * 2.1f)).pow(3)
            val level = if (view.still) 0.8f else 0.2f + 0.8f * wave
            val v = (bright * level).toInt()
            for ((x, y) in cells) {
                val mx = ox + x
                val my = oy + y
                if (mx in 0 until N && my in 0 until N && v > buf[my * N + mx]) buf[my * N + mx] = v
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------
    // Planet
    // ----------------------------------------------------------------------------------------------------------

    private fun planet(species: Species, shape: Shape, cx: Float, cy: Float, r: Float, a: Float, b: Float, t: Float, gain: Float) {
        val baby = species.stage == Stage.BABY
        val moon = species.stage == Stage.ADULT
        val habit = view.habit
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)
        val time = if (view.still) 0f else t
        val spin = time * PLANET_SPIN + view.spin
        val cs = cos(spin); val ss = sin(spin)

        // The ring's plane in the planet's own frame: tipped toward us, rocked sideways by the habit.
        val roll = if (habit >= 0f) RING_ROCK * sin(habit * 2f * PI.toFloat()) else 0f
        val n0y = -cos(RING_TIP)
        val rnx = -n0y * sin(roll)
        val rny = n0y * cos(roll)
        val rnz = -sin(RING_TIP)
        // The ray's direction (0, 0, 1) in the planet's frame (undo pitch, then yaw).
        val dbx = -sa * cb
        val dby = sb
        val dbz = ca * cb
        val ringDen = rnx * dbx + rny * dby + rnz * dbz
        val ring = shape.ring

        // The moon on its own inclined orbit, in the planet's frame, then into the view.
        var mx = 0f
        var my = 0f
        var mz = 0f
        if (moon) {
            val ma = (if (view.still) 0.9f else MOON_START + time * MOON_SPEED) + (if (habit >= 0f) 2f * PI.toFloat() * ease(habit) else 0f) + view.moonTurn
            val m0x = cos(ma) * MOON_ORBIT
            val m0z = sin(ma) * MOON_ORBIT
            val m1y = -sin(0.9f) * m0z
            val m1z = cos(0.9f) * m0z
            val mbx = m0x * cos(-0.45f) - m1y * sin(-0.45f)
            val mby = m0x * sin(-0.45f) + m1y * cos(-0.45f)
            val mx1 = ca * mbx + sa * m1z
            val mz1 = -sa * mbx + ca * m1z
            mx = mx1
            my = cb * mby - sb * mz1
            mz = sb * mby + cb * mz1
        }

        val reach = (if (ring) RING_OUT else 1f + LUMP) + 0.1f
        val reachMoon = if (moon) MOON_ORBIT + MOON_R + 0.1f else 0f
        val span = max(reach, reachMoon) * r + 1f
        val x0 = maxOf(0, (cx - span).toInt())
        val x1 = minOf(N - 1, (cx + span).toInt() + 1)
        val y0 = maxOf(0, (cy - span).toInt())
        val y1 = minOf(N - 1, (cy + span).toInt() + 1)
        val inv = 1f / (SS * SS)
        for (y in y0..y1) for (x in x0..x1) {
            val idx = y * N + x
            if (!Living3D.VISIBLE[idx]) continue
            var sum = 0f
            var hits = 0
            for (sj in 0 until SS) for (si in 0 until SS) {
                val u = (x + (si + 0.5f) / SS - 0.5f - cx) / r
                val v = (y + (sj + 0.5f) / SS - 0.5f - cy) / r
                var best = 1e9f
                var value = -1f

                // The ball; the camera sits at z = -4 looking along +z, so its front is at negative z.
                val rad = if (baby) 1f + LUMP * sin(3f * atan2(v, u) + spin * 0.5f) else 1f
                val q = rad * rad - u * u - v * v
                if (q > 0f) {
                    val z = -sqrt(q)
                    best = z + 4f
                    val nu = u / rad; val nv = v / rad; val nz = z / rad
                    val lam = max(0f, nu * L[0] + nv * L[1] + nz * L[2])
                    // The surface point in the planet's frame (undo pitch, then yaw), turned by the spin.
                    val y1 = cb * nv + sb * nz
                    val z1 = -sb * nv + cb * nz
                    val bx = ca * nu - sa * z1
                    val bz = sa * nu + ca * z1
                    val px = cs * bx + ss * bz
                    val pz = -ss * bx + cs * bz
                    value = if (baby) {
                        var dark = 1f
                        for (c in CRATERS) if (px * c[0] + y1 * c[1] + pz * c[2] > 0.93f) dark = 0.45f
                        (PLANET_AMBIENT + PLANET_DIFFUSE * lam) * dark * gain
                    } else {
                        val band = 0.74f + 0.26f * sin(y1 * 7.5f + 0.9f * sin(atan2(pz, px) * 2f))
                        val storm = px * STORM[0] + y1 * STORM[1] + pz * STORM[2]
                        val spot = if (storm > 0.94f) 0.35f else 1f
                        (PLANET_AMBIENT + PLANET_DIFFUSE * lam) * band * spot * gain
                    }
                }

                // The ring: where the ray meets its plane (in the planet's frame), if nearer than the ball.
                if (ring && abs(ringDen) > 1e-4f) {
                    val oy1 = cb * v + sb * -4f
                    val oz1 = -sb * v + cb * -4f
                    val obx = ca * u - sa * oz1
                    val obz = sa * u + ca * oz1
                    val tt = -(rnx * obx + rny * oy1 + rnz * obz) / ringDen
                    val qx = obx + tt * dbx
                    val qy = oy1 + tt * dby
                    val qz = obz + tt * dbz
                    val rr = sqrt(qx * qx + qy * qy + qz * qz)
                    if (rr in RING_IN..RING_OUT && tt < best) {
                        best = tt
                        val grain = 0.85f + 0.15f * sin(atan2(qz, qx) * 6f - time * 1.5f)
                        value = (if (rr < RING_SPLIT) 1300f else 2700f) * grain * gain
                    }
                }

                // The moon, lit from the same side.
                if (moon) {
                    val dx = u - mx
                    val dy = v - my
                    val mq = MOON_R * MOON_R - dx * dx - dy * dy
                    if (mq > 0f) {
                        val dz = -sqrt(mq)
                        val tt = mz + dz + 4f
                        if (tt < best) {
                            val lam = max(0f, (dx * L[0] + dy * L[1] + dz * L[2]) / MOON_R)
                            value = (900f + 2900f * lam) * gain
                        }
                    }
                }
                if (value >= 0f) { sum += value; hits++ }
            }
            if (hits > 0) buf[idx] = (sum * inv).toInt().coerceIn(0, MAX)
        }
    }

    private fun ease(p: Float): Float {
        val k = p.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }
}
