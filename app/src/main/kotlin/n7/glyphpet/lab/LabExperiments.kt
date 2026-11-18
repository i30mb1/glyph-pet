package n7.glyphpet.lab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import n7.glyphpet.lab.Layer.Companion.C
import n7.glyphpet.lab.Layer.Companion.N

/** One Lab idea. [render] draws a frame into a cleared layer; dt and t are in seconds. Must be cheap and never throw. */
internal abstract class Experiment {
    abstract fun render(out: Layer, dt: Float, t: Float, s: LabSensor)
    open fun reset() = Unit
}

/** A tiny seeded random, so the scenes look the same every time. */
internal class Rnd(seed: Int) {
    private var s = seed.toLong().coerceAtLeast(1)
    fun next(): Float {
        s = (s * 16807) % 2147483647
        return s / 2147483647f
    }
}

/** Gravity direction as a unit vector; straight down when the phone lies flat and gravity says nothing. */
internal fun downOf(s: LabSensor): Pair<Float, Float> {
    val l = sqrt(s.gx * s.gx + s.gy * s.gy)
    return if (l < 0.25f) 0f to 1f else s.gx / l to s.gy / l
}

// ---------------------------------------------------------------------------------------------------------------
// 1. Клубок: the hedgehog curled into a spiky ball rolls where the phone is tilted.
// ---------------------------------------------------------------------------------------------------------------
internal class BallExperiment : Experiment() {
    companion object {
        /** Ball radius, LEDs. */
        const val R = 3.4f
        /** How far from the centre the ball's centre may go (the round edge minus the radius). */
        const val LIMIT = 12.3f - R
        /** Acceleration in LEDs/s² at 1 g. */
        const val GRAVITY = 38f
        /** Fraction of the speed kept after one second of rolling. */
        const val DAMPING = 0.55f
        /** Fraction of the speed into the wall kept after a bounce. */
        const val BOUNCE = 0.5f
        /** Speed in LEDs/s a shake of 1 g throws the ball with. */
        const val THROW = 28f
        const val SPINES = 9
    }

    private var x = C
    private var y = C
    private var vx = 0f
    private var vy = 0f
    private var roll = 0f
    private var squash = 0f
    private var sqx = 1f
    private var sqy = 0f

    override fun reset() {
        x = C; y = C; vx = 0f; vy = 0f; roll = 0f; squash = 0f
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        s.consumeJolt()?.let { (jx, jy) -> vx += jx * THROW; vy += jy * THROW }
        // Two half steps keep the bounce honest at a low frame rate.
        repeat(2) { step(dt / 2f, s) }

        // The shadow: dim, offset down-right as if the light came from the top left.
        val r2s = R * R * 1.05f
        for (py in 0 until N) for (px in 0 until N) {
            val ex = px - (x + 1.1f)
            val ey = py - (y + 1.4f)
            if (ex * ex + ey * ey < r2s) out.max(px, py, 220f)
        }
        // The body: a sphere lit from the top left, squashed along the last hit.
        val sq = squash
        for (py in 0 until N) for (px in 0 until N) {
            val ex = px - x
            val ey = py - y
            val along = ex * sqx + ey * sqy
            val across = -ex * sqy + ey * sqx
            val a2 = along / (1 - sq)
            val c2 = across / (1 + sq * 0.6f)
            val r2 = (a2 * a2 + c2 * c2) / (R * R)
            if (r2 > 1f) continue
            val nz = sqrt(1f - r2)
            val lam = ((-ex * 0.5f - ey * 0.6f) / R + nz * 0.6f).coerceAtLeast(0f)
            // Soft rim: the last bit of radius fades, so the ball moves smoothly between LEDs.
            val edge = ((1f - r2) * 4f).coerceIn(0f, 1f)
            out.v[py * N + px] = (640f + 1120f * lam) * edge + out.v[py * N + px] * (1 - edge)
        }
        // Spines and the nose turn with the rolling: that is how you see it roll rather than slide.
        for (i in 0 until SPINES) {
            val a = roll + i * 2f * PI.toFloat() / SPINES
            val rr = R + 0.7f
            out.splat(x + cos(a) * rr, y + sin(a) * rr, 2400f)
        }
        out.splat(x + cos(roll + 1f) * 1.6f, y + sin(roll + 1f) * 1.6f, Lit.BRIGHT)
    }

