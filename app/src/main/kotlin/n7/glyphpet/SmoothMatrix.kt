package n7.glyphpet

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.theme.N7Colors

/**
 * The pet's matrix on the app's screen, finer than the real one: the 25x25 picture is doubled the way pixel art is
 * ([scale2x]: edges stay sharp, only the steps of a slant are rounded off) and shown by a grid of LEDs twice as fine
 * ([FINE] per side), so faces read clearly on a phone screen without a blur. The real
 * matrix is untouched; this is only how the app draws it.
 */
@Composable
fun SmoothMatrix(cells: List<Cell>, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val levels = remember(cells) {
        FloatArray(SIZE * SIZE).also { a -> for (c in cells) if (c.x in 0 until SIZE && c.y in 0 until SIZE) a[c.y * SIZE + c.x] = c.level }
    }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        val side = size.minDimension
        val unit = side / 28.2f
        val origin = Offset((size.width - side) / 2f + 1.6f * unit, (size.height - side) / 2f + 1.6f * unit)
        val c = origin + Offset(12.5f * unit, 12.5f * unit)
        drawCircle(N7Colors.Lens, radius = 13.85f * unit, center = c)
        drawCircle(N7Colors.LensRim, radius = 13.85f * unit, center = c, style = Stroke(width = 0.22f * unit))
        val sub = unit / SUB
        val r = sub * 0.42f
        val fine = scale2x(levels)
        for (sy in 0 until FINE) for (sx in 0 until FINE) {
            if (!GlyphMatrixSpec.isVisible(sx / SUB, sy / SUB)) continue
            drawCircle(lerp(N7Colors.LedOff, N7Colors.LedOn, fine[sy * FINE + sx].coerceIn(0f, 1f)), r, origin + Offset((sx + 0.5f) * sub, (sy + 0.5f) * sub))
        }
    }
}

/**
 * Scale2x (EPX): every LED becomes four, each one taking a neighbour's light where two neighbours that meet at its
 * corner agree and the other two do not. A slant gets a step in between; straight edges and single dots stay sharp.
 */
private fun scale2x(src: FloatArray): FloatArray {
    val out = FloatArray(FINE * FINE)
    fun at(x: Int, y: Int) = if (x in 0 until SIZE && y in 0 until SIZE) src[y * SIZE + x] else 0f
    fun same(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.08f
    for (y in 0 until SIZE) for (x in 0 until SIZE) {
        val p = at(x, y)
        val a = at(x, y - 1)
        val b = at(x + 1, y)
        val c = at(x - 1, y)
        val d = at(x, y + 1)
        val e0 = if (same(c, a) && !same(c, d) && !same(a, b)) a else p
        val e1 = if (same(a, b) && !same(a, c) && !same(b, d)) b else p
        val e2 = if (same(d, c) && !same(d, b) && !same(c, a)) c else p
        val e3 = if (same(b, d) && !same(b, a) && !same(d, c)) d else p
        out[(2 * y) * FINE + 2 * x] = e0
        out[(2 * y) * FINE + 2 * x + 1] = e1
        out[(2 * y + 1) * FINE + 2 * x] = e2
        out[(2 * y + 1) * FINE + 2 * x + 1] = e3
    }
    return out
}

private const val SIZE = GlyphMatrixSpec.SIZE
private const val SUB = 2
private const val FINE = SIZE * SUB
