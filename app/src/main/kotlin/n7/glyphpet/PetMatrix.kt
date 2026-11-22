package n7.glyphpet

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyph.ui.theme.N7Colors

/**
 * The pet's matrix on the app's screen: exactly the 25x25 LEDs of the back of the phone, nothing added or guessed,
 * only drawn the way the real ones look under glass: each lit LED brighter in the middle and soft at the edge, a faint
 * glow around the bright ones, unlit LEDs dark so the picture stands out, a glass disc darker to the rim with a faint
 * reflection at the top.
 */
@Composable
fun PetMatrix(cells: List<Cell>, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val levels = remember(cells) {
        FloatArray(SIZE * SIZE).also { a ->
            for (c in cells) if (c.x in 0 until SIZE && c.y in 0 until SIZE) a[c.y * SIZE + c.x] = c.level.coerceIn(0f, 1f)
        }
    }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        val side = size.minDimension
        val unit = side / 28.2f
        val origin = Offset((size.width - side) / 2f + 1.6f * unit, (size.height - side) / 2f + 1.6f * unit)
        val c = origin + Offset(12.5f * unit, 12.5f * unit)
        val lens = 13.85f * unit
        drawCircle(Brush.radialGradient(listOf(GLASS_MIDDLE, N7Colors.Lens), center = c, radius = lens), lens, c)
        drawCircle(N7Colors.LensRim, lens, c, style = Stroke(width = 0.22f * unit))

        fun at(x: Int, y: Int) = origin + Offset((x + 0.5f) * unit, (y + 0.5f) * unit)
        val r = 0.40f * unit
        for (y in 0 until SIZE) for (x in GlyphMatrixSpec.rowRange(y)) {
            val level = levels[y * SIZE + x]
            val p = at(x, y)
            if (level <= 0f) {
                drawCircle(LED_DARK, r, p)
                continue
            }
            // Soft edge, bright middle: three discs, no gradient per LED, so a full frame stays cheap.
            drawCircle(lerp(N7Colors.LedOff, N7Colors.LedOn, level * 0.62f), r, p)
            drawCircle(lerp(N7Colors.LedOff, N7Colors.LedOn, level * 0.84f), r * 0.78f, p)
            drawCircle(lerp(N7Colors.LedOff, N7Colors.LedOn, level), r * 0.52f, p)
        }
        // The glow goes on top, added to the light, only around the bright LEDs.
        val halo = r * 2.6f
        for (y in 0 until SIZE) for (x in GlyphMatrixSpec.rowRange(y)) {
            val level = levels[y * SIZE + x]
            if (level < GLOW_FROM) continue
            val p = at(x, y)
            val a = GLOW * level * level
            drawCircle(Brush.radialGradient(listOf(N7Colors.LedOn.copy(alpha = a), Color.Transparent), center = p, radius = halo), halo, p, blendMode = BlendMode.Plus)
        }
        // A faint reflection across the top of the glass.
        val top = Offset(c.x - lens * 0.64f, c.y - lens * 0.92f)
        val glare = Size(lens * 1.28f, lens * 0.62f)
        drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent), startY = top.y, endY = top.y + glare.height), top, glare)
    }
}

private const val SIZE = GlyphMatrixSpec.SIZE
private val LED_DARK = Color(0xFF1A1B1E)
private val GLASS_MIDDLE = Color(0xFF141519)
private const val GLOW_FROM = 0.45f
private const val GLOW = 0.22f