    private fun step(dt: Float, s: LabSensor) {
        if (dt <= 0f) return
        vx += s.gx * GRAVITY * dt
        vy += s.gy * GRAVITY * dt
        val keep = DAMPING.pow(dt)
        vx *= keep; vy *= keep
        x += vx * dt; y += vy * dt
        val dx = x - C
        val dy = y - C
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > LIMIT) {
            val nx = dx / dist
            val ny = dy / dist
            x = C + nx * LIMIT; y = C + ny * LIMIT
            val vn = vx * nx + vy * ny
            if (vn > 0) {
                vx -= (1 + BOUNCE) * vn * nx
                vy -= (1 + BOUNCE) * vn * ny
                if (vn > 3f) {
                    squash = (vn / 25f).coerceAtMost(0.45f)
                    sqx = nx; sqy = ny
                }
            }
        }
        squash *= 0.002f.pow(dt)
        // Rolling without slipping on whatever is "down": spin = (down × velocity) / R.
        val (ddx, ddy) = downOf(s)
        roll += (ddy * vx - ddx * vy) / R * dt
        if (abs(roll) > 1000f) roll %= (2 * PI.toFloat())
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 2. 3D-поворот: a voxel hedgehog inflated from the teen sprite, z-buffered, slowly turning left and right.
// ---------------------------------------------------------------------------------------------------------------
internal class VoxelExperiment : Experiment() {
    companion object {
        /** Largest turn, radians (±80°). */
        const val SWING = 1.40f
        /** Turns per second of the swing, as an angular frequency. */
        const val SPEED = 0.7f
        /** Extra turn per unit of sideways tilt. */
        const val TILT_TURN = 0.8f
        /** Distance between voxel layers in depth. */
        const val LAYER = 0.8f
        /** Two surfaces closer than this in depth blend; further ones are hidden. */
        const val DEPTH_EPS = 0.6f
    }

    private val form = HogForm.TEEN

    // Voxels as flat arrays: position and kind (0 body, 1 face hole, 2 nose, 3 spine).
    private val vx: FloatArray
    private val vy: FloatArray
    private val vz: FloatArray
    private val kind: IntArray
    private val zbuf = FloatArray(N * N)
    private val acc = FloatArray(N * N)
    private val wsum = FloatArray(N * N)
    private val px: FloatArray
    private val py: FloatArray
    private val pd: FloatArray

    init {
        val w = form.w
        val h = form.h
        val solid = { x: Int, y: Int -> form.char(x, y) != '.' }
        // How deep inside the outline each cell is (1 at the edge): the thickness grows toward the middle.
        fun inset(x: Int, y: Int): Int {
            var k = 1
            while (k < 4) {
                var edge = false
                for (a in -k..k) for (b in -k..k) {
                    if (abs(a) + abs(b) > k) continue
                    if (!solid(x + a, y + b)) edge = true
                }
                if (edge) break
                k++
            }
            return k
        }
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lz = ArrayList<Float>()
        val lk = ArrayList<Int>()
        fun add(x: Float, y: Float, z: Float, k: Int) { lx += x; ly += y; lz += z; lk += k }
        for (y in 0 until h) for (x in 0 until w) {
            val c = form.char(x, y)
            if (c == '.') continue
            val cx = x - (w - 1) / 2f
            val cy = y - (h - 1) / 2f
            val d = inset(x, y).coerceAtMost(3)
            val front = -d
            // The back is thicker and spiky: spines stand a bit higher on every other cell.
            val back = (d * 1.3f).toInt().coerceAtLeast(1)
            val face = (x to y) in form.faceCells
            for (z in front..back) {
                val k = when {
                    c == 'o' && z == front -> 2
                    face && z <= front + 1 -> 1
                    else -> 0
                }
                add(cx, cy, z * LAYER, k)
            }
            if (c == 'o') add(cx, cy, (front - 0.7f) * LAYER, 2)
            if ((x + y) % 2 == 0 && y < h - 2) add(cx, cy, (back + 1) * LAYER, 3)
            // The fringe of spines on top stands up too.
            if (y <= 1) add(cx, cy - 0.6f, (back * 0.5f) * LAYER, 3)
        }
        vx = lx.toFloatArray(); vy = ly.toFloatArray(); vz = lz.toFloatArray(); kind = lk.toIntArray()
        px = FloatArray(vx.size); py = FloatArray(vx.size); pd = FloatArray(vx.size)
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val a = sin(t * SPEED) * SWING + s.tiltX * TILT_TURN
        val b = s.tiltY * 0.4f
        val ca = cos(a)
        val sa = sin(a)
        val cb = cos(b)
        val sb = sin(b)
        zbuf.fill(-1e9f)
        acc.fill(0f)
        wsum.fill(0f)
        // Pass 1: project, and find the nearest depth at every LED the voxel touches.
        for (i in vx.indices) {
            // Turn around y (left-right), then tip around x.
            val x1 = ca * vx[i] + sa * vz[i]
            val z1 = -sa * vx[i] + ca * vz[i]
            val y2 = cb * vy[i] - sb * z1
            val z2 = sb * vy[i] + cb * z1
            px[i] = C + x1
            py[i] = C + 0.5f + y2
            pd[i] = -z2
            forFour(px[i], py[i]) { idx, w -> if (w > 0.15f && pd[i] > zbuf[idx]) zbuf[idx] = pd[i] }
        }
        // Pass 2: only the front surface adds its light, shared sub-pixel between neighbours.
        for (i in vx.indices) {
            val depth = pd[i]
            val value = when (kind[i]) {
                1 -> 0f
                2 -> Lit.BRIGHT
                3 -> 900f + 380f * (depth + 4f).coerceIn(0f, 8f)
                else -> 180f + 230f * (depth + 4f).coerceIn(0f, 8f)
            }
            forFour(px[i], py[i]) { idx, w ->
                if (depth >= zbuf[idx] - DEPTH_EPS) {
                    acc[idx] += value * w
                    wsum[idx] += w
                }
            }
        }
        for (i in 0 until N * N) {
            val ws = wsum[i]
            if (ws <= 0f) continue
            out.v[i] = acc[i] / ws.coerceAtLeast(1f)
        }
    }

