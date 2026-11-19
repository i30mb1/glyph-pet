package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A pet turning around its vertical axis in pseudo-3D, for [Moments.evolve]. The sprite is "inflated" into a voxel
 * volume (thicker toward the middle of the silhouette, like a pillow), turned, z-buffered and splatted sub-pixel onto
 * the matrix; nearer surfaces are brighter. The same idea as the Lab's voxel hedgehog, for any species.
 */
internal object Turn3D {
    private const val N = GlyphMatrixSpec.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private const val BODY = SpeciesRenderer.BODY

    /** Distance between voxel layers in depth. */
    private const val LAYER = 0.8f

    /** Two surfaces closer than this in depth blend; further ones are hidden. */
    private const val DEPTH_EPS = 0.6f

    /** The thickest a pet gets: this many layers in front of the middle and behind it. */
    private const val MAX_INSET = 3

    private const val KIND_BODY: Byte = 0
    private const val KIND_BRIGHT: Byte = 1
    private const val KIND_HOLE: Byte = 2

    /** A voxel volume around the sprite's middle, which sits at ([cx], [cy]) on the matrix. */
    private class Model(
        val x: FloatArray,
        val y: FloatArray,
        val z: FloatArray,
        val kind: ByteArray,
        val cx: Float,
        val cy: Float,
    )

    private val models = HashMap<String, Model>()

    private fun model(s: Species, face: Face): Model = synchronized(models) {
        val key = "${s.id}/$face"
        models[key] ?: build(s, face).also {
            if (models.size > 8) models.clear()
            models[key] = it
        }
    }

    private fun build(s: Species, face: Face): Model {
        val grid = SpeciesRenderer.carve(s, face)
        val w = s.width
        val h = s.height
        fun solid(x: Int, y: Int) = (s.rows.getOrNull(y)?.getOrNull(x) ?: '.') != '.'
        // How deep inside the outline a cell is (1 at the edge): the thickness grows toward the middle.
        fun inset(x: Int, y: Int): Int {
            var k = 1
            while (k < MAX_INSET) {
                var edge = false
                for (a in -k..k) for (b in -k..k) if (abs(a) + abs(b) <= k && !solid(x + a, y + b)) edge = true
                if (edge) break
                k++
            }
            return k
        }
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lz = ArrayList<Float>()
        val lk = ArrayList<Byte>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y)) continue
            val c = grid.getOrNull(y)?.getOrNull(x) ?: '#'
            val d = inset(x, y)
            for (zi in -d..d) {
                lx += x - (w - 1) / 2f
                ly += y - (h - 1) / 2f
                lz += zi * LAYER
                lk += when {
                    // Eyes and mouth are carved into the front only; the back of the head is plain.
                    c == '.' -> if (zi <= -d + 1) KIND_HOLE else KIND_BODY
                    c == 'o' -> KIND_BRIGHT
                    else -> KIND_BODY
                }
            }
        }
        return Model(
            lx.toFloatArray(), ly.toFloatArray(), lz.toFloatArray(), lk.toByteArray(),
            cx = s.ox + (w - 1) / 2f, cy = s.oy + (h - 1) / 2f,
        )
    }

    /**
     * Draws [s] with [face], turned [angle] radians around its vertical axis (0 faces the viewer, the turn goes the
     * same way as the angle grows), into [frame] keeping the brighter value. [light] 0..1 floods it with light (1 is
     * one bright shape), [gain] dims the whole drawing.
     */
    fun draw(frame: IntArray, s: Species, face: Face, angle: Float, light: Float = 0f, gain: Float = 1f) {
        val m = model(s, face)
        val count = m.x.size
        val ca = cos(angle)
        val sa = sin(angle)
        val px = FloatArray(count)
        val py = FloatArray(count)
        val pd = FloatArray(count)
        val zbuf = FloatArray(N * N) { -1e9f }
        val acc = FloatArray(N * N)
        val wsum = FloatArray(N * N)
        // Pass 1: turn, project, and find the nearest depth at every LED a voxel touches.
        for (i in 0 until count) {
            px[i] = m.cx + ca * m.x[i] + sa * m.z[i]
            py[i] = m.cy + m.y[i]
            pd[i] = sa * m.x[i] - ca * m.z[i]
            val d = pd[i]
            splat(px[i], py[i]) { idx, w -> if (w > 0.15f && d > zbuf[idx]) zbuf[idx] = d }
        }
        val l = light.coerceIn(0f, 1f)
        // Pass 2: only the front surface adds its light, nearer is brighter.
        for (i in 0 until count) {
            val d = pd[i]
            val base = when (m.kind[i]) {
                KIND_HOLE -> 0f
                KIND_BRIGHT -> BRIGHT * (0.75f + 0.08f * d).coerceIn(0.5f, 1f)
                else -> BODY * (0.62f + 0.16f * d).coerceIn(0.3f, 1.45f)
            }
            val value = base + (BRIGHT - base) * l
            splat(px[i], py[i]) { idx, w ->
                if (d >= zbuf[idx] - DEPTH_EPS) {
                    acc[idx] += value * w
                    wsum[idx] += w
                }
            }
        }
        for (i in 0 until N * N) {
            val ws = wsum[i]
            if (ws <= 0f) continue
            val v = (acc[i] / ws.coerceAtLeast(1f) * gain).toInt().coerceIn(0, BRIGHT)
            SpeciesRenderer.put(frame, i % N, i / N, v)
        }
    }

    private inline fun splat(x: Float, y: Float, f: (Int, Float) -> Unit) {
        if (x.isNaN() || y.isNaN()) return
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        if (x0 in 0 until N && y0 in 0 until N) f(y0 * N + x0, (1 - fx) * (1 - fy))
        if (x0 + 1 in 0 until N && y0 in 0 until N) f(y0 * N + x0 + 1, fx * (1 - fy))
        if (x0 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0, (1 - fx) * fy)
        if (x0 + 1 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0 + 1, fx * fy)
    }
}
