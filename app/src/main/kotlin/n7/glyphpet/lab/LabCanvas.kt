package n7.glyphpet.lab

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.matrix.MatrixFont
import kotlin.math.floor

/** Brightness levels of the Lab, in matrix units (0..4095). */
internal object Lit {
    const val MAX = 4095f
    const val BODY = 1200f
    const val BRIGHT = 4000f
}

/**
 * A float picture: [w] x [h] values with [cov] coverage (1 where the picture is solid, even if dark, like an eye hole),
 * so it can hide what is behind it.
 */
internal class Sprite(val w: Int, val h: Int, val v: FloatArray, val cov: FloatArray) {
    fun value(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) v[y * w + x] else 0f
    fun coverage(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) cov[y * w + x] else 0f
}

/**
 * A 25x25 float layer: [v] brightness and [cov] coverage. Everything that moves is drawn sub-pixel: a point between
 * LEDs shares its brightness between the four neighbours (bilinear splat), so motion is smooth instead of stepping.
 */
internal class Layer {
    val v = FloatArray(N * N)
    val cov = FloatArray(N * N)

    fun clear() {
        v.fill(0f)
        cov.fill(0f)
    }

    /** Adds [value] at a sub-pixel point. */
    fun splat(x: Float, y: Float, value: Float, coverage: Float = 0f) {
        if (x.isNaN() || y.isNaN() || x < -2f || y < -2f || x > N + 1f || y > N + 1f) return
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        add(x0, y0, value, coverage, (1 - fx) * (1 - fy))
        add(x0 + 1, y0, value, coverage, fx * (1 - fy))
        add(x0, y0 + 1, value, coverage, (1 - fx) * fy)
        add(x0 + 1, y0 + 1, value, coverage, fx * fy)
    }

    private fun add(x: Int, y: Int, value: Float, coverage: Float, w: Float) {
        if (x !in 0 until N || y !in 0 until N || w <= 0f) return
        val i = y * N + x
        v[i] += value * w
        if (coverage > 0f) cov[i] = (cov[i] + coverage * w).coerceAtMost(1f)
    }

    /** Lights a whole LED, keeping the brighter value. */
    fun max(x: Int, y: Int, value: Float) {
        if (x !in 0 until N || y !in 0 until N) return
        val i = y * N + x
        if (value > v[i]) v[i] = value
    }

    /** Draws [s] with its top-left corner at the sub-pixel point ([x], [y]), every source pixel splatted. */
    fun sprite(s: Sprite, x: Float, y: Float, gain: Float = 1f) {
        for (sy in 0 until s.h) for (sx in 0 until s.w) {
            val c = s.cov[sy * s.w + sx]
            if (c <= 0f) continue
            splat(x + sx, y + sy, s.v[sy * s.w + sx] * gain, c)
        }
    }

    /**
     * Draws [s] scaled by [sx], [sy] around its bottom-centre anchor at ([ax], [ay]), by sampling it bilinearly for
     * every LED it covers: smooth squash and stretch.
     */
    fun spriteScaled(s: Sprite, ax: Float, ay: Float, sx: Float, sy: Float, gain: Float = 1f) {
        if (sx <= 0.05f || sy <= 0.05f) return
        val left = ax - s.w * sx / 2f
        val top = ay - s.h * sy
        val x0 = floor(left).toInt().coerceAtLeast(0)
        val x1 = (floor(left + s.w * sx).toInt() + 1).coerceAtMost(N - 1)
        val y0 = floor(top).toInt().coerceAtLeast(0)
        val y1 = (floor(ay).toInt() + 1).coerceAtMost(N - 1)
        for (y in y0..y1) for (x in x0..x1) {
            // The source coordinate of this LED's centre; source pixel centres sit at +0.5.
            val u = (x + 0.5f - left) / sx - 0.5f
            val w = (y + 0.5f - top) / sy - 0.5f
            val iu = floor(u).toInt()
            val iw = floor(w).toInt()
            val fu = u - iu
            val fw = w - iw
            val c = s.coverage(iu, iw) * (1 - fu) * (1 - fw) + s.coverage(iu + 1, iw) * fu * (1 - fw) +
                s.coverage(iu, iw + 1) * (1 - fu) * fw + s.coverage(iu + 1, iw + 1) * fu * fw
            if (c <= 0.01f) continue
            val value = s.value(iu, iw) * (1 - fu) * (1 - fw) + s.value(iu + 1, iw) * fu * (1 - fw) +
                s.value(iu, iw + 1) * (1 - fu) * fw + s.value(iu + 1, iw + 1) * fu * fw
            val i = y * N + x
            v[i] += value * gain
            cov[i] = (cov[i] + c).coerceAtMost(1f)
        }
    }

    /** Puts this layer over [under]: where this layer covers, what is below is hidden. */
    fun over(under: Layer, gain: Float = 1f) {
        for (i in 0 until N * N) {
            val c = cov[i] * gain
            under.v[i] = under.v[i] * (1 - c) + v[i] * gain
            if (c > under.cov[i]) under.cov[i] = c
        }
    }

    /** Writes the layer as a matrix frame: only real LEDs, values 0..4095. */
    fun toFrame(out: IntArray = IntArray(N * N)): IntArray {
        for (i in 0 until N * N) {
            val value = v[i]
            out[i] = if (!VISIBLE[i] || value.isNaN()) 0 else value.coerceIn(0f, Lit.MAX).toInt()
        }
        return out
    }

    companion object {
        const val N = GlyphMatrixSpec.SIZE
        const val C = 12f

        /** Real LEDs, row by row. */
        val VISIBLE = BooleanArray(N * N) { GlyphMatrixSpec.isVisible(it % N, it / N) }

        /**
         * The experiment number as big dotted digits: each pixel of the 3x5 font is a 2x2 dot with a dark gap, so a
         * digit is 8 x 14 LEDs, centred.
         */
        fun number(n: Int, into: Layer, value: Float = Lit.BRIGHT) {
            val text = n.toString()
            val glyphs = text.mapNotNull { MatrixFont.Small.glyph(it) }
            if (glyphs.isEmpty()) return
            val widths = glyphs.map { it[0].length * 3 - 1 }
            val total = widths.sum() + 3 * (glyphs.size - 1)
            var left = (N - total) / 2
            val top = (N - (glyphs[0].size * 3 - 1)) / 2
            glyphs.forEachIndexed { k, rows ->
                rows.forEachIndexed { gy, row ->
                    row.forEachIndexed { gx, ch ->
                        if (ch != '#') return@forEachIndexed
                        for (dy in 0..1) for (dx in 0..1) into.max(left + gx * 3 + dx, top + gy * 3 + dy, value)
                    }
                }
                left += widths[k] + 3
            }
        }
    }
}
