package n7.glyphpet.pet.signature

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Species
import kotlin.math.roundToInt

/**
 * Shared bits of the common pets' small signature effects (on top of [Kit], [Canvas] and [SafeSignature]): where a
 * form stands on the matrix and a sprite turned on its side.
 */
internal object CommonKit {
    /** The lowest row of [s]'s sprite that has anything lit. */
    fun lowestRow(s: Species): Int = s.rows.indexOfLast { row -> row.any { it != '.' } }.coerceAtLeast(0)

    /** The highest row of [s]'s sprite that has anything lit. */
    fun topRow(s: Species): Int = s.rows.indexOfFirst { row -> row.any { it != '.' } }.coerceAtLeast(0)

    /** The middle of the lit span of sprite row [y], in sprite columns. */
    fun rowMid(s: Species, y: Int): Float {
        val row = s.rows.getOrNull(y).orEmpty()
        val a = row.indexOfFirst { it != '.' }
        val b = row.indexOfLast { it != '.' }
        return if (a < 0) (s.width - 1) / 2f else (a + b) / 2f
    }

    /** The middle column of [s] standing [dx] cells from home, in matrix cells. */
    fun midX(s: Species, dx: Int): Float = s.ox + dx + (s.width - 1) / 2f

    /**
     * The ground: the matrix row right under the pet's feet, moved up until it is on the round matrix at the pet's
     * middle (so things put "on the ground" are always seen).
     */
    fun groundRow(s: Species, dx: Int): Int {
        val x = midX(s, dx).roundToInt()
        var y = s.oy + lowestRow(s) + 1
        while (y > 0 && !GlyphMatrixSpec.isVisible(x, y)) y--
        return y
    }

    /** [s] turned a quarter round: [headRight] true turns its top to the right (clockwise). */
    fun rotate(s: Sprite, headRight: Boolean): Sprite {
        val w = s.h
        val h = s.w
        val v = FloatArray(w * h)
        val cov = FloatArray(w * h)
        for (y in 0 until s.h) for (x in 0 until s.w) {
            val nx = if (headRight) s.h - 1 - y else y
            val ny = if (headRight) x else s.w - 1 - x
            v[ny * w + nx] = s.v[y * s.w + x]
            cov[ny * w + nx] = s.cov[y * s.w + x]
        }
        return Sprite(w, h, v, cov)
    }

    /** Lights the cell ([x], [y]) with [value] if it is on the matrix, keeping the brighter value. */
    fun put(frame: IntArray, x: Int, y: Int, value: Float) {
        if (x !in 0 until Kit.N || y !in 0 until Kit.N || value.isNaN()) return
        val i = y * Kit.N + x
        if (!Kit.VISIBLE[i]) return
        val v = value.toInt().coerceIn(0, Kit.BRIGHT.toInt())
        if (v > frame[i]) frame[i] = v
    }
}
