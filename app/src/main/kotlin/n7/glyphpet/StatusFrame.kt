package n7.glyphpet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import n7.glyph.ui.theme.N7Colors
import n7.glyphpet.pet.Rarity
import kotlin.math.roundToInt

/** How far inside the tile's edge the status outline runs: the "on the matrix" border stays on the edge itself. */
private val INSET = 4.dp

/** Distance between two dots of a dotted outline. */
private val DOT_STEP = 6.dp
private val DOT_RADIUS = 1.dp

/** Dots lit in the mythic run, the head included; they dim towards the tail. */
private const val RUN = 7

/** One lap of the mythic run. */
private const val LAP_MS = 2600

/**
 * A hero's status as an outline in the Nothing dot style, drawn over the tile [cornerRadius] rounds: common none, rare
 * a dotted outline, legendary a solid bright one, mythic dim dots with a lit run going round and round.
 */
@Composable
fun Modifier.statusFrame(rarity: Rarity, cornerRadius: Dp): Modifier {
    if (rarity == Rarity.COMMON) return this
    val phase: State<Float>? = if (rarity == Rarity.MYTHIC) {
        rememberInfiniteTransition(label = "mythic").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LAP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "run",
        )
    } else {
        null
    }
    return drawWithContent {
        drawContent()
        // Read only while drawing: the run moves without recomposing the tile.
        drawStatus(rarity, cornerRadius, phase?.value ?: 0f)
    }
}

private fun DrawScope.drawStatus(rarity: Rarity, cornerRadius: Dp, phase: Float) {
    val inset = INSET.toPx()
    val w = size.width - 2 * inset
    val h = size.height - 2 * inset
    if (w <= 0f || h <= 0f) return
    val r = (cornerRadius - INSET).toPx().coerceAtLeast(0f)
    val outline = Path().apply { addRoundRect(RoundRect(inset, inset, inset + w, inset + h, CornerRadius(r))) }
    when (rarity) {
        Rarity.COMMON -> Unit
        Rarity.LEGENDARY -> drawPath(outline, N7Colors.Ink, style = Stroke(width = 1.5.dp.toPx()))
        Rarity.RARE, Rarity.MYTHIC -> {
            val measure = PathMeasure().apply { setPath(outline, false) }
            val length = measure.length
            if (length <= 0f) return
            val count = (length / DOT_STEP.toPx()).roundToInt().coerceAtLeast(1)
            val step = length / count
            val dot = DOT_RADIUS.toPx()
            val head = phase * count
            for (i in 0 until count) {
                val at = measure.getPosition(i * step)
                if (rarity == Rarity.RARE) {
                    drawCircle(N7Colors.InkSubtle, dot, at)
                    continue
                }
                // How far behind the head this dot is, going round.
                val behind = ((head - i) % count + count) % count
                if (behind < RUN) {
                    val lit = 1f - behind / RUN
                    drawCircle(N7Colors.LedOn.copy(alpha = 0.35f + 0.65f * lit), dot * (1f + 0.4f * lit), at)
                } else {
                    drawCircle(N7Colors.InkSubtle.copy(alpha = 0.45f), dot, at)
                }
            }
        }
    }
}
