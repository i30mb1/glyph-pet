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
 * The pet's matrix on the app's screen, finer than the real one: the 25x25 picture is smoothed and shown by a grid of
 * LEDs twice as fine ([FINE] per side), so shapes have soft edges and faces read clearly on a phone screen. The real
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
        fun at(x: Int, y: Int) = if (x in 0 until SIZE && y in 0 until SIZE) levels[y * SIZE + x] else 0f
        for (sy in 0 until FINE) for (sx in 0 until FINE) {
            if (!GlyphMatrixSpec.isVisible(sx / SUB, sy / SUB)) continue
            // Between the four LEDs round this fine one: a smooth picture instead of hard squares.
            val u = (sx + 0.5f) / SUB - 0.5f
            val v = (sy + 0.5f) / SUB - 0.5f
            val x0 = kotlin.math.floor(u).toInt()
            val y0 = kotlin.math.floor(v).toInt()
            val fx = u - x0
            val fy = v - y0
            val level = at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy) + at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy
            drawCircle(lerp(N7Colors.LedOff, N7Colors.LedOn, level.coerceIn(0f, 1f)), r, origin + Offset((sx + 0.5f) * sub, (sy + 0.5f) * sub))
        }
    }
}

private const val SIZE = GlyphMatrixSpec.SIZE
private const val SUB = 2
private const val FINE = SIZE * SUB
