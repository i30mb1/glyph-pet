package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gem, real-time 3D: a brilliant-cut stone (table, 16 crown facets, 8 pavilion facets), ray-cast per LED, turns twice
 * on its axis; tilting spins it further and rocks it toward you. Each facet has its own brightness under a fixed light,
 * glints flash on the ones that turn to face it, and little sparkles twinkle around. The eyes and smile sit on the
 * front facets and turn with them: they squeeze together as it turns, and are gone while the back faces you.
 */
internal class LegendGem : Signature {
    override val durationMs = 6400L

    private val tilt = TiltFollow()
    private val sprite = IntArray(N * N)
    private val gem = IntArray(N * N)
    private val worldN = FloatArray(PLANES.size * 3)
    private var tiltSpin = 0f

    override fun start() {
        tilt.reset()
        tiltSpin = 0f
    }

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val dt = input.dtMs.coerceIn(0, 200) / 1000f
        tilt.update(input)
        plain(sprite, input)

        val env = envelope(t, durationMs, FADE_MS)
        tiltSpin += tilt.x * TILT_SPIN * dt
        // Two whole turns, so it ends facing front again; the tilt's share fades out with the effect.
        val ay = (TURNS * 2 * PI).toFloat() * ramp(t, FADE_MS / 2, durationMs - FADE_MS / 2) + tiltSpin * env
        val ax = (BASE_TIP + tilt.y * TILT_TIP) * env
        val cay = cos(ay)
        val say = sin(ay)
        val cax = cos(ax)
        val sax = sin(ax)

        // Facet normals into the world (the light stays put, the stone turns under it).
        for (p in PLANES.indices) {
            val q = PLANES[p]
            val rx = cay * q[0] + say * q[2]
            val rz = -say * q[0] + cay * q[2]
            worldN[p * 3] = rx
            worldN[p * 3 + 1] = cax * q[1] - sax * rz
            worldN[p * 3 + 2] = sax * q[1] + cax * rz
        }
        // The camera's ray, in the stone's frame: o = u * ex + v * ey - 4 * ez, along ez.
        val ex = inverse(1f, 0f, 0f, cax, sax, cay, say)
        val ey = inverse(0f, 1f, 0f, cax, sax, cay, say)
        val ez = inverse(0f, 0f, 1f, cax, sax, cay, say)

        val scaleX = s.width / 2f * 0.92f
        val scaleY = s.height / 1.65f
        val cx = s.ox + input.dx + (s.width - 1) / 2f
        val cy = s.oy + (s.height - 1) / 2f

        gem.fill(0)
        var glint = 0f
        val x0 = (cx - scaleX - 1).toInt()
        val x1 = (cx + scaleX + 1).toInt() + 1
        val y0 = (cy - scaleY * 0.9f - 1).toInt()
        val y1 = (cy + scaleY * 0.9f + 1).toInt() + 1
        for (y in maxOf(0, y0)..minOf(N - 1, y1)) for (x in maxOf(0, x0)..minOf(N - 1, x1)) {
            val u = (x - cx) / scaleX
            val v = (y - cy) / scaleY + MID_Y
            val ox = u * ex[0] + v * ey[0] - 4f * ez[0]
            val oy = u * ex[1] + v * ey[1] - 4f * ez[1]
            val oz = u * ex[2] + v * ey[2] - 4f * ez[2]
            var tin = -1e9f
            var tout = 1e9f
            var hit = -1
            for (p in PLANES.indices) {
                val q = PLANES[p]
                val den = q[0] * ez[0] + q[1] * ez[1] + q[2] * ez[2]
                val num = q[3] - (q[0] * ox + q[1] * oy + q[2] * oz)
                if (abs(den) < 1e-7f) {
                    if (num < 0f) { tin = 1e9f; break }
                    continue
                }
                val tt = num / den
                if (den < 0f) { if (tt > tin) { tin = tt; hit = p } } else if (tt < tout) tout = tt
            }
            if (hit < 0 || tin > tout) continue
            val nx = worldN[hit * 3]
            val ny = worldN[hit * 3 + 1]
            val nz = worldN[hit * 3 + 2]
            val diff = max(0f, nx * L[0] + ny * L[1] + nz * L[2])
            // The view ray (+z) mirrored on the facet; a glint where it points at the light.
            val rx = -2f * nz * nx
            val ry = -2f * nz * ny
            val rz = 1f - 2f * nz * nz
            val spec = max(0f, rx * L[0] + ry * L[1] + rz * L[2]).pow(18)
            glint = max(glint, spec)
            setLed(gem, x, y, AMBIENT + DIFFUSE * diff + MAX * spec)
        }