    private inline fun forFour(x: Float, y: Float, f: (Int, Float) -> Unit) {
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        if (x0 in 0 until N && y0 in 0 until N) f(y0 * N + x0, (1 - fx) * (1 - fy))
        if (x0 + 1 in 0 until N && y0 in 0 until N) f(y0 * N + x0 + 1, fx * (1 - fy))
        if (x0 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0, (1 - fx) * fy)
        if (x0 + 1 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0 + 1, fx * fy)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 3. Окно в телефон: three layers that shift with tilt by their depth.
// ---------------------------------------------------------------------------------------------------------------
internal class WindowExperiment : Experiment() {
    companion object {
        /** How many LEDs the far layer shifts at full tilt; the middle shifts half that, the glass a little backwards. */
        const val SHIFT = 3.4f
        /** Flip to -1 if the scene seems to move the wrong way on the phone. */
        const val SIGN = 1f
        const val MID_DEPTH = 0.45f
        const val GLASS_DEPTH = -0.2f
    }

    private val rnd = Rnd(11)
    private val stars = Array(14) { floatArrayOf(rnd.next() * 29 - 2, rnd.next() * 13 - 1, rnd.next() * 6.28f) }
    private val trees = listOf(floatArrayOf(1f, 5f), floatArrayOf(6f, 3.5f), floatArrayOf(17f, 4.5f), floatArrayOf(23f, 3f))
    private val drops = Array(5) { floatArrayOf(rnd.next() * 20 + 2, rnd.next() * 25, 0.6f + rnd.next() * 0.8f) }
    private val open = HogForm.TEEN.sprite()
    private val shut = HogForm.TEEN.sprite(blink = true)
    private val mid = Layer()

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val tx = -s.tiltX * SHIFT * SIGN
        val ty = -s.tiltY * SHIFT * SIGN
        // Far: dim twinkling stars, dark tree silhouettes on a dim horizon.
        for (st in stars) {
            val tw = 0.7f + 0.3f * sin(t * 2.1f + st[2])
            out.splat(st[0] + tx, st[1] + ty, 520f * tw)
        }
        val horizon = 17f + ty
        var gx = -3f
        while (gx <= 27f) {
            out.splat(gx + tx, horizon, 260f)
            gx += 0.5f
        }
        for (tr in trees) {
            val bx = tr[0] + tx
            val hgt = tr[1]
            var k = 0f
            while (k < hgt) {
                val half = (hgt - k) * 0.45f
                var dx = -half
                while (dx <= half) {
                    out.splat(bx + dx, horizon - 0.5f - k, 300f)
                    dx += 0.7f
                }
                k += 0.7f
            }
        }
        // Middle: the hedgehog on its own little ground, bobbing a little, hiding what is behind it.
        mid.clear()
        val hx = HogForm.TEEN.ox + tx * MID_DEPTH
        val hy = HogForm.TEEN.oy + 1f + ty * MID_DEPTH + sin(t * 1.3f) * 0.4f
        mid.sprite(if (blinking(t)) shut else open, hx, hy)
        var g = 3f
        while (g <= 22f) {
            mid.splat(g + tx * MID_DEPTH, hy + HogForm.TEEN.h + 0.3f, 500f, 1f)
            g += 0.5f
        }
        mid.over(out)
        // Near: bright drops sliding down the glass along gravity, with a dim trail.
        val (dx, dy) = downOf(s)
        for (d in drops) {
            if (dt > 0f) {
                d[0] += dx * d[2] * 2.2f * dt
                d[1] += dy * d[2] * 2.2f * dt
                val ex = d[0] - C
                val ey = d[1] - C
                if (ex * ex + ey * ey > 14f * 14f) {
                    // Back in at the upper (upwind) edge, somewhere across it.
                    val across = (rnd.next() - 0.5f) * 16f
                    d[0] = C - dx * 12.5f - dy * across
                    d[1] = C - dy * 12.5f + dx * across
                }
            }
            val nx = d[0] - tx * GLASS_DEPTH
            val ny = d[1] - ty * GLASS_DEPTH
            out.splat(nx - dx * 1.1f, ny - dy * 1.1f, 700f)
            out.splat(nx - dx * 2.2f, ny - dy * 2.2f, 300f)
            out.splat(nx, ny, Lit.BRIGHT)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 4. Снег с глубиной: three layers of snow fall along gravity; it gathers on the hedgehog's back.
// ---------------------------------------------------------------------------------------------------------------
internal class SnowExperiment : Experiment() {
    companion object {
        /** Fall speed of the near, middle and far layer, LEDs/s. */
        val SPEED = floatArrayOf(6.5f, 3.6f, 1.7f)
        /** Brightness of the near, middle and far layer. */
        val VALUE = floatArrayOf(4000f, 1800f, 600f)
        val COUNT = intArrayOf(14, 14, 18)
        /** Snow added to a column of the back per flake that lands. */
        const val GATHER = 0.08f
        /** Brightness of a full line of snow on the back. */
        const val PILE = 900f
        const val TOP = 11
    }

    private val rnd = Rnd(5)
    private val form = HogForm.TEEN
    private val open = form.sprite()
    private val shut = form.sprite(blink = true)
    private val flakes: Array<FloatArray>
    private val pile = FloatArray(N)

    /** Per matrix column: the row just above the hedgehog's top, or -1. */
    private val roof = IntArray(N) { -1 }
    private val front = Layer()

    init {
        val list = ArrayList<FloatArray>()
        for (z in 0..2) repeat(COUNT[z]) { list += floatArrayOf(rnd.next() * 25, rnd.next() * 25, z.toFloat(), rnd.next() * 6.28f) }
        flakes = list.toTypedArray()
        val left = form.ox
        for (x in 0 until form.w) {
            val top = (0 until form.h).firstOrNull { form.char(x, it) != '.' } ?: continue
            roof[left + x] = TOP + top - 1
        }
    }

    override fun reset() {
        pile.fill(0f)
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val (dx, dy) = downOf(s)
        if (s.consumeJolt() != null) pile.fill(0f)
        // A steep tilt lets the snow slide off.
        if (abs(s.gx) > 0.6f) for (x in 0 until N) pile[x] *= exp(-dt * 1.5f)

        // Far and middle snow behind the hedgehog, near snow in front of it.
        for (f in flakes) if (f[2] > 0f) fall(f, dt, t, dx, dy, out)
        front.clear()
        front.sprite(if (blinking(t)) shut else open, form.ox.toFloat(), TOP.toFloat())
        for (x in 0 until N) {
            val r = roof[x]
            if (r < 0 || pile[x] <= 0.02f) continue
            front.v[r * N + x] += PILE * pile[x]
            front.cov[r * N + x] = 1f
        }
        front.over(out)
        for (f in flakes) if (f[2] == 0f) fall(f, dt, t, dx, dy, out)
    }

    private fun fall(f: FloatArray, dt: Float, t: Float, dx: Float, dy: Float, out: Layer) {
        val z = f[2].toInt()
        val sp = SPEED[z]
        val sway = sin(t * 1.5f + f[3]) * 0.6f * (3 - z) / 3f
        f[0] += (dx * sp + sway * dy) * dt
        f[1] += (dy * sp - sway * dx) * dt
        // The near and middle snow lands on the back while it falls downwards.
        if (z < 2 && dy > 0.5f) {
            val col = f[0].toInt()
            if (col in 0 until N && roof[col] >= 0 && f[1] >= roof[col] && f[1] < roof[col] + 1f) {
                pile[col] = (pile[col] + GATHER).coerceAtMost(1f)
                respawn(f, dx, dy)
                return
            }
        }
        val ex = f[0] - C
        val ey = f[1] - C
        if (ex * ex + ey * ey > 13.5f * 13.5f) respawn(f, dx, dy)
        out.splat(f[0], f[1], VALUE[z])
    }

    /** Born again on the upwind side of the circle. */
    private fun respawn(f: FloatArray, dx: Float, dy: Float) {
        val a = kotlin.math.atan2(-dy, -dx) + (rnd.next() - 0.5f) * 2.4f
        f[0] = C + cos(a) * 12.8f
        f[1] = C + sin(a) * 12.8f
    }
}