        drawFace(gem, input, cx, cy, scaleX, scaleY, cay, say, cax, sax)
        sparkles(gem, t, cx, cy, scaleX, scaleY, glint)
        mix(frame, sprite, gem, env)
    }

    /** The eyes and smile, on the front facets: where they are once the stone has turned, if they face us. */
    private fun drawFace(
        frame: IntArray, input: SignatureInput, cx: Float, cy: Float, scaleX: Float, scaleY: Float,
        cay: Float, say: Float, cax: Float, sax: Float,
    ) {
        fun place(p: FloatArray): FloatArray {
            val rx = cay * p[0] + say * p[2]
            val rz = -say * p[0] + cay * p[2]
            val wy = cax * p[1] - sax * rz
            val wz = sax * p[1] + cax * rz
            return floatArrayOf(cx + rx * scaleX, cy + (wy - MID_Y) * scaleY, wz)
        }
        fun hole(x: Int, y: Int) {
            if (x in 0 until N && y in 0 until N && frame[y * N + x] > 0) frame[y * N + x] = 0
        }
        val wide = scaleX >= 5.5f
        for (anchor in EYES) {
            val (px, py, pz) = place(anchor)
            if (pz > FACING) continue
            val ex = if (wide) (px - 0.5f).roundToInt() else px.roundToInt()
            val ey = (py - 0.5f).roundToInt()
            for (a in 0..(if (wide) 1 else 0)) {
                if (!input.blink) hole(ex + a, ey)
                hole(ex + a, ey + 1)
            }
        }
        val (mx, my, mz) = place(MOUTH)
        if (mz <= FACING) {
            val x = mx.roundToInt()
            val y = my.roundToInt()
            hole(x - 1, y)
            hole(x + 1, y)
            hole(x, y + 1)
        }
    }

    /** Little crosses round the stone, each flaring in turn, and all at once while a glint flashes. */
    private fun sparkles(frame: IntArray, t: Long, cx: Float, cy: Float, scaleX: Float, scaleY: Float, glint: Float) {
        for (i in SPARKS.indices) {
            val (ax, ay) = SPARKS[i]
            val x = (cx + ax * (scaleX + 2.5f)).roundToInt()
            val y = (cy + ay * (scaleY + 1.5f)).roundToInt()
            val wave = max(0f, sin(t * 0.0042f + i * 2.2f)).pow(6)
            val level = max(wave, glint * 0.8f)
            if (level < 0.08f) continue
            val v = MAX * level
            fun dot(dx: Int, dy: Int, k: Float) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until N && yy in 0 until N && frame[yy * N + xx] == 0) maxLed(frame, xx, yy, v * k)
            }
            dot(0, 0, 1f)
            if (level > 0.45f) { dot(1, 0, 0.4f); dot(-1, 0, 0.4f); dot(0, 1, 0.4f); dot(0, -1, 0.4f) }
        }
    }

    private companion object {
        const val TURNS = 2
        /** Radians a second of extra spin at full side tilt, and how far a forward/back lean rocks it. */
        const val TILT_SPIN = 3f
        const val TILT_TIP = 0.5f
        /** Tipped a little toward the viewer, so the table shows. */
        const val BASE_TIP = -0.28f
        const val AMBIENT = 420f
        const val DIFFUSE = 1700f
        const val FADE_MS = 550L
        /** A face point shows while it points at least this much our way (z toward the viewer is negative). */
        const val FACING = -0.3f
        /** The middle of the stone's height, table -0.62 to culet 0.95. */
        const val MID_Y = 0.165f

        val L = norm3(-0.55f, -0.7f, -0.6f)

        /** The stone's frame: y down, the viewer at -z. Eyes and mouth on the front of the pavilion, just under the girdle. */
        val EYES = listOf(floatArrayOf(-0.33f, 0.06f, -0.68f), floatArrayOf(0.33f, 0.06f, -0.68f))
        val MOUTH = floatArrayOf(0f, 0.33f, -0.55f)
        val SPARKS = listOf(-1f to -0.75f, 1f to -0.45f, -0.9f to 0.6f, 0.95f to 0.75f)

        /** Inverse rotation (x by -ax, then y by -ay) of a vector. */
        fun inverse(x: Float, y: Float, z: Float, cax: Float, sax: Float, cay: Float, say: Float): FloatArray {
            val y1 = cax * y + sax * z
            val z1 = -sax * y + cax * z
            return floatArrayOf(cay * x - say * z1, y1, say * x + cay * z1)
        }

        /** The cut as half-spaces n . p <= d: [nx, ny, nz, d]. */
        val PLANES: Array<FloatArray> = run {
            val top = -0.62f
            val girdle = -0.18f
            val culet = 0.95f
            fun ring(r: Float, y: Float, off: Float) = Array(8) { i ->
                val a = (i + off) / 8f * 2f * PI.toFloat()
                floatArrayOf(cos(a) * r, y, sin(a) * r)
            }
            val tRing = ring(0.5f, top, 0f)
            val gRing = ring(1f, girdle, 0.5f)
            val apex = floatArrayOf(0f, culet, 0f)
            val out = ArrayList<FloatArray>()
            out += floatArrayOf(0f, -1f, 0f, -top)
            fun add(a: FloatArray, b: FloatArray, c: FloatArray) {
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
            for (i in 0 until 8) {
                val j = (i + 1) % 8
                add(tRing[i], tRing[j], gRing[i])
                add(gRing[i], gRing[(i + 7) % 8], tRing[i])
                add(gRing[i], gRing[j], apex)
            }
            out.toTypedArray()
        }
    }
}
